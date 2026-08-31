import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import routes from './router'
import { i18n } from './i18n'
import { useAuth } from './composables/useAuth'
import './styles.css'

const router = createRouter({
	history: createWebHistory(),
	routes
})

const { refreshMe, isLoggedIn } = useAuth()
let sessionRestored = false

// 首次导航前先调 /me 恢复会话（刷新 /favorites 也能正确识别登录态）；
// 之后再改由 useAuth/useFavorites 的登录态响应式联动。
router.beforeEach(async (to) => {
	if (!sessionRestored) {
		sessionRestored = true
		await refreshMe()
	}
	if (to.meta.requiresAuth && !isLoggedIn.value) {
		return { path: '/login', query: { redirect: to.fullPath } }
	}
})

createApp(App).use(router).use(i18n).mount('#app')
