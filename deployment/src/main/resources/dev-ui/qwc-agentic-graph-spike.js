import { LitElement, html, css } from 'lit';
import { observeState } from 'lit-element-state';
import { themeState } from 'theme-state';
import '@vaadin/button';
import cytoscape from 'cytoscape';
// elkjs maps its bare name to a CommonJS file; the UMD bundle registers window.ELK when imported
import 'elkjs/elk.bundled.js';

/**
 * T0.8 spike: Cytoscape.js rendering with an ELK layout, compound nodes and on-demand highlighting.
 * Fixture: the StoryCreator sample (a loop nested in a sequence). To be replaced by the real graph component (T1.6).
 */
const FIXTURE = {
    nodes: [
        { id: 'create', label: 'create', kind: 'SEQUENCE' },
        { id: 'draft', label: 'draft', kind: 'AI_AGENT', parent: 'create' },
        { id: 'polish', label: 'polish (max 5)', kind: 'LOOP', parent: 'create' },
        { id: 'score', label: 'score', kind: 'AI_AGENT', parent: 'polish' },
        { id: 'edit', label: 'edit', kind: 'AI_AGENT', parent: 'polish' },
    ],
    edges: [
        { id: 'draft-polish', source: 'draft', target: 'polish', kind: 'SEQUENCE' },
        { id: 'score-edit', source: 'score', target: 'edit', kind: 'SEQUENCE' },
        { id: 'edit-score', source: 'edit', target: 'score', kind: 'LOOP_BACK', label: 'until score >= 0.8' },
    ],
};

const LEAF_WIDTH = 140;
const LEAF_HEIGHT = 44;
const STATES = ['running', 'done', 'failed'];

export class QwcAgenticGraphSpike extends observeState(LitElement) {

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
            padding: 1rem;
            height: 100%;
            box-sizing: border-box;
        }
        .toolbar {
            display: flex;
            gap: 0.5rem;
            align-items: center;
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
        _error: { state: true },
        _layoutDone: { state: true },
    };

    async firstUpdated() {
        try {
            const positions = await this._elkLayout(FIXTURE);
            this._cy = cytoscape({
                container: this.renderRoot.querySelector('#graph'),
                elements: this._elements(FIXTURE, positions),
                layout: { name: 'preset', fit: true, padding: 30 },
                style: this._style(),
                autoungrabify: true,
                wheelSensitivity: 0.2,
            });
            if (this._cy.zoom() > 1) {
                this._cy.zoom(1);
                this._cy.center();
            }
            this._appliedTheme = themeState.theme?.name;
            this._layoutDone = true;
        } catch (e) {
            this._error = e?.message || String(e);
        }
    }

    updated() {
        // observeState re-renders on theme changes: restyle the canvas with the new Lumo colors
        if (this._cy && this._appliedTheme !== themeState.theme?.name) {
            this._appliedTheme = themeState.theme?.name;
            this._cy.style(this._style());
        }
    }

    disconnectedCallback() {
        clearTimeout(this._simulation);
        this._cy?.destroy();
        super.disconnectedCallback();
    }

    render() {
        return html`
            <div class="toolbar">
                <vaadin-button theme="small" @click=${this._simulateRun} ?disabled=${!this._layoutDone}>Simulate run</vaadin-button>
                <vaadin-button theme="small" @click=${() => this.clearHighlights()} ?disabled=${!this._layoutDone}>Clear</vaadin-button>
                <span>Theme: ${themeState.theme?.name ?? 'unknown'}</span>
            </div>
            ${this._error ? html`<div class="error">Graph failed: ${this._error}</div>` : ''}
            <div id="graph"></div>
        `;
    }

    /**
     * Highlights a node: state is one of running, done, failed (null clears it).
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
     * @return node id -> { x, y, parent } as rendered, for checks
     */
    renderedNodes() {
        const result = {};
        this._cy?.nodes().forEach(n => {
            const bb = n.boundingBox();
            result[n.id()] = { x1: bb.x1, y1: bb.y1, x2: bb.x2, y2: bb.y2, parent: n.parent().id() ?? null };
        });
        return result;
    }

    _simulateRun() {
        const steps = [
            ['create', 'running'], ['draft', 'running'], ['draft', 'done'], ['polish', 'running'],
            ['score', 'running'], ['score', 'done'], ['edit', 'running'], ['edit', 'done'],
            ['polish', 'done'], ['create', 'done'],
        ];
        this.clearHighlights();
        const next = (i) => {
            if (i < steps.length) {
                this.highlight(...steps[i]);
                this._simulation = setTimeout(() => next(i + 1), 400);
            }
        };
        next(0);
    }

    async _elkLayout(graph) {
        const elk = new window.ELK();
        const byId = new Map(graph.nodes.map(n => [n.id, { ...n, elk: { id: n.id } }]));
        const roots = [];
        for (const node of byId.values()) {
            const isParent = graph.nodes.some(n => n.parent === node.id);
            if (!isParent) {
                node.elk.width = LEAF_WIDTH;
                node.elk.height = LEAF_HEIGHT;
            } else {
                node.elk.layoutOptions = { 'elk.padding': '[top=36,left=16,bottom=16,right=16]' };
                node.elk.children = [];
            }
        }
        for (const node of byId.values()) {
            if (node.parent) {
                byId.get(node.parent).elk.children.push(node.elk);
            } else {
                roots.push(node.elk);
            }
        }
        const layout = await elk.layout({
            id: 'root',
            layoutOptions: {
                'elk.algorithm': 'layered',
                'elk.direction': 'DOWN',
                'elk.hierarchyHandling': 'INCLUDE_CHILDREN',
                'elk.spacing.nodeNode': '30',
                'elk.layered.spacing.nodeNodeBetweenLayers': '40',
            },
            children: roots,
            // back-edges are drawn but not laid out, so the forward order (score before edit) is kept
            edges: graph.edges.filter(e => e.kind !== 'LOOP_BACK')
                    .map(e => ({ id: e.id, sources: [e.source], targets: [e.target] })),
        });

        // ELK gives top-left corners relative to the parent; Cytoscape wants absolute centers of leaf nodes
        const positions = {};
        const walk = (node, offsetX, offsetY) => {
            const x = offsetX + (node.x ?? 0);
            const y = offsetY + (node.y ?? 0);
            if (!node.children || node.children.length === 0) {
                positions[node.id] = { x: x + node.width / 2, y: y + node.height / 2 };
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
                data: { id: n.id, label: n.label, kind: n.kind, parent: n.parent },
                position: positions[n.id],
                classes: n.kind.toLowerCase(),
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
                'background-color': base, 'border-width': 1.5, 'border-color': contrast,
                'width': LEAF_WIDTH, 'height': LEAF_HEIGHT, 'shape': 'round-rectangle',
            } },
            { selector: ':parent', style: {
                'text-valign': 'top', 'text-halign': 'center', 'text-margin-y': -4, 'font-weight': 'bold',
                'background-opacity': 0.04, 'background-color': text, 'padding': 20,
            } },
            { selector: 'node.sequence', style: { 'shape': 'rectangle', 'border-color': secondary } },
            { selector: 'node.loop', style: { 'border-style': 'dashed', 'border-color': '#8b5cf6' } },
            { selector: 'node.ai_agent', style: { 'border-color': '#10b981' } },
            { selector: 'edge', style: {
                'width': 1.5, 'line-color': secondary, 'target-arrow-color': secondary, 'target-arrow-shape': 'triangle',
                'curve-style': 'bezier', 'label': 'data(label)', 'font-size': 10, 'color': secondary,
                'text-background-color': base, 'text-background-opacity': 1, 'text-background-padding': 2,
            } },
            { selector: 'edge.loop_back', style: { 'line-style': 'dashed', 'line-color': '#8b5cf6', 'target-arrow-color': '#8b5cf6' } },
            { selector: 'node.running', style: { 'border-width': 4, 'border-color': primary } },
            { selector: 'node.done', style: { 'border-width': 3, 'border-color': success } },
            { selector: 'node.failed', style: { 'border-width': 4, 'border-color': error } },
        ];
    }
}

customElements.define('qwc-agentic-graph-spike', QwcAgenticGraphSpike);
