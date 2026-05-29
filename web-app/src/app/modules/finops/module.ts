import FinOpsPage from './pages/FinOpsPage'
import type { AppModule } from '../../platform/module-types'

const finopsModule: AppModule = {
    id: 'finops',
    owner: 'platform',
    routes: [
        {
            id: 'finops.index',
            path: '/finops',
            component: FinOpsPage,
            access: 'authenticated',
        },
    ],
    navItems: [
        {
            id: 'finops.nav',
            type: 'route',
            group: 'business',
            order: 15,
            titleKey: 'sidebar.finops',
            icon: 'finops',
            routeId: 'finops.index',
        },
    ],
}

export default finopsModule
