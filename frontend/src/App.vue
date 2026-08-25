<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import ThemeSelector from './components/ThemeSelector.vue'
import BackButton from './components/BackButton.vue'

const route = useRoute()
const language = ref('zh')
const theme = ref('deep-space')

function toggleLanguage() {
	language.value = language.value === 'zh' ? 'en' : 'zh'
}
</script>

<template>
	<div class="page-shell" :data-theme="theme">
		<header class="topbar">
			<div>
				<p class="eyebrow">Astronomy / 科普</p>
				<h1 class="site-title">宇宙是旷野</h1>
			</div>
			<div class="topbar-actions">
				<nav class="topnav" aria-label="Primary">
					<RouterLink class="topnav-link" to="/">{{ language === 'zh' ? '首页' : 'Home' }}</RouterLink>
					<RouterLink class="topnav-link" to="/categories">{{ language === 'zh' ? '星表' : 'Index' }}</RouterLink>
					<RouterLink class="topnav-link" to="/detail/planet">{{ language === 'zh' ? '行星' : 'Planets' }}</RouterLink>
					<RouterLink class="topnav-link" to="/ask">{{ language === 'zh' ? 'AI 问答' : 'Ask AI' }}</RouterLink>
				</nav>
				<div class="toolbar-group">
					<ThemeSelector v-model="theme" :language="language" />
					<button class="language-toggle" type="button" @click="toggleLanguage">
						{{ language === 'zh' ? 'EN' : '中文' }}
					</button>
				</div>
			</div>
		</header>

		<BackButton v-if="route.path !== '/'" :language="language" />

		<RouterView v-slot="{ Component }">
			<component :is="Component" :language="language" />
		</RouterView>
	</div>
</template>
