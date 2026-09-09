<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useFavorites } from '../composables/useFavorites'
import ObjectCard from '../components/ObjectCard.vue'
import GeneratedFavoriteCard from '../components/GeneratedFavoriteCard.vue'

const router = useRouter()
const { favorites, toggle, generationFavorites, toggleGeneration, load } = useFavorites()

const activeTab = ref('natural') // 'natural' | 'generated'

onMounted(() => {
	load()
})

async function onToggle(object) {
	const result = await toggle(object)
	if (result.needLogin) {
		router.push('/login')
	}
}

async function onToggleGeneration(item) {
	const result = await toggleGeneration({ historyId: item.generationId })
	if (result.needLogin) {
		router.push('/login')
	}
}
</script>

<template>
	<main>
		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('favorites.kicker') }}</p>
				<h3>{{ $t('favorites.title') }}</h3>
			</div>

			<div class="history-tabs">
				<button
					class="history-tab"
					:class="{ active: activeTab === 'natural' }"
					type="button"
					@click="activeTab = 'natural'"
				>
					{{ $t('favorites.tabs.natural') }}
				</button>
				<button
					class="history-tab"
					:class="{ active: activeTab === 'generated' }"
					type="button"
					@click="activeTab = 'generated'"
				>
					{{ $t('favorites.tabs.generated') }}
				</button>
			</div>

			<template v-if="activeTab === 'natural'">
				<p v-if="favorites.length === 0" class="detail-missing">{{ $t('favorites.empty') }}</p>

				<div v-else class="card-grid object-card-grid">
					<ObjectCard v-for="object in favorites" :key="object.slug" :object="object">
						<template #actions="{ object: obj }">
							<div class="object-actions">
								<RouterLink class="secondary-button" :to="`/object/${obj.slug}`">{{ $t('common.viewDetails') }}</RouterLink>
								<button class="favorite-remove" type="button" @click="onToggle(obj)">
									{{ $t('favorites.remove') }}
								</button>
							</div>
						</template>
					</ObjectCard>
				</div>
			</template>

			<template v-else>
				<p v-if="generationFavorites.length === 0" class="detail-missing">{{ $t('favorites.generatedEmpty') }}</p>

				<div v-else class="card-grid object-card-grid">
					<GeneratedFavoriteCard
						v-for="item in generationFavorites"
						:key="item.generationId"
						:item="item"
						@remove="onToggleGeneration"
					/>
				</div>
			</template>
		</section>
	</main>
</template>

<style scoped>
/* tab 样式与 HistoryPage 保持一致 */
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

/* 卡片底部操作：查看详情 / 已收藏 复用全局 46px 胶囊按钮规格，
   并排成等宽的一对，避免两按钮一高一矮 */
.object-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	margin-top: 12px;
	font-size: 0.9rem;
}

.object-actions > * {
	flex: 1 1 auto;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	min-height: 46px;
	padding: 0 18px;
}

.favorite-remove {
	background: var(--button-bg);
	border: 1px solid var(--button-border);
	color: var(--text);
	border-radius: 999px;
	cursor: pointer;
	transition: background 0.2s ease;
}

.favorite-remove:hover {
	background: var(--panel-glow);
}
</style>
