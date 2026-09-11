<script setup>
// 天体生成 Agent 页面（独立一级模块）：
// 类型 chips + 参数表单 + 自然语言描述三者结合输入 → 后端 Agent 检索真实天体作参考 → 生成结果。
// 结果展示：Agent 执行轨迹 → 天体视觉（文生图或 SVG）→ 参数卡片 → Markdown 介绍 → 参考来源跳转。
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import MarkdownView from '../components/MarkdownView.vue'
import CelestialVisual from '../components/CelestialVisual.vue'
import SelectDropdown from '../components/SelectDropdown.vue'
import { repairRender } from '../utils/renderRepair'
import { api, ApiUnavailableError } from '../api'
import { useAuth } from '../composables/useAuth'
import { useFavorites } from '../composables/useFavorites'
import { useConfirm } from '../composables/useConfirm'

const { t, tm } = useI18n()
const { confirm } = useConfirm()

// 类型 chips 文案走 i18n（双语）
const typeChips = tm('agent.typeChips')

// 输入（两者结合：chips + 表单 + 描述框）
const selectedType = ref('')
const mass = ref('')
const radius = ref('')
const temperature = ref('')
// 马卡龙色系（柔和低饱和），默认第一个，点选圆形色块即用
const macaronColors = ['#f2a6b8', '#c9b8e8', '#a8d8d0', '#f5e6ca']
const color = ref(macaronColors[0])
// 是否主动点过色板：点过才把该颜色当权威约束注入描述并覆盖画面（默认色不再强行生效）
const colorTouched = ref(false)
const description = ref('')

// 状态机
const status = ref('idle') // idle | generating | ready | error
const error = ref('')
const result = ref(null)
// 视觉用 render：对模型原始 render 做确定性兜底（主色权威 + 卫星解析）后传给 CelestialVisual
const visualRender = ref(null)

const samples = tm('agent.samples')

// —— 自定义智能体（登录后可用；未登录走内置路径）——
const { isLoggedIn, refreshMe } = useAuth()
// —— 收藏自建天体：result.historyId 存在才可收藏（未登录/落库失败时为 null，不展示按钮）——
const { isFavoriteGeneration, toggleGeneration } = useFavorites()
const favoriting = ref(false)
const myAgents = ref([])
const selectedAgentId = ref('') // '' = 内置智能体
const editing = ref(null) // null | { id }，id 为 null 表示新建
const agentForm = ref({ name: '', systemPrompt: '', knowledgeSearchEnabled: true, imageGenEnabled: false })
const saving = ref(false)
const agentError = ref('')

const agentOptions = computed(() => [
	{ value: '', label: t('agents.defaultAgent'), sub: t('agents.builtinHint') },
	...myAgents.value.map((a) => ({
		value: String(a.id),
		label: a.name,
		sub: [
			a.knowledgeSearchEnabled ? t('agents.knowledgeSearch') : '',
			a.imageGenEnabled ? t('agents.imageGen') : ''
		].filter(Boolean).join(' · ')
	}))
])

async function loadAgents() {
	if (!isLoggedIn.value) return
	try {
		myAgents.value = await api.getAgents()
	} catch {
		myAgents.value = []
	}
}

onMounted(async () => {
	await refreshMe()
	loadAgents()
})
watch(isLoggedIn, (v) => {
	if (v) loadAgents()
})

function startCreate() {
	editing.value = { id: null }
	agentForm.value = { name: '', systemPrompt: '', knowledgeSearchEnabled: true, imageGenEnabled: false }
	agentError.value = ''
}

function startEdit(a) {
	editing.value = { id: a.id }
	agentForm.value = {
		name: a.name,
		systemPrompt: a.systemPrompt,
		knowledgeSearchEnabled: a.knowledgeSearchEnabled,
		imageGenEnabled: a.imageGenEnabled
	}
	agentError.value = ''
}

function cancelEdit() {
	editing.value = null
	agentError.value = ''
}

async function saveAgent() {
	const name = agentForm.value.name.trim()
	const persona = agentForm.value.systemPrompt.trim()
	if (!name) {
		agentError.value = t('agents.nameRequired')
		return
	}
	if (!persona) {
		agentError.value = t('agents.personaRequired')
		return
	}
	saving.value = true
	agentError.value = ''
	const payload = {
		name,
		systemPrompt: persona,
		knowledgeSearchEnabled: agentForm.value.knowledgeSearchEnabled,
		imageGenEnabled: agentForm.value.imageGenEnabled
	}
	try {
		if (editing.value.id) {
			const updated = await api.updateAgent(editing.value.id, payload)
			const idx = myAgents.value.findIndex((a) => a.id === updated.id)
			if (idx >= 0) myAgents.value[idx] = updated
		} else {
			const created = await api.createAgent(payload)
			myAgents.value.unshift(created)
			selectedAgentId.value = String(created.id) // 新建后立即选中
		}
		editing.value = null
	} catch (e) {
		agentError.value =
			e instanceof ApiUnavailableError ? t('agents.backendUnavailable') : e.message || t('agents.saveFailed')
	} finally {
		saving.value = false
	}
}

async function confirmDelete(a) {
	if (!(await confirm(t('agents.deleteConfirm')))) return
	try {
		await api.deleteAgent(a.id)
		myAgents.value = myAgents.value.filter((x) => x.id !== a.id)
		if (selectedAgentId.value === String(a.id)) selectedAgentId.value = ''
	} catch (e) {
		agentError.value =
			e instanceof ApiUnavailableError ? t('agents.backendUnavailable') : e.message || t('agents.deleteFailed')
	}
}

function buildDescription() {
	const parts = []
	if (description.value.trim()) parts.push(description.value.trim())
	if (selectedType.value) parts.push(selectedType.value)
	if (mass.value.trim()) parts.push(`质量${mass.value.trim()}`)
	if (radius.value.trim()) parts.push(`半径${radius.value.trim()}`)
	if (temperature.value.trim()) parts.push(`表面温度${temperature.value.trim()}`)
	// 只有用户主动点过色板才把颜色写进描述（否则默认色会污染纯打字的请求）
	if (colorTouched.value && color.value) parts.push(`颜色${color.value}`)
	return parts.join('，')
}

function pickColor(c) {
	color.value = c
	colorTouched.value = true
}

// 只有真正点过色板才显示"已选中"，避免默认色块（未点击）被误认成已生效的选择
function colorActive(c) {
	return colorTouched.value && color.value === c
}

function fillSample(text) {
	description.value = text
}

function clearAll() {
	selectedType.value = ''
	mass.value = ''
	radius.value = ''
	temperature.value = ''
	color.value = macaronColors[0]
	colorTouched.value = false
	description.value = ''
	visualRender.value = null
}

async function generate() {
	const desc = buildDescription()
	if (!desc) {
		error.value = t('agent.descRequired')
		return
	}
	status.value = 'generating'
	error.value = ''
	try {
		const res = selectedAgentId.value
			? await api.generateCelestialWithAgent(selectedAgentId.value, desc)
			: await api.generateCelestial(desc)
		result.value = res
		// 视觉单独走确定性兜底：用户点选的颜色权威覆盖，卫星从描述解析补上
		visualRender.value = repairRender(res.render, {
			userColor: colorTouched.value ? color.value : '',
			desc,
			paramsText: Object.entries(res.parameters || {}).map(([k, v]) => `${k}${v}`).join(' ')
		})
		status.value = 'ready'
		backfillForm(res)
	} catch (e) {
		status.value = 'error'
		error.value =
			e instanceof ApiUnavailableError ? t('agent.backendUnavailable') : e.message || t('agent.generateFailed')
	}
}

// Agent 识别自然语言后回填表单（展示 Agent 的参数解析能力）
function backfillForm(res) {
	const p = res.parameters || {}
	if (!mass.value && p['质量']) mass.value = p['质量']
	if (!radius.value && p['半径']) radius.value = p['半径']
	if (!temperature.value && p['表面温度']) temperature.value = p['表面温度']
	if (!selectedType.value && res.type) selectedType.value = res.type
}

async function onToggleFavorite() {
	if (!result.value?.historyId || favoriting.value) return
	favoriting.value = true
	try {
		await toggleGeneration({ historyId: result.value.historyId })
	} finally {
		favoriting.value = false
	}
}
</script>

<template>
	<main>
		<section class="section-block agent-section">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('agent.kicker') }}</p>
				<h3>{{ $t('agent.title') }}</h3>
				<p>{{ $t('agent.intro') }}</p>
			</div>

			<!-- 自定义智能体面板（登录可见）：管理 + 选择生成所用 Agent -->
			<div v-if="isLoggedIn" class="agents-panel">
				<div class="agents-head">
					<h4 class="agents-title">{{ $t('agents.myAgents') }}</h4>
					<button class="secondary-button" type="button" @click="startCreate">{{ $t('agents.create') }}</button>
				</div>

				<div class="agent-select-row">
					<span class="field-label">{{ $t('agents.select') }}</span>
					<SelectDropdown
						:model-value="selectedAgentId"
						:options="agentOptions"
						:aria-label="$t('agents.select')"
						@update:model-value="selectedAgentId = $event"
					>
						<template #item="{ option }">
							<span class="opt-label">{{ option.label }}</span>
							<span v-if="option.sub" class="opt-sub">{{ option.sub }}</span>
						</template>
					</SelectDropdown>
				</div>

				<div v-if="myAgents.length" class="agent-list">
					<div
						v-for="a in myAgents"
						:key="a.id"
						class="agent-row"
						:class="{ selected: selectedAgentId === String(a.id) }"
					>
						<div class="agent-row-main">
							<span class="agent-name">{{ a.name }}</span>
							<span class="agent-toggles">
								<span class="toggle-badge" :class="{ off: !a.knowledgeSearchEnabled }">
									{{ $t('agents.knowledgeSearch') }}
								</span>
								<span class="toggle-badge" :class="{ off: !a.imageGenEnabled }">
									{{ $t('agents.imageGen') }}
								</span>
							</span>
						</div>
						<div class="agent-row-actions">
							<button class="text-button" type="button" @click="startEdit(a)">{{ $t('agents.edit') }}</button>
							<button class="text-button danger" type="button" @click="confirmDelete(a)">
								{{ $t('agents.delete') }}
							</button>
						</div>
					</div>
				</div>
				<p v-else-if="!editing" class="field-hint agents-empty">{{ $t('agents.empty') }}</p>

				<!-- 创建 / 编辑表单 -->
				<form v-if="editing" class="agent-form" @submit.prevent="saveAgent">
					<label class="field">
						<span class="field-label">{{ $t('agents.name') }}</span>
						<input
							v-model="agentForm.name"
							type="text"
							:placeholder="$t('agents.namePlaceholder')"
							maxlength="50"
						/>
					</label>
					<label class="field">
						<span class="field-label">{{ $t('agents.persona') }}</span>
						<textarea
							v-model="agentForm.systemPrompt"
							:placeholder="$t('agents.personaPlaceholder')"
							rows="4"
							maxlength="4000"
						></textarea>
					</label>
					<div class="form-toggles">
						<label class="web-toggle" :class="{ checked: agentForm.knowledgeSearchEnabled }">
							<input v-model="agentForm.knowledgeSearchEnabled" type="checkbox" />
							<span class="toggle-track"><span class="toggle-thumb"></span></span>
							<span class="toggle-label">{{ $t('agents.knowledgeSearch') }}</span>
						</label>
						<label class="web-toggle" :class="{ checked: agentForm.imageGenEnabled }">
							<input v-model="agentForm.imageGenEnabled" type="checkbox" />
							<span class="toggle-track"><span class="toggle-thumb"></span></span>
							<span class="toggle-label">{{ $t('agents.imageGen') }}</span>
						</label>
					</div>
					<div class="form-actions">
						<button class="primary-button" type="submit" :disabled="saving">
							{{ editing.id ? $t('agents.save') : $t('agents.create') }}
						</button>
						<button class="secondary-button" type="button" :disabled="saving" @click="cancelEdit">
							{{ $t('agents.cancel') }}
						</button>
					</div>
					<p v-if="agentError" class="agent-error">{{ agentError }}</p>
				</form>
			</div>

			<!-- 输入区 -->
			<div class="agent-input">
				<div class="type-chips">
					<button
						v-for="c in typeChips"
						:key="c"
						class="type-chip"
						:class="{ active: selectedType === c }"
						type="button"
						:disabled="status === 'generating'"
						@click="selectedType = selectedType === c ? '' : c"
					>
						{{ c }}
					</button>
				</div>

				<div class="param-grid">
					<label class="field">
						<span class="field-label">{{ $t('agent.mass') }}</span>
						<input v-model="mass" type="text" :placeholder="'3 × 太阳'" :disabled="status === 'generating'" />
					</label>
					<label class="field">
						<span class="field-label">{{ $t('agent.radius') }}</span>
						<input v-model="radius" type="text" :placeholder="'0.8 × 太阳'" :disabled="status === 'generating'" />
					</label>
					<label class="field">
						<span class="field-label">{{ $t('agent.temperature') }}</span>
						<input v-model="temperature" type="text" :placeholder="'3500 K'" :disabled="status === 'generating'" />
					</label>
					<label class="field">
						<span class="field-label">{{ $t('agent.color') }}</span>
						<!-- 马卡龙圆形色板：默认第一色，点选即换；圆润样式贴合项目 UI -->
						<div class="color-swatches">
							<button
								v-for="c in macaronColors"
								:key="c"
								class="color-swatch"
								:class="{ active: colorActive(c) }"
								:style="{ background: c }"
								type="button"
								:disabled="status === 'generating'"
								:aria-label="c"
								:title="c"
								@click="pickColor(c)"
							></button>
						</div>
					</label>
				</div>

				<label class="field desc-field">
					<span class="field-label">{{ $t('agent.naturalDesc') }}</span>
					<textarea
						v-model="description"
						:placeholder="$t('agent.descPlaceholder')"
						:disabled="status === 'generating'"
						rows="3"
					></textarea>
				</label>

				<p class="field-hint">{{ $t('agent.descOrForm') }}</p>

				<div class="agent-actions">
					<button class="primary-button" type="button" :disabled="status === 'generating'" @click="generate">
						{{ status === 'generating' ? $t('agent.generating') : $t('agent.generate') }}
					</button>
					<button class="secondary-button" type="button" :disabled="status === 'generating'" @click="clearAll">
						{{ $t('agent.clear') }}
					</button>
				</div>

				<p v-if="error" class="agent-error">{{ error }}</p>
			</div>

			<!-- 生成中提示 -->
			<div v-if="status === 'generating'" class="agent-wait">
				<span class="dot" v-for="i in 3" :key="i"></span>
				<p>{{ $t('agent.generatingHint') }}</p>
			</div>

			<!-- 结果区 -->
			<template v-if="status === 'ready' && result">
				<!-- Agent 执行轨迹 -->
				<div class="result-block">
					<h4 class="result-title">{{ $t('agent.steps') }}</h4>
					<ol class="step-list">
						<li v-for="(s, i) in result.steps" :key="i" class="step-item">
							<span class="step-phase">{{ s.phase }}</span>
							<span class="step-detail">{{ s.detail }}</span>
						</li>
					</ol>
				</div>

				<!-- 天体视觉 + 基本信息 -->
				<div class="result-block visual-block">
					<div class="result-name-row">
						<h4 class="result-title">{{ result.name }}</h4>
						<button
							v-if="result.historyId"
							class="favorite-toggle"
							type="button"
							:disabled="favoriting"
							@click="onToggleFavorite"
						>
							{{ isFavoriteGeneration(result.historyId) ? $t('agent.unfavorite') : $t('agent.favorite') }}
						</button>
					</div>
					<div class="visual-panel">
						<img v-if="result.imageUrl" :src="result.imageUrl" :alt="result.name" class="ai-image" />
						<CelestialVisual v-else :render="visualRender || result.render || {}" :name="result.name" />
					</div>
					<p v-if="!result.success" class="visual-caption image-temporary-hint">
						{{ $t('agent.generationFailedImageHint') }}
					</p>
					<p class="visual-caption">
						{{ $t('agent.imageHint') }}
					</p>
					<p v-if="result.imageUrl && result.imageTemporary" class="visual-caption image-temporary-hint">
						{{ $t('agent.imageTemporaryHint') }}
					</p>
					<p v-if="!result.imageUrl && visualRender?.truncatedFrom" class="visual-caption">
						{{ $t('agent.satellitesTruncated', { n: visualRender.truncatedFrom, max: visualRender.satellites.length }) }}
					</p>
				</div>

				<!-- 参数卡片 -->
				<div class="result-block">
					<h4 class="result-title">{{ $t('agent.parameters') }}</h4>
					<dl v-if="result.parameters && Object.keys(result.parameters).length" class="facts-list">
						<div v-for="(v, k) in result.parameters" :key="k" class="fact-row">
							<dt>{{ k }}</dt>
							<dd>{{ v }}</dd>
						</div>
					</dl>
				</div>

				<!-- Markdown 介绍 -->
				<div v-if="result.introduction" class="result-block">
					<h4 class="result-title">{{ $t('agent.introduction') }}</h4>
					<MarkdownView :content="result.introduction" />
				</div>

				<!-- 参考的真实天体 -->
				<div v-if="result.sources && result.sources.length" class="result-block">
					<h4 class="result-title">{{ $t('agent.sources') }}</h4>
					<div class="agent-sources">
						<RouterLink
							v-for="(s, i) in result.sources"
							:key="i"
							:to="`/object/${s.slug}`"
							class="source-chip"
							:title="s.excerpt"
						>
							{{ s.title }}<span class="source-type">{{ s.type }}</span>
						</RouterLink>
					</div>
				</div>
			</template>

			<!-- 空态：示例描述 -->
			<div v-if="status === 'idle'" class="agent-empty">
				<p class="field-hint">{{ $t('agent.trySamples') }}</p>
				<button v-for="(s, i) in samples" :key="i" class="suggestion-chip" type="button" @click="fillSample(s)">
					{{ s }}
				</button>
			</div>
		</section>
	</main>
</template>

<style scoped>
.agent-section {
	max-width: 46rem;
	margin: 0 auto;
}

.agent-input {
	display: flex;
	flex-direction: column;
	gap: 1rem;
	margin-bottom: 1.6rem;
}

.type-chips {
	display: flex;
	flex-wrap: wrap;
	gap: 0.45rem;
}

.type-chip {
	padding: 0.45rem 0.9rem;
	border: 1px solid var(--button-border);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--text);
	font: inherit;
	font-size: 0.88rem;
	cursor: pointer;
	transition: background 0.2s ease;
}

.type-chip:hover {
	background: var(--panel-glow);
}

.type-chip.active {
	background: var(--accent);
	border-color: var(--accent);
	color: #fff;
}

.type-chip:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

.param-grid {
	display: grid;
	grid-template-columns: repeat(4, minmax(0, 1fr));
	gap: 0.8rem;
}

@media (max-width: 640px) {
	.param-grid {
		grid-template-columns: repeat(2, minmax(0, 1fr));
	}
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

.field input[type='text'],
.field textarea {
	padding: 0.6rem 0.8rem;
	border-radius: 10px;
	border: 1px solid var(--button-border);
	background: var(--card);
	color: var(--text);
	font: inherit;
	outline: none;
	resize: vertical;
}

.field input[type='text']:focus,
.field textarea:focus {
	border-color: var(--accent);
}

/* 马卡龙圆形色板：胶囊容器 + 圆形色块，选中用 accent 双圈描边 */
.color-swatches {
	display: flex;
	align-items: center;
	gap: 0.55rem;
	padding: 0.45rem 0.7rem;
	border-radius: 999px;
	border: 1px solid var(--card-border);
	background: var(--card);
}

.color-swatch {
	width: 1.8rem;
	height: 1.8rem;
	padding: 0;
	border-radius: 50%;
	border: 2px solid var(--card);
	cursor: pointer;
	transition: transform 0.15s ease;
}

.color-swatch:hover {
	transform: scale(1.08);
}

.color-swatch.active {
	border-color: var(--accent);
	box-shadow: 0 0 0 1.5px var(--card), 0 0 0 3px var(--accent);
}

.color-swatch:disabled {
	opacity: 0.55;
	cursor: not-allowed;
}

.field-hint {
	font-size: 0.8rem;
	color: var(--muted);
	margin: 0;
}

.agent-actions {
	display: flex;
	gap: 0.6rem;
	align-items: center;
}

.agent-error {
	color: var(--danger, #e57373);
	font-size: 0.85rem;
	margin: 0;
}

/* 生成中的等待动画 */
.agent-wait {
	display: flex;
	align-items: center;
	gap: 0.5rem;
	padding: 1.2rem;
	border-radius: 14px;
	border: 1px solid var(--card-border);
	background: var(--card);
	margin-bottom: 1.6rem;
}

.agent-wait p {
	margin: 0;
	font-size: 0.9rem;
	color: var(--muted);
}

.dot {
	width: 8px;
	height: 8px;
	border-radius: 50%;
	background: var(--accent);
	animation: agent-bounce 1.2s infinite ease-in-out;
}

.dot:nth-child(2) {
	animation-delay: 0.15s;
}

.dot:nth-child(3) {
	animation-delay: 0.3s;
}

@keyframes agent-bounce {
	0%, 80%, 100% {
		opacity: 0.3;
		transform: translateY(0);
	}
	40% {
		opacity: 1;
		transform: translateY(-4px);
	}
}

/* 结果区 */
.result-block {
	margin-bottom: 1.8rem;
}

.result-title {
	font-family: var(--font-display);
	font-size: 1.05rem;
	margin: 0 0 0.8rem;
}

.result-name-row {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 0.6rem;
	margin: 0 0 0.8rem;
}

.result-name-row .result-title {
	margin: 0;
}

.favorite-toggle {
	padding: 0.3rem 0.8rem;
	border: 1px solid var(--button-border);
	border-radius: 999px;
	background: var(--button-bg);
	color: var(--accent);
	font: inherit;
	font-size: 0.8rem;
	cursor: pointer;
	transition: background 0.2s ease;
}

.favorite-toggle:hover {
	background: var(--panel-glow);
}

.favorite-toggle:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

.step-list {
	list-style: none;
	margin: 0;
	padding: 0;
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
}

.step-item {
	display: flex;
	align-items: baseline;
	gap: 0.6rem;
	padding: 0.55rem 0.9rem;
	border-radius: 12px;
	border: 1px solid var(--card-border);
	background: var(--card);
	font-size: 0.88rem;
}

.step-phase {
	flex-shrink: 0;
	font-size: 0.72rem;
	letter-spacing: 0.06em;
	color: var(--accent);
	border: 1px solid var(--button-border);
	border-radius: 999px;
	padding: 0.1rem 0.55rem;
}

.step-detail {
	color: var(--muted);
	word-break: break-word;
}

.visual-block {
	text-align: center;
}

.visual-panel {
	max-width: 380px;
	margin: 0 auto;
}

.ai-image {
	display: block;
	width: 100%;
	height: auto;
	border-radius: 18px;
	border: 1px solid var(--card-border);
}

.visual-caption {
	font-size: 0.78rem;
	color: var(--muted);
	margin: 0.6rem 0 0;
}

.image-temporary-hint {
	color: var(--danger, #e57373);
}

.agent-sources {
	display: flex;
	flex-wrap: wrap;
	gap: 0.45rem;
}

.source-chip {
	font-size: 0.82rem;
	color: var(--accent);
	border: 1px solid var(--button-border);
	border-radius: 999px;
	padding: 0.25rem 0.75rem;
	text-decoration: none;
	transition: background 0.2s ease;
}

.source-chip:hover {
	background: var(--panel-glow);
}

.source-type {
	margin-left: 0.35rem;
	font-size: 0.72rem;
	text-transform: uppercase;
	color: var(--muted);
}

.agent-empty {
	display: flex;
	flex-direction: column;
	gap: 0.6rem;
	align-items: flex-start;
}

.suggestion-chip {
	text-align: left;
	padding: 0.5rem 0.9rem;
	border: 1px solid var(--button-border);
	border-radius: 12px;
	background: var(--button-bg);
	color: var(--text);
	font: inherit;
	font-size: 0.85rem;
	cursor: pointer;
	transition: background 0.2s ease;
}

.suggestion-chip:hover {
	background: var(--panel-glow);
}

/* —— 自定义智能体面板 —— */
.agents-panel {
	display: flex;
	flex-direction: column;
	gap: 0.8rem;
	margin-bottom: 1.6rem;
	padding: 1.1rem 1.2rem;
	border-radius: 16px;
	border: 1px solid var(--card-border);
	background: var(--card);
}

.agents-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 0.6rem;
}

.agents-title {
	font-family: var(--font-display);
	font-size: 1rem;
	margin: 0;
}

.agent-select-row {
	display: flex;
	align-items: center;
	gap: 0.6rem;
}

.agent-list {
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
}

.agent-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 0.6rem;
	padding: 0.55rem 0.85rem;
	border-radius: 12px;
	border: 1px solid var(--card-border);
	background: var(--panel-glow);
}

.agent-row.selected {
	border-color: var(--accent);
}

.agent-row-main {
	display: flex;
	align-items: center;
	gap: 0.6rem;
	min-width: 0;
}

.agent-name {
	font-weight: 600;
	font-size: 0.9rem;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.agent-toggles {
	display: inline-flex;
	gap: 0.35rem;
	flex-shrink: 0;
}

.toggle-badge {
	font-size: 0.7rem;
	color: var(--accent);
	border: 1px solid var(--button-border);
	border-radius: 999px;
	padding: 0.1rem 0.5rem;
	white-space: nowrap;
}

.toggle-badge.off {
	color: var(--muted);
	opacity: 0.7;
}

.agent-row-actions {
	display: inline-flex;
	gap: 0.3rem;
	flex-shrink: 0;
}

.text-button {
	background: none;
	border: none;
	color: var(--accent);
	font: inherit;
	font-size: 0.82rem;
	cursor: pointer;
	padding: 0.25rem 0.45rem;
	border-radius: 8px;
}

.text-button:hover {
	background: var(--panel-glow);
}

.text-button.danger {
	color: var(--danger, #e57373);
}

.agents-empty {
	margin: 0;
}

.agent-form {
	display: flex;
	flex-direction: column;
	gap: 0.8rem;
	padding-top: 0.6rem;
	border-top: 1px dashed var(--card-border);
}

.form-toggles {
	display: flex;
	gap: 1.2rem;
	flex-wrap: wrap;
}

.form-actions {
	display: flex;
	gap: 0.6rem;
}

.opt-label {
	font-size: 0.88rem;
}

.opt-sub {
	font-size: 0.72rem;
	color: var(--muted);
}

/* 开关（复用 AskPage 的 web-toggle 胶囊样式） */
.web-toggle {
	display: inline-flex;
	align-items: center;
	gap: 0.45rem;
	cursor: pointer;
	white-space: nowrap;
}

.web-toggle input {
	display: none;
}

.toggle-track {
	width: 2.1rem;
	height: 1.15rem;
	border-radius: 999px;
	background: var(--button-bg);
	position: relative;
	transition: background 0.2s ease;
}

.toggle-thumb {
	position: absolute;
	top: 50%;
	left: 0.2rem;
	transform: translateY(-50%);
	width: 0.75rem;
	height: 0.75rem;
	border-radius: 50%;
	background: var(--muted);
	transition: transform 0.2s ease, background 0.2s ease;
}

.web-toggle.checked .toggle-track {
	background: var(--accent);
	border-color: var(--accent);
}

.web-toggle.checked .toggle-thumb {
	transform: translateY(-50%) translateX(0.95rem);
	background: #fff;
}

.toggle-label {
	font-size: 0.85rem;
	color: var(--text);
}

.web-toggle:has(input:disabled) {
	opacity: 0.6;
	cursor: not-allowed;
}
</style>
