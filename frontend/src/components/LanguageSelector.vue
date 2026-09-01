<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
	modelValue: { type: String, required: true }
})

const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const root = ref(null)

const languageOptions = [
	{ value: 'zh', label: '中文' },
	{ value: 'en', label: 'English' }
]

const selectedLabel = computed(() => {
	const option = languageOptions.find((item) => item.value === props.modelValue)
	return option ? option.label : '中文'
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
	<div ref="root" class="language-select-wrap" @keydown.esc="onKeydown">
		<button
			class="language-select"
			type="button"
			:aria-haspopup="'listbox'"
			:aria-expanded="open"
			:aria-label="'Language switcher'"
			@click="toggle"
		>
			{{ selectedLabel }}
			<span class="chevron" :class="{ 'chevron-open': open }" aria-hidden="true"></span>
		</button>

		<Transition name="menu">
			<ul v-if="open" class="language-menu" role="listbox">
				<li
					v-for="option in languageOptions"
					:key="option.value"
					role="option"
					:aria-selected="option.value === modelValue"
					class="language-menu-item"
					:class="{ active: option.value === modelValue }"
					@click="select(option.value)"
				>
					{{ option.label }}
				</li>
			</ul>
		</Transition>
	</div>
</template>

<style scoped>
.language-select-wrap {
	display: inline-flex;
	position: relative;
}

/* 触发按钮：与主题选择器同尺寸同圆角 */
.language-select {
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

.language-select:hover {
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
.language-menu {
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

.language-menu-item {
	display: flex;
	align-items: center;
	padding: 8px 12px;
	border-radius: 10px;
	font-size: 0.88rem;
	color: var(--text);
	white-space: nowrap;
	cursor: pointer;
	transition: background 160ms ease, color 160ms ease;
}

.language-menu-item:hover {
	background: var(--panel-glow);
}

.language-menu-item.active {
	color: var(--accent);
	font-weight: 600;
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
