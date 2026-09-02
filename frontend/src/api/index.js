/**
 * 后端 API 客户端。
 *
 * - 成功：返回 ApiResponse 解包后的 data。
 * - 后端在线但出错（success=false 或非 2xx）：抛 ApiError（带 message 和 status）。
 * - 后端不可达（网络失败或超时）：抛 ApiUnavailableError，页面据此回退到静态数据。
 */

const BASE = import.meta.env.VITE_API_BASE ?? ''

const DEFAULT_TIMEOUT = 8000

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
	const fullUrl = BASE + path
	// 极老的内核/受限 WebView 可能缺少 fetch，会抛 ReferenceError（请求根本没发出）。
	// 这里给出明确原因便于定位。
	if (typeof fetch !== 'function') {
		console.error('[api] 当前环境缺少 fetch，无法发起请求')
		throw new ApiUnavailableError('env-no-fetch')
	}
	// AbortController 缺失时退化为「无超时」请求，保证老环境也能发请求。
	const canAbort = typeof AbortController === 'function'
	const controller = canAbort ? new AbortController() : null
	const timer = canAbort ? setTimeout(() => controller.abort(), timeout) : null
	try {
		const response = await fetch(fullUrl, {
			method,
			body,
			headers,
			// 会话基于 HttpOnly Cookie，跨源(VITE_API_BASE)时也要带上 cookie
			credentials: 'include',
			...(controller ? { signal: controller.signal } : {})
		})
		// 响应体变量名用 data，避免与请求参数 body 撞名触发 TDZ（Cannot access 'body' before initialization）
		const data = await response.json().catch(() => null)
		if (!response.ok || !data || data.success === false) {
			throw new ApiError(data?.message || `Request failed: ${response.status}`, response.status)
		}
		return data.data
	} catch (error) {
		if (error instanceof ApiError) throw error
		// 透传底层错误（AbortError=超时、TypeError=网络失败、ReferenceError=缺少全局对象…）
		// 连同完整请求 URL，便于前端定位。
		console.error(`[api] request failed: ${fullUrl}`, error)
		throw new ApiUnavailableError(`${error?.name || 'NetworkError'}: ${error?.message || ''} @ ${fullUrl}`)
	} finally {
		if (timer !== null) clearTimeout(timer)
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
	// AI 生成类接口耗时远超默认 8s（LLM 检索+成文通常 10~30s），沿用上传文件的 60s 超时，避免被 AbortController 提前掐断
	explain: (slug) => request(`/api/ai/explain/${encodeURIComponent(slug)}`, { timeout: 60000 }),
	// 天体生成 Agent：描述 → 检索真实天体作参考 → 生成虚拟天体的介绍与渲染参数
	generateCelestial: (description) =>
		request('/api/ai/generate-celestial', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ description }),
			timeout: 60000
		}),
	uploadKnowledge: (file) => {
		const form = new FormData()
		form.append('file', file)
		return request('/api/knowledge/upload', { method: 'POST', body: form, timeout: 60000 })
	},
	// —— 认证 ——
	verifyCode: (email, purpose) =>
		request('/api/auth/verify-code', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ email, purpose })
		}),
	register: (email, password, code) =>
		request('/api/auth/register', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ email, password, code: code || null })
		}),
	loginPassword: (email, password) =>
		request('/api/auth/login-password', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ email, password })
		}),
	loginCode: (email, code) =>
		request('/api/auth/login-code', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ email, code })
		}),
	logout: () => request('/api/auth/logout', { method: 'POST' }),
	me: () => request('/api/auth/me'),
	// —— 收藏（按用户隔离）——
	getFavorites: () => request('/api/favorites'),
	addFavorite: (slug) => request(`/api/favorites/${encodeURIComponent(slug)}`, { method: 'PUT' }),
	removeFavorite: (slug) => request(`/api/favorites/${encodeURIComponent(slug)}`, { method: 'DELETE' }),
	// —— 个人知识库文件 ——
	getKnowledgeFiles: () => request('/api/knowledge/files'),
	deleteKnowledgeFile: (id) => request(`/api/knowledge/files/${id}`, { method: 'DELETE' }),
	// —— 对话历史（仅当前用户）——
	getHistory: (page = 0, size = 20, q = '') => {
		const params = new URLSearchParams({ page, size })
		if (q) params.set('q', q)
		return request(`/api/history?${params.toString()}`)
	},
	deleteHistory: (id) => request(`/api/history/${id}`, { method: 'DELETE' }),
	// —— 自定义智能体（按用户隔离）——
	getAgents: () => request('/api/agents'),
	createAgent: (payload) =>
		request('/api/agents', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(payload)
		}),
	updateAgent: (id, payload) =>
		request(`/api/agents/${id}`, {
			method: 'PUT',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(payload)
		}),
	deleteAgent: (id) => request(`/api/agents/${id}`, { method: 'DELETE' }),
	// 使用某个自定义智能体生成天体（人设 + 工具开关生效）
	generateCelestialWithAgent: (id, description) =>
		request(`/api/agents/${id}/generate`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ description }),
			timeout: 60000
		})
}
