import { createI18n } from 'vue-i18n'
import zh from './locales/zh.js'
import en from './locales/en.js'

// 语言持久化：启动时读取，非法值回退中文。
const stored = (() => {
	try {
		return localStorage.getItem('wilderness-locale')
	} catch {
		return null
	}
})()

export const i18n = createI18n({
	legacy: false,
	globalInjection: true,
	locale: stored === 'en' ? 'en' : 'zh',
	fallbackLocale: 'zh',
	messages: { zh, en }
})

/**
 * 数据字段选择辅助：数据层（zhName/enName、imageAltZh/imageAltEn 等）本来就是成对字段，
 * 在此按当前语言统一取值，取代各处 `language === 'zh' ? zh : en` 三元。
 * 读取 `i18n.global.locale.value` 会建立响应依赖，切换语言时组件自动重渲染。
 */
export function pick(zhValue, enValue) {
	return i18n.global.locale.value === 'zh' ? zhValue : enValue
}
