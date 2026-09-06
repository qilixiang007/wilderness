<script setup>
import { computed, nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { openChatStream } from '../api'
import MarkdownView from '../components/MarkdownView.vue'

const { t, tm } = useI18n()

const suggestions = computed(() => tm('ask.suggestions'))

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

const messages = ref([]) // { role: 'user' | 'assistant', content, sources }
const input = ref('')
const streaming = ref(false)
const webSearch = ref(false)
const chatBox = ref(null)

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
	messages.value.push(userMsg, { role: 'assistant', content: '', sources: [] })
	// 取数组里那份响应式代理，而不是 push 前的裸对象引用，
	// 否则后面对它的赋值不会被 Vue 追踪到，界面要等流结束才会一次性刷新。
	const assistantMsg = messages.value[messages.value.length - 1]
	streaming.value = true
	scrollToBottom()

	openChatStream(question, {
		webEnabled: webSearch.value,
		onSources: (sources) => {
			assistantMsg.sources = sources
		},
		onDelta: (delta) => {
			assistantMsg.content += delta
			scrollToBottom()
		},
		onDone: () => {
			streaming.value = false
		}
	})
}
</script>

<template>
	<main>
		<section class="section-block ask-section">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('ask.kicker') }}</p>
				<h3>{{ $t('ask.title') }}</h3>
				<p>{{ $t('ask.intro') }}</p>
			</div>

			<div v-if="messages.length === 0" class="ask-empty">
				<p>{{ $t('ask.tryAsking') }}</p>
				<div class="ask-suggestions">
					<button
						v-for="q in suggestions"
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

.ask-empty {
	margin: 1.5rem 0;
	color: var(--muted);
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
