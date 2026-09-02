<script setup>
// 对话历史模块（仅当前用户自己的记录）：
// 每次 AI 问答都会落库（MySQL）+ 异步入分析索引，这里按天分组展示，
// 可搜索问题关键词、点开看回答与引文来源、删除单条。
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import MarkdownView from '../components/MarkdownView.vue'
import { api, ApiUnavailableError } from '../api'

const { t } = useI18n()

const PAGE_SIZE = 20

const items = ref([])
const totalElements = ref(0)
const page = ref(0)
const q = ref('')
const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const expandedId = ref(null)
const deletingId = ref(null)

const hasMore = computed(() => items.value.length < totalElements.value)

async function load(reset = false) {
	if (reset) {
		page.value = 0
		items.value = []
	}
	loading.value = true
	error.value = ''
	try {
		const res = await api.getHistory(page.value, PAGE_SIZE, q.value)
		if (reset) items.value = res.items
		else items.value = [...items.value, ...res.items]
		totalElements.value = res.totalElements
		loaded.value = true
	} catch (err) {
		error.value =
			err instanceof ApiUnavailableError
				? t('history.offline')
				: t('history.loadFailed')
	} finally {
		loading.value = false
	}
}

function loadMore() {
	page.value += 1
	load()
}

function onSearch() {
	load(true)
}

function toggle(item) {
	expandedId.value = expandedId.value === item.id ? null : item.id
}

async function remove(item) {
	if (deletingId.value) return
	deletingId.value = item.id
	try {
		await api.deleteHistory(item.id)
		items.value = items.value.filter((i) => i.id !== item.id)
		totalElements.value = Math.max(0, totalElements.value - 1)
	} catch {
		error.value = t('history.deleteFailed')
	} finally {
		deletingId.value = null
	}
}

// —— 按天分组 ——
function dayKey(iso) {
	const d = new Date(iso)
	const m = String(d.getMonth() + 1).padStart(2, '0')
	const day = String(d.getDate()).padStart(2, '0')
	return `${d.getFullYear()}-${m}-${day}`
}

function dayLabel(key) {
	const today = dayKey(new Date())
	const yesterday = dayKey(new Date(Date.now() - 86400000))
	if (key === today) return t('history.groupToday')
	if (key === yesterday) return t('history.groupYesterday')
	return key
}

const groups = computed(() => {
	const map = new Map()
	for (const item of items.value) {
		const key = dayKey(item.createdAt)
		if (!map.has(key)) map.set(key, [])
		map.get(key).push(item)
	}
	return [...map.entries()].map(([key, list]) => ({ key, label: dayLabel(key), list }))
})

function timeOf(iso) {
	const d = new Date(iso)
	return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(() => load(true))
</script>

<template>
	<main>
		<section class="section-block history-section">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('history.kicker') }}</p>
				<h3>{{ $t('history.title') }}</h3>
				<p>{{ $t('history.intro') }}</p>
			</div>

			<form class="history-search" @submit.prevent="onSearch">
				<input
					v-model="q"
					:placeholder="$t('history.searchPlaceholder')"
					:disabled="loading"
				/>
				<button class="primary-button" type="submit" :disabled="loading">
					{{ $t('history.search') }}
				</button>
			</form>

			<p v-if="error" class="history-error">{{ error }}</p>
			<p v-if="loaded && items.length === 0 && !error" class="history-empty">
				{{ $t('history.empty') }}
			</p>

			<div v-for="group in groups" :key="group.key" class="history-group">
				<h4 class="history-day">{{ group.label }}</h4>
				<div v-for="item in group.list" :key="item.id" class="history-item">
					<button class="history-item-head" type="button" :disabled="deletingId === item.id" @click="toggle(item)">
						<span class="history-question">{{ item.question }}</span>
						<span v-if="item.webEnabled" class="history-web">{{ $t('history.webSearch') }}</span>
						<span class="history-time">{{ timeOf(item.createdAt) }}</span>
					</button>

					<div v-if="expandedId === item.id" class="history-item-body">
						<MarkdownView :content="item.answer" />

						<div v-if="item.sources && item.sources.length" class="chat-sources">
							<span class="sources-label">{{ $t('common.sources') }}</span>
							<a
								v-for="(s, si) in item.sources.filter((x) => x.type === 'web')"
								:key="si"
								:href="s.slug"
								target="_blank"
								rel="noopener"
								class="source-chip"
								:title="s.excerpt"
							>
								{{ s.title }}<span class="source-type">{{ s.type }}</span>
							</a>
							<RouterLink
								v-for="(s, si) in item.sources.filter((x) => x.type !== 'web')"
								:key="si"
								:to="`/object/${s.slug}`"
								class="source-chip"
								:title="s.excerpt"
							>
								{{ s.title }}<span class="source-type">{{ s.type }}</span>
							</RouterLink>
						</div>

						<div class="history-actions">
							<button class="history-delete" type="button" :disabled="deletingId === item.id" @click="remove(item)">
								{{ $t('history.delete') }}
							</button>
						</div>
					</div>
				</div>
			</div>

			<div v-if="hasMore" class="history-more">
				<button class="secondary-button" type="button" :disabled="loading" @click="loadMore">
					{{ loading ? $t('common.loading') : $t('history.loadMore') }}
				</button>
			</div>
		</section>
	</main>
</template>

<style scoped>
.history-section {
	max-width: 46rem;
}

.history-search {
	display: flex;
	gap: 0.6rem;
	margin-bottom: 1.2rem;
}

.history-search input {
	flex: 1;
	padding: 0.65rem 0.9rem;
	border-radius: 10px;
	border: 1px solid var(--button-border);
	background: var(--card);
	color: var(--text);
	font: inherit;
	outline: none;
}

.history-search input:focus {
	border-color: var(--accent);
}

.history-error {
	color: var(--danger, #e57373);
	font-size: 0.85rem;
}

.history-empty {
	color: var(--muted);
	font-size: 0.9rem;
	margin: 1.5rem 0;
}

.history-group {
	margin-bottom: 1.4rem;
}

.history-day {
	font-family: var(--font-display);
	font-size: 0.85rem;
	letter-spacing: 0.06em;
	text-transform: uppercase;
	color: var(--muted);
	margin: 0 0 0.5rem;
}

.history-item {
	border: 1px solid var(--card-border);
	border-radius: 12px;
	background: var(--card);
	margin-bottom: 0.6rem;
	overflow: hidden;
}

.history-item-head {
	display: flex;
	align-items: center;
	gap: 0.6rem;
	width: 100%;
	padding: 0.7rem 0.95rem;
	border: none;
	background: none;
	color: var(--text);
	font: inherit;
	font-size: 0.92rem;
	text-align: left;
	cursor: pointer;
}

.history-item-head:hover {
	background: var(--panel-glow);
}

.history-item-head:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

.history-question {
	flex: 1;
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.history-web {
	flex-shrink: 0;
	font-size: 0.7rem;
	letter-spacing: 0.04em;
	color: var(--accent);
	border: 1px solid var(--button-border);
	border-radius: 999px;
	padding: 0.1rem 0.55rem;
}

.history-time {
	flex-shrink: 0;
	font-size: 0.78rem;
	color: var(--muted);
}

.history-item-body {
	padding: 0.4rem 0.95rem 0.95rem;
	border-top: 1px solid var(--card-border);
}

.history-actions {
	margin-top: 0.8rem;
}

.history-delete {
	padding: 0.25rem 0.7rem;
	border: 1px solid var(--button-border);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--muted);
	font: inherit;
	font-size: 0.78rem;
	cursor: pointer;
	transition: background 0.2s ease, color 0.2s ease;
}

.history-delete:hover {
	background: var(--panel-glow);
	color: var(--danger, #e57373);
}

.history-delete:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

.history-more {
	display: flex;
	justify-content: center;
	margin-top: 1rem;
}

/* 来源 chips（与 AskPage 同款） */
.chat-sources {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 0.4rem;
	margin-top: 0.6rem;
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
