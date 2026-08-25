/**
 * 后端 API 客户端。
 *
 * - 成功：返回 ApiResponse 解包后的 data。
 * - 后端在线但出错（success=false 或非 2xx）：抛 ApiError（带 message 和 status）。
 * - 后端不可达（网络失败或超时）：抛 ApiUnavailableError，页面据此回退到静态数据。
 */

const BASE = import.meta.env.VITE_API_BASE ?? ''

const DEFAULT_TIMEOUT = 4000

export class ApiUnavailableError extends Error {
	constructor(message = 'Backend unreachable') {
		super(message)
		this.name = 'ApiUnavailableError'
	}
}

export class ApiError extends Error {
	constructor(message, status) {
		super(message)
		this.name = 'ApiError'
		this.status = status
	}
}

async function request(path, { method = 'GET', body, headers, timeout = DEFAULT_TIMEOUT } = {}) {
	const controller = new AbortController()
	const timer = setTimeout(() => controller.abort(), timeout)
	try {
		const response = await fetch(BASE + path, {
			method,
			body,
			headers,
			signal: controller.signal
		})
		const body = await response.json().catch(() => null)
		if (!response.ok || !body || body.success === false) {
			throw new ApiError(body?.message || `Request failed: ${response.status}`, response.status)
		}
		return body.data
	} catch (error) {
		if (error instanceof ApiError) throw error
		throw new ApiUnavailableError()
	} finally {
		clearTimeout(timer)
	}
}

/**
 * 打开 AI 问答的 SSE 流式连接(后端为 GET 端点,可用 EventSource)。
 * - onSources(sourceList):检索到的引文(先于回答推送)
 * - onDelta(text):回答的增量片段
 * - onDone():连接结束
 * 返回 EventSource,便于前端主动关闭。
 */
export function openChatStream(question, { webEnabled = false, onSources, onDelta, onDone }) {
	const es = new EventSource(
		`${BASE}/api/ai/chat/stream?question=${encodeURIComponent(question)}&webEnabled=${webEnabled}`
	)
	es.addEventListener('sources', (e) => {
		try {
			onSources(JSON.parse(e.data))
		} catch {
			/* 忽略解析失败的来源事件 */
		}
	})
	es.addEventListener('delta', (e) => onDelta(e.data))
	es.onerror = () => {
		es.close()
		onDone()
	}
	return es
}

export const api = {
	getCategories: () => request('/api/categories'),
	getCategory: (slug) => request(`/api/categories/${encodeURIComponent(slug)}`),
	getObjects: (categorySlug) => request(`/api/objects?category=${encodeURIComponent(categorySlug)}`),
	getObject: (slug) => request(`/api/objects/${encodeURIComponent(slug)}`),
	search: (q) => request(`/api/search?q=${encodeURIComponent(q)}`),
	chat: (question, webEnabled = false) =>
		request('/api/ai/chat', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ question, webSearchEnabled: webEnabled })
		}),
	explain: (slug) => request(`/api/ai/explain/${encodeURIComponent(slug)}`),
	uploadKnowledge: (file) => {
		const form = new FormData()
		form.append('file', file)
		return request('/api/knowledge/upload', { method: 'POST', body: form, timeout: 60000 })
	}
}
