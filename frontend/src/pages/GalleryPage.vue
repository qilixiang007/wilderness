<script setup>
// 公开广场完整列表页：匿名可访问，分页加载，列表内展开看介绍（不做独立详情路由）。
// 管理员登录时每条展开后多一个"下架"按钮，直接改回私密（事后下架，不做发布前审核）。
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import MarkdownView from '../components/MarkdownView.vue'
import CelestialVisual from '../components/CelestialVisual.vue'
import OfflineNotice from '../components/OfflineNotice.vue'
import { api, ApiUnavailableError } from '../api'
import { useAuth } from '../composables/useAuth'

const { t } = useI18n()
const { isAdmin } = useAuth()

const PAGE_SIZE = 20

const items = ref([])
const totalElements = ref(0)
const page = ref(0)
const loading = ref(true)
const loaded = ref(false)
const offline = ref(false)
const error = ref('')
const expandedId = ref(null)
const takingDownId = ref(null)

const hasMore = computed(() => items.value.length < totalElements.value)

async function load(reset = false) {
	if (reset) {
		page.value = 0
		items.value = []
	}
	loading.value = true
	offline.value = false
	error.value = ''
	try {
		const res = await api.getGallery(page.value, PAGE_SIZE)
		items.value = reset ? res.items : [...items.value, ...res.items]
		totalElements.value = res.totalElements
		loaded.value = true
	} catch (e) {
		if (e instanceof ApiUnavailableError) {
			offline.value = true
		} else {
			error.value = t('common.loadFailed')
		}
	} finally {
		loading.value = false
	}
}

function loadMore() {
	page.value += 1
	load()
}

function toggle(item) {
	expandedId.value = expandedId.value === item.id ? null : item.id
}

async function takedown(item) {
	if (takingDownId.value) return
	takingDownId.value = item.id
	try {
		await api.setGenerationVisibility(item.id, false)
		items.value = items.value.filter((i) => i.id !== item.id)
		totalElements.value = Math.max(0, totalElements.value - 1)
		if (expandedId.value === item.id) expandedId.value = null
	} catch {
		error.value = t('gallery.takedownFailed')
	} finally {
		takingDownId.value = null
	}
}

onMounted(() => load(true))
</script>

<template>
	<main>
		<section class="section-block gallery-page-section">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('home.observerNotes') }}</p>
				<h3>{{ $t('gallery.title') }}</h3>
				<p>{{ $t('gallery.intro') }}</p>
			</div>

			<OfflineNotice v-if="offline" />
			<p v-if="error" class="gallery-error">{{ error }}</p>
			<p v-if="loaded && items.length === 0 && !error && !offline" class="gallery-empty">
				{{ $t('gallery.empty') }}
			</p>

			<div v-for="item in items" :key="item.id" class="gallery-item">
				<button class="gallery-item-head" type="button" @click="toggle(item)">
					<span class="gallery-item-thumb">
						<img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.name" />
						<CelestialVisual v-else :render="item.render || {}" :name="item.name" />
					</span>
					<span class="gallery-item-name">{{ item.name }}</span>
					<span v-if="item.type" class="gallery-item-type">{{ item.type }}</span>
				</button>

				<div v-if="expandedId === item.id" class="gallery-item-body">
					<p v-if="item.imageUrl && item.imageTemporary" class="image-temporary-hint">
						{{ $t('agent.imageTemporaryHint') }}
					</p>
					<MarkdownView v-if="item.introduction" :content="item.introduction" />

					<div v-if="isAdmin" class="gallery-actions">
						<button
							class="gallery-takedown"
							type="button"
							:disabled="takingDownId === item.id"
							@click="takedown(item)"
						>
							{{ $t('gallery.takedown') }}
						</button>
					</div>
				</div>
			</div>

			<div v-if="hasMore" class="gallery-more">
				<button class="secondary-button" type="button" :disabled="loading" @click="loadMore">
					{{ loading ? $t('common.loading') : $t('history.loadMore') }}
				</button>
			</div>
		</section>
	</main>
</template>

<style scoped>
.gallery-page-section {
	max-width: 46rem;
}

.gallery-error {
	color: var(--danger, #e57373);
	font-size: 0.85rem;
}

.gallery-empty {
	color: var(--muted);
	font-size: 0.9rem;
	margin: 1.5rem 0;
}

.gallery-item {
	border: 1px solid var(--card-border);
	border-radius: 12px;
	background: var(--card);
	margin-bottom: 0.6rem;
	overflow: hidden;
}

.gallery-item-head {
	display: flex;
	align-items: center;
	gap: 0.7rem;
	width: 100%;
	padding: 0.6rem 0.95rem;
	border: none;
	background: none;
	color: var(--text);
	font: inherit;
	font-size: 0.92rem;
	text-align: left;
	cursor: pointer;
}

.gallery-item-head:hover {
	background: var(--panel-glow);
}

.gallery-item-thumb {
	flex-shrink: 0;
	width: 44px;
	height: 44px;
	border-radius: 8px;
	overflow: hidden;
	background: linear-gradient(180deg, var(--bg-soft), rgba(0, 0, 0, 0.15));
}

.gallery-item-thumb img {
	display: block;
	width: 100%;
	height: 100%;
	object-fit: contain;
}

.gallery-item-name {
	flex: 1;
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.gallery-item-type {
	flex-shrink: 0;
	font-size: 0.78rem;
	color: var(--muted);
}

.gallery-item-body {
	padding: 0.4rem 0.95rem 0.95rem;
	border-top: 1px solid var(--card-border);
}

.image-temporary-hint {
	color: var(--danger, #e57373);
	font-size: 0.78rem;
	margin: 0.4rem 0;
}

.gallery-actions {
	margin-top: 0.8rem;
}

.gallery-takedown {
	padding: 0.25rem 0.7rem;
	border: 1px solid var(--danger, #e57373);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--danger, #e57373);
	font: inherit;
	font-size: 0.78rem;
	cursor: pointer;
	transition: background 0.2s ease;
}

.gallery-takedown:hover {
	background: var(--panel-glow);
}

.gallery-takedown:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

.gallery-more {
	display: flex;
	justify-content: center;
	margin-top: 1rem;
}
</style>
