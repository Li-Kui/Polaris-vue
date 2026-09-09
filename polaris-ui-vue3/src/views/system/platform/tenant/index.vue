<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <!-- 搜索过滤器玻璃卡片 -->
      <div class="polaris-filter-card" v-show="showSearch">
        <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="68px" class="polaris-filter-form">
          <el-form-item label="租户名称" prop="tenantName">
            <el-input
              v-model="queryParams.tenantName"
              placeholder="请输入租户名称"
              clearable
              style="width: 240px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="租户编码" prop="tenantCode">
            <el-input
              v-model="queryParams.tenantCode"
              placeholder="请输入租户编码"
              clearable
              style="width: 240px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery" class="polaris-query-btn">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery" class="polaris-reset-btn">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 数据表格玻璃卡片 -->
      <div class="polaris-table-card">
        <div class="polaris-action-row">
          <div class="actions-left">
            <el-button
              type="primary"
              icon="Plus"
              @click="handleAdd"
              v-hasPermi="['system:tenant:add']"
              class="action-btn-primary"
            >新建租户</el-button>
          </div>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </div>

        <el-table v-loading="loading" :data="tenantList" class="polaris-el-table">
          <el-table-column label="租户ID" align="center" prop="tenantId" width="90" />
          <el-table-column label="租户名称" align="center" prop="tenantName" min-width="150" :show-overflow-tooltip="true">
            <template #default="scope">
              <span class="tenant-name-text">{{ scope.row.tenantName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="租户编码" align="center" prop="tenantCode" min-width="140">
            <template #default="scope">
              <span class="tenant-code-badge">{{ scope.row.tenantCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="联系人" align="center" prop="contactName" width="120" />
          <el-table-column label="联系电话" align="center" prop="contactPhone" width="140" />
          <el-table-column label="Token 配额" align="center" min-width="160">
            <template #default="scope">
              <div class="token-quota-badge">
                <span class="used-val">{{ scope.row.usedTokens || 0 }}</span>
                <span class="split">/</span>
                <span class="total-val">{{ scope.row.quotaTokens === -1 ? '无限' : scope.row.quotaTokens }}</span>
              </div>
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
          <el-table-column label="创建时间" align="center" prop="createTime" width="170" />
          <el-table-column label="操作" align="center" width="140" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-tooltip content="修改租户" placement="top">
                <el-button link icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:tenant:edit']" class="table-opt-btn opt-edit" />
              </el-tooltip>
              <el-tooltip content="删除租户" placement="top">
                <el-button link icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:tenant:remove']" class="table-opt-btn opt-del" />
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 添加或修改租户配置对话框 -->
    <el-dialog :title="title" v-model="open" width="580px" append-to-body class="polaris-glass-dialog">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="租户名称" prop="tenantName">
              <el-input v-model="form.tenantName" placeholder="例如: 智能客服业务部" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="租户编码" prop="tenantCode">
              <el-input v-model="form.tenantCode" placeholder="例如: service_dept" :disabled="!!form.tenantId" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactName">
              <el-input v-model="form.contactName" placeholder="联系人姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" placeholder="联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="Token 配额" prop="quotaTokens">
              <el-input-number v-model="form.quotaTokens" :min="-1" style="width: 180px;" />
              <span class="quota-tip">（-1 代表无限配额）</span>
            </el-form-item>
          </el-col>
          <template v-if="!form.tenantId">
            <el-col :span="12">
              <el-form-item label="初始管理员" prop="adminUsername">
                <el-input v-model="form.adminUsername" placeholder="用户名 (如: admin)" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="初始密码" prop="adminPassword">
                <el-input v-model="form.adminPassword" type="password" placeholder="中台登录密码" show-password />
              </el-form-item>
            </el-col>
          </template>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="open = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {addTenant, delTenant, getTenant, listTenant, updateTenant} from '@/api/system/platformTenant'

const loading = ref(false)
const showSearch = ref(true)
const tenantList = ref([])
const open = ref(false)
const title = ref('')
const formRef = ref(null)

const queryParams = reactive({
  tenantName: '',
  tenantCode: ''
})

const form = reactive({
  tenantId: undefined,
  tenantName: '',
  tenantCode: '',
  contactName: '',
  contactPhone: '',
  quotaTokens: -1,
  adminUsername: '',
  adminPassword: ''
})

const rules = {
  tenantName: [{ required: true, message: '租户名称不能为空', trigger: 'blur' }],
  tenantCode: [{ required: true, message: '租户编码不能为空', trigger: 'blur' }],
  adminUsername: [{ required: true, message: '初始管理员账号不能为空', trigger: 'blur' }],
  adminPassword: [{ required: true, message: '初始密码不能为空', trigger: 'blur' }]
}

function getList() {
  loading.value = true
  listTenant(queryParams).then(res => {
    tenantList.value = res.data?.rows || []
    loading.value = false
  })
}

function handleQuery() { getList() }
function resetQuery() {
  queryParams.tenantName = ''
  queryParams.tenantCode = ''
  getList()
}

function handleAdd() {
  Object.assign(form, {
    tenantId: undefined,
    tenantName: '',
    tenantCode: '',
    contactName: '',
    contactPhone: '',
    quotaTokens: -1,
    adminUsername: 'admin',
    adminPassword: 'password123'
  })
  title.value = '新建中台租户'
  open.value = true
}

function handleUpdate(row) {
  getTenant(row.tenantId).then(res => {
    Object.assign(form, res.data)
    title.value = '修改租户'
    open.value = true
  })
}

function submitForm() {
  formRef.value.validate(valid => {
    if (valid) {
      if (form.tenantId) {
        updateTenant(form).then(() => {
          ElMessage.success('修改成功')
          open.value = false
          getList()
        })
      } else {
        addTenant({
          tenant: {
            tenantName: form.tenantName,
            tenantCode: form.tenantCode,
            contactName: form.contactName,
            contactPhone: form.contactPhone,
            quotaTokens: form.quotaTokens
          },
          adminUsername: form.adminUsername,
          adminPassword: form.adminPassword
        }).then(() => {
          ElMessage.success('创建租户成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除租户 "${row.tenantName}" 吗？`, '警告', { type: 'warning' }).then(() => {
    delTenant(row.tenantId).then(() => {
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

.polaris-filter-card {
  margin-bottom: 16px;
}

.tenant-name-text {
  font-weight: 600;
  color: #0f172a;

  .dark & {
    color: #f1f5f9;
  }
}

.tenant-code-badge {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 8px;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 13px;
  font-weight: 700;
  color: #4338ca !important;
  background: rgba(79, 70, 229, 0.1) !important;
  border: 1px solid rgba(79, 70, 229, 0.25) !important;
  letter-spacing: 0.5px;

  .dark & {
    color: #a5b4fc !important;
    background: rgba(99, 102, 241, 0.2) !important;
    border-color: rgba(99, 102, 241, 0.4) !important;
  }
}

.token-quota-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  padding: 3px 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.06);

  .used-val {
    color: #4f46e5;
    font-weight: 700;
  }
  .split {
    color: #94a3b8;
  }
  .total-val {
    color: #334155;
    font-weight: 600;
  }

  .dark & {
    background: rgba(255, 255, 255, 0.06);
    border-color: rgba(255, 255, 255, 0.08);

    .used-val {
      color: #818cf8;
    }
    .total-val {
      color: #cbd5e1;
    }
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
    &:hover {
      background: rgba(79, 70, 229, 0.1);
    }
  }

  &.opt-del {
    color: #ef4444 !important;
    &:hover {
      background: rgba(239, 68, 68, 0.1);
    }
  }

  .dark & {
    &.opt-edit {
      color: #818cf8 !important;
      &:hover {
        background: rgba(129, 140, 248, 0.15);
      }
    }
    &.opt-del {
      color: #f87171 !important;
      &:hover {
        background: rgba(248, 113, 113, 0.15);
      }
    }
  }
}

.quota-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #94a3b8;
}
</style>
