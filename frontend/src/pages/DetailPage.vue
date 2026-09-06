<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, ApiUnavailableError, ApiError } from '../api'
import { celestialCategories } from '../data/celestial'
import { pick } from '../i18n'
import OfflineNotice from '../components/OfflineNotice.vue'
import ObjectCard from '../components/ObjectCard.vue'
import CompareHint from '../components/CompareHint.vue'
import { useCompare } from '../composables/useCompare'

const { isSelected } = useCompare()
const route = useRoute()
const category = ref(null)
const status = ref('loading') // loading | ready | offline | error | notfound
const error = ref('')

async function load() {
	const slug = route.params.slug
	category.value = null
	error.value = ''
	status.value = 'loading'
	try {
		category.value = await api.getCategory(slug)
		status.value = 'ready'
	} catch (e) {
		if (e instanceof ApiUnavailableError) {
			const fallback = celestialCategories.find((item) => item.slug === slug)
			if (fallback) {
				category.value = fallback
				status.value = 'offline'
			} else {
				status.value = 'notfound'
			}
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
				<p class="eyebrow">{{ $t('category.indexKicker') }}</p>
				<h3>{{ $t('category.file') }}</h3>
			</div>

			<p v-if="status === 'loading'" class="loading-hint">{{ $t('common.loading') }}</p>

			<div v-else-if="status === 'error'" class="load-error">
				<p>{{ error || $t('common.loadFailed') }}</p>
				<button class="secondary-button" type="button" @click="load">
					{{ $t('common.retry') }}
				</button>
			</div>

			<p v-else-if="status === 'notfound'" class="detail-missing">{{ $t('category.notFound') }}</p>

			<template v-else-if="category">
				<OfflineNotice v-if="status === 'offline'" />
				<article class="info-card detail-card">
					<div class="detail-visual">
						<img :src="category.image" :alt="pick(category.imageAltZh, category.imageAltEn)" />
					</div>
					<h4>{{ pick(category.zhName, category.enName) }}</h4>
					<p>{{ pick(category.zhDescription, category.enDescription) }}</p>
				</article>

				<template v-if="status === 'ready'">
					<div class="section-heading compact object-heading">
						<h4>{{ $t('object.objects') }}</h4>
					</div>
					<CompareHint v-if="category.objects && category.objects.length" />
					<div v-if="category.objects && category.objects.length" class="card-grid object-card-grid">
						<ObjectCard
							v-for="object in category.objects"
							:key="object.slug"
							:object="object"
							selectable
							:selected="isSelected(object.slug)"
						/>
					</div>
					<p v-else class="detail-missing">{{ $t('category.noObjects') }}</p>
				</template>
			</template>
		</section>
	</main>
</template>
