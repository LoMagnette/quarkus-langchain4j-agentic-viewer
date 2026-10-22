import { LitElement, html, css, nothing } from 'lit';
import '@vaadin/button';
import './qwc-agentic-value.js';
import { compareRuns, formatDuration } from './qwc-agentic-run-model.js';

const VERDICTS = {
    same: 'same', maybe: 'may differ (truncated)', changed: 'changed', onlyA: 'only in A', onlyB: 'only in B',
};
/** Graph state per verdict (qwc-agentic-graph). */
const GRAPH_STATES = { same: 'diff-same', maybe: 'diff-same', changed: 'diff-changed', onlyA: 'diff-only', onlyB: 'diff-only' };

/**
 * Comparison of two runs of a system (A, the older, and B): inputs, totals, and each agent invocation matched across
 * them with what changed; the graph is colored by node. Shown in a large dialog (the page's): A and B side by side.
 * Properties: system, runIds ([a, b]), traceA (optional), rpc (getRun), graph.
 * Fires 'close'.
 */
export class QwcAgenticRunCompare extends LitElement {

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-s);
            font-size: var(--lumo-font-size-s);
        }
        .head {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
        }
        .head h4 {
            margin: 0;
            flex: 1;
        }
        table {
            border-collapse: collapse;
            width: 100%;
            table-layout: fixed;
        }
        th, td {
            text-align: left;
            vertical-align: top;
            padding: 2px var(--lumo-space-xs);
            border-bottom: 1px solid var(--lumo-contrast-10pct);
            overflow-wrap: anywhere;
        }
        col.name {
            width: 22%;
        }
        col.verdict {
            width: 11em;
        }
        /* totals and inputs: A and B line up with the agents' columns */
        col.label {
            width: calc(22% + 11em);
        }
        .run-label {
            font-weight: bold;
        }
        .origin {
            margin-left: var(--lumo-space-xs);
            padding: 0 var(--lumo-space-xs);
            border-radius: var(--lumo-border-radius-s);
            background: var(--lumo-contrast-10pct);
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-xxs);
            font-weight: normal;
            text-transform: uppercase;
        }
        h5 {
            margin: var(--lumo-space-s) 0 0;
            font-size: var(--lumo-font-size-s);
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--lumo-secondary-text-color);
        }
        th {
            color: var(--lumo-secondary-text-color);
            font-weight: normal;
        }
        .verdict {
            white-space: nowrap;
            border-radius: var(--lumo-border-radius-s);
            padding: 0 var(--lumo-space-xs);
        }
        .verdict.changed {
            background: rgba(245, 158, 11, 0.25);
        }
        .verdict.onlyA, .verdict.onlyB {
            background: var(--lumo-contrast-10pct);
        }
        .verdict.maybe {
            background: var(--lumo-primary-color-10pct);
        }
        .differs {
            font-weight: bold;
        }
        .muted {
            color: var(--lumo-secondary-text-color);
        }
        .error {
            color: var(--lumo-error-text-color);
        }
    `;

    static properties = {
        system: { type: Object },
        runIds: { type: Array },
        /** run A's trace when the server no longer has it (a run kept in the page across a reload) */
        traceA: { attribute: false },
        rpc: { attribute: false },
        graph: { attribute: false },
        _result: { state: true },
        _error: { state: true },
    };

    willUpdate(changed) {
        if (changed.has('runIds') && this.runIds?.length === 2 && this.rpc) {
            const runIds = this.runIds;
            this._result = undefined;
            this._error = undefined;
            const traceA = this.traceA;
            Promise.all(runIds.map((runId, i) => i === 0 && traceA ? Promise.resolve(traceA)
                    : this.rpc.getRun({ runId }).then(response => response.result)))
                .then(([a, b]) => {
                    if (this.runIds === runIds) {
                        this._result = compareRuns(a, b);
                        this._color();
                    }
                })
                .catch(error => {
                    if (this.runIds === runIds) {
                        this._error = `Cannot compare the runs: ${error?.error?.message ?? error?.message ?? error}`;
                    }
                });
        }
    }

    /** Colors the graph by node: same, changed, or run by one of the runs only. */
    _color() {
        this.graph?.resetRun();
        this._result?.nodes.forEach((verdict, nodeId) => this.graph?.highlight(nodeId, GRAPH_STATES[verdict]));
    }

    _close() {
        this.graph?.resetRun();
        this.dispatchEvent(new CustomEvent('close'));
    }

    render() {
        const result = this._result;
        return html`
            <div class="head">
                <h4>${result ? html`${this._label('A', result.a)} vs ${this._label('B', result.b)}` : 'A vs B'}</h4>
                <vaadin-button theme="tertiary small" data-action="close-compare" @click=${this._close}>Close</vaadin-button>
            </div>
            ${this._error ? html`<div class="error">${this._error}</div>`
                : !result ? html`<div class="muted">Loading the runs...</div>` : this._renderResult(result)}`;
    }

    _renderResult(result) {
        const changed = result.rows.filter(row => row.verdict !== 'same').length;
        return html`
            <table data-compare-totals>
                <colgroup><col class="label"><col><col></colgroup>
                <tr><th></th><th class="run-label">A</th><th class="run-label">B</th></tr>
                <tr><th>Status</th><td>${result.a.status?.toLowerCase()}</td><td>${result.b.status?.toLowerCase()}</td></tr>
                <tr><th>Duration</th><td>${this._duration(result.a.duration)}</td><td>${this._duration(result.b.duration)}</td></tr>
                <tr><th>Tokens</th><td>${result.a.tokens}</td><td>${result.b.tokens}</td></tr>
            </table>
            <h5>Inputs</h5>
            <table data-compare-inputs>
                <colgroup><col class="label"><col><col></colgroup>
                ${result.inputs.map(input => html`
                    <tr class=${input.same ? '' : 'differs'} data-input=${input.name}>
                        <th>${input.name}</th>
                        <td><qwc-agentic-value .value=${input.a}></qwc-agentic-value></td>
                        <td><qwc-agentic-value .value=${input.b}></qwc-agentic-value></td>
                    </tr>`)}
            </table>
            <h5>Agents <span class="muted">· ${changed === 0 ? 'no difference' : `${changed} difference${changed === 1 ? '' : 's'}`}</span></h5>
            <table data-compare-rows>
                <colgroup><col class="name"><col class="verdict"><col><col></colgroup>
                <tr><th>Agent</th><th></th><th class="run-label">A</th><th class="run-label">B</th></tr>
                ${result.rows.map(row => html`
                    <tr data-compare-row=${row.key} data-node=${row.nodeId} data-verdict=${row.verdict}>
                        <td style="padding-left: ${row.depth * 12 + 4}px">${row.name}${this._where(row)}</td>
                        <td><span class="verdict ${row.verdict}">${VERDICTS[row.verdict]}</span></td>
                        <td>${this._side(row.a)}</td>
                        <td>${this._side(row.b)}</td>
                    </tr>`)}
            </table>`;
    }

    /** A run as named in the title: its letter, start time and origin. */
    _label(letter, run) {
        return html`<span class="run-label">${letter}</span>${run.startedAt ? ` (${new Date(run.startedAt).toLocaleTimeString()})` : ''}${
            run.origin === 'APPLICATION' ? html`<span class="origin">app</span>` : nothing}`;
    }

    _where(row) {
        const invocation = row.a ?? row.b;
        const parts = [];
        if (invocation.iteration != null) {
            parts.push(`iteration ${invocation.iteration + 1}`);
        }
        if (invocation.itemIndex != null) {
            parts.push(`item ${invocation.itemIndex + 1}`);
        }
        return parts.length ? html` <span class="muted">${parts.join(' · ')}</span>` : nothing;
    }

    _side(invocation) {
        if (!invocation) {
            return html`<span class="muted">—</span>`;
        }
        return html`<qwc-agentic-value .value=${invocation.output}></qwc-agentic-value>
            <div class="muted">${invocation.status === 'failed' ? 'failed · ' : ''}${this._duration(invocation.duration)}${
                invocation.tokens ? ` · ${invocation.tokens} tokens` : ''}</div>`;
    }

    _duration(millis) {
        return millis == null ? '—' : formatDuration(millis);
    }
}

customElements.define('qwc-agentic-run-compare', QwcAgenticRunCompare);
