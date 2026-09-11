<script setup>
// 开始探索页：由英雄区 + 分类栅格两个模块组合而成，各自独立成组件文件。
import ExploreHero from '../components/ExploreHero.vue'
import CategoryGrid from '../components/CategoryGrid.vue'
import PublicGallerySection from '../components/PublicGallerySection.vue'
</script>

<template>
	<main>
		<ExploreHero />

		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('gallery.kicker') }}</p>
				<h3>{{ $t('gallery.title') }}</h3>
				<p>{{ $t('gallery.intro') }}</p>
			</div>

			<PublicGallerySection />
		</section>

		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('home.observerNotes') }}</p>
				<h3>{{ $t('category.title') }}</h3>
				<p>{{ $t('category.intro') }}</p>
			</div>

			<CategoryGrid />
		</section>
	</main>
</template>
