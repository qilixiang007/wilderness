<script setup>
import { pick } from '../i18n'
import { dedupeSourcesBySlug } from '../utils/sources'
import MarkdownView from './MarkdownView.vue'

defineProps({
	item: { type: Object, required: true }
})
</script>

<template>
	<article class="info-card explain-card">
		<h4>{{ item.error ? item.slug : pick(item.zhName, item.enName) }}</h4>
		<p v-if="item.error" class="load-error-text">{{ item.error }}</p>
		<template v-else>
			<MarkdownView :content="item.answer" />
			<p v-if="item.retrievalDegraded" class="retrieval-degraded-hint">
				{{ $t('common.retrievalDegradedHint') }}
			</p>
			<div v-if="item.sources && item.sources.length" class="explain-sources">
				<span class="sources-label">{{ $t('common.sources') }}</span>
				<RouterLink
					v-for="(s, si) in dedupeSourcesBySlug(item.sources)"
					:key="si"
					:to="`/object/${s.slug}`"
					class="source-chip"
					:title="s.excerpt"
				>
					{{ s.title }}<span class="source-type">{{ s.type }}</span>
				</RouterLink>
			</div>
		</template>
	</article>
</template>

<style scoped>
.load-error-text {
	color: var(--muted);
}

.retrieval-degraded-hint {
	color: var(--danger, #e57373);
	font-size: 0.78rem;
	margin-top: 0.6rem;
}

.explain-sources {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 0.4rem;
	margin-top: 0.9rem;
}

.sources-label {
	font-size: 0.78rem;
	letter-spacing: 0.08em;
	text-transform: uppercase;
	color: var(--muted);
	margin: 0 0.25rem 0 0.15rem;
}

.source-chip {
	font-size: 0.82rem;
	color: var(--accent);
	border: 1px solid var(--button-border);
	border-radius: 999px;
	padding: 0.2rem 0.7rem;
	text-decoration: none;
	transition: background 0.2s ease;
}

.source-chip:hover {
	background: var(--panel-glow);
}

.source-type {
	margin-left: 0.35rem;
	font-size: 0.72rem;
	text-transform: uppercase;
	color: var(--muted);
}
</style>
