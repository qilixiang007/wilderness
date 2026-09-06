<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../api'
import { useAuth } from '../composables/useAuth'

const { t } = useI18n()
const { user } = useAuth()

// 与后端 PasswordPolicy 保持一致：8-64 位，必须同时包含英文字母和数字
const PASSWORD_REGEX = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,64}$/

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')
const notice = ref('')
const submitting = ref(false)

async function submit() {
	if (submitting.value) return
	error.value = ''
	notice.value = ''
	if (!oldPassword.value) {
		error.value = t('account.oldPasswordRequired')
		return
	}
	if (!PASSWORD_REGEX.test(newPassword.value || '')) {
		error.value = t('auth.passwordTooShort')
		return
	}
	if (newPassword.value !== confirmPassword.value) {
		error.value = t('account.confirmMismatch')
		return
	}

	submitting.value = true
	try {
		await api.changePassword(oldPassword.value, newPassword.value)
		notice.value = t('account.changeSuccess')
		oldPassword.value = ''
		newPassword.value = ''
		confirmPassword.value = ''
	} catch (err) {
		error.value = err.message || t('account.changeFailed')
	} finally {
		submitting.value = false
	}
}
</script>

<template>
	<main>
		<section class="section-block">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('account.kicker') }}</p>
				<h3>{{ $t('account.title') }}</h3>
			</div>

			<div class="account-panel">
				<p class="account-email">{{ user?.email }}</p>

				<form class="account-form" @submit.prevent="submit">
					<label class="field">
						<span class="field-label">{{ $t('account.oldPassword') }}</span>
						<input v-model="oldPassword" type="password" autocomplete="current-password" />
					</label>

					<label class="field">
						<span class="field-label">{{ $t('account.newPassword') }}</span>
						<input v-model="newPassword" type="password" autocomplete="new-password" :placeholder="$t('auth.passwordHint')" />
					</label>

					<label class="field">
						<span class="field-label">{{ $t('account.confirmPassword') }}</span>
						<input v-model="confirmPassword" type="password" autocomplete="new-password" />
					</label>

					<p v-if="error" class="account-error">{{ error }}</p>
					<p v-if="notice" class="account-notice">{{ notice }}</p>

					<button class="primary-button account-submit" type="submit" :disabled="submitting">
						{{ submitting ? $t('auth.submitting') : $t('account.changeSubmit') }}
					</button>
				</form>
			</div>
		</section>
	</main>
</template>

<style scoped>
.account-panel {
	max-width: 420px;
	padding: 28px;
	border-radius: 20px;
	border: 1px solid var(--card-border);
	background: var(--card);
}

.account-email {
	margin: 0 0 18px;
	color: var(--muted);
	font-size: 0.9rem;
}

.account-form {
	display: flex;
	flex-direction: column;
	gap: 0.9rem;
}

.field {
	display: flex;
	flex-direction: column;
	gap: 0.35rem;
}

.field-label {
	font-size: 0.82rem;
	color: var(--muted);
}

.field input {
	padding: 0.6rem 0.8rem;
	border-radius: 10px;
	border: 1px solid var(--button-border);
	background: var(--card);
	color: var(--text);
	font: inherit;
	outline: none;
}

.field input:focus {
	border-color: var(--accent);
}

.account-error {
	color: var(--danger, #e57373);
	font-size: 0.85rem;
	margin: 0;
}

.account-notice {
	color: var(--accent);
	font-size: 0.85rem;
	margin: 0;
}

.account-submit {
	margin-top: 0.3rem;
	justify-content: center;
}

.account-submit:disabled {
	opacity: 0.55;
	cursor: not-allowed;
}
</style>
