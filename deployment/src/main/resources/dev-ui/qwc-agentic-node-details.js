import { LitElement, html, css, nothing } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';
import { KIND_LABELS, KIND_STYLES } from './qwc-agentic-graph.js';

/** "com.example.Outer$Inner#method" -> "Inner#method" */
function shortRef(ref) {
    if (!ref) {
        return ref;
    }
    const [className, method] = ref.split('#');
    const simple = className.substring(Math.max(className.lastIndexOf('.'), className.lastIndexOf('$')) + 1);
    return method ? `${simple}#${method}` : simple;
}

/**
 * Details of one topology node (an AgentNode of the AgenticSystem): what the agent is and does, without opening the
 * code. Every field is listed, with "none" when it has no value, so nothing is silently missing.
 * Fires 'close' when the close button is clicked.
 */
export class QwcAgenticNodeDetails extends LitElement {

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-s);
            padding: var(--lumo-space-m);
            border: 1px solid var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-m);
            overflow-y: auto;
            font-size: var(--lumo-font-size-s);
        }
        .title {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
        }
        .title h4 {
            margin: 0;
            flex: 1;
            font-size: var(--lumo-font-size-l);
            overflow-wrap: anywhere;
        }
        .kind {
            padding: 0 var(--lumo-space-s);
            border: 2px solid;
            border-radius: var(--lumo-border-radius-s);
            white-space: nowrap;
        }
        .id {
            color: var(--lumo-secondary-text-color);
            overflow-wrap: anywhere;
        }
        dl {
            display: grid;
            grid-template-columns: max-content 1fr;
            gap: var(--lumo-space-xs) var(--lumo-space-m);
            margin: 0;
        }
        dt {
            color: var(--lumo-secondary-text-color);
        }
        dd {
            margin: 0;
            overflow-wrap: anywhere;
        }
        h5 {
            margin: var(--lumo-space-s) 0 0;
            font-size: var(--lumo-font-size-s);
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--lumo-secondary-text-color);
        }
        .none {
            color: var(--lumo-tertiary-text-color);
            font-style: italic;
        }
        .chips {
            display: flex;
            flex-wrap: wrap;
            gap: var(--lumo-space-xs);
        }
        .chip {
            padding: 0 var(--lumo-space-xs);
            border-radius: var(--lumo-border-radius-s);
            background: var(--lumo-contrast-10pct);
            font-family: var(--lumo-font-family-monospace, monospace);
        }
        pre {
            margin: 0;
            padding: var(--lumo-space-s);
            max-height: 12em;
            overflow: auto;
            white-space: pre-wrap;
            border-radius: var(--lumo-border-radius-s);
            background: var(--lumo-contrast-5pct);
            font-size: var(--lumo-font-size-xs);
        }
        ul.tools {
            margin: 0;
            padding: 0;
            list-style: none;
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-xs);
        }
        .tool-name {
            font-family: var(--lumo-font-family-monospace, monospace);
            font-weight: bold;
        }
        .tool-ref, .tool-description {
            color: var(--lumo-secondary-text-color);
        }
    `;

    static properties = {
        node: { type: Object },
    };

    render() {
        const node = this.node;
        if (!node) {
            return nothing;
        }
        const style = KIND_STYLES[node.kind];
        return html`
            <div class="title">
                <h4>${node.name}</h4>
                <span class="kind" style="border-color: ${style?.color ?? 'currentColor'}">${KIND_LABELS[node.kind] ?? node.kind}</span>
                <vaadin-button theme="tertiary icon small" aria-label="Close" title="Close"
                               @click=${() => this.dispatchEvent(new CustomEvent('close'))}>
                    <vaadin-icon icon="font-awesome-solid:xmark"></vaadin-icon>
                </vaadin-button>
            </div>
            <div class="id">${node.id}</div>
            <dl>
                <dt>Description</dt><dd data-field="description">${node.description ?? this._none()}</dd>
                <dt>Input keys</dt><dd data-field="inputKeys">${this._chips(node.inputKeys)}</dd>
                <dt>Output key</dt><dd data-field="outputKey">${node.outputKey ? this._chips([node.outputKey]) : this._none()}</dd>
                <dt>Optional</dt><dd data-field="optional">${node.optional ? 'yes' : 'no'}</dd>
                <dt>Async</dt><dd data-field="async">${node.async ? 'yes' : 'no'}</dd>
                ${this._renderGuard(node.guard)}
                ${node.plannerInfo ? html`
                    <dt>Planner</dt>
                    <dd data-field="planner">${node.plannerInfo.plannerClass
                        ?? (node.plannerInfo.extras?.supplier ? html`from ${shortRef(node.plannerInfo.extras.supplier)}` : this._none())}</dd>` : nothing}
            </dl>
            ${node.kind === 'AI_AGENT' ? this._renderAiDetails(node.aiDetails) : nothing}`;
    }

    _renderGuard(guard) {
        if (!guard) {
            return nothing;
        }
        if (guard.maxIterations != null) {
            return html`
                <dt>Exit condition</dt><dd data-field="exitCondition">${guard.label ?? this._none()}</dd>
                <dt>Max iterations</dt><dd data-field="maxIterations">${guard.maxIterations}</dd>
                <dt>Exit tested</dt><dd data-field="testExitAtLoopEnd">${guard.testExitAtLoopEnd ? 'at the end of an iteration' : 'after each agent'}</dd>`;
        }
        return html`<dt>Activation</dt><dd data-field="activation">${guard.label}</dd>`;
    }

    _renderAiDetails(details) {
        return html`
            <h5>Model</h5>
            <div data-field="model">${details?.modelName ?? html`<span class="none">default model</span>`}</div>
            <h5>System message</h5>
            <div data-field="systemMessage">${details?.systemMessage ? html`<pre>${details.systemMessage}</pre>` : this._none()}</div>
            <h5>User message</h5>
            <div data-field="userMessage">${details?.userMessage ? html`<pre>${details.userMessage}</pre>` : this._none()}</div>
            <h5>Tools</h5>
            <div data-field="tools">${details?.tools?.length ? html`
                <ul class="tools">${details.tools.map(tool => html`
                    <li>
                        <span class="tool-name">${tool.name}</span>
                        <span class="tool-ref">${shortRef(`${tool.declaringClass}#${tool.methodName}`)}</span>
                        ${tool.description ? html`<div class="tool-description">${tool.description}</div>` : nothing}
                    </li>`)}
                </ul>` : this._none()}</div>
            ${details?.runtimeToolSources?.length ? html`
                <h5>Tools known at runtime</h5>
                <div class="chips" data-field="runtimeToolSources">${details.runtimeToolSources.map(source =>
                    html`<span class="chip">${source.startsWith('mcp:') ? source : shortRef(source)}</span>`)}</div>` : nothing}`;
    }

    _chips(values) {
        return values?.length ? html`<span class="chips">${values.map(v => html`<span class="chip">${v}</span>`)}</span>`
            : this._none();
    }

    _none() {
        return html`<span class="none">none</span>`;
    }
}

customElements.define('qwc-agentic-node-details', QwcAgenticNodeDetails);
