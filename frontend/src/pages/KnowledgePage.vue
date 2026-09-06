<script setup>
// 个人知识库模块（独立页面）：
// 上传文件（Word/PDF/Excel/txt）→ 后端自动切块向量化入知识库 → 这里展示「我的文件」清单。
// 上传与文件列表按账号隔离，仅登录可用。
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, ApiUnavailableError } from '../api'
import { useAuth } from '../composables/useAuth'

const { t, tm } = useI18n()
const { isLoggedIn } = useAuth()

// 知识库工作原理科普：query → 检索 → AI 作答，给非专业用户看的简化流程
const flowSteps = tm('knowledge.flowSteps')

// 文件上传入库
const uploading = ref(false)
const uploadResult = ref(null)
const uploadError = ref('')

async function onFileChange(event) {
	const file = event.target.files[0]
	if (!file) return
	uploading.value = true
	uploadError.value = ''
	uploadResult.value = null
	try {
		uploadResult.value = await api.uploadKnowledge(file)
		loadFiles()
	} catch (err) {
		uploadError.value =
			err instanceof ApiUnavailableError
				? t('knowledge.backendUnavailable')
				: err.message || t('knowledge.uploadFailed')
	} finally {
		uploading.value = false
		event.target.value = ''
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
					<label class="upload-btn" :class="{ disabled: uploading }">
						{{
							uploading
								? $t('knowledge.uploading')
								: $t('knowledge.uploadFile')
						}}
						<input type="file" accept=".txt,.md,.pdf,.docx,.xls,.xlsx" :disabled="uploading" @change="onFileChange" />
					</label>
					<span v-if="uploadResult" class="upload-ok">
						{{ $t('knowledge.uploaded', { fileName: uploadResult.fileName, chunkCount: uploadResult.chunkCount }) }}
					</span>
					<span v-if="uploadError" class="upload-err">{{ uploadError }}</span>
				</template>
				<RouterLink v-else class="upload-btn login-to-upload" :to="'/login?redirect=/knowledge'">
					{{ $t('auth.loginToUpload') }}
				</RouterLink>
			</div>

			<div v-if="isLoggedIn" class="file-list">
				<p v-if="myFiles.length === 0" class="file-list-empty">{{ $t('knowledge.noFiles') }}</p>
				<div v-for="f in myFiles" :key="f.id" class="file-row">
					<span class="file-name" :title="f.fileName">{{ f.fileName }}</span>
					<span class="file-meta">{{ f.charCount }} {{ $t('knowledge.chars') }} · {{ f.chunkCount }} {{ $t('knowledge.chunks') }}</span>
					<button class="file-delete" type="button" :disabled="deletingId === f.id" @click="deleteFile(f)">
						{{ $t('knowledge.deleteFile') }}
					</button>
				</div>
			</div>
		</section>
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

.file-delete:hover {
	background: var(--panel-glow);
	color: var(--danger, #e57373);
}

.file-delete:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}
</style>
