import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import { observeState } from 'lit-element-state';
import { themeState } from 'theme-state';
import cytoscape from 'cytoscape';
// elkjs maps its bare name to a CommonJS file; the UMD bundle registers window.ELK when imported
import 'elkjs/elk.bundled.js';

const LEAF_WIDTH = 150;
const LEAF_HEIGHT = 44;
const COMPOUND_PADDING = 20;
const TITLE_SPACE = 24;
const STATES = ['running', 'done', 'failed'];

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
    INPUT_KEY: 'System input',
};

/**
 * How each node kind is drawn; the graph style and the legend both use it. Agents are leaf shapes, workflows are
 * boxes told apart by their border. Hues are mid-saturation so they read on light and dark themes.
 */
export const KIND_STYLES = {
    AI_AGENT: { shape: 'round-rectangle', color: '#10b981', border: 'solid' },
    NON_AI_AGENT: { shape: 'cut-rectangle', color: '#64748b', border: 'solid' },
    HUMAN_IN_THE_LOOP: { shape: 'round-hexagon', color: '#f59e0b', border: 'solid' },
    SEQUENCE: { shape: 'round-rectangle', color: '#64748b', border: 'solid' },
    PARALLEL: { shape: 'round-rectangle', color: '#3b82f6', border: 'double' },
    PARALLEL_MAPPER: { shape: 'round-rectangle', color: '#06b6d4', border: 'double' },
    LOOP: { shape: 'round-rectangle', color: '#8b5cf6', border: 'dashed' },
    CONDITIONAL: { shape: 'round-rectangle', color: '#f97316', border: 'dotted' },
    SUPERVISOR: { shape: 'round-rectangle', color: '#ec4899', border: 'solid', width: 3 },
    PLANNER: { shape: 'round-rectangle', color: '#d946ef', border: 'solid', width: 3 },
    INPUT_KEY: { shape: 'round-tag', color: '#94a3b8', border: 'dashed' },
};

/** Separates a topology node id from the role of a helper node drawn for it (never used in topology ids). */
const HELPER_SEPARATOR = '::';
const HELPER_SIZE = 14;
/** Gap between the right side of a loop body and its back-edge bracket. */
const BACK_EDGE_GAP = 24;
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
    const edge = (source, target, kind, label) =>
        edges.push({ id: `${source}->${target}:${kind}`, source, target, kind, label });
    const helper = (parent, role) => {
        const id = parent.id + HELPER_SEPARATOR + role;
        nodes.push({ id, label: '', kind: role.toUpperCase(), parent: parent.id, parentKind: parent.kind, compound: false,
            helper: true, width: role === 'hub' ? 18 : HELPER_SIZE, height: role === 'hub' ? 18 : HELPER_SIZE });
        return id;
    };

    const visit = (node, parent) => {
        const compound = node.children?.length > 0;
        nodes.push({
            id: node.id,
            label: compound ? `${KIND_LABELS[node.kind] ?? node.kind} · ${node.name}` : node.name,
            kind: node.kind,
            parent,
            compound,
            // extra layout room: a loop draws its back-edge and label on the right, conditional guards label the fan-out
            paddingRight: compound && node.kind === 'LOOP' ? BACK_EDGE_GAP + labelWidth(loopLabel(node.guard)) + 12 : undefined,
            layerSpacing: compound && node.kind === 'CONDITIONAL' ? 70 : undefined,
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
                    const label = node.kind === 'CONDITIONAL' ? child.guard?.label
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
            nodes.push({ id: node.id, label: node.name, kind: node.kind, compound: false });
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
    agents.forEach(({ node }) => nodes.push({ id: node.id, label: node.name, kind: node.kind, compound: false }));
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

function labelWidth(label) {
    return Math.max(0, ...(label ?? '').split('\n').map(line => line.length)) * EDGE_LABEL_CHAR_WIDTH;
}

/** "max 5", then the exit condition and when it is tested, one per line. */
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
    }

    willUpdate(changed) {
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

    /** Lists the node kinds drawn in the current view, drawn like in the graph. */
    _renderLegend() {
        const kinds = [...new Set((this._graph?.nodes ?? []).filter(n => !n.helper || n.inputKey).map(n => n.kind))];
        const order = Object.keys(KIND_STYLES);
        kinds.sort((a, b) => order.indexOf(a) - order.indexOf(b));
        return html`<div class="legend">${kinds.map(kind => {
            const style = KIND_STYLES[kind] ?? KIND_STYLES.NON_AI_AGENT;
            return html`<span class="legend-item" data-kind=${kind}>
                <span class="swatch ${style.shape} ${style.border}"
                      style="border-color: ${style.color}; border-style: ${style.border}"></span>
                ${KIND_LABELS[kind] ?? kind}</span>`;
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
                this._fit();
            }
        });
        this._resizeObserver.observe(this.renderRoot.querySelector('#graph'));
    }

    disconnectedCallback() {
        this._resizeObserver?.disconnect();
        this._cy?.destroy();
        this._cy = undefined;
        super.disconnectedCallback();
    }

    /** Fits the whole graph, without zooming small graphs above 100%. */
    _fit() {
        this._cy.fit(undefined, 30);
        if (this._cy.zoom() > 1) {
            this._cy.zoom(1);
            this._cy.center();
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
            this._fit();
            // a click on a node selects it, a click on the background clears the selection (nodeId null)
            this._cy.on('tap', event => {
                const node = event.target !== this._cy && event.target.isNode?.() && !event.target.data('helper')
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
     * Highlights a node: state is one of running, done, failed (null clears it).
     * @return false when the node id is unknown
     */
    highlight(nodeId, state) {
        const node = this._cy?.getElementById(nodeId);
        if (!node || node.empty()) {
            return false;
        }
        node.removeClass(STATES.join(' '));
        if (state) {
            node.addClass(state);
        }
        return true;
    }

    clearSelection() {
        this._cy?.nodes().unselect();
    }

    clearHighlights() {
        this._cy?.nodes().removeClass(STATES.join(' '));
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
                    // compound size from ELK; an input key node is sized from its label
                    parentKind: n.parentKind, width: n.inputKey ? n.width : positions[n.id]?.width,
                    height: n.inputKey ? n.height : positions[n.id]?.height },
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
                'text-wrap': 'ellipsis', 'text-max-width': LEAF_WIDTH - 12,
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
            { selector: 'node.input_key', style: {
                'label': 'data(label)', 'events': 'no', 'width': 'data(width)', 'height': 'data(height)',
                'shape': 'round-tag', 'border-width': 2, 'background-color': base, 'font-style': 'italic',
            } },
            { selector: 'edge.data, edge.input, edge.argument', style: {
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
            { selector: 'node.running', style: { 'border-width': 4, 'border-color': primary } },
            { selector: 'node.done', style: { 'border-width': 3, 'border-color': success } },
            { selector: 'node.failed', style: { 'border-width': 4, 'border-color': error } },
        ];
    }
}

customElements.define('qwc-agentic-graph', QwcAgenticGraph);
