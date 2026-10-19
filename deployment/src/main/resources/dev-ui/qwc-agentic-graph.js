import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import { observeState } from 'lit-element-state';
import { themeState } from 'theme-state';
import cytoscape from 'cytoscape';
// elkjs maps its bare name to a CommonJS file; the UMD bundle registers window.ELK when imported
import 'elkjs/elk.bundled.js';

const LEAF_WIDTH = 150;
const LEAF_HEIGHT = 44;
/** Approximate width of a character of a box title (bold, font size 12). */
const TITLE_CHAR_WIDTH = 7;
const COMPOUND_PADDING = 20;
const TITLE_SPACE = 24;
const STATES = ['planned', 'ready', 'running', 'waiting', 'done', 'failed', 'recovered', 'interrupted',
    // the comparison of two runs
    'diff-same', 'diff-changed', 'diff-only'];
/** States that pulse: an agent running, a human step waiting for its answer (a running box pulses its border). */
const ACTIVE_STATES = ['running', 'waiting'];
/**
 * What the animation sets on a node: the halo of an active agent, the fading halo of an agent that just finished (fast
 * agents would show nothing otherwise), the border of a running box. When the system asks for reduced motion nothing
 * moves, but the feedback stays: a still halo while active, a still afterglow for the same time.
 */
const PULSE_STYLES = 'underlay-color underlay-opacity underlay-padding underlay-shape border-width';
const PULSE_PERIOD_MS = 1200;
const AFTERGLOW_MS = 1000;

/** Display labels of the node kinds. */
export const KIND_LABELS = {
    AI_AGENT: 'AI agent',
    NON_AI_AGENT: 'Non-AI agent',
    SEQUENCE: 'Sequence',
    PARALLEL: 'Parallel',
    PARALLEL_MAPPER: 'Parallel mapper',
    LOOP: 'Loop',
    CONDITIONAL: 'Conditional',
    SUPERVISOR: 'Supervisor',
    PLANNER: 'Planner',
    HUMAN_IN_THE_LOOP: 'Human in the loop',
    A2A_CLIENT_AGENT: 'A2A agent',
    MCP_CLIENT_AGENT: 'MCP client agent',
    REGISTRY_AGENT: 'Registry agent',
    INPUT_KEY: 'System input',
    CONTRIBUTED: 'Planner state',
    GOAL: 'Goal key',
    STATE_KEY: 'State key',
    BOARD: 'Blackboard',
    DESIRE: 'Desire',
    TALLY: 'Vote',
};

/** Tags of the langchain4j-agentic-patterns planners, shown instead of 'Planner' on their nodes. */
export const PATTERN_TAGS = {
    GOAP: 'GOAP', P2P: 'Peer-to-peer', BLACKBOARD: 'Blackboard', BDI: 'BDI', DEBATE: 'Debate', VOTING: 'Voting',
};

/** Display label of a node kind: the pattern for a planner of langchain4j-agentic-patterns. */
export function kindLabel(kind, patternType) {
    return (kind === 'PLANNER' && PATTERN_TAGS[patternType]) || KIND_LABELS[kind] || kind;
}

/**
 * How each node kind is drawn; the graph style and the legend both use it. Agents are leaf shapes, workflows are
 * boxes told apart by their border. Hues are mid-saturation so they read on light and dark themes.
 */
export const KIND_STYLES = {
    AI_AGENT: { shape: 'round-rectangle', color: '#10b981', border: 'solid' },
    NON_AI_AGENT: { shape: 'cut-rectangle', color: '#64748b', border: 'solid' },
    HUMAN_IN_THE_LOOP: { shape: 'round-hexagon', color: '#f59e0b', border: 'solid' },
    // remote agents: the work happens on another server
    A2A_CLIENT_AGENT: { shape: 'ellipse', color: '#0284c7', border: 'solid' },
    MCP_CLIENT_AGENT: { shape: 'rhomboid', color: '#65a30d', border: 'solid' },
    // resolved by name at runtime
    REGISTRY_AGENT: { shape: 'round-rectangle', color: '#64748b', border: 'dashed' },
    SEQUENCE: { shape: 'round-rectangle', color: '#64748b', border: 'solid' },
    PARALLEL: { shape: 'round-rectangle', color: '#3b82f6', border: 'double' },
    PARALLEL_MAPPER: { shape: 'round-rectangle', color: '#06b6d4', border: 'double' },
    LOOP: { shape: 'round-rectangle', color: '#8b5cf6', border: 'dashed' },
    CONDITIONAL: { shape: 'round-rectangle', color: '#f97316', border: 'dotted' },
    SUPERVISOR: { shape: 'round-rectangle', color: '#ec4899', border: 'solid', width: 3 },
    PLANNER: { shape: 'round-rectangle', color: '#d946ef', border: 'solid', width: 3 },
    INPUT_KEY: { shape: 'round-tag', color: '#94a3b8', border: 'dashed' },
    CONTRIBUTED: { shape: 'round-rectangle', color: '#d946ef', border: 'dotted' },
    GOAL: { shape: 'round-tag', color: '#d946ef', border: 'double' },
    STATE_KEY: { shape: 'round-tag', color: '#94a3b8', border: 'solid' },
    BOARD: { shape: 'round-rectangle', color: '#d946ef', border: 'double' },
    DESIRE: { shape: 'round-tag', color: '#d946ef', border: 'dotted' },
    TALLY: { shape: 'round-rectangle', color: '#d946ef', border: 'double' },
};

/** Separates a topology node id from the role of a helper node drawn for it (never used in topology ids). */
const HELPER_SEPARATOR = '::';
const HELPER_SIZE = 14;
/** Gap between the right side of a loop body and its back-edge bracket. */
const BACK_EDGE_GAP = 24;
/** The back-edge of a debate: the debaters argue again while they disagree. */
const DEBATE_ROUND_LABEL = 'next round\nuntil they agree';
/** Approximate width of a character of an edge label (font size 10). */
const EDGE_LABEL_CHAR_WIDTH = 5;

/**
 * Turns an AgenticSystem topology into graph nodes and edges.
 * A node with children is drawn as a compound node containing them; node ids are the topology node ids. Helper
 * nodes (fork, join...) are added inside a compound where its kind needs them; their id is the compound id,
 * {@code ::} and their role.
 * Edges have a kind: the layout ignores BACK edges, so cycles do not reorder the forward flow.
 *
 * @return {{nodes: Array<{id, label, kind, parent, compound, helper?}>,
 *           edges: Array<{id, source, target, kind, label?}>}}
 */
export function toGraph(system) {
    const nodes = [];
    const edges = [];
    const edgeIds = new Map();
    // ids are unique even for several edges between the same nodes (e.g. two transitions a contributor draws)
    const edge = (source, target, kind, label) => {
        const base = `${source}->${target}:${kind}`;
        const count = (edgeIds.get(base) ?? 0) + 1;
        edgeIds.set(base, count);
        edges.push({ id: count === 1 ? base : `${base}#${count}`, source, target, kind, label });
    };
    const helper = (parent, role) => {
        const id = parent.id + HELPER_SEPARATOR + role;
        nodes.push({ id, label: '', kind: role.toUpperCase(), parent: parent.id, parentKind: parent.kind, compound: false,
            helper: true, width: role === 'hub' ? 18 : HELPER_SIZE, height: role === 'hub' ? 18 : HELPER_SIZE });
        return id;
    };

    /**
     * A goal-oriented (GOAP) planner chains its sub-agents by keys towards its goal (its output key): an edge per key
     * from the sub-agent writing it to each sub-agent reading it, keys no sub-agent writes as inputs, the goal as a node
     * fed by its writer.
     */
    const goalGraph = node => {
        const keyNode = key => {
            const id = node.id + HELPER_SEPARATOR + 'key' + HELPER_SEPARATOR + key;
            if (!nodes.some(n => n.id === id)) {
                nodes.push({ id, label: key, kind: 'INPUT_KEY', parent: node.id, compound: false, helper: true, inputKey: true,
                    width: Math.max(60, key.length * 7 + 24), height: 28 });
            }
            return id;
        };
        const writers = new Map();
        node.children.forEach(child => child.outputKey && writers.set(child.outputKey,
            [...(writers.get(child.outputKey) ?? []), child.id]));
        // input keys first: the layout keeps the model order, so they sit above the agents reading them
        node.children.forEach(child => (child.inputKeys ?? []).filter(key => !writers.has(key)).forEach(key => keyNode(key)));
        node.children.forEach(child => visit(child, node.id));
        node.children.forEach(child => (child.inputKeys ?? []).forEach(key => {
            const sources = (writers.get(key) ?? []).filter(id => id !== child.id);
            sources.length ? sources.forEach(source => edge(source, child.id, 'DEPENDS', key))
                : edge(keyNode(key), child.id, 'DEPENDS');
        }));
        if (node.outputKey) {
            const goal = node.id + HELPER_SEPARATOR + 'goal';
            nodes.push({ id: goal, label: `goal: ${node.outputKey}`, kind: 'GOAL', parent: node.id, compound: false,
                width: Math.max(80, node.outputKey.length * 7 + 60), height: 30 });
            (writers.get(node.outputKey) ?? []).forEach(writer => edge(writer, goal, 'DEPENDS'));
        }
    };

    /**
     * A peer-to-peer planner activates its sub-agents as their inputs appear in the scope: a bipartite graph of the
     * sub-agents and the state keys they read (key -> agent) and write (agent -> key); cycles are legitimate. Keys no
     * sub-agent writes come first, then each sub-agent followed by the key it writes, so the layout follows the flow.
     */
    const peerGraph = node => {
        const written = new Set(node.children.map(child => child.outputKey).filter(Boolean));
        const keyNode = key => {
            const id = node.id + HELPER_SEPARATOR + 'key' + HELPER_SEPARATOR + key;
            if (!nodes.some(n => n.id === id)) {
                nodes.push({ id, label: key, kind: written.has(key) ? 'STATE_KEY' : 'INPUT_KEY', parent: node.id,
                    compound: false, helper: true, inputKey: true, width: Math.max(60, key.length * 7 + 24), height: 28 });
            }
            return id;
        };
        node.children.forEach(child => (child.inputKeys ?? []).filter(key => !written.has(key)).forEach(keyNode));
        node.children.forEach(child => {
            visit(child, node.id);
            if (child.outputKey) {
                keyNode(child.outputKey);
            }
        });
        node.children.forEach(child => {
            (child.inputKeys ?? []).forEach(key => edge(keyNode(key), child.id, 'READS'));
            if (child.outputKey) {
                edge(child.id, keyNode(child.outputKey), 'WRITES');
            }
        });
    };

    /**
     * A blackboard planner lets its agents (knowledge sources) fire on what is on the blackboard (the scope): a central
     * board, read by each agent (its input keys) and written by it (its output key); the goal is the planner's key.
     */
    const boardGraph = node => {
        const board = node.id + HELPER_SEPARATOR + 'board';
        const label = node.outputKey ? `blackboard · goal: ${node.outputKey}` : 'blackboard';
        nodes.push({ id: board, label, kind: 'BOARD', parent: node.id, compound: false,
            width: Math.max(160, label.length * 7 + 30), height: 34 });
        node.children.forEach(child => visit(child, node.id));
        node.children.forEach(child => {
            if (child.inputKeys?.length) {
                edge(board, child.id, 'READS', child.inputKeys.join(', '));
            }
            if (child.outputKey) {
                edge(child.id, board, 'WRITES', child.outputKey);
            }
        });
    };

    /**
     * A BDI planner pursues the achievable, unsatisfied desire of highest priority by calling its agents in order (its
     * intention): one node per desire its supplier declares, highest priority first, linked to its agents (numbered
     * when the intention has several). Desires unknown (declared elsewhere, a builder system): the generic hub.
     */
    const desireGraph = node => {
        const desires = [...node.plannerInfo.desires].map((desire, i) => ({ desire, i }))
            .sort((a, b) => (b.desire.priority ?? 0) - (a.desire.priority ?? 0) || a.i - b.i);
        desires.forEach(({ desire, i }) => {
            const label = desireLabel(desire);
            nodes.push({ id: node.id + HELPER_SEPARATOR + 'desire' + HELPER_SEPARATOR + i, label, kind: 'DESIRE',
                parent: node.id, compound: false, width: Math.max(90, label.length * 7 + 30), height: 30 });
        });
        node.children.forEach(child => visit(child, node.id));
        desires.forEach(({ desire, i }) => desire.agents.forEach((agent, step) => edge(
            node.id + HELPER_SEPARATOR + 'desire' + HELPER_SEPARATOR + i, agent, 'DEPENDS',
            desire.agents.length > 1 ? String(step + 1) : undefined)));
        // an agent no desire names is never called: left unlinked
    };

    /**
     * A debate planner calls its debaters (every sub-agent but the last) each round until they agree or the maximum
     * of rounds is reached, then its judge (the last sub-agent): the debaters side by side between a fork and a join,
     * a back-edge for the next round, the judge after them.
     */
    const debateGraph = node => {
        const debaters = node.children.slice(0, -1);
        const judge = node.children[node.children.length - 1];
        const fork = helper(node, 'fork');
        debaters.forEach(child => visit(child, node.id));
        const join = helper(node, 'join');
        visit(judge, node.id);
        debaters.forEach(child => {
            edge(fork, child.id, 'FORK');
            edge(child.id, join, 'JOIN');
        });
        edge(join, judge.id, 'SEQUENCE', 'judge');
        edge(join, fork, 'BACK', DEBATE_ROUND_LABEL);
    };

    /**
     * A voting planner calls all its sub-agents (voters) at once, then aggregates their outputs (votes) with its
     * strategy into its result: the voters between a fork and the tally, each vote labelled with its key.
     */
    const votingGraph = node => {
        const fork = helper(node, 'fork');
        node.children.forEach(child => visit(child, node.id));
        const tally = node.id + HELPER_SEPARATOR + 'tally';
        const label = node.outputKey ? `vote · ${node.outputKey}` : 'vote';
        nodes.push({ id: tally, label, kind: 'TALLY', parent: node.id, compound: false,
            width: Math.max(90, label.length * 7 + 30), height: 30 });
        node.children.forEach(child => {
            edge(fork, child.id, 'FORK');
            edge(child.id, tally, 'WRITES', child.outputKey);
        });
    };

    const visit = (node, parent) => {
        const compound = node.children?.length > 0;
        const badge = warningBadge(system, node.id);
        nodes.push({
            id: node.id,
            label: (compound ? `${kindLabel(node.kind, node.plannerInfo?.patternType)} · ${node.name}` : node.name) + badge,
            warned: !!badge,
            kind: node.kind,
            pattern: node.plannerInfo?.patternType,
            parent,
            compound,
            // extra layout room: a loop draws its back-edge and label on the right, conditional guards label the fan-out
            paddingRight: compound && node.kind === 'LOOP' ? BACK_EDGE_GAP + labelWidth(loopLabel(node.guard)) + 12
                : compound && isDebate(node) ? BACK_EDGE_GAP + labelWidth(DEBATE_ROUND_LABEL) + 12 : undefined,
            layerSpacing: compound && (node.kind === 'CONDITIONAL' || system.contributions?.[node.id]
                || node.plannerInfo?.patternType === 'GOAP' || node.plannerInfo?.patternType === 'BDI') ? 70
                : compound && node.plannerInfo?.patternType === 'P2P' ? 40
                // reads and writes between the board and each agent carry key labels both ways
                : compound && node.plannerInfo?.patternType === 'BLACKBOARD' ? 110 : undefined,
            // a peer-to-peer graph is a long chain of agents and keys: it reads better left to right
            direction: compound && node.plannerInfo?.patternType === 'P2P' ? 'RIGHT' : undefined,
            wrap: compound && node.plannerInfo?.patternType === 'P2P',
        });
        if (!compound) {
            return;
        }
        // nodes are listed in drawing order (the layout keeps it): helpers before, children, helpers after
        const ids = node.children.map(child => child.id);
        switch (node.kind) {
            case 'PARALLEL':
            case 'PARALLEL_MAPPER':
            case 'CONDITIONAL': {
                const fork = helper(node, 'fork');
                node.children.forEach(child => visit(child, node.id));
                const join = helper(node, 'join');
                node.children.forEach(child => {
                    // a conditional branch shows its activation condition; a mapper runs its agent per item
                    // (a loop branch keeps its exit condition in the guard, its activation apart)
                    const label = node.kind === 'CONDITIONAL' ? (child.kind === 'LOOP' ? child.guard?.activation : child.guard?.label)
                            : node.kind === 'PARALLEL_MAPPER' ? 'each item' : undefined;
                    edge(fork, child.id, node.kind === 'CONDITIONAL' ? 'GUARD' : 'FORK', label);
                    edge(child.id, join, 'JOIN');
                });
                break;
            }
            case 'SEQUENCE':
                node.children.forEach(child => visit(child, node.id));
                ids.slice(1).forEach((id, i) => edge(ids[i], id, 'SEQUENCE'));
                break;
            case 'LOOP':
                node.children.forEach(child => visit(child, node.id));
                ids.slice(1).forEach((id, i) => edge(ids[i], id, 'SEQUENCE'));
                edge(ids[ids.length - 1], ids[0], 'BACK', loopLabel(node.guard));
                break;
            case 'SUPERVISOR':
            case 'PLANNER': {
                // what an AgenticTopologyContributor of the application draws replaces the generic view
                const contribution = system.contributions?.[node.id];
                if (contribution) {
                    node.children.forEach(child => visit(child, node.id));
                    // a node id given twice is drawn once (Cytoscape refuses duplicate ids)
                    contribution.nodes?.filter((extra, i, all) => all.findIndex(other => other.id === extra.id) === i)
                        .forEach(extra => nodes.push({ id: extra.id, label: extra.label, kind: 'CONTRIBUTED',
                            parent: node.id, compound: false, width: Math.max(70, extra.label.length * 7 + 24), height: 30 }));
                    contribution.edges?.forEach(extra => edge(extra.source, extra.target, 'CONTRIBUTED', extra.label ?? undefined));
                    break;
                }
                if (node.plannerInfo?.patternType === 'GOAP') {
                    goalGraph(node);
                    break;
                }
                if (node.plannerInfo?.patternType === 'P2P') {
                    peerGraph(node);
                    break;
                }
                if (node.plannerInfo?.patternType === 'BLACKBOARD') {
                    boardGraph(node);
                    break;
                }
                if (node.plannerInfo?.patternType === 'BDI' && node.plannerInfo.desires?.length) {
                    desireGraph(node);
                    break;
                }
                if (isDebate(node)) {
                    debateGraph(node);
                    break;
                }
                if (node.plannerInfo?.patternType === 'VOTING') {
                    votingGraph(node);
                    break;
                }
                // sub-agents are chosen at runtime: candidates of a hub, with no order between them
                const hub = helper(node, 'hub');
                node.children.forEach(child => visit(child, node.id));
                ids.forEach(id => edge(hub, id, 'CANDIDATE'));
                break;
            }
            default:
                node.children.forEach(child => visit(child, node.id));
                break;
        }
    };
    visit(system.root, undefined);
    return { nodes, edges };
}

/** Kinds drawn as a single box in the data-flow view: the agents (a workflow is only an agent when it has no children). */
function isAgent(node) {
    return !(node.children?.length > 0);
}

/**
 * Turns an AgenticSystem topology into its data-flow graph: the agents, with an edge per AgenticScope key from each
 * agent writing it (output key) to each other agent reading it (input keys), labelled with the key. Agent ids are the
 * topology node ids, as in the structure view. A key no agent writes comes from:
 * <ol>
 * <li>the caller when the root method takes it: a system input node;</li>
 * <li>the items of an enclosing parallel mapper: edges from the sources of the mapper's input keys;</li>
 * <li>an enclosing supervisor, which passes arguments to the agents it calls: an edge from the supervisor node;</li>
 * <li>otherwise an unknown origin, drawn as a system input too.</li>
 * </ol>
 *
 * @return {{nodes: Array<{id, label, kind, compound, helper?, inputKey?}>, edges: Array<{id, source, target, kind, label}>}}
 */
export function toDataFlowGraph(system) {
    const agents = []; // { node, ancestors } in tree order
    const collect = (node, ancestors) => isAgent(node) ? agents.push({ node, ancestors })
        : node.children.forEach(child => collect(child, [node, ...ancestors]));
    collect(system.root, []);

    const writers = new Map();
    agents.forEach(({ node }) => {
        if (node.outputKey) {
            writers.set(node.outputKey, [...(writers.get(node.outputKey) ?? []), node.id]);
        }
    });
    const nodes = [];
    const edges = [];
    const inputNode = key => {
        const id = 'key' + HELPER_SEPARATOR + key;
        if (!nodes.some(n => n.id === id)) {
            nodes.push({ id, label: key, kind: 'INPUT_KEY', compound: false, helper: true, inputKey: true,
                width: Math.max(60, key.length * 7 + 24), height: 28 });
        }
        return id;
    };
    const readers = [...agents]; // agents, then suppliers, whose input keys are drawn
    const supplier = (node, ancestors) => {
        if (!nodes.some(n => n.id === node.id)) {
            nodes.push({ id: node.id, label: node.name, kind: node.kind, pattern: node.plannerInfo?.patternType, compound: false });
            readers.push({ node, ancestors });
        }
        return node.id;
    };
    /** Sources of a key, from the point of view of an agent with these ancestors: [{ id, kind, label }] */
    const sourcesOf = (key, ancestors) => {
        if (writers.has(key)) {
            return writers.get(key).map(id => ({ id, kind: 'DATA', label: key }));
        }
        if ((system.root.inputKeys ?? []).includes(key)) {
            return [{ id: inputNode(key), kind: 'INPUT', label: key }];
        }
        const mapper = ancestors.find(a => a.kind === 'PARALLEL_MAPPER');
        if (mapper?.inputKeys?.length) {
            return mapper.inputKeys.flatMap(items => sourcesOf(items, ancestors.slice(ancestors.indexOf(mapper) + 1))
                .map(source => ({ ...source, label: `${key} ← each ${items}` })));
        }
        const supervisor = ancestors.find(a => a.kind === 'SUPERVISOR');
        if (supervisor) {
            return [{ id: supplier(supervisor, ancestors.slice(ancestors.indexOf(supervisor) + 1)), kind: 'ARGUMENT',
                label: key }];
        }
        return [{ id: inputNode(key), kind: 'INPUT', label: key }];
    };

    // system inputs and suppliers first, in the order they are first read, so they sit on top of the layout
    const reads = [];
    for (let i = 0; i < readers.length; i++) { // suppliers found on the way are appended, and read too
        const { node, ancestors } = readers[i];
        (node.inputKeys ?? []).forEach(key => sourcesOf(key, ancestors).forEach(source => reads.push({ source, reader: node })));
    }
    agents.forEach(({ node }) => nodes.push({ id: node.id, label: node.name, kind: node.kind, pattern: node.plannerInfo?.patternType, compound: false }));
    // system inputs, then suppliers, then agents: the layout breaks cycles against this order, so sources stay on top
    nodes.sort((a, b) => (b.inputKey ? 1 : 0) - (a.inputKey ? 1 : 0));
    // one edge per (source, reader) pair, listing every key it carries
    reads.filter(({ source, reader }) => source.id !== reader.id).forEach(({ source, reader }) => {
        const id = `${source.id}->${reader.id}:${source.kind}`;
        const existing = edges.find(e => e.id === id);
        if (existing) {
            existing.label += '\n' + source.label;
        } else {
            edges.push({ id, source: source.id, target: reader.id, kind: source.kind, label: source.label });
        }
    });
    return { nodes, edges, dataFlow: true };
}

/** " ⚠" for a node with warnings of the topology checks, " ⓘ" for one with hints only, "" otherwise. */
export function warningBadge(system, nodeId) {
    const levels = (system?.warnings ?? []).filter(w => w.nodeId === nodeId).map(w => w.level);
    return levels.includes('WARNING') ? ' ⚠' : levels.length ? ' ⓘ' : '';
}

function labelWidth(label) {
    return Math.max(0, ...(label ?? '').split('\n').map(line => line.length)) * EDGE_LABEL_CHAR_WIDTH;
}

/** "max 5", then the exit condition and when it is tested, one per line. */
/** A debate planner draws its debaters and judge; it needs at least two debaters and a judge (as DebatePlanner). */
function isDebate(node) {
    return node.plannerInfo?.patternType === 'DEBATE' && node.children?.length >= 3;
}

/** A desire of a BDI planner, as drawn: its name and priority. */
export function desireLabel(desire) {
    return desire.priority != null ? `${desire.name} · priority ${desire.priority}` : desire.name;
}

function loopLabel(guard) {
    if (!guard) {
        return undefined;
    }
    const lines = [`max ${guard.maxIterations}`];
    if (guard.label) {
        lines.push(`until ${guard.label}`);
        if (guard.testExitAtLoopEnd) {
            lines.push('(tested at the end)');
        }
    }
    return lines.join('\n');
}

/**
 * Topology graph of one agentic system: Cytoscape.js rendering of an ELK layout, nested workflows as compound nodes.
 * Fires 'node-selected' (detail: { nodeId }) when a topology node is clicked, with a null id when the background is.
 */
export class QwcAgenticGraph extends observeState(LitElement) {

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            min-height: 480px;
        }
        #graph {
            flex: 1;
            min-height: 480px;
            border: 1px solid var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-m);
        }
        .error {
            color: var(--lumo-error-text-color);
        }
        .toolbar {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: var(--lumo-space-m);
        }
        .views {
            display: inline-flex;
            gap: var(--lumo-space-xs);
        }
        .legend {
            display: flex;
            flex-wrap: wrap;
            gap: var(--lumo-space-m);
            padding: var(--lumo-space-xs) 0;
            font-size: var(--lumo-font-size-s);
            color: var(--lumo-secondary-text-color);
        }
        .legend-item {
            display: inline-flex;
            align-items: center;
            gap: var(--lumo-space-xs);
        }
        .swatch {
            display: inline-block;
            width: 22px;
            height: 12px;
            border: 2px solid;
            border-radius: 4px;
            box-sizing: border-box;
        }
        .swatch.cut-rectangle {
            border-radius: 0;
            clip-path: polygon(4px 0, calc(100% - 4px) 0, 100% 4px, 100% calc(100% - 4px), calc(100% - 4px) 100%, 4px 100%, 0 calc(100% - 4px), 0 4px);
        }
        .swatch.round-hexagon {
            border-radius: 6px / 50%;
        }
        .swatch.ellipse {
            border-radius: 50%;
        }
        .swatch.rhomboid {
            transform: skewX(-20deg);
        }
        .swatch.double {
            border-width: 4px;
        }
    `;

    static properties = {
        system: { type: Object },
        /** 'structure' (workflows as nested boxes) or 'dataflow' (agents linked by the scope keys they share) */
        view: { type: String },
        _error: { state: true },
    };

    constructor() {
        super();
        this.view = 'structure';
        this._resetOverlay();
    }

    /**
     * What the current run shows on the graph (node states, node information, path edges), kept apart from the
     * Cytoscape elements so that a redraw (other view, theme) shows it again.
     */
    _resetOverlay() {
        this._overlay = { states: new Map(), infos: new Map(), paths: new Map() };
    }

    willUpdate(changed) {
        if (changed.has('system') && changed.get('system') !== undefined) {
            this._resetOverlay();
        }
        if (changed.has('system') || changed.has('view')) {
            this._graph = this.system ? (this.view === 'dataflow' ? toDataFlowGraph(this.system) : toGraph(this.system))
                : undefined;
        }
    }

    render() {
        return html`
            ${this._error ? html`<div class="error">Graph failed: ${this._error}</div>` : ''}
            <div class="toolbar">
                <span class="views" role="group" aria-label="View">
                    ${[['structure', 'Structure'], ['dataflow', 'Data flow']].map(([view, label]) => html`
                        <vaadin-button theme="small ${this.view === view ? 'primary' : 'tertiary'}" data-view=${view}
                                       aria-pressed=${this.view === view} @click=${() => this.view = view}>${label}</vaadin-button>`)}
                </span>
                ${this._renderLegend()}
            </div>
            <div id="graph"></div>`;
    }

    /** Lists the node kinds drawn in the current view, drawn like in the graph; planners by pattern when known. */
    _renderLegend() {
        const entries = new Map();
        (this._graph?.nodes ?? []).filter(n => !n.helper || n.inputKey).forEach(n => {
            const label = kindLabel(n.kind, n.pattern);
            if (!entries.has(label)) {
                entries.set(label, n.kind);
            }
        });
        const order = Object.keys(KIND_STYLES);
        const legend = [...entries].sort(([, a], [, b]) => order.indexOf(a) - order.indexOf(b));
        return html`<div class="legend">${legend.map(([label, kind]) => {
            const style = KIND_STYLES[kind] ?? KIND_STYLES.NON_AI_AGENT;
            return html`<span class="legend-item" data-kind=${kind}>
                <span class="swatch ${style.shape} ${style.border}"
                      style="border-color: ${style.color}; border-style: ${style.border}"></span>
                ${label}</span>`;
        })}
        ${this.view === 'dataflow' ? html`<span class="legend-item">key: writer → reader</span>` : ''}</div>`;
    }

    updated(changed) {
        if (changed.has('system') || changed.has('view')) {
            this._draw();
        } else if (this._cy && this._appliedTheme !== themeState.theme?.name) {
            // observeState re-renders on theme changes: restyle the canvas with the new Lumo colors
            this._appliedTheme = themeState.theme?.name;
            this._cy.style(this._style());
        }
    }

    firstUpdated() {
        // the canvas follows its container (e.g. when a details panel opens next to it)
        this._resizeObserver = new ResizeObserver(() => {
            if (this._cy) {
                this._cy.resize();
                // keeps what the user zoomed or panned to (the timeline grows during a run, side panels open)
                if (!this._viewportMoved) {
                    this._fit();
                }
            }
        });
        this._resizeObserver.observe(this.renderRoot.querySelector('#graph'));
    }

    disconnectedCallback() {
        this._stopPulse();
        this._resizeObserver?.disconnect();
        this._cy?.destroy();
        this._cy = undefined;
        super.disconnectedCallback();
    }

    /** Fits the whole graph, without zooming small graphs above 100%. */
    _fit() {
        this._fitting = true;
        try {
            this._cy.fit(undefined, 30);
            if (this._cy.zoom() > 1) {
                this._cy.zoom(1);
                this._cy.center();
            }
        } finally {
            this._fitting = false;
        }
    }

    async _draw() {
        this._cy?.destroy();
        this._cy = undefined;
        this.layoutDone = false;
        if (!this.system) {
            return;
        }
        const system = this.system;
        try {
            const graph = this._graph;
            const positions = await this._elkLayout(graph);
            if (system !== this.system || graph !== this._graph) {
                return; // another system or view was selected while laying out
            }
            this._cy = cytoscape({
                container: this.renderRoot.querySelector('#graph'),
                elements: this._elements(graph, positions),
                layout: { name: 'preset', fit: true, padding: 30 },
                style: this._style(),
                autoungrabify: true,
                wheelSensitivity: 0.2,
            });
            this._viewportMoved = false;
            this._fit();
            // any other viewport change is the user's (wheel, drag, pinch)
            this._cy.on('viewport', () => {
                if (!this._fitting) {
                    this._viewportMoved = true;
                }
            });
            this._applyOverlay();
            // a click on a node selects it, a click on the background clears the selection (nodeId null)
            this._cy.on('tap', event => {
                // helpers and the nodes of a topology contributor are not agents: nothing to show
                const node = event.target !== this._cy && event.target.isNode?.() && !event.target.data('helper')
                    && !event.target.hasClass('contributed') && !event.target.hasClass('goal') && !event.target.hasClass('board')
                    ? event.target : null;
                if (event.target === this._cy || node) {
                    this.dispatchEvent(new CustomEvent('node-selected', {
                        detail: { nodeId: node?.id() ?? null }, bubbles: true, composed: true,
                    }));
                }
            });
            this._appliedTheme = themeState.theme?.name;
            this._error = undefined;
            this.layoutDone = true;
            this.dispatchEvent(new CustomEvent('graph-rendered', { bubbles: true, composed: true }));
        } catch (e) {
            this._error = e?.message || String(e);
        }
    }

    /**
     * Highlights a node: state is one of planned, running, waiting (for human input), done, failed, recovered (null clears
     * it).
     * @return false when the node id is unknown
     */
    highlight(nodeId, state) {
        state ? this._overlay.states.set(nodeId, state) : this._overlay.states.delete(nodeId);
        return this._highlight(nodeId, state);
    }

    _highlight(nodeId, state) {
        const node = this._cy?.getElementById(nodeId);
        if (!node || node.empty()) {
            return false;
        }
        const previous = STATES.find(s => node.hasClass(s));
        node.removeClass(STATES.join(' '));
        if (state) {
            node.addClass(state);
        }
        node.removeStyle(PULSE_STYLES);
        node.removeScratch('afterglow');
        if (ACTIVE_STATES.includes(state)) {
            this._pulse();
        } else if (previous === 'running' && state && node.isChildless()) {
            // a run went through it (not a replay or a redraw): a fading halo marks where it just was, at once (the timer
            // fades it)
            node.scratch('afterglow', performance.now());
            node.style({ 'underlay-color': node.style('border-color'), 'underlay-shape': 'round-rectangle',
                'underlay-opacity': 0.4, 'underlay-padding': 6 });
            this._pulse();
        }
        return true;
    }

    /**
     * Animates the halo of the active nodes and the afterglow of the finished ones: one timer for all of them, stopped
     * when none is left. With reduced motion the halos stand still (the stylesheet's halo while active).
     */
    _pulse() {
        if (this._pulseTimer) {
            return;
        }
        this._pulseTimer = setInterval(() => {
            const still = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
            if (!this._cy) {
                this._stopPulse();
                return;
            }
            const now = performance.now();
            const wave = (1 - Math.cos((now % PULSE_PERIOD_MS) / PULSE_PERIOD_MS * 2 * Math.PI)) / 2;
            const agents = this._cy.nodes(ACTIVE_STATES.map(state => '.' + state + ':childless').join(', '));
            const boxes = this._cy.nodes('.running:parent');
            if (!still) {
                agents.style({ 'underlay-opacity': 0.12 + 0.3 * wave, 'underlay-padding': 4 + 10 * wave });
                boxes.style({ 'border-width': 3 + 3 * wave });
            }
            const glowing = this._cy.nodes(':childless').filter(node => node.scratch('afterglow') !== undefined);
            glowing.forEach(node => {
                const age = (now - node.scratch('afterglow')) / AFTERGLOW_MS;
                if (age >= 1) {
                    node.removeScratch('afterglow');
                    node.removeStyle(PULSE_STYLES);
                } else {
                    node.style({ 'underlay-color': node.style('border-color'), 'underlay-shape': 'round-rectangle',
                        'underlay-opacity': still ? 0.35 : 0.4 * (1 - age), 'underlay-padding': still ? 8 : 6 + 8 * age });
                }
            });
            if (glowing.empty() && (still || agents.empty() && boxes.empty())) {
                this._stopPulse();
            }
        }, 50);
    }

    _stopPulse() {
        clearInterval(this._pulseTimer);
        this._pulseTimer = undefined;
    }

    clearSelection() {
        this._cy?.nodes().unselect();
    }

    /**
     * Shows run information on a node (e.g. "1.2 s · 46 tokens", "×3"): a second label line under an agent, after the
     * title of a box. Null removes it.
     * @return false when the node id is unknown
     */
    setNodeInfo(nodeId, text) {
        text ? this._overlay.infos.set(nodeId, text) : this._overlay.infos.delete(nodeId);
        return this._label(nodeId);
    }

    /** Shows the run information of a node, and its step numbers when no candidate edge leads to it. */
    _label(nodeId) {
        const node = this._cy?.getElementById(nodeId);
        if (!node || node.empty()) {
            return false;
        }
        const base = node.data('baseLabel') ?? node.data('label');
        node.data('baseLabel', base);
        const steps = this._stepsOnNode(node) ? this._overlay.paths.get(nodeId) : undefined;
        const parts = [this._overlay.infos.get(nodeId), steps ? `step${steps.includes(',') ? 's' : ''} ${steps}` : undefined]
            .filter(Boolean);
        if (node.isParent()) {
            const label = parts.length ? `${base}   ${parts.join(' · ')}` : base;
            node.data('label', label);
            // a narrow box widens to its title (it grows to the right, see min-width-bias)
            node.style('min-width', Math.max(node.data('width') ?? 0, label.length * TITLE_CHAR_WIDTH + 24 - 2 * COMPOUND_PADDING));
        } else {
            node.data('label', [base, ...parts].join('\n'));
            // one line more than the box holds makes it taller
            parts.length > 1 ? node.style('height', LEAF_HEIGHT + 14 * (parts.length - 1)) : node.removeStyle('height');
        }
        return true;
    }

    _stepsOnNode(node) {
        return this._overlay.paths.has(node.id()) && node.incomers('edge.candidate').empty();
    }

    /** @return the run information shown on a node, or null */
    nodeInfo(nodeId) {
        const node = this._cy?.getElementById(nodeId);
        if (!node || node.empty() || node.data('baseLabel') === undefined) {
            return null;
        }
        const label = node.data('label');
        const base = node.data('baseLabel');
        return label === base ? null : label.substring(base.length).trim();
    }

    /**
     * Marks a sub-agent of a supervisor or planner as called: its candidate edge becomes solid, labelled with the
     * call numbers (e.g. "2", "1, 3"). Null clears it.
     * @return false when the structure view has no candidate edge to that node (e.g. in the data-flow view)
     */
    setPath(nodeId, label) {
        label ? this._overlay.paths.set(nodeId, label) : this._overlay.paths.delete(nodeId);
        return this._setPath(nodeId, label);
    }

    _setPath(nodeId, label) {
        const edges = this._cy?.edges('.candidate').filter(edge => edge.target().id() === nodeId);
        if (!edges || edges.empty()) {
            // no candidate edge (a contributed drawing, the data-flow view): the numbers go on the node
            return this._label(nodeId);
        }
        edges.toggleClass('taken', !!label);
        edges.data('label', label ?? '');
        return true;
    }

    /** @return the sub-agents marked as called, with their call numbers: [{ nodeId, label }] */
    takenPath() {
        if (!this._cy) {
            return [];
        }
        const onNodes = [...this._overlay.paths].filter(([id]) => {
            const node = this._cy.getElementById(id);
            return !node.empty() && this._stepsOnNode(node);
        }).map(([nodeId, label]) => ({ nodeId, label }));
        return [...this._cy.edges('.taken').map(edge => ({ nodeId: edge.target().id(), label: edge.data('label') })), ...onNodes];
    }

    _applyOverlay() {
        this._overlay.states.forEach((state, nodeId) => this._highlight(nodeId, state));
        this._overlay.paths.forEach((label, nodeId) => this._setPath(nodeId, label));
        this._overlay.infos.forEach((text, nodeId) => this._label(nodeId));
    }

    /** Clears the highlights and run information of a previous run. */
    resetRun() {
        this._resetOverlay();
        this._cy?.edges('.taken').forEach(edge => {
            edge.removeClass('taken');
            edge.data('label', '');
        });
        this.clearHighlights();
        this._cy?.nodes().forEach(node => {
            if (node.data('baseLabel') !== undefined) {
                node.data('label', node.data('baseLabel'));
                node.removeStyle('height min-width');
            }
        });
    }

    clearHighlights() {
        this._overlay.states.clear();
        this._cy?.nodes().removeClass(STATES.join(' ')).removeStyle(PULSE_STYLES).removeScratch('afterglow');
    }

    /**
     * @return the highlight state of a node, or null
     */
    stateOf(nodeId) {
        const node = this._cy?.getElementById(nodeId);
        return node && !node.empty() ? STATES.find(s => node.hasClass(s)) ?? null : null;
    }

    /**
     * @return node id -> rendered bounding box and parent id, for checks
     */
    renderedNodes() {
        const result = {};
        this._cy?.nodes().forEach(n => {
            const bb = n.boundingBox({ includeLabels: false });
            result[n.id()] = { x1: bb.x1, y1: bb.y1, x2: bb.x2, y2: bb.y2, parent: n.parent().id() ?? null,
                kind: n.data('kind') };
        });
        return result;
    }

    /**
     * @return edge id -> source, target, kind class, label and rendered bounding box (label included), for checks
     */
    renderedEdges() {
        const result = {};
        this._cy?.edges().forEach(e => {
            const bb = e.boundingBox({ includeLabels: true });
            result[e.id()] = { source: e.source().id(), target: e.target().id(), kind: e.classes()[0],
                label: e.data('label'), x1: bb.x1, y1: bb.y1, x2: bb.x2, y2: bb.y2 };
        });
        return result;
    }

    async _elkLayout(graph) {
        const elk = new window.ELK();
        // each compound is laid out on its own (ELK's default hierarchy handling) with its edges, so siblings keep
        // their declaration order: hierarchical layout (INCLUDE_CHILDREN) ignores the model order
        const options = {
            'elk.algorithm': 'layered',
            'elk.direction': 'DOWN',
            'elk.spacing.nodeNode': '30',
            'elk.layered.spacing.nodeNodeBetweenLayers': '40',
            'elk.layered.nodePlacement.strategy': 'BRANDES_KOEPF',
            // centers a fork over the branches it fans out to (and a join under them)
            'elk.layered.nodePlacement.bk.fixedAlignment': 'BALANCED',
            'elk.layered.considerModelOrder.strategy': 'NODES_AND_EDGES',
            'elk.layered.crossingMinimization.forceNodeModelOrder': 'true',
            // unconnected siblings would otherwise be packed as separate components, sorted by id
            'elk.separateConnectedComponents': 'false',
            // data-flow graphs have cycles (a loop reads what it wrote): reverse the edges against the agent order
            'elk.layered.cycleBreaking.strategy': 'MODEL_ORDER',
        };
        if (graph.dataFlow) {
            // edges carry key labels and are drawn along the routes ELK computes, around the nodes
            options['elk.layered.spacing.nodeNodeBetweenLayers'] = '50';
            options['elk.spacing.nodeNode'] = '50';
            options['elk.spacing.edgeNode'] = '20';
            options['elk.edgeRouting'] = 'ORTHOGONAL';
            options['elk.edgeLabels.placement'] = 'CENTER';
        }
        const root = { id: 'root', layoutOptions: options, children: [], edges: [] };
        const byId = new Map(graph.nodes.map(n => [n.id, { node: n, elk: { id: n.id } }]));
        for (const { node, elk: elkNode } of byId.values()) {
            if (node.compound) {
                // Cytoscape draws the box with a uniform padding; the extra top space holds the title
                elkNode.layoutOptions = {
                    ...options,
                    'elk.padding': `[top=${COMPOUND_PADDING + TITLE_SPACE},left=${COMPOUND_PADDING},`
                            + `bottom=${COMPOUND_PADDING},right=${COMPOUND_PADDING + (node.paddingRight ?? 0)}]`,
                    ...(node.layerSpacing ? { 'elk.layered.spacing.nodeNodeBetweenLayers': String(node.layerSpacing) } : {}),
                    ...(node.direction ? { 'elk.direction': node.direction } : {}),
                    // a long chain is folded into rows (ELK graph wrapping) to keep a readable aspect ratio
                    ...(node.wrap ? { 'elk.layered.wrapping.strategy': 'MULTI_EDGE', 'elk.aspectRatio': '2.0',
                        'elk.layered.wrapping.additionalEdgeSpacing': '20' } : {}),
                };
                elkNode.children = [];
                elkNode.edges = [];
            } else {
                elkNode.width = node.width ?? LEAF_WIDTH;
                elkNode.height = node.height ?? LEAF_HEIGHT;
            }
        }
        for (const { node, elk: elkNode } of byId.values()) {
            (node.parent ? byId.get(node.parent).elk : root).children.push(elkNode);
        }
        // an edge belongs to the parent of its ends (all edges link siblings); back-edges are drawn but not laid
        // out, so the forward order is kept
        for (const e of graph.edges.filter(e => e.kind !== 'BACK')) {
            const parent = byId.get(e.source).node.parent;
            const owner = parent && parent === byId.get(e.target).node.parent ? byId.get(parent).elk : root;
            owner.edges.push({ id: e.id, sources: [e.source], targets: [e.target],
                // data-flow edges carry key labels: ELK reserves room for them
                labels: graph.dataFlow && e.label ? [{ text: e.label, width: labelWidth(e.label),
                    height: 12 * e.label.split('\n').length }] : undefined });
        }
        const layout = await elk.layout(root);

        // ELK gives top-left corners relative to the parent; Cytoscape wants absolute centers of leaf nodes,
        // compound nodes are sized from their children (their ELK size is kept for the title space)
        const positions = {};
        const walk = (node, offsetX, offsetY) => {
            const x = offsetX + (node.x ?? 0);
            const y = offsetY + (node.y ?? 0);
            if (!node.children || node.children.length === 0) {
                positions[node.id] = { x: x + node.width / 2, y: y + node.height / 2 };
            } else {
                // Cytoscape adds its padding around min-width/min-height
                positions[node.id] = { width: node.width - 2 * COMPOUND_PADDING, height: node.height - 2 * COMPOUND_PADDING };
            }
            positions[node.id].box = { x1: x, y1: y, x2: x + node.width, y2: y + node.height };
            (node.children ?? []).forEach(child => walk(child, x, y));
        };
        layout.children.forEach(child => walk(child, 0, 0));
        // routes of the edges owned by the root (all data-flow edges), in absolute coordinates
        positions.routes = {};
        (layout.edges ?? []).forEach(edge => {
            const section = edge.sections?.[0];
            if (section) {
                positions.routes[edge.id] = { start: section.startPoint, bends: section.bendPoints ?? [], end: section.endPoint };
            }
        });
        return positions;
    }

    _elements(graph, positions) {
        return [
            ...graph.nodes.map(n => ({
                group: 'nodes',
                data: { id: n.id, label: n.label, kind: n.kind, parent: n.parent, helper: n.helper ?? false,
                    // a node sized from its label (input key, goal) keeps its size; compounds get theirs from ELK
                    parentKind: n.parentKind, width: n.width ?? positions[n.id]?.width,
                    height: n.height ?? positions[n.id]?.height },
                position: n.compound ? undefined : positions[n.id],
                classes: [n.kind.toLowerCase(), n.compound ? 'workflow' : 'leaf', n.helper ? 'helper' : '']
                        .filter(Boolean).join(' '),
            })),
            ...graph.edges.map(e => ({
                group: 'edges',
                data: { id: e.id, source: e.source, target: e.target, label: e.label ?? '' },
                // the curve style of a routed edge comes from a class: Cytoscape ignores it in an element's initial bypass
                classes: e.kind.toLowerCase() + (graph.dataFlow && positions.routes[e.id]
                    ? (positions.routes[e.id].bends.length ? ' routed' : ' routed-straight') : ''),
                style: positions.routes[e.id] && graph.dataFlow ? this._routedEdgeStyle(positions, e)
                        : e.kind !== 'BACK' ? undefined
                        : e.source !== e.target ? this._backEdgeStyle(graph, positions, e)
                        // a one-agent loop body: the label goes right of the self-loop curl
                        : { 'text-margin-x': labelWidth(e.label) / 2 + 30 },
            })),
        ];
    }

    /**
     * Draws an edge along its ELK route: end points relative to the node centers, bend points as Cytoscape segments
     * (weight along the line between the node centers, distance perpendicular to it).
     */
    _routedEdgeStyle(positions, edge) {
        const route = positions.routes[edge.id];
        const s = positions[edge.source];
        const t = positions[edge.target];
        const style = {
            'source-endpoint': `${route.start.x - s.x}px ${route.start.y - s.y}px`,
            'target-endpoint': `${route.end.x - t.x}px ${route.end.y - t.y}px`,
        };
        const dx = t.x - s.x;
        const dy = t.y - s.y;
        const length2 = dx * dx + dy * dy;
        if (route.bends.length === 0 || length2 === 0) {
            return style;
        }
        const length = Math.sqrt(length2);
        return {
            ...style,
            'segment-weights': route.bends.map(b => ((b.x - s.x) * dx + (b.y - s.y) * dy) / length2).join(' '),
            'segment-distances': route.bends.map(b => ((b.x - s.x) * -dy + (b.y - s.y) * dx) / length).join(' '),
        };
    }

    /**
     * Places the back-edge bracket right of every agent of the loop body, the label right of the bracket.
     * Segment distances are measured from the line between the source and target centers.
     */
    _backEdgeStyle(graph, positions, edge) {
        const loop = graph.nodes.find(n => n.id === edge.source).parent;
        const right = Math.max(...graph.nodes.filter(n => n.parent === loop).map(n => positions[n.id].box.x2));
        const centre = id => (positions[id].box.x1 + positions[id].box.x2) / 2;
        const distance = right + BACK_EDGE_GAP - (centre(edge.source) + centre(edge.target)) / 2;
        return {
            'segment-distances': `${distance} ${distance}`,
            'text-margin-x': labelWidth(edge.label) / 2 + 6,
        };
    }

    _style() {
        const v = (name, fallback) => getComputedStyle(document.body).getPropertyValue(name).trim() || fallback;
        const text = v('--lumo-body-text-color', '#1f2937');
        const secondary = v('--lumo-secondary-text-color', '#6b7280');
        const base = v('--lumo-base-color', '#ffffff');
        const contrast = v('--lumo-contrast-30pct', '#9ca3af');
        const primary = v('--lumo-primary-color', '#1676f3');
        const success = v('--lumo-success-color', '#16a34a');
        const error = v('--lumo-error-color', '#dc2626');
        return [
            { selector: 'node', style: {
                'label': 'data(label)', 'color': text, 'font-size': 12, 'text-valign': 'center', 'text-halign': 'center',
                'text-wrap': 'wrap', 'text-max-width': LEAF_WIDTH - 12, 'line-height': 1.3,
                'background-color': base, 'border-width': 2, 'border-color': contrast,
                'width': LEAF_WIDTH, 'height': LEAF_HEIGHT, 'shape': 'round-rectangle',
            } },
            { selector: ':parent', style: {
                'text-valign': 'top', 'text-halign': 'center', 'font-weight': 'bold',
                'text-max-width': 1000, 'background-opacity': 0.04, 'background-color': text, 'padding': COMPOUND_PADDING,
                // the box takes the ELK size, whose extra top space holds the title inside the box: an edge into the
                // box then stops at its border instead of crossing the title
                'min-width': 'data(width)', 'min-height': 'data(height)', 'min-height-bias-top': '100%',
                'min-height-bias-bottom': '0%', 'text-margin-y': 22,
                // extra width (room for a loop back-edge) goes to the right, where ELK reserved it
                'min-width-bias-left': '0%', 'min-width-bias-right': '100%',
            } },
            ...Object.entries(KIND_STYLES).map(([kind, k]) => ({ selector: `node.${kind.toLowerCase()}`, style: {
                'shape': k.shape, 'border-color': k.color, 'border-style': k.border, 'border-width': k.width ?? 2,
                ...(k.border === 'double' ? { 'border-width': 5 } : {}),
            } })),
            { selector: 'node.helper', style: {
                'shape': 'diamond', 'width': HELPER_SIZE, 'height': HELPER_SIZE, 'background-color': secondary,
                'border-width': 0, 'label': '', 'events': 'no',
            } },
            { selector: 'edge', style: {
                'width': 1.5, 'line-color': secondary, 'target-arrow-color': secondary, 'target-arrow-shape': 'triangle',
                'curve-style': 'bezier', 'label': 'data(label)', 'font-size': 10, 'color': secondary,
                'text-background-color': base, 'text-background-opacity': 1, 'text-background-padding': 2,
            } },
            // fan-out edges leave the fork without an arrow; joins converge on the join point
            { selector: 'edge.fork', style: { 'target-arrow-shape': 'triangle' } },
            { selector: 'edge.join', style: { 'target-arrow-shape': 'none' } },
            // the goal key of a goal-oriented planner
            { selector: 'node.board', style: {
                'width': 'data(width)', 'height': 'data(height)', 'font-weight': 'bold', 'background-color': base,
            } },
            { selector: 'edge.reads, edge.writes', style: {
                'text-rotation': 'none', 'font-family': 'monospace', 'text-wrap': 'wrap',
            } },
            { selector: 'node.desire, node.tally', style: {
                'width': 'data(width)', 'height': 'data(height)', 'font-weight': 'bold', 'background-color': base,
            } },
            { selector: 'node.goal', style: {
                'width': 'data(width)', 'height': 'data(height)', 'font-weight': 'bold', 'background-color': base,
            } },
            { selector: 'node.input_key, node.state_key', style: {
                'label': 'data(label)', 'events': 'no', 'width': 'data(width)', 'height': 'data(height)',
                'shape': 'round-tag', 'border-width': 2, 'background-color': base, 'font-style': 'italic',
            } },
            { selector: 'edge.data, edge.input, edge.argument, edge.depends', style: {
                'text-rotation': 'none', 'font-family': 'monospace', 'text-wrap': 'wrap',
            } },
            { selector: 'edge.routed', style: { 'curve-style': 'segments', 'edge-distances': 'node-position' } },
            { selector: 'edge.routed-straight', style: { 'curve-style': 'straight' } },
            { selector: 'edge.argument', style: {
                'line-style': 'dashed', 'line-color': KIND_STYLES.SUPERVISOR.color, 'target-arrow-color': KIND_STYLES.SUPERVISOR.color,
            } },
            { selector: 'edge.input', style: { 'line-style': 'dashed' } },
            { selector: 'node.hub', style: { 'shape': 'ellipse', 'width': 18, 'height': 18 } },
            { selector: 'node.hub[parentKind = "SUPERVISOR"]', style: { 'background-color': KIND_STYLES.SUPERVISOR.color } },
            { selector: 'node.hub[parentKind = "PLANNER"]', style: { 'background-color': KIND_STYLES.PLANNER.color } },
            { selector: 'edge.candidate', style: { 'line-style': 'dashed', 'arrow-scale': 0.8 } },
            // drawn by an AgenticTopologyContributor of the application
            { selector: 'edge.contributed', style: {
                'line-color': KIND_STYLES.PLANNER.color, 'target-arrow-color': KIND_STYLES.PLANNER.color,
                'color': text, 'font-style': 'italic',
            } },
            // a sub-agent the supervisor or planner called in the current run, labelled with the call numbers
            { selector: 'edge.candidate.taken', style: {
                'line-style': 'solid', 'width': 3, 'arrow-scale': 1, 'line-color': KIND_STYLES.SUPERVISOR.color,
                'target-arrow-color': KIND_STYLES.SUPERVISOR.color, 'color': text, 'font-size': 12, 'font-weight': 'bold',
                'z-index': 10,
            } },
            { selector: 'edge.guard', style: {
                'line-color': KIND_STYLES.CONDITIONAL.color, 'target-arrow-color': KIND_STYLES.CONDITIONAL.color,
                'color': text, 'font-style': 'italic',
            } },
            // the back-edge is a bracket on the right of the loop body, in the room the layout left for it: out of the
            // last agent, up, into the first one (a self-loop when the body is one agent); distances set per edge
            { selector: 'edge.back', style: {
                'curve-style': 'segments', 'segment-weights': '0 1', 'edge-distances': 'node-position',
                'line-style': 'dashed', 'line-color': KIND_STYLES.LOOP.color, 'target-arrow-color': KIND_STYLES.LOOP.color,
                'text-wrap': 'wrap', 'color': text, 'text-background-opacity': 0,
            } },
            { selector: 'edge.back:loop', style: {
                'curve-style': 'bezier', 'loop-direction': '90deg', 'loop-sweep': '-40deg', 'control-point-step-size': 60,
            } },
            { selector: 'node:selected', style: {
                'overlay-color': primary, 'overlay-opacity': 0.12, 'overlay-padding': 4,
            } },
            { selector: 'node.running', style: { 'border-width': 4, 'border-color': primary, 'border-style': 'solid' } },
            // active agents get a halo, pulsing (_pulse); on a box it would tint the whole box
            { selector: 'node.running:childless, node.waiting:childless', style: {
                'underlay-color': primary, 'underlay-opacity': 0.25, 'underlay-padding': 8, 'underlay-shape': 'round-rectangle',
            } },
            // on the path a goal-oriented planner computed, not run yet
            { selector: 'node.planned', style: { 'border-width': 3, 'border-color': primary, 'border-style': 'dashed' } },
            // a peer-to-peer agent whose inputs are all in the scope
            { selector: 'node.ready', style: { 'border-width': 3, 'border-color': primary, 'border-style': 'dotted' } },
            // a human-in-the-loop step waiting for the answer given in the run panel
            { selector: 'node.waiting', style: { 'border-width': 4, 'border-color': primary, 'border-style': 'dashed' } },
            { selector: 'node.done', style: { 'border-width': 3, 'border-color': success } },
            { selector: 'node.failed', style: { 'border-width': 4, 'border-color': error } },
            // an error handled by an @ErrorHandler (retry or replacement result)
            { selector: 'node.recovered', style: { 'border-width': 4, 'border-color': '#f59e0b' } },
            // still running when the run ended (cancelled, failed elsewhere, or the events were lost)
            { selector: 'node.interrupted', style: { 'border-width': 3, 'border-color': secondary, 'border-style': 'dashed' } },
            // comparing two runs: same output, changed, run by one of them only
            { selector: 'node.diff-same', style: { 'border-width': 3, 'border-color': success } },
            { selector: 'node.diff-changed', style: { 'border-width': 4, 'border-color': '#f59e0b' } },
            { selector: 'node.diff-only', style: { 'border-width': 4, 'border-color': '#8b5cf6', 'border-style': 'dashed' } },
        ];
    }
}

customElements.define('qwc-agentic-graph', QwcAgenticGraph);
