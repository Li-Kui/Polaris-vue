<template>
  <div>
    <div class="toolbar">
      <div><h2>服务商连接</h2><p>集中维护 API 密钥和服务地址，多个模型可以复用同一个连接。</p></div>
      <el-button class="connection-create" type="primary" round @click="openCreate">＋ 新增连接</el-button>
    </div>
    <el-table v-loading="loading" :data="connections">
      <el-table-column prop="connectionName" label="连接名称" min-width="160" />
      <el-table-column label="服务商" width="140"><template #default="{ row }">{{ profileLabel(row.providerCode) }}</template></el-table-column>
      <el-table-column label="连接方式" width="110"><template #default="{ row }"><el-tag class="mode-tag" size="small" round>{{ accessModeLabel(row.networkMode) }}</el-tag></template></el-table-column>
      <el-table-column prop="baseUrl" label="服务地址" min-width="240" show-overflow-tooltip />
      <el-table-column label="API 密钥" width="100"><template #default="{ row }"><el-tag :type="row.credentialConfigured ? 'success' : 'warning'" round>{{ row.credentialConfigured ? '已配置' : '未配置' }}</el-tag></template></el-table-column>
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-switch :model-value="row.status === '1'" @change="toggleStatus(row, $event)" /></template></el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button class="edit-action" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button class="delete-action" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑连接' : '新增连接'" width="min(660px, calc(100vw - 28px))" class="provider-connection-dialog" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="连接名称"><el-input v-model="form.connectionName" /></el-form-item>
        <el-alert :title="form.id ? '可以调整连接方式和服务地址；服务商类型创建后保持不变。' : '选择服务商后会自动填写推荐地址。'" type="info" :closable="false" class="form-tip" />
        <el-form-item label="服务商">
          <el-input v-if="form.id" :model-value="profileLabel(form.providerCode)" disabled />
          <template v-else>
            <el-select v-model="form.providerCode" style="width: 100%" @change="providerChanged">
              <el-option v-for="profile in profiles" :key="profile.code" :label="profileLabel(profile.code)" :value="profile.code">
                <div class="provider-option"><span>{{ profileLabel(profile.code) }}</span><small>{{ providerDescription(profile.code) }}</small></div>
              </el-option>
            </el-select>
          </template>
        </el-form-item>
        <el-form-item label="连接方式">
          <el-radio-group v-model="form.networkMode">
            <el-radio-button value="DIRECT">直连厂商</el-radio-button>
            <el-radio-button value="RELAY">兼容中转</el-radio-button>
          </el-radio-group>
          <div class="field-tip">直连使用厂商接口；兼容中转使用你填写的 OpenAI 兼容地址。两种方式都会保留当前服务商的适配能力。</div>
        </el-form-item>
        <el-form-item label="服务地址"><el-input v-model="form.baseUrl" placeholder="例如 https://api.example.com/v1" /></el-form-item>
        <el-form-item v-if="form.id" label="凭据操作">
          <el-radio-group v-model="form.credentialAction"><el-radio value="KEEP">保持</el-radio><el-radio value="REPLACE">替换</el-radio><el-radio value="CLEAR">清除</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item v-if="!form.id || form.credentialAction === 'REPLACE'" label="API 密钥">
          <el-input v-model="form.apiKey" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {getModelEditorContext} from '@/api/ai/model'
import {
  changeProviderConnectionStatus,
  createProviderConnection,
  deleteProviderConnection,
  listProviderConnections,
  updateProviderConnection
} from '@/api/ai/providerConnection'

const emit = defineEmits(['changed'])
const connections = ref([])
const profiles = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive({})
onMounted(load)

async function load() {
  loading.value = true
  try {
    const [listResponse, contextResponse] = await Promise.all([listProviderConnections({ pageNum: 1, pageSize: 200 }), getModelEditorContext()])
    const page = unwrap(listResponse)
    connections.value = page?.rows || page?.records || page || []
    profiles.value = (unwrap(contextResponse)?.providerProfiles || []).filter(item => item.layer === 'PROVIDER')
  } finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { id: null, connectionName: '', providerCode: '', protocolCode: '', networkMode: 'DIRECT', baseUrl: '', apiKey: '', status: '1', remark: '' })
  dialogVisible.value = true
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id, revision: row.revision, connectionName: row.connectionName,
    providerCode: row.providerCode, protocolCode: row.protocolCode,
    networkMode: row.networkMode, baseUrl: row.baseUrl,
    credentialAction: 'KEEP', apiKey: '', remark: row.remark || ''
  })
  dialogVisible.value = true
}
function providerChanged(code) {
  const profile = profiles.value.find(item => item.code === code)
  if (!profile) return
  form.protocolCode = profile.protocolCode
  form.baseUrl = profile.defaultBaseUrl
}
function profileLabel(code) {
  return ({
    OPENAI: 'OpenAI', DASHSCOPE: '阿里云百炼', DEEPSEEK: 'DeepSeek',
    SILICONFLOW: '硅基流动', ARK: '火山方舟', CUSTOM: '自定义兼容服务'
  })[code] || code
}
function providerDescription(code) {
  return ({
    OPENAI: 'OpenAI 官方接口', DASHSCOPE: '通义千问与百炼模型',
    DEEPSEEK: 'DeepSeek 官方接口',
    SILICONFLOW: '第三方平台，支持多种开源模型',
    ARK: '火山引擎模型服务', CUSTOM: '自建服务或其他兼容接口'
  })[code] || ''
}
function accessModeLabel(mode) { return String(mode).toUpperCase() === 'RELAY' ? '兼容中转' : '直连厂商' }
async function save() {
  if (!form.connectionName?.trim()) return ElMessage.error('请输入连接名称')
  if (!form.providerCode) return ElMessage.error('请选择服务商')
  if (!form.baseUrl?.trim()) return ElMessage.error('请输入服务地址')
  if (form.credentialAction === 'REPLACE' && !form.apiKey) return ElMessage.error('请输入新的 API 密钥')
  saving.value = true
  try {
    if (form.id) {
      await updateProviderConnection(form.id, {
        connectionName: form.connectionName.trim(), networkMode: form.networkMode,
        baseUrl: form.baseUrl, credentialAction: form.credentialAction,
        credential: form.credentialAction === 'REPLACE' ? { apiKey: form.apiKey } : {},
        expectedRevision: form.revision, remark: form.remark || null
      })
    } else {
      await createProviderConnection({
        connectionName: form.connectionName.trim(), providerCode: form.providerCode,
        protocolCode: form.protocolCode, networkMode: form.networkMode,
        baseUrl: form.baseUrl, credential: form.apiKey ? { apiKey: form.apiKey } : {},
        extraConfig: {}, status: '1', remark: form.remark || null
      })
    }
    dialogVisible.value = false
    ElMessage.success('连接已保存')
    await load()
    emit('changed')
  } finally { saving.value = false }
}
async function toggleStatus(row, enabled) {
  await changeProviderConnectionStatus(row.id, { status: enabled ? '1' : '0', expectedRevision: row.revision })
  await load(); emit('changed')
}
async function remove(row) {
  await ElMessageBox.confirm(`确认删除连接“${row.connectionName}”？`, '提示', { type: 'warning' })
  await deleteProviderConnection(row.id)
  ElMessage.success('连接已删除')
  await load(); emit('changed')
}
function unwrap(response) { return response?.data?.data ?? response?.data ?? response }
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; padding: 4px 2px; }
.toolbar h2 { margin: 0 0 6px; color: var(--mc-text); font-size: 20px; }.toolbar p { margin: 0; color: var(--mc-muted); }
.toolbar :deep(.connection-create) { border: 0 !important; color: #fff !important; background: linear-gradient(135deg, #2563eb, #7c3aed) !important; box-shadow: 0 8px 20px rgba(37, 99, 235, .24); }
:deep(.el-table) { --el-table-bg-color: var(--mc-surface); --el-table-tr-bg-color: var(--mc-surface); --el-table-text-color: var(--mc-text); overflow: hidden; border: 1px solid var(--mc-border); border-radius: 14px; background: var(--mc-surface); }
:deep(.el-table th.el-table__cell) { height: 48px; background: var(--mc-surface-muted) !important; color: var(--mc-text) !important; }
:deep(.el-table td.el-table__cell), :deep(.el-table__fixed-right) { background: var(--mc-surface) !important; color: var(--mc-text) !important; }
.mode-tag { border-color: var(--mc-border) !important; color: var(--mc-text) !important; background: var(--mc-surface-muted) !important; }
.edit-action { color: var(--mc-accent) !important; }.delete-action { color: var(--mc-danger) !important; }
.field-tip { margin-top: 8px; color: var(--mc-muted); font-size: 12px; line-height: 1.5; }
:global(.provider-option) { display: flex; align-items: center; justify-content: space-between; gap: 24px; min-width: 360px; }
:global(.provider-option small) { color: #8492a6; font-size: 12px; }
.form-tip { margin-bottom: 16px; }
:global(.provider-connection-dialog) { overflow: hidden; display: flex; max-height: calc(100vh - 32px); flex-direction: column; margin: 16px auto !important; border: 1px solid var(--mc-border); border-radius: 18px !important; color: var(--mc-text); background: var(--mc-surface) !important; }
:global(.provider-connection-dialog .el-dialog__header) { margin-right: 0; padding: 22px 24px 16px; border-bottom: 1px solid var(--mc-border); }
:global(.provider-connection-dialog .el-dialog__title), :global(.provider-connection-dialog .el-form-item__label) { color: var(--mc-text) !important; }
:global(.provider-connection-dialog .el-dialog__body) { overflow-y: auto; min-height: 0; padding: 20px 24px; color: var(--mc-text); background: var(--mc-surface); }
:global(.provider-connection-dialog .el-dialog__footer) { padding: 14px 24px 20px; border-top: 1px solid var(--mc-border); background: var(--mc-surface-muted); }
@media (max-width: 700px) { .toolbar { align-items: flex-start; flex-direction: column; gap: 14px; } }
</style>
