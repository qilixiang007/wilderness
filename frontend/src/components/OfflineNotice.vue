<script setup>
defineProps({ language: { type: String, default: 'zh' } })
</script>

<template>
	<p class="offline-notice" role="status">
		{{ language === 'zh' ? '离线模式：后端未连接，显示本地缓存内容。' : 'Offline mode: backend unreachable, showing local content.' }}
	</p>
</template>
