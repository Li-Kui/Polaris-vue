<template>
  <div class="app-container customer-service-page">
    <header class="cs-page-heading">
      <div><p class="cs-eyebrow">CUSTOMER SERVICE</p><h1>数字人客服</h1><p class="cs-subtitle">配置、测试与发布你的数字人接待服务</p></div>
      <el-button type="primary" size="large" @click="openCreate"><span aria-hidden="true">＋</span> 创建客服</el-button>
    </header>
    <section class="cs-list-tools" aria-label="客服筛选">
      <div class="cs-search-row"><el-input v-model="search" placeholder="搜索客服名称" clearable aria-label="搜索客服名称" /><el-select v-model="statusFilter" aria-label="客服状态"><el-option label="全部状态" value="ALL" /><el-option label="已启用" value="ACTIVE" /><el-option label="已停用" value="DISABLED" /></el-select><div class="cs-view-actions"><el-radio-group v-model="listView" aria-label="列表展示方式"><el-radio-button value="cards">卡片</el-radio-button><el-radio-button value="table">列表</el-radio-button></el-radio-group><el-button :loading="loading" aria-label="刷新客服列表" @click="loadApps">刷新</el-button></div></div>
      <div class="cs-filter-tabs" role="tablist" aria-label="发布状态"><button v-for="tab in listTabs" :key="tab.key" type="button" role="tab" :aria-selected="publishFilter === tab.key" :class="{ active: publishFilter === tab.key }" @click="publishFilter = tab.key">{{ tab.label }}<span v-if="tab.key === 'ALL'">{{ apps.length }}</span></button></div>
    </section>
    <el-alert v-if="listError" :title="listError" type="error" :closable="false" />
    <div v-loading="loading" class="cs-list-content">
      <div v-if="listView === 'cards'" class="cs-app-grid">
        <article v-for="row in filteredApps" :key="row.id" class="cs-app-card">
          <div class="cs-card-overview"><div class="cs-card-avatar"><CustomerAvatar v-if="row.config.avatar?.coverAssetId" :config="{ name: row.config.avatar.name, coverAssetId: row.config.avatar.coverAssetId, fit: row.config.avatar.fit || 'CONTAIN' }" state="STOPPED" :active="true" /><span v-else aria-hidden="true">{{ row.config.name.slice(0, 1) }}</span></div><div class="cs-card-title"><h2 :title="row.config.name">{{ row.config.name }}</h2><div class="cs-badges"><span class="cs-status" :class="{ 'is-live': row.status === 'ACTIVE' }">{{ row.status === 'ACTIVE' ? '已启用' : '已停用' }}</span><span class="cs-status" :class="{ 'is-accent': row.currentReleaseId }">{{ row.currentReleaseId ? '已有发布' : '待发布' }}</span></div><p>知识库 {{ row.config.knowledgeBaseIds?.length || 0 }} · 业务能力 {{ row.config.capabilities?.length || 0 }}</p><p class="cs-card-meta">{{ row.config.voice?.sttModelId ? '语音已配置' : '文字接待' }}<span>修订 {{ row.revision }}</span></p></div></div>
          <footer class="cs-card-actions"><el-button type="primary" :disabled="busy || !row.currentReleaseId || row.status !== 'ACTIVE'" @click="preview(row)">接待预览</el-button><el-button @click="openEdit(row)">编辑草稿</el-button><el-dropdown trigger="click" :disabled="busy" @command="command => handleAppAction(command, row)"><el-button class="cs-more-button" :aria-label="`${row.config.name}更多操作`">•••</el-button><template #dropdown><el-dropdown-menu class="customer-service-menu"><el-dropdown-item command="publish" :disabled="row.status !== 'ACTIVE'">发布版本</el-dropdown-item><el-dropdown-item command="channels" :disabled="!row.currentReleaseId">分享渠道</el-dropdown-item><el-dropdown-item command="records">接待记录</el-dropdown-item><el-dropdown-item command="status" divided>{{ row.status === 'ACTIVE' ? '停用客服' : '启用客服' }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></footer>
        </article>
      </div>
      <el-table v-else :data="filteredApps" row-key="id" class="cs-table">
        <el-table-column prop="config.name" label="客服名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="110"><template #default="{ row }"><span class="cs-status" :class="{ 'is-live': row.status === 'ACTIVE' }">{{ row.status === 'ACTIVE' ? '已启用' : '已停用' }}</span></template></el-table-column>
        <el-table-column prop="revision" label="配置修订" width="100" />
        <el-table-column label="发布" min-width="130"><template #default="{ row }">{{ row.currentReleaseId ? '已有发布版本' : '尚未发布' }}</template></el-table-column>
        <el-table-column label="操作" min-width="310"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑草稿</el-button><el-button link type="primary" :disabled="busy || !row.currentReleaseId || row.status !== 'ACTIVE'" @click="preview(row)">接待预览</el-button><el-dropdown trigger="click" :disabled="busy" @command="command => handleAppAction(command, row)"><el-button link type="primary">更多操作</el-button><template #dropdown><el-dropdown-menu class="customer-service-menu"><el-dropdown-item command="publish" :disabled="row.status !== 'ACTIVE'">发布版本</el-dropdown-item><el-dropdown-item command="channels" :disabled="!row.currentReleaseId">分享渠道</el-dropdown-item><el-dropdown-item command="records">接待记录</el-dropdown-item><el-dropdown-item command="status" divided>{{ row.status === 'ACTIVE' ? '停用客服' : '启用客服' }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && !filteredApps.length" :description="apps.length ? '没有符合筛选条件的客服' : '创建你的第一位数字人客服'" />
    </div>
    <p class="cs-page-note">人物、知识与语音按发布配置运行。真实订单与口型同步尚未接入。</p>

    <el-dialog v-model="editorOpen" class="customer-service-editor" :title="editing ? '编辑客服草稿' : '创建客服'" width="min(1180px, calc(100vw - 24px))" :close-on-click-modal="false">
      <div class="cs-editor-heading"><div><h2>{{ form.name || '新的数字人客服' }}</h2><span class="cs-status is-accent">{{ editing ? `草稿 · 修订 ${editing.revision}` : '未保存草稿' }}</span></div><p>配置保存为草稿，发布后对新会话生效。</p></div>
      <nav class="cs-editor-tabs" aria-label="客服配置分区"><button v-for="tab in editorTabs" :key="tab.key" type="button" :class="{ active: editorTab === tab.key }" :aria-current="editorTab === tab.key ? 'page' : undefined" @click="editorTab = tab.key">{{ tab.label }}</button></nav>
      <div class="cs-editor-layout" :class="{ 'is-avatar-editor': editorTab === 'avatar' }">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="cs-editor-form">
        <section v-show="editorTab === 'basic'" class="cs-form-section"><h3>基本信息</h3><p class="muted">定义客服的名称、接待方式和服务范围。</p>
          <el-form-item label="客服名称" prop="name"><el-input v-model="form.name" maxlength="64" show-word-limit /></el-form-item>
          <el-form-item label="接待规则与人设" prop="persona"><el-input v-model="form.persona" type="textarea" :rows="5" maxlength="2000" show-word-limit /></el-form-item>
          <el-form-item label="欢迎语" prop="welcomeMessage"><el-input v-model="form.welcomeMessage" maxlength="500" /></el-form-item>
          <el-form-item label="无依据时的回复" prop="noAnswerMessage"><el-input v-model="form.noAnswerMessage" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        </section>
        <section v-show="editorTab === 'knowledge'" class="cs-form-section"><h3>知识与模型</h3><p class="muted">选择对话模型与已就绪的知识库。</p>
          <el-form-item label="对话模型" prop="modelId"><el-select v-model="form.modelId" filterable placeholder="选择已配置的对话模型" class="full-width" popper-class="customer-service-menu"><el-option v-for="model in models" :key="model.id" :label="model.name || model.modelName || model.modelCode" :value="model.id" /></el-select></el-form-item>
          <el-form-item label="响应模式"><el-select v-model="form.responseMode" class="full-width" popper-class="customer-service-menu"><el-option label="跟随模型" value="MODEL_DEFAULT" /><el-option label="快速答复" value="FAST" /></el-select></el-form-item>
          <p class="muted">快速答复按请求关闭思考，目前支持阿里云官方直连 qwen3.8-27b；复杂分析可选择跟随模型。</p>
          <el-form-item label="知识库（最多 3 个）" prop="knowledgeBaseIds"><el-select v-model="form.knowledgeBaseIds" multiple :multiple-limit="3" filterable placeholder="选择索引已就绪的知识库" class="full-width" popper-class="customer-service-menu"><el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" :disabled="kb.indexStatus !== 'READY'" /></el-select></el-form-item>
        </section>
        <section v-show="editorTab === 'voice'" class="cs-form-section"><h3>语音设置</h3><p class="muted">配置识别与回答播报。连续语音由用户主动开启。</p>
          <el-form-item label="短录音识别模型（可选）"><el-select v-model="form.voice.sttModelId" clearable class="full-width" placeholder="不配置则仅使用文字输入" popper-class="customer-service-menu"><el-option v-for="model in speechModels.filter(item => item.capability === 'AUDIO_STT')" :key="model.id" :label="model.name" :value="model.id" /></el-select></el-form-item>
          <el-form-item label="回答合成模型（可选）"><el-select v-model="form.voice.ttsModelId" clearable class="full-width" placeholder="选择已配置合成模型" popper-class="customer-service-menu"><el-option v-for="model in speechModels.filter(item => item.capability === 'AUDIO_TTS')" :key="model.id" :label="model.name" :value="model.id" /></el-select></el-form-item>
          <el-form-item label="识别术语"><el-input v-model="vocabularyText" type="textarea" :rows="3" maxlength="1000" placeholder="每行一个专有名词，例如：星河归档中心" /></el-form-item><p class="muted">最多20项，每项30字、合计300字；辅助识别，不强制替换文字。</p>
          <div class="cs-setting-row"><div><strong>简洁回答</strong><p class="muted">优先使用1～2句回答，保留必要条件与来源。</p></div><el-switch v-model="form.voice.briefReplies" aria-label="简洁回答" /></div>
          <div class="cs-setting-row"><div><strong>设备本地音色</strong><p class="muted">允许使用浏览器提供的本地中文音色。</p></div><el-switch v-model="form.voice.devicePlayback" aria-label="回答播报" /></div>
          <details class="cs-help"><summary>语音配置说明</summary><p class="muted">短录音识别后先核对文字再发送；连续语音按停顿发送、播报时关闭麦克风，支持按钮打断。供应商音色使用合成模型配置，整轮回答最多播报前600字，连续对话支持已校验分段。术语支持情况以发布校验为准。</p></details>
        </section>
        <section v-show="editorTab === 'avatar'" class="cs-form-section"><CustomerAvatarEditor v-if="editorOpen" :active="editorTab === 'avatar'" v-model="form.avatar" @busy="avatarUploading = $event" /></section>
        <section v-show="editorTab === 'capabilities'" class="cs-form-section"><CustomerCapabilityEditor v-if="editorOpen" v-model="form.capabilities" @editing="capabilityEditing = $event" /></section>
        <section v-show="editorTab === 'contact'" class="cs-form-section"><CustomerContactEditor v-if="editorOpen" v-model="form.contact" /></section>
      </el-form>
      <aside v-show="editorTab !== 'avatar'" class="cs-editor-preview"><div class="cs-section-heading"><h3>形象预览</h3><span class="cs-status">仅预览外观</span></div><CustomerAvatar v-if="editorOpen && form.avatar?.coverAssetId" :config="form.avatar" state="IDLE" :active="editorTab !== 'avatar'" /><div v-else class="cs-avatar-empty"><span aria-hidden="true">✦</span><strong>{{ form.name || '你的数字人客服' }}</strong><p>在人物形象中配置封面和动态素材</p><el-button @click="editorTab = 'avatar'">配置人物形象</el-button></div><div class="cs-preview-summary"><p><span>知识库</span><strong>{{ form.knowledgeBaseIds.length }} 个</strong></p><p><span>业务能力</span><strong>{{ form.capabilities.length }} 项</strong></p><p><span>语音识别</span><strong>{{ form.voice.sttModelId ? '已配置' : '未配置' }}</strong></p></div><p class="muted">此处不会开启麦克风或发起问答。</p></aside>
      </div>
      <template #footer><div class="cs-editor-footer"><span>保存草稿后发布，新会话才会使用新配置。</span><div><el-button @click="editorOpen = false">取消</el-button><el-button type="primary" :disabled="capabilityEditing || avatarUploading" :loading="busy" @click="save">保存草稿</el-button></div></div></template>
    </el-dialog>
    <el-drawer v-model="previewOpen" class="customer-service-preview customer-reception-drawer" title="接待测试" size="min(1440px, 100vw)" :close-on-click-modal="false"><CustomerConversation v-if="previewOpen" :app-id="previewAppId" :service-name="apps.find(item => item.id === previewAppId)?.config.name" @closed="previewOpen = false" /></el-drawer>
    <CustomerChannelPanel v-if="channelApp" :app="channelApp" @closed="channelApp = null" />
    <CustomerRecordPanel v-if="recordApp" :app="recordApp" @closed="recordApp = null" />
  </div>
</template>
<script setup>
import {computed, nextTick, onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {listAvailableModel} from '@/api/ai/model'
import {listKnowledge} from '@/api/ai/knowledge'
import CustomerCapabilityEditor from './CustomerCapabilityEditor.vue'
import './customerService.scss'
import CustomerConversation from './CustomerConversation.vue'
import CustomerAvatar from './CustomerAvatar.vue'
import CustomerChannelPanel from './CustomerChannelPanel.vue'
import CustomerAvatarEditor from './CustomerAvatarEditor.vue'
import CustomerRecordPanel from './CustomerRecordPanel.vue'
import CustomerContactEditor from './CustomerContactEditor.vue'
import {customerContactSettings} from '@/utils/customerContactState'
import {customerRecognitionVocabulary, customerVoiceConfiguration} from '@/utils/customerVoiceConfiguration'
import {customerConfirmation} from '@/utils/customerServiceState'
import {
  createCustomerService,
  listCustomerServices,
  listCustomerSpeechModels,
  publishCustomerService,
  setCustomerServiceStatus,
  updateCustomerService
} from '@/api/platform/customerService'

const apps = ref([]), models = ref([]), knowledgeBases = ref([]), speechModels = ref([])
const search = ref(''), statusFilter = ref('ALL'), publishFilter = ref('ALL'), listView = ref('cards'), listError = ref('')
const listTabs = [{ key: 'ALL', label: '全部' }, { key: 'PUBLISHED', label: '已发布' }, { key: 'DRAFT', label: '未发布' }, { key: 'DISABLED', label: '已停用' }]
const filteredApps = computed(() => apps.value.filter(row => (!search.value.trim() || row.config.name.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase()))
  && (statusFilter.value === 'ALL' || row.status === statusFilter.value)
  && (publishFilter.value === 'ALL' || publishFilter.value === 'PUBLISHED' && row.currentReleaseId || publishFilter.value === 'DRAFT' && !row.currentReleaseId || publishFilter.value === 'DISABLED' && row.status === 'DISABLED')))
const editorTab = ref('basic')
const editorTabs = [{ key: 'basic', label: '基本信息' }, { key: 'avatar', label: '人物形象' }, { key: 'knowledge', label: '知识与模型' }, { key: 'voice', label: '语音设置' }, { key: 'capabilities', label: '业务能力' }, { key: 'contact', label: '人工入口' }]
function handleAppAction(command, row) {
  if (command === 'publish') return publish(row)
  if (command === 'channels') channelApp.value = row
  if (command === 'records') recordApp.value = row
  if (command === 'status') return changeStatus(row)
}

const loading = ref(false), busy = ref(false), editorOpen = ref(false), editing = ref(null), formRef = ref()
const previewOpen = ref(false), previewAppId = ref(null), channelApp = ref(null)
const recordApp = ref(null)
const capabilityEditing = ref(false), vocabularyText = ref(''), avatarUploading = ref(false)
const defaults = () => ({ name: '', persona: '请以简洁、友好的方式提供知识咨询，只回答服务范围内的问题。', welcomeMessage: '您好，有什么可以帮您？', noAnswerMessage: '当前资料不足以确认这个问题，请补充信息或联系人工客服。', modelId: null, responseMode: 'MODEL_DEFAULT', knowledgeBaseIds: [], capabilities: [], voice: customerVoiceConfiguration(), avatar: null, contact: null })
const form = reactive(defaults())
const rules = Object.fromEntries(['name', 'persona', 'welcomeMessage', 'noAnswerMessage', 'modelId', 'knowledgeBaseIds'].map(key => [key, [{ required: true, message: '请填写或选择此项', trigger: 'change' }]]))
async function loadApps() {
  loading.value = true
  listError.value = ''
  try { apps.value = (await listCustomerServices()).data || [] } catch { listError.value = '客服列表未加载，请检查登录或稍后刷新。' } finally { loading.value = false }
}
async function loadResources() {
  const [modelResult, kbResult, speechResult] = await Promise.all([listAvailableModel(), listKnowledge({ pageNum: 1, pageSize: 200 }), listCustomerSpeechModels().catch(() => ({ data: [] }))])
  speechModels.value = speechResult.data || []
  models.value = modelResult.data || []
  knowledgeBases.value = kbResult.data?.items || kbResult.data?.list || kbResult.data?.rows || kbResult.rows || []
}
async function openCreate() {
  await loadResources()
  editing.value = null
  editorTab.value = 'basic'
  capabilityEditing.value = false
  Object.assign(form, defaults()); vocabularyText.value = ''
  editorOpen.value = true
}
async function openEdit(row) {
  await loadResources()
  editing.value = { id: row.id, revision: row.revision }
  editorTab.value = 'basic'
  capabilityEditing.value = false
  Object.assign(form, { ...row.config, avatar: row.config.avatar ? { ...row.config.avatar } : null, responseMode: row.config.responseMode || 'MODEL_DEFAULT', knowledgeBaseIds: [...row.config.knowledgeBaseIds], capabilities: row.config.capabilities || [], voice: customerVoiceConfiguration(row.config.voice || {}) })
  form.contact = row.config.contact ? { ...row.config.contact } : null
  vocabularyText.value = form.voice.vocabulary.join('\n')
  editorOpen.value = true
}
async function save() {
  if (capabilityEditing.value || avatarUploading.value) return
  try { await formRef.value.validate() } catch (fields) {
    editorTab.value = ['modelId', 'knowledgeBaseIds'].includes(Object.keys(fields || {})[0]) ? 'knowledge' : 'basic'
    await nextTick(); formRef.value?.scrollToField(Object.keys(fields || {})[0]); return
  }
  let vocabulary, contact
  try { vocabulary = customerRecognitionVocabulary(vocabularyText.value) } catch (invalid) { editorTab.value = 'voice'; ElMessage.warning(invalid.message); return }
  try { contact = customerContactSettings(form.contact) } catch (invalid) { editorTab.value = 'contact'; ElMessage.warning(invalid.message); return }
  if (form.avatar && (!form.avatar.name?.trim() || !form.avatar.coverAssetId)) { editorTab.value = 'avatar'; ElMessage.warning('请填写人物名称并选择静态封面'); return }
  busy.value = true
  try {
    const config = { ...form, contact, knowledgeBaseIds: [...form.knowledgeBaseIds], voice: { ...form.voice, vocabulary } }
    if (editing.value) await updateCustomerService(editing.value.id, editing.value.revision, config)
    else await createCustomerService(config)
    editorOpen.value = false
    ElMessage.success('草稿已保存')
    await loadApps()
  } finally { busy.value = false }
}
async function publish(row) {
  if (!await customerConfirmation(() => ElMessageBox.confirm('发布当前草稿？系统会校验模型和知识库，新会话将使用该版本。', '发布客服', { customClass: 'customer-service-confirm' }))) return
  busy.value = true
  try { await publishCustomerService(row.id, row.revision); await loadApps(); ElMessage.success('发布成功') } finally { busy.value = false }
}
async function changeStatus(row) {
  const status = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  if (!await customerConfirmation(() => ElMessageBox.confirm(status === 'DISABLED' ? '停用后将阻止新会话与新输入，正在生成的回答也会停止接收。' : '确认启用客服？', '修改客服状态', { customClass: 'customer-service-confirm' }))) return
  busy.value = true
  try { await setCustomerServiceStatus(row.id, row.revision, status); await loadApps() } finally { busy.value = false }
}
function preview(row) { previewAppId.value = row.id; previewOpen.value = true }
onMounted(() => loadApps())
</script>

