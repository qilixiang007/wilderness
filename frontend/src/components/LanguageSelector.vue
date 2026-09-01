<script setup>
import SelectDropdown from './SelectDropdown.vue'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()

const languageOptions = [
	{ value: 'zh', label: '中文' },
	{ value: 'en', label: 'English' }
]
</script>

<template>
	<SelectDropdown
		:model-value="locale"
		:options="languageOptions"
		:aria-label="$t('accessibility.languageSwitcher')"
		@update:model-value="locale = $event"
	/>
</template>
