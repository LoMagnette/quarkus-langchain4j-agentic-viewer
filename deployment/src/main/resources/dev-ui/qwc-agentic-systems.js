import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';

/**
 * Agentic Systems - lists the agentic systems of the application.
 * Data comes from the JSON-RPC service in the runtime-dev module.
 */
export class QwcAgenticSystems extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: block;
            padding: 1rem;
        }

        .stat-value {
            font-size: 1.5rem;
            font-weight: bold;
            color: var(--lumo-primary-color);
        }

        .stat-label {
            font-size: 0.85rem;
            color: var(--lumo-secondary-text-color);
        }

        .error {
            color: var(--lumo-error-text-color);
        }
    `;

    static properties = {
        _agentCount: { state: true },
        _error: { state: true },
    };

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getAgentCount()
            .then(response => {
                this._agentCount = response.result;
            })
            .catch(error => {
                this._error = error?.message || String(error);
            });
    }

    render() {
        if (this._error) {
            return html`<div class="error">Unable to load agentic systems: ${this._error}</div>`;
        }
        if (this._agentCount === undefined) {
            return html`<div class="stat-label">Loading agentic systems...</div>`;
        }
        return html`
            <div class="stat-value">${this._agentCount}</div>
            <div class="stat-label">agents discovered</div>
        `;
    }
}

customElements.define('qwc-agentic-systems', QwcAgenticSystems);
