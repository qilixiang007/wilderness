<script setup>
import { computed } from 'vue'
import { themeOptions } from '../theme/themeOptions'

const props = defineProps({
  language: { type: String, required: true },
  modelValue: { type: String, required: true }
})

const emit = defineEmits(['update:modelValue'])

const selectedLabel = computed(() => {
  const option = themeOptions.find((item) => item.value === props.modelValue)
  if (!option) return props.language === 'zh' ? '深邃风' : 'Deep Space'
  return props.language === 'zh' ? option.label.zh : option.label.en
})

function handleChange(event) {
  emit('update:modelValue', event.target.value)
}
</script>

<template>
  <label class="theme-select-wrap" :aria-label="language === 'zh' ? '主题切换' : 'Theme switcher'">
    <select class="theme-select" :value="modelValue" @change="handleChange">
      <option v-for="option in themeOptions" :key="option.value" :value="option.value">
        {{ language === 'zh' ? option.label.zh : option.label.en }}
      </option>
    </select>
  </label>
</template>
