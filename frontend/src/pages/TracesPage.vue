<script setup>
// AI 调用链路列表：管理员看全部（多一个「统计概览」页签），普通用户只看自己的。
// 筛选条件同步到 URL query，从详情页返回时筛选与页签都还在。
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import SelectDropdown from '../components/SelectDropdown.vue'
import '../components/trace/trace-viz.css'
import { api, ApiError, ApiUnavailableError } from '../api'
import { useAuth } from '../composables/useAuth'
import {
	TRACE_OPERATIONS,
	TRACE_STATUSES,
	formatDateTime,
	formatDuration,
	formatNumber,
	opLabel,
	statusLabel
} from '../utils/traceFormat'

// 统计面板只有管理员能看到，ECharts 随它按需加载，普通用户不下载
const TraceStatsPanel = defineAsyncComponent(() => import('../components/trace/TraceStatsPanel.vue'))

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const { isAdmin } = useAuth()

const PAGE_SIZE = 20
const RANGE_MS = { h1: 3600 * 1000, h24: 24 * 3600 * 1000, d7: 7 * 24 * 3600 * 1000 }

const activeTab = ref(route.query.tab === 'stats' ? 'stats' : 'list')

const filters = reactive({
	name: typeof route.query.name === 'string' ? route.query.name : '',
	status: typeof route.query.status === 'string' ? route.query.status : '',
	range: typeof route.query.range === 'string' && RANGE_MS[route.query.range] ? route.query.range : 'd7',
	keyword: typeof route.query.keyword === 'string' ? route.query.keyword : ''
})

const nameOptions = computed(() => [
	{ value: '', label: t('traces.filters.allNames') },
	...TRACE_OPERATIONS.map((name) => ({ value: name, label: opLabel(t, te, name) }))
])
const statusOptions = computed(() => [
	{ value: '', label: t('traces.filters.allStatus') },
	...TRACE_STATUSES.map((s) => ({ value: s, label: statusLabel(t, te, s) }))
])
const rangeOptions = computed(() =>
	Object.keys(RANGE_MS).map((key) => ({ value: key, label: t(`traces.ranges.${key}`) }))
)

const items = ref([])
const totalElements = ref(0)
const page = ref(0)
const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const truncated = ref(false)

const hasMore = computed(() => items.value.length < totalElements.value)

async function load(reset = false) {
	if (reset) {
		page.value = 0
		items.value = []
	}
	loading.value = true
	error.value = ''
	try {
		const to = Date.now()
		const res = await api.listTraces({
			page: page.value,
			size: PAGE_SIZE,
			name: filters.name,
			status: filters.status,
			from: to - RANGE_MS[filters.range],
			keyword: filters.keyword.trim()
		})
		items.value = reset ? res.items : [...items.value, ...res.items]
		totalElements.value = res.totalElements
		truncated.value = res.keywordTruncated
		loaded.value = true
	} catch (err) {
		// 503（关键词检索时 ES 不可用）等后端错误直接展示后端给的提示
		if (err instanceof ApiUnavailableError) error.value = t('traces.offline')
		else if (err instanceof ApiError) error.value = err.message || t('traces.loadFailed')
		else error.value = t('traces.loadFailed')
	} finally {
		loading.value = false
	}
}

function syncQuery() {
	const query = { tab: activeTab.value === 'stats' ? 'stats' : undefined }
	if (filters.name) query.name = filters.name
	if (filters.status) query.status = filters.status
	if (filters.range !== 'd7') query.range = filters.range
	if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
	router.replace({ query })
}

function search() {
	syncQuery()
	load(true)
}

function resetFilters() {
	filters.name = ''
	filters.status = ''
	filters.range = 'd7'
	filters.keyword = ''
	search()
}

function onFilterChange(key, value) {
	filters[key] = value
	search()
}

function switchTab(tab) {
	activeTab.value = tab
	syncQuery()
}

function loadMore() {
	page.value += 1
	load()
}

onMounted(() => load(true))
</script>

<template>
	<main>
		<section class="section-block traces-section">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('traces.kicker') }}</p>
				<h3>{{ $t('traces.title') }}</h3>
				<p>{{ $t('traces.intro') }}</p>
				<p v-if="isAdmin" class="admin-note">{{ $t('traces.introAdmin') }}</p>
			</div>

			<div v-if="isAdmin" class="traces-tabs">
				<button class="traces-tab" :class="{ active: activeTab === 'list' }" type="button" @click="switchTab('list')">
					{{ $t('traces.tabs.list') }}
				</button>
				<button class="traces-tab" :class="{ active: activeTab === 'stats' }" type="button" @click="switchTab('stats')">
					{{ $t('traces.tabs.stats') }}
				</button>
			</div>

			<TraceStatsPanel v-if="isAdmin && activeTab === 'stats'" />

			<template v-else>
				<form class="traces-filters" @submit.prevent="search">
					<SelectDropdown
						:model-value="filters.name"
						:options="nameOptions"
						:aria-label="$t('traces.filters.name')"
						@update:model-value="onFilterChange('name', $event)"
					/>
					<SelectDropdown
						:model-value="filters.status"
						:options="statusOptions"
						:aria-label="$t('traces.filters.status')"
						@update:model-value="onFilterChange('status', $event)"
					/>
					<SelectDropdown
						:model-value="filters.range"
						:options="rangeOptions"
						:aria-label="$t('traces.filters.range')"
						@update:model-value="onFilterChange('range', $event)"
					/>
					<input
						v-model="filters.keyword"
						class="traces-keyword"
						:placeholder="$t('traces.filters.keywordPlaceholder')"
						:disabled="loading"
					/>
					<button class="primary-button" type="submit" :disabled="loading">{{ $t('traces.filters.search') }}</button>
					<button class="secondary-button" type="button" :disabled="loading" @click="resetFilters">
						{{ $t('traces.filters.reset') }}
					</button>
				</form>

				<p v-if="error" class="traces-error">{{ error }}</p>
				<p v-if="truncated && !error" class="traces-note">{{ $t('traces.keywordTruncated') }}</p>
				<p v-if="loaded && items.length === 0 && !error" class="traces-empty">{{ $t('traces.empty') }}</p>

				<div v-if="items.length" class="traces-list trace-viz" :class="{ 'with-user': isAdmin }">
					<div class="traces-row traces-head" aria-hidden="true">
						<span>{{ $t('traces.columns.operation') }}</span>
						<span>{{ $t('traces.columns.status') }}</span>
						<span>{{ $t('traces.columns.time') }}</span>
						<span class="num">{{ $t('traces.columns.duration') }}</span>
						<span class="num">{{ $t('traces.columns.tokens') }}</span>
						<span class="num">{{ $t('traces.columns.spans') }}</span>
						<span v-if="isAdmin">{{ $t('traces.columns.user') }}</span>
					</div>
					<RouterLink v-for="item in items" :key="item.traceId" class="traces-row" :to="`/traces/${item.traceId}`">
						<span class="op" :title="item.name">{{ opLabel(t, te, item.name) }}</span>
						<span>
							<span class="trace-status" :class="`is-${item.status}`" :title="item.errorMessage || ''">
								{{ statusLabel(t, te, item.status) }}
							</span>
						</span>
						<span class="muted trace-num">{{ formatDateTime(item.startTime) }}</span>
						<span class="num trace-num">{{ formatDuration(item.durationMs) }}</span>
						<span class="num trace-num">{{ formatNumber(item.totalTokens) }}</span>
						<span class="num trace-num">{{ item.spanCount }}</span>
						<span v-if="isAdmin" class="muted user" :title="item.userEmail || ''">
							{{ item.userEmail || $t('traces.anonymous') }}
						</span>
					</RouterLink>
				</div>

				<div v-if="hasMore" class="traces-more">
					<button class="secondary-button" type="button" :disabled="loading" @click="loadMore">
						{{ loading ? $t('common.loading') : $t('traces.loadMore') }}
					</button>
				</div>
			</template>
		</section>
	</main>
</template>

<style scoped>
.admin-note {
	margin-top: 0.4rem !important;
	font-size: 0.85rem;
	color: var(--accent) !important;
}

.traces-tabs {
	display: flex;
	gap: 0.4rem;
	max-width: 24rem;
	margin-bottom: 1.2rem;
}

.traces-tab {
	flex: 1;
	padding: 0.5rem;
	border: 1px solid var(--button-border);
	border-radius: 10px;
	background: var(--button-bg);
	color: var(--text);
	font: inherit;
	cursor: pointer;
	transition: background 0.2s ease;
}

.traces-tab.active {
	background: var(--accent);
	border-color: var(--accent);
	color: var(--accent-contrast);
}

.traces-filters {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: 0.6rem;
	margin-bottom: 1.2rem;
}

.traces-keyword {
	flex: 1;
	min-width: 12rem;
	min-height: 46px;
	padding: 0 0.9rem;
	border-radius: 999px;
	border: 1px solid var(--button-border);
	background: var(--button-bg);
	color: var(--text);
	font: inherit;
	outline: none;
}

.traces-keyword:focus {
	border-color: var(--accent);
}

.traces-error {
	color: var(--danger, #e57373);
	font-size: 0.88rem;
}

.traces-note,
.traces-empty {
	color: var(--muted);
	font-size: 0.88rem;
}

.traces-list {
	border: 1px solid var(--card-border);
	border-radius: 14px;
	overflow-x: auto;
	background: var(--card);
}

.traces-row {
	display: grid;
	grid-template-columns: minmax(10rem, 1.6fr) 6.5rem 9rem 5.5rem 5.5rem 3.5rem;
	align-items: center;
	gap: 0.8rem;
	min-width: 44rem;
	padding: 0.7rem 1rem;
	border-bottom: 1px solid var(--viz-grid);
	color: var(--text);
	font-size: 0.88rem;
}

.with-user .traces-row {
	grid-template-columns: minmax(10rem, 1.6fr) 6.5rem 9rem 5.5rem 5.5rem 3.5rem minmax(8rem, 1fr);
	min-width: 54rem;
}

.traces-row:last-child {
	border-bottom: none;
}

a.traces-row:hover {
	background: var(--panel-glow);
}

.traces-head {
	font-size: 0.74rem;
	color: var(--muted);
	letter-spacing: 0.04em;
}

.op,
.user {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.num {
	text-align: right;
}

.muted {
	color: var(--muted);
	font-size: 0.82rem;
}

.traces-more {
	display: flex;
	justify-content: center;
	margin-top: 1rem;
}
</style>
