import { ref, computed } from 'vue'
import { api } from '../api'

// 模块级单例：顶栏、路由守卫、收藏、Ask 页共享同一登录态。
// 会话存在后端 HttpOnly Cookie 里，前端只保存 UserDTO(id, email)。
const user = ref(null)

export function useAuth() {
	const isLoggedIn = computed(() => user.value != null)

	/** 启动/刷新时恢复会话：请求 /me，401 或后端不可达都视为未登录。 */
	async function refreshMe() {
		try {
			user.value = await api.me()
		} catch {
			user.value = null
		}
	}

	/** 登录：method = 'password' | 'code'，成功后保存用户信息。 */
	async function login(method, payload) {
		const me = method === 'password'
			? await api.loginPassword(payload)
			: await api.loginCode(payload)
		user.value = me
		return me
	}

	/** 注册（密码 + 可选验证码），成功后自动登录。 */
	async function register(payload) {
		const me = await api.register(payload.email, payload.password, payload.code)
		user.value = me
		return me
	}

	/** 登出：调后端删会话，本地登录态清空。 */
	async function logout() {
		try {
			await api.logout()
		} catch {
			/* 后端不可达时也清本地登录态 */
		}
		user.value = null
	}

	return { user, isLoggedIn, refreshMe, login, register, logout }
}
