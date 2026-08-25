<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, ApiUnavailableError, ApiError } from '../api'
import { celestialCategories } from '../data/celestial'
import OfflineNotice from '../components/OfflineNotice.vue'

const props = defineProps({ language: { type: String, required: true } })

const route = useRoute()
const category = ref(null)
const status = ref('loading') // loading | ready | offline | error | notfound
const error = ref('')

async function load() {
	const slug = route.params.slug
	category.value = null
	error.value = ''
	status.value = 'loading'
	try {
		category.value = await api.getCategory(slug)
		status.value = 'ready'
	} catch (e) {
		if (e instanceof ApiUnavailableError) {
			const fallback = celestialCategories.find((item) => item.slug === slug)
			if (fallback) {
				category.value = fallback
				status.value = 'offline'
			} else {
				status.value = 'notfound'
			}
		} else if (e instanceof ApiError && e.status === 404) {
			status.value = 'notfound'
		} else {
			error.value = e instanceof ApiError ? e.message : ''
			status.value = 'error'
		}
	}
}

watch(() => route.params.slug, load, { immediate: true })
</script>

<template>
	<main>
		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">03</p>
				<h3>{{ language === 'zh' ? '分类详情' : 'Category Detail' }}</h3>
			</div>

			<p v-if="status === 'loading'" class="loading-hint">{{ language === 'zh' ? '加载中…' : 'Loading…' }}</p>

			<div v-else-if="status === 'error'" class="load-error">
				<p>{{ error || (language === 'zh' ? '加载失败，请重试。' : 'Failed to load. Please retry.') }}</p>
				<button class="secondary-button" type="button" @click="load">
					{{ language === 'zh' ? '重试' : 'Retry' }}
				</button>
			</div>

			<p v-else-if="status === 'notfound'" class="detail-missing">{{ language === 'zh' ? '未找到该天体分类。' : 'Category not found.' }}</p>

			<template v-else-if="category">
				<OfflineNotice v-if="status === 'offline'" :language="language" />
				<article class="info-card detail-card">
					<div class="detail-visual">
						<img :src="category.image" :alt="language === 'zh' ? category.imageAltZh : category.imageAltEn" />
					</div>
					<h4>{{ language === 'zh' ? category.zhName : category.enName }}</h4>
					<p>{{ language === 'zh' ? category.zhDescription : category.enDescription }}</p>
				</article>

				<template v-if="status === 'ready'">
					<div class="section-heading compact object-heading">
						<h4>{{ language === 'zh' ? '天体列表' : 'Objects' }}</h4>
					</div>
					<div v-if="category.objects && category.objects.length" class="card-grid object-card-grid">
						<article v-for="object in category.objects" :key="object.slug" class="info-card object-card">
							<div class="object-visual">
								<img :src="object.image" :alt="language === 'zh' ? object.zhName : object.enName" />
							</div>
							<div class="object-copy">
								<h4>{{ language === 'zh' ? object.zhName : object.enName }}</h4>
								<p>{{ language === 'zh' ? object.zhDescription : object.enDescription }}</p>
								<a class="secondary-button" :href="`/object/${object.slug}`">{{ language === 'zh' ? '查看详情' : 'View details' }}</a>
							</div>
						</article>
					</div>
					<p v-else class="detail-missing">{{ language === 'zh' ? '该分类下暂无天体。' : 'No objects in this category yet.' }}</p>
				</template>
			</template>
		</section>
	</main>
</template>
