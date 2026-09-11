<script setup>
// 首页"公开广场"精选板块：拉取最新几条公开生成天体，卡片视觉照抄 GeneratedFavoriteCard.vue，
// 操作区只有"查看更多"跳完整广场页——不做筛选、不做详情路由。
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import CelestialVisual from './CelestialVisual.vue'
import OfflineNotice from './OfflineNotice.vue'
import { api, ApiUnavailableError } from '../api'

const PREVIEW_SIZE = 8

const { t } = useI18n()

const items = ref([])
const loading = ref(true)
const offline = ref(false)
const error = ref('')

async function load() {
	loading.value = true
	offline.value = false
	error.value = ''
	try {
		const res = await api.getGallery(0, PREVIEW_SIZE)
		items.value = res.items
	} catch (e) {
		if (e instanceof ApiUnavailableError) {
			offline.value = true
		} else {
			error.value = t('common.loadFailed')
		}
	} finally {
		loading.value = false
	}
}

onMounted(load)
</script>

<template>
	<div class="gallery-section-wrap">
		<p v-if="loading" class="loading-hint">{{ $t('common.loading') }}</p>

		<div v-else-if="error" class="load-error">
			<p>{{ error }}</p>
			<button class="secondary-button" type="button" @click="load">
				{{ $t('common.retry') }}
			</button>
		</div>

		<template v-else>
			<OfflineNotice v-if="offline" />
			<p v-if="!offline && items.length === 0" class="gallery-empty">{{ $t('gallery.empty') }}</p>

			<div v-else class="card-grid gallery-preview-grid">
				<article v-for="item in items" :key="item.id" class="info-card gallery-card">
					<div class="gallery-visual">
						<img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" />
						<CelestialVisual v-else class="fit-visual" :render="item.render || {}" :name="item.name" />
					</div>
					<div class="object-copy">
						<h4>{{ item.name }}</h4>
						<p v-if="item.type">{{ item.type }}</p>
						<p v-if="item.imageUrl && item.imageTemporary" class="image-temporary-hint">
							{{ $t('agent.imageTemporaryHint') }}
						</p>
					</div>
				</article>
			</div>

			<div class="gallery-more">
				<RouterLink class="secondary-button" to="/gallery">{{ $t('gallery.viewMore') }}</RouterLink>
			</div>
		</template>
	</div>
</template>

<style scoped>
.gallery-preview-grid {
	grid-template-columns: repeat(4, minmax(0, 1fr));
}

.gallery-card {
	display: flex;
	flex-direction: column;
	padding: 0;
	overflow: hidden;
}

.gallery-visual {
	position: relative;
	aspect-ratio: 1 / 1;
	overflow: hidden;
	background: linear-gradient(180deg, var(--bg-soft), rgba(0, 0, 0, 0.15));
}

.gallery-visual img {
	display: block;
	width: 100%;
	height: 100%;
	object-fit: contain;
}

.gallery-visual :deep(.fit-visual) {
	width: 100%;
	height: 100%;
}

.image-temporary-hint {
	color: var(--danger, #e57373);
	font-size: 0.78rem;
}

.gallery-empty {
	color: var(--muted);
	font-size: 0.9rem;
	margin: 1.5rem 0;
}

.gallery-more {
	display: flex;
	justify-content: center;
	margin-top: 1.2rem;
}

@media (max-width: 860px) {
	.gallery-preview-grid {
		grid-template-columns: repeat(2, minmax(0, 1fr));
	}
}

@media (max-width: 520px) {
	.gallery-preview-grid {
		grid-template-columns: 1fr;
	}
}
</style>
