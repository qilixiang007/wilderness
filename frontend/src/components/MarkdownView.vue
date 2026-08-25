<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({ content: { type: String, default: '' } })

const html = computed(() => DOMPurify.sanitize(marked.parse(props.content || '')))
</script>

<template>
	<div class="markdown-body" v-html="html" />
</template>

<style scoped>
.markdown-body {
	font-size: 0.95rem;
	line-height: 1.7;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
	margin: 0.9em 0 0.4em;
	line-height: 1.35;
	color: var(--accent);
}

.markdown-body h1 { font-size: 1.15rem; }
.markdown-body h2 { font-size: 1.05rem; }
.markdown-body h3, .markdown-body h4 { font-size: 0.98rem; }

.markdown-body p {
	margin: 0.45em 0;
}

.markdown-body ul,
.markdown-body ol {
	margin: 0.45em 0;
	padding-left: 1.4em;
}

.markdown-body li {
	margin: 0.2em 0;
}

.markdown-body strong {
	color: var(--accent-strong);
}

.markdown-body code {
	background: var(--button-bg);
	border: 1px solid var(--button-border);
	border-radius: 4px;
	padding: 0.1em 0.35em;
	font-size: 0.88em;
}

.markdown-body blockquote {
	margin: 0.6em 0;
	padding-left: 0.9em;
	border-left: 3px solid var(--button-border);
	color: var(--muted);
}
</style>
