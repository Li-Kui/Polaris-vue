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
    <el-dialog :title="title" v-model="open" width="600px" append-to-body class="polaris-glass-dialog">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="连接器名称" prop="connectorName">
          <el-input v-model="form.connectorName" placeholder="例如: 财务系统发票查询接口" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.example.com/v1" />
        </el-form-item>
        <el-form-item label="认证类型" prop="authType">
          <el-select v-model="form.authType" placeholder="请选择" style="width: 100%;">
            <el-option label="无需认证 (NONE)" value="NONE" />
            <el-option label="API Key (Header 传递)" value="API_KEY" />
            <el-option label="Bearer Token" value="BEARER" />
          </el-select>
        </el-form-item>
        <el-form-item label="超时时间" prop="timeoutMs">
          <el-input-number v-model="form.timeoutMs" :min="1000" :step="1000" style="width: 180px;" />
          <span style="margin-left: 10px; color: #94a3b8; font-size: 13px;">毫秒 (ms)</span>
        </el-form-item>
      </el-form>
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
import {addConnector, delConnector, listConnector, updateConnector} from '@/api/platform/connector'

const loading = ref(false)
const connectorList = ref([])
const open = ref(false)
const title = ref('')
const submitLoading = ref(false)
const formRef = ref(null)

const form = reactive({
  id: undefined,
  connectorName: '',
  baseUrl: '',
  authType: 'NONE',
  timeoutMs: 30000
})

const rules = {
  connectorName: [{ required: true, message: '请输入连接器名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }]
}

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
    timeoutMs: 30000
  })
  title.value = '新建 API 连接器'
  open.value = true
}

function handleUpdate(row) {
  Object.assign(form, row)
  title.value = '修改 API 连接器'
  open.value = true
}

function submitForm() {
  formRef.value.validate(valid => {
    if (valid) {
      submitLoading.value = true
      const action = form.id ? updateConnector(form) : addConnector(form)
      action.then(() => {
        ElMessage.success('保存成功')
        open.value = false
        submitLoading.value = false
        getList()
      }).catch(() => {
        submitLoading.value = false
      })
    }
  })
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除连接器 "${row.connectorName}" 吗？`, '警告', { type: 'warning' }).then(() => {
    delConnector(row.id).then(() => {
      ElMessage.success('删除成功')
      getList()
    })
  })
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
