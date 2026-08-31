<script setup>
// 分类卡片栅格模块：加载全部星体类型并以卡片展示。
// 后端不可用时回退本地静态分类，附离线提示。
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, ApiUnavailableError, ApiError } from '../api'
import { celestialCategories } from '../data/celestial'
import { pick } from '../i18n'
import OfflineNotice from '../components/OfflineNotice.vue'

const { t } = useI18n()

const categories = ref([])
const loading = ref(true)
const offline = ref(false)
const error = ref('')

async function load() {
	loading.value = true
	offline.value = false
	error.value = ''
	try {
		categories.value = await api.getCategories()
	} catch (e) {
		if (e instanceof ApiUnavailableError) {
			offline.value = true
			categories.value = celestialCategories
		} else if (e instanceof ApiError) {
			error.value = e.message
		} else {
			error.value = t('common.loadFailed')
		}
	} finally {
		loading.value = false
	}
}

onMounted(load)
</script>

<template>
	<div id="explore-categories" class="category-grid-wrap">
		<p v-if="loading" class="loading-hint">{{ $t('common.loading') }}</p>

		<div v-else-if="error" class="load-error">
			<p>{{ error }}</p>
			<button class="secondary-button" type="button" @click="load">
				{{ $t('common.retry') }}
			</button>
		</div>

		<template v-else>
			<OfflineNotice v-if="offline" />
			<div class="card-grid category-grid">
				<article v-for="item in categories" :key="item.slug" class="info-card category-card">
					<div class="category-visual">
						<img :src="item.image" :alt="pick(item.imageAltZh, item.imageAltEn)" />
					</div>
					<div class="category-copy">
						<h4>{{ pick(item.zhName, item.enName) }}</h4>
						<span class="catalog-tag">
							{{ $t('common.objects', { n: item.objectCount }) }}
						</span>
						<p>{{ pick(item.zhDescription, item.enDescription) }}</p>
						<RouterLink class="secondary-button" :to="`/detail/${item.slug}`">{{ $t('common.viewDetails') }}</RouterLink>
					</div>
				</article>
			</div>
		</template>
	</div>
</template>
