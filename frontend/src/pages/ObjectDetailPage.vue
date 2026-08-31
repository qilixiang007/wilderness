<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api, ApiUnavailableError, ApiError } from '../api'
import { pick } from '../i18n'
import { useFavorites } from '../composables/useFavorites'
import OfflineNotice from '../components/OfflineNotice.vue'
import MarkdownView from '../components/MarkdownView.vue'

const { t } = useI18n()

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
			e instanceof ApiError ? e.message : t('object.generateFailed')
		explainStatus.value = 'error'
	}
}

const route = useRoute()
const object = ref(null)
const status = ref('loading') // loading | ready | offline | error | notfound
const error = ref('')

// 收藏（localStorage 持久化）
const { isFavorite, toggle } = useFavorites()
const fav = computed(() => isFavorite(object.value?.slug))

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
				<p class="eyebrow">{{ $t('object.file') }}</p>
				<h3>{{ $t('object.file') }}</h3>
			</div>

			<p v-if="status === 'loading'" class="loading-hint">{{ $t('common.loading') }}</p>

			<div v-else-if="status === 'error'" class="load-error">
				<p>{{ error || $t('common.loadFailed') }}</p>
				<button class="secondary-button" type="button" @click="load">
					{{ $t('common.retry') }}
				</button>
			</div>

			<p v-else-if="status === 'notfound'" class="detail-missing">{{ $t('object.notFound') }}</p>

			<div v-else-if="status === 'offline'" class="load-error">
				<OfflineNotice />
				<p>{{ $t('object.offline') }}</p>
			</div>

			<template v-else-if="object">
				<article class="info-card detail-card">
					<div class="detail-visual">
						<img :src="object.image" :alt="pick(object.zhName, object.enName)" />
					</div>
					<p class="detail-category-link">
						<RouterLink :to="`/detail/${object.category.slug}`">← {{ pick(object.category.zhName, object.category.enName) }}</RouterLink>
					</p>
					<div class="object-title-row">
						<h4>{{ pick(object.zhName, object.enName) }}</h4>
						<button class="favorite-btn" type="button" @click="toggle(object)">
							{{ fav ? $t('favorites.remove') : $t('favorites.add') }}
						</button>
					</div>
					<p>{{ pick(object.zhDescription, object.enDescription) }}</p>
				</article>

				<div v-if="object.facts && object.facts.length" class="section-heading compact object-heading">
					<h4>{{ $t('object.keyFacts') }}</h4>
				</div>
				<dl v-if="object.facts && object.facts.length" class="facts-list">
					<div v-for="fact in object.facts" :key="fact.sortOrder" class="fact-row">
						<dt>{{ pick(fact.zhLabel, fact.enLabel) }}</dt>
						<dd>{{ pick(fact.zhValue, fact.enValue) }}</dd>
					</div>
				</dl>

				<div class="section-heading compact object-heading">
					<h4>{{ $t('object.aiExplainer') }}</h4>
				</div>

				<div v-if="explainStatus === 'idle'" class="explain-prompt">
					<p>{{ $t('object.explainPrompt') }}</p>
					<button class="secondary-button" type="button" @click="loadExplain">
						{{ $t('object.generate') }}
					</button>
				</div>

				<p v-else-if="explainStatus === 'loading'" class="loading-hint">
					{{ $t('object.generating') }}
				</p>

				<div v-else-if="explainStatus === 'error'" class="load-error">
					<p>{{ explainError }}</p>
					<button class="secondary-button" type="button" @click="loadExplain">
						{{ $t('common.retry') }}
					</button>
				</div>

				<article v-else class="info-card explain-card">
					<MarkdownView :content="explanation" />
					<div v-if="explainSources.length" class="explain-sources">
						<span class="sources-label">{{ $t('common.sources') }}</span>
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
.object-title-row {
	display: flex;
	align-items: center;
	gap: 0.7rem;
	flex-wrap: wrap;
}

.favorite-btn {
	background: var(--button-bg);
	border: 1px solid var(--button-border);
	color: var(--text);
	border-radius: 999px;
	padding: 0.3rem 0.85rem;
	font-size: 0.82rem;
	cursor: pointer;
	transition: background 0.2s ease;
}

.favorite-btn:hover {
	background: var(--panel-glow);
}

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
