<script setup>
import SelectDropdown from './SelectDropdown.vue'
import { themeOptions } from '../theme/themeOptions'
import { pick } from '../i18n'

defineProps({
	modelValue: { type: String, required: true }
})

defineEmits(['update:modelValue'])

function getThemeLabel(option) {
	return pick(option.label.zh, option.label.en)
}
</script>

<template>
	<SelectDropdown
		:model-value="modelValue"
		:options="themeOptions"
		:aria-label="$t('theme.switcher')"
		:get-label="getThemeLabel"
		@update:model-value="$emit('update:modelValue', $event)"
	>
		<template #item="{ option }">
			<span class="theme-dot" :style="{ background: option.color }" aria-hidden="true"></span>
			{{ getThemeLabel(option) }}
		</template>
	</SelectDropdown>
</template>

<style scoped>
.theme-dot {
	width: 10px;
	height: 10px;
	flex-shrink: 0;
	border-radius: 50%;
}
</style>
