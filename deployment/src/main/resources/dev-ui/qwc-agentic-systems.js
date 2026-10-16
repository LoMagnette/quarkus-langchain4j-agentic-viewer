import { LitElement, html, css } from 'lit';
import { agenticSystems } from 'build-time-data';
import '@vaadin/grid';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/button';
import '@vaadin/icon';
import { KIND_LABELS } from './qwc-agentic-graph.js';

/** Agent kinds are not patterns. */
const AGENT_KINDS = new Set(['AI_AGENT', 'NON_AI_AGENT']);

/** Workflow kinds used in a system, in tree order, without duplicates. */
export function patternKinds(system) {
    const kinds = [];
    const visit = node => {
        if (!AGENT_KINDS.has(node.kind) && !kinds.includes(node.kind)) {
            kinds.push(node.kind);
        }
        node.children?.forEach(visit);
    };
    visit(system.root);
    return kinds;
}

/**
 * Agentic Systems - lists the root agentic systems of the application; selecting one opens its view.
 * Declarative systems come from the build-time data (T1.4); the system view shows the topology graph (T1.6).
 */
export class QwcAgenticSystems extends LitElement {

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-m);
            padding: var(--lumo-space-m);
            height: 100%;
            box-sizing: border-box;
        }
        vaadin-grid {
            flex: 1;
            cursor: pointer;
        }
        .empty {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: var(--lumo-space-s);
            margin-top: var(--lumo-space-xl);
            color: var(--lumo-secondary-text-color);
            text-align: center;
        }
        .empty .title {
            font-size: var(--lumo-font-size-l);
            color: var(--lumo-body-text-color);
        }
        .kinds {
            display: flex;
            flex-wrap: wrap;
            gap: var(--lumo-space-xs);
        }
        .kind {
            padding: 0 var(--lumo-space-s);
            border-radius: var(--lumo-border-radius-s);
            background: var(--lumo-contrast-10pct);
            font-size: var(--lumo-font-size-s);
            white-space: nowrap;
        }
        .kind.agent {
            background: var(--lumo-primary-color-10pct);
        }
        .muted {
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-s);
        }
        .header {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-m);
        }
        .header h3 {
            margin: 0;
        }
        qwc-agentic-graph {
            flex: 1;
        }
    `;

    static properties = {
        _selected: { state: true },
    };

    constructor() {
        super();
        this._systems = agenticSystems ?? [];
    }

    render() {
        if (this._selected) {
            return this._renderSystem(this._selected);
        }
        if (this._systems.length === 0) {
            return html`
                <div class="empty">
                    <vaadin-icon icon="font-awesome-solid:robot"></vaadin-icon>
                    <div class="title">No agentic system found</div>
                    <div>Annotate an interface method with <code>@SequenceAgent</code>, <code>@ParallelAgent</code>,
                        <code>@LoopAgent</code>, <code>@ConditionalAgent</code>, <code>@SupervisorAgent</code>,
                        <code>@PlannerAgent</code> or <code>@Agent</code> to see it here.</div>
                </div>`;
        }
        return html`
            <vaadin-grid .items=${this._systems} theme="row-stripes no-border" all-rows-visible
                         @active-item-changed=${e => this._open(e.detail.value)}>
                <vaadin-grid-column header="System" auto-width flex-grow="0"
                    ${columnBodyRenderer(system => html`<strong>${system.name}</strong>`)}></vaadin-grid-column>
                <vaadin-grid-column header="Origin" auto-width flex-grow="0"
                    ${columnBodyRenderer(system => html`${system.origin.toLowerCase()}`)}></vaadin-grid-column>
                <vaadin-grid-column header="Patterns" auto-width
                    ${columnBodyRenderer(system => this._renderKinds(system))}></vaadin-grid-column>
                <vaadin-grid-column header="Defined in" auto-width
                    ${columnBodyRenderer(system => html`<span class="muted">${system.sourceRef?.className}#${system.sourceRef?.methodName}</span>`)}></vaadin-grid-column>
            </vaadin-grid>`;
    }

    _renderKinds(system) {
        const kinds = patternKinds(system);
        if (kinds.length === 0) {
            return html`<span class="kind agent">single ${KIND_LABELS[system.root.kind] ?? system.root.kind}</span>`;
        }
        return html`<span class="kinds">${kinds.map(kind => html`<span class="kind">${KIND_LABELS[kind] ?? kind}</span>`)}</span>`;
    }

    _open(system) {
        if (system) {
            this._selected = system;
        }
    }

    _renderSystem(system) {
        return html`
            <div class="header">
                <vaadin-button theme="tertiary" @click=${() => this._selected = undefined}>
                    <vaadin-icon icon="font-awesome-solid:arrow-left" slot="prefix"></vaadin-icon>Systems
                </vaadin-button>
                <h3>${system.name}</h3>
                ${this._renderKinds(system)}
            </div>
            <div class="muted">${system.origin.toLowerCase()} system defined in
                ${system.sourceRef?.className}#${system.sourceRef?.methodName}</div>
            <qwc-agentic-graph .system=${system}></qwc-agentic-graph>`;
    }
}

customElements.define('qwc-agentic-systems', QwcAgenticSystems);
