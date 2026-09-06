<script setup>
import { pick } from '../i18n'
import { useCompare, COMPARE_MAX } from '../composables/useCompare'

const props = defineProps({
	object: { type: Object, required: true },
	selectable: { type: Boolean, default: false },
	selected: { type: Boolean, default: false },
	showDetails: { type: Boolean, default: true }
})

const { count, toggle } = useCompare()

function onToggleCompare() {
	toggle(props.object)
}
</script>

<template>
	<article class="info-card object-card" :class="{ 'object-card-selected': selectable && selected }">
		<div class="object-visual">
			<img :src="object.image" :alt="pick(object.zhName, object.enName)" />
			<button
				v-if="selectable"
				type="button"
				class="compare-badge"
				:class="{ 'compare-badge-active': selected }"
				:disabled="!selected && count >= COMPARE_MAX"
				:title="selected ? $t('compare.remove') : $t('compare.add')"
				:aria-label="selected ? $t('compare.remove') : $t('compare.add')"
				@click.stop="onToggleCompare"
			>
				<span class="compare-badge-icon">{{ selected ? '✓' : '+' }}</span>
				<span class="compare-badge-label">{{ selected ? $t('compare.selectedBadge') : $t('compare.addBadge') }}</span>
			</button>
		</div>
		<div class="object-copy">
			<h4>{{ pick(object.zhName, object.enName) }}</h4>
			<p v-if="object.zhDescription || object.enDescription">{{ pick(object.zhDescription, object.enDescription) }}</p>
			<slot name="actions" :object="object">
				<RouterLink v-if="showDetails" class="secondary-button" :to="`/object/${object.slug}`">{{ $t('common.viewDetails') }}</RouterLink>
			</slot>
		</div>
	</article>
</template>

<style scoped>
.object-visual {
	position: relative;
}

/* 角标浮在任意天体照片上（深空黑到土星的浅色背景都有），不能用跟随主题背景的
   低透明度按钮样式（那套假设是垫在页面自身底色上，垫图片时对比度随图片内容漂移，
   四张卡片看起来就会像样式不统一）。固定用不透明深色底+白字，任何图片上都读得清。 */
.compare-badge {
	position: absolute;
	top: 8px;
	right: 8px;
	height: 28px;
	padding: 0 10px 0 8px;
	border-radius: 999px;
	border: 1px solid rgba(255, 255, 255, 0.55);
	background: rgba(10, 12, 20, 0.6);
	color: #fff;
	cursor: pointer;
	font-size: 0.78rem;
	font-weight: 600;
	line-height: 1;
	white-space: nowrap;
	display: flex;
	align-items: center;
	gap: 4px;
	transition: background 0.2s ease, color 0.2s ease;
	backdrop-filter: blur(2px);
}

.compare-badge-icon {
	font-size: 0.9rem;
}

.compare-badge:hover {
	background: rgba(10, 12, 20, 0.75);
}

.compare-badge:disabled {
	opacity: 0.4;
	cursor: not-allowed;
}

.compare-badge-active {
	background: var(--accent);
	color: var(--accent-contrast);
	border-color: var(--accent);
}

.object-card-selected {
	outline: 2px solid var(--accent);
	outline-offset: -2px;
}
</style>
