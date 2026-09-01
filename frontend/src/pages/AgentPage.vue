<script setup>
// 天体生成 Agent 页面（独立一级模块）：
// 类型 chips + 参数表单 + 自然语言描述三者结合输入 → 后端 Agent 检索真实天体作参考 → 生成结果。
// 结果展示：Agent 执行轨迹 → 天体视觉（文生图或 SVG）→ 参数卡片 → Markdown 介绍 → 参考来源跳转。
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import MarkdownView from '../components/MarkdownView.vue'
import CelestialVisual from '../components/CelestialVisual.vue'
import { api, ApiUnavailableError } from '../api'

const { t, tm } = useI18n()

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
const description = ref('')

// 状态机
const status = ref('idle') // idle | generating | ready | error
const error = ref('')
const result = ref(null)

const samples = tm('agent.samples')

function buildDescription() {
	const parts = []
	if (description.value.trim()) parts.push(description.value.trim())
	if (selectedType.value) parts.push(selectedType.value)
	if (mass.value.trim()) parts.push(`质量${mass.value.trim()}`)
	if (radius.value.trim()) parts.push(`半径${radius.value.trim()}`)
	if (temperature.value.trim()) parts.push(`表面温度${temperature.value.trim()}`)
	if (color.value) parts.push(`颜色${color.value}`)
	return parts.join('，')
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
	description.value = ''
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
		const res = await api.generateCelestial(desc)
		result.value = res
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
</script>

<template>
	<main>
		<section class="section-block agent-section">
			<div class="section-heading">
				<p class="eyebrow">{{ $t('agent.kicker') }}</p>
				<h3>{{ $t('agent.title') }}</h3>
				<p>{{ $t('agent.intro') }}</p>
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
								:class="{ active: color === c }"
								:style="{ background: c }"
								type="button"
								:disabled="status === 'generating'"
								:aria-label="c"
								:title="c"
								@click="color = c"
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
					<h4 class="result-title">{{ result.name }}</h4>
					<div class="visual-panel">
						<img v-if="result.imageUrl" :src="result.imageUrl" :alt="result.name" class="ai-image" />
						<CelestialVisual v-else :render="result.render || {}" :name="result.name" />
					</div>
					<p class="visual-caption">
						{{ $t('agent.imageHint') }}
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
</style>
