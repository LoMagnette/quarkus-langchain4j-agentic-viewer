/**
 * The model of a run shared by the Dev UI components: LiveRun (what a run did so far, node by node, and how the graph
 * shows it) and the small helpers the run panel, timeline, output and node details use.
 */

/** An error as shown: its message, and the deepest cause when langchain4j wrapped it (what the agent really threw). */
export function errorText(error) {
    const message = error?.message ?? error?.exceptionClass ?? 'unknown error';
    return error?.rootCauseClass && error.rootCauseMessage !== error.message
        ? `${message}\nCaused by ${simpleClass(error.rootCauseClass)}: ${error.rootCauseMessage ?? '(no message)'}`
        : message;
}

/** "com.example.Outer$Inner#method" -> "Inner#method" */
export function shortRef(ref) {
    if (!ref) {
        return ref;
    }
    const [className, method] = ref.split('#');
    const simple = className.substring(Math.max(className.lastIndexOf('.'), className.lastIndexOf('$')) + 1);
    return method ? `${simple}#${method}` : simple;
}

export function simpleClass(name) {
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

export function simpleType(name) {
    return name ? name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1) : '';
}

export function formatDuration(millis) {
    return millis < 1000 ? `${Math.round(millis)} ms` : `${(millis / 1000).toFixed(1)} s`;
}

/** The node with this id in a topology tree, undefined when none. */
export function findNode(node, id) {
    if (!id || node.id === id) {
        return id ? node : undefined;
    }
    for (const child of node.children ?? []) {
        const found = findNode(child, id);
        if (found) {
            return found;
        }
    }
    return undefined;
}

/** Every node of a topology tree, by id. */
export function indexNodes(root) {
    const nodes = new Map();
    const index = node => {
        nodes.set(node.id, node);
        node.children?.forEach(index);
    };
    if (root) {
        index(root);
    }
    return nodes;
}

/**
 * What a run did so far, node by node, and how the graph shows it: a node is running while any of its invocations
 * runs (parallel branches, mapper items), done when they all finished, failed when one failed; finished agents show
 * their duration and tokens, loops their iteration count, parallel mappers their items.
 */
export class LiveRun {

    constructor(system, graph) {
        this.graph = graph;
        this.nodes = indexNodes(system.root);
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
                this.interrupt();
                break;
            }
            case 'AgentFinished':
            case 'AgentFailed': {
                // a step known by node only (no invocation id) ends with the agent of its node
                this.waiting = this.waiting.filter(w => w.invocationId ? w.invocationId !== event.invocationId
                    : w.nodeId !== event.nodeId);
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

    /**
     * The run ended, or its events no longer arrive: what was still running (or waiting) was interrupted, e.g. by a
     * cancel, which drops the events that follow.
     */
    interrupt() {
        this.waiting = [];
        [...this.running].filter(([, count]) => count > 0).forEach(([nodeId]) => {
            this.running.set(nodeId, 0);
            this.graph?.highlight(nodeId, 'interrupted');
            this._showInfo(nodeId);
        });
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

/** Whether a value carries a truncation marker of the server (PayloadTruncator): equal values may still differ. */
export function isTruncated(value) {
    if (typeof value === 'string') {
        return value.includes('… [truncated') || value.startsWith('… [');
    }
    if (Array.isArray(value)) {
        return value.some(isTruncated);
    }
    if (value && typeof value === 'object') {
        return '…' in value || Object.values(value).some(isTruncated);
    }
    return false;
}

/** The agent invocations of a run, from its events, each with a key stable across runs of the same system. */
function invocations(events) {
    const byId = new Map();
    const list = [];
    for (const event of events ?? []) {
        if (event.type === 'AgentStarted') {
            const invocation = { id: event.invocationId, parentId: event.parentInvocationId, nodeId: event.nodeId,
                name: event.agentName, iteration: event.iteration, itemIndex: event.itemIndex, status: 'running' };
            byId.set(event.invocationId, invocation);
            list.push(invocation);
        } else if (event.type === 'AgentFinished' || event.type === 'AgentFailed') {
            const invocation = byId.get(event.invocationId);
            if (invocation) {
                invocation.status = event.type === 'AgentFailed' ? 'failed' : 'done';
                invocation.output = event.type === 'AgentFailed' ? event.error?.message : event.output;
                invocation.duration = event.durationMillis;
                invocation.tokens = event.tokens?.totalTokens;
            }
        }
    }
    // node, loop iteration and mapper item, under the parent's key; the n-th call of a node with neither (a supervisor
    // calling an agent again) under the same parent
    const keys = new Map();
    const seen = new Map();
    const keyOf = invocation => {
        if (keys.has(invocation.id)) {
            return keys.get(invocation.id);
        }
        const parent = byId.get(invocation.parentId);
        const base = `${parent ? keyOf(parent) : ''}/${invocation.nodeId}[${invocation.iteration ?? ''},${invocation.itemIndex ?? ''}]`;
        const occurrence = (seen.get(base) ?? 0) + 1;
        seen.set(base, occurrence);
        const key = occurrence > 1 ? `${base}#${occurrence}` : base;
        keys.set(invocation.id, key);
        return key;
    };
    list.forEach(invocation => invocation.key = keyOf(invocation));
    const depthOf = invocation => byId.has(invocation.parentId) ? depthOf(byId.get(invocation.parentId)) + 1 : 0;
    list.forEach(invocation => invocation.depth = depthOf(invocation));
    return list;
}

/** Totals of a run: status, duration, tokens. */
function totals(trace) {
    const events = trace?.events ?? [];
    const end = events.find(e => e.type === 'RunFinished' || e.type === 'RunFailed');
    return {
        status: trace?.summary?.status, duration: end?.durationMillis ?? trace?.summary?.durationMillis,
        tokens: events.filter(e => e.type === 'AgentFinished').reduce((sum, e) => sum + (e.tokens?.totalTokens ?? 0), 0),
        output: end?.type === 'RunFinished' ? end.output : undefined,
    };
}

const VERDICT_RANK = { same: 0, maybe: 1, changed: 2, onlyA: 3, onlyB: 3 };

/**
 * Compares two runs of a system: their inputs, each agent invocation matched across them (node, loop iteration, mapper
 * item, under the same parents), and their totals.
 *
 * @return {{inputs, rows, nodes, a, b}} rows in the order of run A, then what only B ran; per row and per node a
 *         verdict: same, maybe (equal up to a truncation), changed, onlyA, onlyB
 */
export function compareRuns(traceA, traceB) {
    const a = invocations(traceA?.events);
    const b = invocations(traceB?.events);
    const bByKey = new Map(b.map(invocation => [invocation.key, invocation]));
    const verdict = (x, y) => {
        if (!y) {
            return 'onlyA';
        }
        if (!x) {
            return 'onlyB';
        }
        if (x.status !== y.status || JSON.stringify(x.output) !== JSON.stringify(y.output)) {
            return 'changed';
        }
        return isTruncated(x.output) || isTruncated(y.output) ? 'maybe' : 'same';
    };
    const rows = a.map(x => ({ key: x.key, nodeId: x.nodeId, name: x.name, depth: x.depth, a: x, b: bByKey.get(x.key),
        verdict: verdict(x, bByKey.get(x.key)) }));
    const aKeys = new Set(a.map(x => x.key));
    b.filter(y => !aKeys.has(y.key)).forEach(y => rows.push({ key: y.key, nodeId: y.nodeId, name: y.name, depth: y.depth,
        a: undefined, b: y, verdict: 'onlyB' }));
    const nodes = new Map();
    rows.forEach(row => {
        const current = nodes.get(row.nodeId);
        if (!current || VERDICT_RANK[row.verdict] > VERDICT_RANK[current]) {
            nodes.set(row.nodeId, row.verdict);
        }
    });
    const inputsA = traceA?.summary?.inputs ?? {};
    const inputsB = traceB?.summary?.inputs ?? {};
    const inputs = [...new Set([...Object.keys(inputsA), ...Object.keys(inputsB)])].map(name => ({ name,
        a: inputsA[name], b: inputsB[name], same: JSON.stringify(inputsA[name]) === JSON.stringify(inputsB[name]) }));
    return { inputs, rows, nodes, a: totals(traceA), b: totals(traceB) };
}
