<script setup>
import { onMounted, ref } from 'vue'
import { api, ApiUnavailableError, ApiError } from '../api'
import { celestialCategories } from '../data/celestial'
import OfflineNotice from '../components/OfflineNotice.vue'

const props = defineProps({ language: { type: String, required: true } })

const categories = ref([])
const loading = ref(true)
const offline = ref(false)
const error = ref('')

async function load() {
	loading.value = true
	offline.value = false
	error.value = ''
	try {
		categories.value = await api.getCategories()
	} catch (e) {
		if (e instanceof ApiUnavailableError) {
			offline.value = true
			categories.value = celestialCategories
		} else if (e instanceof ApiError) {
			error.value = e.message
		} else {
			error.value = props.language === 'zh' ? '加载失败，请重试。' : 'Failed to load. Please retry.'
		}
	} finally {
		loading.value = false
	}
}

onMounted(load)
</script>

<template>
	<main>
		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">02</p>
				<h3>{{ language === 'zh' ? '天体分类' : 'Celestial Categories' }}</h3>
				<p>{{ language === 'zh' ? '按类型浏览天体知识。' : 'Browse celestial knowledge by type.' }}</p>
			</div>

			<p v-if="loading" class="loading-hint">{{ language === 'zh' ? '加载中…' : 'Loading…' }}</p>

			<div v-else-if="error" class="load-error">
				<p>{{ error }}</p>
				<button class="secondary-button" type="button" @click="load">
					{{ language === 'zh' ? '重试' : 'Retry' }}
				</button>
			</div>

			<template v-else>
				<OfflineNotice v-if="offline" :language="language" />
				<div class="card-grid category-grid">
					<article v-for="item in categories" :key="item.slug" class="info-card category-card">
						<div class="category-visual">
							<img :src="item.image" :alt="language === 'zh' ? item.imageAltZh : item.imageAltEn" />
						</div>
						<div class="category-copy">
							<h4>{{ language === 'zh' ? item.zhName : item.enName }}</h4>
							<p class="category-count">
								{{ language === 'zh' ? `${item.objectCount} 个天体` : `${item.objectCount} objects` }}
							</p>
							<p>{{ language === 'zh' ? item.zhDescription : item.enDescription }}</p>
							<a class="secondary-button" :href="`/detail/${item.slug}`">{{ language === 'zh' ? '查看详情' : 'View details' }}</a>
						</div>
					</article>
				</div>
			</template>
		</section>
	</main>
</template>
