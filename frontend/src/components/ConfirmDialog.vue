<script setup>
import { useConfirm } from '../composables/useConfirm'

const { state, respond } = useConfirm()
</script>

<template>
	<div v-if="state.open" class="confirm-overlay" @click.self="respond(false)">
		<div class="confirm-panel" role="alertdialog" aria-modal="true">
			<p class="confirm-message">{{ state.message }}</p>
			<div class="confirm-actions">
				<button class="secondary-button" type="button" @click="respond(false)">
					{{ state.cancelText || $t('common.cancel') }}
				</button>
				<button class="confirm-danger-btn" type="button" @click="respond(true)">
					{{ state.confirmText || $t('common.confirmDelete') }}
				</button>
			</div>
		</div>
	</div>
</template>

<style scoped>
.confirm-overlay {
	position: fixed;
	inset: 0;
	background: rgba(0, 0, 0, 0.5);
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 1.5rem;
	z-index: 200;
}

.confirm-panel {
	width: min(24rem, 100%);
	background: var(--bg-soft);
	border: 1px solid var(--card-border);
	border-radius: 0.9rem;
	padding: 1.3rem 1.4rem;
}

.confirm-message {
	margin: 0;
	font-size: 0.92rem;
	line-height: 1.6;
	color: var(--text);
}

.confirm-actions {
	display: flex;
	justify-content: flex-end;
	gap: 0.6rem;
	margin-top: 1.2rem;
}

.confirm-danger-btn {
	padding: 0.4rem 1rem;
	border: 1px solid var(--danger, #e57373);
	border-radius: 999px;
	background: var(--danger, #e57373);
	color: #fff;
	font: inherit;
	font-size: 0.85rem;
	cursor: pointer;
	transition: opacity 0.2s ease;
}

.confirm-danger-btn:hover {
	opacity: 0.85;
}
</style>
