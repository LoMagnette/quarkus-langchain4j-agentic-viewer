import { LitElement, html, css, nothing } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';
import 'qui-ide-link';
import { devuiState } from 'devui-state';
import { KIND_LABELS, KIND_STYLES } from './qwc-agentic-graph.js';
import { shortRef } from './qwc-agentic-run-model.js';

/** What a piece of code is for its node. */
const CODE_ROLES = {
    AGENT: 'Agent', OUTPUT: '@Output', PLANNER_SUPPLIER: 'Planner supplier', EXIT_CONDITION: 'Exit condition',
    ACTIVATION_CONDITION: 'Activation condition', SYSTEM_MESSAGE: 'System message', USER_MESSAGE: 'User message',
    TOOL: 'Tool', CALL_SITE: 'Built at',
};
/** Display names of the langchain4j-agentic-patterns planners. */
const PATTERN_LABELS = {
    GOAP: 'goal-oriented (GOAP)', P2P: 'peer-to-peer', BLACKBOARD: 'blackboard', BDI: 'belief-desire-intention (BDI)',
    DEBATE: 'debate', VOTING: 'voting',
};

/**
 * Details of one topology node (an AgentNode of the AgenticSystem): what the agent is and does, without opening the
 * code. Every field is listed, with "none" when it has no value, so nothing is silently missing.
 * Fires 'close' when the close button is clicked.
 */
export class QwcAgenticNodeDetails extends LitElement {

    static styles = css`
        .muted {
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-xs);
        }
        :host {
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-s);
            padding: var(--lumo-space-m);
            border: 1px solid var(--lumo-contrast-10pct);
            border-radius: var(--lumo-border-radius-m);
            overflow-y: auto;
            font-size: var(--lumo-font-size-s);
        }
        .title {
            display: flex;
            align-items: center;
            gap: var(--lumo-space-s);
        }
        .title h4 {
            margin: 0;
            flex: 1;
            font-size: var(--lumo-font-size-l);
            overflow-wrap: anywhere;
        }
        .kind {
            padding: 0 var(--lumo-space-s);
            border: 2px solid;
            border-radius: var(--lumo-border-radius-s);
            white-space: nowrap;
        }
        .id {
            color: var(--lumo-secondary-text-color);
            overflow-wrap: anywhere;
        }
        dl {
            display: grid;
            grid-template-columns: max-content 1fr;
            gap: var(--lumo-space-xs) var(--lumo-space-m);
            margin: 0;
        }
        dt {
            color: var(--lumo-secondary-text-color);
        }
        dd {
            margin: 0;
            overflow-wrap: anywhere;
        }
        h5 {
            margin: var(--lumo-space-s) 0 0;
            font-size: var(--lumo-font-size-s);
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--lumo-secondary-text-color);
        }
        .none {
            color: var(--lumo-tertiary-text-color);
            font-style: italic;
        }
        .chips {
            display: flex;
            flex-wrap: wrap;
            gap: var(--lumo-space-xs);
        }
        .chip {
            padding: 0 var(--lumo-space-xs);
            border-radius: var(--lumo-border-radius-s);
            background: var(--lumo-contrast-10pct);
            font-family: var(--lumo-font-family-monospace, monospace);
        }
        pre {
            margin: 0;
            padding: var(--lumo-space-s);
            max-height: 12em;
            overflow: auto;
            white-space: pre-wrap;
            border-radius: var(--lumo-border-radius-s);
            background: var(--lumo-contrast-5pct);
            font-size: var(--lumo-font-size-xs);
        }
        ul.tools {
            margin: 0;
            padding: 0;
            list-style: none;
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-xs);
        }
        .tool-name {
            font-family: var(--lumo-font-family-monospace, monospace);
            font-weight: bold;
        }
        .tool-ref, .tool-description {
            color: var(--lumo-secondary-text-color);
        }
        ul.warnings {
            margin: var(--lumo-space-xs) 0;
            padding: 0;
            list-style: none;
            font-size: var(--lumo-font-size-s);
        }
        ul.warnings li.warning {
            color: #b45309;
        }
        ul.warnings li.hint {
            color: var(--lumo-secondary-text-color);
        }
        ol.desires {
            margin: 0;
            padding-left: var(--lumo-space-m);
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-xs);
        }
        ul.code {
            margin: 0;
            padding: 0;
            list-style: none;
            display: flex;
            flex-direction: column;
            gap: var(--lumo-space-xs);
            font-size: var(--lumo-font-size-s);
        }
        .code-role {
            color: var(--lumo-secondary-text-color);
            margin-right: var(--lumo-space-xs);
        }
        .code-link, .code-ref {
            font-family: var(--lumo-font-family-monospace, monospace);
        }
        .code-link {
            color: var(--lumo-primary-text-color);
        }
    `;

    static properties = {
        node: { type: Object },
        /** id of the node's system, to ask for the node's code */
        systemId: { type: String },
        /** the topology checks' warnings and hints of the system (those of this node are shown) */
        warnings: { type: Array },
        /** the page's plain JSON-RPC adapter (getNodeSources) */
        rpc: { attribute: false },
        _sources: { state: true },
    };

    willUpdate(changed) {
        if ((changed.has('node') || changed.has('systemId')) && this.node && this.systemId && this.rpc?.getNodeSources) {
            const node = this.node;
            this._sources = undefined;
            this.rpc.getNodeSources({ systemId: this.systemId, nodeId: node.id })
                .then(response => {
                    if (this.node === node) {
                        this._sources = response.result ?? [];
                    }
                })
                .catch(() => {
                    if (this.node === node) {
                        this._sources = [];
                    }
                });
        }
    }

    render() {
        const node = this.node;
        if (!node) {
            return nothing;
        }
        const style = KIND_STYLES[node.kind];
        return html`
            <div class="title">
                <h4>${node.name}</h4>
                <span class="kind" style="border-color: ${style?.color ?? 'currentColor'}">${KIND_LABELS[node.kind] ?? node.kind}</span>
                <vaadin-button theme="tertiary icon small" aria-label="Close" title="Close"
                               @click=${() => this.dispatchEvent(new CustomEvent('close'))}>
                    <vaadin-icon icon="font-awesome-solid:xmark"></vaadin-icon>
                </vaadin-button>
            </div>
            <div class="id">${node.id}</div>
            ${this._renderWarnings(node)}
            <dl>
                <dt>Description</dt><dd data-field="description">${node.description ?? this._none()}</dd>
                <dt>Input keys</dt><dd data-field="inputKeys">${this._chips(node.inputKeys)}</dd>
                <dt>Output key</dt><dd data-field="outputKey">${node.outputKey ? this._chips([node.outputKey]) : this._none()}</dd>
                <dt>Optional</dt><dd data-field="optional">${node.optional ? 'yes' : 'no'}</dd>
                <dt>Async</dt><dd data-field="async">${node.async ? 'yes' : 'no'}</dd>
                ${this._renderSettings(node)}
                ${this._renderGuard(node.guard)}
                ${node.plannerInfo ? html`
                    <dt>Planner</dt>
                    <dd data-field="planner"><span title=${node.plannerInfo.plannerClass ?? ''}>${node.plannerInfo.plannerClass
                        ? shortRef(node.plannerInfo.plannerClass) : this._none()}</span>${node.plannerInfo.patternType
                        && node.plannerInfo.patternType !== 'CUSTOM' ? html` · ${PATTERN_LABELS[node.plannerInfo.patternType]
                        ?? node.plannerInfo.patternType} pattern` : nothing}${node.plannerInfo.extras?.supplier
                        ? html`<div class="muted">from ${shortRef(node.plannerInfo.extras.supplier)}</div>` : nothing}</dd>
                    ${this._renderPatternRoles(node)}` : nothing}
            </dl>
            ${node.kind === 'AI_AGENT' ? this._renderAiDetails(node.aiDetails) : nothing}
            ${node.remoteDetails ? this._renderRemoteDetails(node.kind, node.remoteDetails) : nothing}
            ${this._renderCode()}`;
    }

    /**
     * Compensation and summarized context when set (a setting left at its default is not shown), and the strategies
     * of a supervisor, marked when they are the defaults.
     */
    _renderSettings(node) {
        const settings = node.settings;
        if (!settings) {
            return nothing;
        }
        const strategy = value => value ? value.toLowerCase().replaceAll('_', ' ') : undefined;
        const marked = (value, fallback) => html`${value ?? this._none()}${value != null && value === fallback
            ? html` <span class="muted">(default)</span>` : nothing}`;
        return html`
            ${settings.compensateOnError ? html`<dt>Compensation</dt><dd data-field="compensation">on error
                <div class="muted">when a tool fails, the successful tool calls before it with @CompensateFor actions are
                    compensated in reverse order</div></dd>` : nothing}
            ${settings.summarizedContext?.length ? html`<dt>Summarized context</dt>
                <dd data-field="summarizedContext">${this._chips(settings.summarizedContext)}
                    <div class="muted">the interactions of these agents make up its context</div></dd>` : nothing}
            ${node.kind === 'SUPERVISOR' ? html`
                <dt>Max invocations</dt><dd data-field="maxAgentsInvocations">${marked(settings.maxAgentsInvocations, 10)}</dd>
                <dt>Context</dt><dd data-field="contextStrategy">${marked(strategy(settings.contextStrategy), 'chat memory')}</dd>
                <dt>Response</dt><dd data-field="responseStrategy">${marked(strategy(settings.responseStrategy), 'last')}</dd>`
                : nothing}`;
    }

    /** What the sub-agents of a BDI, debate or voting planner are for it. */
    _renderPatternRoles(node) {
        const name = id => node.children.find(child => child.id === id)?.name ?? id;
        const names = children => children.map(child => child.name).join(', ');
        switch (node.plannerInfo.patternType) {
            case 'BDI':
                return html`<dt>Desires</dt><dd data-field="desires">${node.plannerInfo.desires?.length
                    ? html`<ol class="desires">${node.plannerInfo.desires.map(desire => html`<li>
                        <strong>${desire.name}</strong>${desire.priority != null ? html` · priority ${desire.priority}` : nothing}
                        <div class="muted">${desire.achievableKey ? html`achievable with ${this._chips([desire.achievableKey])}`
                            : 'achievable: custom condition'} · ${desire.satisfiedKey
                            ? html`satisfied by ${this._chips([desire.satisfiedKey])}` : 'satisfied: custom condition'}</div>
                        <div>${desire.agents.map(name).join(' → ')}</div></li>`)}</ol>`
                    : html`<span class="muted">unknown: only desires declared with literals in the planner supplier are
                        read (a builder system's planner keeps them private)</span>`}</dd>`;
            case 'DEBATE':
                return node.children.length >= 3 ? html`
                    <dt>Debaters</dt><dd data-field="debaters">${names(node.children.slice(0, -1))}</dd>
                    <dt>Judge</dt><dd data-field="judge">${node.children[node.children.length - 1].name}
                        <div class="muted">the last sub-agent; called once the debaters agree or after the last round</div></dd>`
                    : nothing;
            case 'VOTING':
                return html`<dt>Voters</dt><dd data-field="voters">${names(node.children)}
                    <div class="muted">all called at once; their outputs are aggregated by the planner's strategy</div></dd>`;
            default:
                return nothing;
        }
    }

    _renderWarnings(node) {
        const warnings = (this.warnings ?? []).filter(w => w.nodeId === node.id);
        return warnings.length ? html`<ul class="warnings" data-field="warnings">${warnings.map(w => html`
            <li class=${w.level.toLowerCase()} data-warning=${w.code}>${w.level === 'WARNING' ? '⚠' : 'ⓘ'} ${w.message}</li>`)}
        </ul>` : nothing;
    }

    /**
     * The application code behind the node: links opening it in the IDE when the Dev UI detected one and the code is in
     * the application sources, plain references otherwise.
     */
    _renderCode() {
        if (!this._sources?.length) {
            return nothing;
        }
        const ide = !!devuiState.ideInfo?.ideName;
        return html`
            <h5>Code</h5>
            <ul class="code" data-field="code">${this._sources.map(source => {
                const label = source.resource ?? shortRef(source.member ? `${source.className}#${source.member}` : source.className);
                const where = source.fileName
                    ? `${source.fileName.substring(source.fileName.lastIndexOf('/') + 1)}:${source.lineNumber}` : undefined;
                return html`<li data-code-role=${source.role}>
                    <span class="code-role">${CODE_ROLES[source.role] ?? source.role}</span>
                    ${source.fileName && ide
                        ? html`<qui-ide-link class="code-link" fileName=${source.fileName} lang=${source.lang}
                                lineNumber=${String(source.lineNumber)} noCheck title="Open in the IDE">${label}</qui-ide-link>`
                        : html`<span class="code-ref">${label}</span>`}
                    <span class="muted" data-code-where>${where ?? 'not in the application sources'}</span>
                </li>`;
            })}</ul>`;
    }

    _renderRemoteDetails(kind, remote) {
        if (kind === 'A2A_CLIENT_AGENT') {
            return html`
                <h5>Remote A2A server</h5>
                <dl>
                    <dt>Server</dt>
                    <dd data-field="a2aServer">${remote.a2aServerUrl
                        ?? (remote.serverUrlSupplier ? html`from ${shortRef(remote.serverUrlSupplier)}` : this._none())}</dd>
                    <dt>Tenant</dt><dd data-field="tenant">${remote.tenant ?? this._none()}</dd>
                </dl>`;
        }
        return html`
            <h5>MCP server</h5>
            <dl>
                <dt>Tool</dt><dd data-field="mcpTool">${remote.toolName ? this._chips([remote.toolName]) : this._none()}</dd>
                <dt>Client</dt>
                <dd data-field="mcpClient">${remote.clientSupplier ? html`from ${shortRef(remote.clientSupplier)}` : this._none()}</dd>
            </dl>`;
    }

    _renderGuard(guard) {
        if (!guard) {
            return nothing;
        }
        if (guard.maxIterations != null) {
            return html`
                ${guard.activation ? html`<dt>Activation</dt><dd data-field="activation">${guard.activation}</dd>` : nothing}
                <dt>Exit condition</dt><dd data-field="exitCondition">${guard.label ?? this._none()}</dd>
                <dt>Max iterations</dt><dd data-field="maxIterations">${guard.maxIterations}</dd>
                <dt>Exit tested</dt><dd data-field="testExitAtLoopEnd">${guard.testExitAtLoopEnd ? 'at the end of an iteration' : 'after each agent'}</dd>`;
        }
        return html`<dt>Activation</dt><dd data-field="activation">${guard.label}</dd>`;
    }

    _renderAiDetails(details) {
        return html`
            <h5>Model</h5>
            <div data-field="model">${details?.modelName ?? html`<span class="none">default model</span>`}</div>
            <h5>System message</h5>
            <div data-field="systemMessage">${details?.systemMessage ? html`<pre>${details.systemMessage}</pre>` : this._none()}</div>
            <h5>User message</h5>
            <div data-field="userMessage">${details?.userMessage ? html`<pre>${details.userMessage}</pre>` : this._none()}</div>
            <h5>Tools</h5>
            <div data-field="tools">${details?.tools?.length ? html`
                <ul class="tools">${details.tools.map(tool => html`
                    <li>
                        <span class="tool-name">${tool.name}</span>
                        <span class="tool-ref">${shortRef(`${tool.declaringClass}#${tool.methodName}`)}</span>
                        ${tool.description ? html`<div class="tool-description">${tool.description}</div>` : nothing}
                    </li>`)}
                </ul>` : this._none()}</div>
            ${details?.runtimeToolSources?.length ? html`
                <h5>Tools known at runtime</h5>
                <div class="chips" data-field="runtimeToolSources">${details.runtimeToolSources.map(source =>
                    html`<span class="chip">${source.startsWith('mcp:') ? source : shortRef(source)}</span>`)}</div>` : nothing}`;
    }

    _chips(values) {
        return values?.length ? html`<span class="chips">${values.map(v => html`<span class="chip">${v}</span>`)}</span>`
            : this._none();
    }

    _none() {
        return html`<span class="none">none</span>`;
    }
}

customElements.define('qwc-agentic-node-details', QwcAgenticNodeDetails);
