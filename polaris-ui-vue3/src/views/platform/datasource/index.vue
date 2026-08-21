<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <div class="polaris-filter-card" v-show="showSearch">
        <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="80px" class="polaris-filter-form">
          <el-form-item label="数据源名称" prop="dsName">
            <el-input v-model="queryParams.dsName" placeholder="请输入数据源名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="数据库类型" prop="dsType">
            <el-select v-model="queryParams.dsType" placeholder="全部类型" clearable style="width: 200px">
              <el-option label="MySQL" value="MYSQL" />
              <el-option label="PostgreSQL" value="POSTGRESQL" />
              <el-option label="SQL Server" value="SQLSERVER" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery" class="polaris-query-btn">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery" class="polaris-reset-btn">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="polaris-table-card">
        <div class="polaris-action-row">
          <div class="actions-left">
            <el-button class="action-btn-primary" type="primary" icon="Plus" @click="handleAdd">新建数据源</el-button>
          </div>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </div>

        <el-table v-loading="loading" :data="dsList" class="polaris-el-table">
          <el-table-column prop="dsName" label="数据源名称" min-width="150" :show-overflow-tooltip="true">
            <template #default="scope">
              <span class="ds-name-text">{{ scope.row.dsName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="dsType" label="类型" align="center" width="130">
            <template #default="scope">
              <span :class="['db-type-badge', scope.row.dsType ? scope.row.dsType.toLowerCase() : '']">{{ scope.row.dsType }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="jdbcUrl" label="JDBC 连接串" min-width="260" :show-overflow-tooltip="true">
            <template #default="scope">
              <span class="url-text">{{ scope.row.jdbcUrl }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column label="只读受控" align="center" width="100">
            <template #default="scope">
              <span :class="['readonly-badge', scope.row.readOnly ? 'is-read' : 'is-write']">
                {{ scope.row.readOnly ? '只读' : '读写' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="160" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-tooltip content="测试连接" placement="top">
                <el-button link icon="Connection" @click="handleTest(scope.row)" class="table-opt-btn opt-test" />
              </el-tooltip>
              <el-tooltip content="修改配置" placement="top">
                <el-button link icon="Edit" @click="handleUpdate(scope.row)" class="table-opt-btn opt-edit" />
              </el-tooltip>
              <el-tooltip content="删除数据源" placement="top">
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
        <el-form-item label="数据源名称" prop="dsName">
          <el-input v-model="form.dsName" placeholder="例如: 核心业务库 (只读副本)" />
        </el-form-item>
        <el-form-item label="数据库类型" prop="dsType">
          <el-select v-model="form.dsType" placeholder="请选择类型" style="width: 100%;">
            <el-option label="MySQL 5.7 / 8.0+" value="MYSQL" />
            <el-option label="PostgreSQL" value="POSTGRESQL" />
            <el-option label="Microsoft SQL Server" value="SQLSERVER" />
          </el-select>
        </el-form-item>
        <el-form-item label="JDBC URL" prop="jdbcUrl">
          <el-input v-model="form.jdbcUrl" placeholder="jdbc:mysql://host:3306/dbname?useSSL=false" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" placeholder="数据库账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="连接密码" prop="password">
              <el-input v-model="form.password" type="password" placeholder="AES 受控加密" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="受控只读" prop="readOnly">
          <el-switch v-model="form.readOnly" active-text="仅允许 SELECT 查询" />
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
import {addDatasource, delDatasource, listDatasource, testDatasource, updateDatasource} from '@/api/platform/datasource'

const loading = ref(false)
const showSearch = ref(true)
const dsList = ref([])
const open = ref(false)
const title = ref('')
const submitLoading = ref(false)
const formRef = ref(null)

const queryParams = reactive({
  dsName: '',
  dsType: ''
})

const form = reactive({
  id: undefined,
  dsName: '',
  dsType: 'MYSQL',
  jdbcUrl: '',
  username: '',
  password: '',
  readOnly: true,
  queryTimeout: 30
})

const rules = {
  dsName: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  dsType: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  jdbcUrl: [{ required: true, message: '请输入 JDBC 连接串', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }]
}

function getList() {
  loading.value = true
  listDatasource(queryParams).then(res => {
    dsList.value = res.rows || []
    loading.value = false
  })
}

function handleQuery() { getList() }
function resetQuery() {
  queryParams.dsName = ''
  queryParams.dsType = ''
  getList()
}

function handleAdd() {
  Object.assign(form, {
    id: undefined,
    dsName: '',
    dsType: 'MYSQL',
    jdbcUrl: '',
    username: '',
    password: '',
    readOnly: true,
    queryTimeout: 30
  })
  title.value = '新建外部数据源'
  open.value = true
}

function handleUpdate(row) {
  Object.assign(form, row)
  title.value = '修改外部数据源'
  open.value = true
}

function handleTest(row) {
  const loadingMsg = ElMessage.info({ message: '正在测试数据源连通性...', duration: 0 })
  testDatasource(row.id).then(res => {
    loadingMsg.close()
    if (res.data?.success) {
      ElMessage.success(`连接成功！耗时: ${res.data.responseTimeMs}ms`)
    } else {
      ElMessage.error(`连接失败: ${res.data?.errorMessage || '未知错误'}`)
    }
  }).catch(() => {
    loadingMsg.close()
  })
}

function submitForm() {
  formRef.value.validate(valid => {
    if (valid) {
      submitLoading.value = true
      const action = form.id ? updateDatasource(form) : addDatasource(form)
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
  ElMessageBox.confirm(`确定删除数据源 "${row.dsName}" 吗？`, '警告', { type: 'warning' }).then(() => {
    delDatasource(row.id).then(() => {
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

.ds-name-text {
  font-weight: 600;
  color: #0f172a;

  .dark & {
    color: #f1f5f9;
  }
}

.db-type-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 6px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.5px;

  &.mysql {
    color: #0284c7 !important;
    background: rgba(2, 132, 199, 0.1) !important;
    border: 1px solid rgba(2, 132, 199, 0.25) !important;
  }
  &.postgresql {
    color: #4338ca !important;
    background: rgba(67, 56, 202, 0.1) !important;
    border: 1px solid rgba(67, 56, 202, 0.25) !important;
  }
  &.sqlserver {
    color: #d97706 !important;
    background: rgba(217, 119, 6, 0.1) !important;
    border: 1px solid rgba(217, 119, 6, 0.25) !important;
  }

  .dark & {
    &.mysql { color: #38bdf8 !important; background: rgba(56, 189, 248, 0.15) !important; }
    &.postgresql { color: #818cf8 !important; background: rgba(129, 140, 248, 0.15) !important; }
    &.sqlserver { color: #fbbf24 !important; background: rgba(251, 191, 36, 0.15) !important; }
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

.readonly-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;

  &.is-read {
    color: #10b981;
    background: rgba(16, 185, 129, 0.1);
  }
  &.is-write {
    color: #f59e0b;
    background: rgba(245, 158, 11, 0.1);
  }
}

.table-opt-btn {
  font-size: 15px;
  padding: 6px;
  border-radius: 8px;
  transition: all 0.2s ease;

  &.opt-test {
    color: #10b981 !important;
    &:hover { background: rgba(16, 185, 129, 0.1); }
  }
  &.opt-edit {
    color: #4f46e5 !important;
    &:hover { background: rgba(79, 70, 229, 0.1); }
  }
  &.opt-del {
    color: #ef4444 !important;
    &:hover { background: rgba(239, 68, 68, 0.1); }
  }

  .dark & {
    &.opt-test { color: #34d399 !important; }
    &.opt-edit { color: #818cf8 !important; }
    &.opt-del { color: #f87171 !important; }
  }
}
</style>
