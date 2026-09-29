<template>
  <div class="app-container model-center-page">
    <ModelEditor
      v-if="editorVisible"
      :key="editingId || 'new'"
      :model-id="editingId"
      @saved="closeEditor(true)"
      @cancel="closeEditor(false)"
    />

    <template v-else>
      <div class="hero">
        <div class="hero-copy">
          <span class="eyebrow">POLARIS AI</span>
          <h1>模型中心</h1>
          <p>统一管理服务商连接和模型能力；新增能力无需修改模型主表。</p>
        </div>
        <div class="hero-actions">
          <div class="summary-pill"><b>{{ models.length }}</b><span>模型配置</span></div>
          <el-button v-if="activeTab === 'models'" class="hero-create" type="primary" size="large" round @click="openEditor(null)">＋ 新增模型</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="center-tabs">
        <el-tab-pane name="models">
          <template #label><span>模型</span></template>
          <div class="filter-row">
            <el-input v-model="keyword" class="model-search" clearable placeholder="按名称、模型编码或服务商模型搜索" />
            <el-button class="refresh-button" @click="loadModels">刷新</el-button>
          </div>
          <el-table v-loading="loading" :data="filteredModels">
            <el-table-column prop="name" label="配置名称" min-width="150" />
            <el-table-column prop="modelCode" label="模型编码" min-width="150"><template #default="{ row }"><code>{{ row.modelCode }}</code></template></el-table-column>
            <el-table-column prop="connectionName" label="连接" min-width="150" />
            <el-table-column prop="modelName" label="服务商模型" min-width="180" show-overflow-tooltip />
            <el-table-column label="分类" width="100"><template #default="{ row }">{{ modelTypeLabel(row.modelType) }}</template></el-table-column>
            <el-table-column label="已启用能力" min-width="280">
              <template #default="{ row }">
                <div v-if="displayCapabilities(row).length" class="capability-tags">
                  <el-tag v-for="capability in displayCapabilities(row).slice(0, 3)" :key="capability" size="small" class="capability-tag" round>{{ capabilityLabel(capability) }}</el-tag>
                  <span v-if="displayCapabilities(row).length > 3" class="capability-more">+{{ displayCapabilities(row).length - 3 }}</span>
                </div>
                <span v-else class="empty-capability">未配置</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.status === '1' ? 'success' : 'info'">{{ row.status === '1' ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column label="默认模型" width="160">
              <template #default="{ row }">
                <div class="capability-tags">
                  <el-tag v-for="capability in defaultCapabilities(row)" :key="capability" size="small" type="success" round>{{ capabilityLabel(capability) }}默认</el-tag>
                  <el-dropdown v-if="availableDefaultCapabilities(row).length" :disabled="loading || settingDefault || !defaultsReady" @command="capability => setDefault(row, capability)">
                    <el-button class="default-action" link type="primary" :disabled="loading || settingDefault || !defaultsReady">设为默认<el-icon class="el-icon--right"><arrow-down /></el-icon></el-button>
                    <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="capability in availableDefaultCapabilities(row)" :key="capability" :command="capability">{{ capabilityLabel(capability) }}</el-dropdown-item></el-dropdown-menu></template>
                  </el-dropdown>
                  <span v-if="!defaultCapabilities(row).length && !availableDefaultCapabilities(row).length" class="empty-capability">—</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }"><el-button class="edit-action" link type="primary" @click="openEditor(row.id)">编辑</el-button><el-button class="delete-action" link type="danger" @click="remove(row)">删除</el-button></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane name="connections">
          <template #label><span>服务商连接</span></template>
          <ProviderConnectionManager @changed="loadModels" />
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import ModelEditor from '@/components/model-center/ModelEditor.vue'
import ProviderConnectionManager from '@/components/model-center/ProviderConnectionManager.vue'
import {deleteModelAggregate, getCapabilityDefault, listModelAggregates, setCapabilityDefault} from '@/api/ai/model'

const activeTab = ref('models')
const models = ref([])
const keyword = ref('')
const loading = ref(false)
const capabilityDefaults = ref({})
const defaultsReady = ref(false)
const settingDefault = ref(false)
const editorVisible = ref(false)
const editingId = ref(null)
const filteredModels = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  if (!term) return models.value
  return models.value.filter(item => [item.name, item.modelCode, item.modelName, item.connectionName]
    .some(value => String(value || '').toLowerCase().includes(term)))
})

onMounted(loadModels)
async function loadModels() {
  loading.value = true
  defaultsReady.value = false
  try {
    models.value = unwrap(await listModelAggregates()) || []
    const capabilities = [...new Set(models.value.flatMap(invocationCapabilities))]
    const entries = await Promise.all(capabilities.map(async capability => {
      try { return [capability, unwrap(await getCapabilityDefault(capability))] }
      catch (error) {
        if (String(error?.message || error).includes('DEFAULT_MODEL_NOT_CONFIGURED')) return [capability, null]
        throw error
      }
    }))
    capabilityDefaults.value = Object.fromEntries(entries)
    defaultsReady.value = true
  } catch {
    ElMessage.error('模型或默认状态加载失败，请刷新重试')
  }
  finally { loading.value = false }
}
function openEditor(id) { editingId.value = id; editorVisible.value = true }
async function closeEditor(reload) { editorVisible.value = false; editingId.value = null; if (reload) await loadModels() }
async function remove(row) {
  await ElMessageBox.confirm(`确认删除模型“${row.name}”？`, '提示', { type: 'warning' })
  await deleteModelAggregate(row.id)
  ElMessage.success('模型已删除')
  await loadModels()
}
async function setDefault(row, capability) {
  if (loading.value || settingDefault.value || !defaultsReady.value || !availableDefaultCapabilities(row).includes(capability)) return
  settingDefault.value = true
  try {
    await setCapabilityDefault(capability, row.id)
    capabilityDefaults.value = { ...capabilityDefaults.value, [capability]: row.id }
    ElMessage.success(`${capabilityLabel(capability)}默认模型已更新`)
  } catch {
    // 请求层已展示失败原因，保留原有默认状态。
  } finally { settingDefault.value = false }
}
function defaultCapabilities(row) {
  return defaultsReady.value ? invocationCapabilities(row).filter(capability => String(capabilityDefaults.value[capability]) === String(row.id)) : []
}
function availableDefaultCapabilities(row) {
  return String(row.status) === '1' ? invocationCapabilities(row).filter(capability => !defaultCapabilities(row).includes(capability)) : []
}
function invocationCapabilities(row) { return (row.enabledCapabilities || []).filter(item => !item.includes('@')) }
function displayCapabilities(row) { return row.enabledCapabilities || [] }
function capabilityLabel(code) {
  const capability = String(code || '').split('@')[0]
  return ({
    CHAT_COMPLETION: '聊天生成', TEXT_EMBEDDING: '文本向量',
    IMAGE_GENERATION: '图片生成', IMAGE_EDIT: '图片编辑',
    IMAGE_INPAINT: '图片局部重绘', IMAGE_VARIATION: '图片变体',
    AUDIO_TTS: '语音合成', AUDIO_STT: '语音识别',
    VIDEO_GENERATION: '视频生成', RERANK: '文档重排',
    STREAMING: '流式输出', TOOL_CALLING: '工具调用',
    REASONING: '推理思考', VISION_INPUT: '图像输入'
  })[capability] || capability
}
function modelTypeLabel(type) {
  return ({ GENERAL: '通用', CHAT: '对话', EMBEDDING: '向量', IMAGE: '图像', AUDIO: '音频', VIDEO: '视频', RERANK: '重排' })[type] || type
}
function unwrap(response) { return response?.data?.data ?? response?.data ?? response }
</script>

<style scoped lang="scss">
.model-center-page {
  --mc-accent: #2563eb;
  --mc-accent-hover: #1d4ed8;
  --mc-accent-soft: #eff6ff;
  --mc-surface: #ffffff;
  --mc-surface-muted: #f8fafc;
  --mc-page-bg: #f5f7fb;
  --mc-text: #172033;
  --mc-muted: #64748b;
  --mc-border: #dbe3ef;
  --mc-danger: #dc2626;
  --el-color-primary: var(--mc-accent);
  min-height: calc(100vh - 84px);
  padding: 22px 28px 36px;
  color: var(--mc-text);
  background: radial-gradient(circle at 92% 0, rgba(37, 99, 235, .1), transparent 30%), var(--mc-page-bg);
}
:global(html.dark .model-center-page) {
  --mc-accent: #60a5fa;
  --mc-accent-hover: #93c5fd;
  --mc-accent-soft: #172554;
  --mc-surface: #171b24;
  --mc-surface-muted: #11151d;
  --mc-page-bg: #0e1118;
  --mc-text: #f1f5f9;
  --mc-muted: #a8b3c5;
  --mc-border: #303949;
  --mc-danger: #fb7185;
}
.hero { position: relative; overflow: hidden; display: flex; align-items: center; justify-content: space-between; margin-bottom: 22px; padding: 28px 30px; border: 1px solid var(--mc-border); border-radius: 20px; background: linear-gradient(125deg, var(--mc-accent-soft), var(--mc-surface) 52%, rgba(16, 185, 129, .08)); box-shadow: 0 14px 38px rgba(15, 23, 42, .08); }
.hero::after { position: absolute; right: -50px; top: -90px; width: 240px; height: 240px; border: 36px solid rgba(64, 158, 255, .08); border-radius: 50%; content: ''; }
.hero-copy, .hero-actions { position: relative; z-index: 1; }
.eyebrow { display: inline-block; margin-bottom: 8px; color: var(--mc-accent); font-size: 11px; font-weight: 800; letter-spacing: .16em; }
.hero h1 { margin: 0 0 8px; color: var(--mc-text); font-size: 28px; letter-spacing: -.02em; }.hero p { margin: 0; color: var(--mc-muted); }
.hero-actions { display: flex; align-items: center; gap: 14px; }
.summary-pill { display: flex; align-items: baseline; gap: 7px; padding: 9px 14px; border: 1px solid var(--mc-border); border-radius: 999px; background: var(--mc-surface); }
.summary-pill b { color: var(--mc-accent) !important; font-size: 20px; }.summary-pill span { color: var(--mc-muted) !important; font-size: 12px; }
.hero :deep(.hero-create) { border: 0 !important; color: #fff !important; background: linear-gradient(135deg, #2563eb, #7c3aed) !important; box-shadow: 0 9px 24px rgba(37, 99, 235, .28); }
.center-tabs { padding: 0 4px; }.center-tabs :deep(.el-tabs__header) { margin-bottom: 20px; }.center-tabs :deep(.el-tabs__item) { height: 44px; font-weight: 600; }
.center-tabs :deep(.el-tabs__item) { color: var(--mc-muted) !important; }
.center-tabs :deep(.el-tabs__item.is-active) { color: var(--mc-accent) !important; }
.center-tabs :deep(.el-tabs__active-bar) { background-color: var(--mc-accent) !important; }
.filter-row { display: flex; gap: 10px; margin-bottom: 16px; }
.model-search { width: min(420px, calc(100% - 76px)); }
.filter-row :deep(.el-input__wrapper) { border-radius: 10px; }
.refresh-button { border-color: var(--mc-border) !important; color: var(--mc-text) !important; background: var(--mc-surface) !important; }
.center-tabs :deep(.el-table) { --el-table-bg-color: var(--mc-surface); --el-table-tr-bg-color: var(--mc-surface); --el-table-text-color: var(--mc-text); overflow: hidden; border: 1px solid var(--mc-border); border-radius: 14px; background: var(--mc-surface); }
.center-tabs :deep(.el-table th.el-table__cell) { height: 48px; background: var(--mc-surface-muted) !important; color: var(--mc-text) !important; }
.center-tabs :deep(.el-table td.el-table__cell), .center-tabs :deep(.el-table__fixed-right) { background: var(--mc-surface) !important; color: var(--mc-text) !important; }
.capability-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.capability-tag { margin: 0; border-color: rgba(37, 99, 235, .25) !important; color: var(--mc-accent) !important; background: var(--mc-accent-soft) !important; }
:global(html.dark .model-center-page .capability-tag) { border-color: rgba(96, 165, 250, .55) !important; color: #dbeafe !important; background: #1e3a5f !important; }
:global(html.dark .model-center-page .el-tag.el-tag--success) { border-color: rgba(74, 222, 128, .5) !important; color: #bbf7d0 !important; background: rgba(20, 83, 45, .82) !important; }
:global(html.dark .model-center-page .el-tag.el-tag--info) { border-color: rgba(148, 163, 184, .45) !important; color: #e2e8f0 !important; background: rgba(51, 65, 85, .82) !important; }
.capability-more, .empty-capability { color: var(--mc-muted); font-size: 12px; }
.default-action, .edit-action { color: var(--mc-accent) !important; }.delete-action { color: var(--mc-danger) !important; }
code { color: var(--mc-accent); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
@media (max-width: 900px) { .model-center-page { padding: 14px; } .hero { align-items: flex-start; flex-direction: column; gap: 18px; } .hero-actions { width: 100%; justify-content: space-between; } }
</style>
