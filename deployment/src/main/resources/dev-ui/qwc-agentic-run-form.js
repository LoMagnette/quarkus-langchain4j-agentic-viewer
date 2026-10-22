import { LitElement, html, css, nothing } from 'lit';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/integer-field';
import '@vaadin/number-field';
import '@vaadin/checkbox';
import '@vaadin/select';
import '@vaadin/button';

/** The widget used for a schema: anything that is not a scalar is edited as JSON. */
export function widgetOf(schema) {
    if (Array.isArray(schema?.enum)) {
        return 'enum';
    }
    switch (schema?.type) {
        case 'string':
            return 'text';
        case 'integer':
            return 'integer';
        case 'number':
            return 'number';
        case 'boolean':
            return 'boolean';
        default:
            return 'json';
    }
}

/** A value of the schema's shape, to pre-fill a JSON editor. */
export function skeleton(schema) {
    if (!schema || typeof schema !== 'object') {
        return null;
    }
    if (Array.isArray(schema.enum)) {
        return schema.enum[0] ?? null;
    }
    switch (schema.type) {
        case 'object': {
            const value = {};
            Object.entries(schema.properties ?? {}).forEach(([name, property]) => value[name] = skeleton(property));
            return value;
        }
        case 'array':
            return [skeleton(schema.items)];
        case 'string':
            return '';
        case 'integer':
        case 'number':
            return 0;
        case 'boolean':
            return false;
        default:
            return null;
    }
}

const TYPE_CHECKS = {
    string: v => typeof v === 'string',
    integer: v => Number.isInteger(v),
    number: v => typeof v === 'number' && Number.isFinite(v),
    boolean: v => typeof v === 'boolean',
    array: v => Array.isArray(v),
    object: v => v !== null && typeof v === 'object' && !Array.isArray(v),
};

/**
 * Checks a value against the JSON schema subset the extension generates (type, enum, properties, required,
 * additionalProperties, items, uniqueItems, minLength, maxLength).
 *
 * @return error messages, prefixed with the path of the wrong part ('' for the value itself)
 */
export function validate(schema, value, path = '') {
    if (!schema || typeof schema !== 'object' || Object.keys(schema).length === 0) {
        return []; // any JSON
    }
    const at = path ? `${path}: ` : '';
    if (value === null || value === undefined) {
        return [`${at}a value is required`];
    }
    if (Array.isArray(schema.enum) && !schema.enum.includes(value)) {
        return [`${at}must be one of ${schema.enum.join(', ')}`];
    }
    if (schema.type && TYPE_CHECKS[schema.type] && !TYPE_CHECKS[schema.type](value)) {
        return [`${at}must be ${schema.type === 'integer' ? 'an integer' : `a${/^[aeiou]/.test(schema.type) ? 'n' : ''} ${schema.type}`}`];
    }
    const errors = [];
    if (schema.type === 'string') {
        if (schema.minLength != null && value.length < schema.minLength) {
            errors.push(`${at}must have at least ${schema.minLength} character(s)`);
        }
        if (schema.maxLength != null && value.length > schema.maxLength) {
            errors.push(`${at}must have at most ${schema.maxLength} character(s)`);
        }
    }
    if (schema.type === 'array') {
        value.forEach((item, i) => errors.push(...validate(schema.items, item, `${path}[${i}]`)));
        if (schema.uniqueItems && new Set(value.map(v => JSON.stringify(v))).size !== value.length) {
            errors.push(`${at}items must be unique`);
        }
    }
    if (schema.type === 'object') {
        (schema.required ?? []).filter(name => value[name] === undefined || value[name] === null)
            .forEach(name => errors.push(`${path ? path + '.' : ''}${name}: a value is required`));
        Object.entries(value).forEach(([name, item]) => {
            const property = schema.properties?.[name] ?? schema.additionalProperties;
            if (property && (item !== null || (schema.required ?? []).includes(name))) {
                errors.push(...validate(property, item, `${path ? path + '.' : ''}${name}`));
            }
        });
    }
    return errors;
}

/**
 * Run form of an agentic system, generated from its input JSON schema (getInputSchema).
 * Fires 'run' (detail: { inputs }) with valid inputs; invalid fields are flagged and nothing is fired.
 * Fires 'inputs-changed' (detail: { keys }) with the names of the inputs that have a value, as they change.
 */
export class QwcAgenticRunForm extends LitElement {

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-s);
        }
        vaadin-text-field, vaadin-text-area, vaadin-integer-field, vaadin-number-field, vaadin-select {
            width: 100%;
        }
        vaadin-text-area.json {
            font-family: var(--lumo-font-family-monospace, monospace);
            --vaadin-input-field-font-size: var(--lumo-font-size-xs);
        }
        .checkbox {
            display: flex;
            flex-direction: column;
        }
        .error {
            color: var(--lumo-error-text-color);
            font-size: var(--lumo-font-size-xs);
        }
        .none {
            color: var(--lumo-secondary-text-color);
        }
        .actions {
            display: flex;
            margin-top: var(--lumo-space-m);
            gap: var(--lumo-space-s);
        }
    `;

    static properties = {
        /** the object schema of the run inputs */
        schema: { type: Object },
        /** initial values by input name (e.g. the last inputs used): the fields are reset when their content changes */
        values: { type: Object },
        /** label of the button submitting the form ('Run' by default) */
        submitLabel: { type: String },
        _texts: { state: true },
        _errors: { state: true },
    };

    willUpdate(changed) {
        if (changed.has('schema')) {
            // one items array per select: a new array on every render makes vaadin-select rebuild its menu
            this._selectItems = {};
            Object.entries(this._properties()).filter(([, property]) => widgetOf(property) === 'enum')
                .forEach(([name, property]) => this._selectItems[name] = [
                    { label: this._required(name) ? 'Choose…' : '(none)', value: '' },
                    ...property.enum.map(v => ({ label: String(v), value: String(v) }))]);
        }
        // the same values again (e.g. parsed anew by the parent) must not throw away what the user typed
        const valuesKey = JSON.stringify(this.values ?? null);
        if (changed.has('schema') || (changed.has('values') && valuesKey !== this._valuesKey)) {
            this._valuesKey = valuesKey;
            this._texts = {};
            this._errors = {};
            Object.entries(this._properties()).forEach(([name, property]) => {
                const value = this.values?.[name];
                if (widgetOf(property) !== 'json') {
                    this._texts[name] = value;
                } else if (value !== undefined) {
                    this._texts[name] = JSON.stringify(value, null, 2);
                } else {
                    // an optional input only shows its shape (placeholder): it is not sent unless filled
                    this._texts[name] = this._required(name) ? JSON.stringify(skeleton(property), null, 2) : '';
                }
            });
        }
    }

    updated(changed) {
        if (changed.has('_texts')) {
            // a required text left empty is collected as '' (and refused at submit): it has no value yet
            const keys = Object.entries(this.collect().inputs).filter(([, value]) => value !== '').map(([key]) => key).sort();
            if (keys.join() !== this._lastKeys) {
                this._lastKeys = keys.join();
                this.dispatchEvent(new CustomEvent('inputs-changed', { detail: { keys }, bubbles: true, composed: true }));
            }
        }
    }

    _properties() {
        return this.schema?.properties ?? {};
    }

    _required(name) {
        return (this.schema?.required ?? []).includes(name);
    }

    render() {
        const properties = Object.entries(this._properties());
        return html`
            ${properties.length === 0 ? html`<div class="none">This system takes no input.</div>` : nothing}
            ${properties.map(([name, property]) => this._renderField(name, property))}
            <div class="actions">
                <vaadin-button theme="primary" data-action="submit-form" @click=${this._submit}>${this.submitLabel ?? 'Run'}</vaadin-button>
            </div>`;
    }

    _renderField(name, property) {
        const error = this._errors?.[name];
        const common = { label: name, required: this._required(name) };
        // the fields only show the errors of the last submit (manualValidation), until edited: a field validating
        // itself on blur would hide its error and move the Run button under the pointer, losing the click
        const set = value => {
            this._texts = { ...this._texts, [name]: value };
            if (this._errors?.[name]) {
                const { [name]: _, ...others } = this._errors;
                this._errors = others;
            }
        };
        switch (widgetOf(property)) {
            case 'text':
                return html`<vaadin-text-field data-input=${name} label=${common.label} ?required=${common.required}
                        .value=${this._texts[name] ?? ''} .manualValidation=${true} .invalid=${!!error} .errorMessage=${error ?? ''}
                        @value-changed=${e => set(e.detail.value)}></vaadin-text-field>`;
            case 'integer':
                return html`<vaadin-integer-field data-input=${name} label=${common.label} ?required=${common.required}
                        .value=${this._texts[name] != null ? String(this._texts[name]) : ''} .manualValidation=${true} .invalid=${!!error}
                        .errorMessage=${error ?? ''} @value-changed=${e => set(e.detail.value)}></vaadin-integer-field>`;
            case 'number':
                return html`<vaadin-number-field data-input=${name} label=${common.label} ?required=${common.required}
                        .value=${this._texts[name] != null ? String(this._texts[name]) : ''} .manualValidation=${true} .invalid=${!!error}
                        .errorMessage=${error ?? ''} @value-changed=${e => set(e.detail.value)}></vaadin-number-field>`;
            case 'boolean':
                return html`<div class="checkbox">
                        <vaadin-checkbox data-input=${name} label=${common.label} .checked=${!!this._texts[name]}
                                @checked-changed=${e => e.detail.value !== !!this._texts[name] && set(e.detail.value)}></vaadin-checkbox>
                        ${error ? html`<span class="error">${error}</span>` : nothing}</div>`;
            case 'enum':
                return html`<vaadin-select data-input=${name} label=${common.label} ?required=${common.required}
                        .items=${this._selectItems[name]}
                        .value=${this._texts[name] ?? ''} .manualValidation=${true} .invalid=${!!error} .errorMessage=${error ?? ''}
                        @value-changed=${e => set(e.detail.value)}></vaadin-select>`;
            default:
                return html`<vaadin-text-area class="json" data-input=${name} label="${name} (JSON)"
                        placeholder=${common.required ? '' : JSON.stringify(skeleton(property))}
                        ?required=${common.required} .value=${this._texts[name] ?? ''} .manualValidation=${true} .invalid=${!!error}
                        .errorMessage=${error ?? ''} @value-changed=${e => set(e.detail.value)}></vaadin-text-area>`;
        }
    }

    /**
     * @return { inputs, errors }: inputs by name (empty optional fields left out), errors by input name
     */
    collect() {
        const inputs = {};
        const errors = {};
        Object.entries(this._properties()).forEach(([name, property]) => {
            const text = this._texts?.[name];
            let value;
            switch (widgetOf(property)) {
                case 'text':
                    value = text === '' && !this._required(name) ? undefined : text;
                    break;
                case 'integer':
                case 'number':
                    value = text === undefined || text === null || text === '' ? undefined : Number(text);
                    break;
                case 'boolean':
                    // an optional boolean the user never set has no value (false would be one)
                    value = text === undefined && !this._required(name) ? undefined : !!text;
                    break;
                case 'enum':
                    value = text === '' || text === undefined ? undefined : property.enum.find(v => String(v) === text);
                    break;
                default:
                    if (text === undefined || text.trim() === '') {
                        value = undefined;
                    } else {
                        try {
                            value = JSON.parse(text);
                        } catch (e) {
                            errors[name] = `invalid JSON: ${e.message}`;
                            return;
                        }
                    }
            }
            if (value === undefined || value === null) {
                if (this._required(name)) {
                    errors[name] = 'a value is required';
                }
                return;
            }
            const problems = validate(property, value);
            if (problems.length > 0) {
                errors[name] = problems.map(p => p.startsWith(': ') ? p.substring(2) : p).join('; ');
                return;
            }
            inputs[name] = value;
        });
        return { inputs, errors };
    }

    _submit() {
        const { inputs, errors } = this.collect();
        this._errors = errors;
        if (Object.keys(errors).length === 0) {
            this.dispatchEvent(new CustomEvent('run', { detail: { inputs }, bubbles: true, composed: true }));
        }
    }
}

customElements.define('qwc-agentic-run-form', QwcAgenticRunForm);
