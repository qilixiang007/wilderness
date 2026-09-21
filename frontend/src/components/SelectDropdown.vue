<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
	modelValue: { type: String, required: true },
	options: { type: Array, required: true },
	ariaLabel: { type: String, required: true },
	getLabel: { type: Function, default: (opt) => opt.label },
	renderItemContent: { type: Function, default: null }
})

const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const root = ref(null)
const buttonEl = ref(null)
const menuEl = ref(null)
const menuStyle = ref({})

const selectedLabel = computed(() => {
	const option = props.options.find((item) => item.value === props.modelValue)
	return option ? props.getLabel(option) : '选择'
})

// 菜单 Teleport 到 body、用 fixed 定位算出来的坐标渲染：
// 页面里 .main-area 自身带 position:relative + z-index，会形成层叠上下文，
// 把内部菜单的 z-index 都"锁"在它下面，导致菜单被 .sidebar（z-index 更高）盖住/裁切。
// Teleport 出去之后菜单直接挂在 body 下，不再受任何祖先层叠上下文影响。
function updatePosition() {
	if (!buttonEl.value) return
	const rect = buttonEl.value.getBoundingClientRect()
	menuStyle.value = {
		top: `${rect.bottom + 6}px`,
		right: `${window.innerWidth - rect.right}px`,
		minWidth: `${rect.width}px`
	}
}

function toggle() {
	if (!open.value) updatePosition()
	open.value = !open.value
}

function select(value) {
	emit('update:modelValue', value)
	open.value = false
}

// 点击面板外任意处关闭（菜单已 Teleport 到 body，要单独判断是否点在菜单内）
function onClickOutside(event) {
	if (!open.value) return
	if (root.value?.contains(event.target)) return
	if (menuEl.value?.contains(event.target)) return
	open.value = false
}

// Esc 关闭
function onKeydown(event) {
	if (event.key === 'Escape') open.value = false
}

function onReposition() {
	if (open.value) updatePosition()
}

onMounted(() => {
	document.addEventListener('click', onClickOutside)
	document.addEventListener('keydown', onKeydown)
	window.addEventListener('resize', onReposition)
	window.addEventListener('scroll', onReposition, true)
})
onBeforeUnmount(() => {
	document.removeEventListener('click', onClickOutside)
	document.removeEventListener('keydown', onKeydown)
	window.removeEventListener('resize', onReposition)
	window.removeEventListener('scroll', onReposition, true)
})
</script>

<template>
	<div ref="root" class="select-wrap">
		<button
			ref="buttonEl"
			class="select-button"
			type="button"
			:aria-haspopup="'listbox'"
			:aria-expanded="open"
			:aria-label="ariaLabel"
			@click="toggle"
		>
			{{ selectedLabel }}
			<span class="chevron" :class="{ 'chevron-open': open }" aria-hidden="true"></span>
		</button>

		<Teleport to="body">
			<Transition name="menu">
				<ul v-if="open" ref="menuEl" class="select-menu" role="listbox" :style="menuStyle">
					<li
						v-for="option in options"
						:key="option.value"
						role="option"
						:aria-selected="option.value === modelValue"
						class="select-menu-item"
						:class="{ active: option.value === modelValue }"
						@click="select(option.value)"
					>
						<slot name="item" :option="option">
							{{ getLabel(option) }}
						</slot>
					</li>
				</ul>
			</Transition>
		</Teleport>
	</div>
</template>

<style scoped>
.select-wrap {
	display: inline-flex;
	position: relative;
}

.select-button {
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

.select-button:hover {
	transform: translateY(-1px);
}

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

.select-menu {
	position: fixed;
	z-index: 200;
	list-style: none;
	margin: 0;
	padding: 5px;
	border-radius: 14px;
	border: 1px solid var(--card-border);
	background: var(--card);
	box-shadow: 0 14px 40px var(--shadow);
}

.select-menu-item {
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

.select-menu-item:hover {
	background: var(--panel-glow);
}

.select-menu-item.active {
	color: var(--accent);
	font-weight: 600;
}

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
