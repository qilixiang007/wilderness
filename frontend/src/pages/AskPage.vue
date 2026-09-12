<script setup>
import { computed, nextTick, onActivated, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, openChatStream } from '../api'
import MarkdownView from '../components/MarkdownView.vue'
import { useAuth } from '../composables/useAuth'

defineOptions({ name: 'AskPage' })

const { t, tm } = useI18n()
const { user, isLoggedIn } = useAuth()

// —— 试试这样问：题库随机抽 3 条，"换一批"从剩余问题里抽，不与当前显示的重复 ——
const allSuggestions = computed(() => tm('ask.suggestions'))
const shownSuggestions = ref([])

function pickSuggestions() {
	const pool = allSuggestions.value.filter((q) => !shownSuggestions.value.includes(q))
	// 剩余问题不够 3 条(题库快抽完一轮)时,从全量题库里补,允许再次出现
	const source = pool.length >= 3 ? pool : allSuggestions.value
	const picked = []
	const candidates = [...source]
	while (picked.length < 3 && candidates.length > 0) {
		const i = Math.floor(Math.random() * candidates.length)
		picked.push(candidates.splice(i, 1)[0])
	}
	shownSuggestions.value = picked
}

pickSuggestions()
// 切换语言时题库文本跟着变(zh.js/en.js 各一份)，已展示的旧语言文案要重新抽一批新语言的
watch(allSuggestions, pickSuggestions)

// 同一篇资料被检索出多个片段时，标题会完全相同——加个"第几段"后缀区分开
function knowledgeSources(msg) {
	return (msg.sources || []).filter((x) => x.type !== 'web')
}

function sourceLabel(s, msg) {
	const dupes = knowledgeSources(msg).filter((x) => x.slug === s.slug)
	if (dupes.length <= 1) return s.title
	// 优先用后端给的真实块序号（原文档里的第几块）；老记录没有这个字段时，退回按出现顺序编号
	const index = s.chunkIndex != null ? s.chunkIndex + 1 : dupes.indexOf(s) + 1
	return t('ask.sourceSegment', { title: s.title, index })
}

// —— 最近历史预览：仅登录用户可见，默认 3 条，"查看更多"按 3 条递增 ——
const RECENT_STEP = 3
const recentHistory = ref([])
const recentTotal = ref(0)
const recentSize = ref(RECENT_STEP)
const recentLoading = ref(false)
const recentExpandedId = ref(null)

const recentHasMore = computed(() => recentHistory.value.length < recentTotal.value)
// 倒序展示：最老的（当前已加载范围内）在上，最新的在下——挨着"新对话"/输入框，
// 跟"查看更多"往上加载更早记录的方向一致，读起来像聊天记录往下滚到最新。
const recentHistoryDisplay = computed(() => [...recentHistory.value].reverse())

async function loadRecentHistory() {
	if (!isLoggedIn.value) return
	recentLoading.value = true
	try {
		const res = await api.getHistory(0, recentSize.value, '')
		recentHistory.value = res.items
		recentTotal.value = res.totalElements
		// 默认展开最近一条，避免一进来看到的全是折叠标题——还得点一下才能看到内容。
		// 只在当前没有任何展开项时才这么做，不覆盖用户自己手动收起/展开的选择。
		if (recentExpandedId.value == null && recentHistory.value.length) {
			recentExpandedId.value = recentHistory.value[0].id
		}
	} catch {
		// 静默失败：这是锦上添花的预览区块，出错就不显示，不打扰主问答流程
		recentHistory.value = []
		recentTotal.value = 0
	} finally {
		recentLoading.value = false
	}
}

function loadMoreRecent() {
	recentSize.value += RECENT_STEP
	loadRecentHistory()
}

function toggleRecent(item) {
	recentExpandedId.value = recentExpandedId.value === item.id ? null : item.id
}

onMounted(() => {
	loadRecentHistory()
})

// KeepAlive 下 onMounted 只会在首次创建时触发一次，之后每次切回来都是"激活"而不是重新挂载，
// 不会自动刷新历史；最近历史预览常驻展示（不只在空状态才显示），所以每次激活都重新拉一次。
onActivated(() => {
	loadRecentHistory()
})

const messages = ref([]) // { role: 'user' | 'assistant', content, sources }
const input = ref('')
const streaming = ref(false)
const webSearch = ref(false)
const chatBox = ref(null)
let currentStream = null // 当前 EventSource，供"新对话"/登出清理主动关闭

async function scrollToBottom() {
	await nextTick()
	if (chatBox.value) {
		chatBox.value.scrollTop = chatBox.value.scrollHeight
	}
}

function send() {
	const question = input.value.trim()
	if (!question || streaming.value) return
	input.value = ''
	const userMsg = { role: 'user', content: question }
	messages.value.push(userMsg, { role: 'assistant', content: '', sources: [], retrievalDegraded: false })
	// 取数组里那份响应式代理，而不是 push 前的裸对象引用，
	// 否则后面对它的赋值不会被 Vue 追踪到，界面要等流结束才会一次性刷新。
	const assistantMsg = messages.value[messages.value.length - 1]
	streaming.value = true
	scrollToBottom()

	currentStream = openChatStream(question, {
		webEnabled: webSearch.value,
		onRetrievalDegraded: () => {
			assistantMsg.retrievalDegraded = true
		},
		onSources: (sources) => {
			assistantMsg.sources = sources
		},
		onDelta: (delta) => {
			assistantMsg.content += delta
			scrollToBottom()
		},
		onDone: () => {
			streaming.value = false
			currentStream = null
		}
	})
}

// "新对话"：关闭进行中的流（若有），清空当前会话。未完成的回答不会被后端记录
// （历史只在自然说完时落库），这是预期行为。
function newConversation() {
	currentStream?.close()
	currentStream = null
	streaming.value = false
	messages.value = []
	input.value = ''
}

// 登出/切换账号：清空本页会话状态和最近历史，避免同一 tab 下一个登录用户看到上一个用户的对话
watch(() => user.value?.id, (newId) => {
	currentStream?.close()
	currentStream = null
	streaming.value = false
	messages.value = []
	input.value = ''
	recentHistory.value = []
	recentTotal.value = 0
	recentSize.value = RECENT_STEP
	recentExpandedId.value = null
	if (newId != null) loadRecentHistory() // 换了个已登录用户：拉取属于新用户的历史预览
})
</script>

<template>
	<main>
		<section class="section-block ask-section">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('ask.kicker') }}</p>
				<h3>{{ $t('ask.title') }}</h3>
				<p>{{ $t('ask.intro') }}</p>
			</div>

			<!-- 最近历史：无论是否已有进行中的对话都常驻展示，不随聊天开始而收起 -->
			<div v-if="isLoggedIn && recentHistory.length" class="ask-recent">
				<p class="ask-recent-label">{{ $t('ask.recentHistory') }}</p>
				<div v-if="recentHasMore" class="history-more">
					<button
						class="secondary-button"
						type="button"
						:disabled="recentLoading"
						@click="loadMoreRecent"
					>
						{{ recentLoading ? $t('common.loading') : $t('ask.viewMoreHistory') }}
					</button>
				</div>
				<div class="history-item" v-for="item in recentHistoryDisplay" :key="item.id">
					<button class="history-item-head" type="button" @click="toggleRecent(item)">
						<span class="history-question">{{ item.question }}</span>
						<span class="history-time">{{ new Date(item.createdAt).toLocaleDateString() }}</span>
					</button>
					<div v-if="recentExpandedId === item.id" class="history-item-body">
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
					</div>
				</div>
			</div>

			<div v-if="messages.length > 0" class="ask-toolbar">
				<button class="secondary-button" type="button" @click="newConversation">
					{{ $t('ask.newConversation') }}
				</button>
			</div>

			<!-- "试试这样问"常驻展示在上方，不随对话开始/进行中而收起 -->
			<div class="ask-try">
				<div class="ask-try-heading">
					<p>{{ $t('ask.tryAsking') }}</p>
					<button
						class="shuffle-button"
						type="button"
						:title="$t('ask.shuffleSuggestions')"
						:aria-label="$t('ask.shuffleSuggestions')"
						@click="pickSuggestions"
					>
						<svg viewBox="0 0 20 20" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.6">
							<path d="M17 10a7 7 0 1 1-2.3-5.2M17 3v4h-4" stroke-linecap="round" stroke-linejoin="round" />
						</svg>
					</button>
				</div>
				<div class="ask-suggestions">
					<button
						v-for="q in shownSuggestions"
						:key="q"
						class="suggestion-chip"
						type="button"
						@click="input = q"
					>
						{{ q }}
					</button>
				</div>
			</div>

			<div ref="chatBox" class="chat-box">
				<div v-for="(msg, i) in messages" :key="i" class="chat-message" :class="msg.role">
					<div class="chat-bubble">
						<MarkdownView v-if="msg.role === 'assistant'" :content="msg.content" />
						<p v-else>{{ msg.content }}</p>
						<span
							v-if="streaming && msg.role === 'assistant' && i === messages.length - 1"
							class="cursor"
						>▍</span>
					</div>

					<p
						v-if="msg.role === 'assistant' && msg.retrievalDegraded"
						class="retrieval-degraded-hint"
					>
						{{ $t('common.retrievalDegradedHint') }}
					</p>

					<div
						v-if="msg.role === 'assistant' && msg.sources && msg.sources.length"
						class="chat-sources"
					>
						<span class="sources-label">{{ $t('common.sources') }}</span>
						<a
							v-for="(s, si) in msg.sources.filter((x) => x.type === 'web')"
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
							v-for="(s, si) in knowledgeSources(msg)"
							:key="si"
							:to="`/object/${s.slug}`"
							class="source-chip"
							:title="s.excerpt"
						>
							{{ sourceLabel(s, msg) }}<span class="source-type">{{ s.type }}</span>
						</RouterLink>
					</div>
				</div>
			</div>

			<form class="ask-input" @submit.prevent="send">
				<label class="web-toggle" :class="{ checked: webSearch }">
					<input v-model="webSearch" type="checkbox" :disabled="streaming" />
					<span class="toggle-track"><span class="toggle-thumb"></span></span>
					<span class="toggle-label">{{ $t('ask.webSearch') }}</span>
				</label>
				<input
					v-model="input"
					:placeholder="$t('ask.placeholder')"
					:disabled="streaming"
				/>
				<button
					class="primary-button"
					type="submit"
					:disabled="streaming || !input.trim()"
				>
					{{
						streaming
							? $t('ask.thinking')
							: $t('ask.send')
					}}
				</button>
			</form>
		</section>
	</main>
</template>

<style scoped>
.ask-section {
	display: flex;
	flex-direction: column;
	min-height: 60vh;
}

.ask-try {
	margin: 1.5rem 0;
	color: var(--muted);
}

.ask-toolbar {
	display: flex;
	justify-content: flex-end;
	margin: -0.5rem 0 1rem;
}

.ask-recent {
	margin-bottom: 1.25rem;
}

.ask-recent-label {
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

.history-question {
	flex: 1;
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
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

.history-more {
	display: flex;
	justify-content: center;
	margin-top: 1rem;
}

.ask-try-heading {
	display: flex;
	align-items: center;
	gap: 0.4rem;
}

.ask-try-heading p {
	margin: 0;
}

.shuffle-button {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 1.6rem;
	height: 1.6rem;
	padding: 0;
	border: 1px solid var(--button-border);
	border-radius: 50%;
	background: var(--button-bg);
	color: var(--muted);
	cursor: pointer;
	transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.shuffle-button:hover {
	background: var(--panel-glow);
	color: var(--accent);
	transform: rotate(50deg);
}

.ask-suggestions {
	display: flex;
	flex-wrap: wrap;
	gap: 0.5rem;
	margin-top: 0.6rem;
}

.suggestion-chip {
	background: var(--button-bg);
	border: 1px solid var(--button-border);
	color: var(--text);
	border-radius: 999px;
	padding: 0.35rem 0.9rem;
	font: inherit;
	cursor: pointer;
	transition: background 0.2s ease;
}

.suggestion-chip:hover {
	background: var(--panel-glow);
}

.chat-box {
	flex: 1;
	overflow-y: auto;
	max-height: 52vh;
	margin: 1rem 0;
	padding: 0.25rem 0.25rem 0.25rem 0;
}

.chat-message {
	display: flex;
	flex-direction: column;
	margin-bottom: 1.1rem;
}

.chat-message.user {
	align-items: flex-end;
}

.chat-bubble {
	max-width: 88%;
	padding: 0.7rem 0.95rem;
	border-radius: 12px;
	background: var(--card);
	border: 1px solid var(--card-border);
	color: var(--text);
	line-height: 1.65;
}

.chat-message.user .chat-bubble {
	background: var(--button-bg);
	border-color: var(--button-border);
}

.chat-bubble p {
	margin: 0.35em 0;
}

.chat-bubble p:first-child {
	margin-top: 0;
}

.chat-bubble p:last-child {
	margin-bottom: 0;
}

.cursor {
	animation: blink 0.9s steps(2) infinite;
}

@keyframes blink {
	0% { opacity: 1; }
	50% { opacity: 0; }
}

.chat-sources {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 0.4rem;
	margin-top: 0.5rem;
}

.retrieval-degraded-hint {
	color: var(--danger, #e57373);
	font-size: 0.78rem;
	margin: 0.4rem 0 0;
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

.web-toggle {
	display: inline-flex;
	align-items: center;
	gap: 0.45rem;
	cursor: pointer;
	white-space: nowrap;
}

.web-toggle input {
	display: none;
}

.toggle-track {
	width: 2.1rem;
	height: 1.15rem;
	border-radius: 999px;
	background: var(--button-bg);
	border: 1px solid var(--button-border);
	position: relative;
	transition: background 0.2s ease;
}

.toggle-thumb {
	position: absolute;
	top: 50%;
	left: 0.18rem;
	transform: translateY(-50%);
	width: 0.75rem;
	height: 0.75rem;
	border-radius: 50%;
	background: var(--muted);
	transition: transform 0.2s ease, background 0.2s ease;
}

.web-toggle.checked .toggle-track {
	background: var(--accent);
	border-color: var(--accent);
}

.web-toggle.checked .toggle-thumb {
	transform: translateY(-50%) translateX(0.95rem);
	background: #fff;
}

.toggle-label {
	font-size: 0.85rem;
	color: var(--text);
}

.web-toggle:has(input:disabled) {
	opacity: 0.6;
	cursor: not-allowed;
}

.ask-input {
	display: flex;
	gap: 0.6rem;
	margin-top: auto;
}

.ask-input input {
	flex: 1;
	padding: 0.65rem 0.9rem;
	border-radius: 10px;
	border: 1px solid var(--button-border);
	background: var(--card);
	color: var(--text);
	font: inherit;
	outline: none;
}

.ask-input input:focus {
	border-color: var(--accent);
}

.ask-input input:disabled {
	opacity: 0.6;
}

.primary-button:disabled {
	opacity: 0.55;
	cursor: not-allowed;
}
</style>
