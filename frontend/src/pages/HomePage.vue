<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { celestialCategories } from '../data/celestial'

defineProps({ language: { type: String, required: true } })

const stats = ref(null)

onMounted(async () => {
	try {
		const categories = await api.getCategories()
		const objects = categories.reduce((sum, category) => sum + (category.objectCount || 0), 0)
		stats.value = { categories: categories.length, objects }
	} catch {
		// 后端不可用：用静态数据兜底，保证统计不撒谎
		stats.value = {
			categories: celestialCategories.length,
			objects: celestialCategories.reduce((sum, category) => sum + (category.objectCount || 0), 0)
		}
	}
})
</script>

<template>
	<main>
		<section class="hero">
			<div class="hero-copy">
				<p class="eyebrow hero-kicker">
					{{ language === 'zh' ? '旷野观星 · 天文科普' : 'A FIELD GUIDE TO THE COSMOS' }}
				</p>
				<h2>{{ language === 'zh' ? '把浩瀚宇宙，讲给每个人听。' : 'Explaining the universe to everyone.' }}</h2>
				<p>{{ language === 'zh' ? '中英双语的星空导览：从恒星到星云，按分类浏览，再慢慢深入每个天体的数据与故事。' : 'A bilingual tour of the night sky — from stars to nebulae, browsable by category and rich with data and stories.' }}</p>
				<div class="hero-actions">
					<button class="primary-button" type="button" @click="$router.push('/categories')">
						{{ language === 'zh' ? '开始探索' : 'Explore now' }}
					</button>
					<button class="secondary-button" type="button" @click="$router.push('/detail/planet')">
						{{ language === 'zh' ? '从行星看起' : 'Start with planets' }}
					</button>
					<button class="secondary-button" type="button" @click="$router.push('/object/sun')">
						{{ language === 'zh' ? '看看太阳' : 'Visit the Sun' }}
					</button>
					<button class="secondary-button" type="button" @click="$router.push('/ask')">
						{{ language === 'zh' ? 'AI 问答' : 'Ask AI' }}
					</button>
				</div>
			</div>

			<div class="hero-panel">
				<!-- 签名元素：猎户座星图（参宿四、参宿七、M42 猎户座星云） -->
				<svg class="constellation" viewBox="0 0 400 320" role="img" aria-label="Orion constellation">
					<g class="const-lines">
						<path class="const-line" d="M150 70 L188 132" style="animation-delay: 0s" />
						<path class="const-line" d="M262 62 L218 148" style="animation-delay: 0s" />
						<path class="const-line" d="M188 132 L203 140" style="animation-delay: 0.25s" />
						<path class="const-line" d="M203 140 L218 148" style="animation-delay: 0.3s" />
						<path class="const-line" d="M188 132 L132 240" style="animation-delay: 0.5s" />
						<path class="const-line" d="M218 148 L268 228" style="animation-delay: 0.55s" />
					</g>
					<g class="const-stars">
						<circle class="const-star" cx="150" cy="70" r="3.2" style="animation-delay: 0.15s" />
						<circle class="const-star" cx="262" cy="62" r="2.8" style="animation-delay: 0.15s" />
						<circle class="const-star belt" cx="188" cy="132" r="2.6" style="animation-delay: 0.4s" />
						<circle class="const-star belt" cx="203" cy="140" r="2.6" style="animation-delay: 0.45s" />
						<circle class="const-star belt" cx="218" cy="148" r="2.6" style="animation-delay: 0.5s" />
						<circle class="const-star" cx="132" cy="240" r="2.8" style="animation-delay: 0.65s" />
						<circle class="const-star" cx="268" cy="228" r="3.2" style="animation-delay: 0.65s" />
						<circle class="const-star belt" cx="203" cy="172" r="1.6" style="animation-delay: 0.9s" />
					</g>
					<g class="const-labels">
						<text class="const-label" x="132" y="56" text-anchor="end">参宿四 · Betelgeuse</text>
						<text class="const-label" x="284" y="252" text-anchor="start">参宿七 · Rigel</text>
						<text class="const-label accent" x="203" y="198" text-anchor="middle">M42 · 猎户座星云</text>
					</g>
				</svg>

				<div class="hero-stat">
					<span>{{ language === 'zh' ? '星表已收录' : 'CATALOGUED' }}</span>
					<strong v-if="stats">
						{{ language === 'zh' ? `${stats.categories} 个分类 · ${stats.objects} 个天体` : `${stats.categories} categories · ${stats.objects} objects` }}
					</strong>
					<strong v-else>{{ language === 'zh' ? '6 个分类 · 16 个天体' : '6 categories · 16 objects' }}</strong>
				</div>
			</div>
		</section>

		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">{{ language === 'zh' ? '观星导览' : "OBSERVER'S NOTES" }}</p>
				<h3>{{ language === 'zh' ? '从这里出发' : 'Where to start' }}</h3>
				<p>{{ language === 'zh' ? '从头顶的星座到亿万光年外的星系，慢慢认识这片旷野。先从分类开始浏览，或直接看看你好奇的天体。' : 'From the constellations overhead to galaxies billions of light-years away, get to know the wilderness above. Start by category, or jump straight to an object you are curious about.' }}</p>
				<p v-if="stats" class="home-stats">
					{{ language === 'zh' ? `来自后端：${stats.categories} 个分类 · ${stats.objects} 个天体` : `From backend: ${stats.categories} categories · ${stats.objects} objects` }}
				</p>
			</div>
		</section>
	</main>
</template>
