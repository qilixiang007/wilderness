<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Sidebar from './components/Sidebar.vue'
import ThemeSelector from './components/ThemeSelector.vue'
import LanguageSelector from './components/LanguageSelector.vue'
import BackButton from './components/BackButton.vue'
import CompareBar from './components/CompareBar.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
import { useAuth } from './composables/useAuth'
import { useTheme } from './composables/useTheme'
import { useCompare } from './composables/useCompare'
import { saveToStorage } from './utils/storage'

const route = useRoute()
const router = useRouter()
const { theme } = useTheme()
const { locale, t } = useI18n()
const { user, isLoggedIn, logout } = useAuth()
const { count: compareCount } = useCompare()
const showCompareBar = computed(() => compareCount.value > 0 && route.path !== '/compare')

// 退出登录：清会话后若在收藏页则回首页
async function handleLogout() {
	await logout()
	if (route.path === '/favorites') {
		router.push('/')
	}
}

// 移动端抽屉开关（桌面固定显示侧边栏）
const sidebarOpen = ref(false)
// 顶栏全局搜索
const searchTerm = ref('')

// 顶栏搜索：跳转搜索页并带上关键词（SearchPage 支持 ?q= 深链）
function goSearch() {
	const term = searchTerm.value.trim()
	if (!term) return
	// 已在搜索页时，vue-router 对相同 query 的 push 会当作重复导航直接跳过，
	// 导致 SearchPage 收不到 query 变化、不触发搜索；追加时间戳强制产生新导航。
	const query = route.path === '/search' ? { q: term, t: Date.now() } : { q: term }
	router.push({ path: '/search', query })
}

// 在搜索页时，顶栏输入框回填当前关键词（URL 深链 / 返回时保持一致）
watch(
	() => route.query.q,
	(queryQ) => {
		if (route.path === '/search' && typeof queryQ === 'string') {
			searchTerm.value = queryQ
		}
	},
	{ immediate: true }
)

// 语言切换即持久化，刷新后保持；页面标题跟随语言。
function updateTitle() {
	document.title = t(route.meta?.titleKey ?? 'site.title')
}

watch(locale, (value) => {
	saveToStorage('wilderness-locale', value)
	updateTitle()
})

watch(() => route.path, updateTitle, { immediate: true })
</script>

<template>
	<div class="page-shell" :data-theme="theme">
		<Sidebar :open="sidebarOpen" @close="sidebarOpen = false" />

		<div class="main-area" :class="{ 'has-compare-bar': showCompareBar }">
			<header class="topbar">
				<button
					class="hamburger"
					type="button"
					:aria-label="$t('nav.menu')"
					@click="sidebarOpen = !sidebarOpen"
				>
					☰
				</button>
				<form class="top-search" role="search" @submit.prevent="goSearch">
					<input
						v-model="searchTerm"
						type="search"
						:placeholder="$t('search.placeholder')"
						:aria-label="$t('search.placeholder')"
					/>
					<button class="secondary-button" type="submit">
						{{ $t('search.submit') }}
					</button>
				</form>
				<div class="toolbar-group">
					<ThemeSelector v-model="theme" />
					<LanguageSelector v-model="locale" />
					<template v-if="isLoggedIn">
						<RouterLink class="user-chip" to="/account" :title="user.email">{{ user.email }}</RouterLink>
						<button class="auth-link" type="button" @click="handleLogout">
							{{ $t('auth.logout') }}
						</button>
					</template>
					<RouterLink v-else class="auth-link" to="/login">
						{{ $t('auth.loginTitle') }}
					</RouterLink>
				</div>
			</header>

			<BackButton v-if="route.path !== '/'" />

			<RouterView />
		</div>

		<CompareBar />
		<ConfirmDialog />
	</div>
</template>

<style scoped>
.main-area.has-compare-bar {
	padding-bottom: 88px;
}

.user-chip {
	max-width: 10rem;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-size: 0.8rem;
	color: var(--muted);
	text-decoration: none;
}

.user-chip:hover {
	color: var(--accent);
	text-decoration: underline;
}

.auth-link {
	font-size: 0.82rem;
	color: var(--accent);
	text-decoration: none;
	background: none;
	border: none;
	padding: 0.2rem 0.4rem;
	cursor: pointer;
	white-space: nowrap;
}

.auth-link:hover {
	text-decoration: underline;
}
</style>
