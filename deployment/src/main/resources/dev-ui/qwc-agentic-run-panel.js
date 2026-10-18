import { LitElement, html, css, nothing } from 'lit';
import '@vaadin/button';
import '@vaadin/dialog';
import '@vaadin/progress-bar';
import '@vaadin/text-area';
import { dialogFooterRenderer, dialogRenderer } from '@vaadin/dialog/lit.js';
import { connectionState } from 'connection-state';
import './qwc-agentic-run-form.js';
import { errorText, formatDuration, LiveRun, shortRef, simpleClass, simpleType } from './qwc-agentic-run-model.js';

const LAST_INPUTS = 'agentic-viewer:last-inputs:';
/** Answer types the server takes as typed: anything else is parsed as JSON (RunManager.provideHumanInput). */
const TEXT_ANSWERS = ['java.lang.String', 'java.lang.Object', 'java.lang.CharSequence'];
const CONFIRMED = 'agentic-viewer:tools-confirmed:';
const WATCH = 'agentic-viewer:rerun-after-reload:';
/** Attempts (500 ms apart) to find the system again after a reload before giving up. */
const RELOAD_ATTEMPTS = 40;

/** Browser session storage, which may be unavailable (private mode, blocked site data). */
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

/**
 * Everything a run of the system can reach outside the JVM: tools of its AI agents, tools only known at runtime,
 * MCP tools and A2A servers.
 */
export function reachableTools(system) {
    const tools = [];
    const add = entry => {
        if (!tools.some(t => t.name === entry.name && t.detail === entry.detail)) {
            tools.push(entry);
        }
    };
    const visit = node => {
        node.aiDetails?.tools?.forEach(tool => add({ name: tool.name, detail: shortRef(`${tool.declaringClass}#${tool.methodName}`) }));
        node.aiDetails?.runtimeToolSources?.forEach(source => add({
            name: source.startsWith('mcp:') ? `MCP tools (${source.substring(4)})` : 'tools supplied at runtime',
            detail: source.startsWith('mcp:') ? node.name : shortRef(source),
        }));
        if (node.kind === 'MCP_CLIENT_AGENT') {
            add({ name: `MCP tool ${node.remoteDetails?.toolName ?? '?'}`, detail: node.name });
        }
        if (node.kind === 'A2A_CLIENT_AGENT') {
            add({ name: 'remote A2A agent', detail: node.remoteDetails?.a2aServerUrl ?? shortRef(node.remoteDetails?.serverUrlSupplier) ?? node.name });
        }
        node.children?.forEach(visit);
    };
    visit(system.root);
    return tools;
}

/**
 * Run panel of a system: the run form, the confirmation of the reachable tools before the first run of the session,
 * then the live view of the run, which also highlights the graph as events arrive. Fires 'run-updated' (detail:
 * { events, running }) as the run progresses.
 * Properties: system (topology), jsonRpc (the page's client), graph (the qwc-agentic-graph element).
 */
export class QwcAgenticRunPanel extends LitElement {

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-s);
            font-size: var(--lumo-font-size-s);
        }
        .status {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
            font-weight: bold;
        }
        .status.running {
            color: var(--lumo-primary-text-color);
        }
        .status.succeeded {
            color: var(--lumo-success-text-color);
        }
        .status.failed {
            color: var(--lumo-error-text-color);
        }
        .status.cancelled, .status.lost {
            color: var(--lumo-secondary-text-color);
        }
        .watch {
            margin-top: var(--lumo-space-m);
            font-size: var(--lumo-font-size-s);
        }
        .history {
            display: flex;
            flex-direction: column;
            margin-top: var(--lumo-space-m);
        }
        .history-row {
            display: grid;
            grid-template-columns: 12px 1fr auto auto;
            align-items: center;
            gap: var(--lumo-space-s);
            padding: 2px var(--lumo-space-xs);
            border-radius: var(--lumo-border-radius-s);
            cursor: pointer;
        }
        .history-row:hover, .history-row.current {
            background: var(--lumo-contrast-5pct);
        }
        .dot {
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
        pre {
            margin: 0;
            padding: var(--lumo-space-s);
            max-height: 16em;
            overflow: auto;
            white-space: pre-wrap;
            border-radius: var(--lumo-border-radius-s);
            background: var(--lumo-contrast-5pct);
            font-size: var(--lumo-font-size-xs);
        }
        h5 {
            margin: var(--lumo-space-s) 0 0;
            font-size: var(--lumo-font-size-s);
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--lumo-secondary-text-color);
        }
        .actions {
            display: flex;
            flex-wrap: wrap;
            gap: var(--lumo-space-s);
        }
        .muted {
            color: var(--lumo-secondary-text-color);
        }
        .error {
            color: var(--lumo-error-text-color);
        }
        .human-input {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-xs);
            padding: var(--lumo-space-s);
            border: 2px dashed var(--lumo-primary-color);
            border-radius: var(--lumo-border-radius-m);
        }
        .human-input .prompt {
            font-weight: bold;
        }
        .human-input vaadin-text-area {
            width: 100%;
        }
    `;

    static properties = {
        system: { type: Object },
        jsonRpc: { attribute: false },
        graph: { attribute: false },
        _schema: { state: true },
        _schemaError: { state: true },
        _mode: { state: true },
        _pending: { state: true },
        _run: { state: true },
        _history: { state: true },
        /** the AwaitingHumanInput event of the step the run waits on, if any */
        _waiting: { state: true },
        _answer: { state: true },
        _answerError: { state: true },
        _answering: { state: true },
        /** goal-oriented planners' previewed paths for the inputs filled in the form: planner node id -> [node ids] */
        _preview: { state: true },
        /** run ids selected in the history to compare (two at most) */
        _compare: { state: true },
        /** whether the last inputs are run again after each hot reload (opt-in, per system) */
        _watch: { state: true },
        /** what the re-run after a reload did or why it did not run */
        _watchMessage: { state: true },
    };

    constructor() {
        super();
        this._mode = 'form';
        this._compare = [];
    }

    willUpdate(changed) {
        if (changed.has('system') && this.system) {
            this._stopStream();
            this._mode = 'form';
            this._run = undefined;
            this._schema = undefined;
            this._schemaError = undefined;
            this._history = undefined;
            this._preview = undefined;
            this._compare = [];
            this._watch = !!session.get(WATCH + this.system.id);
            this._watchMessage = undefined;
            this._beforeReload = undefined;
            // parsed once: the form resets its fields when given other values
            this._lastInputs = session.get(LAST_INPUTS + this.system.id);
            const systemId = this.system.id;
            this._loadHistory();
            this.jsonRpc?.getInputSchema({ systemId })
                .then(response => {
                    if (this.system?.id === systemId) {
                        this._schema = response.result;
                    }
                })
                .catch(error => {
                    if (this.system?.id === systemId) {
                        this._schemaError = `Cannot load the inputs: ${error?.error?.message ?? error?.message ?? error}`;
                    }
                });
        }
    }

    connectedCallback() {
        super.connectedCallback();
        // the stream of a run does not survive a disconnect or a hot reload, and nothing tells its observer
        this._connectionObserver = () => {
            const connection = connectionState.current;
            if ((connection?.isDisconnected || connection?.isHotreloading) && this._observer) {
                this._lost(connection.isHotreloading ? 'The application reloaded: its runs were stopped'
                    : 'Disconnected from the application: the run may go on, open it again from the history');
            }
            if (connection?.isHotreloading) {
                // the client sets 'connected' right after: re-run once the reload is over
                setTimeout(() => this._afterReload(), 0);
            }
        };
        connectionState.addObserver(this._connectionObserver, ['current']);
    }

    disconnectedCallback() {
        connectionState.removeObserver(this._connectionObserver);
        this._stopStream();
        super.disconnectedCallback();
    }

    /** A new user action (run, open a past run): responses to earlier ones are ignored. */
    _newAction() {
        this._action = (this._action ?? 0) + 1;
        return this._action;
    }

    /** The events of the followed run can no longer arrive: the live view ends, with why. */
    _lost(message) {
        this._stopStream();
        this._live?.interrupt();
        this._waiting = undefined;
        this._run = { ...this._run, status: 'lost', error: { message } };
        this._publish(false);
        this._loadHistory();
    }

    render() {
        return html`
            ${this._mode === 'live' && this._run ? this._renderLive() : this._renderForm()}
            ${this._renderWatch()}
            ${this._renderHistory()}
            <vaadin-dialog header-title="Run ${this.system?.name ?? ''}?" .opened=${!!this._pending}
                    @opened-changed=${e => { if (!e.detail.value) { this._pending = undefined; } }}
                    ${dialogRenderer(() => this._renderConfirmation(), [this._pending])}
                    ${dialogFooterRenderer(() => html`
                        <vaadin-button data-confirm="cancel" @click=${() => this._pending = undefined}>Cancel</vaadin-button>
                        <vaadin-button data-confirm="run" theme="primary" @click=${this._confirm}>Run</vaadin-button>`,
                    [this._pending])}></vaadin-dialog>`;
    }

    _renderForm() {
        if (this._schemaError) {
            return html`<div class="error">${this._schemaError}</div>`;
        }
        if (!this._schema) {
            return html`<div class="muted">Loading the inputs...</div>`;
        }
        const last = this._lastInputs;
        return html`
            <qwc-agentic-run-form .schema=${this._schema} .values=${last}
                    @run=${e => this._requestRun(e.detail.inputs)}
                    @inputs-changed=${e => this._previewGoalPaths(e.detail.keys)}></qwc-agentic-run-form>
            ${this._renderPreview()}
            ${last && this._run ? html`<div class="actions">
                <vaadin-button theme="tertiary" @click=${() => this._mode = 'live'}>Back to the last run</vaadin-button>
            </div>` : nothing}`;
    }

    /** Goal-oriented planner nodes of the system (they get a path preview). */
    _goalPlanners() {
        const planners = [];
        const visit = node => {
            if (node.plannerInfo?.patternType === 'GOAP') {
                planners.push(node);
            }
            node.children?.forEach(visit);
        };
        if (this.system) {
            visit(this.system.root);
        }
        return planners;
    }

    /** Asks the path each goal-oriented planner would take for the filled inputs (debounced). */
    _previewGoalPaths(keys) {
        if (this._goalPlanners().length === 0) {
            return;
        }
        clearTimeout(this._previewTimer);
        const systemId = this.system.id;
        this._previewTimer = setTimeout(() => this.jsonRpc.previewGoalPath({ systemId, inputKeys: keys })
            .then(response => {
                if (this.system?.id === systemId) {
                    this._preview = response.result ?? {};
                }
            })
            .catch(() => this._preview = undefined), 250);
    }

    _renderPreview() {
        const planners = this._goalPlanners();
        if (planners.length === 0 || !this._preview) {
            return nothing;
        }
        const names = new Map();
        const index = node => {
            names.set(node.id, node.name);
            node.children?.forEach(index);
        };
        index(this.system.root);
        return html`${planners.filter(p => this._preview[p.id]).map(planner => {
            const path = this._preview[planner.id];
            return html`<div class="preview" data-goal-preview=${planner.id}>
                <h5>Planned path${planners.length > 1 ? ` of ${planner.name}` : ''}</h5>
                ${path.length > 0 ? html`<div data-goal-path>${path.map(id => names.get(id) ?? id).join(' → ')}</div>`
                    : html`<div class="muted" data-goal-path>no path to <code>${planner.outputKey}</code> from these inputs</div>`}
            </div>`;
        })}`;
    }

    _renderConfirmation() {
        const tools = this.system ? reachableTools(this.system) : [];
        return html`
            <div class="tools-confirmation" style="max-width: 32em">
                <p>This run executes the system for real: its models are called and its tools run, with their side
                    effects and cost.</p>
                ${tools.length === 0 ? html`<p>No tool is reachable from this system.</p>` : html`
                    <p>Tools reachable from this system:</p>
                    <ul>${tools.map(tool => html`<li><strong>${tool.name}</strong> <span style="opacity: 0.7">${tool.detail}</span></li>`)}</ul>`}
            </div>`;
    }

    _renderLive() {
        const run = this._run;
        const waiting = run.status === 'running' ? this._waiting : undefined;
        const label = waiting ? 'Waiting for your input'
            : { running: 'Running...', succeeded: 'Succeeded', failed: 'Failed', cancelled: 'Cancelled',
                lost: 'Connection lost' }[run.status];
        return html`
            <div class="status ${run.status}" data-run-status=${waiting ? 'waiting' : run.status}>
                ${label}${run.duration != null ? html`<span class="muted">in ${formatDuration(run.duration)}</span>` : nothing}
            </div>
            ${run.status === 'running' && !waiting ? html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>` : nothing}
            ${waiting ? this._renderHumanInput(waiting) : nothing}
            ${run.status === 'running' ? html`
                <div class="actions"><vaadin-button theme="error tertiary" data-action="cancel" ?disabled=${!run.runId}
                        @click=${this._cancel}>Cancel</vaadin-button></div>` : nothing}
            ${run.status === 'succeeded' ? html`<div class="actions"><vaadin-button theme="tertiary" data-action="show-output"
                    @click=${() => this.dispatchEvent(new CustomEvent('show-output', { bubbles: true, composed: true }))}>
                    Show output</vaadin-button></div>` : nothing}
            ${['failed', 'cancelled', 'lost'].includes(run.status) ? html`<h5>${{ cancelled: 'Cancelled', lost: 'Live view stopped' }[run.status] ?? 'Error'}</h5>
                ${run.status === 'failed' && (run.failingAgent || run.error?.exceptionClass) ? html`<div data-run-failure>
                    ${run.failingAgent ? html`in <strong>${run.failingAgent}</strong>: ` : nothing}${simpleClass(run.error?.exceptionClass)}</div>` : nothing}
                <pre class="error" data-run-error>${errorText(run.error)}</pre>` : nothing}
            <h5>Inputs</h5>
            <pre>${this._format(run.inputs)}</pre>
            <div class="actions">
                <vaadin-button theme="primary" ?disabled=${run.status === 'running'} data-action="rerun"
                        @click=${() => this._requestRun(run.inputs)}>Run again</vaadin-button>
                <vaadin-button ?disabled=${run.status === 'running'} data-action="edit"
                        @click=${() => this._mode = 'form'}>Edit inputs</vaadin-button>
            </div>`;
    }

    /** The paused state of a human-in-the-loop step: what it asks, its inputs and the answer box. */
    _renderHumanInput(waiting) {
        const node = this._live?.nodes.get(waiting.nodeId);
        const json = waiting.valueType && !TEXT_ANSWERS.includes(waiting.valueType);
        const inputs = Object.entries(waiting.inputs ?? {});
        return html`
            <div class="human-input" data-human-input=${waiting.nodeId}>
                <div class="prompt" data-human-prompt>${waiting.prompt ?? `${node?.name ?? 'A step'} needs your input`}</div>
                <div class="muted">${node?.name ?? waiting.nodeId}${waiting.expectedKey ? html` writes <code>${waiting.expectedKey}</code>` : nothing}</div>
                ${inputs.length > 0 ? html`<pre data-human-inputs>${inputs.map(([name, value]) => `${name}: ${this._format(value)}`).join('\n')}</pre>` : nothing}
                <vaadin-text-area data-human-answer label=${json ? `Answer (JSON ${simpleType(waiting.valueType)})` : 'Answer'}
                        .value=${this._answer ?? ''} ?disabled=${this._answering} .invalid=${!!this._answerError}
                        .errorMessage=${this._answerError ?? ''} @value-changed=${e => this._answer = e.detail.value}
                        @keydown=${e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { this._sendAnswer(); } }}></vaadin-text-area>
                <div class="actions">
                    <vaadin-button theme="primary" data-action="answer" ?disabled=${this._answering}
                            @click=${this._sendAnswer}>Send</vaadin-button>
                </div>
            </div>`;
    }

    _sendAnswer() {
        const runId = this._run?.runId;
        if (!runId || !this._waiting || this._answering) {
            return;
        }
        this._answering = true;
        this._answerError = undefined;
        this.jsonRpc.provideHumanInput({ runId, value: this._answer ?? '' })
            .then(response => {
                if (response.result) {
                    this._answer = undefined;
                } else {
                    this._answerError = 'The step no longer waits for an answer';
                }
            })
            .catch(error => this._answerError = error?.error?.message ?? error?.message ?? String(error))
            .finally(() => this._answering = false);
    }

    _format(value) {
        return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    }

    _renderHistory() {
        if (!this._history || this._history.length === 0) {
            return nothing;
        }
        return html`
            <div class="history">
                <h5>Recent runs</h5>
                ${this._history.map(summary => html`
                    <div class="history-row ${summary.runId === this._run?.runId ? 'current' : ''}"
                            data-history-run=${summary.runId} title="Open this run"
                            @click=${() => this._openPast(summary.runId)}>
                        <span class="dot ${summary.status}"></span>
                        <span>${new Date(summary.startedAt).toLocaleTimeString()} · ${summary.status.toLowerCase()}</span>
                        <span class="muted">${summary.durationMillis != null ? formatDuration(summary.durationMillis) : '…'}</span>
                        <input type="checkbox" data-compare-run=${summary.runId} title="Select to compare"
                                ?disabled=${summary.status === 'RUNNING'} .checked=${this._compare.includes(summary.runId)}
                                @click=${e => e.stopPropagation()} @change=${e => this._toggleCompare(summary.runId, e.target.checked)}>
                    </div>`)}
                ${this._compare.length === 2 ? html`<div class="actions">
                    <vaadin-button theme="tertiary" data-action="compare" @click=${this._requestCompare}>Compare the 2 runs</vaadin-button>
                </div>` : this._compare.length === 1 ? html`<div class="muted">Select another run to compare</div>` : nothing}
            </div>`;
    }

    /** Two runs at most: a third one replaces the first chosen. */
    _toggleCompare(runId, checked) {
        const others = this._compare.filter(id => id !== runId);
        this._compare = checked ? [...others.slice(-1), runId] : others;
    }

    /** Asks the page to compare the selected runs, the older as A. */
    _requestCompare() {
        const startedAt = runId => this._history.find(summary => summary.runId === runId)?.startedAt ?? 0;
        const runIds = [...this._compare].sort((x, y) => startedAt(x) - startedAt(y));
        this.dispatchEvent(new CustomEvent('compare-runs', { detail: { runIds }, bubbles: true, composed: true }));
    }

    _loadHistory() {
        const systemId = this.system?.id;
        this.jsonRpc?.listRuns({ systemId })
            .then(response => {
                if (this.system?.id === systemId) {
                    this._history = response.result ?? [];
                }
            })
            .catch(() => this._history = []);
    }

    /**
     * Reopens a run of the history: its recorded events are replayed on the graph, the timeline and the scope panel;
     * a run still running is followed live (streamRun replays what it already emitted).
     */
    _openPast(runId) {
        const system = this.system;
        const action = this._newAction();
        this._stopStream();
        this.jsonRpc.getRun({ runId }).then(response => {
            const trace = response.result;
            if (!trace || this.system !== system || this._action !== action) {
                return;
            }
            this.graph?.resetRun();
            const summary = trace.summary;
            this._mode = 'live';
            if (summary.status === 'RUNNING') {
                this._run = { status: 'running', inputs: summary.inputs, runId };
                this._events = [];
                this._publish(true);
                this._follow(system, runId);
                return;
            }
            const live = new LiveRun(system, this.graph);
            this._live = live;
            this._waiting = undefined;
            trace.events.forEach(event => live.apply(event));
            this._events = trace.events;
            this._publish(false);
            this._run = this._ended({ inputs: summary.inputs, runId }, trace.events[trace.events.length - 1]);
        }).catch(error => {
            if (this._action === action) {
                this._mode = 'live';
                this._run = { status: 'failed', inputs: {},
                    error: { message: `Cannot open the run: ${error?.error?.message ?? error?.message ?? error}` } };
            }
        });
    }

    /** The run state after its terminal event. */
    _ended(run, event) {
        if (event?.type === 'RunFinished') {
            return { ...run, status: 'succeeded', output: event.output, duration: event.durationMillis };
        }
        const cancelled = event?.error?.exceptionClass === 'java.util.concurrent.CancellationException';
        const failingNode = this._live?.failingNode();
        const failingAgent = failingNode ? this._live.nodes.get(failingNode)?.name ?? failingNode : undefined;
        return { ...run, status: cancelled ? 'cancelled' : 'failed', error: event?.error, duration: event?.durationMillis,
            failingAgent: cancelled ? undefined : failingAgent };
    }

    _cancel() {
        if (this._run?.runId) {
            this.jsonRpc.cancelRun({ runId: this._run.runId });
        }
    }

    /** Asks for the confirmation of the reachable tools before the first run of the system in this session. */
    _requestRun(inputs) {
        session.set(LAST_INPUTS + this.system.id, inputs);
        this._lastInputs = inputs;
        if (session.get(CONFIRMED + this.system.id)) {
            this._start(inputs);
        } else {
            this._pending = { inputs };
        }
    }

    _confirm() {
        const pending = this._pending;
        this._pending = undefined;
        if (pending) {
            session.set(CONFIRMED + this.system.id, true);
            this._start(pending.inputs);
        }
    }

    /** @param started told the run id once the run started */
    _start(inputs, started) {
        const action = this._newAction();
        this._stopStream();
        this._waiting = undefined;
        this._answer = undefined;
        this._answerError = undefined;
        this.graph?.resetRun();
        const system = this.system;
        this._run = { status: 'running', inputs };
        this._events = [];
        this._publish(true);
        this._mode = 'live';
        this.jsonRpc.startRun({ systemId: system.id, inputs })
            .then(response => {
                if (this.system !== system || this._action !== action) {
                    this._loadHistory(); // the run goes on, in the history
                    return;
                }
                const runId = response.result;
                this._run = { ...this._run, runId };
                started?.(runId);
                this._follow(system, runId);
                this._loadHistory();
            })
            .catch(error => {
                if (this._action === action) {
                    this._run = { ...this._run, status: 'failed',
                        error: { message: error?.error?.message ?? error?.message ?? String(error) } };
                }
            });
    }

    /** Applies the events of the run to the graph and the status, until RunFinished or RunFailed. */
    _follow(system, runId) {
        this._stopStream(); // one stream at most
        const live = new LiveRun(system, this.graph);
        this._live = live;
        this._waiting = undefined;
        this._observer = this.jsonRpc.streamRun({ runId }).onError(response => {
            if (this._live === live) {
                this._lost(`Cannot follow the run: ${response?.error?.message ?? 'the stream failed'}`);
            }
        }).onNext(response => {
            const event = response.result;
            if (!event || this._live !== live) {
                return;
            }
            live.apply(event);
            this._events.push(event);
            const waiting = live.pendingInput();
            if (waiting?.invocationId !== this._waiting?.invocationId || waiting?.timestamp !== this._waiting?.timestamp) {
                this._answerError = undefined;
            }
            this._waiting = waiting;
            const ended = event.type === 'RunFinished' || event.type === 'RunFailed';
            if (!ended) {
                this._schedulePublish();
                return;
            }
            this._publish(false);
            this._run = this._ended(this._run, event);
            this._stopStream();
            this._loadHistory();
            this._keepForReload(system, runId);
        });
    }

    /**
     * With re-run after reload on, the trace of each ended run is kept in the page (runs do not survive a reload); a
     * run started by a reload is then compared with the one kept before it.
     */
    _keepForReload(system, runId) {
        if (!this._watch) {
            return;
        }
        const compareWith = this._rerunOf === runId ? this._beforeReload : undefined;
        this._rerunOf = undefined;
        this.jsonRpc.getRun({ runId }).then(response => {
            if (this.system !== system || !response.result) {
                return;
            }
            this._beforeReload = response.result;
            if (compareWith) {
                this.dispatchEvent(new CustomEvent('compare-runs', { detail: { runIds: [compareWith.summary.runId, runId],
                    traceA: compareWith }, bubbles: true, composed: true }));
            }
        }).catch(() => {});
    }

    _toggleWatch(on) {
        this._watch = on;
        session.set(WATCH + this.system.id, on);
        this._watchMessage = on ? 'The last inputs run again after each reload of the application' : undefined;
    }

    /** After a hot reload: runs the last inputs again (opt-in), once the system is known again. */
    async _afterReload() {
        const system = this.system;
        if (!this._watch || !system || this._reloading) {
            return;
        }
        const inputs = this._lastInputs;
        if (!inputs) {
            this._watchMessage = 'Not re-run after the reload: no run yet';
            return;
        }
        if (!session.get(CONFIRMED + system.id)) {
            this._watchMessage = 'Not re-run after the reload: run it once yourself to confirm the tools it can reach';
            return;
        }
        this._reloading = true; // once per reload
        try {
            let known = false;
            for (let attempt = 0; attempt < (this.reloadAttempts ?? RELOAD_ATTEMPTS) && !known && this.system === system; attempt++) {
                known = await this.jsonRpc.getTopology({ systemId: system.id }).then(() => true, () => false);
                if (!known) {
                    await new Promise(resolve => setTimeout(resolve, 500));
                }
            }
            if (this.system !== system) {
                return;
            }
            if (!known) {
                this._watchMessage = 'Not re-run after the reload: the system no longer exists';
                return;
            }
            const schema = await this.jsonRpc.getInputSchema({ systemId: system.id }).then(r => r.result, () => undefined);
            const properties = Object.keys(schema?.properties ?? {});
            const changed = Object.keys(inputs).some(name => !properties.includes(name))
                || (schema?.required ?? []).some(name => !(name in inputs));
            if (changed) {
                this._watchMessage = 'Not re-run after the reload: the inputs of the system changed';
                return;
            }
            this._schema = schema;
            this._watchMessage = 'Re-run after the reload';
            this._start(inputs, runId => this._rerunOf = runId);
        } finally {
            this._reloading = false;
        }
    }

    _renderWatch() {
        if (!this._schema) {
            return nothing;
        }
        return html`<div class="watch">
            <label><input type="checkbox" data-action="watch" .checked=${!!this._watch}
                    @change=${e => this._toggleWatch(e.target.checked)}> Re-run after a reload</label>
            ${this._watchMessage ? html`<div class="muted" data-watch-message>${this._watchMessage}</div>` : nothing}
        </div>`;
    }

    /**
     * Tells the page about the events of the current run, for the timeline and the scope panel: each of them works on
     * all the events, so a run's events are passed on at most once per frame (thousands may arrive within a moment).
     */
    _schedulePublish() {
        if (this._publishFrame === undefined) {
            this._publishFrame = requestAnimationFrame(() => {
                this._publishFrame = undefined;
                this._publish(true);
            });
        }
    }

    /** Tells the page about the events of the current run now (a copy: the run keeps adding to its own list). */
    _publish(running) {
        if (this._publishFrame !== undefined) {
            cancelAnimationFrame(this._publishFrame);
            this._publishFrame = undefined;
        }
        this.dispatchEvent(new CustomEvent('run-updated', {
            detail: { events: [...(this._events ?? [])], running }, bubbles: true, composed: true,
        }));
    }

    _stopStream() {
        this._observer?.cancel?.();
        this._observer = undefined;
    }

    /** @return the live state of the current run, for checks */
    liveState() {
        return this._live?.state();
    }
}

customElements.define('qwc-agentic-run-panel', QwcAgenticRunPanel);
