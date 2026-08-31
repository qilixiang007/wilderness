<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api } from '../api'
import { useAuth } from '../composables/useAuth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { login, register } = useAuth()

// 模式：登录 / 注册
const mode = ref('login')
// 登录方式：密码 / 验证码
const loginMethod = ref('password')

const email = ref('')
const password = ref('')
const code = ref('')
const error = ref('')
const submitting = ref(false)
const codeSent = ref(false)
// 验证码投递方式：'email'=已发真实邮件；'log'=开发模式,码在服务端日志、未发邮件
const codeDelivery = ref('email')
const countdown = ref(0)

let timer = null

// 注册页才需要验证码发送键（登录页走 login purpose，注册页走 register purpose）
function codePurpose() {
	return mode.value === 'register' ? 'register' : 'login'
}

async function sendCode() {
	error.value = ''
	if (!email.value.trim()) {
		error.value = t('auth.emailRequired')
		return
	}
	if (countdown.value > 0) return
	try {
		codeDelivery.value = await api.verifyCode(email.value.trim(), codePurpose())
		codeSent.value = true
		countdown.value = 60
		if (timer) clearInterval(timer)
		timer = setInterval(() => {
			countdown.value--
			if (countdown.value <= 0 && timer) {
				clearInterval(timer)
				timer = null
			}
		}, 1000)
	} catch (err) {
		error.value = err.message || t('auth.sendCodeFailed')
	}
}

function switchMode(next) {
	mode.value = next
	error.value = ''
	code.value = ''
}

function switchMethod(next) {
	loginMethod.value = next
	error.value = ''
}

async function submit() {
	if (submitting.value) return
	error.value = ''
	if (!email.value.trim()) {
		error.value = t('auth.emailRequired')
		return
	}
	if (mode.value === 'login' && loginMethod.value === 'password' && !password.value) {
		error.value = t('auth.passwordRequired')
		return
	}
	if ((mode.value === 'register') && (!password.value || password.value.length < 6)) {
		error.value = t('auth.passwordTooShort')
		return
	}
	if (mode.value === 'login' && loginMethod.value === 'code' && !code.value.trim()) {
		error.value = t('auth.codeRequired')
		return
	}

	submitting.value = true
	try {
		if (mode.value === 'register') {
			await register({ email: email.value.trim(), password: password.value, code: code.value.trim() || null })
		} else if (loginMethod.value === 'password') {
			await login('password', { email: email.value.trim(), password: password.value })
		} else {
			await login('code', { email: email.value.trim(), code: code.value.trim() })
		}
		// 登录成功：回跳来源页（默认首页）
		const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
		router.push(redirect)
	} catch (err) {
		error.value = err.message || t('auth.loginFailed')
	} finally {
		submitting.value = false
	}
}

onUnmounted(() => {
	if (timer) clearInterval(timer)
})

const isCodeMode = computed(() => mode.value === 'login' && loginMethod.value === 'code')
</script>

<template>
	<main>
		<section class="section-block auth-section">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('auth.kicker') }}</p>
				<h3>{{ mode === 'login' ? $t('auth.loginTitle') : $t('auth.registerTitle') }}</h3>
				<p>{{ $t('auth.intro') }}</p>
			</div>

			<div class="auth-card">
				<div class="auth-tabs">
					<button
						class="auth-tab"
						:class="{ active: mode === 'login' }"
						type="button"
						@click="switchMode('login')"
					>
						{{ $t('auth.loginTitle') }}
					</button>
					<button
						class="auth-tab"
						:class="{ active: mode === 'register' }"
						type="button"
						@click="switchMode('register')"
					>
						{{ $t('auth.registerTitle') }}
					</button>
				</div>

				<div v-if="mode === 'login'" class="method-tabs">
					<button
						class="method-tab"
						:class="{ active: loginMethod === 'password' }"
						type="button"
						@click="switchMethod('password')"
					>
						{{ $t('auth.tabPassword') }}
					</button>
					<button
						class="method-tab"
						:class="{ active: loginMethod === 'code' }"
						type="button"
						@click="switchMethod('code')"
					>
						{{ $t('auth.tabCode') }}
					</button>
				</div>

				<form class="auth-form" @submit.prevent="submit">
					<label class="field">
						<span class="field-label">{{ $t('auth.email') }}</span>
						<input v-model="email" type="email" autocomplete="email" :placeholder="'name@qq.com / @163.com / @gmail.com'" />
					</label>

					<label v-if="mode === 'register' || loginMethod === 'password'" class="field">
						<span class="field-label">{{ $t('auth.password') }}</span>
						<input v-model="password" type="password" autocomplete="current-password" :placeholder="$t('auth.passwordHint')" />
					</label>

					<div v-if="isCodeMode || mode === 'register'" class="field">
						<span class="field-label">{{ $t('auth.code') }}</span>
						<div class="code-row">
							<input v-model="code" type="text" inputmode="numeric" autocomplete="one-time-code" :placeholder="$t('auth.codePlaceholder')" />
							<button
								class="send-code-btn"
								type="button"
								:disabled="countdown > 0"
								@click="sendCode"
							>
								{{ countdown > 0 ? $t('auth.resendIn', { s: countdown }) : $t('auth.sendCode') }}
							</button>
						</div>
						<p v-if="codeSent" class="field-hint ok">
							{{ codeDelivery === 'log' ? $t('auth.codeLogHint') : $t('auth.codeSent') }}
						</p>
						<p v-else-if="mode === 'register'" class="field-hint">{{ $t('auth.registerCodeOptional') }}</p>
					</div>

					<p v-if="error" class="auth-error">{{ error }}</p>

					<button class="primary-button auth-submit" type="submit" :disabled="submitting">
						{{
							submitting
								? $t('auth.submitting')
								: mode === 'register'
									? $t('auth.register')
									: $t('auth.login')
						}}
					</button>
				</form>
			</div>
		</section>
	</main>
</template>

<style scoped>
.auth-section {
	max-width: 30rem;
	margin: 0 auto;
}

.auth-card {
	background: var(--card);
	border: 1px solid var(--card-border);
	border-radius: 14px;
	padding: 1.4rem 1.5rem 1.6rem;
}

.auth-tabs {
	display: flex;
	gap: 0.4rem;
	margin-bottom: 1rem;
}

.auth-tab {
	flex: 1;
	padding: 0.5rem;
	border: 1px solid var(--button-border);
	border-radius: 10px;
	background: var(--button-bg);
	color: var(--text);
	font: inherit;
	cursor: pointer;
	transition: background 0.2s ease;
}

.auth-tab.active {
	background: var(--accent);
	border-color: var(--accent);
	color: #fff;
}

.method-tabs {
	display: flex;
	gap: 0.4rem;
	margin-bottom: 1rem;
}

.method-tab {
	flex: 1;
	padding: 0.4rem;
	border: none;
	border-bottom: 2px solid transparent;
	background: none;
	color: var(--muted);
	font: inherit;
	cursor: pointer;
}

.method-tab.active {
	color: var(--text);
	border-bottom-color: var(--accent);
}

.auth-form {
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

.code-row {
	display: flex;
	gap: 0.5rem;
}

.code-row input {
	flex: 1;
	padding: 0.6rem 0.8rem;
	border-radius: 10px;
	border: 1px solid var(--button-border);
	background: var(--card);
	color: var(--text);
	font: inherit;
	outline: none;
}

.code-row input:focus {
	border-color: var(--accent);
}

.send-code-btn {
	flex-shrink: 0;
	padding: 0.6rem 0.9rem;
	border: 1px solid var(--button-border);
	border-radius: 10px;
	background: var(--button-bg);
	color: var(--text);
	font: inherit;
	font-size: 0.85rem;
	cursor: pointer;
	transition: background 0.2s ease;
}

.send-code-btn:hover {
	background: var(--panel-glow);
}

.send-code-btn:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

.field-hint {
	font-size: 0.78rem;
	color: var(--muted);
	margin: 0;
}

.field-hint.ok {
	color: var(--accent);
}

.auth-error {
	color: var(--danger, #e57373);
	font-size: 0.85rem;
	margin: 0;
}

.auth-submit {
	margin-top: 0.3rem;
	justify-content: center;
}

.auth-submit:disabled {
	opacity: 0.55;
	cursor: not-allowed;
}
</style>
