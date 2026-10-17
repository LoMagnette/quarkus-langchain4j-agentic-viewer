import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/grid';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/button';
import '@vaadin/icon';
import { KIND_LABELS } from './qwc-agentic-graph.js';
import './qwc-agentic-node-details.js';
import './qwc-agentic-run-panel.js';
import './qwc-agentic-timeline.js';
import './qwc-agentic-output.js';
import './qwc-agentic-scope-state.js';
import '@vaadin/tabs';

/** The node with this id in a topology tree, undefined when none. */
function findNode(node, id) {
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

/**
 * Agentic Systems - lists the root agentic systems of the application; selecting one opens its view with the topology
 * graph (T1.6). Everything comes from the runtime-dev registry over JSON-RPC (T2.1): listSystems, then getTopology.
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
        .error {
            color: var(--lumo-error-text-color);
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
        .system-body {
            flex: 1;
            display: flex;
            gap: var(--lumo-space-m);
            min-height: 0;
        }
        .graph-column {
            flex: 1;
            min-width: 0;
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-s);
        }
        qwc-agentic-graph {
            flex: 1;
            min-height: 0;
        }
        qwc-agentic-timeline {
            flex: 0 0 auto;
            max-height: 35%;
            border: 1px solid var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-m);
            padding: var(--lumo-space-xs) var(--lumo-space-s);
        }
        .side {
            flex: 0 0 380px;
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-s);
            min-height: 0;
            border: 1px solid var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-m);
            padding: var(--lumo-space-s) var(--lumo-space-m) var(--lumo-space-m);
            box-sizing: border-box;
            overflow-y: auto;
        }
        .side-header {
            display: flex;
            align-items: center;
        }
        .side-header vaadin-tabs {
            flex: 1;
        }
        .side [hidden] {
            display: none;
        }
        qwc-agentic-node-details {
            border: none;
            padding: 0;
        }
        .header .spacer {
            flex: 1;
        }
    `;

    static properties = {
        _systems: { state: true },
        _error: { state: true },
        _summary: { state: true },
        _selected: { state: true },
        _systemError: { state: true },
        _node: { state: true },
        /** side panel: undefined (closed), 'run' or 'details' */
        _side: { state: true },
        /** events of the current run, from the run panel */
        _runEvents: { state: true },
        _runRunning: { state: true },
    };

    jsonRpc = new JsonRpc(this);

    /**
     * The JSON-RPC calls the run panel makes. A plain object: the JsonRpc proxy answers every property, including the
     * one Lit probes to recognize directives, so it cannot be passed through a binding.
     */
    rpc = {
        getInputSchema: params => this.jsonRpc.getInputSchema(params),
        startRun: params => this.jsonRpc.startRun(params),
        streamRun: params => this.jsonRpc.streamRun(params),
        listRuns: params => this.jsonRpc.listRuns(params),
        getRun: params => this.jsonRpc.getRun(params),
        cancelRun: params => this.jsonRpc.cancelRun(params),
        provideHumanInput: params => this.jsonRpc.provideHumanInput(params),
        previewGoalPath: params => this.jsonRpc.previewGoalPath(params),
    };

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.listSystems()
            .then(response => this._systems = response.result ?? [])
            .catch(error => this._error = `Cannot list the agentic systems: ${error?.message ?? error}`);
    }

    render() {
        if (this._error) {
            return html`<div class="error">${this._error}</div>`;
        }
        if (this._summary) {
            return this._renderSystem(this._summary, this._selected);
        }
        if (this._systems === undefined) {
            return html`<div class="muted">Loading agentic systems...</div>`;
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
                    ${columnBodyRenderer(summary => this._renderKinds(summary))}></vaadin-grid-column>
                <vaadin-grid-column header="Defined in" auto-width
                    ${columnBodyRenderer(system => html`<span class="muted">${system.sourceRef?.className}#${system.sourceRef?.methodName}</span>`)}></vaadin-grid-column>
            </vaadin-grid>`;
    }

    _renderKinds(summary) {
        const kinds = summary.patternKinds ?? [];
        if (kinds.length === 0) {
            return html`<span class="kind agent">single ${KIND_LABELS[summary.rootKind] ?? summary.rootKind}</span>`;
        }
        return html`<span class="kinds">${kinds.map(kind => html`<span class="kind">${KIND_LABELS[kind] ?? kind}</span>`)}</span>`;
    }

    _closeDetails() {
        this._node = undefined;
        this.renderRoot.querySelector('qwc-agentic-graph')?.clearSelection();
        this._side = this._runOpened ? 'run' : undefined;
    }

    _selectNode(system, nodeId) {
        this._node = findNode(system.root, nodeId);
        if (this._node) {
            this._side = 'details';
        } else if (this._side === 'details') {
            this._side = this._runOpened ? 'run' : undefined;
        }
    }

    _openRun() {
        this._runOpened = true;
        this._side = 'run';
    }

    _open(summary) {
        if (!summary) {
            return;
        }
        this._summary = summary;
        this._selected = undefined;
        this._node = undefined;
        this._side = undefined;
        this._runOpened = false;
        this._runEvents = undefined;
        this._systemError = undefined;
        // the topology, with what the application's topology contributors draw in its planner nodes
        Promise.all([
            this.jsonRpc.getTopology({ systemId: summary.id }),
            this.jsonRpc.getContributions({ systemId: summary.id }).then(response => response.result ?? {}).catch(() => ({})),
        ])
            .then(([response, contributions]) => {
                if (this._summary === summary) {
                    this._selected = { ...response.result, contributions };
                }
            })
            .catch(error => this._systemError = `Cannot load ${summary.name}: ${error?.error?.message ?? error?.message ?? error}`);
    }

    _back() {
        this._summary = undefined;
        this._selected = undefined;
        this._node = undefined;
        this._side = undefined;
        this._runOpened = false;
        this._runEvents = undefined;
    }

    _renderSystem(summary, system) {
        return html`
            <div class="header">
                <vaadin-button theme="tertiary" @click=${this._back}>
                    <vaadin-icon icon="font-awesome-solid:arrow-left" slot="prefix"></vaadin-icon>Systems
                </vaadin-button>
                <h3>${summary.name}</h3>
                ${this._renderKinds(summary)}
                <span class="spacer"></span>
                <vaadin-button theme="primary" data-action="open-run" ?disabled=${!system} @click=${this._openRun}>
                    <vaadin-icon icon="font-awesome-solid:play" slot="prefix"></vaadin-icon>Run
                </vaadin-button>
            </div>
            <div class="muted">${summary.origin.toLowerCase()} system defined in
                ${summary.sourceRef?.className}#${summary.sourceRef?.methodName}</div>
            ${this._systemError ? html`<div class="error">${this._systemError}</div>`
                : !system ? html`<div class="muted">Loading the topology...</div>` : this._renderGraph(system)}`;
    }

    _renderGraph(system) {
        const tabs = [...(this._runOpened ? ['run'] : []), ...(this._runEvents ? ['output', 'scope'] : []),
            ...(this._node ? ['details'] : [])];
        const labels = { run: 'Run', output: 'Output', scope: 'Scope', details: 'Details' };
        return html`
            <div class="system-body">
                <div class="graph-column">
                    <qwc-agentic-graph .system=${system}
                                       @node-selected=${e => this._selectNode(system, e.detail.nodeId)}></qwc-agentic-graph>
                    ${this._runEvents ? html`<qwc-agentic-timeline .events=${this._runEvents} .system=${system}
                            .running=${!!this._runRunning}></qwc-agentic-timeline>` : ''}
                </div>
                <div class="side" ?hidden=${!this._side}>
                    <div class="side-header">
                        <vaadin-tabs .selected=${Math.max(0, tabs.indexOf(this._side))}
                                @selected-changed=${e => { if (tabs[e.detail.value]) { this._side = tabs[e.detail.value]; } }}>
                            ${tabs.map(tab => html`<vaadin-tab data-tab=${tab}>${labels[tab]}</vaadin-tab>`)}
                        </vaadin-tabs>
                    </div>
                    ${this._runEvents ? html`<qwc-agentic-output ?hidden=${this._side !== 'output'}
                            .events=${this._runEvents}></qwc-agentic-output>` : ''}
                    ${this._runEvents ? html`<qwc-agentic-scope-state ?hidden=${this._side !== 'scope'}
                            .events=${this._runEvents}></qwc-agentic-scope-state>` : ''}
                    ${this._runOpened ? html`<qwc-agentic-run-panel ?hidden=${this._side !== 'run'} .system=${system}
                            @run-updated=${e => { this._runEvents = e.detail.events; this._runRunning = e.detail.running; }}
                            @show-output=${() => this._side = 'output'}
                            .jsonRpc=${this.rpc} .graph=${this.renderRoot.querySelector('qwc-agentic-graph')}>
                        </qwc-agentic-run-panel>` : ''}
                    ${this._node ? html`<qwc-agentic-node-details ?hidden=${this._side !== 'details'} .node=${this._node}
                            @close=${this._closeDetails}></qwc-agentic-node-details>` : ''}
                </div>
            </div>`;
    }
}

customElements.define('qwc-agentic-systems', QwcAgenticSystems);
