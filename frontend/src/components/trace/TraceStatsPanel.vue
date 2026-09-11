<script setup>
// 管理员统计概览。形式选择：
// - 单个指标用数字卡片（不是图）；
// - 调用量随时间：成功/失败堆叠柱（失败用状态色 critical，图例带文字）；
// - 耗时：P50/P95 两条折线，同一 y 轴（ms），末端直接标注系列名；
// - token 量级不同，单独一张柱状图，绝不和耗时共用双 y 轴；
// - 每张图都有表格视图可切换，按操作/按模型拆分直接给表格。
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectDropdown from '../SelectDropdown.vue'
import TraceChart from './TraceChart.vue'
import { api, ApiError, ApiUnavailableError } from '../../api'
import { formatBucket, formatDuration, formatNumber, formatPercent, opLabel } from '../../utils/traceFormat'

const { t, te } = useI18n()

const RANGE_MS = { h24: 24 * 3600 * 1000, d7: 7 * 24 * 3600 * 1000 }
const range = ref('h24')
const rangeOptions = computed(() => [
	{ value: 'h24', label: t('traces.ranges.h24') },
	{ value: 'd7', label: t('traces.ranges.d7') }
])

const stats = ref(null)
const loading = ref(false)
const error = ref('')
const tableView = ref(false)

async function load() {
	loading.value = true
	error.value = ''
	try {
		const to = Date.now()
		stats.value = await api.getTraceStats({ from: to - RANGE_MS[range.value], to })
	} catch (err) {
		if (err instanceof ApiError && err.status === 503) error.value = t('traces.stats.unavailable')
		else if (err instanceof ApiUnavailableError) error.value = t('traces.offline')
		else error.value = err.message || t('traces.loadFailed')
	} finally {
		loading.value = false
	}
}

watch(range, load)
onMounted(load)

const summary = computed(() => stats.value?.summary)
const hasData = computed(() => (summary.value?.traces ?? 0) > 0)

const kpis = computed(() => {
	const s = summary.value
	if (!s) return []
	return [
		{ key: 'traces', value: formatNumber(s.traces) },
		{ key: 'errorRate', value: formatPercent(s.traces ? s.errors / s.traces : null) },
		{ key: 'p50', value: formatDuration(s.p50Ms) },
		{ key: 'p95', value: formatDuration(s.p95Ms) },
		{ key: 'ttftP50', value: formatDuration(s.ttftP50Ms) },
		{ key: 'ttftP95', value: formatDuration(s.ttftP95Ms) },
		{ key: 'tokens', value: formatNumber(s.totalTokens) }
	]
})

/** 后端只返回有数据的桶；按固定步长补齐空桶，时间轴才连续。ES 的 fixed_interval 以 UTC 纪元对齐。 */
const buckets = computed(() => {
	const s = stats.value
	if (!s) return []
	const step = s.interval === '1d' ? 24 * 3600 * 1000 : 3600 * 1000
	const byTime = new Map(s.timeline.map((b) => [b.time, b]))
	const out = []
	for (let time = Math.floor(s.from / step) * step; time < s.to; time += step) {
		out.push(byTime.get(time) ?? { time, traces: 0, errors: 0, p50Ms: null, p95Ms: null, tokens: 0 })
	}
	return out
})

const categories = computed(() => buckets.value.map((b) => formatBucket(b.time, stats.value.interval)))

function baseOption(tk) {
	return {
		animationDuration: 300,
		textStyle: { color: tk.muted },
		grid: { left: 8, right: 56, top: 36, bottom: 4, containLabel: true },
		tooltip: {
			trigger: 'axis',
			backgroundColor: tk.surface,
			borderColor: tk.axis,
			textStyle: { color: tk.text, fontSize: 12 }
		},
		legend: {
			top: 0,
			left: 0,
			icon: 'roundRect',
			itemWidth: 10,
			itemHeight: 10,
			textStyle: { color: tk.text, fontSize: 12 }
		},
		xAxis: {
			type: 'category',
			data: categories.value,
			axisLine: { lineStyle: { color: tk.axis } },
			axisTick: { show: false },
			axisLabel: { color: tk.muted, fontSize: 11, hideOverlap: true }
		},
		yAxis: {
			type: 'value',
			splitLine: { lineStyle: { color: tk.grid } },
			axisLabel: { color: tk.muted, fontSize: 11 }
		}
	}
}

function buildVolume(tk) {
	const base = baseOption(tk)
	const bar = { type: 'bar', stack: 'volume', barMaxWidth: 18, itemStyle: { borderColor: tk.surface, borderWidth: 1 } }
	return {
		...base,
		tooltip: { ...base.tooltip, axisPointer: { type: 'shadow' } },
		series: [
			{
				...bar,
				name: t('traces.stats.series.success'),
				color: tk.series1,
				// 圆角只给堆叠的最顶端：该桶没有失败时，成功段就是顶端
				data: buckets.value.map((b) => ({
					value: b.traces - b.errors,
					itemStyle: { borderRadius: b.errors ? 0 : [4, 4, 0, 0] }
				}))
			},
			{
				...bar,
				name: t('traces.stats.series.error'),
				color: tk.critical,
				itemStyle: { ...bar.itemStyle, borderRadius: [4, 4, 0, 0] },
				data: buckets.value.map((b) => b.errors)
			}
		]
	}
}

function buildLatency(tk) {
	const base = baseOption(tk)
	const showSymbol = buckets.value.length <= 24
	const line = (name, color, key) => ({
		type: 'line',
		name,
		color,
		data: buckets.value.map((b) => (b[key] == null ? null : Math.round(b[key]))),
		connectNulls: false,
		showSymbol,
		symbol: 'circle',
		symbolSize: 8,
		lineStyle: { width: 2 },
		endLabel: { show: true, formatter: '{a}', color: tk.text, fontSize: 11 }
	})
	return {
		...base,
		tooltip: { ...base.tooltip, axisPointer: { type: 'line', lineStyle: { color: tk.axis } }, valueFormatter: formatDuration },
		yAxis: { ...base.yAxis, axisLabel: { ...base.yAxis.axisLabel, formatter: (v) => formatDuration(v) } },
		series: [
			line(t('traces.stats.series.p50'), tk.series1, 'p50Ms'),
			line(t('traces.stats.series.p95'), tk.series2, 'p95Ms')
		]
	}
}

function compact(n) {
	if (n >= 1e6) return `${(n / 1e6).toFixed(1)}M`
	if (n >= 1e3) return `${(n / 1e3).toFixed(1)}k`
	return String(n)
}

function buildTokens(tk) {
	const base = baseOption(tk)
	return {
		...base,
		// 单系列：卡片标题已说明含义，不需要图例
		legend: { show: false },
		tooltip: { ...base.tooltip, axisPointer: { type: 'shadow' }, valueFormatter: formatNumber },
		yAxis: { ...base.yAxis, axisLabel: { ...base.yAxis.axisLabel, formatter: compact } },
		series: [
			{
				type: 'bar',
				name: t('traces.stats.series.tokens'),
				color: tk.series1,
				barMaxWidth: 18,
				itemStyle: { borderRadius: [4, 4, 0, 0] },
				data: buckets.value.map((b) => b.tokens)
			}
		]
	}
}

function errorRate(errors, total) {
	return formatPercent(total ? errors / total : null)
}
</script>

<template>
	<div class="stats-panel trace-viz" :class="{ refreshing: loading && stats }">
		<div class="stats-filters">
			<SelectDropdown v-model="range" :options="rangeOptions" :aria-label="$t('traces.filters.range')" />
			<button v-if="hasData" class="view-toggle" type="button" @click="tableView = !tableView">
				{{ tableView ? $t('traces.stats.chartView') : $t('traces.stats.tableView') }}
			</button>
		</div>

		<p v-if="error" class="stats-error">{{ error }}</p>
		<p v-else-if="loading && !stats" class="stats-hint">{{ $t('common.loading') }}</p>
		<p v-else-if="stats && !hasData" class="stats-hint">{{ $t('traces.stats.empty') }}</p>

		<template v-if="stats && hasData && !error">
			<div class="kpi-grid">
				<div v-for="kpi in kpis" :key="kpi.key" class="kpi-tile">
					<span class="kpi-label">{{ $t(`traces.stats.kpi.${kpi.key}`) }}</span>
					<strong class="kpi-value">{{ kpi.value }}</strong>
				</div>
			</div>

			<template v-if="!tableView">
				<div class="chart-card">
					<h4>{{ $t('traces.stats.charts.volume') }}</h4>
					<TraceChart :build="buildVolume" :data="buckets" :aria-label="$t('traces.stats.charts.volume')" />
				</div>
				<div class="chart-card">
					<h4>{{ $t('traces.stats.charts.latency') }}</h4>
					<TraceChart :build="buildLatency" :data="buckets" :aria-label="$t('traces.stats.charts.latency')" />
				</div>
				<div class="chart-card">
					<h4>{{ $t('traces.stats.charts.tokens') }}</h4>
					<TraceChart :build="buildTokens" :data="buckets" :aria-label="$t('traces.stats.charts.tokens')" />
				</div>
			</template>

			<div v-else class="chart-card table-wrap">
				<table class="trace-table">
					<thead>
						<tr>
							<th>{{ $t('traces.stats.columns.time') }}</th>
							<th>{{ $t('traces.stats.columns.calls') }}</th>
							<th>{{ $t('traces.stats.columns.errors') }}</th>
							<th>{{ $t('traces.stats.series.p50') }}</th>
							<th>{{ $t('traces.stats.series.p95') }}</th>
							<th>{{ $t('traces.stats.series.tokens') }}</th>
						</tr>
					</thead>
					<tbody>
						<tr v-for="b in buckets.filter((x) => x.traces > 0 || x.tokens > 0)" :key="b.time">
							<td>{{ formatBucket(b.time, stats.interval) }}</td>
							<td class="trace-num">{{ formatNumber(b.traces) }}</td>
							<td class="trace-num">{{ formatNumber(b.errors) }}</td>
							<td class="trace-num">{{ formatDuration(b.p50Ms) }}</td>
							<td class="trace-num">{{ formatDuration(b.p95Ms) }}</td>
							<td class="trace-num">{{ formatNumber(b.tokens) }}</td>
						</tr>
					</tbody>
				</table>
			</div>

			<div class="chart-card table-wrap">
				<h4>{{ $t('traces.stats.byName') }}</h4>
				<table class="trace-table">
					<thead>
						<tr>
							<th>{{ $t('traces.columns.operation') }}</th>
							<th>{{ $t('traces.stats.columns.calls') }}</th>
							<th>{{ $t('traces.stats.columns.errorRate') }}</th>
							<th>{{ $t('traces.stats.series.p50') }}</th>
							<th>{{ $t('traces.stats.series.p95') }}</th>
						</tr>
					</thead>
					<tbody>
						<tr v-for="row in stats.byName" :key="row.name">
							<td>{{ opLabel(t, te, row.name) }}</td>
							<td class="trace-num">{{ formatNumber(row.traces) }}</td>
							<td class="trace-num">{{ errorRate(row.errors, row.traces) }}</td>
							<td class="trace-num">{{ formatDuration(row.p50Ms) }}</td>
							<td class="trace-num">{{ formatDuration(row.p95Ms) }}</td>
						</tr>
					</tbody>
				</table>
			</div>

			<div v-if="stats.byModel.length" class="chart-card table-wrap">
				<h4>{{ $t('traces.stats.byModel') }}</h4>
				<table class="trace-table">
					<thead>
						<tr>
							<th>{{ $t('traces.stats.columns.model') }}</th>
							<th>{{ $t('traces.stats.columns.calls') }}</th>
							<th>{{ $t('traces.stats.columns.prompt') }}</th>
							<th>{{ $t('traces.stats.columns.completion') }}</th>
							<th>{{ $t('traces.stats.columns.total') }}</th>
						</tr>
					</thead>
					<tbody>
						<tr v-for="row in stats.byModel" :key="row.model">
							<td>{{ row.model }}</td>
							<td class="trace-num">{{ formatNumber(row.calls) }}</td>
							<td class="trace-num">{{ formatNumber(row.promptTokens) }}</td>
							<td class="trace-num">{{ formatNumber(row.completionTokens) }}</td>
							<td class="trace-num">{{ formatNumber(row.totalTokens) }}</td>
						</tr>
					</tbody>
				</table>
			</div>
		</template>
	</div>
</template>

<style scoped>
.stats-panel {
	display: flex;
	flex-direction: column;
	gap: 1rem;
	transition: opacity 160ms ease;
}

/* 切换时间范围时保留旧图、降低不透明度，不闪骨架屏 */
.stats-panel.refreshing {
	opacity: 0.55;
}

.stats-filters {
	display: flex;
	align-items: center;
	gap: 0.6rem;
}

.view-toggle {
	margin-left: auto;
	padding: 0.35rem 0.8rem;
	border: 1px solid var(--button-border);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--accent);
	font: inherit;
	font-size: 0.8rem;
	cursor: pointer;
}

.view-toggle:hover {
	background: var(--panel-glow);
}

.stats-error {
	color: var(--viz-critical-text);
	font-size: 0.88rem;
}

.stats-hint {
	color: var(--muted);
	font-size: 0.9rem;
}

.kpi-grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr));
	gap: 0.7rem;
}

.kpi-tile {
	display: flex;
	flex-direction: column;
	gap: 0.35rem;
	padding: 0.85rem 1rem;
	border-radius: 14px;
	border: 1px solid var(--card-border);
	background: var(--button-bg);
}

.kpi-label {
	font-size: 0.74rem;
	color: var(--muted);
	letter-spacing: 0.04em;
}

.kpi-value {
	font-size: 1.35rem;
	font-weight: 600;
	color: var(--text);
}

.chart-card {
	padding: 1rem 1.1rem 0.8rem;
	border-radius: 16px;
	border: 1px solid var(--card-border);
	background: var(--card);
}

.chart-card h4 {
	margin: 0 0 0.6rem;
	font-size: 0.95rem;
	font-weight: 600;
}

.table-wrap {
	overflow-x: auto;
}

.trace-table {
	width: 100%;
	border-collapse: collapse;
	font-size: 0.84rem;
}

.trace-table th,
.trace-table td {
	padding: 0.5rem 0.6rem;
	border-bottom: 1px solid var(--viz-grid);
	text-align: right;
	white-space: nowrap;
}

.trace-table th:first-child,
.trace-table td:first-child {
	text-align: left;
}

.trace-table th {
	font-weight: 500;
	font-size: 0.76rem;
	color: var(--muted);
}
</style>
