<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api, ApiError } from '../api'
import { pick } from '../i18n'
import { COMPARE_MAX } from '../composables/useCompare'
import MarkdownView from '../components/MarkdownView.vue'

const { t } = useI18n()
const route = useRoute()

const slugs = ref([])

// A 路：各天体基础参数（独立于 B 路，互不阻塞）
const objects = ref([]) // [{ slug, ok, data }]

// B 路：AI 讲解 + 综合总结
const compareStatus = ref('idle') // idle | loading | ready | error
const compareItems = ref([])
const overview = ref(null)
const compareErrorMessage = ref('')

async function loadObjects() {
	const results = await Promise.allSettled(slugs.value.map((slug) => api.getObject(slug)))
	objects.value = results.map((r, i) => ({
		slug: slugs.value[i],
		ok: r.status === 'fulfilled',
		data: r.status === 'fulfilled' ? r.value : null
	}))
}

async function loadCompare() {
	compareStatus.value = 'loading'
	compareErrorMessage.value = ''
	try {
		const result = await api.compare(slugs.value)
		compareItems.value = result.items
		overview.value = result.overview
		compareStatus.value = 'ready'
	} catch (e) {
		compareErrorMessage.value = e instanceof ApiError ? e.message : t('common.loadFailed')
		compareStatus.value = 'error'
	}
}

// 参数速览表：以各对象 facts 的 zhLabel 做行联合，按各自 sortOrder 排序
const tableRows = computed(() => {
	const rows = new Map()
	for (const o of objects.value) {
		if (!o.ok) continue
		for (const fact of o.data.facts || []) {
			if (!rows.has(fact.zhLabel)) {
				rows.set(fact.zhLabel, { zhLabel: fact.zhLabel, enLabel: fact.enLabel, sortOrder: fact.sortOrder, bySlug: {} })
			}
			rows.get(fact.zhLabel).bySlug[o.slug] = fact
		}
	}
	return [...rows.values()].sort((a, b) => a.sortOrder - b.sortOrder)
})

function load() {
	const raw = route.query.slugs
	const list = typeof raw === 'string' ? raw.split(',').map((s) => s.trim()).filter(Boolean) : []
	slugs.value = [...new Set(list)].slice(0, COMPARE_MAX)
	if (slugs.value.length < 2) return
	loadObjects()
	loadCompare()
}

watch(() => route.query.slugs, load, { immediate: true })
</script>

<template>
	<main>
		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('compare.title') }}</p>
				<h3>{{ $t('compare.title') }}</h3>
			</div>

			<div v-if="slugs.length < 2" class="detail-missing">
				<p>{{ $t('compare.empty') }}</p>
				<RouterLink class="secondary-button" to="/">{{ $t('compare.backToPick') }}</RouterLink>
			</div>

			<template v-else>
				<!-- 参数速览表 -->
				<div class="section-heading compact object-heading">
					<h4>{{ $t('compare.tableHeading') }}</h4>
				</div>
				<div class="compare-table-wrap">
					<table class="compare-table">
						<thead>
							<tr>
								<th></th>
								<th v-for="o in objects" :key="o.slug">
									{{ o.ok ? pick(o.data.zhName, o.data.enName) : o.slug }}
								</th>
							</tr>
						</thead>
						<tbody>
							<tr v-for="row in tableRows" :key="row.zhLabel">
								<th>{{ pick(row.zhLabel, row.enLabel) }}</th>
								<td v-for="o in objects" :key="o.slug">
									<template v-if="o.ok && row.bySlug[o.slug]">
										{{ pick(row.bySlug[o.slug].zhValue, row.bySlug[o.slug].enValue) }}
									</template>
									<template v-else>{{ $t('compare.noFacts') }}</template>
								</td>
							</tr>
						</tbody>
					</table>
				</div>

				<!-- 综合对比 -->
				<div class="section-heading compact object-heading">
					<h4>{{ $t('compare.overviewHeading') }}</h4>
				</div>
				<p v-if="compareStatus === 'loading'" class="loading-hint">{{ $t('compare.loadingOverview') }}</p>
				<div v-else-if="compareStatus === 'error'" class="load-error">
					<p>{{ compareErrorMessage }}</p>
					<button class="secondary-button" type="button" @click="loadCompare">{{ $t('common.retry') }}</button>
				</div>
				<template v-else-if="compareStatus === 'ready'">
					<article v-if="overview" class="info-card explain-card">
						<MarkdownView :content="overview" />
					</article>
					<div v-else class="load-error">
						<p>{{ $t('compare.overviewFailed') }}</p>
						<button class="secondary-button" type="button" @click="loadCompare">{{ $t('common.retry') }}</button>
					</div>
				</template>

				<!-- 逐篇讲解 -->
				<template v-if="compareStatus === 'ready'">
					<div class="section-heading compact object-heading">
						<h4>{{ $t('compare.explainHeading') }}</h4>
					</div>
					<article v-for="item in compareItems" :key="item.slug" class="info-card explain-card">
						<h4>{{ item.error ? item.slug : pick(item.zhName, item.enName) }}</h4>
						<p v-if="item.error" class="load-error-text">{{ item.error }}</p>
						<template v-else>
							<MarkdownView :content="item.answer" />
							<div v-if="item.sources && item.sources.length" class="explain-sources">
								<span class="sources-label">{{ $t('common.sources') }}</span>
								<RouterLink
									v-for="(s, si) in item.sources"
									:key="si"
									:to="`/object/${s.slug}`"
									class="source-chip"
									:title="s.excerpt"
								>
									{{ s.title }}<span class="source-type">{{ s.type }}</span>
								</RouterLink>
							</div>
						</template>
					</article>
				</template>
			</template>
		</section>
	</main>
</template>

<style scoped>
.compare-table-wrap {
	overflow-x: auto;
	margin-bottom: 1.5rem;
}

.compare-table {
	width: 100%;
	border-collapse: collapse;
	font-size: 0.88rem;
}

.compare-table th,
.compare-table td {
	padding: 0.5rem 0.8rem;
	border-bottom: 1px solid var(--button-border);
	text-align: left;
	white-space: nowrap;
}

.compare-table thead th {
	color: var(--accent);
}

.compare-table tbody th {
	color: var(--muted);
	font-weight: 400;
}

.load-error-text {
	color: var(--muted);
}
</style>
