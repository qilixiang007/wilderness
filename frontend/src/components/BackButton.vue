<script setup>
import { useRouter } from 'vue-router'

const props = defineProps({
  language: { type: String, default: 'zh' }
})

const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}
</script>

<template>
  <button class="back-button" type="button" @click="goBack">
    {{ language === 'zh' ? '← 返回上一页' : '← Go back' }}
  </button>
</template>
