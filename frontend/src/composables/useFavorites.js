import { ref, watch } from 'vue'
import { api } from '../api'
import { useAuth } from './useAuth'

// 模块级单例：详情页与收藏页共享同一响应式数组。
// 收藏按用户存后端（favorite 表），登录态变化时自动拉取/清空。
const favorites = ref([])
// 自建天体收藏（favorite_generation 表），与上面的真实天体收藏并行维护、互不影响。
const generationFavorites = ref([])

/** 拉取当前用户收藏（真实天体 + 自建天体）；未登录或后端不可达时置空。 */
async function load() {
	const { user } = useAuth()
	if (!user.value) {
		favorites.value = []
		generationFavorites.value = []
		return
	}
	try {
		favorites.value = await api.getFavorites()
	} catch {
		favorites.value = []
	}
	try {
		generationFavorites.value = await api.getGenerationFavorites()
	} catch {
		generationFavorites.value = []
	}
}

// 登录态变化即同步一次（登录→拉取该用户收藏，登出→清空）。模块级注册一次即可。
watch(useAuth().user, () => {
	load()
})

export function useFavorites() {
	const { user } = useAuth()

	function isFavorite(slug) {
		return favorites.value.some((f) => f.slug === slug)
	}

	/**
	 * 收藏/取消收藏。未登录返回 { needLogin: true }，调用方据此跳登录页。
	 * 成功后返回后端最新收藏列表。
	 */
	async function toggle(item) {
		if (!user.value) return { needLogin: true }
		if (isFavorite(item.slug)) {
			favorites.value = await api.removeFavorite(item.slug)
		} else {
			favorites.value = await api.addFavorite(item.slug)
		}
		return { needLogin: false }
	}

	function isFavoriteGeneration(historyId) {
		return generationFavorites.value.some((f) => f.generationId === historyId)
	}

	/** 收藏/取消收藏自建天体；item 至少带 historyId（对应 CelestialGenerationHistory 行 id）。 */
	async function toggleGeneration(item) {
		if (!user.value) return { needLogin: true }
		if (isFavoriteGeneration(item.historyId)) {
			generationFavorites.value = await api.removeGenerationFavorite(item.historyId)
		} else {
			generationFavorites.value = await api.addGenerationFavorite(item.historyId)
		}
		return { needLogin: false }
	}

	return { favorites, isFavorite, toggle, generationFavorites, isFavoriteGeneration, toggleGeneration, load }
}
