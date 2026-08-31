<script setup>
import { computed } from 'vue'
import { themeOptions } from '../theme/themeOptions'
import { pick } from '../i18n'

const props = defineProps({
  modelValue: { type: String, required: true }
})

const emit = defineEmits(['update:modelValue'])

const selectedLabel = computed(() => {
  const option = themeOptions.find((item) => item.value === props.modelValue)
  if (!option) return pick('深邃风', 'Deep Space')
  return pick(option.label.zh, option.label.en)
})

function handleChange(event) {
  emit('update:modelValue', event.target.value)
}
</script>

<template>
  <label class="theme-select-wrap" :aria-label="$t('theme.switcher')">
    <select class="theme-select" :value="modelValue" @change="handleChange">
      <option v-for="option in themeOptions" :key="option.value" :value="option.value">
        {{ pick(option.label.zh, option.label.en) }}
      </option>
    </select>
  </label>
</template>
