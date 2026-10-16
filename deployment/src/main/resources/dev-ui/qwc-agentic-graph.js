import { LitElement, html, css } from 'lit';
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
};

/** Separates a topology node id from the role of a helper node drawn for it (never used in topology ids). */
const HELPER_SEPARATOR = '::';

/**
 * Turns an AgenticSystem topology into graph nodes and edges.
 * A node with children is drawn as a compound node containing them; node ids are the topology node ids.
 * Edges have a kind: the layout ignores BACK edges, so cycles do not reorder the forward flow.
 *
 * @return {{nodes: Array<{id, label, kind, parent, helper?}>, edges: Array<{id, source, target, kind, label?}>}}
 */
export function toGraph(system) {
    const nodes = [];
    const edges = [];
    const edge = (source, target, kind, label) =>
        edges.push({ id: `${source}->${target}:${kind}`, source, target, kind, label });

    const visit = (node, parent) => {
        const compound = node.children?.length > 0;
        nodes.push({
            id: node.id,
            label: compound ? `${KIND_LABELS[node.kind] ?? node.kind} · ${node.name}` : node.name,
            kind: node.kind,
            parent,
            compound,
        });
        node.children?.forEach(child => visit(child, node.id));
        if (compound) {
            linkChildren(node, edge);
        }
    };
    visit(system.root, undefined);
    return { nodes, edges };
}

/**
 * Edges between the children of a workflow node, by kind.
 */
function linkChildren(node, edge) {
    const ids = node.children.map(child => child.id);
    switch (node.kind) {
        case 'SEQUENCE':
        case 'LOOP':
            ids.slice(1).forEach((id, i) => edge(ids[i], id, 'SEQUENCE'));
            break;
        default:
            // fan-out, guards and candidates come with the per-kind edges (T1.6b-d)
            break;
    }
}

/**
 * Topology graph of one agentic system: Cytoscape.js rendering of an ELK layout, nested workflows as compound nodes.
 * Fires 'node-selected' (detail: { nodeId }) when a topology node is clicked.
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
    `;

    static properties = {
        system: { type: Object },
        _error: { state: true },
    };

    render() {
        return html`
            ${this._error ? html`<div class="error">Graph failed: ${this._error}</div>` : ''}
            <div id="graph"></div>`;
    }

    updated(changed) {
        if (changed.has('system')) {
            this._draw();
        } else if (this._cy && this._appliedTheme !== themeState.theme?.name) {
            // observeState re-renders on theme changes: restyle the canvas with the new Lumo colors
            this._appliedTheme = themeState.theme?.name;
            this._cy.style(this._style());
        }
    }

    disconnectedCallback() {
        this._cy?.destroy();
        this._cy = undefined;
        super.disconnectedCallback();
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
            const graph = toGraph(system);
            const positions = await this._elkLayout(graph);
            if (system !== this.system) {
                return; // another system was selected while laying out
            }
            this._cy = cytoscape({
                container: this.renderRoot.querySelector('#graph'),
                elements: this._elements(graph, positions),
                layout: { name: 'preset', fit: true, padding: 30 },
                style: this._style(),
                autoungrabify: true,
                wheelSensitivity: 0.2,
            });
            // fit() zooms small graphs far above 100%
            if (this._cy.zoom() > 1) {
                this._cy.zoom(1);
                this._cy.center();
            }
            this._cy.on('tap', 'node', event => {
                if (!event.target.data('helper')) {
                    this.dispatchEvent(new CustomEvent('node-selected', {
                        detail: { nodeId: event.target.id() }, bubbles: true, composed: true,
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
            'elk.layered.considerModelOrder.strategy': 'NODES_AND_EDGES',
            'elk.layered.crossingMinimization.forceNodeModelOrder': 'true',
            // unconnected siblings would otherwise be packed as separate components, sorted by id
            'elk.separateConnectedComponents': 'false',
        };
        const root = { id: 'root', layoutOptions: options, children: [], edges: [] };
        const byId = new Map(graph.nodes.map(n => [n.id, { node: n, elk: { id: n.id } }]));
        for (const { node, elk: elkNode } of byId.values()) {
            if (node.compound) {
                // Cytoscape draws the box with a uniform padding; the extra top space holds the title
                elkNode.layoutOptions = {
                    ...options,
                    'elk.padding': `[top=${COMPOUND_PADDING + TITLE_SPACE},left=${COMPOUND_PADDING},bottom=${COMPOUND_PADDING},right=${COMPOUND_PADDING}]`,
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
            owner.edges.push({ id: e.id, sources: [e.source], targets: [e.target] });
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
            (node.children ?? []).forEach(child => walk(child, x, y));
        };
        layout.children.forEach(child => walk(child, 0, 0));
        return positions;
    }

    _elements(graph, positions) {
        return [
            ...graph.nodes.map(n => ({
                group: 'nodes',
                data: { id: n.id, label: n.label, kind: n.kind, parent: n.parent, helper: n.helper ?? false,
                    width: positions[n.id]?.width, height: positions[n.id]?.height },
                position: n.compound ? undefined : positions[n.id],
                classes: [n.kind.toLowerCase(), n.compound ? 'workflow' : 'leaf', n.helper ? 'helper' : '']
                        .filter(Boolean).join(' '),
            })),
            ...graph.edges.map(e => ({
                group: 'edges',
                data: { id: e.id, source: e.source, target: e.target, label: e.label ?? '' },
                classes: e.kind.toLowerCase(),
            })),
        ];
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
                'background-color': base, 'border-width': 1.5, 'border-color': contrast,
                'width': LEAF_WIDTH, 'height': LEAF_HEIGHT, 'shape': 'round-rectangle',
            } },
            { selector: ':parent', style: {
                'text-valign': 'top', 'text-halign': 'center', 'font-weight': 'bold',
                'text-max-width': 1000, 'background-opacity': 0.04, 'background-color': text, 'padding': COMPOUND_PADDING,
                // the box takes the ELK size, whose extra top space holds the title inside the box: an edge into the
                // box then stops at its border instead of crossing the title
                'min-width': 'data(width)', 'min-height': 'data(height)', 'min-height-bias-top': '100%',
                'min-height-bias-bottom': '0%', 'text-margin-y': 22,
            } },
            { selector: 'edge', style: {
                'width': 1.5, 'line-color': secondary, 'target-arrow-color': secondary, 'target-arrow-shape': 'triangle',
                'curve-style': 'bezier', 'label': 'data(label)', 'font-size': 10, 'color': secondary,
                'text-background-color': base, 'text-background-opacity': 1, 'text-background-padding': 2,
            } },
            { selector: 'node.running', style: { 'border-width': 4, 'border-color': primary } },
            { selector: 'node.done', style: { 'border-width': 3, 'border-color': success } },
            { selector: 'node.failed', style: { 'border-width': 4, 'border-color': error } },
        ];
    }
}

customElements.define('qwc-agentic-graph', QwcAgenticGraph);
