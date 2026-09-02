<script setup>
// 天体生成 Agent 的 SVG 程序化渲染组件（图片双通道的默认通道）。
// 接收后端 LLM 结构化输出的 RenderSpec 参数，按类别绘制恒星/行星/卫星/星系/星云/彗星。
// 容错：LLM 可能缺字段/给非法值，所有字段合并默认值 + 数值 clamp + 颜色校验。
import { computed } from 'vue'

const props = defineProps({
	render: { type: Object, default: null },
	name: { type: String, default: '' }
})

const DEFAULTS = {
	category: 'star',
	primaryColor: '#e7b96a',
	secondaryColor: '#d89b3f',
	accentColor: '#fff3d6',
	coreSize: 0.45,
	brightness: 0.8,
	hasRings: false,
	ringTilt: 20,
	bands: 6,
	bandContrast: 0.4,
	spots: 0,
	surfaceTexture: 0.3,
	noise: 0.5,
	spiralArms: 2,
	spiralTwist: 0.6,
	tailLength: 0.7,
	tailSpread: 0.3,
	glowSpread: 0.5
}

const CATEGORIES = ['star', 'planet', 'moon', 'galaxy', 'nebula', 'small-bodies']

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v))

// 非法颜色回退默认（保证 fill 合法，避免 SVG 静默丢弃）
const safeColor = (v, fallback) =>
	typeof v === 'string' && /^#([0-9a-fA-F]{3}){1,2}$/.test(v.trim()) ? v : fallback

// 颜色工具：主色缺辅色/点缀色时从主色推导，避免「紫色主体 + 金色默认辅色」的割裂观感
const validHex = (v) => typeof v === 'string' && /^#([0-9a-fA-F]{3}){1,2}$/.test(v.trim())
const hexToRgb = (hex) => {
	const h = hex.trim().replace('#', '')
	const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h
	const n = parseInt(full, 16)
	return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}
const toHex = (n) => Math.round(n).toString(16).padStart(2, '0')
// 两色加权混合：w=0 返回 a，w=1 返回 b
const mixHex = (a, b, w) => {
	const [r1, g1, b1] = hexToRgb(a)
	const [r2, g2, b2] = hexToRgb(b)
	const k = 1 - w
	return `#${toHex(r1 * k + r2 * w)}${toHex(g1 * k + g2 * w)}${toHex(b1 * k + b2 * w)}`
}
const shade = (hex, w) => mixHex(hex, '#000000', w)
const lighten = (hex, w) => mixHex(hex, '#ffffff', w)

// 合并默认值 + clamp + 颜色校验后的有效渲染参数
// 合并默认值 + clamp 后的有效渲染参数。
// 颜色单独处理：主色优先，辅/点缀色缺项时从主色推导（避免 render 只给主色/主色被权威覆盖时，
// 画面混入与主色无关的固定金色）。卫星清洗为 { color, size } 白名单，最多 9 颗。
const r = computed(() => {
	const src = props.render || {}
	const out = {}
	for (const [k, def] of Object.entries(DEFAULTS)) {
		if (typeof def !== 'number') continue
		const v = typeof src[k] === 'number' ? src[k] : def
		out[k] = k === 'coreSize' ? clamp(v, 0.2, 0.9)
			: k === 'ringTilt' ? clamp(v, 0, 75)
			: k === 'spiralArms' ? Math.round(clamp(v, 2, 6))
			: k === 'bands' ? Math.round(clamp(v, 0, 14))
			: k === 'spots' ? Math.round(clamp(v, 0, 12))
			: clamp(v, 0, 1)
	}
	out.primaryColor = safeColor(src.primaryColor, DEFAULTS.primaryColor)
	out.secondaryColor = validHex(src.secondaryColor) ? src.secondaryColor : shade(out.primaryColor, 0.55)
	out.accentColor = validHex(src.accentColor) ? src.accentColor : lighten(out.primaryColor, 0.5)
	const category = String(src.category || DEFAULTS.category).toLowerCase()
	out.category = CATEGORIES.includes(category) ? category : DEFAULTS.category
	const sats = Array.isArray(src.satellites) ? src.satellites : []
	out.satellites = sats.slice(0, 9).map((s) => {
		const color = safeColor(s && s.color, out.accentColor)
		const size = Number(s && s.size)
		return { color, size: Number.isFinite(size) ? clamp(size, 0.05, 0.5) : 0.13 }
	})
	return out
})

const CX = 200
const CY = 200
const coreRadius = computed(() => r.value.coreSize * 95)
const glowRadius = computed(() => coreRadius.value * (1 + r.value.glowSpread * 2.4))

// 星空背景散点（固定种子，避免每次重渲染跳动）
const stars = [
	[28, 45, 1], [61, 22, 0.7], [92, 71, 1.3], [41, 118, 0.6], [76, 152, 1],
	[20, 200, 0.8], [52, 246, 1.2], [90, 305, 0.6], [38, 355, 1], [70, 385, 0.8],
	[118, 36, 0.7], [154, 12, 1.1], [132, 96, 0.8], [158, 178, 0.6], [148, 272, 1],
	[124, 360, 0.9], [245, 28, 0.8], [292, 15, 1.1], [268, 88, 0.7], [305, 66, 1],
	[330, 130, 0.9], [362, 188, 0.6], [332, 262, 1.1], [365, 318, 0.8], [300, 368, 0.7],
	[255, 350, 1], [218, 385, 0.8], [345, 250, 0.6], [355, 90, 0.8], [182, 24, 0.9]
]

// 行星云带：颜色在主/辅色间交替，对比度驱动透明度
const bandList = computed(() => {
	const bands = Math.max(1, r.value.bands)
	const list = []
	const span = coreRadius.value * 1.6
	const top = CY - span / 2
	for (let i = 0; i < bands; i++) {
		list.push({
			y: top + (span * i) / Math.max(1, bands - 1),
			h: span / Math.max(1, bands) * 0.7,
			color: i % 2 === 0 ? r.value.primaryColor : r.value.secondaryColor,
			o: 0.25 + r.value.bandContrast * 0.5
		})
	}
	return list
})

// 行星斑点（大红斑/亮区）固定候选位置
const SPOT_SLOTS = [
	[238, 218, 20, 9], [178, 182, 13, 6], [216, 168, 9, 4], [260, 200, 8, 4], [170, 228, 7, 3]
]

// 月面环形山（位置/半径固定，深浅随机微差）
const craters = [
	[214, 186, 16], [182, 216, 11], [228, 226, 8], [172, 176, 7], [198, 244, 6], [240, 172, 5]
]

// 星云弥散云团：主/辅/点缀三色椭圆，位置固定
const nebulaBlobs = [
	[150, 170, 90, 58, 0.55], [252, 196, 84, 66, 0.5], [196, 148, 62, 44, 0.45],
	[210, 252, 70, 50, 0.42], [132, 240, 56, 40, 0.4], [268, 132, 52, 38, 0.38]
]

// 星系对数螺线旋臂
const galaxySpiral = computed(() => {
	const d = r.value
	let path = ''
	const radius = coreRadius.value * 1.5
	for (let a = 0; a < d.spiralArms; a++) {
		const start = (a * 360) / d.spiralArms
		let sub = ''
		const steps = 30
		for (let i = 0; i <= steps; i++) {
			const t = i / steps
			const ang = ((start + t * 380 * (0.45 + d.spiralTwist * 0.55)) * Math.PI) / 180
			const rad = 10 + t * radius
			const x = CX + rad * Math.cos(ang)
			const y = CY + rad * Math.sin(ang)
			sub += (i === 0 ? `M${x.toFixed(1)},${y.toFixed(1)}` : ` L${x.toFixed(1)},${y.toFixed(1)}`)
		}
		path += sub + ' '
	}
	return path
})

// 彗尾锥形（由宽到窄朝左下延伸）
const cometTail = computed(() => {
	const d = r.value
	const L = 90 + d.tailLength * 130
	const spread = 30 + d.tailSpread * 70
	const x = CX + 26
	const y = CY - 14
	return `M${x},${y} L${x - L},${y + L * 0.72 - spread / 2} L${x - L * 0.92},${y + L * 0.72 + spread / 2} Z`
})

// 环绕卫星布局：等角落在倾斜轨道椭圆上（仅行星/恒星；轨道半径随中心体缩放、不超画布）
const satelliteOrbit = computed(() => {
	const sats = r.value.satellites
	const host = r.value.category === 'planet' || r.value.category === 'star'
	if (!sats.length || !host) return null
	const rx = Math.min(coreRadius.value * (r.value.hasRings ? 3.1 : 2.6), 185)
	const ry = rx * 0.42
	const tilt = r.value.ringTilt || 20
	const dots = sats.map((s, i) => {
		const a = ((i * 360) / sats.length + 15) * (Math.PI / 180)
		return {
			color: s.color,
			x: CX + rx * Math.cos(a),
			y: CY + ry * Math.sin(a),
			rad: Math.max(3, coreRadius.value * 0.12 * ((s.size || 0.13) / 0.13))
		}
	})
	return { rx, ry, tilt, dots }
})

const turbBase = computed(() => (0.012 + r.value.noise * 0.05).toFixed(4))
const surfaceBase = computed(() => (0.03 + r.value.surfaceTexture * 0.09).toFixed(4))
</script>

<template>
	<svg class="celestial-visual" viewBox="0 0 400 400" role="img" :aria-label="name || '生成的天体'">
		<defs>
			<radialGradient id="cglow" cx="50%" cy="50%" r="50%">
				<stop offset="0%" :stop-color="r.primaryColor" :stop-opacity="0.85 * r.brightness" />
				<stop offset="100%" :stop-color="r.primaryColor" stop-opacity="0" />
			</radialGradient>

			<linearGradient id="cbody" x1="0" y1="0" x2="1" y2="1">
				<stop offset="0%" :stop-color="r.primaryColor" />
				<stop offset="100%" :stop-color="r.secondaryColor" />
			</linearGradient>

			<!-- 表面噪点纹理（卫星/行星） -->
			<filter id="ctex" x="-20%" y="-20%" width="140%" height="140%">
				<feTurbulence :baseFrequency="surfaceBase" numOctaves="2" seed="11" />
				<feColorMatrix type="matrix"
					values="0 0 0 0 0.9  0 0 0 0 0.85  0 0 0 0 0.8  0 0 0 0.4 0" />
				<feComposite operator="in" in2="SourceGraphic" />
			</filter>

			<!-- 星云尘埃噪点 -->
			<filter id="cnoise" x="-30%" y="-30%" width="160%" height="160%">
				<feTurbulence :baseFrequency="turbBase" numOctaves="3" seed="3" type="fractalNoise" />
				<feColorMatrix type="matrix"
					values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 0.5 0" />
			</filter>

			<filter id="cblur" x="-30%" y="-30%" width="160%" height="160%">
				<feGaussianBlur stdDeviation="18" />
			</filter>

			<clipPath id="ccore">
				<circle :cx="CX" :cy="CY" :r="coreRadius" />
			</clipPath>
		</defs>

		<!-- 星空背景 -->
		<circle v-for="(s, i) in stars" :key="i"
			:cx="s[0]" :cy="s[1]" :r="s[2]" fill="#eef3ff" opacity="0.35" />

		<!-- 环境光晕 -->
		<circle :cx="CX" :cy="CY" :r="glowRadius" fill="url(#cglow)" />

		<!-- ═══ 恒星 ═══ -->
		<g v-if="r.category === 'star'">
			<g v-for="i in 4" :key="i" :transform="`rotate(${(i - 1) * 45} ${CX} ${CY})`">
				<rect :x="CX - 2.5" :y="CY - coreRadius * 3" width="5" :height="coreRadius * 6"
					:fill="r.secondaryColor" opacity="0.55" rx="2.5" />
			</g>
			<circle class="pulse" :cx="CX" :cy="CY" :r="coreRadius" fill="url(#cbody)" />
			<circle :cx="CX - coreRadius * 0.3" :cy="CY - coreRadius * 0.35" :r="coreRadius * 0.16"
				fill="#fff" opacity="0.55" />
		</g>

		<!-- ═══ 行星 ═══ -->
		<g v-else-if="r.category === 'planet'">
			<g v-if="r.hasRings">
				<ellipse v-for="i in 3" :key="i" :cx="CX" :cy="CY"
					:rx="coreRadius * (1.7 + i * 0.3)" :ry="coreRadius * (0.5 + i * 0.08)"
					fill="none" :stroke="r.accentColor" :stroke-width="3 - i * 0.6"
					opacity="0.8" :transform="`rotate(${r.ringTilt} ${CX} ${CY})`" />
			</g>
			<circle :cx="CX" :cy="CY" :r="coreRadius" fill="url(#cbody)" />
			<g :clip-path="'url(#ccore)'">
				<rect v-for="(b, i) in bandList" :key="i"
					:x="CX - coreRadius" :y="b.y" :width="coreRadius * 2" :height="b.h"
					:fill="b.color" :opacity="b.o" />
				<circle :cx="CX" :cy="CY" :r="coreRadius" fill="none" :stroke="r.secondaryColor" stroke-width="2" opacity="0.4" />
			</g>
			<ellipse v-for="(sp, i) in SPOT_SLOTS.slice(0, r.spots)" :key="i"
				:cx="sp[0]" :cy="sp[1]" :rx="sp[2]" :ry="sp[3]"
				:fill="r.accentColor" opacity="0.5" />
		</g>

		<!-- ═══ 卫星（月面） ═══ -->
		<g v-else-if="r.category === 'moon'">
			<circle :cx="CX" :cy="CY" :r="coreRadius" fill="url(#cbody)" filter="url(#ctex)" />
			<circle v-for="(c, i) in craters" :key="i"
				:cx="c[0]" :cy="c[1]" :r="c[2]" fill="#0a0f24"
				opacity="0.35 + (i % 3) * 0.12" />
			<circle :cx="CX - coreRadius * 0.28" :cy="CY - coreRadius * 0.3" :r="coreRadius * 0.12"
				fill="#fff" opacity="0.18" />
		</g>

		<!-- ═══ 星系 ═══ -->
		<g v-else-if="r.category === 'galaxy'">
			<path :d="galaxySpiral" fill="none" :stroke="r.primaryColor" stroke-width="7" opacity="0.8" />
			<path :d="galaxySpiral" fill="none" :stroke="r.secondaryColor" stroke-width="3" opacity="0.7" />
			<circle :cx="CX" :cy="CY" :r="coreRadius * 0.35" fill="url(#cglow)" />
			<circle :cx="CX" :cy="CY" :r="coreRadius * 0.16" :fill="r.accentColor" opacity="0.95" />
		</g>

		<!-- ═══ 星云 ═══ -->
		<g v-else-if="r.category === 'nebula'">
			<ellipse v-for="(b, i) in nebulaBlobs" :key="i"
				:cx="b[0]" :cy="b[1]" :rx="b[2]" :ry="b[3]"
				:fill="i % 3 === 0 ? r.primaryColor : i % 3 === 1 ? r.secondaryColor : r.accentColor"
				:opacity="b[4]" filter="url(#cblur)" />
			<circle :cx="CX" :cy="CY" :r="coreRadius * 0.7" :fill="r.primaryColor" opacity="0.4" filter="url(#cblur)" />
			<circle :cx="CX" :cy="CY" :r="coreRadius * 0.35" :fill="r.accentColor" opacity="0.9" />
		</g>

		<!-- ═══ 彗星 / 小天体 ═══ -->
		<g v-else>
			<path :d="cometTail" :fill="r.accentColor" opacity="0.35" />
			<path :d="cometTail" :fill="r.primaryColor" opacity="0.2" :transform="`translate(6 -2)`" />
			<circle :cx="CX" :cy="CY" :r="coreRadius * 0.6" :fill="r.accentColor" />
			<circle :cx="CX - coreRadius * 0.18" :cy="CY - coreRadius * 0.2" :r="coreRadius * 0.16"
				fill="#fff" opacity="0.5" />
		</g>

		<!-- 环绕卫星：等角落在倾斜轨道上（行星/恒星；卫星贴主天体公转面，画在最上层） -->
		<g v-if="satelliteOrbit" :transform="`rotate(${satelliteOrbit.tilt} ${CX} ${CY})`">
			<ellipse :cx="CX" :cy="CY" :rx="satelliteOrbit.rx" :ry="satelliteOrbit.ry"
				fill="none" :stroke="r.accentColor" stroke-width="1.5" opacity="0.22" stroke-dasharray="3 6" />
			<g v-for="(m, i) in satelliteOrbit.dots" :key="i">
				<circle :cx="m.x" :cy="m.y" :r="m.rad" :fill="m.color" />
				<circle :cx="m.x - m.rad * 0.25" :cy="m.y - m.rad * 0.3" :r="m.rad * 0.4" fill="#fff" opacity="0.35" />
			</g>
		</g>

		<!-- 全局尘埃噪点（增强质感） -->
		<circle :cx="CX" :cy="CY" :r="glowRadius" fill="#fff" opacity="0.5"
			filter="url(#cnoise)" :style="{ mixBlendMode: 'screen' }" v-if="r.noise > 0.15" />
	</svg>
</template>

<style scoped>
.celestial-visual {
	display: block;
	width: 100%;
	height: auto;
	background: radial-gradient(ellipse at 50% 40%, rgba(17, 25, 51, 0.6), rgba(4, 7, 18, 0.95));
	border-radius: 18px;
}

/* 恒星呼吸光晕动画 */
.pulse {
	animation: celestial-pulse 3.2s ease-in-out infinite;
	transform-origin: center;
	transform-box: fill-box;
}

@keyframes celestial-pulse {
	0%, 100% {
		opacity: 1;
	}
	50% {
		opacity: 0.82;
	}
}

@media (prefers-reduced-motion: reduce) {
	.pulse {
		animation: none;
	}
}
</style>
