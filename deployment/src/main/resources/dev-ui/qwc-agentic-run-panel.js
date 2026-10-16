import { LitElement, html, css, nothing } from 'lit';
import '@vaadin/button';
import '@vaadin/dialog';
import '@vaadin/progress-bar';
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
            const systemId = this.system.id;
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
                    @run=${e => this._requestRun(e.detail.inputs)}></qwc-agentic-run-form>
            ${last && this._run ? html`<div class="actions">
                <vaadin-button theme="tertiary" @click=${() => this._mode = 'live'}>Back to the last run</vaadin-button>
            </div>` : nothing}`;
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
        const label = { running: 'Running...', succeeded: 'Succeeded', failed: 'Failed' }[run.status];
        return html`
            <div class="status ${run.status}" data-run-status=${run.status}>
                ${label}${run.duration != null ? html`<span class="muted">in ${formatDuration(run.duration)}</span>` : nothing}
            </div>
            ${run.status === 'running' ? html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>` : nothing}
            ${run.status === 'succeeded' ? html`<h5>Output</h5><pre data-run-output>${this._format(run.output)}</pre>` : nothing}
            ${run.status === 'failed' ? html`<h5>Error</h5><pre class="error" data-run-error>${run.error?.message ?? run.error?.exceptionClass ?? 'unknown error'}</pre>` : nothing}
            <h5>Inputs</h5>
            <pre>${this._format(run.inputs)}</pre>
            <div class="actions">
                <vaadin-button theme="primary" ?disabled=${run.status === 'running'} data-action="rerun"
                        @click=${() => this._requestRun(run.inputs)}>Run again</vaadin-button>
                <vaadin-button ?disabled=${run.status === 'running'} data-action="edit"
                        @click=${() => this._mode = 'form'}>Edit inputs</vaadin-button>
            </div>`;
    }

    _format(value) {
        return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
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
            })
            .catch(error => this._run = { ...this._run, status: 'failed',
                error: { message: error?.error?.message ?? error?.message ?? String(error) } });
    }

    /** Applies the events of the run to the graph and the status, until RunFinished or RunFailed. */
    _follow(system, runId) {
        const live = new LiveRun(system, this.graph);
        this._live = live;
        this._observer = this.jsonRpc.streamRun({ runId }).onNext(response => {
            const event = response.result;
            if (!event || this._live !== live) {
                return;
            }
            live.apply(event);
            this._events = [...this._events, event];
            this._publish(event.type !== 'RunFinished' && event.type !== 'RunFailed');
            if (event.type === 'RunFinished') {
                this._run = { ...this._run, status: 'succeeded', output: event.output, duration: event.durationMillis };
                this._stopStream();
            } else if (event.type === 'RunFailed') {
                this._run = { ...this._run, status: 'failed', error: event.error, duration: event.durationMillis };
                this._stopStream();
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
                const parent = this._parent(event.nodeId);
                const parentKind = this.nodes.get(parent)?.kind;
                if (event.iteration != null && parentKind === 'LOOP') {
                    this.loops.set(parent, Math.max(this.loops.get(parent) ?? 0, event.iteration + 1));
                    this._showInfo(parent);
                }
                if (event.itemIndex != null && parentKind === 'PARALLEL_MAPPER') {
                    const items = this.items.get(parent) ?? { started: 0, finished: 0 };
                    items.started++;
                    this.items.set(parent, items);
                    this._showInfo(parent);
                }
                break;
            }
            case 'AgentFinished':
            case 'AgentFailed': {
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
                    this.graph?.highlight(event.nodeId, this.failed.has(event.nodeId) ? 'failed' : 'done');
                }
                const parent = this._parent(event.nodeId);
                if (this.items.has(parent)) {
                    this.items.get(parent).finished++;
                    this._showInfo(parent);
                }
                this._showInfo(event.nodeId);
                break;
            }
            default:
                break;
        }
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
        };
    }
}

customElements.define('qwc-agentic-run-panel', QwcAgenticRunPanel);
