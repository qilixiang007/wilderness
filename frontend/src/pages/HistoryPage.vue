<script setup>
// 对话历史模块（仅当前用户自己的记录）：
// 每次 AI 问答都会落库（MySQL）+ 异步入分析索引，这里按天分组展示，
// 可搜索问题关键词、点开看回答与引文来源、删除单条。
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import MarkdownView from '../components/MarkdownView.vue'
import ExplainResultCard from '../components/ExplainResultCard.vue'
import CelestialVisual from '../components/CelestialVisual.vue'
import { pick } from '../i18n'
import { api, ApiUnavailableError } from '../api'
import { useFavorites } from '../composables/useFavorites'
import { useConfirm } from '../composables/useConfirm'

const { t } = useI18n()
const { confirm } = useConfirm()

const PAGE_SIZE = 20

const activeTab = ref('conversation') // 'conversation' | 'compare' | 'generation'

function switchTab(tab) {
	activeTab.value = tab
	if (tab === 'compare' && !compareLoaded.value) {
		loadCompareHistory(true)
	}
	if (tab === 'generation' && !generationLoaded.value) {
		loadGenerationHistory(true)
	}
}

const items = ref([])
const totalElements = ref(0)
const page = ref(0)
const q = ref('')
const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const expandedId = ref(null)
const deletingId = ref(null)

// 同一篇资料被检索出多个片段时，标题会完全相同——加个"第几段"后缀区分开
function knowledgeSources(item) {
	return (item.sources || []).filter((x) => x.type !== 'web')
}

function sourceLabel(s, item) {
	const dupes = knowledgeSources(item).filter((x) => x.slug === s.slug)
	if (dupes.length <= 1) return s.title
	// 优先用后端给的真实块序号（原文档里的第几块）；老记录没有这个字段时，退回按出现顺序编号
	const index = s.chunkIndex != null ? s.chunkIndex + 1 : dupes.indexOf(s) + 1
	return t('ask.sourceSegment', { title: s.title, index })
}

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
	if (!(await confirm(t('history.deleteConfirm')))) return
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

// —— 对比历史（独立于对话历史的状态；不做关键词搜索，落库/展示逻辑照抄上面这套）——
const compareRecords = ref([])
const compareTotalElements = ref(0)
const comparePage = ref(0)
const compareLoading = ref(false)
const compareLoaded = ref(false)
const compareError = ref('')
const compareExpandedId = ref(null)
const compareDeletingId = ref(null)

const compareHasMore = computed(() => compareRecords.value.length < compareTotalElements.value)

async function loadCompareHistory(reset = false) {
	if (reset) {
		comparePage.value = 0
		compareRecords.value = []
	}
	compareLoading.value = true
	compareError.value = ''
	try {
		const res = await api.getCompareHistory(comparePage.value, PAGE_SIZE)
		if (reset) compareRecords.value = res.items
		else compareRecords.value = [...compareRecords.value, ...res.items]
		compareTotalElements.value = res.totalElements
		compareLoaded.value = true
	} catch (err) {
		compareError.value =
			err instanceof ApiUnavailableError ? t('history.offline') : t('history.loadFailed')
	} finally {
		compareLoading.value = false
	}
}

function loadMoreCompare() {
	comparePage.value += 1
	loadCompareHistory()
}

function toggleCompare(record) {
	compareExpandedId.value = compareExpandedId.value === record.id ? null : record.id
}

async function removeCompare(record) {
	if (compareDeletingId.value) return
	if (!(await confirm(t('history.deleteCompareConfirm')))) return
	compareDeletingId.value = record.id
	try {
		await api.deleteCompareHistory(record.id)
		compareRecords.value = compareRecords.value.filter((r) => r.id !== record.id)
		compareTotalElements.value = Math.max(0, compareTotalElements.value - 1)
	} catch {
		compareError.value = t('history.deleteFailed')
	} finally {
		compareDeletingId.value = null
	}
}

/** 对比历史条目头部标题：把对比的天体名拼起来，失败项用 slug 兜底。 */
function compareTitle(record) {
	return record.items.map((i) => (i.error ? i.slug : pick(i.zhName, i.enName))).join(' · ')
}

const compareGroups = computed(() => {
	const map = new Map()
	for (const record of compareRecords.value) {
		const key = dayKey(record.createdAt)
		if (!map.has(key)) map.set(key, [])
		map.get(key).push(record)
	}
	return [...map.entries()].map(([key, list]) => ({ key, label: dayLabel(key), list }))
})

// —— 天体生成历史（独立于以上两套的状态；列表用摘要字段，展开卡片时才懒加载详情，
// 详情里带完整链路日志——system/user prompt、检索参考资料原文、模型原始返回、LangSmith run id）——
const generationRecords = ref([])
const generationTotalElements = ref(0)
const generationPage = ref(0)
const generationLoading = ref(false)
const generationLoaded = ref(false)
const generationError = ref('')
const generationExpandedId = ref(null)
const generationDeletingId = ref(null)
const generationDetail = ref({}) // id -> detail DTO（懒加载后缓存，避免重复展开重复请求）
const generationDetailLoadingId = ref(null)
const generationLogExpandedId = ref(null) // 详情里"完整链路日志"子折叠区，独立于卡片展开
// 收藏（自建天体）：函数名和本页 toggleGeneration（展开卡片）撞名，导入时重命名
const { isFavoriteGeneration, toggleGeneration: toggleGenerationFavorite } = useFavorites()
const favoritingId = ref(null)
const visibilityTogglingId = ref(null)

const generationHasMore = computed(() => generationRecords.value.length < generationTotalElements.value)

async function loadGenerationHistory(reset = false) {
	if (reset) {
		generationPage.value = 0
		generationRecords.value = []
	}
	generationLoading.value = true
	generationError.value = ''
	try {
		const res = await api.getGenerationHistory(generationPage.value, PAGE_SIZE)
		if (reset) generationRecords.value = res.items
		else generationRecords.value = [...generationRecords.value, ...res.items]
		generationTotalElements.value = res.totalElements
		generationLoaded.value = true
	} catch (err) {
		generationError.value =
			err instanceof ApiUnavailableError ? t('history.offline') : t('history.loadFailed')
	} finally {
		generationLoading.value = false
	}
}

function loadMoreGeneration() {
	generationPage.value += 1
	loadGenerationHistory()
}

async function toggleGeneration(record) {
	if (generationExpandedId.value === record.id) {
		generationExpandedId.value = null
		return
	}
	generationExpandedId.value = record.id
	if (generationDetail.value[record.id]) return
	generationDetailLoadingId.value = record.id
	try {
		const detail = await api.getGenerationHistoryDetail(record.id)
		generationDetail.value = { ...generationDetail.value, [record.id]: detail }
	} catch {
		// 静默失败：详情加载不到就只显示卡片头部，不影响列表本身
	} finally {
		generationDetailLoadingId.value = null
	}
}

function toggleGenerationLog(id) {
	generationLogExpandedId.value = generationLogExpandedId.value === id ? null : id
}

async function onToggleVisibility(record) {
	if (visibilityTogglingId.value) return
	visibilityTogglingId.value = record.id
	try {
		record.isPublic = await api.setGenerationVisibility(record.id, !record.isPublic)
	} catch {
		generationError.value = t('history.generationVisibilityFailed')
	} finally {
		visibilityTogglingId.value = null
	}
}

async function onToggleFavorite(historyId) {
	if (favoritingId.value) return
	favoritingId.value = historyId
	try {
		await toggleGenerationFavorite({ historyId })
	} finally {
		favoritingId.value = null
	}
}

async function removeGeneration(record) {
	if (generationDeletingId.value) return
	if (!(await confirm(t('history.deleteGenerationConfirm')))) return
	generationDeletingId.value = record.id
	try {
		await api.deleteGenerationHistory(record.id)
		generationRecords.value = generationRecords.value.filter((r) => r.id !== record.id)
		generationTotalElements.value = Math.max(0, generationTotalElements.value - 1)
	} catch {
		generationError.value = t('history.deleteFailed')
	} finally {
		generationDeletingId.value = null
	}
}

const generationGroups = computed(() => {
	const map = new Map()
	for (const record of generationRecords.value) {
		const key = dayKey(record.createdAt)
		if (!map.has(key)) map.set(key, [])
		map.get(key).push(record)
	}
	return [...map.entries()].map(([key, list]) => ({ key, label: dayLabel(key), list }))
})

onMounted(() => load(true))
</script>

<template>
	<main>
		<section class="section-block history-section">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('history.kicker') }}</p>
				<h3>{{ $t('history.title') }}</h3>
				<p>
					{{
						activeTab === 'conversation'
							? $t('history.intro')
							: activeTab === 'compare'
								? $t('history.compareIntro')
								: $t('history.generationIntro')
					}}
				</p>
			</div>

			<div class="history-tabs">
				<button
					class="history-tab"
					:class="{ active: activeTab === 'conversation' }"
					type="button"
					@click="switchTab('conversation')"
				>
					{{ $t('history.tabs.conversation') }}
				</button>
				<button
					class="history-tab"
					:class="{ active: activeTab === 'compare' }"
					type="button"
					@click="switchTab('compare')"
				>
					{{ $t('history.tabs.compare') }}
				</button>
				<button
					class="history-tab"
					:class="{ active: activeTab === 'generation' }"
					type="button"
					@click="switchTab('generation')"
				>
					{{ $t('history.tabs.generation') }}
				</button>
			</div>

			<template v-if="activeTab === 'conversation'">
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
									v-for="(s, si) in knowledgeSources(item)"
									:key="si"
									:to="`/object/${s.slug}`"
									class="source-chip"
									:title="s.excerpt"
								>
									{{ sourceLabel(s, item) }}<span class="source-type">{{ s.type }}</span>
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
			</template>

			<template v-else-if="activeTab === 'compare'">
				<p v-if="compareError" class="history-error">{{ compareError }}</p>
				<p v-if="compareLoaded && compareRecords.length === 0 && !compareError" class="history-empty">
					{{ $t('history.compareEmpty') }}
				</p>

				<div v-for="group in compareGroups" :key="group.key" class="history-group">
					<h4 class="history-day">{{ group.label }}</h4>
					<div v-for="record in group.list" :key="record.id" class="history-item">
						<button
							class="history-item-head"
							type="button"
							:disabled="compareDeletingId === record.id"
							@click="toggleCompare(record)"
						>
							<span class="history-question">{{ compareTitle(record) }}</span>
							<span class="history-time">{{ timeOf(record.createdAt) }}</span>
						</button>

						<div v-if="compareExpandedId === record.id" class="history-item-body">
							<MarkdownView v-if="record.overview" :content="record.overview" />
							<p v-else class="history-empty">{{ $t('compare.overviewFailed') }}</p>

							<ExplainResultCard v-for="item in record.items" :key="item.slug" :item="item" />

							<div class="history-actions">
								<button
									class="history-delete"
									type="button"
									:disabled="compareDeletingId === record.id"
									@click="removeCompare(record)"
								>
									{{ $t('history.delete') }}
								</button>
							</div>
						</div>
					</div>
				</div>

				<div v-if="compareHasMore" class="history-more">
					<button class="secondary-button" type="button" :disabled="compareLoading" @click="loadMoreCompare">
						{{ compareLoading ? $t('common.loading') : $t('history.loadMore') }}
					</button>
				</div>
			</template>

			<template v-else>
				<p v-if="generationError" class="history-error">{{ generationError }}</p>
				<p v-if="generationLoaded && generationRecords.length === 0 && !generationError" class="history-empty">
					{{ $t('history.generationEmpty') }}
				</p>

				<div v-for="group in generationGroups" :key="group.key" class="history-group">
					<h4 class="history-day">{{ group.label }}</h4>
					<div v-for="record in group.list" :key="record.id" class="history-item">
						<button
							class="history-item-head"
							type="button"
							:disabled="generationDeletingId === record.id"
							@click="toggleGeneration(record)"
						>
							<span class="history-question">{{ record.name }}</span>
							<span v-if="!record.success" class="history-web generation-fail-badge">
								{{ $t('history.generationFailed') }}
							</span>
							<span class="history-time">{{ timeOf(record.createdAt) }}</span>
						</button>

						<div v-if="generationExpandedId === record.id" class="history-item-body">
							<p v-if="generationDetailLoadingId === record.id" class="history-empty">
								{{ $t('common.loading') }}
							</p>

							<template v-else-if="generationDetail[record.id]">
								<p class="generation-desc">{{ generationDetail[record.id].description }}</p>
								<div class="generation-meta-row">
									<span class="generation-agent-badge">
										{{ $t('history.generationAgentUsed', { name: generationDetail[record.id].agentName || $t('agents.defaultAgent') }) }}
									</span>
									<button class="favorite-toggle" type="button" :disabled="favoritingId === record.id" @click="onToggleFavorite(record.id)">
										{{ isFavoriteGeneration(record.id) ? $t('agent.unfavorite') : $t('agent.favorite') }}
									</button>
									<button
										v-if="generationDetail[record.id].success"
										class="visibility-toggle"
										:class="{ 'is-public': record.isPublic }"
										type="button"
										:disabled="visibilityTogglingId === record.id"
										@click="onToggleVisibility(record)"
									>
										{{ record.isPublic ? $t('history.generationMakePrivate') : $t('history.generationMakePublic') }}
									</button>
								</div>

								<p v-if="!generationDetail[record.id].success" class="generation-fail-notice">
									{{ generationDetail[record.id].errorMessage || $t('history.generationFailed') }}
								</p>

								<div class="generation-visual">
									<img
										v-if="generationDetail[record.id].imageUrl"
										:src="generationDetail[record.id].imageUrl"
										:alt="generationDetail[record.id].name"
										class="ai-image"
									/>
									<CelestialVisual
										v-else
										:render="generationDetail[record.id].render || {}"
										:name="generationDetail[record.id].name"
									/>
								</div>
								<p v-if="!generationDetail[record.id].success" class="generation-image-temporary-hint">
									{{ $t('agent.generationFailedImageHint') }}
								</p>
								<p v-if="generationDetail[record.id].imageUrl && generationDetail[record.id].imageTemporary" class="generation-image-temporary-hint">
									{{ $t('agent.imageTemporaryHint') }}
								</p>

								<dl
									v-if="generationDetail[record.id].parameters && Object.keys(generationDetail[record.id].parameters).length"
									class="facts-list"
								>
									<div v-for="(v, k) in generationDetail[record.id].parameters" :key="k" class="fact-row">
										<dt>{{ k }}</dt>
										<dd>{{ v }}</dd>
									</div>
								</dl>

								<MarkdownView
									v-if="generationDetail[record.id].introduction"
									:content="generationDetail[record.id].introduction"
								/>

								<div
									v-if="generationDetail[record.id].sources && generationDetail[record.id].sources.length"
									class="chat-sources"
								>
									<span class="sources-label">{{ $t('common.sources') }}</span>
									<RouterLink
										v-for="(s, si) in knowledgeSources(generationDetail[record.id])"
										:key="si"
										:to="`/object/${s.slug}`"
										class="source-chip"
										:title="s.excerpt"
									>
										{{ sourceLabel(s, generationDetail[record.id]) }}<span class="source-type">{{ s.type }}</span>
									</RouterLink>
								</div>

								<button class="log-toggle" type="button" @click="toggleGenerationLog(record.id)">
									{{
										generationLogExpandedId === record.id
											? $t('history.generationHideLog')
											: $t('history.generationViewLog')
									}}
								</button>

								<div v-if="generationLogExpandedId === record.id" class="log-block">
									<div class="log-field">
										<span class="log-label">{{ $t('history.generationSystemPrompt') }}</span>
										<pre class="log-pre">{{ generationDetail[record.id].systemPrompt }}</pre>
									</div>
									<div class="log-field">
										<span class="log-label">{{ $t('history.generationUserPrompt') }}</span>
										<pre class="log-pre">{{ generationDetail[record.id].userPrompt }}</pre>
									</div>
									<div v-if="generationDetail[record.id].referenceText" class="log-field">
										<span class="log-label">{{ $t('history.generationReference') }}</span>
										<pre class="log-pre">{{ generationDetail[record.id].referenceText }}</pre>
									</div>
									<div v-if="generationDetail[record.id].rawModelResponse" class="log-field">
										<span class="log-label">{{ $t('history.generationRawResponse') }}</span>
										<pre class="log-pre">{{ generationDetail[record.id].rawModelResponse }}</pre>
									</div>
									<div v-if="generationDetail[record.id].langsmithRunId" class="log-field">
										<span class="log-label">{{ $t('history.generationRunId') }}</span>
										<pre class="log-pre">{{ generationDetail[record.id].langsmithRunId }}</pre>
									</div>
								</div>
							</template>

							<div class="history-actions">
								<button
									class="history-delete"
									type="button"
									:disabled="generationDeletingId === record.id"
									@click="removeGeneration(record)"
								>
									{{ $t('history.delete') }}
								</button>
							</div>
						</div>
					</div>
				</div>

				<div v-if="generationHasMore" class="history-more">
					<button class="secondary-button" type="button" :disabled="generationLoading" @click="loadMoreGeneration">
						{{ generationLoading ? $t('common.loading') : $t('history.loadMore') }}
					</button>
				</div>
			</template>
		</section>
	</main>
</template>

<style scoped>
.history-section {
	max-width: 46rem;
}

.history-tabs {
	display: flex;
	gap: 0.4rem;
	margin-bottom: 1.2rem;
}

.history-tab {
	flex: 1;
	padding: 0.5rem;
	border: 1px solid var(--button-border);
	border-radius: 10px;
	background: var(--button-bg);
	color: var(--text);
	font: inherit;
	cursor: pointer;
	transition: background 0.2s ease;
}

.history-tab.active {
	background: var(--accent);
	border-color: var(--accent);
	color: var(--accent-contrast);
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

/* —— 天体生成历史 tab —— */
.generation-fail-badge {
	color: var(--danger, #e57373);
	border-color: var(--danger, #e57373);
}

.generation-desc {
	color: var(--muted);
	font-size: 0.88rem;
	margin: 0 0 0.4rem;
}

.generation-meta-row {
	display: flex;
	align-items: center;
	gap: 0.5rem;
	margin: 0 0 0.8rem;
}

.generation-agent-badge {
	display: inline-block;
	font-size: 0.72rem;
	color: var(--accent);
	border: 1px solid var(--button-border);
	border-radius: 999px;
	padding: 0.1rem 0.55rem;
}

.favorite-toggle {
	padding: 0.2rem 0.7rem;
	border: 1px solid var(--button-border);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--accent);
	font: inherit;
	font-size: 0.72rem;
	cursor: pointer;
	transition: background 0.2s ease;
}

.favorite-toggle:hover {
	background: var(--panel-glow);
}

.favorite-toggle:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

.visibility-toggle {
	padding: 0.2rem 0.7rem;
	border: 1px solid var(--button-border);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--muted);
	font: inherit;
	font-size: 0.72rem;
	cursor: pointer;
	transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

.visibility-toggle:hover {
	background: var(--panel-glow);
}

.visibility-toggle.is-public {
	color: var(--accent);
	border-color: var(--accent);
}

.visibility-toggle:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

.generation-fail-notice {
	color: var(--danger, #e57373);
	font-size: 0.85rem;
	margin: 0 0 0.8rem;
}

.generation-image-temporary-hint {
	color: var(--danger, #e57373);
	font-size: 0.78rem;
	text-align: center;
	margin: 0.6rem 0 0;
}

.generation-visual {
	max-width: 320px;
	margin: 0 auto 1rem;
}

.ai-image {
	display: block;
	width: 100%;
	height: auto;
	border-radius: 18px;
	border: 1px solid var(--card-border);
}

.log-toggle {
	margin-top: 0.8rem;
	padding: 0.35rem 0.8rem;
	border: 1px solid var(--button-border);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--accent);
	font: inherit;
	font-size: 0.8rem;
	cursor: pointer;
	transition: background 0.2s ease;
}

.log-toggle:hover {
	background: var(--panel-glow);
}

.log-block {
	display: flex;
	flex-direction: column;
	gap: 0.7rem;
	margin-top: 0.7rem;
	padding-top: 0.7rem;
	border-top: 1px dashed var(--card-border);
}

.log-field {
	display: flex;
	flex-direction: column;
	gap: 0.3rem;
}

.log-label {
	font-size: 0.75rem;
	letter-spacing: 0.06em;
	text-transform: uppercase;
	color: var(--muted);
}

.log-pre {
	margin: 0;
	max-height: 220px;
	overflow: auto;
	padding: 0.7rem 0.85rem;
	border-radius: 10px;
	border: 1px solid var(--card-border);
	background: var(--panel-glow);
	font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
	font-size: 0.78rem;
	line-height: 1.5;
	white-space: pre-wrap;
	word-break: break-word;
}
</style>
