<script setup>
// 单条调用链路详情：摘要 → 瀑布图 → 选中 span 的输入/输出/错误。
// llm span 的输入是 [{role, content}] 消息数组，按角色分块展示；其余 span 直接格式化 JSON。
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import TraceWaterfall from '../components/trace/TraceWaterfall.vue'
import '../components/trace/trace-viz.css'
import { api, ApiError, ApiUnavailableError } from '../api'
import { useAuth } from '../composables/useAuth'
import { RUN_TYPES, formatDateTime, formatDuration, formatNumber, opLabel, statusLabel } from '../utils/traceFormat'

const props = defineProps({
	traceId: { type: String, required: true }
})

const { t, te } = useI18n()
const { isAdmin } = useAuth()

const detail = ref(null)
const loading = ref(false)
const error = ref('')
const selectedId = ref(null)
const copied = ref(false)

async function load() {
	loading.value = true
	error.value = ''
	detail.value = null
	try {
		detail.value = await api.getTrace(props.traceId)
		selectedId.value = detail.value.spans[0]?.spanId ?? null
	} catch (err) {
		if (err instanceof ApiError && err.status === 404) error.value = t('traces.notFound')
		else if (err instanceof ApiUnavailableError) error.value = t('traces.offline')
		else error.value = err.message || t('traces.loadFailed')
	} finally {
		loading.value = false
	}
}

watch(() => props.traceId, load, { immediate: true })

const summary = computed(() => detail.value?.summary)
const spans = computed(() => detail.value?.spans ?? [])
const selected = computed(() => spans.value.find((s) => s.spanId === selectedId.value) ?? null)
const traceStartMs = computed(() => (spans.value[0] ? new Date(spans.value[0].startTime).getTime() : 0))

/** 本条 trace 实际出现的 run_type，按固定槽位顺序排列作图例。 */
const presentRunTypes = computed(() => RUN_TYPES.filter((rt) => spans.value.some((s) => s.runType === rt)))

const llmMessages = computed(() => {
	const s = selected.value
	if (!s || s.runType !== 'llm' || !Array.isArray(s.inputs?.messages)) return null
	return s.inputs.messages
})

const llmOutput = computed(() => {
	const s = selected.value
	if (!s || s.runType !== 'llm' || typeof s.outputs?.content !== 'string') return null
	return s.outputs
})

function pretty(value) {
	if (value == null) return t('traces.span.none')
	if (typeof value === 'string') return value
	return JSON.stringify(value, null, 2)
}

function roleLabel(role) {
	const key = `traces.roles.${role}`
	return te(key) ? t(key) : role
}

function offsetOf(span) {
	return new Date(span.startTime).getTime() - traceStartMs.value
}

async function copyTraceId() {
	try {
		await navigator.clipboard.writeText(props.traceId)
		copied.value = true
		setTimeout(() => (copied.value = false), 1500)
	} catch {
		/* 剪贴板不可用时静默 */
	}
}
</script>

<template>
	<main class="trace-viz">
		<section class="section-block">
			<p v-if="loading" class="detail-hint">{{ $t('common.loading') }}</p>
			<div v-else-if="error" class="detail-error">
				<p>{{ error }}</p>
				<RouterLink class="secondary-button" to="/traces">{{ $t('traces.back') }}</RouterLink>
			</div>

			<template v-else-if="summary">
				<div class="section-heading compact">
					<p class="eyebrow">{{ $t('traces.detailTitle') }}</p>
					<div class="detail-title">
						<h3>{{ opLabel(t, te, summary.name) }}</h3>
						<span class="trace-status" :class="`is-${summary.status}`">{{ statusLabel(t, te, summary.status) }}</span>
					</div>
					<p class="trace-id">
						<span>{{ $t('traces.traceId') }}</span>
						<code>{{ traceId }}</code>
						<button class="copy-button" type="button" @click="copyTraceId">
							{{ copied ? $t('traces.copied') : $t('traces.copy') }}
						</button>
					</p>
				</div>

				<dl class="summary-grid">
					<div class="summary-item">
						<dt>{{ $t('traces.startedAt') }}</dt>
						<dd>{{ formatDateTime(summary.startTime) }}</dd>
					</div>
					<div class="summary-item">
						<dt>{{ $t('traces.duration') }}</dt>
						<dd>{{ formatDuration(summary.durationMs) }}</dd>
					</div>
					<div class="summary-item">
						<dt>{{ $t('traces.tokens') }}</dt>
						<dd>
							{{ formatNumber(summary.totalTokens) }}
							<small v-if="summary.totalTokens != null">
								{{ $t('traces.tokenBreakdown', { prompt: formatNumber(summary.promptTokens), completion: formatNumber(summary.completionTokens) }) }}
							</small>
						</dd>
					</div>
					<div v-if="summary.firstTokenMs != null" class="summary-item">
						<dt>{{ $t('traces.ttft') }}</dt>
						<dd>{{ formatDuration(summary.firstTokenMs) }}</dd>
					</div>
					<div class="summary-item">
						<dt>{{ $t('traces.spanCount') }}</dt>
						<dd>{{ summary.spanCount }}</dd>
					</div>
					<div v-if="isAdmin" class="summary-item">
						<dt>{{ $t('traces.user') }}</dt>
						<dd class="ellipsis">{{ summary.userEmail || $t('traces.anonymous') }}</dd>
					</div>
				</dl>
				<p v-if="summary.errorMessage" class="summary-error">{{ summary.errorMessage }}</p>
			</template>
		</section>

		<section v-if="summary" class="section-block">
			<div class="waterfall-head">
				<div>
					<h4>{{ $t('traces.waterfall') }}</h4>
					<p class="detail-hint">{{ $t('traces.waterfallHint') }}</p>
				</div>
				<ul class="rt-legend" :aria-label="$t('traces.runTypes')">
					<li v-for="rt in presentRunTypes" :key="rt">
						<span class="rt-swatch" :class="`rt-${rt}`" aria-hidden="true" />{{ rt }}
					</li>
				</ul>
			</div>
			<div class="waterfall-scroll">
				<TraceWaterfall :spans="spans" :selected-id="selectedId" @select="selectedId = $event" />
			</div>
		</section>

		<section v-if="selected" class="section-block span-detail">
			<div class="span-head">
				<h4>{{ selected.name }}</h4>
				<span class="span-type"><span class="rt-swatch" :class="`rt-${selected.runType}`" aria-hidden="true" />{{ selected.runType }}</span>
				<span class="trace-status" :class="`is-${selected.status}`">{{ statusLabel(t, te, selected.status) }}</span>
			</div>

			<dl class="span-meta">
				<div>
					<dt>{{ $t('traces.span.duration') }}</dt>
					<dd>{{ formatDuration(selected.durationMs) }}</dd>
				</div>
				<div>
					<dt>{{ $t('traces.span.offset') }}</dt>
					<dd>+{{ formatDuration(offsetOf(selected)) }}</dd>
				</div>
				<div v-if="selected.modelName">
					<dt>{{ $t('traces.span.model') }}</dt>
					<dd>{{ selected.modelName }}</dd>
				</div>
				<div v-if="selected.totalTokens != null">
					<dt>{{ $t('traces.span.tokens') }}</dt>
					<dd>
						{{ formatNumber(selected.totalTokens) }}
						<small>{{ $t('traces.tokenBreakdown', { prompt: formatNumber(selected.promptTokens), completion: formatNumber(selected.completionTokens) }) }}</small>
					</dd>
				</div>
				<div v-if="selected.firstTokenMs != null">
					<dt>{{ $t('traces.ttft') }}</dt>
					<dd>{{ formatDuration(selected.firstTokenMs) }}</dd>
				</div>
			</dl>

			<div v-if="selected.error" class="span-block">
				<span class="block-label is-error">{{ $t('traces.span.error') }}</span>
				<pre class="trace-pre error-pre">{{ selected.error }}</pre>
			</div>

			<div class="span-block">
				<span class="block-label">{{ $t('traces.span.inputs') }}</span>
				<div v-if="llmMessages" class="messages">
					<div v-for="(m, i) in llmMessages" :key="i" class="message">
						<span class="message-role">{{ roleLabel(m.role) }}</span>
						<pre class="trace-pre">{{ m.content }}</pre>
					</div>
				</div>
				<pre v-else class="trace-pre">{{ pretty(selected.inputs) }}</pre>
			</div>

			<div class="span-block">
				<span class="block-label">{{ $t('traces.span.outputs') }}</span>
				<template v-if="llmOutput">
					<pre class="trace-pre">{{ llmOutput.content }}</pre>
					<p v-if="llmOutput.finishReason" class="finish-reason">finish_reason: {{ llmOutput.finishReason }}</p>
				</template>
				<pre v-else class="trace-pre">{{ pretty(selected.outputs) }}</pre>
			</div>
		</section>
	</main>
</template>

<style scoped>
.detail-hint {
	margin: 0.2rem 0 0;
	color: var(--muted);
	font-size: 0.85rem;
}

.detail-error {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 0.8rem;
	color: var(--muted);
}

.detail-title {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 0.8rem;
}

.detail-title .trace-status {
	font-size: 0.85rem;
}

.trace-id {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 0.5rem;
	margin-top: 0.5rem !important;
	font-size: 0.8rem;
}

.trace-id code {
	font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
	color: var(--text);
}

.copy-button {
	padding: 0.15rem 0.6rem;
	border: 1px solid var(--button-border);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--accent);
	font: inherit;
	font-size: 0.74rem;
	cursor: pointer;
}

.summary-grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr));
	gap: 0.7rem;
	margin: 0;
}

.summary-item {
	padding: 0.8rem 1rem;
	border-radius: 14px;
	border: 1px solid var(--card-border);
	background: var(--button-bg);
	min-width: 0;
}

.summary-item dt,
.span-meta dt {
	font-size: 0.74rem;
	color: var(--muted);
	letter-spacing: 0.04em;
}

.summary-item dd {
	margin: 0.35rem 0 0;
	font-size: 1.15rem;
	font-weight: 600;
}

.summary-item small,
.span-meta small {
	display: block;
	margin-top: 0.15rem;
	font-size: 0.72rem;
	font-weight: 400;
	color: var(--muted);
}

.ellipsis {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-size: 0.9rem !important;
}

.summary-error {
	margin: 0.9rem 0 0;
	color: var(--viz-critical-text);
	font-size: 0.85rem;
	word-break: break-word;
}

.waterfall-head {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	flex-wrap: wrap;
	gap: 0.8rem;
	margin-bottom: 1rem;
}

.waterfall-head h4,
.span-head h4 {
	margin: 0;
	font-family: var(--font-display);
	font-size: 1.1rem;
	font-weight: 600;
}

.rt-legend {
	display: flex;
	flex-wrap: wrap;
	gap: 0.4rem 0.9rem;
	margin: 0;
	padding: 0;
	list-style: none;
	font-size: 0.78rem;
	color: var(--text);
}

.rt-legend li {
	display: inline-flex;
	align-items: center;
	gap: 0.35rem;
}

.waterfall-scroll {
	overflow-x: auto;
}

.waterfall-scroll > * {
	min-width: 38rem;
}

.span-head {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 0.7rem;
	margin-bottom: 0.9rem;
}

.span-type {
	display: inline-flex;
	align-items: center;
	gap: 0.35rem;
	font-size: 0.78rem;
	color: var(--muted);
}

.span-meta {
	display: flex;
	flex-wrap: wrap;
	gap: 0.6rem 1.8rem;
	margin: 0 0 1rem;
}

.span-meta dd {
	margin: 0.25rem 0 0;
	font-weight: 600;
}

.span-block {
	display: flex;
	flex-direction: column;
	gap: 0.4rem;
	margin-top: 0.9rem;
}

.block-label {
	font-size: 0.75rem;
	letter-spacing: 0.06em;
	text-transform: uppercase;
	color: var(--muted);
}

.block-label.is-error {
	color: var(--viz-critical-text);
}

.error-pre {
	border-color: var(--viz-critical);
}

.messages {
	display: flex;
	flex-direction: column;
	gap: 0.6rem;
}

.message {
	display: flex;
	flex-direction: column;
	gap: 0.25rem;
}

.message-role {
	font-size: 0.74rem;
	font-weight: 600;
	color: var(--accent);
}

.finish-reason {
	margin: 0;
	font-size: 0.74rem;
	color: var(--muted);
}
</style>
