import { LitElement, html, css, nothing } from 'lit';
import { formatDuration, indexNodes, patternNote, TERMINATIONS } from './qwc-agentic-run-model.js';
import { KIND_LABELS, KIND_STYLES, kindLabel } from './qwc-agentic-graph.js';
import './qwc-agentic-value.js';

/**
 * Rows of the waterfall timeline of a run, from its events: one row per agent invocation, nested by parent invocation
 * (the way AgentMonitor records them), so each loop iteration and parallel-mapper item has its own row; tool calls are
 * rows under the invocation that made them, and planner decisions numbered steps under the planner's invocation.
 *
 * @param now time to use as the end of what is still running (ms since the epoch)
 * @param system the run's system, for what a BDI or debate planner's step means (its desires, its judge); optional
 * @return {{rows: Array, start: number, end: number}} rows depth-first, start and end of the run (ms since the epoch)
 */
export function buildTimeline(events, now = Date.now(), system = undefined) {
    const runStart = events.find(e => e.type === 'RunStarted')?.timestamp ?? events[0]?.timestamp ?? now;
    const runEnd = events.find(e => e.type === 'RunFinished' || e.type === 'RunFailed')?.timestamp;
    const invocations = new Map();
    const roots = [];
    const toolCalls = new Map();
    const steps = new Map(); // decisions so far per planner invocation
    // agent names by node, to name the agents a planner chose (they start after the decision)
    const agentNames = new Map(events.filter(e => e.type === 'AgentStarted').map(e => [e.nodeId, e.agentName]));
    const agentName = id => agentNames.get(id) ?? id.substring(id.lastIndexOf('/') + 1).replace(/~\d+$/, '');
    const nodes = indexNodes(system?.root);
    // the scope keys written so far, inputs included (what a BDI step pursues depends on them)
    const written = new Set(Object.keys(events.find(e => e.type === 'RunStarted')?.inputs ?? {}));
    for (const event of events) {
        switch (event.type) {
            case 'ScopeStateChanged':
                written.add(event.key);
                break;
            case 'AgentStarted': {
                const invocation = {
                    kind: 'agent', id: event.invocationId, nodeId: event.nodeId, name: event.agentName,
                    iteration: event.iteration, itemIndex: event.itemIndex, start: event.timestamp, end: undefined,
                    status: 'running', inputs: event.inputs, children: [],
                };
                invocations.set(event.invocationId, invocation);
                const parent = invocations.get(event.parentInvocationId);
                (parent ? parent.children : roots).push(invocation);
                break;
            }
            case 'AwaitingHumanInput': {
                const invocation = invocations.get(event.invocationId);
                if (invocation) {
                    invocation.waiting = true;
                }
                break;
            }
            case 'AgentFinished':
            case 'AgentFailed': {
                const invocation = invocations.get(event.invocationId);
                if (invocation) {
                    invocation.waiting = false;
                    invocation.end = event.timestamp;
                    invocation.duration = event.durationMillis;
                    invocation.status = event.type === 'AgentFailed' ? 'failed' : 'done';
                    invocation.tokens = event.tokens?.totalTokens;
                    invocation.error = event.error?.message;
                    invocation.output = event.output;
                    invocation.finished = event.type === 'AgentFinished';
                }
                break;
            }
            case 'ErrorHandled': {
                const row = {
                    kind: 'error', id: `error:${event.invocationId}:${event.timestamp}:${event.agentName}`,
                    name: `${event.agentName}: ${(event.error?.exceptionClass ?? 'error').split('.').pop()}`,
                    outcome: event.outcome, start: event.timestamp, end: event.timestamp, duration: 0,
                    status: event.outcome === 'THROW_EXCEPTION' ? 'failed' : 'recovered', children: [],
                };
                const enclosing = invocations.get(event.invocationId);
                (enclosing ? enclosing.children : roots).push(row);
                break;
            }
            case 'PlannerDecision': {
                // numbered steps are the agents chosen; ending the workflow is not a step
                const chosen = (event.selectedNodeIds ?? []).map(agentName);
                const note = patternNote(event, nodes.get(event.nodeId), written);
                const step = (steps.get(event.invocationId) ?? 0) + (chosen.length > 0 ? 1 : 0);
                steps.set(event.invocationId, step);
                const row = {
                    kind: 'decision', id: `decision:${event.invocationId}:${event.timestamp}:${step}:${chosen.length}`,
                    name: chosen.length > 0 ? `Step ${step}` : event.extras?.action === 'doneWithResult' ? 'Done with result'
                        : event.extras?.action === 'suspend' ? 'Suspended' : 'Done',
                    nodeId: event.nodeId, decision: chosen.length > 0 ? `→ ${chosen.join(', ')}`
                        // a blackboard step: the candidates it picked from, and how
                        + (event.extras?.candidates ? ` of ${event.extras.candidates.map(agentName).join(', ')}` : '')
                        + (event.extras?.strategy ? ` · ${event.extras.strategy}` : '')
                        + (note ? ` · ${note}` : '')
                        : [TERMINATIONS[event.extras?.termination], note].filter(Boolean).join(' · ') || undefined,
                    start: event.timestamp, end: event.timestamp, duration: 0, status: 'done', children: [],
                };
                const planner = invocations.get(event.invocationId);
                (planner ? planner.children : roots).push(row);
                break;
            }
            case 'ToolStarted': {
                const call = {
                    kind: 'tool', id: `tool:${event.invocationId}:${event.toolCallId ?? event.toolName}:${event.timestamp}`,
                    name: event.toolName, arguments: event.arguments, start: event.timestamp, status: 'running',
                    children: [],
                };
                // calls without id of one tool in one invocation end in the order they started
                const key = `${event.invocationId}/${event.toolCallId ?? event.toolName}`;
                toolCalls.set(key, [...(toolCalls.get(key) ?? []), call]);
                const agent = invocations.get(event.invocationId);
                (agent ? agent.children : roots).push(call);
                break;
            }
            case 'ToolFinished': {
                const key = `${event.invocationId}/${event.toolCallId ?? event.toolName}`;
                const call = toolCalls.get(key)?.shift();
                if (call) {
                    call.end = event.timestamp;
                    call.duration = event.durationMillis;
                    call.result = event.result;
                    call.status = 'done';
                }
                break;
            }
            default:
                break;
        }
    }
    const end = runEnd ?? now;
    if (runEnd !== undefined) {
        // what was still running when the run ended was interrupted (a cancel drops the events that follow)
        const interrupt = entry => {
            if (entry.status === 'running') {
                entry.status = 'interrupted';
                entry.end = runEnd;
            }
            entry.children.forEach(interrupt);
        };
        roots.forEach(interrupt);
    }
    const rows = [];
    const visit = (entry, depth) => {
        const finish = entry.end ?? end;
        rows.push({ ...entry, depth, offset: Math.max(0, entry.start - runStart), span: Math.max(0, finish - entry.start),
            children: undefined });
        // by start; parallel-mapper items (concurrent) by item index
        [...entry.children].sort((a, b) => a.itemIndex != null && b.itemIndex != null ? a.itemIndex - b.itemIndex
                : a.start - b.start).forEach(child => visit(child, depth + 1));
    };
    roots.forEach(root => visit(root, 0));
    return { rows, start: runStart, end };
}

/**
 * Waterfall timeline of a run: one row per invocation and tool call, nested by parent, with bars placed by start offset
 * and duration over the run's span. Rows are tagged with the kind of their node (when the topology is given) and
 * expand on a click: an agent to its inputs and output, a tool call to its arguments and result.
 */
export class QwcAgenticTimeline extends LitElement {

    static styles = css`
        :host {
            display: block;
            overflow: auto;
            font-size: var(--lumo-font-size-xs);
        }
        .row {
            display: grid;
            grid-template-columns: minmax(180px, 30%) 1fr 70px;
            align-items: center;
            gap: var(--lumo-space-s);
            min-height: 22px;
            border-bottom: 1px solid var(--lumo-contrast-5pct);
        }
        .row.tool, .row.agent {
            cursor: pointer;
        }
        .kind {
            display: inline-block;
            margin-right: var(--lumo-space-xs);
            padding: 0 4px;
            border: 1px solid;
            border-radius: var(--lumo-border-radius-s);
            font-size: 10px;
            line-height: 14px;
            vertical-align: middle;
        }
        .details {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
            gap: var(--lumo-space-s) var(--lumo-space-m);
            margin: 0 0 var(--lumo-space-xs);
            padding: var(--lumo-space-s);
            background: var(--lumo-contrast-5pct);
            border-radius: var(--lumo-border-radius-s);
        }
        .details h6 {
            margin: 0 0 var(--lumo-space-xs);
            font-size: 10px;
            text-transform: uppercase;
            color: var(--lumo-secondary-text-color);
        }
        .details .error {
            color: var(--lumo-error-text-color);
            white-space: pre-wrap;
        }
        .label {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .label .detail {
            color: var(--lumo-secondary-text-color);
        }
        .row.tool .label {
            font-style: italic;
        }
        .track {
            position: relative;
            height: 12px;
        }
        .bar {
            position: absolute;
            top: 0;
            height: 12px;
            min-width: 2px;
            border-radius: 3px;
            background: var(--lumo-success-color-50pct);
        }
        .bar.running {
            background: var(--lumo-primary-color-50pct);
        }
        .bar.failed {
            background: var(--lumo-error-color-50pct);
        }
        .bar.interrupted {
            background: var(--lumo-contrast-20pct);
        }
        .bar.tool {
            background: var(--lumo-contrast-40pct);
        }
        .bar.recovered {
            background: #f59e0b;
        }
        .bar.decision {
            background: #ec4899;
            width: 3px !important;
        }
        .row.decision .label {
            color: var(--lumo-secondary-text-color);
            /* a step names the agent picked and, for a blackboard, the candidates: shown in full */
            white-space: normal;
        }
        .row.decision .label .detail {
            color: var(--lumo-body-text-color);
        }
        .row.error .label {
            color: var(--lumo-error-text-color);
        }
        .duration {
            text-align: right;
            color: var(--lumo-secondary-text-color);
        }
        .tool-details {
            margin: 0 0 var(--lumo-space-xs);
            padding: var(--lumo-space-xs) var(--lumo-space-s);
            white-space: pre-wrap;
            background: var(--lumo-contrast-5pct);
            border-radius: var(--lumo-border-radius-s);
            font-family: var(--lumo-font-family-monospace, monospace);
        }
        .empty, .muted {
            color: var(--lumo-secondary-text-color);
        }
    `;

    static properties = {
        events: { type: Array },
        /** the system of the run, to tag each row with the kind of its node */
        system: { type: Object },
        /** whether the run is still going: the timeline then grows with the clock */
        running: { type: Boolean },
        _open: { state: true },
        _tick: { state: true },
    };

    constructor() {
        super();
        this.events = [];
        this._open = new Set();
    }

    updated(changed) {
        if (changed.has('running')) {
            clearInterval(this._timer);
            if (this.running) {
                this._timer = setInterval(() => this._tick = Date.now(), 250);
            }
        }
    }

    disconnectedCallback() {
        clearInterval(this._timer);
        super.disconnectedCallback();
    }

    render() {
        const { rows, start, end } = buildTimeline(this.events ?? [], Date.now(), this.system);
        if (rows.length === 0) {
            return html`<div class="empty">No invocation yet.</div>`;
        }
        const span = Math.max(1, end - start);
        const nodes = this._nodes();
        return html`${rows.map(row => html`
            <div class="row ${row.kind}" data-row=${row.id} data-depth=${row.depth}
                    @click=${['tool', 'agent'].includes(row.kind) ? () => this._toggle(row.id) : undefined}>
                <span class="label" style="padding-left: ${row.depth * 14}px" title=${row.kind === 'decision' ? `${row.name} ${row.decision ?? ''}` : row.nodeId ?? row.name}>
                    ${row.kind === 'tool' ? '🔧 ' : row.kind === 'error' ? '⚠ ' : row.kind === 'decision' ? '◆ ' : ''}${this._kind(nodes.get(row.nodeId))}${row.name}${this._detail(row)}
                </span>
                <span class="track">
                    <span class="bar ${row.kind === 'tool' || row.kind === 'decision' ? row.kind : row.status}"
                          style="left: ${(row.offset / span) * 100}%; width: ${(row.span / span) * 100}%"></span>
                </span>
                <span class="duration">${row.kind === 'decision' ? '' : row.status === 'running' ? '…'
                        : row.status === 'interrupted' ? 'interrupted' : formatDuration(row.duration ?? row.span)}</span>
            </div>
            ${row.kind === 'tool' && this._open.has(row.id) ? html`
                <div class="tool-details" style="margin-left: ${row.depth * 14}px" data-tool-details=${row.id}>arguments: ${row.arguments ?? ''}
result: ${row.result ?? '(running)'}</div>` : nothing}
            ${row.kind !== 'tool' && this._open.has(row.id) ? this._details(row) : nothing}`)}`;
    }

    /** Each node of the system, by node id. */
    _nodes() {
        const nodes = new Map();
        const visit = node => {
            nodes.set(node.id, node);
            (node.children ?? []).forEach(visit);
        };
        if (this.system?.root) {
            visit(this.system.root);
        }
        return nodes;
    }

    _kind(node) {
        if (!node?.kind) {
            return nothing;
        }
        const kind = node.kind;
        const color = KIND_STYLES[kind]?.color ?? 'var(--lumo-contrast-50pct)';
        return html`<span class="kind" data-kind=${kind} style="color: ${color}; border-color: ${color}"
                          title=${KIND_LABELS[kind] ?? kind}>${kindLabel(kind, node.plannerInfo?.patternType)}</span>`;
    }

    /** What an agent got and gave, side by side when there is room. */
    _details(row) {
        if (row.kind !== 'agent') {
            return nothing;
        }
        const outcome = row.status === 'running' ? html`<span class="muted">running…</span>`
            : row.status === 'interrupted' ? html`<span class="muted">interrupted: the run ended first</span>`
            : row.status === 'failed' ? html`<div class="error">${row.error}</div>`
                : html`<qwc-agentic-value .value=${row.output}></qwc-agentic-value>`;
        return html`<div class="details" style="margin-left: ${row.depth * 14}px" data-details=${row.id}>
            <div data-details-inputs><h6>Inputs</h6><qwc-agentic-value .value=${row.inputs}></qwc-agentic-value></div>
            <div data-details-output><h6>${row.status === 'failed' ? 'Error' : 'Output'}</h6>${outcome}</div>
        </div>`;
    }

    _detail(row) {
        const parts = [];
        if (row.kind === 'decision' && row.decision) {
            parts.push(row.decision);
        }
        if (row.kind === 'error') {
            parts.push({ RETRY: 'retried', RETURN_RESULT: 'recovered with a result', THROW_EXCEPTION: 'fatal' }[row.outcome] ?? row.outcome);
        }
        if (row.waiting && row.status === 'running') {
            parts.push('waiting for input');
        }
        if (row.iteration != null) {
            parts.push(`iteration ${row.iteration + 1}`);
        }
        if (row.itemIndex != null) {
            parts.push(`item ${row.itemIndex + 1}`);
        }
        if (row.tokens) {
            parts.push(`${row.tokens} tokens`);
        }
        return parts.length ? html` <span class="detail">${parts.join(' · ')}</span>` : nothing;
    }

    _toggle(id) {
        const open = new Set(this._open);
        open.has(id) ? open.delete(id) : open.add(id);
        this._open = open;
    }
}

customElements.define('qwc-agentic-timeline', QwcAgenticTimeline);
