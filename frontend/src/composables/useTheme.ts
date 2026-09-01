import { ref, watch } from 'vue'
import { getFromStorage, saveToStorage } from '../utils/storage'

const THEME_STORAGE_KEY = 'wilderness-theme'
const DEFAULT_THEME = 'minimal'

export function useTheme() {
	const theme = ref(getInitialTheme())

	function getInitialTheme() {
		return getFromStorage(THEME_STORAGE_KEY, DEFAULT_THEME)
	}

	function persistTheme(value) {
		saveToStorage(THEME_STORAGE_KEY, value)
	}

	watch(theme, persistTheme)

	return { theme }
}
