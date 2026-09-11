import HomePage from './pages/HomePage.vue'
import DetailPage from './pages/DetailPage.vue'
import ObjectDetailPage from './pages/ObjectDetailPage.vue'
import AskPage from './pages/AskPage.vue'
import KnowledgePage from './pages/KnowledgePage.vue'
import AgentPage from './pages/AgentPage.vue'
import SearchPage from './pages/SearchPage.vue'
import ComparePage from './pages/ComparePage.vue'
import FavoritesPage from './pages/FavoritesPage.vue'
import HistoryPage from './pages/HistoryPage.vue'
import AuthPage from './pages/AuthPage.vue'
import AccountPage from './pages/AccountPage.vue'
import GalleryPage from './pages/GalleryPage.vue'

export default [
	{ path: '/', component: HomePage, meta: { titleKey: 'site.title' } },
	{ path: '/detail/:slug', component: DetailPage, props: true, meta: { titleKey: 'category.file' } },
	{ path: '/object/:slug', component: ObjectDetailPage, props: true, meta: { titleKey: 'object.file' } },
	{ path: '/knowledge', component: KnowledgePage, meta: { titleKey: 'knowledge.title' } },
	{ path: '/ask', component: AskPage, meta: { titleKey: 'ask.title' } },
	{ path: '/agent', component: AgentPage, meta: { titleKey: 'agent.title' } },
	{ path: '/search', component: SearchPage, meta: { titleKey: 'search.title' } },
	{ path: '/compare', component: ComparePage, meta: { titleKey: 'compare.title' } },
	{ path: '/favorites', component: FavoritesPage, meta: { requiresAuth: true, titleKey: 'favorites.title' } },
	{ path: '/history', component: HistoryPage, meta: { requiresAuth: true, titleKey: 'history.title' } },
	{ path: '/login', component: AuthPage, meta: { titleKey: 'auth.title' } },
	{ path: '/account', component: AccountPage, meta: { requiresAuth: true, titleKey: 'account.title' } },
	{ path: '/gallery', component: GalleryPage, meta: { titleKey: 'gallery.title' } }
]
