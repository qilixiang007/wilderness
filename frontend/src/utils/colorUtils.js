// 共享颜色工具：#RRGGBB 混合/取色，供 CelestialVisual（渲染默认值推导）与
// renderRepair（用户选色级联覆盖辅色/点缀色）复用，避免两处各自实现一套混色逻辑。

export const validHex = (v) => typeof v === 'string' && /^#([0-9a-fA-F]{3}){1,2}$/.test(v.trim())

export const hexToRgb = (hex) => {
	const h = hex.trim().replace('#', '')
	const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h
	const n = parseInt(full, 16)
	return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

const toHex = (n) => Math.round(n).toString(16).padStart(2, '0')

// 两色加权混合：w=0 返回 a，w=1 返回 b
export const mixHex = (a, b, w) => {
	const [r1, g1, b1] = hexToRgb(a)
	const [r2, g2, b2] = hexToRgb(b)
	const k = 1 - w
	return `#${toHex(r1 * k + r2 * w)}${toHex(g1 * k + g2 * w)}${toHex(b1 * k + b2 * w)}`
}

export const shade = (hex, w) => mixHex(hex, '#000000', w)
export const lighten = (hex, w) => mixHex(hex, '#ffffff', w)
