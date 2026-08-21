<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <div class="polaris-table-card">
        <div class="polaris-action-row">
          <div class="actions-left">
            <el-button class="action-btn-primary" type="primary" icon="Plus" @click="handleAdd">添加租户成员</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="userList" class="polaris-el-table">
          <el-table-column prop="username" label="用户名" min-width="140">
            <template #default="scope">
              <span class="username-text">{{ scope.row.username }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="nickname" label="昵称" min-width="130" />
          <el-table-column prop="role" label="角色身份" align="center" width="130">
            <template #default="scope">
              <span :class="['role-badge', scope.row.role === 'admin' ? 'is-admin' : 'is-member']">
                {{ scope.row.role === 'admin' ? '租户管理员' : '普通成员' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column prop="phone" label="手机号码" width="140" />
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
          <el-table-column prop="lastLoginTime" label="最后登录时间" align="center" width="170" />
          <el-table-column label="操作" align="center" width="140" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-tooltip content="修改成员" placement="top">
                <el-button link icon="Edit" @click="handleUpdate(scope.row)" class="table-opt-btn opt-edit" />
              </el-tooltip>
              <el-tooltip content="移除成员" placement="top">
                <el-button link icon="Delete" @click="handleDelete(scope.row)" class="table-opt-btn opt-del" />
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 弹窗 -->
    <el-dialog :title="title" v-model="open" width="560px" append-to-body class="polaris-glass-dialog">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="登录用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入初始密码" show-password />
        </el-form-item>
        <el-form-item label="用户昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="例如: 张三" />
        </el-form-item>
        <el-form-item label="角色分配" prop="role">
          <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%;">
            <el-option label="租户管理员 (全量管理权限)" value="admin" />
            <el-option label="普通成员 (调用与体验权限)" value="member" />
          </el-select>
        </el-form-item>
        <el-form-item label="电子邮箱" prop="email">
          <el-input v-model="form.email" placeholder="user@example.com" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="手机号码" />
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
import {addTenantUser, delTenantUser, listTenantUser, updateTenantUser} from '@/api/platform/user'

const loading = ref(false)
const userList = ref([])
const open = ref(false)
const title = ref('')
const submitLoading = ref(false)
const formRef = ref(null)

const form = reactive({
  id: undefined,
  username: '',
  password: '',
  nickname: '',
  role: 'member',
  email: '',
  phone: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }]
}

function getList() {
  loading.value = true
  listTenantUser().then(res => {
    userList.value = res.rows || []
    loading.value = false
  })
}

function handleAdd() {
  Object.assign(form, {
    id: undefined,
    username: '',
    password: 'password123',
    nickname: '',
    role: 'member',
    email: '',
    phone: ''
  })
  title.value = '添加租户成员'
  open.value = true
}

function handleUpdate(row) {
  Object.assign(form, row)
  title.value = '修改租户成员'
  open.value = true
}

function submitForm() {
  formRef.value.validate(valid => {
    if (valid) {
      submitLoading.value = true
      const action = form.id ? updateTenantUser(form) : addTenantUser(form)
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
  ElMessageBox.confirm(`确定移除成员 "${row.username}" 吗？`, '警告', { type: 'warning' }).then(() => {
    delTenantUser(row.id).then(() => {
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

.username-text {
  font-weight: 600;
  color: #0f172a;

  .dark & {
    color: #f1f5f9;
  }
}

.role-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;

  &.is-admin {
    color: #7c3aed !important;
    background: rgba(124, 58, 237, 0.1) !important;
    border: 1px solid rgba(124, 58, 237, 0.25) !important;
  }

  &.is-member {
    color: #0284c7 !important;
    background: rgba(2, 132, 199, 0.1) !important;
    border: 1px solid rgba(2, 132, 199, 0.25) !important;
  }

  .dark & {
    &.is-admin {
      color: #c084fc !important;
      background: rgba(192, 132, 252, 0.15) !important;
      border-color: rgba(192, 132, 252, 0.35) !important;
    }
    &.is-member {
      color: #38bdf8 !important;
      background: rgba(56, 189, 248, 0.15) !important;
      border-color: rgba(56, 189, 248, 0.35) !important;
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
