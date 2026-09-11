<script setup>
// 调用瀑布图：左列是缩进的 span 树，右列是同一时间轴上的耗时条。
// 后端已按 dotted_order 排好序（即深度优先），这里直接逐行渲染，depth 决定缩进。
// 颜色表示 run_type（分类身份色），失败另配 ✕ 图标 + 文字，不单靠颜色。
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDuration, statusLabel } from '../../utils/traceFormat'

const props = defineProps({
	spans: { type: Array, required: true },
	selectedId: { type: String, default: null }
})

const emit = defineEmits(['select'])
const { t, te } = useI18n()

function ms(value) {
	return value == null ? null : new Date(value).getTime()
}

const range = computed(() => {
	let start = Infinity
	let end = -Infinity
	for (const s of props.spans) {
		const st = ms(s.startTime)
		const en = ms(s.endTime) ?? (s.durationMs != null ? st + s.durationMs : st)
		start = Math.min(start, st)
		end = Math.max(end, en)
	}
	if (!Number.isFinite(start)) return { start: 0, total: 1 }
	return { start, total: Math.max(end - start, 1) }
})

const rows = computed(() =>
	props.spans.map((s) => {
		const offset = ms(s.startTime) - range.value.start
		const duration = s.durationMs ?? 0
		const left = (offset / range.value.total) * 100
		const width = (duration / range.value.total) * 100
		// 耗时标签默认放在条右侧；条靠近右边缘时放左侧；左侧也放不下（条几乎占满整行）时，
		// 以带底色的小标签叠在条的右端内侧，避免溢出轨道压到左侧名称栏
		const nearEnd = left + width > 78
		const placement = !nearEnd ? 'right' : left > 14 ? 'left' : 'inside'
		return { span: s, offset, left, width, placement }
	})
)

const ticks = computed(() => [0, 0.25, 0.5, 0.75, 1].map((f) => ({ pos: f * 100, label: formatDuration(f * range.value.total) })))

function barStyle(row) {
	return { left: `${row.left}%`, width: `max(${row.width}%, 3px)` }
}

function labelStyle(row) {
	if (row.placement === 'left') return { right: `${100 - row.left}%` }
	if (row.placement === 'inside') return { right: `calc(${Math.max(100 - row.left - row.width, 0)}% + 4px)` }
	return { left: `calc(${row.left + row.width}% + 6px)` }
}

function rowTitle(row) {
	const s = row.span
	return `${s.name} · ${s.runType} · ${formatDuration(s.durationMs)} · +${formatDuration(row.offset)}`
}
</script>

<template>
	<div class="waterfall trace-viz" role="list">
		<div class="wf-axis" aria-hidden="true">
			<span class="wf-axis-spacer" />
			<div class="wf-axis-track">
				<span
					v-for="tick in ticks"
					:key="tick.pos"
					class="wf-tick trace-num"
					:class="{ 'is-end': tick.pos === 100, 'is-start': tick.pos === 0 }"
					:style="{ left: `${tick.pos}%` }"
				>
					{{ tick.label }}
				</span>
			</div>
		</div>

		<button
			v-for="row in rows"
			:key="row.span.spanId"
			class="wf-row"
			:class="{ selected: row.span.spanId === selectedId }"
			type="button"
			role="listitem"
			:title="rowTitle(row)"
			@click="emit('select', row.span.spanId)"
		>
			<span class="wf-label" :style="{ paddingLeft: `${row.span.depth * 16}px` }">
				<span v-if="row.span.depth > 0" class="wf-branch" aria-hidden="true">└</span>
				<span class="wf-name">{{ row.span.name }}</span>
				<span class="wf-type">
					<span class="rt-swatch" :class="`rt-${row.span.runType}`" aria-hidden="true" />
					{{ row.span.runType }}
				</span>
				<span
					v-if="row.span.status !== 'success'"
					class="trace-status"
					:class="`is-${row.span.status}`"
				>
					{{ statusLabel(t, te, row.span.status) }}
				</span>
			</span>
			<span class="wf-track">
				<span class="wf-bar" :class="`rt-${row.span.runType}`" :style="barStyle(row)" />
				<span class="wf-duration trace-num" :class="`is-${row.placement}`" :style="labelStyle(row)">
					{{ formatDuration(row.span.durationMs) }}
				</span>
			</span>
		</button>
	</div>
</template>

<style scoped>
.waterfall {
	--label-col: minmax(12rem, 38%);
	display: flex;
	flex-direction: column;
	font-size: 0.84rem;
}

.wf-axis,
.wf-row {
	display: grid;
	grid-template-columns: var(--label-col) 1fr;
	gap: 0.8rem;
}

.wf-axis {
	padding: 0 0.6rem 0.4rem;
	border-bottom: 1px solid var(--viz-axis);
}

.wf-axis-track {
	position: relative;
	height: 1.1rem;
}

.wf-tick {
	position: absolute;
	top: 0;
	transform: translateX(-50%);
	font-size: 0.72rem;
	color: var(--muted);
	white-space: nowrap;
}

.wf-tick.is-start {
	transform: none;
}

.wf-tick.is-end {
	transform: translateX(-100%);
}

.wf-row {
	align-items: center;
	width: 100%;
	min-height: 2.1rem;
	padding: 0.25rem 0.6rem;
	border: none;
	border-bottom: 1px solid var(--viz-grid);
	background: none;
	color: var(--text);
	font: inherit;
	text-align: left;
	cursor: pointer;
}

.wf-row:hover {
	background: var(--panel-glow);
}

.wf-row.selected {
	background: var(--button-bg);
	box-shadow: inset 3px 0 0 var(--accent);
}

.wf-label {
	display: flex;
	align-items: center;
	gap: 0.45rem;
	min-width: 0;
}

.wf-branch {
	color: var(--muted);
	opacity: 0.6;
}

.wf-name {
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.wf-type {
	display: inline-flex;
	align-items: center;
	gap: 0.3rem;
	flex-shrink: 0;
	font-size: 0.72rem;
	color: var(--muted);
}

.wf-track {
	position: relative;
	height: 1.4rem;
	background: var(--viz-track);
	border-radius: 4px;
}

.wf-bar {
	position: absolute;
	top: 50%;
	height: 10px;
	transform: translateY(-50%);
	border-radius: 4px;
	background: var(--rt-chain);
}

.wf-bar.rt-llm { background: var(--rt-llm); }
.wf-bar.rt-retriever { background: var(--rt-retriever); }
.wf-bar.rt-tool { background: var(--rt-tool); }
.wf-bar.rt-embedding { background: var(--rt-embedding); }
.wf-bar.rt-parser { background: var(--rt-parser); }

.wf-duration {
	position: absolute;
	top: 50%;
	transform: translateY(-50%);
	padding: 0 6px;
	font-size: 0.72rem;
	color: var(--muted);
	white-space: nowrap;
}

/* 叠在条上的标签：用图表底色做衬底，文字保持正文墨色而不是系列色 */
.wf-duration.is-inside {
	padding: 1px 6px;
	border-radius: 4px;
	background: var(--viz-surface);
	color: var(--text);
}

@media (max-width: 640px) {
	.waterfall {
		--label-col: minmax(8rem, 45%);
	}

	.wf-type {
		display: none;
	}
}
</style>
