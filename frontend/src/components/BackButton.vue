<script setup>
import { useRouter } from 'vue-router'

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
    {{ $t('common.back') }}
  </button>
</template>
