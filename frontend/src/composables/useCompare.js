import { computed, ref } from 'vue'

// 模块级单例：跨页面共享同一份"已勾选待对比"列表（镜像 useFavorites 的单例模式）。
// 只存轻量快照（够悬浮栏渲染 chip），不持久化，刷新页面即清空。
const selected = ref([])

export const COMPARE_MAX = 4

export function useCompare() {
	function isSelected(slug) {
		return selected.value.some((o) => o.slug === slug)
	}

	/** 勾选/取消勾选。已达上限时不切换状态，返回 { maxed: true } 供调用方处理。 */
	function toggle(item) {
		const idx = selected.value.findIndex((o) => o.slug === item.slug)
		if (idx >= 0) {
			selected.value.splice(idx, 1)
			return { removed: true }
		}
		if (selected.value.length >= COMPARE_MAX) {
			return { maxed: true }
		}
		selected.value.push({ slug: item.slug, zhName: item.zhName, enName: item.enName, image: item.image })
		return { added: true }
	}

	function clear() {
		selected.value = []
	}

	return {
		selected,
		count: computed(() => selected.value.length),
		isSelected,
		toggle,
		clear
	}
}
