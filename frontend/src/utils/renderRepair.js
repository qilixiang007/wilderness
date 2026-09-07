// 天体生成 Agent 的「确定性兜底」层：把用户显式要求的颜色 / N 颗卫星，确定性命中到画面上。
//
// 为什么需要：模型经常漏填 render 的颜色/卫星（实测对「紫色行星+3 颗绿卫星」的两次调用，
// render 都只给了 category、颜色字段全 null），漏填后组件会落入与请求无关的金色默认。
// 本模块以「用户输入 + 参数卡」为准做兜底，保证画面对得上用户要求，而不是押模型自觉。

import { shade, lighten } from './colorUtils'

// 6 位 #RRGGBB 主色提取（描述 / 参数卡里的颜色行）
const HEX6_RE = /#([0-9a-fA-F]{6})\b/

/** 取文本里第一个合法 6 位 #RRGGBB；没有返回空串。 */
export function firstHex(text) {
	if (typeof text !== 'string') return ''
	const m = text.match(HEX6_RE)
	return m ? m[0].toLowerCase() : ''
}

// 常见中文颜色词 → #RRGGBB（卫星颜色解析用；匹配按词长优先，先「绿色」后「绿」）
const COLOR_WORDS = {
	绿色: '#3cb371', 翠绿: '#20b2aa', 青色: '#4cc3b0', 蓝色: '#4a7bd9', 天蓝: '#6aa8e8',
	深蓝: '#3557c9', 紫色: '#8a5bd9', 淡紫: '#c9a0ff', 红色: '#d96a5a', 橙红: '#e87a4a',
	橙色: '#e8a33d', 金黄: '#e7c34f', 黄色: '#d9c14a', 白色: '#e8e8ea', 灰色: '#b8c0cc',
	银色: '#cfd6e0', 黑色: '#3a3f4a', 粉色: '#f2a6b8', 玫红: '#e89bb0',
	绿: '#3cb371', 青: '#4cc3b0', 蓝: '#4a7bd9', 紫: '#8a5bd9', 红: '#d96a5a',
	橙: '#e8a33d', 黄: '#d9c14a', 白: '#e8e8ea', 灰: '#b8c0cc', 银: '#cfd6e0', 粉: '#f2a6b8'
}

// 画面展示上限：唯一定义处，CelestialVisual 从这里导入复用，避免两处硬编码各自维护。
export const MAX_SATELLITES = 9

/**
 * 从用户描述里解析「N 颗[颜色词]卫星」→ 每颗一个 { color, size }。
 * 容错：容忍 颗/个、空格、中间夹「的/小/微型」等修饰。返回最多 9 颗；
 * 描述无卫星或解析不到 → []。这是模型漏填 satellites 时的确定性兜底。
 */
const SATELLITE_PHRASE_RE = /(\d+)\s*[颗个]\s*([^，。；、\n\d]{0,10})卫星/g

export function parseSatellites(text) {
	if (typeof text !== 'string') return []
	const found = []
	// 数字 后跟 颗/个（可含空格），再抓最多 10 个非标点字符里的颜色词，以「卫星」收尾
	const re = new RegExp(SATELLITE_PHRASE_RE)
	let m
	while ((m = re.exec(text)) && found.length < MAX_SATELLITES) {
		const count = Math.min(parseInt(m[1], 10) || 1, MAX_SATELLITES)
		const segment = m[2] || ''
		const words = Object.keys(COLOR_WORDS).sort((a, b) => b.length - a.length)
		const word = words.find((w) => segment.includes(w)) || ''
		const color = word ? COLOR_WORDS[word] : ''
		for (let i = 0; i < count && found.length < MAX_SATELLITES; i++) {
			found.push(color ? { color, size: 0.13 } : { size: 0.13 })
		}
	}
	return found
}

/** 描述里「N 颗…卫星」的原始请求数量（不裁剪），仅用于判断是否发生了展示层截断。 */
function requestedSatelliteCount(text) {
	if (typeof text !== 'string') return 0
	let total = 0
	const re = new RegExp(SATELLITE_PHRASE_RE)
	let m
	while ((m = re.exec(text))) {
		total += parseInt(m[1], 10) || 1
	}
	return total
}

/**
 * 生成交给 <CelestialVisual> 的有效 render：主色权威链 + 卫星兜底。
 *
 * @param render 模型原始 render（可为 null/部分）
 * @param opts { userColor 用户点选色板的 #hex（权威，无条件覆盖）;
 *               desc 用户原始描述; paramsText 参数卡文本（键值拼接）}
 * @returns 附加 truncatedFrom 字段：请求的卫星数超过 MAX_SATELLITES 时为原始请求数，否则为 null。
 */
export function repairRender(render, { userColor = '', desc = '', paramsText = '' } = {}) {
	const base = render && typeof render === 'object' ? render : {}

	// 主色权威链：用户点过色板 > 模型给的主色 > 参数卡/描述里的 #hex
	let primaryColor = ''
	if (userColor) primaryColor = userColor
	else if (base.primaryColor) primaryColor = base.primaryColor
	else primaryColor = firstHex(paramsText) || firstHex(desc)

	// 卫星：模型给了合法数组就用；否则按描述里的「N 颗…卫星」解析兜底
	const hasModelSats = Array.isArray(base.satellites) && base.satellites.length > 0
	const requestedCount = hasModelSats ? base.satellites.length : requestedSatelliteCount(desc)
	const satellites = hasModelSats ? base.satellites.slice(0, MAX_SATELLITES) : parseSatellites(desc)
	const truncatedFrom = requestedCount > MAX_SATELLITES ? requestedCount : null

	const repaired = { ...base, satellites, truncatedFrom }
	if (primaryColor) repaired.primaryColor = primaryColor
	// 用户真实点选颜色时视为对整体调色板的强约束：辅色/点缀色一并按主色派生，
	// 覆盖模型自己给的值，避免画面残留与所选色无关的色块。
	if (userColor) {
		repaired.secondaryColor = shade(primaryColor, 0.55)
		repaired.accentColor = lighten(primaryColor, 0.5)
	}
	return repaired
}
