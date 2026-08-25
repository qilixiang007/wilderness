<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, ApiUnavailableError, ApiError } from '../api'
import OfflineNotice from '../components/OfflineNotice.vue'

const props = defineProps({ language: { type: String, required: true } })

const route = useRoute()
const object = ref(null)
const status = ref('loading') // loading | ready | offline | error | notfound
const error = ref('')

async function load() {
	const slug = route.params.slug
	object.value = null
	error.value = ''
	status.value = 'loading'
	try {
		object.value = await api.getObject(slug)
		status.value = 'ready'
	} catch (e) {
		if (e instanceof ApiUnavailableError) {
			status.value = 'offline'
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
				<p class="eyebrow">04</p>
				<h3>{{ language === 'zh' ? '天体详情' : 'Object Detail' }}</h3>
			</div>

			<p v-if="status === 'loading'" class="loading-hint">{{ language === 'zh' ? '加载中…' : 'Loading…' }}</p>

			<div v-else-if="status === 'error'" class="load-error">
				<p>{{ error || (language === 'zh' ? '加载失败，请重试。' : 'Failed to load. Please retry.') }}</p>
				<button class="secondary-button" type="button" @click="load">
					{{ language === 'zh' ? '重试' : 'Retry' }}
				</button>
			</div>

			<p v-else-if="status === 'notfound'" class="detail-missing">{{ language === 'zh' ? '未找到该天体。' : 'Celestial object not found.' }}</p>

			<div v-else-if="status === 'offline'" class="load-error">
				<OfflineNotice :language="language" />
				<p>{{ language === 'zh' ? '后端不可用，暂无该天体的离线数据。' : 'Backend unreachable; no offline data for this object.' }}</p>
			</div>

			<template v-else-if="object">
				<article class="info-card detail-card">
					<div class="detail-visual">
						<img :src="object.image" :alt="language === 'zh' ? object.zhName : object.enName" />
					</div>
					<p class="detail-category-link">
						<a :href="`/detail/${object.category.slug}`">← {{ language === 'zh' ? object.category.zhName : object.category.enName }}</a>
					</p>
					<h4>{{ language === 'zh' ? object.zhName : object.enName }}</h4>
					<p>{{ language === 'zh' ? object.zhDescription : object.enDescription }}</p>
				</article>

				<div v-if="object.facts && object.facts.length" class="section-heading compact object-heading">
					<h4>{{ language === 'zh' ? '关键数据' : 'Key facts' }}</h4>
				</div>
				<dl v-if="object.facts && object.facts.length" class="facts-list">
					<div v-for="fact in object.facts" :key="fact.sortOrder" class="fact-row">
						<dt>{{ language === 'zh' ? fact.zhLabel : fact.enLabel }}</dt>
						<dd>{{ language === 'zh' ? fact.zhValue : fact.enValue }}</dd>
					</div>
				</dl>
			</template>
		</section>
	</main>
</template>
