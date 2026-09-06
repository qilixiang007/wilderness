<script setup>
import { useCompareHint } from '../composables/useCompareHint'
import { COMPARE_MAX } from '../composables/useCompare'

const { show, dismiss } = useCompareHint()
</script>

<template>
	<p v-if="show" class="compare-hint" role="status">
		<span>{{ $t('compare.hint', { max: COMPARE_MAX }) }}</span>
		<button type="button" class="compare-hint-close" :aria-label="$t('compare.hintDismiss')" @click="dismiss">×</button>
	</p>
</template>

<style scoped>
.compare-hint {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 12px;
	margin: 0 0 18px;
	padding: 12px 16px;
	border-radius: 14px;
	border: 1px dashed var(--button-border);
	background: var(--button-bg);
	color: var(--muted);
}

.compare-hint-close {
	flex: none;
	border: none;
	background: none;
	color: var(--muted);
	cursor: pointer;
	font-size: 1.1rem;
	line-height: 1;
	padding: 0 2px;
}

.compare-hint-close:hover {
	color: var(--text);
}
</style>
