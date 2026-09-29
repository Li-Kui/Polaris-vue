<template>
  <div v-loading="loading" class="model-editor">
    <div class="editor-header">
      <div class="editor-heading">
        <span class="editor-kicker">MODEL SETUP</span>
        <h2>{{ modelId ? '编辑模型' : '新增模型' }}</h2>
        <p>选择服务商和远程模型后，按实际用途开启能力即可。</p>
      </div>
      <div class="editor-actions">
        <el-button class="secondary-action" @click="cancel">取消</el-button>
        <el-button class="secondary-action" :disabled="!testable" :loading="testing" @click="testDraft">测试连接</el-button>
        <el-button class="primary-action" type="primary" :loading="saving" @click="save">保存模型</el-button>
      </div>
    </div>

    <el-card shadow="never" class="section-card">
      <template #header><b>常用配置</b></template>
      <el-form ref="baseForm" :model="state" :rules="rules" label-position="top">
        <div class="base-grid">
          <el-form-item label="配置名称" prop="name"><el-input v-model="state.name" maxlength="100" /></el-form-item>
          <el-form-item prop="connectionId">
            <template #label>
              <span>服务商连接</span>
              <el-button class="inline-action" link type="primary" @click="openQuickConnection">没有连接？立即新增</el-button>
            </template>
            <el-select v-model="state.connectionId" filterable @change="connectionChanged">
              <el-option
                v-for="connection in enabledConnections"
                :key="connection.id"
                :label="`${connection.connectionName} · ${profileLabel(connection.providerCode)}`"
                :value="connection.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="远程模型" prop="modelName" class="wide">
            <div class="remote-model-picker">
            <el-select v-model="remoteFamily" aria-label="模型系列" clearable placeholder="全部系列" @change="remotePurpose = ''">
              <el-option v-for="group in remoteModelGroups" :key="group.label" :label="`${group.label} · ${group.models.length}`" :value="group.label" />
            </el-select>
            <el-select v-model="remotePurpose" aria-label="模型用途" clearable placeholder="全部用途">
              <el-option v-for="purpose in remotePurposeOptions" :key="purpose.code" :label="`${purpose.label} · ${purpose.count}`" :value="purpose.code" />
            </el-select>
            <el-select
              v-model="state.modelName"
              aria-label="远程模型"
              filterable
              allow-create
              :loading="discovering"
              placeholder="搜索模型名称，或按系列选择"
              @change="remoteModelChanged"
            >
              <el-option-group v-for="group in filteredRemoteModelGroups" :key="group.label" :label="`${group.label} · ${group.models.length}`">
                <el-option v-for="model in group.models" :key="model" :label="model" :value="model" />
              </el-option-group>
            </el-select>
            </div>
            <div class="field-tip">可按系列和用途筛选，也可搜索或直接输入模型名。用途按名称识别，实际支持能力以服务商为准。</div>
            <div v-if="discoveryError" class="discovery-status discovery-status--error">
              <span>{{ discoveryError }}</span>
              <el-button link type="primary" :loading="discovering" @click="discover(false)">重新获取</el-button>
            </div>
            <div v-else-if="discoverySucceeded" class="discovery-status discovery-status--success">
              已从服务商获取 {{ remoteModels.length }} 个远程模型
            </div>
          </el-form-item>
          <el-form-item label="描述" class="wide"><el-input v-model="state.description" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="section-card">
      <template #header><b>模型能力</b><span class="inherit-tip">只开启业务实际需要的能力</span></template>
      <CapabilitySelector
        v-if="state.connectionId"
        :schemas="visibleSchemas"
        :selected-keys="enabledCapabilityKeys"
        :recommendation="capabilityRecommendation"
        @toggle="toggleCapability"
      />
      <el-empty v-else description="先选择或新增服务商连接，再配置该服务商支持的模型能力" :image-size="72" />
    </el-card>

    <div v-if="enabledCapabilities.length" class="capability-list">
      <CapabilityCard
        v-for="item in enabledCapabilities"
        :key="item.key"
        :item="item"
        @update-config="item.config = $event"
      />
    </div>

    <el-collapse v-model="advancedSections" class="advanced-settings">
      <el-collapse-item title="高级设置（通常无需修改）" name="advanced">
        <el-card shadow="never" class="section-card advanced-card">
          <el-form :model="state" label-position="top">
            <div class="base-grid">
              <el-form-item label="模型编码">
                <el-input v-model="state.modelCode" placeholder="留空时根据远程模型自动生成" />
              </el-form-item>
              <el-form-item label="模型分类">
                <el-input :model-value="derivedModelTypeLabel" disabled />
                <div class="field-tip">根据已开启的核心能力自动生成，无需手动维护。</div>
              </el-form-item>
              <el-form-item label="状态"><el-switch v-model="enabled" /></el-form-item>
            </div>
          </el-form>
          <div class="policy-title">运行策略 <span>空值表示继承系统默认</span></div>
          <RuntimePolicyEditor v-model="state.runtimePolicies[0]" />
        </el-card>
      </el-collapse-item>
    </el-collapse>

    <el-dialog v-model="quickConnectionVisible" title="快速新增服务商连接" width="min(620px, calc(100vw - 28px))" class="quick-connection-dialog" destroy-on-close>
      <el-alert title="选择服务商和连接方式，再填写 API 密钥。服务地址可按实际接入情况调整。" type="info" :closable="false" class="quick-tip" />
      <el-form label-position="top">
        <el-form-item label="服务商">
          <el-select v-model="quickConnection.providerCode" style="width: 100%" @change="quickProviderChanged">
            <el-option v-for="profile in providerProfiles" :key="profile.code" :label="profileLabel(profile.code)" :value="profile.code">
              <div class="provider-option"><span>{{ profileLabel(profile.code) }}</span><small>{{ providerDescription(profile.code) }}</small></div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="API 密钥">
          <el-input v-model="quickConnection.apiKey" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="连接方式">
          <el-radio-group v-model="quickConnection.networkMode">
            <el-radio-button value="DIRECT">直连厂商</el-radio-button>
            <el-radio-button value="RELAY">兼容中转</el-radio-button>
          </el-radio-group>
          <div class="field-tip">直连使用厂商地址；中转使用你填写的地址，保留所选服务商的能力配置。</div>
        </el-form-item>
        <el-form-item label="服务地址"><el-input v-model="quickConnection.baseUrl" /></el-form-item>
        <el-collapse v-model="quickAdvancedSections" class="quick-advanced">
          <el-collapse-item title="连接高级设置" name="connection-advanced">
            <el-form-item label="连接名称"><el-input v-model="quickConnection.connectionName" /></el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button @click="quickConnectionVisible = false">取消</el-button>
        <el-button type="primary" :loading="quickSaving" @click="saveQuickConnection">创建并使用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {computed, nextTick, onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import CapabilitySelector from './CapabilitySelector.vue'
import CapabilityCard from './CapabilityCard.vue'
import RuntimePolicyEditor from './RuntimePolicyEditor.vue'
import {
  createModelAggregate,
  getModelAggregate,
  getModelEditorContext,
  resolveSchemaOptions,
  testModelDraft,
  updateModelAggregate
} from '@/api/ai/model'
import {createProviderConnection, discoverProviderModels} from '@/api/ai/providerConnection'

const props = defineProps({ modelId: { type: Number, default: null } })
const emit = defineEmits(['saved', 'cancel'])
const baseForm = ref()
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const discovering = ref(false)
const discoveryError = ref('')
const discoverySucceeded = ref(false)
const connections = ref([])
const schemas = ref([])
const providerProfiles = ref([])
const remoteModels = ref([])
const remoteFamily = ref('')
const remotePurpose = ref('')
const modelPurposes = [
  { code: 'TEXT', label: '文本对话' }, { code: 'EMBEDDING', label: '向量' },
  { code: 'AUDIO', label: '语音' }, { code: 'IMAGE', label: '图像' },
  { code: 'VIDEO', label: '视频' }, { code: 'RERANK', label: '重排' },
  { code: 'MULTIMODAL', label: '多模态' }
]
const initialPersistable = ref('')
const lastAutoModelCode = ref('')
const advancedSections = ref([])
const quickAdvancedSections = ref([])
const quickConnectionVisible = ref(false)
const quickSaving = ref(false)
const quickConnection = reactive({
  connectionName: '', providerCode: '', protocolCode: '',
  networkMode: 'DIRECT', baseUrl: '', apiKey: ''
})
const emptyPolicy = () => ({
  capabilityCode: '', maxConcurrency: null, connectTimeoutMs: null,
  readTimeoutMs: null, retryCount: null, qpsLimit: null, priority: null,
  extraConfig: {}
})
const state = reactive({
  id: null, revision: null, name: '', modelCode: '', connectionId: null,
  modelName: '', modelType: 'GENERAL', description: '', deptId: null,
  status: '1', capabilities: [], runtimePolicies: [emptyPolicy()]
})
const rules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  modelCode: [
    { required: true, message: '请输入模型编码', trigger: 'blur' },
    { pattern: /^[a-z0-9][a-z0-9._-]{0,99}$/, message: '仅支持小写字母、数字、点、下划线和短横线', trigger: 'blur' }
  ],
  connectionId: [{ required: true, message: '请选择连接', trigger: 'change' }],
  modelName: [{ required: true, message: '请选择或输入远程模型', trigger: 'change' }]
}

const enabled = computed({
  get: () => state.status === '1',
  set: value => { state.status = value ? '1' : '0' }
})
const enabledConnections = computed(() => connections.value.filter(item => item.status === '1'))
const selectedConnection = computed(() => connections.value.find(item => item.id === state.connectionId))
const selectedProviderProfile = computed(() => providerProfiles.value.find(
  item => item.code === selectedConnection.value?.providerCode))
const remoteModelGroups = computed(() => {
  const groups = new Map()
  const names = [...new Set([
    ...remoteModels.value.map(remoteId),
    ...(state.modelName ? [state.modelName] : [])
  ])]
  names.forEach(name => {
    const label = remoteModelFamily(name)
    if (!groups.has(label)) groups.set(label, [])
    groups.get(label).push(name)
  })
  const preferredFamily = ({
    DEEPSEEK: 'DeepSeek', DASHSCOPE: '通义千问（Qwen）',
    OPENAI: 'OpenAI', ARK: '豆包（Doubao）'
  })[selectedConnection.value?.providerCode]
  return [...groups].map(([label, models]) => ({
    label,
    models: models.sort((a, b) => Number(isDatedModel(a)) - Number(isDatedModel(b))
      || a.localeCompare(b, 'en', { numeric: true, sensitivity: 'base' }))
  })).sort((a, b) => {
    if (a.label === preferredFamily) return -1
    if (b.label === preferredFamily) return 1
    if (a.label === '其他模型') return 1
    if (b.label === '其他模型') return -1
    return a.label.localeCompare(b.label, 'zh-CN')
  })
})
const familyRemoteModelGroups = computed(() => remoteFamily.value
  ? remoteModelGroups.value.filter(group => group.label === remoteFamily.value)
  : remoteModelGroups.value)
const remotePurposeOptions = computed(() => modelPurposes.map(purpose => ({
  ...purpose,
  count: familyRemoteModelGroups.value.reduce((count, group) => count
    + group.models.filter(model => remoteModelPurpose(model) === purpose.code).length, 0)
})).filter(purpose => purpose.count > 0))
const filteredRemoteModelGroups = computed(() => familyRemoteModelGroups.value.flatMap(group =>
  modelPurposes.filter(purpose => !remotePurpose.value || purpose.code === remotePurpose.value)
    .map(purpose => ({
      label: `${group.label} / ${purpose.label}`,
      models: group.models.filter(model => remoteModelPurpose(model) === purpose.code)
    })).filter(group => group.models.length)))
const visibleSchemas = computed(() => {
  const supported = selectedProviderProfile.value?.supportedCapabilities || []
  if (!state.connectionId || !supported.length) return schemas.value
  const supportedSet = new Set(supported)
  return schemas.value.filter(schema => schema.kind === 'FEATURE'
    ? (schema.allowedAppliesTo || []).some(item => supportedSet.has(item))
    : supportedSet.has(schema.code))
})
const enabledCapabilities = computed(() => state.capabilities.filter(item => item.enabled))
const enabledCapabilityKeys = computed(() => enabledCapabilities.value.map(item => item.key))
const testable = computed(() => !!state.connectionId && !!state.modelName && enabledCapabilities.value.some(item => item.schema.kind === 'INVOCATION'))
const recommendedCapabilityCode = computed(() => recommendCapabilityCode(state.modelName))
const capabilityRecommendation = computed(() => {
  if (!state.modelName || !recommendedCapabilityCode.value) return ''
  const schema = visibleSchemas.value.find(item => item.code === recommendedCapabilityCode.value)
  return schema ? `已推荐：${schema.name}` : ''
})
const derivedModelType = computed(() => deriveModelType(enabledCapabilities.value))
const derivedModelTypeLabel = computed(() => ({
  GENERAL: '通用（多种能力）', CHAT: '对话', EMBEDDING: '向量', IMAGE: '图像',
  AUDIO: '音频', VIDEO: '视频', RERANK: '重排'
})[derivedModelType.value] || '通用')

onMounted(load)

async function load() {
  loading.value = true
  try {
    const contextResponse = await getModelEditorContext()
    const context = unwrap(contextResponse)
    connections.value = context.connections || []
    schemas.value = context.capabilitySchemas || []
    providerProfiles.value = (context.providerProfiles || []).filter(item => item.layer === 'PROVIDER')
    if (props.modelId) {
      const detail = unwrap(await getModelAggregate(props.modelId))
      Object.assign(state, {
        id: detail.id,
        revision: detail.revision,
        name: detail.name,
        modelCode: detail.modelCode,
        connectionId: detail.connectionId,
        modelName: detail.modelName,
        modelType: detail.modelType || 'GENERAL',
        description: detail.description,
        deptId: detail.deptId,
        status: detail.status,
        capabilities: (detail.capabilities || []).map(item => ({ ...item, options: {}, key: capabilityKey(item.capabilityCode, item.appliesToCapabilityCode) })),
        runtimePolicies: detail.runtimePolicies?.length ? detail.runtimePolicies.map(item => ({ ...item })) : [emptyPolicy()]
      })
      await discover(false)
      syncRemoteFamily()
      await Promise.all(enabledCapabilities.value.map(loadDynamicOptions))
    }
    await nextTick()
    initialPersistable.value = JSON.stringify(buildPersistableRequest())
  } finally {
    loading.value = false
  }
}

async function toggleCapability(schema, appliesTo, value) {
  const key = capabilityKey(schema.code, appliesTo)
  let item = state.capabilities.find(current => current.key === key)
  if (!item) {
    item = {
      key,
      capabilityCode: schema.code,
      appliesToCapabilityCode: appliesTo || '',
      schemaVersion: schema.schemaVersion,
      enabled: false,
      config: defaults(schema.schema),
      schema,
      options: {}
    }
    state.capabilities.push(item)
  }
  item.enabled = !!value
  if (item.enabled) await loadDynamicOptions(item)
  if (value && schema.code === 'CHAT_COMPLETION') {
    const streaming = visibleSchemas.value.find(current => current.code === 'STREAMING')
    if (streaming && !state.capabilities.some(current => current.capabilityCode === 'STREAMING'
      && current.appliesToCapabilityCode === 'CHAT_COMPLETION')) {
      await toggleCapability(streaming, 'CHAT_COMPLETION', true)
    }
  }
}

async function connectionChanged() {
  state.modelName = ''
  remoteFamily.value = ''
  remotePurpose.value = ''
  remoteModels.value = []
  discoveryError.value = ''
  discoverySucceeded.value = false
  await enableRecommendedCapability()
  await discover(true)
}

async function remoteModelChanged() {
  syncRemoteFamily()
  ensureModelCode()
  await applyModelCapabilityRecommendation()
  applyModelParameterRecommendations()
  await Promise.all(enabledCapabilities.value.map(loadDynamicOptions))
}

function applyModelParameterRecommendations() {
  const name = String(state.modelName || '').toLowerCase()
  if (!/(reason|thinking|deepseek-(r|v4|flash))/.test(name)) return
  const chat = state.capabilities.find(item => item.enabled
    && item.capabilityCode === 'CHAT_COMPLETION' && !item.appliesToCapabilityCode)
  if (chat && Number(chat.config?.maxTokens) === 2048) chat.config.maxTokens = 8192
}

async function loadDynamicOptions(item) {
  if (!state.connectionId || !state.modelName) return
  const entries = Object.entries(item.schema?.uiSchema || {})
    .filter(([, ui]) => ui.optionsResolver)
  for (const [field] of entries) {
    try {
      item.options[field] = unwrap(await resolveSchemaOptions({
        connectionId: state.connectionId,
        modelName: state.modelName,
        capability: item.capabilityCode,
        field
      })) || []
    } catch (_) {
      item.options[field] = []
    }
  }
}

async function discover(showError) {
  if (!state.connectionId) return
  discovering.value = true
  discoveryError.value = ''
  try {
    remoteModels.value = unwrap(await discoverProviderModels(state.connectionId)) || []
    discoverySucceeded.value = true
  } catch (error) {
    discoverySucceeded.value = false
    discoveryError.value = discoveryFailureMessage(error)
    if (showError) ElMessage.warning(discoveryError.value)
  } finally {
    discovering.value = false
  }
}

function discoveryFailureMessage(error) {
  const raw = String(error?.message || '')
  const messages = {
    MODEL_DISCOVERY_AUTH_FAILED: 'API 密钥无效或没有读取模型列表的权限，可修改连接后重试。',
    MODEL_DISCOVERY_NOT_SUPPORTED: '该服务地址不支持自动获取模型，可直接输入完整模型名称。',
    MODEL_DISCOVERY_RATE_LIMITED: '服务商请求过于频繁，请稍后重新获取。',
    MODEL_DISCOVERY_TIMEOUT: '服务商响应超时，请检查网络后重新获取。',
    MODEL_DISCOVERY_DNS_FAILED: '无法解析服务商域名，请检查服务地址或网络设置。',
    MODEL_DISCOVERY_CONNECTION_REFUSED: '服务连接被拒绝，请检查服务地址；如使用代理，请确认代理正在运行。',
    MODEL_DISCOVERY_TLS_FAILED: 'HTTPS 证书校验失败，请检查服务地址和证书配置。',
    MODEL_DISCOVERY_RESPONSE_INVALID: '服务商返回的模型列表格式不兼容，可直接输入完整模型名称。',
    MODEL_DISCOVERY_MODEL_LIMIT_EXCEEDED: '服务商返回的模型数量过多，请直接搜索或输入模型名称。',
    MODEL_DISCOVERY_UNAVAILABLE: '暂时无法连接服务商，请检查服务地址和网络后重试。'
  }
  const matched = Object.keys(messages).find(code => raw.includes(code))
  return matched ? messages[matched] : '未能获取远程模型列表，可重新获取或直接输入模型名称。'
}

function buildPersistableRequest() {
  state.modelType = derivedModelType.value
  return {
    id: state.id,
    expectedRevision: state.revision,
    name: state.name?.trim(),
    modelCode: state.modelCode?.trim().toLowerCase(),
    connectionId: state.connectionId,
    modelName: state.modelName?.trim(),
    modelType: state.modelType,
    description: state.description?.trim() || null,
    deptId: state.deptId,
    status: state.status,
    capabilities: state.capabilities.map(item => ({
      capabilityCode: item.capabilityCode,
      appliesToCapabilityCode: item.appliesToCapabilityCode || null,
      schemaVersion: item.schemaVersion,
      enabled: item.enabled,
      config: compactConfig(item.config, item.schema?.schema)
    })),
    runtimePolicies: state.runtimePolicies.map(policy => ({ ...policy, extraConfig: policy.extraConfig || {} }))
  }
}

async function save() {
  ensureModelCode()
  await baseForm.value?.validate()
  if (!enabledCapabilities.value.some(item => item.schema.kind === 'INVOCATION')) {
    return ElMessage.error('至少启用一项调用能力')
  }
  const unsupported = enabledCapabilities.value.flatMap(item =>
    Object.values(item.schema.uiSchema || {}).filter(ui =>
      ui.component === 'custom' && !['voice-selector', 'image-size-selector'].includes(ui.customComponent)))
  if (unsupported.length) return ElMessage.error('存在当前版本不支持的配置项，无法保存')
  saving.value = true
  try {
    const request = buildPersistableRequest()
    if (state.id) await updateModelAggregate(state.id, request)
    else await createModelAggregate(request)
    ElMessage.success('模型配置已保存')
    emit('saved')
  } catch (error) {
    if (String(error?.message || '').includes('MODEL_CONFIG_CONFLICT')) {
      ElMessage.error('配置已被其他人修改，请刷新后重试')
    } else throw error
  } finally {
    saving.value = false
  }
}

function openQuickConnection() {
  Object.assign(quickConnection, {
    connectionName: '', providerCode: '', protocolCode: '',
    networkMode: 'DIRECT', baseUrl: '', apiKey: ''
  })
  quickAdvancedSections.value = []
  quickConnectionVisible.value = true
}

function quickProviderChanged(code) {
  const profile = providerProfiles.value.find(item => item.code === code)
  if (!profile) return
  quickConnection.connectionName = `${profileLabel(code)}连接`
  quickConnection.protocolCode = profile.protocolCode
  quickConnection.baseUrl = profile.defaultBaseUrl
}

async function saveQuickConnection() {
  if (!quickConnection.providerCode) return ElMessage.error('请选择服务商')
  if (!quickConnection.apiKey?.trim()) return ElMessage.error('请输入 API Key')
  quickSaving.value = true
  try {
    const createdId = unwrap(await createProviderConnection({
      connectionName: quickConnection.connectionName.trim(),
      providerCode: quickConnection.providerCode,
      protocolCode: quickConnection.protocolCode,
      networkMode: quickConnection.networkMode,
      baseUrl: quickConnection.baseUrl,
      credential: { apiKey: quickConnection.apiKey },
      extraConfig: {}, status: '1', remark: null
    }))
    const context = unwrap(await getModelEditorContext())
    connections.value = context.connections || []
    providerProfiles.value = (context.providerProfiles || []).filter(item => item.layer === 'PROVIDER')
    state.connectionId = createdId
    quickConnection.apiKey = ''
    quickConnectionVisible.value = false
    ElMessage.success('连接已创建并自动选中')
    await connectionChanged()
  } finally {
    quickSaving.value = false
  }
}

async function enableRecommendedCapability() {
  if (enabledCapabilities.value.some(item => item.schema.kind === 'INVOCATION')) return
  const invocationSchemas = visibleSchemas.value.filter(item => item.kind === 'INVOCATION')
  const recommended = invocationSchemas.find(item => item.code === recommendedCapabilityCode.value)
    || invocationSchemas.find(item => item.code === 'CHAT_COMPLETION') || invocationSchemas[0]
  if (recommended) await toggleCapability(recommended, '', true)
}

async function applyModelCapabilityRecommendation() {
  const suggested = visibleSchemas.value.find(item =>
    item.kind === 'INVOCATION' && item.code === recommendedCapabilityCode.value)
  if (!suggested) return
  const current = enabledCapabilities.value.filter(item => item.schema.kind === 'INVOCATION')
  if (!current.length) {
    await toggleCapability(suggested, '', true)
    return
  }
  // 只有单项核心能力时才自动替换；多项选择视为用户的明确配置并完整保留。
  if (current.length === 1 && current[0].capabilityCode !== suggested.code) {
    await toggleCapability(current[0].schema, current[0].appliesToCapabilityCode, false)
    await toggleCapability(suggested, '', true)
  }
}

function recommendCapabilityCode(modelName) {
  const name = String(modelName || '').toLowerCase()
  if (!name) return 'CHAT_COMPLETION'
  if (/(rerank|reranker)/.test(name)) return 'RERANK'
  if (/(embedding|embed|bge-|e5-|gte-)/.test(name)) return 'TEXT_EMBEDDING'
  if (/(whisper|speech.to.text|speech_to_text|\bstt\b|\basr\b)/.test(name)) return 'AUDIO_STT'
  if (/(text.to.speech|text_to_speech|\btts\b)/.test(name)) return 'AUDIO_TTS'
  if (/(image.edit|image_edit|inpaint)/.test(name)) return 'IMAGE_EDIT'
  if (/(dall-e|dalle|flux|stable.diffusion|sdxl|image|wanx)/.test(name)) return 'IMAGE_GENERATION'
  if (/(video|wan2|sora|\bt2v\b|\bi2v\b)/.test(name)) return 'VIDEO_GENERATION'
  return 'CHAT_COMPLETION'
}

function deriveModelType(capabilities) {
  const categories = new Set(capabilities
    .filter(item => item.schema.kind === 'INVOCATION')
    .map(item => ({
      CHAT_COMPLETION: 'CHAT', TEXT_EMBEDDING: 'EMBEDDING', RERANK: 'RERANK',
      IMAGE_GENERATION: 'IMAGE', IMAGE_EDIT: 'IMAGE', IMAGE_INPAINT: 'IMAGE', IMAGE_VARIATION: 'IMAGE',
      AUDIO_TTS: 'AUDIO', AUDIO_STT: 'AUDIO', VIDEO_GENERATION: 'VIDEO'
    })[item.capabilityCode] || 'GENERAL'))
  return categories.size === 1 ? [...categories][0] : 'GENERAL'
}

function ensureModelCode() {
  const current = state.modelCode?.trim()
  // 自动生成的编码随远程模型一起更新；用户手动改过后不再覆盖。
  if (current && current !== lastAutoModelCode.value) return
  const source = state.modelName || state.name
  const generated = String(source || '').toLowerCase()
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^[^a-z0-9]+|[^a-z0-9]+$/g, '')
    .slice(0, 100)
  state.modelCode = generated || `model-${Date.now().toString(36)}`
  lastAutoModelCode.value = state.modelCode
}

function profileLabel(code) {
  return ({
    OPENAI: 'OpenAI', DASHSCOPE: '阿里云百炼', DEEPSEEK: 'DeepSeek',
    SILICONFLOW: '硅基流动（第三方平台）', ARK: '火山方舟', CUSTOM: '自定义兼容服务'
  })[code] || code
}

function providerDescription(code) {
  return ({
    OPENAI: 'OpenAI 官方接口',
    DASHSCOPE: '通义千问与百炼模型',
    DEEPSEEK: 'DeepSeek 官方接口',
    SILICONFLOW: '第三方平台，支持多种开源模型',
    ARK: '火山引擎模型服务',
    CUSTOM: '自建服务或其他兼容接口'
  })[code] || ''
}

async function testDraft() {
  if (testing.value) return
  const invocation = enabledCapabilities.value.find(item => item.schema.kind === 'INVOCATION')
  if (!invocation) return
  testing.value = true
  try {
    const result = unwrap(await testModelDraft({
      connectionId: state.connectionId,
      modelName: state.modelName,
      capabilityCode: invocation.capabilityCode,
      schemaVersion: invocation.schemaVersion,
      capabilityConfig: compactConfig(invocation.config, invocation.schema?.schema),
      runtimePolicy: state.runtimePolicies[0],
      testCapability: invocation.capabilityCode
    }))
    ElMessage.success(result?.message || '模型测试成功')
  } catch {
    // 请求层已经展示服务端返回的可读错误，避免事件处理器继续抛出未处理异常。
  } finally {
    testing.value = false
  }
}

async function cancel() {
  if (JSON.stringify(buildPersistableRequest()) !== initialPersistable.value) {
    await ElMessageBox.confirm('存在未保存的更改，确认离开？', '提示', { type: 'warning' })
  }
  emit('cancel')
}

function defaults(schema) {
  const result = {}
  Object.entries(schema?.properties || {}).forEach(([key, definition]) => {
    const value = definition.defaultValue ?? definition.default
    if (value !== undefined && value !== null) result[key] = value
  })
  return result
}

function compactConfig(config, schema) {
  const numericFields = new Set([
    'maxTokens', 'temperature', 'strength', 'maskFeather', 'similarity',
    'sampleRate', 'speed', 'pitch', 'dimension', 'maxInputTokens', 'batchSize',
    'topN', 'duration', 'fps'
  ])
  return Object.fromEntries(Object.entries(config || {})
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => {
      const type = String(schema?.properties?.[key]?.type || '').toLowerCase()
      if ((type === 'integer' || type === 'number' || numericFields.has(key))
          && typeof value === 'string') {
        const number = Number(value)
        return [key, Number.isFinite(number) ? number : value]
      }
      return [key, value]
    }))
}

function capabilityKey(code, appliesTo) { return appliesTo ? `${code}@${appliesTo}` : code }
function remoteId(model) { return model?.id || model?.modelId || model?.name || String(model) }
function syncRemoteFamily() {
  remoteFamily.value = state.modelName ? remoteModelFamily(state.modelName) : ''
  remotePurpose.value = state.modelName ? remoteModelPurpose(state.modelName) : ''
}
function remoteModelPurpose(modelName) {
  const name = String(modelName).toLowerCase()
  const capability = recommendCapabilityCode(name)
  const purpose = {
    TEXT_EMBEDDING: 'EMBEDDING', RERANK: 'RERANK',
    AUDIO_STT: 'AUDIO', AUDIO_TTS: 'AUDIO',
    IMAGE_GENERATION: 'IMAGE', IMAGE_EDIT: 'IMAGE', VIDEO_GENERATION: 'VIDEO'
  }[capability]
  if (purpose) return purpose
  if (/(omni|vision|\bvl\b|qvq)/.test(name)) return 'MULTIMODAL'
  if (/(speech|audio|unisound)/.test(name)) return 'AUDIO'
  return 'TEXT'
}
function remoteModelFamily(modelName) {
  const name = String(modelName).toLowerCase()
  const families = [
    [/deepseek/, 'DeepSeek'],
    [/(qwen|qvq|qwq|codeqwen)/, '通义千问（Qwen）'],
    [/(\bglm\b|zhipu)/, '智谱（GLM）'],
    [/(kimi|moonshot)/, 'Kimi'],
    [/minimax/, 'MiniMax'],
    [/(doubao|seed-)/, '豆包（Doubao）'],
    [/(gpt-|chatgpt|dall-e|whisper|^o[134](?:-|$))/, 'OpenAI'],
    [/(claude|anthropic)/, 'Claude'],
    [/(gemini|gemma)/, 'Google'],
    [/(wan\d|wanx)/, '通义万相（Wan）'],
    [/(asr|speech|tts|unisound)/, '其他语音模型'],
    [/(embedding|rerank|bge-|e5-|gte-)/, '其他向量与重排模型'],
    [/(flux|stable.diffusion|sdxl|z-image)/, '其他图像模型']
  ]
  return families.find(([pattern]) => pattern.test(name))?.[1] || '其他模型'
}
function isDatedModel(modelName) {
  return /-(?:20\d{2}-?\d{2}-?\d{2}|\d{4})$/.test(modelName)
}
function unwrap(response) { return response?.data?.data ?? response?.data ?? response }
</script>

<style scoped>
.model-editor { --el-color-primary: var(--mc-accent); max-width: 1280px; margin: 0 auto; padding-bottom: 30px; color: var(--mc-text); }
.remote-model-picker { display: flex; gap: 10px; min-width: 0; width: 100%; }
.remote-model-picker > :first-child { flex: 0 0 180px; }
.remote-model-picker > :nth-child(2) { flex: 0 0 140px; }
.remote-model-picker > :last-child { flex: 1; min-width: 0; }
@media (max-width: 600px) { .remote-model-picker { flex-direction: column; } .remote-model-picker > :first-child, .remote-model-picker > :nth-child(2) { flex: auto; } }
.editor-header { position: relative; overflow: hidden; display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 20px; padding: 26px 28px; border: 1px solid var(--mc-border); border-radius: 18px; background: linear-gradient(120deg, var(--mc-accent-soft), var(--mc-surface) 58%, rgba(16, 185, 129, .08)); box-shadow: 0 12px 34px rgba(15, 23, 42, .08); }
.editor-header::after { position: absolute; right: -54px; top: -78px; width: 180px; height: 180px; border: 28px solid rgba(37, 99, 235, .08); border-radius: 50%; content: ''; pointer-events: none; }
.editor-heading, .editor-actions { position: relative; z-index: 1; }
.editor-kicker { display: block; margin-bottom: 7px; color: var(--mc-accent); font-size: 11px; font-weight: 800; letter-spacing: .14em; }
.editor-header h2 { margin: 0 0 6px; color: var(--mc-text); font-size: 26px; }
.editor-header p { margin: 0; color: var(--mc-muted); }
.editor-actions { display: flex; align-items: center; gap: 10px; }
.primary-action { border: 0 !important; color: #fff !important; background: linear-gradient(135deg, #2563eb, #7c3aed) !important; box-shadow: 0 8px 20px rgba(37, 99, 235, .24); }
.secondary-action { border-color: var(--mc-border) !important; color: var(--mc-text) !important; background: var(--mc-surface) !important; }
.secondary-action.is-disabled { color: var(--mc-muted) !important; background: var(--mc-surface-muted) !important; opacity: .7; }
.section-card { margin-bottom: 18px; border-color: var(--mc-border); border-radius: 16px; color: var(--mc-text); background: var(--mc-surface); box-shadow: 0 8px 28px rgba(15, 23, 42, .05); }
.section-card :deep(.el-card__header) { border-bottom-color: var(--mc-border); color: var(--mc-text); background: var(--mc-surface-muted); }
.section-card :deep(.el-card__body) { color: var(--mc-text); background: var(--mc-surface); }
.section-card :deep(.el-form-item__label) { color: var(--mc-text) !important; font-weight: 600; }
.base-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.base-grid .wide { grid-column: 1 / -1; }
.base-grid :deep(.el-select) { width: 100%; }
.inherit-tip { margin-left: 10px; color: var(--mc-muted); font-size: 12px; }
.inline-action { margin-left: 10px; color: var(--mc-accent) !important; vertical-align: baseline; }
.advanced-settings { margin-bottom: 16px; border: 1px solid var(--mc-border); border-radius: 14px; padding: 0 18px; color: var(--mc-text); background: var(--mc-surface-muted); }
.advanced-settings :deep(.el-collapse-item__header) { color: var(--mc-text); background: transparent; }
.advanced-settings :deep(.el-collapse-item__wrap) { background: transparent; }
.advanced-card { margin: 0; border: 0; }
.policy-title { margin: 4px 0 14px; font-weight: 600; }
.policy-title span { margin-left: 8px; color: var(--mc-muted); font-size: 12px; font-weight: 400; }
.quick-tip { margin-bottom: 16px; }
.field-tip { margin-top: 8px; color: var(--mc-muted); font-size: 12px; line-height: 1.5; }
.discovery-status { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 10px; padding: 9px 12px; border: 1px solid var(--mc-border); border-radius: 10px; font-size: 12px; line-height: 1.5; }
.discovery-status--error { border-color: color-mix(in srgb, var(--el-color-warning) 38%, var(--mc-border)); color: var(--el-color-warning-dark-2); background: color-mix(in srgb, var(--el-color-warning) 9%, var(--mc-surface)); }
.discovery-status--success { justify-content: flex-start; color: var(--el-color-success-dark-2); background: color-mix(in srgb, var(--el-color-success) 8%, var(--mc-surface)); }
.quick-advanced { overflow: hidden; margin-top: 4px; padding: 0 14px; border: 1px solid var(--mc-border); border-radius: 12px; background: var(--mc-surface-muted); }
.quick-advanced :deep(.el-collapse-item__header), .quick-advanced :deep(.el-collapse-item__wrap) { color: var(--mc-text); background: transparent; }
:global(.quick-connection-dialog) { overflow: hidden; display: flex; max-height: calc(100vh - 32px); flex-direction: column; margin: 16px auto !important; border: 1px solid var(--mc-border); border-radius: 18px !important; color: var(--mc-text); background: var(--mc-surface) !important; }
:global(.quick-connection-dialog .el-dialog__header) { margin-right: 0; padding: 22px 24px 16px; border-bottom: 1px solid var(--mc-border); }
:global(.quick-connection-dialog .el-dialog__title), :global(.quick-connection-dialog .el-form-item__label), :global(.quick-connection-dialog .el-collapse-item__header) { color: var(--mc-text) !important; }
:global(.quick-connection-dialog .el-dialog__body) { overflow-y: auto; min-height: 0; padding: 20px 24px; color: var(--mc-text); background: var(--mc-surface); }
:global(.quick-connection-dialog .el-dialog__footer) { padding: 14px 24px 20px; border-top: 1px solid var(--mc-border); background: var(--mc-surface-muted); }
:global(.provider-option) { display: flex; align-items: center; justify-content: space-between; gap: 24px; min-width: 360px; }
:global(.provider-option small) { color: #8492a6; font-size: 12px; }
@media (max-width: 1000px) { .base-grid { grid-template-columns: 1fr; } .editor-header { align-items: flex-start; flex-direction: column; } .editor-actions { width: 100%; flex-wrap: wrap; } }
</style>
