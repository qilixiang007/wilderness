<script setup>
// ECharts 的薄封装：按需注册模块（只打包柱状、折线和基础组件），
// 配置由调用方的 build(tokens) 生成——tokens 是从 CSS 变量读出的当前主题配色，
// 所以切换主题时重新读取并重绘，图表与页面始终同一套颜色。
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
	build: { type: Function, required: true },
	// 数据变化时重绘：传入图表依赖的数据引用
	data: { type: null, default: null },
	ariaLabel: { type: String, required: true },
	height: { type: Number, default: 240 }
})

const root = ref(null)
let chart = null
let resizeObserver = null
let themeObserver = null

/** 从 CSS 变量读出当前主题下的配色与文字色。 */
function readTokens() {
	const style = getComputedStyle(root.value)
	const v = (name) => style.getPropertyValue(name).trim()
	return {
		series1: v('--viz-series-1'),
		series2: v('--viz-series-2'),
		critical: v('--viz-critical'),
		grid: v('--viz-grid'),
		axis: v('--viz-axis'),
		surface: v('--viz-surface'),
		text: v('--text'),
		muted: v('--muted'),
		card: v('--card')
	}
}

function render() {
	if (!chart) return
	chart.setOption(props.build(readTokens()), true)
}

onMounted(() => {
	chart = init(root.value, null, { renderer: 'canvas' })
	render()
	resizeObserver = new ResizeObserver(() => chart?.resize())
	resizeObserver.observe(root.value)
	// 主题挂在外层 .page-shell 的 data-theme 上，属性变化即重读 CSS 变量重绘
	const shell = root.value.closest('.page-shell')
	if (shell) {
		themeObserver = new MutationObserver(() => nextTick(render))
		themeObserver.observe(shell, { attributes: true, attributeFilter: ['data-theme'] })
	}
})

watch(() => props.data, render, { deep: false })

onBeforeUnmount(() => {
	resizeObserver?.disconnect()
	themeObserver?.disconnect()
	chart?.dispose()
	chart = null
})
</script>

<template>
	<div ref="root" class="trace-chart trace-viz" role="img" :aria-label="ariaLabel" :style="{ height: `${height}px` }" />
</template>

<style scoped>
.trace-chart {
	width: 100%;
}
</style>
