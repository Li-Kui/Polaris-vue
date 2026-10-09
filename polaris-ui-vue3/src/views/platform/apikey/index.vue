<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <div class="polaris-table-card">
        <div class="polaris-action-row">
          <div class="actions-left">
            <el-button class="action-btn-primary" type="primary" icon="Plus" @click="handleAdd">新建 API 密钥</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="keyList" class="polaris-el-table">
          <el-table-column prop="keyName" label="密钥名称" min-width="150" :show-overflow-tooltip="true">
            <template #default="scope">
              <span class="key-name-text">{{ scope.row.keyName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="keyPrefix" label="API Key" min-width="280">
            <template #default="scope">
              <div class="api-key-box">
                <span class="api-key-badge">{{ formatApiKey(scope.row.keyPrefix) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="rateLimit" label="限流频率" align="center" width="130">
            <template #default="scope">
              <span class="rate-badge">{{ scope.row.rateLimit || 60 }} 次/分</span>
            </template>
          </el-table-column>
          <el-table-column label="工作流范围" min-width="180" show-overflow-tooltip>
            <template #default="scope">
              {{ formatAllowedWorkflows(scope.row.allowedWorkflows) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="120">
            <template #default="scope">
              <div :class="['status-cell', keyStatus(scope.row) !== '正常' ? 'status-disabled' : '']">
                <span :class="['pulse-light-ripple', keyStatus(scope.row) === '正常' ? 'pulse-active' : 'pulse-error']"></span>
                <span class="status-label" :class="keyStatus(scope.row) === '正常' ? 'text-active' : 'text-error'">
                  {{ keyStatus(scope.row) }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="有效期" align="center" width="170">
            <template #default="scope">
              {{ scope.row.expireTime || '永久有效' }}
            </template>
          </el-table-column>
          <el-table-column prop="lastUsedTime" label="最后调用时间" align="center" width="170" />
          <el-table-column prop="createTime" label="创建时间" align="center" width="170" />
          <el-table-column label="操作" align="center" width="180" fixed="right" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-button link type="primary" @click="handleValidity(scope.row)">有效期与状态</el-button>
              <el-tooltip content="删除密钥" placement="top">
                <el-button link icon="Delete" aria-label="删除密钥" @click="handleDelete(scope.row)" class="table-opt-btn opt-del" />
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 新增弹窗 -->
    <el-dialog title="新建 API Key" v-model="open" width="520px" append-to-body class="polaris-glass-dialog api-key-dialog">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="密钥名称" prop="keyName">
          <el-input v-model="form.keyName" placeholder="例如: 智能问答业务集成" />
        </el-form-item>
        <el-form-item label="限流频率" prop="rateLimit">
          <el-input-number v-model="form.rateLimit" :min="1" :max="10000" style="width: 160px;" />
          <span style="margin-left: 10px; color: #94a3b8; font-size: 13px;">次/分钟</span>
        </el-form-item>
        <el-form-item label="API 权限" prop="permissionValues">
          <el-checkbox-group v-model="form.permissionValues" class="permission-list">
            <el-checkbox label="chat">大模型对话</el-checkbox>
            <el-checkbox label="workflow:execute">发起工作流</el-checkbox>
            <el-checkbox label="workflow:read">查询工作流执行</el-checkbox>
            <el-checkbox label="workflow:cancel">取消工作流执行</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="工作流范围">
          <el-input
            v-model="form.allowedWorkflowsText"
            type="textarea"
            :rows="3"
            placeholder="每行一个工作流编码；留空表示不限制"
          />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker v-model="form.expireTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="留空表示永久有效" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="open = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确定创建</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="API Key 有效期与状态" v-model="validityOpen" width="520px" append-to-body class="polaris-glass-dialog api-key-dialog">
      <el-form :model="validityForm" label-width="90px">
        <el-form-item label="密钥名称">{{ validityForm.keyName }}</el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="validityForm.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker v-model="validityForm.expireTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="留空表示永久有效" />
        </el-form-item>
        <el-alert title="停用或过期后无法继续调用，已有事件连接也会关闭；不会取消已启动的工作流任务。"
                  type="info" :closable="false" show-icon />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="validityOpen = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitValidity">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- API Key 仅在创建成功时展示一次 -->
    <el-dialog title="API Key 创建成功" v-model="createdKeyOpen" width="560px" append-to-body
               :close-on-click-modal="false" @closed="createdApiKey = ''" class="polaris-glass-dialog api-key-dialog">
      <el-alert title="请立即复制并妥善保存，此密钥关闭后将无法再次查看。" type="warning" :closable="false" show-icon />
      <div class="created-key-box">
        <el-input :model-value="createdApiKey" readonly>
          <template #append>
            <el-button icon="DocumentCopy" class="copy-btn" @click="copyText(createdApiKey)">复制</el-button>
          </template>
        </el-input>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="createdKeyOpen = false">我已保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {addApiKey, delApiKey, listApiKey, updateApiKey} from '@/api/platform/apiKey'

const loading = ref(false)
const keyList = ref([])
const open = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const createdKeyOpen = ref(false)
const createdApiKey = ref('')
const validityOpen = ref(false)
const validityForm = reactive({id: null, keyName: '', status: '0', expireTime: null})

const form = reactive({
  keyName: '',
  rateLimit: 60,
  permissionValues: ['chat'],
  allowedWorkflowsText: '',
  expireTime: null
})

const rules = {
  keyName: [{ required: true, message: '请输入密钥名称', trigger: 'blur' }]
}

function getList() {
  loading.value = true
  listApiKey().then(res => {
    keyList.value = res.data?.rows || []
  }).finally(() => {
    loading.value = false
  })
}

function handleAdd() {
  form.keyName = ''
  form.rateLimit = 60
  form.permissionValues = ['chat']
  form.allowedWorkflowsText = ''
  form.expireTime = null
  open.value = true
}

function submitForm() {
  formRef.value.validate(valid => {
    if (valid) {
      submitLoading.value = true
      addApiKey({
        keyName: form.keyName,
        rateLimit: form.rateLimit,
        expireTime: form.expireTime || null,
        permissions: JSON.stringify(form.permissionValues),
        allowedWorkflows: form.allowedWorkflowsText.trim()
          ? JSON.stringify(form.allowedWorkflowsText.split('\n')
            .map(item => item.trim()).filter(Boolean))
          : null
      }).then(res => {
        createdApiKey.value = res.data?.apiKey || ''
        ElMessage.success('创建 API Key 成功')
        open.value = false
        submitLoading.value = false
        createdKeyOpen.value = true
        getList()
      }).catch(() => {
        submitLoading.value = false
      })
    }
  })
}

function handleValidity(row) {
  Object.assign(validityForm, {
    id: row.id,
    keyName: row.keyName,
    status: row.status,
    expireTime: row.expireTime || null
  })
  validityOpen.value = true
}

function submitValidity() {
  submitLoading.value = true
  updateApiKey({
    id: validityForm.id,
    status: validityForm.status,
    expireTime: validityForm.expireTime || null
  }).then(() => {
    ElMessage.success('更新有效期与状态成功')
    validityOpen.value = false
    getList()
  }).finally(() => {
    submitLoading.value = false
  })
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除 API Key "${row.keyName}" 吗？删除后对应客户端将无法调用。`, '警告', {
    type: 'warning'
  }).then(() => {
    delApiKey(row.id).then(() => {
      ElMessage.success('删除成功')
      getList()
    })
  })
}

function copyText(text) {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制 API Key 到剪贴板')
  })
}

function formatApiKey(keyPrefix) {
  return keyPrefix ? `${keyPrefix}••••••••` : '已隐藏'
}

function keyStatus(row) {
  if (row.status !== '0') return '停用'
  if (row.expireTime && new Date(row.expireTime.replace(' ', 'T')).getTime() <= Date.now()) return '已过期'
  return '正常'
}

function formatAllowedWorkflows(value) {
  if (!value) return '不限'
  try {
    const workflows = JSON.parse(value)
    return workflows.length ? workflows.join('、') : '无'
  } catch (error) {
    return value
  }
}

onMounted(getList)
</script>

<style lang="scss" scoped>
:global(.api-key-dialog.polaris-glass-dialog.el-dialog) {
  background: var(--el-bg-color-overlay) !important;
  border-color: var(--el-border-color) !important;
}

:global(.api-key-dialog .el-dialog__title) {
  color: var(--el-text-color-primary) !important;
}

:global(.api-key-dialog.el-dialog .el-dialog__footer .dialog-footer .el-button.el-button--default:not(.el-button--primary):not(.el-button--success):not(.el-button--warning):not(.el-button--danger)) {
  color: var(--el-text-color-primary) !important;
  background: var(--el-bg-color-overlay) !important;
  border-color: var(--el-border-color) !important;
}

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

.key-name-text {
  font-weight: 600;
  color: #0f172a;

  .dark & {
    color: #f1f5f9;
  }
}

.api-key-box {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.api-key-badge {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 8px;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
  color: #4338ca !important;
  background: rgba(79, 70, 229, 0.08) !important;
  border: 1px solid rgba(79, 70, 229, 0.2) !important;
  letter-spacing: 0.5px;

  .dark & {
    color: #a5b4fc !important;
    background: rgba(99, 102, 241, 0.15) !important;
    border-color: rgba(99, 102, 241, 0.35) !important;
  }
}

.created-key-box {
  margin-top: 20px;
}

.permission-list {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.copy-btn {
  font-size: 16px;
  color: #4f46e5 !important;
  padding: 4px;
  border-radius: 6px;

  &:hover {
    background: rgba(79, 70, 229, 0.1);
  }

  .dark & {
    color: #818cf8 !important;
    &:hover {
      background: rgba(129, 140, 248, 0.15);
    }
  }
}

.rate-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  background: rgba(0, 0, 0, 0.04);

  .dark & {
    color: #94a3b8;
    background: rgba(255, 255, 255, 0.06);
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

  &.opt-del {
    color: #ef4444 !important;
    &:hover {
      background: rgba(239, 68, 68, 0.1);
    }
  }

  .dark & {
    &.opt-del {
      color: #f87171 !important;
      &:hover {
        background: rgba(248, 113, 113, 0.15);
      }
    }
  }
}
</style>
