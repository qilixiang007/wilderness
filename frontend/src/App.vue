<script setup>
import { ref } from 'vue'

const language = ref('zh')

function toggleLanguage() {
	language.value = language.value === 'zh' ? 'en' : 'zh'
}
</script>

<template>
	<div class="page-shell">
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
				<button class="language-toggle" type="button" @click="toggleLanguage">
					{{ language === 'zh' ? 'EN' : '中文' }}
				</button>
			</div>
		</header>

		<RouterView v-slot="{ Component }">
			<component :is="Component" :language="language" />
		</RouterView>
	</div>
</template>
