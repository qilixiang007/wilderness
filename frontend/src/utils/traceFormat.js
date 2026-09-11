// AI 调用链路页面共用的格式化工具。

/** 后端 trace 根 span 的全部操作名（筛选下拉用，顺序即展示顺序）。 */
export const TRACE_OPERATIONS = [
	'rag.stream_chat',
	'rag.chat',
	'rag.explain',
	'compare',
	'agent.generate_celestial',
	'agent.generate_celestial_custom',
	'knowledge.upload'
]

export const TRACE_STATUSES = ['success', 'error', 'cancelled']

/** span 的 run_type，顺序固定——它就是分类色的槽位顺序（颜色跟着类型走，不随出现顺序变）。 */
export const RUN_TYPES = ['chain', 'llm', 'retriever', 'tool', 'embedding', 'parser']

/** 操作名本地化：rag.chat → traces.ops.rag_chat；没有文案的回退为原始名。 */
export function opLabel(t, te, name) {
	if (!name) return ''
	const key = `traces.ops.${name.replace(/\./g, '_')}`
	return te(key) ? t(key) : name
}

export function statusLabel(t, te, status) {
	const key = `traces.status.${status}`
	return te(key) ? t(key) : status
}

/** 毫秒 → 人类可读：<1s 用 ms，<1min 用一位小数的秒，更长用 分+秒。 */
export function formatDuration(ms) {
	if (ms == null || Number.isNaN(ms)) return '—'
	const value = Math.round(ms)
	if (value < 1000) return `${value} ms`
	if (value < 60000) return `${(value / 1000).toFixed(1)} s`
	const minutes = Math.floor(value / 60000)
	const seconds = Math.round((value % 60000) / 1000)
	return `${minutes}m ${seconds}s`
}

export function formatNumber(n) {
	if (n == null) return '—'
	return Number(n).toLocaleString()
}

export function formatPercent(ratio) {
	if (ratio == null || Number.isNaN(ratio)) return '—'
	return `${(ratio * 100).toFixed(ratio < 0.1 ? 1 : 0)}%`
}

function pad(n) {
	return String(n).padStart(2, '0')
}

/** ISO 或毫秒 → 本地时间 MM-DD HH:mm:ss。 */
export function formatDateTime(value) {
	if (value == null) return '—'
	const d = new Date(value)
	return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 统计时间桶的轴标签：按小时分桶显示 HH:00，按天显示 MM-DD。 */
export function formatBucket(ms, interval) {
	const d = new Date(ms)
	return interval === '1d' ? `${pad(d.getMonth() + 1)}-${pad(d.getDate())}` : `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:00`
}
