import { LitElement, html, css, nothing } from 'lit';
import '@vaadin/button';
import '@vaadin/dialog';
import '@vaadin/progress-bar';
import '@vaadin/text-area';
import { dialogFooterRenderer, dialogRenderer } from '@vaadin/dialog/lit.js';
import './qwc-agentic-run-form.js';

const LAST_INPUTS = 'agentic-viewer:last-inputs:';
const CONFIRMED = 'agentic-viewer:tools-confirmed:';

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

function shortRef(ref) {
    if (!ref) {
        return ref;
    }
    const [className, method] = ref.split('#');
    const simple = className.substring(Math.max(className.lastIndexOf('.'), className.lastIndexOf('$')) + 1);
    return method ? `${simple}#${method}` : simple;
}

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

function simpleClass(name) {
    return name ? name.substring(name.lastIndexOf('.') + 1) : 'error';
}

/** Why a peer-to-peer planner stopped. */
export const TERMINATIONS = {
    EXIT_CONDITION: 'exit condition met',
    MAX_INVOCATIONS: 'maximum invocations reached',
    STABLE_STATE: 'stable state (no agent can activate)',
    GOAL_REACHED: 'goal reached',
    QUIESCENT: 'quiescent (no agent can fire)',
};

function simpleType(name) {
    return name ? name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1) : '';
}

function formatDuration(millis) {
    return millis < 1000 ? `${millis} ms` : `${(millis / 1000).toFixed(1)} s`;
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
        .status.cancelled {
            color: var(--lumo-secondary-text-color);
        }
        .history {
            display: flex;
            flex-direction: column;
            margin-top: var(--lumo-space-m);
        }
        .history-row {
            display: grid;
            grid-template-columns: 12px 1fr auto;
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
    };

    constructor() {
        super();
        this._mode = 'form';
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
            const systemId = this.system.id;
            this._loadHistory();
            this.jsonRpc?.getInputSchema({ systemId })
                .then(response => {
                    if (this.system?.id === systemId) {
                        this._schema = response.result;
                    }
                })
                .catch(error => this._schemaError = `Cannot load the inputs: ${error?.error?.message ?? error?.message ?? error}`);
        }
    }

    disconnectedCallback() {
        this._stopStream();
        super.disconnectedCallback();
    }

    render() {
        return html`
            ${this._mode === 'live' && this._run ? this._renderLive() : this._renderForm()}
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
        const last = session.get(LAST_INPUTS + this.system.id);
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
            : { running: 'Running...', succeeded: 'Succeeded', failed: 'Failed', cancelled: 'Cancelled' }[run.status];
        return html`
            <div class="status ${run.status}" data-run-status=${waiting ? 'waiting' : run.status}>
                ${label}${run.duration != null ? html`<span class="muted">in ${formatDuration(run.duration)}</span>` : nothing}
            </div>
            ${run.status === 'running' && !waiting ? html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>` : nothing}
            ${waiting ? this._renderHumanInput(waiting) : nothing}
            ${run.status === 'running' ? html`
                <div class="actions"><vaadin-button theme="error tertiary" data-action="cancel" ?disabled=${!run.runId}
                        @click=${this._cancel}>Cancel</vaadin-button></div>` : nothing}
            ${run.status === 'succeeded' ? html`<h5>Output</h5><pre data-run-output>${this._format(run.output)}</pre>` : nothing}
            ${run.status === 'failed' || run.status === 'cancelled' ? html`<h5>${run.status === 'cancelled' ? 'Cancelled' : 'Error'}</h5>
                ${run.status === 'failed' && (run.failingAgent || run.error?.exceptionClass) ? html`<div data-run-failure>
                    ${run.failingAgent ? html`in <strong>${run.failingAgent}</strong>: ` : nothing}${simpleClass(run.error?.exceptionClass)}</div>` : nothing}
                <pre class="error" data-run-error>${run.error?.message ?? run.error?.exceptionClass ?? 'unknown error'}</pre>` : nothing}
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
        const json = waiting.valueType && waiting.valueType !== 'java.lang.String';
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
                    </div>`)}
            </div>`;
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
        this._stopStream();
        this.jsonRpc.getRun({ runId }).then(response => {
            const trace = response.result;
            if (!trace || this.system !== system) {
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
        }).catch(error => this._run = { status: 'failed', inputs: {},
            error: { message: `Cannot open the run: ${error?.error?.message ?? error?.message ?? error}` } });
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

    _start(inputs) {
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
                if (this.system !== system) {
                    return;
                }
                const runId = response.result;
                this._run = { ...this._run, runId };
                this._follow(system, runId);
                this._loadHistory();
            })
            .catch(error => this._run = { ...this._run, status: 'failed',
                error: { message: error?.error?.message ?? error?.message ?? String(error) } });
    }

    /** Applies the events of the run to the graph and the status, until RunFinished or RunFailed. */
    _follow(system, runId) {
        const live = new LiveRun(system, this.graph);
        this._live = live;
        this._waiting = undefined;
        this._observer = this.jsonRpc.streamRun({ runId }).onNext(response => {
            const event = response.result;
            if (!event || this._live !== live) {
                return;
            }
            live.apply(event);
            this._events = [...this._events, event];
            const waiting = live.pendingInput();
            if (waiting?.invocationId !== this._waiting?.invocationId || waiting?.timestamp !== this._waiting?.timestamp) {
                this._answerError = undefined;
            }
            this._waiting = waiting;
            this._publish(event.type !== 'RunFinished' && event.type !== 'RunFailed');
            if (event.type === 'RunFinished' || event.type === 'RunFailed') {
                this._run = this._ended(this._run, event);
                this._stopStream();
                this._loadHistory();
            }
        });
    }

    /** Tells the page about the events of the current run, for the timeline and the scope panel. */
    _publish(running) {
        this.dispatchEvent(new CustomEvent('run-updated', {
            detail: { events: this._events, running }, bubbles: true, composed: true,
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

/**
 * What a run did so far, node by node, and how the graph shows it: a node is running while any of its invocations
 * runs (parallel branches, mapper items), done when they all finished, failed when one failed; finished agents show
 * their duration and tokens, loops their iteration count, parallel mappers their items.
 */
class LiveRun {

    constructor(system, graph) {
        this.graph = graph;
        this.nodes = new Map();
        const index = node => {
            this.nodes.set(node.id, node);
            node.children?.forEach(index);
        };
        index(system.root);
        this.running = new Map();
        this.stats = new Map();
        this.loops = new Map();
        this.items = new Map();
        this.failed = new Set();
        /** errors handled by an error handler, by node: { exception, outcome } */
        this.handled = new Map();
        /** human-in-the-loop steps waiting for an answer: their AwaitingHumanInput events, oldest first */
        this.waiting = [];
        /** peer-to-peer planners: node id -> { invocations, max, termination } */
        this.peers = new Map();
        /** peer-to-peer agents that can activate: inputs all in the scope, one written since they last ran */
        this.ready = new Set();
        /** event sequence of the last write of each scope key, and of the last start of each node */
        this.sequence = 0;
        this.written = new Map();
        this.started = new Map();
        /** nodes on the path a goal-oriented planner computed, not run yet: node id -> position (1-based) */
        this.planned = new Map();
        /** sub-agents called by each supervisor or planner, in call order: node id -> [child node ids] */
        this.calls = new Map();
        /** decisions of each supervisor or planner, in order: node id -> [{ selected, done, action }] */
        this.decisions = new Map();
    }

    /** @return the AwaitingHumanInput event of the oldest step waiting for an answer (the one provideHumanInput answers) */
    pendingInput() {
        return this.waiting[0];
    }

    _stats(nodeId) {
        if (!this.stats.has(nodeId)) {
            this.stats.set(nodeId, { duration: 0, tokens: 0, finished: 0 });
        }
        return this.stats.get(nodeId);
    }

    _parent(nodeId) {
        const slash = nodeId.lastIndexOf('/');
        return slash < 0 ? undefined : nodeId.substring(0, slash);
    }

    apply(event) {
        switch (event.type) {
            case 'AgentStarted': {
                this.running.set(event.nodeId, (this.running.get(event.nodeId) ?? 0) + 1);
                this.graph?.highlight(event.nodeId, 'running');
                this.ready.delete(event.nodeId);
                this.started.set(event.nodeId, ++this.sequence);
                if (this.planned.has(event.nodeId)) {
                    this._showInfo(event.nodeId); // no longer "planned"
                }
                const parent = this._parent(event.nodeId);
                const parentKind = this.nodes.get(parent)?.kind;
                if (event.iteration != null && parentKind === 'LOOP') {
                    this.loops.set(parent, Math.max(this.loops.get(parent) ?? 0, event.iteration + 1));
                    this._showInfo(parent);
                }
                if ((parentKind === 'SUPERVISOR' || parentKind === 'PLANNER') && !this.decisions.has(parent)) {
                    // the path taken, without decision events: each called sub-agent gets the numbers of its calls
                    const calls = this.calls.get(parent) ?? [];
                    calls.push(event.nodeId);
                    this.calls.set(parent, calls);
                    const numbers = calls.map((id, i) => id === event.nodeId ? i + 1 : undefined).filter(Boolean);
                    this.graph?.setPath(event.nodeId, numbers.join(', '));
                }
                if (event.itemIndex != null && parentKind === 'PARALLEL_MAPPER') {
                    const items = this.items.get(parent) ?? { started: 0, finished: 0 };
                    items.started++;
                    this.items.set(parent, items);
                    this._showInfo(parent);
                }
                break;
            }
            case 'AwaitingHumanInput': {
                this.waiting.push(event);
                this.graph?.highlight(event.nodeId, 'waiting');
                this._showInfo(event.nodeId);
                break;
            }
            case 'RunFinished':
            case 'RunFailed': {
                this.waiting = [];
                break;
            }
            case 'AgentFinished':
            case 'AgentFailed': {
                this.waiting = this.waiting.filter(w => w.invocationId !== event.invocationId);
                const left = Math.max(0, (this.running.get(event.nodeId) ?? 1) - 1);
                this.running.set(event.nodeId, left);
                const stats = this._stats(event.nodeId);
                stats.duration += event.durationMillis ?? 0;
                stats.tokens += event.tokens?.totalTokens ?? 0;
                stats.finished++;
                if (event.type === 'AgentFailed') {
                    this.failed.add(event.nodeId);
                }
                if (left === 0) {
                    const recovered = this.handled.get(event.nodeId)?.outcome !== undefined
                        && this.handled.get(event.nodeId).outcome !== 'THROW_EXCEPTION';
                    this.graph?.highlight(event.nodeId, this.failed.has(event.nodeId) ? 'failed' : recovered ? 'recovered' : 'done');
                }
                const parent = this._parent(event.nodeId);
                if (this.items.has(parent)) {
                    this.items.get(parent).finished++;
                    this._showInfo(parent);
                }
                this._showInfo(event.nodeId);
                break;
            }
            case 'ScopeStateChanged': {
                this.written.set(event.key, ++this.sequence);
                // a peer-to-peer planner draws its state keys: written ones light up
                this.peers.forEach((peer, plannerId) => this.graph?.highlight(`${plannerId}::key::${event.key}`, 'done'));
                break;
            }
            case 'PlannerDecision': {
                if (event.extras?.pattern === 'BLACKBOARD') {
                    this.peers.set(event.nodeId, { invocations: event.extras.invocations, max: event.extras.maxInvocations,
                        termination: event.extras.termination, goalReached: event.extras.goalReached });
                    // the candidates of this step light up, the picked one is about to run; the others go back
                    const candidates = new Set((event.extras.candidates ?? []).filter(id => !(event.selectedNodeIds ?? []).includes(id)));
                    (this.nodes.get(event.nodeId)?.children ?? []).forEach(child => {
                        if ((this.running.get(child.id) ?? 0) > 0) {
                            return;
                        }
                        if (candidates.has(child.id)) {
                            this.ready.add(child.id);
                            this.graph?.highlight(child.id, 'ready');
                        } else if (this.ready.delete(child.id)) {
                            this.graph?.highlight(child.id, this.failed.has(child.id) ? 'failed' : this.stats.has(child.id) ? 'done' : null);
                        }
                    });
                }
                if (event.extras?.pattern === 'P2P') {
                    this.peers.set(event.nodeId, { invocations: event.extras.invocations, max: event.extras.maxInvocations,
                        termination: event.extras.termination });
                    // the agents that can activate light up (as P2PPlanner: inputs all in the scope, and new since the
                    // agent last ran); the others go back to what they did
                    const present = new Set(event.extras.ready ?? []);
                    (this.nodes.get(event.nodeId)?.children ?? []).forEach(child => {
                        if ((this.running.get(child.id) ?? 0) > 0) {
                            return;
                        }
                        const lastRun = this.started.get(child.id);
                        const fresh = lastRun === undefined
                            || (child.inputKeys ?? []).some(key => (this.written.get(key) ?? 0) > lastRun);
                        if (present.has(child.id) && fresh && !event.done) {
                            this.ready.add(child.id);
                            this.graph?.highlight(child.id, 'ready');
                        } else if (this.ready.delete(child.id)) {
                            this.graph?.highlight(child.id, this.failed.has(child.id) ? 'failed' : this.stats.has(child.id) ? 'done' : null);
                        }
                    });
                }
                if (Array.isArray(event.extras?.path)) {
                    // the path a goal-oriented planner computed: highlighted, then walked step by step
                    event.extras.path.forEach((id, i) => {
                        this.planned.set(id, i + 1);
                        if ((this.running.get(id) ?? 0) === 0 && !this.stats.has(id)) {
                            this.graph?.highlight(id, 'planned');
                            this._showInfo(id);
                        }
                    });
                }
                // the path taken, step by step: each chosen sub-agent gets the numbers of the steps that chose it
                const steps = this.decisions.get(event.nodeId) ?? [];
                steps.push({ selected: event.selectedNodeIds ?? [], done: event.done, action: event.extras?.action });
                this.decisions.set(event.nodeId, steps);
                // steps are the decisions choosing agents (as in the timeline)
                const chosen = steps.filter(step => step.selected.length > 0);
                (event.selectedNodeIds ?? []).forEach(id => this.graph?.setPath(id, chosen
                    .map((step, i) => step.selected.includes(id) ? i + 1 : undefined).filter(Boolean).join(', ')));
                this._showInfo(event.nodeId);
                break;
            }
            case 'ErrorHandled': {
                if (!event.nodeId) {
                    break;
                }
                const exception = simpleClass(event.error?.exceptionClass);
                this.handled.set(event.nodeId, { exception, outcome: event.outcome });
                if (event.outcome === 'THROW_EXCEPTION') {
                    this.failed.add(event.nodeId);
                    this.graph?.highlight(event.nodeId, 'failed');
                } else if ((this.running.get(event.nodeId) ?? 0) === 0) {
                    this.graph?.highlight(event.nodeId, 'recovered');
                }
                this._showInfo(event.nodeId);
                break;
            }
            default:
                break;
        }
    }

    /** The node that made the run fail: the last fatal error handled, else the last failed agent. */
    failingNode() {
        const fatal = [...this.handled].filter(([, h]) => h.outcome === 'THROW_EXCEPTION').map(([id]) => id);
        return fatal[fatal.length - 1] ?? [...this.failed].pop();
    }

    _showInfo(nodeId) {
        const parts = [];
        if (this.loops.has(nodeId)) {
            parts.push(`×${this.loops.get(nodeId)}`);
        }
        if (this.items.has(nodeId)) {
            const items = this.items.get(nodeId);
            parts.push(`${items.finished}/${items.started} items`);
        }
        const peer = this.peers.get(nodeId);
        if (peer) {
            parts.push(peer.termination ? `stopped: ${TERMINATIONS[peer.termination] ?? peer.termination}`
                : `invocations ${peer.invocations}${peer.max != null ? `/${peer.max}` : ''}`
                    + (peer.goalReached === false ? ' · goal not reached' : ''));
        }
        if (this.planned.has(nodeId) && (this.running.get(nodeId) ?? 0) === 0 && !this.stats.has(nodeId)) {
            parts.push(`planned ${this.planned.get(nodeId)}`);
        }
        const steps = this.decisions.get(nodeId);
        if (steps?.length) {
            const calls = steps.filter(step => step.selected.length > 0).length;
            parts.push(steps[steps.length - 1].done ? `done after ${calls} step${calls === 1 ? '' : 's'}` : `step ${calls}`);
        }
        if (this.waiting.some(w => w.nodeId === nodeId)) {
            parts.push('waiting for input');
        }
        const handled = this.handled.get(nodeId);
        if (handled) {
            // short: the node has room for one line; the timeline and the run panel name the exception
            parts.push(handled.outcome === 'RETRY' ? 'retried' : handled.outcome === 'RETURN_RESULT' ? 'recovered' : 'failed');
        }
        const stats = this.stats.get(nodeId);
        if (stats && (this.running.get(nodeId) ?? 0) === 0) {
            parts.push(formatDuration(stats.duration));
            if (stats.tokens > 0) {
                parts.push(`${stats.tokens} tokens`);
            }
        }
        this.graph?.setNodeInfo(nodeId, parts.join(' · ') || null);
    }

    state() {
        return {
            running: Object.fromEntries([...this.running].filter(([, count]) => count > 0)),
            loops: Object.fromEntries(this.loops),
            items: Object.fromEntries(this.items),
            failed: [...this.failed],
            waiting: this.waiting.map(w => w.nodeId),
            calls: Object.fromEntries(this.calls),
            decisions: Object.fromEntries(this.decisions),
            planned: Object.fromEntries(this.planned),
            peers: Object.fromEntries(this.peers),
            ready: [...this.ready],
        };
    }
}

customElements.define('qwc-agentic-run-panel', QwcAgenticRunPanel);
