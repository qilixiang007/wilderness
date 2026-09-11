import { ref } from 'vue'

// 模块级单例：全站共用一个确认弹窗实例（镜像 useCompare 的单例模式）。
// 调用 confirm() 拿到 Promise<boolean>，弹窗组件在 App.vue 里挂载一次即可。
const state = ref({
	open: false,
	message: '',
	confirmText: '',
	cancelText: '',
	resolve: null
})

export function useConfirm() {
	/** 弹出确认框，用户点确认/取消前 await 挂起；点遮罩等同取消。 */
	function confirm(message, options = {}) {
		return new Promise((resolve) => {
			state.value = {
				open: true,
				message,
				confirmText: options.confirmText ?? '',
				cancelText: options.cancelText ?? '',
				resolve
			}
		})
	}

	function respond(result) {
		state.value.resolve?.(result)
		state.value = { ...state.value, open: false, resolve: null }
	}

	return { state, confirm, respond }
}
