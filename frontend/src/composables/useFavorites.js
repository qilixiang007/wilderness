import { ref } from 'vue'

const KEY = 'wilderness-favorites'

// 模块级单例：详情页与收藏页共享同一响应式数组。
// 存 slug + 名称 + 图片摘要（约 5KB，远小于 localStorage 配额），
// 收藏页零请求、后端离线也可浏览。
const favorites = ref(load())

function load() {
	try {
		return JSON.parse(localStorage.getItem(KEY)) ?? []
	} catch {
		return []
	}
}

function save() {
	try {
		localStorage.setItem(KEY, JSON.stringify(favorites.value))
	} catch {
		/* 存储不可用时仅内存态 */
	}
}

export function useFavorites() {
	function isFavorite(slug) {
		return favorites.value.some((f) => f.slug === slug)
	}

	function toggle(item) {
		const index = favorites.value.findIndex((f) => f.slug === item.slug)
		if (index >= 0) {
			favorites.value.splice(index, 1)
		} else {
			favorites.value.push({
				slug: item.slug,
				zhName: item.zhName,
				enName: item.enName,
				image: item.image
			})
		}
		save()
	}

	return { favorites, isFavorite, toggle }
}
