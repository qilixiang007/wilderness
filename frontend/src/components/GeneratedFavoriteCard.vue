<script setup>
// 收藏页"自建天体" tab 用的卡片：结构参考 ObjectCard.vue，但生成天体没有独立详情路由，
// 视觉用 imageUrl 或 CelestialVisual 兜底渲染，操作区只有"取消收藏"。
import CelestialVisual from './CelestialVisual.vue'

defineProps({
	item: { type: Object, required: true } // FavoriteGenerationDTO：generationId/name/type/render/imageUrl/...
})

defineEmits(['remove'])
</script>

<template>
	<article class="info-card generated-card">
		<div class="generated-visual">
			<img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" />
			<CelestialVisual v-else class="fit-visual" :render="item.render || {}" :name="item.name" />
		</div>
		<div class="object-copy">
			<h4>{{ item.name }}</h4>
			<p v-if="item.type">{{ item.type }}</p>
			<button class="secondary-button" type="button" @click="$emit('remove', item)">
				{{ $t('favorites.remove') }}
			</button>
		</div>
	</article>
</template>

<style scoped>
.generated-card {
	display: flex;
	flex-direction: column;
	padding: 0;
	overflow: hidden;
}

.generated-visual {
	position: relative;
	aspect-ratio: 1 / 1;
	overflow: hidden;
	background: linear-gradient(180deg, var(--bg-soft), rgba(0, 0, 0, 0.15));
}

.generated-visual img {
	display: block;
	width: 100%;
	height: 100%;
	object-fit: contain;
}

.generated-visual :deep(.fit-visual) {
	width: 100%;
	height: 100%;
}
</style>
