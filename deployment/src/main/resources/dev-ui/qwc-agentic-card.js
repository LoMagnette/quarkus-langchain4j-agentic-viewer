import { LitElement, html, css } from 'lit';
import { agentCount, toolCount, hasImperative } from 'build-time-data';

/**
 * Summary card component for the Agentic Viewer extension.
 */
export class QwcAgenticCard extends LitElement {

    static styles = css`
        :host {
            display: block;
        }

        .card-content {
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
        }

        .stats-row {
            display: flex;
            gap: 1rem;
        }

        .stat {
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        .stat-icon {
            font-size: 1.25rem;
        }

        .stat-value {
            font-size: 1.5rem;
            font-weight: bold;
            color: var(--lumo-primary-color);
        }

        .stat-label {
            color: var(--lumo-secondary-text-color);
            font-size: 0.85rem;
        }

        .warning {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.5rem;
            background: var(--lumo-warning-color-10pct);
            border-radius: 4px;
            font-size: 0.85rem;
            color: var(--lumo-warning-text-color);
        }

        .empty-state {
            color: var(--lumo-secondary-text-color);
            font-style: italic;
        }
    `;

    render() {
        const hasData = agentCount > 0 || toolCount > 0;

        if (!hasData) {
            return html`
                <div class="card-content">
                    <div class="empty-state">
                        No agentic patterns detected
                    </div>
                </div>
            `;
        }

        return html`
            <div class="card-content">
                <div class="stats-row">
                    <div class="stat">
                        <span class="stat-icon">🤖</span>
                        <span class="stat-value">${agentCount}</span>
                        <span class="stat-label">Agents</span>
                    </div>
                    <div class="stat">
                        <span class="stat-icon">🔧</span>
                        <span class="stat-value">${toolCount}</span>
                        <span class="stat-label">Tools</span>
                    </div>
                </div>
                ${hasImperative ? html`
                    <div class="warning">
                        ⚠️ Imperative patterns detected
                    </div>
                ` : ''}
            </div>
        `;
    }
}

customElements.define('qwc-agentic-card', QwcAgenticCard);
