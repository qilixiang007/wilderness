<script setup>
import { celestialCategories } from '../data/celestial'
defineProps({ language: { type: String, required: true } })
</script>

<template>
	<main>
		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">02</p>
				<h3>{{ language === 'zh' ? '天体分类' : 'Celestial Categories' }}</h3>
				<p>{{ language === 'zh' ? '按类型浏览天体知识。' : 'Browse celestial knowledge by type.' }}</p>
			</div>

			<div class="card-grid">
				<article v-for="item in celestialCategories" :key="item.slug" class="info-card">
					<h4>{{ language === 'zh' ? item.zhName : item.enName }}</h4>
					<p>{{ language === 'zh' ? item.zhDescription : item.enDescription }}</p>
					<a class="secondary-button" :href="`/detail/${item.slug}`">{{ language === 'zh' ? '查看详情' : 'View details' }}</a>
				</article>
			</div>
		</section>
	</main>
</template>