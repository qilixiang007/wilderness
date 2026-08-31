<script setup>
// 开始探索模块的英雄区：猎户座星图签名 + 星表统计。
// 统计来自后端，失败时用本地静态数据兜底（保证数字不撒谎）。
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { celestialCategories } from '../data/celestial'

const stats = ref(null)

onMounted(async () => {
	try {
		const categories = await api.getCategories()
		const objects = categories.reduce((sum, category) => sum + (category.objectCount || 0), 0)
		stats.value = { categories: categories.length, objects }
	} catch {
		stats.value = {
			categories: celestialCategories.length,
			objects: celestialCategories.reduce((sum, category) => sum + (category.objectCount || 0), 0)
		}
	}
})

// 「开始探索」按钮：当前已在探索页，改为平滑滚动到下方分类区
function scrollToCategories() {
	document.getElementById('explore-categories')?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<template>
	<section class="hero">
		<div class="hero-copy">
			<p class="eyebrow hero-kicker">{{ $t('home.heroKicker') }}</p>
			<h2>{{ $t('home.headline') }}</h2>
			<p>{{ $t('home.description') }}</p>
			<div class="hero-actions">
				<button class="primary-button" type="button" @click="scrollToCategories">
					{{ $t('home.exploreNow') }}
				</button>
				<button class="secondary-button" type="button" @click="$router.push('/detail/planet')">
					{{ $t('home.startWithPlanets') }}
				</button>
				<button class="secondary-button" type="button" @click="$router.push('/object/sun')">
					{{ $t('home.visitSun') }}
				</button>
				<button class="secondary-button" type="button" @click="$router.push('/ask')">
					{{ $t('home.ask') }}
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
				<span>{{ $t('home.catalogued') }}</span>
				<strong v-if="stats">
					{{ $t('home.stats', { categories: stats.categories, objects: stats.objects }) }}
				</strong>
				<strong v-else>{{ $t('home.statsFallback') }}</strong>
			</div>
		</div>
	</section>
</template>
