import { ref } from 'vue'

// 对比按钮的一次性引导提示：只在用户第一次看到可对比的天体网格时出现一次
const HINT_KEY = 'wilderness-compare-hint-seen'

export function useCompareHint() {
	let seen = false
	try {
		seen = !!window.localStorage.getItem(HINT_KEY)
	} catch {
		/* 隐私模式等场景下 localStorage 不可用，直接当作已提示过，不再打扰 */
		seen = true
	}

	const show = ref(!seen)

	function dismiss() {
		show.value = false
		try {
			window.localStorage.setItem(HINT_KEY, '1')
		} catch {
			/* 忽略写入失败 */
		}
	}

	return { show, dismiss }
}
