<script setup>
defineProps({ language: { type: String, required: true } })
</script>

<template>
	<main>
		<section class="hero">
			<div class="hero-copy">
				<p class="hero-kicker">Bilingual astronomy showcase</p>
				<h2>{{ language === 'zh' ? '把浩瀚宇宙，讲给每个人听。' : 'Explaining the universe in a way everyone can follow.' }}</h2>
				<p>{{ language === 'zh' ? '这是一个中英双语的天文科普网页，先聚焦恒星、行星、卫星、星系、星云、彗星、小行星等基础天体，再逐步扩展到搜索、收藏、AI 讲解和公开 API 接入。' : 'This bilingual astronomy site starts with stars, planets, moons, galaxies, nebulae, comets, and asteroids, then grows into search, bookmarks, AI explanations, and public API integration.' }}</p>
				<div class="hero-actions">
					<a class="primary-button" href="/categories">{{ language === 'zh' ? '开始探索' : 'Explore now' }}</a>
					<a class="secondary-button" href="/categories">{{ language === 'zh' ? '查看分类页' : 'View categories' }}</a>
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
			</div>
		</section>
	</main>
</template>