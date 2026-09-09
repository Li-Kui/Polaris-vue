<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <div v-show="showSearch" class="polaris-filter-card">
        <el-form :model="queryParams" inline label-width="80px" class="polaris-filter-form">
          <el-form-item label="连接名称">
            <el-input v-model="queryParams.dsName" clearable placeholder="搜索数据源连接" style="width: 240px" @keyup.enter="getList" />
          </el-form-item>
          <el-form-item label="数据库类型">
            <el-select v-model="queryParams.dsType" clearable placeholder="全部类型" style="width: 200px">
              <el-option label="MySQL" value="MYSQL" />
              <el-option label="PostgreSQL" value="POSTGRESQL" />
              <el-option label="SQL Server" value="SQLSERVER" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="getList">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="polaris-table-card">
        <div class="polaris-action-row">
          <el-button type="primary" icon="Plus" @click="handleAdd">新建数据库连接</el-button>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
        </div>

        <el-table v-loading="loading" :data="datasources" class="polaris-el-table">
          <el-table-column prop="dsName" label="连接名称" min-width="160">
            <template #default="{row}"><strong>{{ row.dsName }}</strong></template>
          </el-table-column>
          <el-table-column prop="dsType" label="类型" width="125">
            <template #default="{row}"><el-tag effect="plain">{{ typeLabel(row.dsType) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="连接目标" min-width="230" show-overflow-tooltip>
            <template #default="{row}"><code>{{ row.host }}:{{ row.port }}/{{ row.databaseName }}</code></template>
          </el-table-column>
          <el-table-column prop="username" label="用户名" min-width="130" show-overflow-tooltip />
          <el-table-column label="当前版本" width="100" align="center">
            <template #default="{row}">v{{ row.configVersion || '-' }}</template>
          </el-table-column>
          <el-table-column label="验证状态" width="135" align="center">
            <template #default="{row}">
              <el-tag :type="row.verificationStatus === 'AVAILABLE' ? 'success' : 'warning'">
                {{ row.verificationStatus === 'AVAILABLE' ? '已验证' : '待验证' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" align="center" fixed="right">
            <template #default="{row}">
              <el-tooltip content="测试连接"><el-button link icon="Connection" @click="handleTest(row)" /></el-tooltip>
              <el-tooltip content="修改连接"><el-button link icon="Edit" @click="handleUpdate(row)" /></el-tooltip>
              <el-tooltip content="删除连接"><el-button link type="danger" icon="Delete" @click="handleDelete(row)" /></el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="open" :title="title" width="680px" append-to-body destroy-on-close>
      <DatasourceForm ref="datasourceFormRef" :form="form" :editing="!!form.id" />
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">验证并保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import DatasourceForm from '@/components/platform/DatasourceForm.vue'
import {
  addDatasource,
  delDatasource,
  getDatasource,
  getDatasourceUsages,
  listDatasource,
  testSavedDatasource,
  updateDatasource
} from '@/api/platform/datasource'

const loading = ref(false)
const showSearch = ref(true)
const datasources = ref([])
const open = ref(false)
const title = ref('')
const submitLoading = ref(false)
const datasourceFormRef = ref(null)
const queryParams = reactive({dsName: '', dsType: ''})
const form = reactive(emptyForm())

function emptyForm() {
  return {
    id: undefined,
    dsName: '',
    dsType: 'MYSQL',
    host: '',
    port: 3306,
    databaseName: '',
    username: '',
    credentialAction: 'REPLACE',
    credential: {password: ''},
    passwordConfigured: false,
    sslEnabled: false,
    connectTimeoutSeconds: 5,
    queryTimeoutSeconds: 10,
    status: '0',
    remark: ''
  }
}

async function getList() {
  loading.value = true
  try {
    const response = await listDatasource(queryParams)
    datasources.value = response.data?.rows || []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryParams.dsName = ''
  queryParams.dsType = ''
  getList()
}

function handleAdd() {
  Object.assign(form, emptyForm())
  title.value = '新建数据库连接'
  open.value = true
}

async function handleUpdate(row) {
  const response = await getDatasource(row.id)
  Object.assign(form, emptyForm(), response.data, {
    credentialAction: 'KEEP',
    credential: {password: ''}
  })
  title.value = '修改数据库连接'
  open.value = true
}

async function handleTest(row) {
  const message = ElMessage.info({message: '正在测试数据库连接…', duration: 0})
  try {
    const response = await testSavedDatasource(row.id)
    const result = response.data
    if (result?.success) {
      ElMessage.success(`连接成功，耗时 ${result.responseTimeMs}ms`)
    } else {
      ElMessage.error(result?.errorMessage || '连接失败')
    }
  } finally {
    message.close()
  }
}

async function submitForm() {
  try {
    await datasourceFormRef.value.validate()
  } catch (error) {
    return
  }
  submitLoading.value = true
  try {
    const payload = datasourceFormRef.value.buildPayload()
    await (form.id ? updateDatasource(payload) : addDatasource(payload))
    ElMessage.success('连接验证通过并已保存')
    open.value = false
    await getList()
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row) {
  const response = await getDatasourceUsages(row.id)
  const count = Number(response.data?.activeBindingCount || 0)
  if (count > 0) {
    ElMessage.warning(`该连接正在被 ${count} 个工作流绑定使用，请先解除绑定`)
    return
  }
  await ElMessageBox.confirm(
    `确定删除数据库连接“${row.dsName}”吗？其历史连接版本也会一起删除。`,
    '删除连接',
    {type: 'warning'}
  )
  await delDatasource(row.id)
  ElMessage.success('删除成功')
  await getList()
}

function typeLabel(type) {
  return {MYSQL: 'MySQL', POSTGRESQL: 'PostgreSQL', SQLSERVER: 'SQL Server'}[type] || type
}

onMounted(getList)
</script>

<style scoped lang="scss">
.app-container.no-sidebar-manage-wrap {
  padding: 16px !important;
}

.content-inner {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

code {
  color: var(--el-text-color-regular);
  font-size: 12px;
}
</style>
