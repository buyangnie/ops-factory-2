import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import PageHeader from '../../../platform/ui/primitives/PageHeader'
import OperationIntelligencePage from './OperationIntelligencePage'
import KnowledgeGraphPage from './KnowledgeGraphPage'

type OperationWorkspaceTab = 'overview' | 'knowledge-graph'

export default function OperationIntelligenceWorkspacePage() {
    const { t } = useTranslation()
    const [activeTab, setActiveTab] = useState<OperationWorkspaceTab>('overview')
    const tabs: Array<{ key: OperationWorkspaceTab; label: string }> = [
        { key: 'overview', label: t('operationIntelligence.workspaceTabs.overview') },
        { key: 'knowledge-graph', label: t('operationIntelligence.workspaceTabs.knowledgeGraph') },
    ]

    return (
        <div className="page-container sidebar-top-page page-shell-wide operation-intelligence-page">
            <PageHeader
                title={t('operationIntelligence.title')}
                subtitle={t('operationIntelligence.workspaceSubtitle')}
            />

            <div className="config-tabs operation-intelligence-main-tabs" role="tablist" aria-label={t('operationIntelligence.workspaceTabsLabel')}>
                {tabs.map(tab => (
                    <button
                        key={tab.key}
                        type="button"
                        role="tab"
                        aria-selected={activeTab === tab.key}
                        className={`config-tab ${activeTab === tab.key ? 'config-tab-active' : ''}`}
                        onClick={() => setActiveTab(tab.key)}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {activeTab === 'overview' ? (
                <OperationIntelligencePage embedded />
            ) : (
                <KnowledgeGraphPage embedded />
            )}
        </div>
    )
}
