import { LitElement, html, css, nothing } from 'lit';

/**
 * Rows of the waterfall timeline of a run, from its events: one row per agent invocation, nested by parent invocation
 * (the way AgentMonitor records them), so each loop iteration and parallel-mapper item has its own row; tool calls are
 * rows under the invocation that made them.
 *
 * @param now time to use as the end of what is still running (ms since the epoch)
 * @return {{rows: Array, start: number, end: number}} rows depth-first, start and end of the run (ms since the epoch)
 */
export function buildTimeline(events, now = Date.now()) {
    const runStart = events.find(e => e.type === 'RunStarted')?.timestamp ?? events[0]?.timestamp ?? now;
    const runEnd = events.find(e => e.type === 'RunFinished' || e.type === 'RunFailed')?.timestamp;
    const invocations = new Map();
    const roots = [];
    const toolCalls = new Map();
    for (const event of events) {
        switch (event.type) {
            case 'AgentStarted': {
                const invocation = {
                    kind: 'agent', id: event.invocationId, nodeId: event.nodeId, name: event.agentName,
                    iteration: event.iteration, itemIndex: event.itemIndex, start: event.timestamp, end: undefined,
                    status: 'running', children: [],
                };
                invocations.set(event.invocationId, invocation);
                const parent = invocations.get(event.parentInvocationId);
                (parent ? parent.children : roots).push(invocation);
                break;
            }
            case 'AgentFinished':
            case 'AgentFailed': {
                const invocation = invocations.get(event.invocationId);
                if (invocation) {
                    invocation.end = event.timestamp;
                    invocation.duration = event.durationMillis;
                    invocation.status = event.type === 'AgentFailed' ? 'failed' : 'done';
                    invocation.tokens = event.tokens?.totalTokens;
                    invocation.error = event.error?.message;
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
            case 'ToolStarted': {
                const call = {
                    kind: 'tool', id: `tool:${event.invocationId}:${event.toolCallId ?? event.toolName}:${event.timestamp}`,
                    name: event.toolName, arguments: event.arguments, start: event.timestamp, status: 'running',
                    children: [],
                };
                toolCalls.set(`${event.invocationId}/${event.toolCallId ?? event.toolName}`, call);
                const agent = invocations.get(event.invocationId);
                (agent ? agent.children : roots).push(call);
                break;
            }
            case 'ToolFinished': {
                const key = `${event.invocationId}/${event.toolCallId ?? event.toolName}`;
                const call = toolCalls.get(key);
                if (call) {
                    toolCalls.delete(key);
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

function formatDuration(millis) {
    return millis < 1000 ? `${Math.round(millis)} ms` : `${(millis / 1000).toFixed(1)} s`;
}

/**
 * Waterfall timeline of a run: one row per invocation and tool call, nested by parent, with bars placed by start offset
 * and duration over the run's span. Tool rows expand to show their arguments and result.
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
        .row.tool {
            cursor: pointer;
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
        .bar.tool {
            background: var(--lumo-contrast-40pct);
        }
        .bar.recovered {
            background: #f59e0b;
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
        .empty {
            color: var(--lumo-secondary-text-color);
        }
    `;

    static properties = {
        events: { type: Array },
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
        const { rows, start, end } = buildTimeline(this.events ?? []);
        if (rows.length === 0) {
            return html`<div class="empty">No invocation yet.</div>`;
        }
        const span = Math.max(1, end - start);
        return html`${rows.map(row => html`
            <div class="row ${row.kind}" data-row=${row.id} data-depth=${row.depth}
                    @click=${row.kind === 'tool' ? () => this._toggle(row.id) : undefined}>
                <span class="label" style="padding-left: ${row.depth * 14}px" title=${row.nodeId ?? row.name}>
                    ${row.kind === 'tool' ? '🔧 ' : row.kind === 'error' ? '⚠ ' : ''}${row.name}${this._detail(row)}
                </span>
                <span class="track">
                    <span class="bar ${row.kind === 'tool' ? 'tool' : row.status}"
                          style="left: ${(row.offset / span) * 100}%; width: ${(row.span / span) * 100}%"></span>
                </span>
                <span class="duration">${row.status === 'running' ? '…' : formatDuration(row.duration ?? row.span)}</span>
            </div>
            ${row.kind === 'tool' && this._open.has(row.id) ? html`
                <div class="tool-details" style="margin-left: ${row.depth * 14}px" data-tool-details=${row.id}>arguments: ${row.arguments ?? ''}
result: ${row.result ?? '(running)'}</div>` : nothing}`)}`;
    }

    _detail(row) {
        const parts = [];
        if (row.kind === 'error') {
            parts.push({ RETRY: 'retried', RETURN_RESULT: 'recovered with a result', THROW_EXCEPTION: 'fatal' }[row.outcome] ?? row.outcome);
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
