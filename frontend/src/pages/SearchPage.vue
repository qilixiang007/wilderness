<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api, ApiUnavailableError } from '../api'
import ObjectCard from '../components/ObjectCard.vue'
import CompareHint from '../components/CompareHint.vue'
import { useCompare } from '../composables/useCompare'

const { isSelected } = useCompare()
const { t } = useI18n()
const route = useRoute()

const q = ref('')
const results = ref([])
const status = ref('idle') // idle | loading | ready | error
const error = ref('')

async function search() {
	const term = q.value.trim()
	if (!term) {
		results.value = []
		status.value = 'idle'
		return
	}
	status.value = 'loading'
	error.value = ''
	try {
		results.value = await api.search(term)
		status.value = 'ready'
	} catch (e) {
		// 后端不可用时带上底层错误名（AbortError=超时 / TypeError=网络失败或 CORS），方便定位
		error.value =
			e instanceof ApiUnavailableError
				? `${t('search.offline')} (${e.message})`
				: e.message || t('search.failed')
		status.value = 'error'
	}
}

// 支持从 URL ?q= 深链进入并直接搜索；顶栏再次搜索时 query 变化也会重新触发。
// 监听整个 query 对象：同词重复搜索时 App.vue 会追加 t=时间戳 强制产生新导航，
// 这里也能收到（watch 引用值变化即触发）。
watch(
	() => route.query,
	(query) => {
		const queryQ = query.q
		if (typeof queryQ === 'string' && queryQ.trim()) {
			q.value = queryQ
			search()
		}
	},
	{ immediate: true }
)
</script>

<template>
	<main>
		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('search.kicker') }}</p>
				<h3>{{ $t('search.title') }}</h3>
			</div>

			<p v-if="status === 'idle'" class="search-hint">{{ $t('search.emptyHint') }}</p>

			<p v-else-if="status === 'loading'" class="loading-hint">{{ $t('common.loading') }}</p>

			<div v-else-if="status === 'error'" class="load-error">
				<p>{{ error }}</p>
				<button class="secondary-button" type="button" @click="search">
					{{ $t('common.retry') }}
				</button>
			</div>

			<template v-else>
				<p v-if="results.length === 0" class="detail-missing">
					{{ $t('search.noResults', { q: q }) }}
				</p>
				<template v-else>
					<CompareHint />
					<div class="card-grid object-card-grid">
						<ObjectCard
							v-for="object in results"
							:key="object.slug"
							:object="object"
							selectable
							:selected="isSelected(object.slug)"
						/>
					</div>
				</template>
			</template>
		</section>
	</main>
</template>

<style scoped>
.search-hint {
	margin: 1.5rem 0;
	color: var(--muted);
}
</style>
