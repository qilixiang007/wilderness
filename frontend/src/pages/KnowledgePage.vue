<script setup>
// 个人知识库模块（独立页面）：
// 上传文件（Word/PDF/Excel/txt）→ 后端自动切块向量化入知识库 → 这里展示「我的文件」清单。
// 上传与文件列表按账号隔离，仅登录可用。
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, ApiUnavailableError } from '../api'
import { useAuth } from '../composables/useAuth'
import { useConfirm } from '../composables/useConfirm'

const { t, tm } = useI18n()
const { isLoggedIn } = useAuth()
const { confirm } = useConfirm()

// 跟后端 KnowledgeUploadService.MAX_FILES_PER_USER 保持一致
const MAX_KNOWLEDGE_FILES = 20

// 知识库工作原理科普：query → 检索 → AI 作答，给非专业用户看的简化流程
const flowSteps = tm('knowledge.flowSteps')

// 文件上传入库：一次可选多个文件，一次请求整批提交。
// 单个文件失败不影响同批其它文件，后端逐文件返回结果，这里按文件展示。
const MAX_BATCH_SIZE = 5

const uploading = ref(false)
const batchResult = ref(null)
const uploadError = ref('')
const fileInputEl = ref(null)

async function onFileChange(event) {
	const files = Array.from(event.target.files)
	event.target.value = ''
	if (files.length === 0) return

	uploadError.value = ''
	batchResult.value = null
	// 超出单批上限：浏览器原生选择框没法限制勾选数量，只能选完之后再拦——
	// 用弹窗代替行内文字提示，并提供"重新选择"直接拉起选择框，省得用户自己再点一次上传按钮。
	if (files.length > MAX_BATCH_SIZE) {
		const reselect = await confirm(
			t('knowledge.batchTooMany', { max: MAX_BATCH_SIZE, count: files.length }),
			{ confirmText: t('knowledge.reselect'), cancelText: t('common.cancel') }
		)
		if (reselect) fileInputEl.value?.click()
		return
	}
	// 与已有文件同名属于覆盖重传，不占新名额（与后端 checkBatchQuota 规则一致）
	const existingNames = new Set(myFiles.value.map((f) => f.fileName))
	const newCount = new Set(files.map((f) => f.name).filter((name) => !existingNames.has(name))).size
	const remaining = MAX_KNOWLEDGE_FILES - myFiles.value.length
	if (newCount > remaining) {
		uploadError.value = t('knowledge.batchOverQuota', { newCount, remaining: Math.max(0, remaining) })
		return
	}

	uploading.value = true
	try {
		batchResult.value = await api.uploadKnowledge(files)
		loadFiles()
	} catch (err) {
		uploadError.value =
			err instanceof ApiUnavailableError
				? t('knowledge.backendUnavailable')
				: err.message || t('knowledge.uploadFailed')
	} finally {
		uploading.value = false
	}
}

// —— 我的文件（按用户隔离，仅登录可见）——
const myFiles = ref([])
const deletingId = ref(null)

async function loadFiles() {
	if (!isLoggedIn.value) {
		myFiles.value = []
		return
	}
	try {
		myFiles.value = await api.getKnowledgeFiles()
	} catch {
		myFiles.value = []
	}
}

async function deleteFile(file) {
	if (deletingId.value) return
	if (!(await confirm(t('knowledge.deleteFileConfirm')))) return
	deletingId.value = file.id
	try {
		await api.deleteKnowledgeFile(file.id)
		myFiles.value = myFiles.value.filter((f) => f.id !== file.id)
	} catch (err) {
		uploadError.value = err.message || t('knowledge.deleteFailed')
	} finally {
		deletingId.value = null
	}
}

// —— 文件预览(按知识库分块顺序展示,而非还原原始文件)——
const previewOpen = ref(false)
const previewData = ref(null)
const previewLoading = ref(false)
const previewError = ref('')

async function openPreview(file) {
	previewOpen.value = true
	previewData.value = null
	previewError.value = ''
	previewLoading.value = true
	try {
		previewData.value = await api.previewKnowledgeFile(file.id)
	} catch (err) {
		previewError.value = err.message || t('knowledge.previewFailed')
	} finally {
		previewLoading.value = false
	}
}

function closePreview() {
	previewOpen.value = false
}

onMounted(loadFiles)
watch(isLoggedIn, (v) => {
	if (v) loadFiles()
	else myFiles.value = []
})
</script>

<template>
	<main>
		<section class="section-block flow-card">
			<div class="section-heading compact">
				<p class="eyebrow">{{ $t('knowledge.flowKicker') }}</p>
				<h4>{{ $t('knowledge.flowTitle') }}</h4>
			</div>
			<ol class="flow-steps">
				<li v-for="(step, i) in flowSteps" :key="i" class="flow-step">
					<span class="flow-step-num">{{ i + 1 }}</span>
					<div class="flow-step-body">
						<p class="flow-step-title">{{ step.title }}</p>
						<p class="flow-step-desc">{{ step.desc }}</p>
					</div>
				</li>
			</ol>
			<p class="flow-note">{{ $t('knowledge.flowNote') }}</p>
		</section>

		<section class="section-block knowledge-section">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('knowledge.kicker') }}</p>
				<h3>{{ $t('knowledge.title') }}</h3>
				<p>{{ $t('knowledge.intro') }}</p>
			</div>

			<div class="upload-bar">
				<template v-if="isLoggedIn">
					<label class="upload-btn" :class="{ disabled: uploading || myFiles.length >= MAX_KNOWLEDGE_FILES }">
						{{
							uploading
								? $t('knowledge.uploading')
								: $t('knowledge.uploadFile')
						}}
						<input
							ref="fileInputEl"
							type="file"
							multiple
							accept=".txt,.md,.pdf,.docx,.xls,.xlsx"
							:disabled="uploading || myFiles.length >= MAX_KNOWLEDGE_FILES"
							@change="onFileChange"
						/>
					</label>
					<span class="file-quota">
						{{ $t('knowledge.fileQuota', { used: myFiles.length, max: MAX_KNOWLEDGE_FILES }) }}
						· {{ $t('knowledge.uploadHint', { max: MAX_BATCH_SIZE }) }}
					</span>
					<span v-if="batchResult" :class="batchResult.failed > 0 ? 'upload-err' : 'upload-ok'">
						{{ $t('knowledge.batchSummary', { succeeded: batchResult.succeeded, failed: batchResult.failed }) }}
					</span>
					<span v-if="uploadError" class="upload-err">{{ uploadError }}</span>
				</template>
				<RouterLink v-else class="upload-btn login-to-upload" :to="'/login?redirect=/knowledge'">
					{{ $t('auth.loginToUpload') }}
				</RouterLink>
			</div>

			<div v-if="batchResult && batchResult.failed > 0" class="batch-result">
				<div v-for="(item, i) in batchResult.items.filter((x) => !x.success)" :key="i" class="batch-row">
					<span class="file-name" :title="item.fileName">{{ item.fileName }}</span>
					<span class="batch-fail" :title="item.error">{{ item.error }}</span>
				</div>
			</div>

			<div v-if="isLoggedIn" class="file-list">
				<p v-if="myFiles.length === 0" class="file-list-empty">{{ $t('knowledge.noFiles') }}</p>
				<div v-for="f in myFiles" :key="f.id" class="file-row">
					<span class="file-name" :title="f.fileName">{{ f.fileName }}</span>
					<span class="file-meta">{{ f.charCount }} {{ $t('knowledge.chars') }} · {{ f.chunkCount }} {{ $t('knowledge.chunks') }}</span>
					<button class="file-preview" type="button" @click="openPreview(f)">
						{{ $t('knowledge.previewFile') }}
					</button>
					<button class="file-delete" type="button" :disabled="deletingId === f.id" @click="deleteFile(f)">
						{{ $t('knowledge.deleteFile') }}
					</button>
				</div>
			</div>
		</section>

		<div v-if="previewOpen" class="preview-overlay" @click.self="closePreview">
			<div class="preview-panel">
				<div class="preview-header">
					<h4 class="preview-title" :title="previewData?.fileName">
						{{ previewData?.fileName || $t('knowledge.previewTitle') }}
					</h4>
					<button class="preview-close" type="button" @click="closePreview">{{ $t('knowledge.previewClose') }}</button>
				</div>
				<p v-if="previewLoading" class="preview-status">{{ $t('knowledge.previewLoading') }}</p>
				<p v-else-if="previewError" class="preview-status preview-error">{{ previewError }}</p>
				<template v-else-if="previewData">
					<p v-if="previewData.chunks.length < previewData.chunkCount" class="preview-note">
						{{ $t('knowledge.previewTruncated', { shown: previewData.chunks.length, total: previewData.chunkCount }) }}
					</p>
					<div v-if="previewData.chunks.length === 0" class="preview-status">{{ $t('knowledge.previewEmpty') }}</div>
					<div v-else class="preview-body">
						<div v-for="(chunk, i) in previewData.chunks" :key="i" class="preview-chunk">
							<p class="preview-chunk-label">
								{{ $t('knowledge.previewSegment', { index: i + 1, total: previewData.chunkCount }) }}
							</p>
							<p class="preview-chunk-text">{{ chunk }}</p>
						</div>
					</div>
				</template>
			</div>
		</div>
	</main>
</template>

<style scoped>
.flow-steps {
	list-style: none;
	margin: 0;
	padding: 0;
	display: grid;
	grid-template-columns: repeat(4, minmax(0, 1fr));
	gap: 1.1rem 1.6rem;
}

@media (max-width: 1100px) {
	.flow-steps {
		grid-template-columns: repeat(2, minmax(0, 1fr));
	}
}

.flow-step {
	display: flex;
	align-items: flex-start;
	gap: 0.7rem;
}

.flow-step-num {
	flex-shrink: 0;
	width: 1.6rem;
	height: 1.6rem;
	border-radius: 50%;
	background: var(--accent);
	color: var(--accent-contrast);
	font-size: 0.8rem;
	font-weight: 700;
	display: flex;
	align-items: center;
	justify-content: center;
}

.flow-step-title {
	margin: 0 0 0.2rem;
	font-weight: 600;
	color: var(--text);
	font-size: 0.92rem;
}

.flow-step-desc {
	margin: 0;
	font-size: 0.82rem;
	line-height: 1.65;
	color: var(--muted);
}

.flow-note {
	margin: 1.2rem 0 0;
	padding-top: 0.9rem;
	border-top: 1px solid var(--card-border);
	font-size: 0.82rem;
	line-height: 1.6;
	color: var(--muted);
}

@media (max-width: 640px) {
	.flow-steps {
		grid-template-columns: 1fr;
	}
}

.upload-bar {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 0.6rem;
	margin-bottom: 0.25rem;
}

.upload-btn {
	position: relative;
	display: inline-flex;
	align-items: center;
	gap: 0.4rem;
	padding: 0.4rem 0.9rem;
	border: 1px solid var(--button-border);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--text);
	font-size: 0.85rem;
	cursor: pointer;
	transition: background 0.2s ease;
}

.upload-btn:hover {
	background: var(--panel-glow);
}

.upload-btn.disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

.upload-btn input[type='file'] {
	position: absolute;
	inset: 0;
	opacity: 0;
	cursor: pointer;
}

.upload-btn.disabled input {
	cursor: not-allowed;
}

.upload-ok {
	font-size: 0.85rem;
	color: var(--accent);
}

.upload-err {
	font-size: 0.85rem;
	color: var(--muted);
}

.file-quota {
	font-size: 0.78rem;
	color: var(--muted);
}

.batch-result {
	margin: 0.2rem 0 0.6rem;
}

.batch-row {
	display: flex;
	align-items: center;
	gap: 0.6rem;
	padding: 0.3rem 0;
	font-size: 0.82rem;
}

.batch-ok {
	flex-shrink: 0;
	color: var(--accent);
}

.batch-fail {
	flex-shrink: 0;
	max-width: 60%;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	color: var(--muted);
}

.login-to-upload {
	text-decoration: none;
	display: inline-flex;
}

.file-list {
	margin: 0.4rem 0 0.9rem;
	border-top: 1px solid var(--card-border);
}

.file-list-empty {
	font-size: 0.85rem;
	color: var(--muted);
	padding: 0.6rem 0 0.2rem;
	margin: 0;
}

.file-row {
	display: flex;
	align-items: center;
	gap: 0.6rem;
	padding: 0.45rem 0;
	border-bottom: 1px solid var(--card-border);
	font-size: 0.85rem;
}

.file-name {
	flex: 1;
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	color: var(--text);
}

.file-meta {
	flex-shrink: 0;
	color: var(--muted);
	font-size: 0.78rem;
}

.file-preview,
.file-delete {
	flex-shrink: 0;
	padding: 0.25rem 0.7rem;
	border: 1px solid var(--button-border);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--muted);
	font: inherit;
	font-size: 0.78rem;
	cursor: pointer;
	transition: background 0.2s ease, color 0.2s ease;
}

.file-preview:hover {
	background: var(--panel-glow);
	color: var(--text);
}

.file-delete:hover {
	background: var(--panel-glow);
	color: var(--danger, #e57373);
}

.file-delete:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

.preview-overlay {
	position: fixed;
	inset: 0;
	background: rgba(0, 0, 0, 0.5);
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 1.5rem;
	z-index: 100;
}

.preview-panel {
	width: min(40rem, 100%);
	max-height: 80vh;
	display: flex;
	flex-direction: column;
	background: var(--bg-soft);
	border: 1px solid var(--card-border);
	border-radius: 0.75rem;
	overflow: hidden;
}

.preview-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 0.6rem;
	padding: 0.8rem 1rem;
	border-bottom: 1px solid var(--card-border);
}

.preview-title {
	margin: 0;
	font-size: 0.95rem;
	color: var(--text);
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.preview-close {
	flex-shrink: 0;
	padding: 0.25rem 0.7rem;
	border: 1px solid var(--button-border);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--muted);
	font: inherit;
	font-size: 0.78rem;
	cursor: pointer;
}

.preview-close:hover {
	background: var(--panel-glow);
	color: var(--text);
}

.preview-status {
	padding: 1.2rem 1rem;
	margin: 0;
	font-size: 0.85rem;
	color: var(--muted);
}

.preview-error {
	color: var(--danger, #e57373);
}

.preview-note {
	margin: 0;
	padding: 0.5rem 1rem 0;
	font-size: 0.78rem;
	color: var(--muted);
}

.preview-body {
	overflow-y: auto;
	padding: 0.4rem 1rem 1rem;
}

.preview-chunk {
	padding: 0.6rem 0;
	border-bottom: 1px solid var(--card-border);
}

.preview-chunk:last-child {
	border-bottom: none;
}

.preview-chunk-label {
	margin: 0 0 0.3rem;
	font-size: 0.72rem;
	color: var(--accent);
	letter-spacing: 0.02em;
}

.preview-chunk-text {
	margin: 0;
	font-size: 0.85rem;
	line-height: 1.6;
	color: var(--text);
	white-space: pre-wrap;
	word-break: break-word;
}
</style>
