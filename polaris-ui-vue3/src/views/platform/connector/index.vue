<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <div class="polaris-table-card">
        <div class="polaris-action-row">
          <div class="actions-left">
            <el-button class="action-btn-primary" type="primary" icon="Plus" @click="handleAdd">新建 API 连接器</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="connectorList" class="polaris-el-table">
          <el-table-column prop="connectorName" label="连接器名称" min-width="160" :show-overflow-tooltip="true">
            <template #default="scope">
              <span class="connector-name-text">{{ scope.row.connectorName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="baseUrl" label="基础 URL" min-width="260" :show-overflow-tooltip="true">
            <template #default="scope">
              <span class="url-text">{{ scope.row.baseUrl }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="authType" label="认证方式" align="center" width="140">
            <template #default="scope">
              <span :class="['auth-type-badge', scope.row.authType ? scope.row.authType.toLowerCase() : '']">
                {{ scope.row.authType || 'NONE' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="timeoutMs" label="超时时间" align="center" width="120">
            <template #default="scope">
              <span class="timeout-text">{{ (scope.row.timeoutMs || 30000) / 1000 }}s</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="120">
            <template #default="scope">
              <div :class="['status-cell', scope.row.status !== '0' ? 'status-disabled' : '']">
                <span :class="['pulse-light-ripple', scope.row.status === '0' ? 'pulse-active' : 'pulse-error']"></span>
                <span class="status-label" :class="scope.row.status === '0' ? 'text-active' : 'text-error'">
                  {{ scope.row.status === '0' ? '正常' : '停用' }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="140" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-tooltip content="修改配置" placement="top">
                <el-button link icon="Edit" @click="handleUpdate(scope.row)" class="table-opt-btn opt-edit" />
              </el-tooltip>
              <el-tooltip content="删除连接器" placement="top">
                <el-button link icon="Delete" @click="handleDelete(scope.row)" class="table-opt-btn opt-del" />
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 弹窗 -->
    <el-dialog :title="title" v-model="open" width="680px" append-to-body class="polaris-glass-dialog">
      <ApiConnectorForm ref="connectorFormRef" :form="form" :editing="!!form.id" />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="open = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确定保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  addConnector,
  delConnector,
  getConnector,
  getConnectorUsages,
  listConnector,
  updateConnector
} from '@/api/platform/connector'
import ApiConnectorForm from '@/components/platform/ApiConnectorForm.vue'

const loading = ref(false)
const connectorList = ref([])
const open = ref(false)
const title = ref('')
const submitLoading = ref(false)
const connectorFormRef = ref(null)

const form = reactive({
  id: undefined,
  connectorName: '',
  baseUrl: '',
  authType: 'NONE',
  originalAuthType: 'NONE',
  credentialAction: 'CLEAR',
  credentialConfigured: false,
  credential: {headerName: 'X-API-Key', secret: ''},
  headerRows: [],
  timeoutSeconds: 30,
  status: '0',
  remark: ''
})

function getList() {
  loading.value = true
  listConnector().then(res => {
    connectorList.value = res.data?.rows || []
    loading.value = false
  })
}

function handleAdd() {
  Object.assign(form, {
    id: undefined,
    connectorName: '',
    baseUrl: '',
    authType: 'NONE',
    originalAuthType: 'NONE',
    credentialAction: 'CLEAR',
    credentialConfigured: false,
    credential: {headerName: 'X-API-Key', secret: ''},
    headerRows: [],
    timeoutSeconds: 30,
    status: '0',
    remark: ''
  })
  title.value = '新建 API 连接器'
  open.value = true
}

async function handleUpdate(row) {
  const response = await getConnector(row.id)
  const detail = response.data || row
  Object.assign(form, {
    id: detail.id,
    connectorName: detail.connectorName || '',
    baseUrl: detail.baseUrl || '',
    authType: detail.authType || 'NONE',
    originalAuthType: detail.authType || 'NONE',
    credentialAction: detail.authType === 'NONE' ? 'CLEAR' : 'KEEP',
    credentialConfigured: !!detail.credentialConfigured,
    credential: {headerName: 'X-API-Key', secret: ''},
    headerRows: parseHeaders(detail.defaultHeaders),
    timeoutSeconds: Math.max(1, Math.round((detail.timeoutMs || 30000) / 1000)),
    status: detail.status || '0',
    remark: detail.remark || ''
  })
  title.value = '修改 API 连接器'
  open.value = true
}

async function submitForm() {
  try {
    await connectorFormRef.value.validate()
  } catch (error) {
    return
  }
  submitLoading.value = true
  try {
    const payload = connectorFormRef.value.buildPayload()
    await (form.id ? updateConnector(payload) : addConnector(payload))
    ElMessage.success('保存成功')
    open.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

function parseHeaders(value) {
  try {
    const headers = typeof value === 'string' ? JSON.parse(value || '{}') : (value || {})
    return Object.entries(headers).map(([name, headerValue]) => ({name, value: String(headerValue)}))
  } catch (error) {
    return []
  }
}

async function handleDelete(row) {
  const usageResponse = await getConnectorUsages(row.id)
  const usageCount = usageResponse.data?.activeBindingCount || 0
  if (usageCount > 0) {
    ElMessage.warning(`该连接器仍被 ${usageCount} 个工作流资源绑定使用，请先解除绑定或停用连接器`)
    return
  }
  await ElMessageBox.confirm(`确定删除连接器 "${row.connectorName}" 吗？`, '警告', {type: 'warning'})
  await delConnector(row.id)
  ElMessage.success('删除成功')
  getList()
}

onMounted(getList)
</script>

<style lang="scss" scoped>
.app-container.no-sidebar-manage-wrap {
  padding: 16px !important;
}

.content-inner {
  padding: 0 !important;
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

.connector-name-text {
  font-weight: 600;
  color: #0f172a;

  .dark & {
    color: #f1f5f9;
  }
}

.url-text {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  color: #64748b;

  .dark & {
    color: #94a3b8;
  }
}

.auth-type-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 6px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.5px;

  &.api_key {
    color: #0284c7 !important;
    background: rgba(2, 132, 199, 0.1) !important;
    border: 1px solid rgba(2, 132, 199, 0.25) !important;
  }
  &.bearer {
    color: #4338ca !important;
    background: rgba(67, 56, 202, 0.1) !important;
    border: 1px solid rgba(67, 56, 202, 0.25) !important;
  }
  &.none {
    color: #64748b !important;
    background: rgba(0, 0, 0, 0.04) !important;
    border: 1px solid rgba(0, 0, 0, 0.08) !important;
  }

  .dark & {
    &.api_key { color: #38bdf8 !important; background: rgba(56, 189, 248, 0.15) !important; }
    &.bearer { color: #818cf8 !important; background: rgba(129, 140, 248, 0.15) !important; }
    &.none { color: #94a3b8 !important; background: rgba(255, 255, 255, 0.06) !important; }
  }
}

.timeout-text {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-weight: 600;
  color: #64748b;

  .dark & {
    color: #94a3b8;
  }
}

.status-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 3px 12px;
  border-radius: 20px;
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.25);

  &.status-disabled {
    background: rgba(239, 68, 68, 0.1);
    border-color: rgba(239, 68, 68, 0.25);
  }

  .dark & {
    background: rgba(16, 185, 129, 0.15);
    border-color: rgba(16, 185, 129, 0.35);

    &.status-disabled {
      background: rgba(239, 68, 68, 0.15);
      border-color: rgba(239, 68, 68, 0.35);
    }
  }
}

.table-opt-btn {
  font-size: 15px;
  padding: 6px;
  border-radius: 8px;
  transition: all 0.2s ease;

  &.opt-edit {
    color: #4f46e5 !important;
    &:hover { background: rgba(79, 70, 229, 0.1); }
  }
  &.opt-del {
    color: #ef4444 !important;
    &:hover { background: rgba(239, 68, 68, 0.1); }
  }

  .dark & {
    &.opt-edit { color: #818cf8 !important; }
    &.opt-del { color: #f87171 !important; }
  }
}
</style>
