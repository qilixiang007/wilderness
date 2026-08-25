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

async function request(path, { timeout = DEFAULT_TIMEOUT } = {}) {
	const controller = new AbortController()
	const timer = setTimeout(() => controller.abort(), timeout)
	try {
		const response = await fetch(BASE + path, { signal: controller.signal })
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

export const api = {
	getCategories: () => request('/api/categories'),
	getCategory: (slug) => request(`/api/categories/${encodeURIComponent(slug)}`),
	getObjects: (categorySlug) => request(`/api/objects?category=${encodeURIComponent(categorySlug)}`),
	getObject: (slug) => request(`/api/objects/${encodeURIComponent(slug)}`),
	search: (q) => request(`/api/search?q=${encodeURIComponent(q)}`)
}
