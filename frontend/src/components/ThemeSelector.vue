<script setup>
// 主题切换下拉（自绘）：原生 <select> 的展开面板是浏览器系统样式，无法圆润，
// 这里换成圆润胶囊按钮 + 自绘圆角弹出列表，贴合项目 UI。
// 触发按钮与顶栏语言切换按钮同尺寸；弹出列表支持点击外部/Esc 关闭。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { themeOptions } from '../theme/themeOptions'
import { pick } from '../i18n'

const props = defineProps({
	modelValue: { type: String, required: true }
})

const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const root = ref(null)

const selectedLabel = computed(() => {
	const option = themeOptions.find((item) => item.value === props.modelValue)
	if (!option) return pick('深邃风', 'Deep Space')
	return pick(option.label.zh, option.label.en)
})

function toggle() {
	open.value = !open.value
}

function select(value) {
	emit('update:modelValue', value)
	open.value = false
}

// 点击面板外任意处关闭
function onClickOutside(event) {
	if (open.value && root.value && !root.value.contains(event.target)) {
		open.value = false
	}
}

// Esc 关闭
function onKeydown(event) {
	if (event.key === 'Escape') open.value = false
}

onMounted(() => document.addEventListener('click', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
	<div ref="root" class="theme-select-wrap" @keydown.esc="onKeydown">
		<button
			class="theme-select"
			type="button"
			:aria-haspopup="'listbox'"
			:aria-expanded="open"
			:aria-label="$t('theme.switcher')"
			@click="toggle"
		>
			{{ selectedLabel }}
			<span class="chevron" :class="{ 'chevron-open': open }" aria-hidden="true"></span>
		</button>

		<Transition name="menu">
			<ul v-if="open" class="theme-menu" role="listbox">
				<li
					v-for="option in themeOptions"
					:key="option.value"
					role="option"
					:aria-selected="option.value === modelValue"
					class="theme-menu-item"
					:class="{ active: option.value === modelValue }"
					@click="select(option.value)"
				>
					<span class="theme-dot" :style="{ background: option.color }" aria-hidden="true"></span>
					{{ pick(option.label.zh, option.label.en) }}
				</li>
			</ul>
		</Transition>
	</div>
</template>

<style scoped>
.theme-select-wrap {
	display: inline-flex;
	position: relative;
}

/* 触发按钮：与顶栏语言切换按钮同尺寸同圆角（全局 .language-toggle 规格） */
.theme-select {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	gap: 8px;
	min-height: 46px;
	padding: 0 18px;
	border-radius: 999px;
	border: 1px solid var(--button-border);
	background: var(--button-bg);
	color: var(--text);
	font: inherit;
	font-size: 0.9rem;
	cursor: pointer;
	transition: transform 160ms ease, border-color 160ms ease, background 160ms ease;
}

.theme-select:hover {
	transform: translateY(-1px);
}

/* 自绘下拉箭头：CSS 三角形 + 开合旋转 */
.chevron {
	width: 0;
	height: 0;
	border-left: 5px solid transparent;
	border-right: 5px solid transparent;
	border-top: 6px solid var(--muted);
	transition: transform 160ms ease;
}

.chevron-open {
	transform: rotate(180deg);
}

/* 圆润弹出列表：右缘对齐按钮，避免超出视口 */
.theme-menu {
	position: absolute;
	top: calc(100% + 6px);
	right: 0;
	z-index: 50;
	min-width: 100%;
	list-style: none;
	margin: 0;
	padding: 5px;
	border-radius: 14px;
	border: 1px solid var(--card-border);
	background: var(--card);
	box-shadow: 0 14px 40px var(--shadow);
}

.theme-menu-item {
	display: flex;
	align-items: center;
	gap: 8px;
	padding: 8px 12px;
	border-radius: 10px;
	font-size: 0.88rem;
	color: var(--text);
	white-space: nowrap;
	cursor: pointer;
	transition: background 160ms ease, color 160ms ease;
}

.theme-menu-item:hover {
	background: var(--panel-glow);
}

.theme-menu-item.active {
	color: var(--accent);
	font-weight: 600;
}

.theme-dot {
	width: 10px;
	height: 10px;
	flex-shrink: 0;
	border-radius: 50%;
}

/* 淡入 + 轻微上移的下滑动画 */
.menu-enter-active,
.menu-leave-active {
	transition: opacity 160ms ease, transform 160ms ease;
}

.menu-enter-from,
.menu-leave-to {
	opacity: 0;
	transform: translateY(-4px);
}
</style>
