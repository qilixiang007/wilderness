import HomePage from './pages/HomePage.vue'
import CategoryPage from './pages/CategoryPage.vue'
import DetailPage from './pages/DetailPage.vue'
import ObjectDetailPage from './pages/ObjectDetailPage.vue'

export default [
	{ path: '/', component: HomePage, meta: { pageTitle: '宇宙是旷野' } },
	{ path: '/categories', component: CategoryPage, meta: { pageTitle: '天体分类' } },
	{ path: '/detail/:slug', component: DetailPage, props: true, meta: { pageTitle: '天体分类' } },
	{ path: '/object/:slug', component: ObjectDetailPage, props: true, meta: { pageTitle: '天体详情' } }
]