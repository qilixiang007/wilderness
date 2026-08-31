<script setup>
// 侧边栏模块：品牌区 + 三个功能模块入口。
// 桌面端固定显示；移动端（<960px）作为抽屉，open 控制滑入滑出，点选模块后自动收起。
defineProps({
	open: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])
</script>

<template>
	<Transition name="backdrop">
		<div v-if="open" class="sidebar-backdrop" @click="emit('close')" />
	</Transition>

	<aside class="sidebar" :class="{ 'sidebar-open': open }">
		<div class="sidebar-brand">
			<p class="eyebrow">Astronomy / 科普</p>
			<h1 class="site-title">{{ $t('site.title') }}</h1>
		</div>

		<nav class="sidebar-nav" aria-label="Modules">
			<RouterLink class="sidebar-link" to="/" @click="emit('close')">
				{{ $t('nav.explore') }}
			</RouterLink>
			<RouterLink class="sidebar-link" to="/favorites" @click="emit('close')">
				{{ $t('nav.favorites') }}
			</RouterLink>
			<RouterLink class="sidebar-link" to="/ask" @click="emit('close')">
				{{ $t('nav.ask') }}
			</RouterLink>
		</nav>

		<p class="sidebar-foot">Wilderness of the Universe</p>
	</aside>
</template>
