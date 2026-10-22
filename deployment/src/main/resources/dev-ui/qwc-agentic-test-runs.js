import { LitElement, html, css, nothing } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';
import '@vaadin/dialog';
import '@vaadin/integer-field';
import '@vaadin/progress-bar';
import '@vaadin/text-area';
import { dialogFooterRenderer, dialogRenderer } from '@vaadin/dialog/lit.js';
import './qwc-agentic-graph.js';
import './qwc-agentic-run-form.js';
import './qwc-agentic-run-compare.js';
import './qwc-agentic-timeline.js';
import './qwc-agentic-value.js';
import { reachableTools } from './qwc-agentic-run-panel.js';
import { errorText, formatDuration, indexNodes, LiveRun, simpleClass } from './qwc-agentic-run-model.js';

/** Runs a series may ask for (RunManager.MAX_SERIES_RUNS). */
export const MAX_RUNS = 50;
const SYSTEM = 'agentic-viewer:test-runs:system';
const COUNT = 'agentic-viewer:test-runs:count';
/** Shared with the run panel: the last inputs of a system. */
const LAST_INPUTS = 'agentic-viewer:last-inputs:';
const POLL_MILLIS = 500;

const session = {
    get(key) {
        try {
            const value = sessionStorage.getItem(key);
            return value === null ? undefined : JSON.parse(value);
        } catch (e) {
            return undefined;
        }
    },
    set(key, value) {
        try {
            sessionStorage.setItem(key, JSON.stringify(value));
        } catch (e) {
            // the session simply will not remember it
        }
    },
};

/** The middle value of a list of numbers (the lower one of the two middle ones). */
function median(values) {
    const sorted = [...values].sort((a, b) => a - b);
    return sorted[Math.floor((sorted.length - 1) / 2)];
}

/**
 * Groups the runs by a key, the largest group first (then the earliest run).
 *
 * @return [{ key, runs }]
 */
export function groupRuns(runs, keyOf) {
    const groups = new Map();
    runs.forEach(run => {
        const key = keyOf(run);
        if (!groups.has(key)) {
            groups.set(key, []);
        }
        groups.get(key).push(run);
    });
    return [...groups.entries()].map(([key, members]) => ({ key, runs: members }))
        .sort((a, b) => b.runs.length - a.runs.length || a.runs[0].index - b.runs[0].index);
}

/**
 * What the ended runs of a series have in common and where they differ: counts by status, duration and token spread,
 * distinct outputs (failures by their error) and distinct paths (the agents invoked, in order).
 */
export function seriesResults(series) {
    const ended = (series?.runs ?? []).filter(run => run.status !== 'RUNNING');
    const count = status => ended.filter(run => run.status === status).length;
    const durations = ended.map(run => run.durationMillis).filter(d => d != null);
    const tokens = ended.map(run => run.tokens ?? 0);
    const spread = values => values.length === 0 ? undefined
        : { min: Math.min(...values), median: median(values), max: Math.max(...values) };
    return {
        ended: ended.length,
        succeeded: count('SUCCEEDED'),
        failed: count('FAILED'),
        cancelled: count('CANCELLED'),
        duration: spread(durations),
        tokens: tokens.some(t => t > 0) ? spread(tokens) : undefined,
        outputs: groupRuns(ended, run => run.status === 'SUCCEEDED' ? `ok:${JSON.stringify(run.output ?? null)}`
            : `${run.status}:${run.error?.rootCauseClass ?? run.error?.exceptionClass ?? ''}:${run.error?.rootCauseMessage ?? run.error?.message ?? ''}`),
        paths: groupRuns(ended, run => (run.path ?? []).join('>')),
    };
}

/**
 * Agentic Test Runs: one agentic system run N times with the same inputs, one run after the other (a series), and how
 * the results differ: outputs, paths, durations and tokens. The run going on shows live on the graph (its human steps
 * are answered here); any run of the series replays on the graph and the timeline, two of them compare.
 */
export class QwcAgenticTestRuns extends LitElement {

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-m);
            padding: var(--lumo-space-m);
            height: 100%;
            box-sizing: border-box;
            overflow-y: auto;
            font-size: var(--lumo-font-size-s);
        }
        .header {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-m);
        }
        .header h3 {
            margin: 0;
        }
        select {
            font: inherit;
        }
        .top {
            flex: 0 0 auto;
            display: flex;
            gap: var(--lumo-space-m);
            height: 460px;
        }
        .config {
            flex: 0 0 380px;
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-s);
            overflow-y: auto;
            border: 1px solid var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-m);
            padding: var(--lumo-space-s) var(--lumo-space-m);
            box-sizing: border-box;
        }
        qwc-agentic-graph {
            flex: 1;
            min-width: 0;
        }
        .box {
            border: 1px solid var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-m);
            padding: var(--lumo-space-s) var(--lumo-space-m);
        }
        h4 {
            margin: 0;
        }
        h5 {
            margin: var(--lumo-space-m) 0 var(--lumo-space-xs);
            font-size: var(--lumo-font-size-s);
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--lumo-secondary-text-color);
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
        th {
            color: var(--lumo-secondary-text-color);
            font-weight: normal;
        }
        tr.selectable {
            cursor: pointer;
        }
        tr.selectable:hover, tr.current {
            background: var(--lumo-contrast-5pct);
        }
        .count {
            font-weight: bold;
            white-space: nowrap;
        }
        .value {
            max-height: 8em;
            overflow: auto;
        }
        .dot {
            display: inline-block;
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: var(--lumo-contrast-30pct);
        }
        .dot.SUCCEEDED {
            background: var(--lumo-success-color);
        }
        .dot.FAILED {
            background: var(--lumo-error-color);
        }
        .dot.RUNNING {
            background: var(--lumo-primary-color);
        }
        .stats {
            display: flex;
            flex-wrap: wrap;
            gap: var(--lumo-space-l);
        }
        .stat strong {
            display: block;
            font-size: var(--lumo-font-size-l);
        }
        .muted {
            color: var(--lumo-secondary-text-color);
        }
        .error {
            color: var(--lumo-error-text-color);
        }
        .actions {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: var(--lumo-space-s);
        }
        .human-input {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-xs);
            padding: var(--lumo-space-s);
            border: 2px dashed var(--lumo-primary-color);
            border-radius: var(--lumo-border-radius-m);
        }
        .past {
            display: flex;
            flex-direction: column;
        }
        .past-row {
            display: flex;
            gap: var(--lumo-space-s);
            align-items: center;
            padding: 2px var(--lumo-space-xs);
            border-radius: var(--lumo-border-radius-s);
            cursor: pointer;
        }
        .past-row:hover, .past-row.current {
            background: var(--lumo-contrast-5pct);
        }
    `;

    static properties = {
        _systems: { state: true },
        _systemId: { state: true },
        _topology: { state: true },
        _schema: { state: true },
        _error: { state: true },
        _count: { state: true },
        _pending: { state: true },
        _series: { state: true },
        _pastSeries: { state: true },
        /** the run of the series shown on the graph and the timeline (replayed, or the one going on) */
        _shownRun: { state: true },
        _events: { state: true },
        _waiting: { state: true },
        _answer: { state: true },
        _compare: { state: true },
        _comparing: { state: true },
    };

    jsonRpc = new JsonRpc(this);

    /** For qwc-agentic-run-compare: a plain object (the JsonRpc proxy cannot go through a Lit binding). */
    rpc = {
        getRun: params => this.jsonRpc.getRun(params),
    };

    constructor() {
        super();
        this._count = session.get(COUNT) ?? 5;
        this._compare = [];
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.listSystems()
            .then(response => {
                this._systems = response.result ?? [];
                const remembered = session.get(SYSTEM);
                const first = this._systems.find(s => s.id === remembered) ?? this._systems[0];
                if (first) {
                    this._select(first.id);
                }
            })
            .catch(error => this._error = `Cannot list the agentic systems: ${error?.error?.message ?? error?.message ?? error}`);
    }

    disconnectedCallback() {
        this._stopFollowing();
        super.disconnectedCallback();
    }

    get _graph() {
        return this.renderRoot.querySelector('qwc-agentic-graph');
    }

    _select(systemId) {
        this._stopFollowing();
        session.set(SYSTEM, systemId);
        this._systemId = systemId;
        this._topology = undefined;
        this._schema = undefined;
        this._series = undefined;
        this._pastSeries = undefined;
        this._shownRun = undefined;
        this._events = undefined;
        this._compare = [];
        this._error = undefined;
        this._lastInputs = session.get(LAST_INPUTS + systemId);
        // every response is for the system selected when asked: a late one for another system is ignored
        const current = () => this._systemId === systemId;
        this.jsonRpc.getTopology({ systemId })
            .then(response => {
                if (current()) {
                    this._topology = { ...response.result, contributions: {} };
                }
            })
            .catch(error => {
                if (current()) {
                    this._error = `Cannot load the system: ${error?.error?.message ?? error?.message ?? error}`;
                }
            });
        this.jsonRpc.getInputSchema({ systemId })
            .then(response => {
                if (current()) {
                    this._schema = response.result;
                }
            })
            .catch(error => {
                if (current()) {
                    this._error = `Cannot load the inputs: ${error?.error?.message ?? error?.message ?? error}`;
                }
            });
        this._loadPastSeries();
    }

    _loadPastSeries() {
        const systemId = this._systemId;
        this.jsonRpc.listSeries({ systemId })
            .then(response => {
                if (this._systemId === systemId) {
                    this._pastSeries = response.result ?? [];
                }
            })
            .catch(() => {
                if (this._systemId === systemId) {
                    this._pastSeries = [];
                }
            });
    }

    render() {
        if (this._error && !this._systems) {
            return html`<div class="error">${this._error}</div>`;
        }
        if (!this._systems) {
            return html`<div class="muted">Loading the agentic systems...</div>`;
        }
        if (this._systems.length === 0) {
            return html`<div class="muted">No agentic system in this application.</div>`;
        }
        return html`
            <div class="header">
                <h3>Run a system several times</h3>
                <select data-test-system title="System" @change=${e => this._select(e.target.value)}>
                    ${this._systems.map(s => html`<option value=${s.id} ?selected=${s.id === this._systemId}>${s.name}</option>`)}
                </select>
                <span class="muted">the same inputs, one run after the other: see how the results differ</span>
            </div>
            ${this._error ? html`<div class="error">${this._error}</div>` : nothing}
            <div class="top">
                <div class="config">${this._renderConfig()}</div>
                ${this._topology ? html`<qwc-agentic-graph .system=${this._topology}></qwc-agentic-graph>`
                    : html`<div class="muted">Loading the topology...</div>`}
            </div>
            ${this._series ? this._renderResults(this._series) : nothing}
            ${this._events ? html`<div class="box">
                <h4 data-shown-run>Run #${this._shownRun?.index ?? '?'}</h4>
                <qwc-agentic-timeline .events=${this._events} .system=${this._topology}
                        .running=${this._shownRun?.status === 'RUNNING'}></qwc-agentic-timeline></div>` : nothing}
            <vaadin-dialog header-title="Run ${this._topology?.name ?? ''} ${this._pending?.count} times?" .opened=${!!this._pending}
                    @opened-changed=${e => { if (!e.detail.value) { this._pending = undefined; } }}
                    ${dialogRenderer(() => this._renderConfirmation(), [this._pending, this._topology])}
                    ${dialogFooterRenderer(() => html`
                        <vaadin-button data-confirm="cancel" @click=${() => this._pending = undefined}>Cancel</vaadin-button>
                        <vaadin-button data-confirm="run" theme="primary" @click=${this._confirm}>Start the ${this._pending?.count} runs</vaadin-button>`,
                    [this._pending])}></vaadin-dialog>
            <vaadin-dialog modeless draggable resizable header-title="Compare runs" data-compare-dialog width="80vw" height="75vh"
                    .opened=${!!this._comparing}
                    @opened-changed=${e => { if (!e.detail.value && this._comparing) { this._endCompare(); } }}
                    ${dialogRenderer(() => this._comparing ? html`<qwc-agentic-run-compare .system=${this._topology}
                            .runIds=${this._comparing} .rpc=${this.rpc} .graph=${this._graph}
                            @close=${() => this._endCompare()}></qwc-agentic-run-compare>` : '',
                    [this._comparing, this._topology])}></vaadin-dialog>`;
    }

    _renderConfig() {
        if (!this._schema) {
            return html`<div class="muted">Loading the inputs...</div>`;
        }
        const running = this._series?.status === 'RUNNING';
        const validCount = Number.isInteger(this._count) && this._count >= 1 && this._count <= MAX_RUNS;
        return html`
            <vaadin-integer-field data-series-count label="Runs" min="1" max=${MAX_RUNS} step-buttons-visible
                    .value=${String(this._count ?? '')} ?disabled=${running}
                    .invalid=${!validCount} error-message="From 1 to ${MAX_RUNS}"
                    @value-changed=${e => { this._count = e.detail.value === '' ? undefined : Number(e.detail.value); }}></vaadin-integer-field>
            ${running ? html`<div class="muted">Running: the inputs cannot change meanwhile</div>`
                : html`<qwc-agentic-run-form .schema=${this._schema} .values=${this._lastInputs}
                        .submitLabel=${`Run ${validCount ? this._count : "N"} times`}
                        @run=${e => this._request(e.detail.inputs)}></qwc-agentic-run-form>`}
            ${this._series ? this._renderProgress(this._series) : nothing}
            ${this._renderPastSeries()}`;
    }

    _renderProgress(series) {
        const started = series.runs.length;
        const ended = series.runs.filter(run => run.status !== 'RUNNING').length;
        const running = series.status === 'RUNNING';
        const label = { RUNNING: `Run ${Math.max(started, 1)} of ${series.count}`, SUCCEEDED: `${ended} runs done`,
            CANCELLED: `Stopped after ${ended} of ${series.count} runs`, FAILED: 'Could not go on' }[series.status];
        return html`
            <div data-series-status=${series.status}>
                <strong>${label}</strong>
                ${series.durationMillis != null ? html`<span class="muted"> in ${formatDuration(series.durationMillis)}</span>` : nothing}
            </div>
            ${running ? html`<vaadin-progress-bar data-series-progress min="0" max=${series.count} .value=${ended}></vaadin-progress-bar>
                <div class="actions"><vaadin-button theme="error tertiary" data-action="stop-series" @click=${this._stop}>Stop</vaadin-button></div>`
                : nothing}
            ${series.error ? html`<div class="error">${series.error}</div>` : nothing}
            ${running && this._waiting ? this._renderHumanInput(this._waiting) : nothing}`;
    }

    _renderHumanInput(waiting) {
        return html`<div class="human-input" data-human-input=${waiting.nodeId}>
            <strong data-human-prompt>${waiting.prompt ?? 'A step needs your input'}</strong>
            <span class="muted">Run #${this._shownRun?.index ?? ''}${waiting.expectedKey ? html` writes <code>${waiting.expectedKey}</code>` : nothing}</span>
            <vaadin-text-area data-human-answer label="Answer" .value=${this._answer ?? ''}
                    @value-changed=${e => this._answer = e.detail.value}></vaadin-text-area>
            <div class="actions"><vaadin-button theme="primary" data-action="answer" @click=${this._sendAnswer}>Send</vaadin-button></div>
        </div>`;
    }

    _renderPastSeries() {
        if (!this._pastSeries || this._pastSeries.length === 0) {
            return nothing;
        }
        return html`<h5>Recent series</h5>
            <div class="past">${this._pastSeries.map(past => html`
                <div class="past-row ${past.seriesId === this._series?.seriesId ? 'current' : ''}" data-past-series=${past.seriesId}
                        title="Open this series" @click=${() => this._open(past.seriesId)}>
                    <span class="dot ${past.status === 'SUCCEEDED' ? 'SUCCEEDED' : past.status}"></span>
                    <span>${new Date(past.startedAt).toLocaleTimeString()} · ${past.started}/${past.count} runs · ${past.status.toLowerCase()}</span>
                </div>`)}</div>`;
    }

    _renderConfirmation() {
        const tools = this._topology ? reachableTools(this._topology) : [];
        const count = this._pending?.count;
        return html`<div class="tools-confirmation" style="max-width: 32em">
            <p>This starts <strong>${count} runs</strong> of the system, one after the other, for real: its models are called
                and its tools run ${count} times, with their side effects and cost.</p>
            ${tools.length === 0 ? html`<p>No tool is reachable from this system.</p>` : html`
                <p>Tools reachable from this system:</p>
                <ul>${tools.map(tool => html`<li><strong>${tool.name}</strong> <span style="opacity: 0.7">${tool.detail}</span></li>`)}</ul>`}
        </div>`;
    }

    _renderResults(series) {
        const results = seriesResults(series);
        const names = this._topology ? indexNodes(this._topology.root) : new Map();
        const pathOf = new Map();
        results.paths.forEach((group, i) => group.runs.forEach(run => pathOf.set(run.index, i + 1)));
        const range = spread => spread ? `${spread.min} · ${spread.median} · ${spread.max}` : '—';
        const durations = spread => spread ? `${formatDuration(spread.min)} · ${formatDuration(spread.median)} · ${formatDuration(spread.max)}` : '—';
        return html`<div class="box" data-series-results>
            <h4>Results</h4>
            <div class="stats" data-series-summary>
                <div class="stat"><strong>${results.succeeded}/${results.ended}</strong>succeeded</div>
                ${results.failed ? html`<div class="stat"><strong class="error">${results.failed}</strong>failed</div>` : nothing}
                ${results.cancelled ? html`<div class="stat"><strong>${results.cancelled}</strong>cancelled</div>` : nothing}
                <div class="stat"><strong>${durations(results.duration)}</strong>duration (min · median · max)</div>
                <div class="stat"><strong>${range(results.tokens)}</strong>tokens (min · median · max)</div>
                <div class="stat"><strong data-distinct-output-count>${results.outputs.length}</strong>distinct output${results.outputs.length === 1 ? '' : 's'}</div>
                <div class="stat"><strong data-distinct-path-count>${results.paths.length}</strong>distinct path${results.paths.length === 1 ? '' : 's'}</div>
            </div>
            <h5>Outputs</h5>
            <table data-distinct-outputs>
                <colgroup><col style="width: 6em"><col><col style="width: 14em"></colgroup>
                ${results.outputs.map(group => {
                    const first = group.runs[0];
                    return html`<tr class="selectable" data-output-group=${group.runs.length} @click=${() => this._show(first)}>
                        <td class="count">${group.runs.length}×</td>
                        <td>${first.status === 'SUCCEEDED' ? html`<div class="value"><qwc-agentic-value .value=${first.output}></qwc-agentic-value></div>`
                            : html`<span class="error">${first.status.toLowerCase()}: ${simpleClass(first.error?.rootCauseClass ?? first.error?.exceptionClass)}
                                ${first.error?.rootCauseMessage ?? first.error?.message ?? ''}</span>`}</td>
                        <td class="muted">runs ${group.runs.map(run => `#${run.index}`).join(', ')}</td>
                    </tr>`;
                })}
            </table>
            <h5>Paths <span class="muted">· the agents each run invoked, in order</span></h5>
            <table data-distinct-paths>
                <colgroup><col style="width: 6em"><col style="width: 4em"><col><col style="width: 14em"></colgroup>
                ${results.paths.map((group, i) => html`<tr class="selectable" data-path-group=${group.runs.length} @click=${() => this._show(group.runs[0])}>
                    <td class="count">${group.runs.length}×</td>
                    <td class="muted">P${i + 1}</td>
                    <td>${(group.runs[0].path ?? []).filter(id => id !== this._topology?.root?.id) // the root starts every run
                        .map(id => names.get(id)?.name ?? id.split('/').pop()).join(' → ') || html`<span class="muted">no agent</span>`}</td>
                    <td class="muted">runs ${group.runs.map(run => `#${run.index}`).join(', ')}</td>
                </tr>`)}
            </table>
            <h5>Runs</h5>
            <table data-series-runs>
                <colgroup><col style="width: 2em"><col style="width: 3em"><col style="width: 7em"><col style="width: 6em"><col style="width: 5em"><col><col style="width: 4em"></colgroup>
                <tr><th></th><th>#</th><th>Status</th><th>Duration</th><th>Tokens</th><th>Output</th><th>Path</th></tr>
                ${series.runs.map(run => html`<tr class="selectable ${run.runId === this._shownRun?.runId ? 'current' : ''}"
                        data-series-run=${run.index} title="Show this run on the graph" @click=${() => this._show(run)}>
                    <td><input type="checkbox" data-compare-run=${run.index} title="Select to compare"
                            ?disabled=${run.status === 'RUNNING'} .checked=${this._compare.includes(run.runId)}
                            @click=${e => e.stopPropagation()} @change=${e => this._toggleCompare(run.runId, e.target.checked)}></td>
                    <td>${run.index}</td>
                    <td><span class="dot ${run.status}"></span> ${run.status.toLowerCase()}</td>
                    <td>${run.durationMillis != null ? formatDuration(run.durationMillis) : '…'}</td>
                    <td>${run.tokens ?? 0}</td>
                    <td>${run.status === 'SUCCEEDED' ? html`<div class="value"><qwc-agentic-value .value=${run.output}></qwc-agentic-value></div>`
                        : run.error ? html`<span class="error">${errorText(run.error).split('\n')[0]}</span>` : nothing}</td>
                    <td class="muted">${pathOf.has(run.index) ? `P${pathOf.get(run.index)}` : ''}</td>
                </tr>`)}
            </table>
            <div class="actions">
                ${this._compare.length === 2 ? html`<vaadin-button theme="tertiary" data-action="compare-series-runs"
                        @click=${this._startCompare}>Compare the 2 runs</vaadin-button>`
                    : html`<span class="muted">Tick two runs to compare them</span>`}
            </div>
        </div>`;
    }

    _request(inputs) {
        if (!(Number.isInteger(this._count) && this._count >= 1 && this._count <= MAX_RUNS)) {
            return;
        }
        session.set(LAST_INPUTS + this._systemId, inputs);
        session.set(COUNT, this._count);
        this._lastInputs = inputs;
        this._pending = { inputs, count: this._count };
    }

    _confirm() {
        const pending = this._pending;
        this._pending = undefined;
        if (!pending) {
            return;
        }
        const systemId = this._systemId;
        this.jsonRpc.startSeries({ systemId, inputs: pending.inputs, count: pending.count })
            .then(response => {
                if (this._systemId === systemId) {
                    this._open(response.result);
                    this._loadPastSeries();
                }
            })
            .catch(error => {
                if (this._systemId === systemId) {
                    this._error = `Cannot start the series: ${error?.error?.message ?? error?.message ?? error}`;
                }
            });
    }

    _stop() {
        if (this._series?.seriesId) {
            this.jsonRpc.cancelSeries({ seriesId: this._series.seriesId });
        }
    }

    /** Opens a series: its results, refreshed while it runs, with the run going on followed live on the graph. */
    _open(seriesId) {
        this._stopFollowing();
        this._error = undefined;
        this._compare = [];
        this._shownRun = undefined;
        this._events = undefined;
        this._graph?.resetRun();
        const following = (this._following ?? 0) + 1;
        this._following = following;
        const poll = () => this.jsonRpc.getSeries({ seriesId })
            .then(response => {
                if (this._following !== following) {
                    return;
                }
                const series = response.result;
                this._series = series;
                const current = series.runs[series.runs.length - 1];
                if (series.status === 'RUNNING' && current?.status === 'RUNNING' && current.runId !== this._liveRunId) {
                    this._followRun(current);
                }
                if (series.status === 'RUNNING') {
                    this._pollTimer = setTimeout(poll, POLL_MILLIS);
                } else {
                    this._loadPastSeries();
                }
            })
            .catch(error => {
                if (this._following === following) {
                    this._error = `Cannot follow the series: ${error?.error?.message ?? error?.message ?? error}`;
                }
            });
        poll();
    }

    _stopFollowing() {
        this._following = (this._following ?? 0) + 1;
        clearTimeout(this._pollTimer);
        this._stopStream();
    }

    _stopStream() {
        this._observer?.cancel?.();
        this._observer = undefined;
        this._liveRunId = undefined;
        this._waiting = undefined;
    }

    /** Shows the run going on live on the graph and the timeline, and its human steps waiting for an answer. */
    _followRun(run) {
        this._stopStream();
        this._liveRunId = run.runId;
        this._graph?.resetRun();
        const live = new LiveRun(this._topology, this._graph);
        this._live = live;
        this._shownRun = run;
        this._events = [];
        const events = [];
        this._observer = this.jsonRpc.streamRun({ runId: run.runId }).onNext(response => {
            const event = response.result;
            if (!event || this._live !== live) {
                return;
            }
            live.apply(event);
            events.push(event);
            this._events = [...events];
            this._waiting = live.pendingInput();
            if (event.type === 'RunFinished' || event.type === 'RunFailed') {
                this._waiting = undefined;
            }
        });
    }

    /** Replays a run of the series on the graph and the timeline. */
    _show(run) {
        if (run.status === 'RUNNING' && run.runId === this._liveRunId) {
            return; // already followed live
        }
        if (this._series?.status === 'RUNNING') {
            this._stopStream(); // looking at another run: the one going on is no longer followed
        }
        this._graph?.resetRun();
        const live = new LiveRun(this._topology, this._graph);
        this._live = live;
        this._shownRun = run;
        this.jsonRpc.getRun({ runId: run.runId }).then(response => {
            if (this._live !== live || !response.result) {
                return;
            }
            response.result.events.forEach(event => live.apply(event));
            this._events = response.result.events;
        }).catch(error => {
            if (this._live === live) {
                this._error = `Cannot open run #${run.index}: ${error?.error?.message ?? error?.message ?? error}`;
            }
        });
    }

    _sendAnswer() {
        const runId = this._liveRunId;
        if (!runId) {
            return;
        }
        this.jsonRpc.provideHumanInput({ runId, value: this._answer ?? '' })
            .then(() => this._answer = undefined)
            .catch(error => {
                if (this._liveRunId === runId) {
                    this._error = error?.error?.message ?? error?.message ?? String(error);
                }
            });
    }

    _toggleCompare(runId, checked) {
        const others = this._compare.filter(id => id !== runId);
        this._compare = checked ? [...others.slice(-1), runId] : others;
    }

    _startCompare() {
        const index = runId => this._series.runs.find(run => run.runId === runId)?.index ?? 0;
        this._comparing = [...this._compare].sort((a, b) => index(a) - index(b));
    }

    _endCompare() {
        this._comparing = undefined;
        this._graph?.resetRun();
    }
}

customElements.define('qwc-agentic-test-runs', QwcAgenticTestRuns);
