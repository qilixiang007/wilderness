<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, ApiUnavailableError, ApiError } from '../api'
import OfflineNotice from '../components/OfflineNotice.vue'
import MarkdownView from '../components/MarkdownView.vue'

const props = defineProps({ language: { type: String, required: true } })

// AI 讲解:基于知识库生成科普讲解
const explainStatus = ref('idle') // idle | loading | ready | error
const explanation = ref('')
const explainSources = ref([])
const explainError = ref('')

async function loadExplain() {
	explainStatus.value = 'loading'
	explainError.value = ''
	try {
		const data = await api.explain(route.params.slug)
		explanation.value = data.answer
		explainSources.value = data.sources || []
		explainStatus.value = 'ready'
	} catch (e) {
		explainError.value =
			e instanceof ApiError ? e.message : props.language === 'zh' ? '生成失败，请重试。' : 'Failed to generate.'
		explainStatus.value = 'error'
	}
}

const route = useRoute()
const object = ref(null)
const status = ref('loading') // loading | ready | offline | error | notfound
const error = ref('')

async function load() {
	const slug = route.params.slug
	object.value = null
	error.value = ''
	status.value = 'loading'
	try {
		object.value = await api.getObject(slug)
		status.value = 'ready'
	} catch (e) {
		if (e instanceof ApiUnavailableError) {
			status.value = 'offline'
		} else if (e instanceof ApiError && e.status === 404) {
			status.value = 'notfound'
		} else {
			error.value = e instanceof ApiError ? e.message : ''
			status.value = 'error'
		}
	}
}

watch(() => route.params.slug, load, { immediate: true })
</script>

<template>
	<main>
		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">{{ language === 'zh' ? '天体档案' : 'OBJECT FILE' }}</p>
				<h3>{{ language === 'zh' ? '天体详情' : 'Object file' }}</h3>
			</div>

			<p v-if="status === 'loading'" class="loading-hint">{{ language === 'zh' ? '加载中…' : 'Loading…' }}</p>

			<div v-else-if="status === 'error'" class="load-error">
				<p>{{ error || (language === 'zh' ? '加载失败，请重试。' : 'Failed to load. Please retry.') }}</p>
				<button class="secondary-button" type="button" @click="load">
					{{ language === 'zh' ? '重试' : 'Retry' }}
				</button>
			</div>

			<p v-else-if="status === 'notfound'" class="detail-missing">{{ language === 'zh' ? '未找到该天体。' : 'Celestial object not found.' }}</p>

			<div v-else-if="status === 'offline'" class="load-error">
				<OfflineNotice :language="language" />
				<p>{{ language === 'zh' ? '后端不可用，暂无该天体的离线数据。' : 'Backend unreachable; no offline data for this object.' }}</p>
			</div>

			<template v-else-if="object">
				<article class="info-card detail-card">
					<div class="detail-visual">
						<img :src="object.image" :alt="language === 'zh' ? object.zhName : object.enName" />
					</div>
					<p class="detail-category-link">
						<RouterLink :to="`/detail/${object.category.slug}`">← {{ language === 'zh' ? object.category.zhName : object.category.enName }}</RouterLink>
					</p>
					<h4>{{ language === 'zh' ? object.zhName : object.enName }}</h4>
					<p>{{ language === 'zh' ? object.zhDescription : object.enDescription }}</p>
				</article>

				<div v-if="object.facts && object.facts.length" class="section-heading compact object-heading">
					<h4>{{ language === 'zh' ? '关键数据' : 'Key facts' }}</h4>
				</div>
				<dl v-if="object.facts && object.facts.length" class="facts-list">
					<div v-for="fact in object.facts" :key="fact.sortOrder" class="fact-row">
						<dt>{{ language === 'zh' ? fact.zhLabel : fact.enLabel }}</dt>
						<dd>{{ language === 'zh' ? fact.zhValue : fact.enValue }}</dd>
					</div>
				</dl>

				<div class="section-heading compact object-heading">
					<h4>{{ language === 'zh' ? 'AI 讲解' : 'AI explainer' }}</h4>
				</div>

				<div v-if="explainStatus === 'idle'" class="explain-prompt">
					<p>{{ language === 'zh' ? '让 AI 结合知识库，为你生成一篇关于该天体的科普讲解。' : 'Let AI write a science explainer for this object based on the knowledge base.' }}</p>
					<button class="secondary-button" type="button" @click="loadExplain">
						{{ language === 'zh' ? '生成讲解' : 'Generate' }}
					</button>
				</div>

				<p v-else-if="explainStatus === 'loading'" class="loading-hint">
					{{ language === 'zh' ? 'AI 正在生成讲解…' : 'AI is writing…' }}
				</p>

				<div v-else-if="explainStatus === 'error'" class="load-error">
					<p>{{ explainError }}</p>
					<button class="secondary-button" type="button" @click="loadExplain">
						{{ language === 'zh' ? '重试' : 'Retry' }}
					</button>
				</div>

				<article v-else class="info-card explain-card">
					<MarkdownView :content="explanation" />
					<div v-if="explainSources.length" class="explain-sources">
						<span class="sources-label">{{ language === 'zh' ? '资料来源' : 'Sources' }}</span>
						<RouterLink
							v-for="(s, si) in explainSources"
							:key="si"
							:to="`/object/${s.slug}`"
							class="source-chip"
							:title="s.excerpt"
						>
							{{ s.title }}<span class="source-type">{{ s.type }}</span>
						</RouterLink>
					</div>
				</article>
			</template>
		</section>
	</main>
</template>

<style scoped>
.explain-prompt {
	margin: 0.25rem 0 1rem;
	color: var(--muted);
}

.explain-prompt .secondary-button {
	margin-top: 0.5rem;
}

.explain-card {
	margin-top: 0.5rem;
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
