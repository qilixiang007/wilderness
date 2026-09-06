<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, openCompareStream } from '../api'
import { COMPARE_MAX } from '../composables/useCompare'
import { dedupeSourcesBySlug } from '../utils/sources'
import { pick } from '../i18n'
import MarkdownView from '../components/MarkdownView.vue'

const route = useRoute()

const slugs = ref([])

// A 路：各天体基础参数（独立于 B 路，互不阻塞）
const objects = ref([]) // [{ slug, ok, data }]

// B 路：流式讲解 + 综合总结。compareItems 按后端完成顺序（不是请求顺序）逐个到达。
const compareItems = ref([])
const overview = ref(null)
const overviewStatus = ref('idle') // idle | waiting | ready | failed
let currentStream = null

async function loadObjects() {
	const results = await Promise.allSettled(slugs.value.map((slug) => api.getObject(slug)))
	objects.value = results.map((r, i) => ({
		slug: slugs.value[i],
		ok: r.status === 'fulfilled',
		data: r.status === 'fulfilled' ? r.value : null
	}))
}

function loadCompareStream() {
	currentStream?.close()
	compareItems.value = []
	overview.value = null
	overviewStatus.value = 'waiting'
	currentStream = openCompareStream(slugs.value, {
		onItem: (item) => compareItems.value.push(item),
		onOverview: (text) => {
			overview.value = text
			overviewStatus.value = text ? 'ready' : 'failed'
		},
		onEnd: () => {
			// 原生 EventSource 无法区分“正常结束”与“连接中断”，
			// 用是否已经收到过 overview 来判断：还在等就是异常中断。
			if (overviewStatus.value === 'waiting') {
				overviewStatus.value = 'failed'
			}
		}
	})
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

const pendingCount = computed(() => Math.max(0, slugs.value.length - compareItems.value.length))

function load() {
	const raw = route.query.slugs
	const list = typeof raw === 'string' ? raw.split(',').map((s) => s.trim()).filter(Boolean) : []
	slugs.value = [...new Set(list)].slice(0, COMPARE_MAX)
	if (slugs.value.length < 2) return
	loadObjects()
	loadCompareStream()
}

watch(() => route.query.slugs, load, { immediate: true })
onBeforeUnmount(() => currentStream?.close())
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

				<!-- 逐篇讲解：随到随显示，不必等全部完成 -->
				<div class="section-heading compact object-heading">
					<h4>{{ $t('compare.explainHeading') }}</h4>
					<span v-if="pendingCount > 0" class="pending-hint">{{ $t('compare.itemsPending', { n: pendingCount }) }}</span>
				</div>
				<article v-for="item in compareItems" :key="item.slug" class="info-card explain-card">
					<h4>{{ item.error ? item.slug : pick(item.zhName, item.enName) }}</h4>
					<p v-if="item.error" class="load-error-text">{{ item.error }}</p>
					<template v-else>
						<MarkdownView :content="item.answer" />
						<div v-if="item.sources && item.sources.length" class="explain-sources">
							<span class="sources-label">{{ $t('common.sources') }}</span>
							<RouterLink
								v-for="(s, si) in dedupeSourcesBySlug(item.sources)"
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

				<!-- 综合对比：等所有讲解都完成后才会开始生成 -->
				<div class="section-heading compact object-heading">
					<h4>{{ $t('compare.overviewHeading') }}</h4>
				</div>
				<p v-if="overviewStatus === 'waiting'" class="loading-hint">
					{{ pendingCount > 0
						? $t('compare.loadingItems', { done: compareItems.length, total: slugs.length })
						: $t('compare.loadingSummaryOnly') }}
				</p>
				<div v-else-if="overviewStatus === 'failed'" class="load-error">
					<p>{{ $t('compare.overviewFailed') }}</p>
					<button class="secondary-button" type="button" @click="loadCompareStream">{{ $t('common.retry') }}</button>
				</div>
				<article v-else-if="overviewStatus === 'ready'" class="info-card explain-card">
					<MarkdownView :content="overview" />
				</article>
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

.pending-hint {
	display: block;
	margin-top: 4px;
	font-size: 0.82rem;
	color: var(--muted);
}

.explain-sources {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 0.4rem;
	margin-top: 0.9rem;
}

.sources-label {
	font-size: 0.78rem;
	letter-spacing: 0.08em;
	text-transform: uppercase;
	color: var(--muted);
	margin: 0 0.25rem 0 0.15rem;
}

.source-chip {
	font-size: 0.82rem;
	color: var(--accent);
	border: 1px solid var(--button-border);
	border-radius: 999px;
	padding: 0.2rem 0.7rem;
	text-decoration: none;
	transition: background 0.2s ease;
}

.source-chip:hover {
	background: var(--panel-glow);
}

.source-type {
	margin-left: 0.35rem;
	font-size: 0.72rem;
	text-transform: uppercase;
	color: var(--muted);
}
</style>
