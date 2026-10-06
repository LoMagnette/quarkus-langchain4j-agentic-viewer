import { LitElement, html, css } from 'lit';
import { agenticGraph, agentCount, toolCount, hasImperative, imperativeLocations } from 'build-time-data';

// Get imperative patterns from the graph data
const imperativePatterns = agenticGraph?.imperativePatterns || [];

/**
 * Agentic Viewer - Visualizes LangChain4j agent workflows using Mermaid.js
 */
export class QwcAgenticViewer extends LitElement {

    static styles = css`
        :host {
            display: block;
            padding: 1rem;
            height: 100%;
        }

        .container {
            display: flex;
            flex-direction: column;
            height: 100%;
        }

        .header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 1rem;
            flex-shrink: 0;
        }

        .stats {
            display: flex;
            gap: 2rem;
        }

        .stat {
            text-align: center;
        }

        .stat-value {
            font-size: 1.5rem;
            font-weight: bold;
            color: var(--lumo-primary-color);
        }

        .stat-label {
            font-size: 0.85rem;
            color: var(--lumo-secondary-text-color);
        }

        .warning {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
            padding: 0.75rem 1rem;
            background: var(--lumo-warning-color-10pct, #fff3cd);
            border-radius: 4px;
            font-size: 0.85rem;
            color: var(--lumo-warning-text-color, #856404);
            margin-bottom: 1rem;
        }

        .warning-header {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            font-weight: bold;
        }

        .warning-details {
            font-size: 0.8rem;
            margin-left: 1.5rem;
        }

        .warning-details ul {
            margin: 0.25rem 0;
            padding-left: 1.5rem;
        }

        .builder-badge {
            display: inline-block;
            padding: 0.1rem 0.4rem;
            background: var(--lumo-warning-color-50pct, #ffc107);
            border-radius: 3px;
            font-size: 0.7rem;
            margin: 0 0.15rem;
        }

        .diagram-container {
            flex: 1;
            background: var(--lumo-base-color, white);
            border: 1px solid var(--lumo-contrast-20pct, #e0e0e0);
            border-radius: 8px;
            padding: 1rem;
            overflow: auto;
            min-height: 400px;
        }

        .diagram-container svg {
            max-width: 100%;
            height: auto;
        }

        .toolbar {
            display: flex;
            gap: 1rem;
            margin-bottom: 1rem;
            align-items: center;
            flex-wrap: wrap;
            padding: 0.5rem;
            background: var(--lumo-contrast-5pct);
            border-radius: 4px;
        }

        .toolbar label {
            display: flex;
            align-items: center;
            gap: 0.25rem;
            font-size: 0.85rem;
        }

        .toolbar select {
            padding: 0.25rem 0.5rem;
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: 4px;
            background: var(--lumo-base-color);
            font-size: 0.85rem;
        }

        .toolbar button {
            padding: 0.5rem 1rem;
            border: 1px solid var(--lumo-contrast-20pct);
            background: var(--lumo-base-color);
            border-radius: 4px;
            cursor: pointer;
            font-size: 0.85rem;
        }

        .toolbar button:hover {
            background: var(--lumo-contrast-10pct);
        }

        .empty-state {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            height: 300px;
            color: var(--lumo-secondary-text-color);
        }

        .empty-state-icon {
            font-size: 3rem;
            margin-bottom: 1rem;
        }

        details {
            margin-top: 1rem;
        }

        summary {
            cursor: pointer;
            color: var(--lumo-secondary-text-color);
            font-size: 0.85rem;
        }

        .debug {
            margin-top: 0.5rem;
            padding: 1rem;
            background: var(--lumo-contrast-5pct);
            border-radius: 4px;
            font-family: monospace;
            font-size: 0.75rem;
            white-space: pre-wrap;
            max-height: 200px;
            overflow: auto;
        }

        .mermaid-code {
            margin-top: 0.5rem;
            padding: 1rem;
            background: var(--lumo-contrast-5pct);
            border-radius: 4px;
            font-family: monospace;
            font-size: 0.8rem;
            white-space: pre-wrap;
            max-height: 300px;
            overflow: auto;
        }
    `;

    static properties = {
        _mermaidCode: { state: true },
        _mermaidLoaded: { state: true },
        _layoutDirection: { state: true },
        _showTools: { state: true },
        _showAiServices: { state: true }
    };

    constructor() {
        super();
        this._mermaidCode = '';
        this._mermaidLoaded = false;
        this._layoutDirection = 'LR'; // Left to Right for better readability
        this._showTools = true;
        this._showAiServices = true;
    }

    firstUpdated() {
        this._loadMermaid();
    }

    async _loadMermaid() {
        try {
            // Load Mermaid from CDN
            const script = document.createElement('script');
            script.src = 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js';
            script.onload = () => {
                window.mermaid.initialize({
                    startOnLoad: false,
                    theme: 'default',
                    flowchart: {
                        useMaxWidth: false,
                        htmlLabels: true,
                        curve: 'basis',
                        nodeSpacing: 50,
                        rankSpacing: 80,
                        padding: 20
                    },
                    themeVariables: {
                        fontSize: '14px'
                    }
                });
                this._mermaidLoaded = true;
                this._renderDiagram();
            };
            document.head.appendChild(script);
        } catch (e) {
            console.error('Failed to load Mermaid:', e);
        }
    }

    _generateMermaidCode() {
        let agents = agenticGraph?.agents || [];
        let tools = agenticGraph?.tools || [];
        const dataFlows = agenticGraph?.dataFlows || [];
        const compositions = agenticGraph?.compositions || [];

        // Apply filters
        if (!this._showAiServices) {
            agents = agents.filter(a => a.type !== 'AI_SERVICE');
        }
        if (!this._showTools) {
            tools = [];
        }

        if (agents.length === 0 && tools.length === 0) {
            return '';
        }

        let code = `flowchart ${this._layoutDirection}\n`;

        // Track which agents are children in compositions (to avoid duplicating them at top level)
        const childAgentIds = new Set();
        compositions.forEach(comp => {
            (comp.childAgentIds || []).forEach(id => childAgentIds.add(id));
        });

        // Create agent nodes
        agents.forEach(agent => {
            const nodeId = this._sanitizeId(agent.id);
            const label = this._createAgentLabel(agent);
            const shape = this._getAgentShape(agent.type);
            code += `    ${nodeId}${shape.open}"${label}"${shape.close}\n`;
        });

        // Create tool nodes
        tools.forEach(tool => {
            const nodeId = this._sanitizeId(tool.id);
            let className = tool.className || '';
            if (className.includes('.')) {
                className = className.substring(className.lastIndexOf('.') + 1);
            }
            const label = `<b>${tool.name || 'Tool'}</b><br/><small>${className}</small>`;
            code += `    ${nodeId}[/"${label.replace(/"/g, "'")}"/]\n`;
        });

        // Create workflow compositions (parent -> child relationships)
        compositions.forEach(comp => {
            const parentId = this._sanitizeId(comp.parentAgentId);
            const childIds = comp.childAgentIds || [];
            const compType = comp.type || 'SEQUENCE';

            if (compType === 'SEQUENCE') {
                // Sequential: connect children in order
                for (let i = 0; i < childIds.length; i++) {
                    const childId = this._sanitizeId(childIds[i]);
                    if (i === 0) {
                        code += `    ${parentId} --> ${childId}\n`;
                    }
                    if (i < childIds.length - 1) {
                        const nextChildId = this._sanitizeId(childIds[i + 1]);
                        code += `    ${childId} --> ${nextChildId}\n`;
                    }
                }
            } else if (compType === 'PARALLEL') {
                // Parallel: parent connects to all children
                childIds.forEach(childId => {
                    code += `    ${parentId} --> ${this._sanitizeId(childId)}\n`;
                });
            } else {
                // Other types: simple parent-child connection
                childIds.forEach(childId => {
                    code += `    ${parentId} --> ${this._sanitizeId(childId)}\n`;
                });
            }
        });

        // Create data flow connections
        dataFlows.forEach(flow => {
            const sourceId = this._sanitizeId(flow.sourceAgentId);
            const targetId = this._sanitizeId(flow.targetAgentId);
            const label = flow.variableName || flow.outputKey || '';
            if (label) {
                code += `    ${sourceId} -->|"${label}"| ${targetId}\n`;
            } else {
                code += `    ${sourceId} --> ${targetId}\n`;
            }
        });

        // Connect agents to their tools
        agents.forEach(agent => {
            const agentId = this._sanitizeId(agent.id);
            (agent.toolIds || []).forEach(toolId => {
                code += `    ${agentId} -.-> ${this._sanitizeId(toolId)}\n`;
            });
        });

        // Add imperative pattern nodes (when we can detect agent classes)
        const imperativeAgentRefs = new Set();
        const imperativeWorkflowNodeIds = new Set();

        if (imperativePatterns && imperativePatterns.length > 0) {
            // Helper to check if a class already exists in declarative agents
            const classExistsInAgents = (className) => {
                const shortName = className.includes('.') ? className.substring(className.lastIndexOf('.') + 1) : className;
                return agents.some(a =>
                    a.className === className ||
                    (a.className && a.className.endsWith('.' + shortName)) ||
                    a.className === shortName
                );
            };

            // Collect unique agent class references not already in declarative graph
            for (const pattern of imperativePatterns) {
                (pattern.agentClassRefs || []).forEach(ref => {
                    if (!classExistsInAgents(ref)) {
                        imperativeAgentRefs.add(ref);
                    }
                });
            }

            // Create nodes for imperative agent references
            for (const ref of imperativeAgentRefs) {
                const nodeId = this._sanitizeId('imperative_' + ref);
                const shortName = ref.includes('.') ? ref.substring(ref.lastIndexOf('.') + 1) : ref;
                const label = `<b>${shortName}</b><br/><i>[IMPERATIVE]</i>`;
                code += `    ${nodeId}[["${label.replace(/"/g, "'")}"]]\n`;
            }

            // Collect unique imperative workflow patterns (sequence, parallel, etc.)
            // Use Map to deduplicate: className -> Set of builder types
            const imperativeWorkflows = new Map();
            for (const pattern of imperativePatterns) {
                // Skip if this class already exists as a declarative agent
                if (classExistsInAgents(pattern.className)) {
                    continue;
                }

                if (!imperativeWorkflows.has(pattern.className)) {
                    imperativeWorkflows.set(pattern.className, new Set());
                }
                (pattern.builderTypes || []).forEach(t => {
                    if (t !== 'AGENT' && t !== 'SYSTEM') {
                        imperativeWorkflows.get(pattern.className).add(t);
                    }
                });
            }

            // Create nodes for imperative workflow patterns
            for (const [className, types] of imperativeWorkflows) {
                if (types.size > 0) {
                    const shortName = className.includes('.') ? className.substring(className.lastIndexOf('.') + 1) : className;
                    for (const type of types) {
                        const nodeId = this._sanitizeId('imperative_workflow_' + className + '_' + type);
                        imperativeWorkflowNodeIds.add(nodeId);
                        const shape = this._getAgentShape(type);
                        const label = `<b>${shortName}</b><br/><i>[${type} - Imperative]</i>`;
                        code += `    ${nodeId}${shape.open}"${label.replace(/"/g, "'")}"${shape.close}\n`;
                    }
                }
            }
        }

        // Add styling
        code += '\n';
        code += '    classDef agent fill:#e1f5fe,stroke:#01579b,stroke-width:2px\n';
        code += '    classDef sequence fill:#f3e5f5,stroke:#4a148c,stroke-width:2px\n';
        code += '    classDef parallel fill:#fff3e0,stroke:#e65100,stroke-width:2px\n';
        code += '    classDef tool fill:#e8f5e9,stroke:#1b5e20,stroke-width:1px\n';
        code += '    classDef aiservice fill:#fce4ec,stroke:#880e4f,stroke-width:2px\n';
        code += '    classDef a2a fill:#e0f7fa,stroke:#006064,stroke-width:2px,stroke-dasharray:3 3\n';
        code += '    classDef imperative fill:#fff9c4,stroke:#f9a825,stroke-width:2px,stroke-dasharray:5 5\n';

        // Apply classes
        agents.forEach(agent => {
            const nodeId = this._sanitizeId(agent.id);
            const className = this._getAgentClass(agent.type);
            code += `    class ${nodeId} ${className}\n`;
        });
        tools.forEach(tool => {
            code += `    class ${this._sanitizeId(tool.id)} tool\n`;
        });

        // Apply imperative class styling (using the Sets we built, avoiding duplicates)
        for (const ref of imperativeAgentRefs) {
            code += `    class ${this._sanitizeId('imperative_' + ref)} imperative\n`;
        }
        for (const nodeId of imperativeWorkflowNodeIds) {
            code += `    class ${nodeId} imperative\n`;
        }

        return code;
    }

    _sanitizeId(id) {
        if (!id) return 'unknown';
        // Mermaid IDs must be alphanumeric with underscores
        return id.replace(/[^a-zA-Z0-9]/g, '_');
    }

    _createAgentLabel(agent) {
        // Get the simple class name (last part after the last dot)
        let className = agent.className || '';
        if (className.includes('.')) {
            className = className.substring(className.lastIndexOf('.') + 1);
        }

        // Use class name as primary label, method name with parameters as secondary
        let label = `<b>${className}</b>`;
        if (agent.name && agent.name !== className) {
            const methodName = agent.methodName || agent.name;
            const params = this._formatInputParameters(agent.inputParameters);
            label += `<br/>${methodName}(${params})`;
        }

        // Add type badge for workflow agents and A2A
        if (agent.type && agent.type !== 'AGENT' && agent.type !== 'AI_SERVICE') {
            if (agent.type === 'A2A' && agent.remoteUrl) {
                label += `<br/><i>[A2A: ${agent.remoteUrl}]</i>`;
            } else {
                label += `<br/><i>[${agent.type}]</i>`;
            }
        }

        // Add short description if available
        if (agent.description && agent.description.length > 0 && agent.description !== 'AI Service') {
            const shortDesc = agent.description.length > 50
                ? agent.description.substring(0, 50) + '...'
                : agent.description;
            label += `<br/><small>${shortDesc}</small>`;
        }

        return label.replace(/"/g, "'");
    }

    _formatInputParameters(params) {
        if (!params || params.length === 0) {
            return '';
        }
        // Format as "name: Type" for each parameter, shortened if too many
        const formatted = params.map(p => {
            const name = p.name || '?';
            const type = p.type || '?';
            if (p.isMemoryId) {
                return `@MemoryId ${name}`;
            }
            return `${name}: ${type}`;
        });
        // If too many parameters, show first 2 and count
        if (formatted.length > 3) {
            return formatted.slice(0, 2).join(', ') + `, +${formatted.length - 2} more`;
        }
        return formatted.join(', ');
    }

    _getAgentShape(type) {
        switch (type) {
            case 'SEQUENCE':
                return { open: '([', close: '])' };  // Stadium shape
            case 'PARALLEL':
                return { open: '{{', close: '}}' };  // Hexagon
            case 'CONDITIONAL':
                return { open: '{', close: '}' };    // Diamond
            case 'LOOP':
                return { open: '(((', close: ')))' }; // Circle
            case 'AI_SERVICE':
                return { open: '[[', close: ']]' };  // Subroutine
            case 'A2A':
                return { open: '>', close: ']' };    // Asymmetric (flag shape for remote)
            default:
                return { open: '[', close: ']' };    // Rectangle
        }
    }

    _getAgentClass(type) {
        switch (type) {
            case 'SEQUENCE': return 'sequence';
            case 'PARALLEL': return 'parallel';
            case 'AI_SERVICE': return 'aiservice';
            case 'A2A': return 'a2a';
            default: return 'agent';
        }
    }

    async _renderDiagram() {
        if (!this._mermaidLoaded) return;

        const code = this._generateMermaidCode();
        this._mermaidCode = code;

        if (!code) return;

        const container = this.shadowRoot.querySelector('.diagram-container');
        if (!container) return;

        try {
            const { svg } = await window.mermaid.render('mermaid-diagram', code);
            container.innerHTML = svg;
        } catch (e) {
            console.error('Mermaid render error:', e);
            container.innerHTML = `<pre style="color: red;">Error rendering diagram:\n${e.message}\n\nGenerated code:\n${code}</pre>`;
        }
    }

    _copyMermaidCode() {
        navigator.clipboard.writeText(this._mermaidCode).then(() => {
            alert('Mermaid code copied to clipboard!');
        });
    }

    _renderImperativeDetails() {
        if (!imperativePatterns || imperativePatterns.length === 0) {
            return html`<p><small>Locations: ${imperativeLocations?.join(', ') || 'Unknown'}</small></p>`;
        }

        // Group patterns by class
        const byClass = {};
        for (const pattern of imperativePatterns) {
            if (!byClass[pattern.className]) {
                byClass[pattern.className] = { builderTypes: new Set(), methods: [], agentRefs: [] };
            }
            pattern.builderTypes?.forEach(t => byClass[pattern.className].builderTypes.add(t));
            if (pattern.methodName) {
                byClass[pattern.className].methods.push(pattern.methodName);
            }
            pattern.agentClassRefs?.forEach(r => byClass[pattern.className].agentRefs.push(r));
        }

        return html`
            <ul>
                ${Object.entries(byClass).map(([className, info]) => {
                    const shortName = className.includes('.') ? className.substring(className.lastIndexOf('.') + 1) : className;
                    const builders = Array.from(info.builderTypes);
                    return html`
                        <li>
                            <strong>${shortName}</strong>
                            ${builders.map(b => html`<span class="builder-badge">${b}</span>`)}
                            ${info.agentRefs.length > 0 ? html`
                                <br/><small>Agent references: ${info.agentRefs.map(r => r.includes('.') ? r.substring(r.lastIndexOf('.') + 1) : r).join(', ')}</small>
                            ` : ''}
                        </li>
                    `;
                })}
            </ul>
            <p><small>Tip: Consider using declarative annotations (@SequenceAgent, @ParallelAgent, etc.) for full visualization support.</small></p>
        `;
    }

    render() {
        const agents = agenticGraph?.agents || [];
        const tools = agenticGraph?.tools || [];
        const hasData = agents.length > 0 || tools.length > 0;

        return html`
            <div class="container">
                <div class="header">
                    <h2>Agentic Workflow Diagram</h2>
                    <div class="stats">
                        <div class="stat">
                            <div class="stat-value">${agentCount || 0}</div>
                            <div class="stat-label">Agents</div>
                        </div>
                        <div class="stat">
                            <div class="stat-value">${toolCount || 0}</div>
                            <div class="stat-label">Tools</div>
                        </div>
                    </div>
                </div>

                ${hasImperative ? html`
                    <div class="warning">
                        <div class="warning-header">
                            <span>⚠️</span>
                            <span>Imperative agent patterns detected</span>
                        </div>
                        <div class="warning-details">
                            <p>Some agents created via AgenticServices builders cannot be fully visualized because their structure is determined at runtime.</p>
                            ${this._renderImperativeDetails()}
                        </div>
                    </div>
                ` : ''}

                ${hasData ? html`
                    <div class="toolbar">
                        <label>
                            Layout:
                            <select @change=${(e) => { this._layoutDirection = e.target.value; this._renderDiagram(); }}>
                                <option value="LR" ?selected=${this._layoutDirection === 'LR'}>Left to Right</option>
                                <option value="TD" ?selected=${this._layoutDirection === 'TD'}>Top to Bottom</option>
                                <option value="RL" ?selected=${this._layoutDirection === 'RL'}>Right to Left</option>
                                <option value="BT" ?selected=${this._layoutDirection === 'BT'}>Bottom to Top</option>
                            </select>
                        </label>
                        <label>
                            <input type="checkbox" ?checked=${this._showTools} @change=${(e) => { this._showTools = e.target.checked; this._renderDiagram(); }}/>
                            Show Tools
                        </label>
                        <label>
                            <input type="checkbox" ?checked=${this._showAiServices} @change=${(e) => { this._showAiServices = e.target.checked; this._renderDiagram(); }}/>
                            Show AI Services
                        </label>
                        <button @click=${this._copyMermaidCode}>Copy Mermaid Code</button>
                        <button @click=${() => this._renderDiagram()}>Refresh</button>
                    </div>
                    <div class="diagram-container">
                        ${!this._mermaidLoaded ? html`<p>Loading diagram...</p>` : ''}
                    </div>
                ` : html`
                    <div class="empty-state">
                        <div class="empty-state-icon">🤖</div>
                        <p>No agentic patterns detected in your application.</p>
                        <p><small>Add @Agent, @SequenceAgent, or @RegisterAiService annotations to see them here.</small></p>
                    </div>
                `}

                <details>
                    <summary>View Mermaid Code</summary>
                    <pre class="mermaid-code">${this._mermaidCode || 'No diagram generated'}</pre>
                </details>

                <details>
                    <summary>Debug: Raw Data</summary>
                    <div class="debug">${JSON.stringify(agenticGraph, null, 2)}</div>
                </details>
            </div>
        `;
    }
}

customElements.define('qwc-agentic-viewer', QwcAgenticViewer);
