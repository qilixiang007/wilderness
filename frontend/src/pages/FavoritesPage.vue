<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useFavorites } from '../composables/useFavorites'
import { pick } from '../i18n'

const router = useRouter()
const { favorites, toggle, load } = useFavorites()

onMounted(() => {
	load()
})

async function onToggle(object) {
	const result = await toggle(object)
	if (result.needLogin) {
		router.push('/login')
	}
}
</script>

<template>
	<main>
		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('favorites.title') }}</p>
				<h3>{{ $t('favorites.title') }}</h3>
			</div>

			<p v-if="favorites.length === 0" class="detail-missing">{{ $t('favorites.empty') }}</p>

			<div v-else class="card-grid object-card-grid">
				<article v-for="object in favorites" :key="object.slug" class="info-card object-card">
					<div class="object-visual">
						<img :src="object.image" :alt="pick(object.zhName, object.enName)" />
					</div>
					<div class="object-copy">
						<h4>{{ pick(object.zhName, object.enName) }}</h4>
						<RouterLink class="secondary-button" :to="`/object/${object.slug}`">{{ $t('common.viewDetails') }}</RouterLink>
						<button class="favorite-remove" type="button" @click="onToggle(object)">
							{{ $t('favorites.remove') }}
						</button>
					</div>
				</article>
			</div>
		</section>
	</main>
</template>

<style scoped>
.object-copy .favorite-remove {
	margin-left: 0.4rem;
}

.favorite-remove {
	background: var(--button-bg);
	border: 1px solid var(--button-border);
	color: var(--text);
	border-radius: 999px;
	padding: 0.3rem 0.85rem;
	font-size: 0.82rem;
	cursor: pointer;
	transition: background 0.2s ease;
}

.favorite-remove:hover {
	background: var(--panel-glow);
}
</style>
