import { LitElement, html, css, nothing } from 'lit';

/**
 * The AgenticScope state of a run, from its events: the run inputs, then every key an agent wrote (ScopeStateChanged),
 * with its value, the node that wrote it and whether the value was truncated.
 *
 * @return {{keys: Array<{key, value, nodeId, source, truncated}>, last: string|undefined}} keys in order of first write
 */
export function scopeState(events) {
    const keys = new Map();
    let last;
    for (const event of events) {
        if (event.type === 'RunStarted') {
            Object.entries(event.inputs ?? {}).forEach(([key, value]) =>
                keys.set(key, { key, value, nodeId: undefined, source: 'input', truncated: false }));
        } else if (event.type === 'ScopeStateChanged') {
            keys.set(event.key, { key: event.key, value: event.value, nodeId: event.nodeId, source: 'agent',
                truncated: !!event.truncated });
            last = event.key;
        }
    }
    return { keys: [...keys.values()], last };
}

/**
 * Side panel listing the AgenticScope keys and values of a run, updated as ScopeStateChanged events arrive; the last
 * changed key is highlighted and truncated values are marked.
 */
export class QwcAgenticScopeState extends LitElement {

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-s);
            font-size: var(--lumo-font-size-s);
        }
        .entry {
            padding: var(--lumo-space-xs) var(--lumo-space-s);
            border-radius: var(--lumo-border-radius-m);
            border: 1px solid var(--lumo-contrast-10pct);
        }
        .entry.last {
            border-color: var(--lumo-primary-color);
            background: var(--lumo-primary-color-10pct);
        }
        .key {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
            font-family: var(--lumo-font-family-monospace, monospace);
            font-weight: bold;
        }
        .tag {
            font-family: var(--lumo-font-family);
            font-weight: normal;
            font-size: var(--lumo-font-size-xs);
            padding: 0 var(--lumo-space-xs);
            border-radius: var(--lumo-border-radius-s);
            background: var(--lumo-contrast-10pct);
            color: var(--lumo-secondary-text-color);
        }
        .tag.truncated {
            background: var(--lumo-warning-color-10pct, var(--lumo-contrast-10pct));
            color: var(--lumo-warning-text-color, var(--lumo-body-text-color));
        }
        .writer {
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-xs);
        }
        pre {
            margin: var(--lumo-space-xs) 0 0;
            max-height: 10em;
            overflow: auto;
            white-space: pre-wrap;
            font-size: var(--lumo-font-size-xs);
        }
        .empty {
            color: var(--lumo-secondary-text-color);
        }
    `;

    static properties = {
        events: { type: Array },
    };

    render() {
        const { keys, last } = scopeState(this.events ?? []);
        if (keys.length === 0) {
            return html`<div class="empty">The scope is empty.</div>`;
        }
        return html`${keys.map(entry => html`
            <div class="entry ${entry.key === last ? 'last' : ''}" data-key=${entry.key}>
                <div class="key">${entry.key}
                    ${entry.source === 'input' ? html`<span class="tag">input</span>` : nothing}
                    ${entry.truncated ? html`<span class="tag truncated" data-truncated>truncated</span>` : nothing}
                </div>
                ${entry.nodeId ? html`<div class="writer">written by ${entry.nodeId.split('/').pop().split('#').pop()}</div>` : nothing}
                <pre>${typeof entry.value === 'string' ? entry.value : JSON.stringify(entry.value, null, 2)}</pre>
            </div>`)}`;
    }
}

customElements.define('qwc-agentic-scope-state', QwcAgenticScopeState);
