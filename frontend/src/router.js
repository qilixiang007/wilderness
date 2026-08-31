import HomePage from './pages/HomePage.vue'
import CategoryPage from './pages/CategoryPage.vue'
import DetailPage from './pages/DetailPage.vue'
import ObjectDetailPage from './pages/ObjectDetailPage.vue'
import AskPage from './pages/AskPage.vue'
import SearchPage from './pages/SearchPage.vue'
import FavoritesPage from './pages/FavoritesPage.vue'

export default [
	{ path: '/', component: HomePage, meta: { titleKey: 'site.title' } },
	{ path: '/categories', component: CategoryPage, meta: { titleKey: 'category.title' } },
	{ path: '/detail/:slug', component: DetailPage, props: true, meta: { titleKey: 'category.file' } },
	{ path: '/object/:slug', component: ObjectDetailPage, props: true, meta: { titleKey: 'object.file' } },
	{ path: '/ask', component: AskPage, meta: { titleKey: 'ask.title' } },
	{ path: '/search', component: SearchPage, meta: { titleKey: 'search.title' } },
	{ path: '/favorites', component: FavoritesPage, meta: { titleKey: 'favorites.title' } }
]
