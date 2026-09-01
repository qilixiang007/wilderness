/**
 * 从localStorage安全读取数据
 * @param {string} key - 存储键
 * @param {any} defaultValue - 读取失败时的默认值
 * @returns {any} 存储的值或默认值
 */
export function getFromStorage(key, defaultValue = null) {
	try {
		const item = localStorage.getItem(key)
		return item !== null ? item : defaultValue
	} catch (e) {
		console.warn(`Failed to read from localStorage: ${key}`, e)
		return defaultValue
	}
}

/**
 * 安全写入数据到localStorage
 * @param {string} key - 存储键
 * @param {string} value - 要存储的值
 * @returns {boolean} 写入是否成功
 */
export function saveToStorage(key, value) {
	try {
		localStorage.setItem(key, value)
		return true
	} catch (e) {
		console.warn(`Failed to write to localStorage: ${key}`, e)
		return false
	}
}

/**
 * 安全删除localStorage中的数据
 * @param {string} key - 存储键
 * @returns {boolean} 删除是否成功
 */
export function removeFromStorage(key) {
	try {
		localStorage.removeItem(key)
		return true
	} catch (e) {
		console.warn(`Failed to remove from localStorage: ${key}`, e)
		return false
	}
}

/**
 * 清空localStorage中的所有数据
 * @returns {boolean} 清空是否成功
 */
export function clearStorage() {
	try {
		localStorage.clear()
		return true
	} catch (e) {
		console.warn('Failed to clear localStorage', e)
		return false
	}
}
