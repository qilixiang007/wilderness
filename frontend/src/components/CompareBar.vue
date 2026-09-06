<script setup>
import { useRoute, useRouter } from 'vue-router'
import { pick } from '../i18n'
import { useCompare, COMPARE_MAX } from '../composables/useCompare'

const route = useRoute()
const router = useRouter()
const { selected, count, toggle, clear } = useCompare()

function remove(item) {
	toggle(item)
}

function start() {
	router.push({ path: '/compare', query: { slugs: selected.value.map((s) => s.slug).join(',') } })
}
</script>

<template>
	<div v-if="count > 0 && route.path !== '/compare'" class="compare-bar">
		<span class="compare-count">{{ $t('compare.selectedCount', { n: count, max: COMPARE_MAX }) }}</span>
		<div class="compare-chips">
			<span v-for="item in selected" :key="item.slug" class="compare-chip">
				<img :src="item.image" :alt="pick(item.zhName, item.enName)" />
				<span class="compare-chip-name">{{ pick(item.zhName, item.enName) }}</span>
				<button type="button" class="compare-chip-remove" :aria-label="$t('compare.remove')" @click="remove(item)">×</button>
			</span>
		</div>
		<div class="compare-bar-actions">
			<button type="button" class="secondary-button" :disabled="count < 2" @click="start">
				{{ $t('compare.start') }}
			</button>
			<button type="button" class="compare-bar-clear" @click="clear">{{ $t('compare.clear') }}</button>
		</div>
	</div>
</template>

<style scoped>
.compare-bar {
	position: fixed;
	left: 0;
	right: 0;
	bottom: 0;
	z-index: 40;
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 12px;
	padding: 10px 20px;
	background: var(--panel-bg, var(--bg));
	border-top: 1px solid var(--button-border);
	box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.15);
}

.compare-count {
	font-size: 0.85rem;
	color: var(--muted);
	white-space: nowrap;
}

.compare-chips {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	flex: 1 1 auto;
}

.compare-chip {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	padding: 4px 8px 4px 4px;
	border-radius: 999px;
	border: 1px solid var(--button-border);
	background: var(--button-bg);
}

.compare-chip img {
	width: 22px;
	height: 22px;
	border-radius: 50%;
	object-fit: cover;
}

.compare-chip-name {
	font-size: 0.82rem;
	max-width: 8rem;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.compare-chip-remove {
	border: none;
	background: none;
	color: var(--muted);
	cursor: pointer;
	font-size: 0.9rem;
	line-height: 1;
	padding: 0 2px;
}

.compare-chip-remove:hover {
	color: var(--text);
}

.compare-bar-actions {
	display: flex;
	align-items: center;
	gap: 10px;
}

.compare-bar-clear {
	background: none;
	border: none;
	color: var(--muted);
	cursor: pointer;
	font-size: 0.82rem;
	text-decoration: underline;
}

.compare-bar-clear:hover {
	color: var(--text);
}
</style>
