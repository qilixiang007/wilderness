<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { celestialCategories } from '../data/celestial'

defineProps({ language: { type: String, required: true } })

const route = useRoute()
const category = computed(() => celestialCategories.find((item) => item.slug === route.params.slug))
</script>

<template>
	<main>
		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">03</p>
				<h3>{{ language === 'zh' ? '详情页' : 'Detail Page' }}</h3>
			</div>

			<article v-if="category" class="info-card detail-card">
				<h4>{{ language === 'zh' ? category.zhName : category.enName }}</h4>
				<p>{{ language === 'zh' ? category.zhDescription : category.enDescription }}</p>
				<p class="detail-meta">{{ language === 'zh' ? '这里后续会放入更完整的图文、数据卡片和 AI 讲解入口。' : 'This area will later host richer media, data cards, and AI explanation entry.' }}</p>
			</article>

			<p v-else class="detail-missing">{{ language === 'zh' ? '未找到该天体。' : 'Celestial object not found.' }}</p>
		</section>
	</main>
</template>