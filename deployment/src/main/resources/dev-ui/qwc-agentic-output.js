import { LitElement, html, css } from 'lit';
import './qwc-agentic-value.js';

/**
 * The Output tab: the result of the run shown (current or replayed), from its events, or its error.
 */
export class QwcAgenticOutput extends LitElement {

    static styles = css`
        :host {
            display: block;
        }
        .muted {
            color: var(--lumo-secondary-text-color);
        }
        .error {
            color: var(--lumo-error-text-color);
            white-space: pre-wrap;
            overflow-wrap: anywhere;
        }
        h5 {
            margin: 0 0 var(--lumo-space-xs);
            font-size: var(--lumo-font-size-xs);
            text-transform: uppercase;
            color: var(--lumo-secondary-text-color);
        }
    `;

    static properties = {
        events: { type: Array },
    };

    render() {
        const events = this.events ?? [];
        const finished = events.find(e => e.type === 'RunFinished');
        const failed = events.find(e => e.type === 'RunFailed');
        if (finished) {
            return html`<qwc-agentic-value data-run-output .value=${finished.output}></qwc-agentic-value>`;
        }
        if (failed) {
            return html`<h5>Error</h5>
                <div class="error" data-run-error>${failed.error?.message ?? failed.error?.exceptionClass ?? 'unknown error'}</div>`;
        }
        return html`<div class="muted">The output appears when the run ends.</div>`;
    }
}

customElements.define('qwc-agentic-output', QwcAgenticOutput);
