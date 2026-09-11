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
 * - onRetrievalDegraded():知识库检索失败,回答未经知识库核实(仅降级时才会收到这个事件)
 * - onSources(sourceList):检索到的引文(先于回答推送)
 * - onDelta(text):回答的增量片段
 * - onDone():连接结束
 * 返回 EventSource,便于前端主动关闭。
 */
export function openChatStream(question, { webEnabled = false, onRetrievalDegraded, onSources, onDelta, onDone }) {
	const es = new EventSource(
		`${BASE}/api/ai/chat/stream?question=${encodeURIComponent(question)}&webEnabled=${webEnabled}`
	)
	es.addEventListener('retrieval-status', () => {
		onRetrievalDegraded?.()
	})
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

/**
 * 打开多天体对比的 SSE 流式连接:每个天体讲解一完成就推 item 事件(顺序=完成顺序，
 * 不是请求顺序),全部完成后再推一次 overview 事件。
 * - onItem(item):单个天体的讲解结果(CompareItemResult,error 非空即该项降级)
 * - onOverview(text):综合总结,失败为 null
 * - onEnd():流结束(不管是正常结束还是异常中断——原生 EventSource 无法区分两者，
 *   调用方需结合是否已收到 onOverview 来判断是否是异常中断)
 */
export function openCompareStream(slugs, { onItem, onOverview, onEnd }) {
	const es = new EventSource(`${BASE}/api/ai/compare/stream?slugs=${encodeURIComponent(slugs.join(','))}`)
	es.addEventListener('item', (e) => {
		try {
			onItem(JSON.parse(e.data))
		} catch {
			/* 忽略解析失败的条目 */
		}
	})
	es.addEventListener('overview', (e) => {
		try {
			onOverview(JSON.parse(e.data).overview)
		} catch {
			onOverview(null)
		}
	})
	es.onerror = () => {
		es.close()
		onEnd?.()
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
	// 一次提交一批文件(后端上限 5 个)。耗时大头在上传完之后的解析+向量化，
	// 满批可能跑几分钟，超时给到 5 分钟。
	uploadKnowledge: (files) => {
		const form = new FormData()
		files.forEach((file) => form.append('files', file))
		return request('/api/knowledge/upload', { method: 'POST', body: form, timeout: 300000 })
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
	resetPassword: (email, code, newPassword) =>
		request('/api/auth/reset-password', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ email, code, newPassword })
		}),
	changePassword: (oldPassword, newPassword) =>
		request('/api/auth/change-password', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ oldPassword, newPassword })
		}),
	logout: () => request('/api/auth/logout', { method: 'POST' }),
	me: () => request('/api/auth/me'),
	// —— 收藏（按用户隔离）——
	getFavorites: () => request('/api/favorites'),
	addFavorite: (slug) => request(`/api/favorites/${encodeURIComponent(slug)}`, { method: 'PUT' }),
	removeFavorite: (slug) => request(`/api/favorites/${encodeURIComponent(slug)}`, { method: 'DELETE' }),
	// —— 收藏：自建天体（生成历史记录 id）——
	getGenerationFavorites: () => request('/api/favorites/generation'),
	addGenerationFavorite: (id) => request(`/api/favorites/generation/${id}`, { method: 'PUT' }),
	removeGenerationFavorite: (id) => request(`/api/favorites/generation/${id}`, { method: 'DELETE' }),
	// —— 个人知识库文件 ——
	getKnowledgeFiles: () => request('/api/knowledge/files'),
	previewKnowledgeFile: (id) => request(`/api/knowledge/files/${id}/preview`),
	deleteKnowledgeFile: (id) => request(`/api/knowledge/files/${id}`, { method: 'DELETE' }),
	// —— 对话历史（仅当前用户）——
	getHistory: (page = 0, size = 20, q = '') => {
		const params = new URLSearchParams({ page, size })
		if (q) params.set('q', q)
		return request(`/api/history?${params.toString()}`)
	},
	deleteHistory: (id) => request(`/api/history/${id}`, { method: 'DELETE' }),
	// —— 对比历史（仅当前用户；对比完成后后端自动落库，无需手动保存）——
	getCompareHistory: (page = 0, size = 20) => request(`/api/history/compare?page=${page}&size=${size}`),
	deleteCompareHistory: (id) => request(`/api/history/compare/${id}`, { method: 'DELETE' }),
	// —— 天体生成历史（仅当前用户；生成完成后后端自动落库，含完整链路日志）——
	getGenerationHistory: (page = 0, size = 20) => request(`/api/history/generation?page=${page}&size=${size}`),
	getGenerationHistoryDetail: (id) => request(`/api/history/generation/${id}`),
	deleteGenerationHistory: (id) => request(`/api/history/generation/${id}`, { method: 'DELETE' }),
	setGenerationVisibility: (id, isPublic) =>
		request(`/api/history/generation/${id}/visibility`, {
			method: 'PATCH',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ isPublic })
		}),
	// —— AI 调用链路（管理员看全部，普通用户看自己的；时间参数为 epoch 毫秒）——
	listTraces: ({ page = 0, size = 20, name, status, from, to, keyword } = {}) => {
		const params = new URLSearchParams({ page, size })
		if (name) params.set('name', name)
		if (status) params.set('status', status)
		if (from != null) params.set('from', from)
		if (to != null) params.set('to', to)
		if (keyword) params.set('keyword', keyword)
		return request(`/api/traces?${params.toString()}`)
	},
	getTrace: (traceId) => request(`/api/traces/${encodeURIComponent(traceId)}`),
	// 仅管理员；ES 聚合可能稍慢，放宽超时
	getTraceStats: ({ from, to } = {}) => {
		const params = new URLSearchParams()
		if (from != null) params.set('from', from)
		if (to != null) params.set('to', to)
		return request(`/api/traces/stats?${params.toString()}`, { timeout: 15000 })
	},
	// —— 公开广场（匿名可访问）——
	getGallery: (page = 0, size = 20) => request(`/api/gallery?page=${page}&size=${size}`),
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
