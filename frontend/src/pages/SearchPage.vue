<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api, ApiUnavailableError } from '../api'
import { pick } from '../i18n'

const { t } = useI18n()
const route = useRoute()

const q = ref('')
const results = ref([])
const status = ref('idle') // idle | loading | ready | error
const error = ref('')

async function search() {
	const term = q.value.trim()
	if (!term) {
		results.value = []
		status.value = 'idle'
		return
	}
	status.value = 'loading'
	error.value = ''
	try {
		results.value = await api.search(term)
		status.value = 'ready'
	} catch (e) {
		error.value =
			e instanceof ApiUnavailableError
				? t('search.offline')
				: e.message || t('search.failed')
		status.value = 'error'
	}
}

// 支持从 URL ?q= 深链进入并直接搜索
onMounted(() => {
	if (route.query.q) {
		q.value = route.query.q
		search()
	}
})
</script>

<template>
	<main>
		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('search.kicker') }}</p>
				<h3>{{ $t('search.title') }}</h3>
			</div>

			<form class="search-form" @submit.prevent="search">
				<input
					v-model="q"
					:placeholder="$t('search.placeholder')"
					:disabled="status === 'loading'"
				/>
				<button
					class="primary-button"
					type="submit"
					:disabled="status === 'loading' || !q.trim()"
				>
					{{ $t('search.submit') }}
				</button>
			</form>

			<p v-if="status === 'idle'" class="search-hint">{{ $t('search.emptyHint') }}</p>

			<p v-else-if="status === 'loading'" class="loading-hint">{{ $t('common.loading') }}</p>

			<div v-else-if="status === 'error'" class="load-error">
				<p>{{ error }}</p>
				<button class="secondary-button" type="button" @click="search">
					{{ $t('common.retry') }}
				</button>
			</div>

			<template v-else>
				<p v-if="results.length === 0" class="detail-missing">
					{{ $t('search.noResults', { q: q }) }}
				</p>
				<div v-else class="card-grid object-card-grid">
					<article v-for="object in results" :key="object.slug" class="info-card object-card">
						<div class="object-visual">
							<img :src="object.image" :alt="pick(object.zhName, object.enName)" />
						</div>
						<div class="object-copy">
							<h4>{{ pick(object.zhName, object.enName) }}</h4>
							<p>{{ pick(object.zhDescription, object.enDescription) }}</p>
							<RouterLink class="secondary-button" :to="`/object/${object.slug}`">{{ $t('common.viewDetails') }}</RouterLink>
						</div>
					</article>
				</div>
			</template>
		</section>
	</main>
</template>

<style scoped>
.search-form {
	display: flex;
	gap: 0.6rem;
	margin-bottom: 0.5rem;
}

.search-form input {
	flex: 1;
	padding: 0.65rem 0.9rem;
	border-radius: 10px;
	border: 1px solid var(--button-border);
	background: var(--card);
	color: var(--text);
	font: inherit;
	outline: none;
}

.search-form input:focus {
	border-color: var(--accent);
}

.search-form input:disabled {
	opacity: 0.6;
}

.search-hint {
	margin: 1.5rem 0;
	color: var(--muted);
}
</style>
