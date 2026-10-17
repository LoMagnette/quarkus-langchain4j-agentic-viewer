import { LitElement, html, css, nothing } from 'lit';

/**
 * A run value shown for reading, not as raw JSON: text as text (line breaks kept, no quotes), records and maps as a
 * list of keys and values (nested ones indented), lists of simple values numbered, a string holding JSON as its
 * structure. Values come from the run events: plain JSON values, possibly cut by the payload limit.
 */
export class QwcAgenticValue extends LitElement {

    static styles = css`
        :host {
            display: block;
            font-size: var(--lumo-font-size-s);
        }
        .text {
            white-space: pre-wrap;
            overflow-wrap: anywhere;
            max-height: 240px;
            overflow-y: auto;
        }
        .scalar {
            font-family: var(--lumo-font-family-monospace, monospace);
        }
        .none {
            color: var(--lumo-secondary-text-color);
            font-style: italic;
        }
        dl {
            display: grid;
            grid-template-columns: max-content 1fr;
            gap: 2px var(--lumo-space-s);
            margin: 0;
        }
        dt {
            font-weight: 600;
            color: var(--lumo-secondary-text-color);
            font-family: var(--lumo-font-family-monospace, monospace);
        }
        dd {
            margin: 0;
            min-width: 0;
        }
        .nested {
            padding-left: var(--lumo-space-s);
            border-left: 2px solid var(--lumo-contrast-10pct);
        }
        ol {
            margin: 0;
            padding-left: 1.6em;
        }
    `;

    static properties = {
        value: {},
    };

    render() {
        return this._render(this.value, 0);
    }

    _render(value, depth) {
        if (value === undefined || value === null) {
            return html`<span class="none">none</span>`;
        }
        if (typeof value === 'string') {
            const parsed = this._json(value);
            return parsed !== undefined ? this._render(parsed, depth)
                : value === '' ? html`<span class="none">empty</span>` : html`<div class="text">${value}</div>`;
        }
        if (typeof value !== 'object') {
            return html`<span class="scalar">${String(value)}</span>`;
        }
        if (depth > 6) {
            return html`<div class="text">${JSON.stringify(value)}</div>`;
        }
        if (Array.isArray(value)) {
            if (value.length === 0) {
                return html`<span class="none">empty list</span>`;
            }
            return html`<ol>${value.map(item => html`<li>${this._render(item, depth + 1)}</li>`)}</ol>`;
        }
        const entries = Object.entries(value);
        if (entries.length === 0) {
            return html`<span class="none">empty</span>`;
        }
        return html`<dl class=${depth > 0 ? 'nested' : nothing}>${entries.map(([key, item]) => html`
            <dt>${key}</dt><dd>${this._render(item, depth + 1)}</dd>`)}</dl>`;
    }

    /** The structure of a string holding a JSON object or array, else undefined. */
    _json(text) {
        const trimmed = text.trim();
        if (!(trimmed.startsWith('{') && trimmed.endsWith('}')) && !(trimmed.startsWith('[') && trimmed.endsWith(']'))) {
            return undefined;
        }
        try {
            return JSON.parse(trimmed);
        } catch (e) {
            return undefined;
        }
    }
}

customElements.define('qwc-agentic-value', QwcAgenticValue);
