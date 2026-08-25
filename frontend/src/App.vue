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
				<h1>宇宙是旷野</h1>
			</div>
			<div class="topbar-actions">
				<nav class="topnav" aria-label="Primary">
					<a class="topnav-link" href="/">首页</a>
					<a class="topnav-link" href="/categories">分类页</a>
					<a class="topnav-link" href="/detail/star">详情页</a>
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
