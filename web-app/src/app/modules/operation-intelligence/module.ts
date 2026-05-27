import OperationIntelligenceWorkspacePage from './pages/OperationIntelligenceWorkspacePage'
import KnowledgeGraphPage from './pages/KnowledgeGraphPage'
import type { AppModule } from '../../platform/module-types'

const operationIntelligenceModule: AppModule = {
    id: 'operation-intelligence',
    owner: 'platform',
    routes: [
        {
            id: 'operation-intelligence.index',
            path: '/operation-intelligence',
            component: OperationIntelligenceWorkspacePage,
            access: 'authenticated',
        },
        {
            id: 'operation-intelligence.knowledge-graph',
            path: '/operation-intelligence/knowledge-graph',
            component: KnowledgeGraphPage,
            access: 'authenticated',
        },
    ],
    navItems: [
        {
            id: 'operation-intelligence.nav',
            type: 'route',
            group: 'business',
            order: 11,
            titleKey: 'sidebar.operationIntelligence',
            icon: 'operationIntelligence',
            routeId: 'operation-intelligence.index',
            end: true,
        },
    ],
}

export default operationIntelligenceModule
