<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import ThemeSelector from './components/ThemeSelector.vue'
import BackButton from './components/BackButton.vue'

const route = useRoute()
const theme = ref('deep-space')
const { locale, t } = useI18n()

function toggleLanguage() {
	locale.value = locale.value === 'zh' ? 'en' : 'zh'
}

// 语言切换即持久化，刷新后保持；页面标题跟随语言。
function updateTitle() {
	document.title = t(route.meta?.titleKey ?? 'site.title')
}

watch(locale, (value) => {
	try {
		localStorage.setItem('wilderness-locale', value)
	} catch {
		/* 存储不可用时仅内存态 */
	}
	updateTitle()
})

watch(() => route.path, updateTitle, { immediate: true })
</script>

<template>
	<div class="page-shell" :data-theme="theme">
		<header class="topbar">
			<div>
				<p class="eyebrow">Astronomy / 科普</p>
				<h1 class="site-title">{{ $t('site.title') }}</h1>
			</div>
			<div class="topbar-actions">
				<nav class="topnav" aria-label="Primary">
					<RouterLink class="topnav-link" to="/">{{ $t('nav.home') }}</RouterLink>
					<RouterLink class="topnav-link" to="/categories">{{ $t('nav.categories') }}</RouterLink>
					<RouterLink class="topnav-link" to="/detail/planet">{{ $t('nav.planets') }}</RouterLink>
					<RouterLink class="topnav-link" to="/ask">{{ $t('nav.ask') }}</RouterLink>
					<RouterLink class="topnav-link" to="/search">{{ $t('nav.search') }}</RouterLink>
					<RouterLink class="topnav-link" to="/favorites">{{ $t('nav.favorites') }}</RouterLink>
				</nav>
				<div class="toolbar-group">
					<ThemeSelector v-model="theme" />
					<button class="language-toggle" type="button" @click="toggleLanguage">
						{{ locale === 'zh' ? 'EN' : '中文' }}
					</button>
				</div>
			</div>
		</header>

		<BackButton v-if="route.path !== '/'" />

		<RouterView />
	</div>
</template>
