<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'

defineProps({ language: { type: String, required: true } })

const stats = ref(null)

onMounted(async () => {
	try {
		const categories = await api.getCategories()
		const objects = categories.reduce((sum, category) => sum + (category.objectCount || 0), 0)
		stats.value = { categories: categories.length, objects }
	} catch {
		/* 后端不可用时保持静默，首页仍可静态浏览 */
	}
})
</script>

<template>
	<main>
		<section class="hero">
			<div class="hero-copy">
				<p class="hero-kicker">Bilingual astronomy showcase</p>
				<h2>{{ language === 'zh' ? '把浩瀚宇宙，讲给每个人听。' : 'Explaining the universe in a way everyone can follow.' }}</h2>
				<p>{{ language === 'zh' ? '这是一个中英双语的天文科普网页，先聚焦恒星、行星、卫星、星系、星云、彗星、小行星等基础天体，再逐步扩展到搜索、收藏、AI 讲解和公开 API 接入。' : 'This bilingual astronomy site starts with stars, planets, moons, galaxies, nebulae, comets, and asteroids, then grows into search, bookmarks, AI explanations, and public API integration.' }}</p>
				<div class="hero-actions">
					<button class="primary-button" type="button" @click="$router.push('/categories')">
						{{ language === 'zh' ? '开始探索' : 'Explore now' }}
					</button>
					<button class="secondary-button" type="button" @click="$router.push('/categories')">
						{{ language === 'zh' ? '查看分类页' : 'View categories' }}
					</button>
					<button class="secondary-button" type="button" @click="$router.push('/detail/star')">
						{{ language === 'zh' ? '查看详情页' : 'View details' }}
					</button>
				</div>
			</div>
			<div class="hero-panel">
				<div class="orbital-ring"></div>
				<div class="hero-stat">
					<span>{{ language === 'zh' ? '第一版重点' : 'First release focus' }}</span>
					<strong>{{ language === 'zh' ? '展示型 + 双语' : 'Presentation-first + bilingual' }}</strong>
				</div>
			</div>
		</section>

		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">01</p>
				<h3>{{ language === 'zh' ? '首页概览' : 'Home overview' }}</h3>
				<p>{{ language === 'zh' ? '首版以展示型为主，帮助用户快速进入天体世界。' : 'The first version focuses on a clear, presentation-driven entry into astronomy.' }}</p>
				<p v-if="stats" class="home-stats">
					{{ language === 'zh' ? `来自后端：${stats.categories} 个分类 · ${stats.objects} 个天体` : `From backend: ${stats.categories} categories · ${stats.objects} objects` }}
				</p>
			</div>
		</section>
	</main>
</template>