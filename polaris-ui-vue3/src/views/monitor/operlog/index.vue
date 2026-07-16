<template>
   <div class="app-container no-sidebar-manage-wrap">
      <div class="content-inner">
         <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px" class="polaris-filter-card polaris-filter-form">
            <el-form-item label="操作地址" prop="operIp">
               <el-input
                  v-model="queryParams.operIp"
                  placeholder="请输入操作地址"
                  clearable
                  style="width: 240px;"
                  @keyup.enter="handleQuery"
               />
            </el-form-item>
            <el-form-item label="系统模块" prop="title">
               <el-input
                  v-model="queryParams.title"
                  placeholder="请输入系统模块"
                  clearable
                  style="width: 240px;"
                  @keyup.enter="handleQuery"
               />
            </el-form-item>
            <el-form-item label="操作人员" prop="operName">
               <el-input
                  v-model="queryParams.operName"
                  placeholder="请输入操作人员"
                  clearable
                  style="width: 240px;"
                  @keyup.enter="handleQuery"
               />
            </el-form-item>
            <el-form-item label="类型" prop="businessType">
               <el-select
                  v-model="queryParams.businessType"
                  placeholder="操作类型"
                  clearable
                  style="width: 240px"
               >
                  <el-option
                     v-for="dict in sys_oper_type"
                     :key="dict.value"
                     :label="dict.label"
                     :value="dict.value"
                  />
               </el-select>
            </el-form-item>
            <el-form-item label="状态" prop="status">
               <el-select
                  v-model="queryParams.status"
                  placeholder="操作状态"
                  clearable
                  style="width: 240px"
               >
                  <el-option
                     v-for="dict in sys_common_status"
                     :key="dict.value"
                     :label="dict.label"
                     :value="dict.value"
                  />
               </el-select>
            </el-form-item>
            <el-form-item label="操作时间" style="width: 308px">
               <el-date-picker
                  v-model="dateRange"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  type="daterange"
                  range-separator="-"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
               ></el-date-picker>
            </el-form-item>
            <el-form-item>
               <el-button type="primary" icon="Search" @click="handleQuery" class="polaris-query-btn">搜索</el-button>
               <el-button icon="Refresh" @click="resetQuery" class="polaris-reset-btn">重置</el-button>
            </el-form-item>
         </el-form>

      <div class="polaris-table-card">
         <div class="polaris-action-row">
            <div class="actions-left">
               <el-button
                  type="danger"
                  icon="Delete"
                  :disabled="multiple"
                  @click="handleDelete"
                  v-hasPermi="['monitor:operlog:remove']"
                  class="action-btn-danger"
               >删除</el-button>
               <el-button
                  type="danger"
                  icon="Delete"
                  @click="handleClean"
                  v-hasPermi="['monitor:operlog:remove']"
                  class="action-btn-danger"
               >清空</el-button>
               <el-button
                  type="warning"
                  icon="Download"
                  @click="handleExport"
                  v-hasPermi="['monitor:operlog:export']"
                  class="action-btn-secondary"
               >导出</el-button>
            </div>
            <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
         </div>

         <el-table ref="operlogRef" v-loading="loading" :data="operlogList" @selection-change="handleSelectionChange" :default-sort="defaultSort" @sort-change="handleSortChange" class="polaris-el-table">
            <el-table-column type="selection" width="50" align="center" />
            <el-table-column label="日志编号" align="center" prop="operId" width="100" />
            <el-table-column label="系统模块" align="center" prop="title" :show-overflow-tooltip="true" min-width="120" />
            <el-table-column label="操作类型" align="center" prop="businessType" width="100">
               <template #default="scope">
                  <dict-tag :options="sys_oper_type" :value="scope.row.businessType" />
               </template>
            </el-table-column>
            <el-table-column label="操作人员" align="center" width="110" prop="operName" :show-overflow-tooltip="true" sortable="custom" :sort-orders="['descending', 'ascending']" />
            <el-table-column label="操作地址" align="center" prop="operIp" width="130" :show-overflow-tooltip="true" />
            <el-table-column label="操作状态" align="center" prop="status" width="100">
               <template #default="scope">
                  <div class="status-cell">
                     <span :class="['pulse-light-ripple', scope.row.status === 0 ? 'pulse-active' : 'pulse-error']"></span>
                     <span class="status-label" :class="scope.row.status === 0 ? 'text-active' : 'text-error'">{{ scope.row.status === 0 ? '正常' : '异常' }}</span>
                  </div>
               </template>
            </el-table-column>
            <el-table-column label="操作日期" align="center" prop="operTime" width="180" sortable="custom" :sort-orders="['descending', 'ascending']">
               <template #default="scope">
                  <span>{{ parseTime(scope.row.operTime) }}</span>
               </template>
            </el-table-column>
            <el-table-column label="消耗时间" align="center" prop="costTime" width="110" :show-overflow-tooltip="true" sortable="custom" :sort-orders="['descending', 'ascending']">
               <template #default="scope">
                  <span>{{ scope.row.costTime }}毫秒</span>
               </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="100" class-name="small-padding fixed-width">
               <template #default="scope">
                  <el-tooltip content="详细" placement="top">
                     <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['monitor:operlog:query']"></el-button>
                  </el-tooltip>
               </template>
            </el-table-column>
         </el-table>

         <pagination
            v-show="total > 0"
            :total="total"
            v-model:page="queryParams.pageNum"
            v-model:limit="queryParams.pageSize"
            @pagination="getList"
         />
      </div>
   </div>
   <operlog-detail v-model:visible="detailVisible" :row="detailRow" />
</div>
</template>

<script setup name="Operlog">
import OperlogDetail from './detail'
import {cleanOperlog, delOperlog, list} from "@/api/monitor/operlog"

const { proxy } = getCurrentInstance()
const { sys_oper_type, sys_common_status } = useDict("sys_oper_type", "sys_common_status")

const operlogList = ref([])
const detailVisible = ref(false)
const loading = ref(true)
const detailRow = ref({})
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const dateRange = ref([])
const defaultSort = ref({ prop: "operTime", order: "descending" })

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    operIp: undefined,
    title: undefined,
    operName: undefined,
    businessType: undefined,
    status: undefined
  }
})

const { queryParams, form } = toRefs(data)

/** 查询登录日志 */
function getList() {
  loading.value = true
  list(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    operlogList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = []
  proxy.resetForm("queryRef")
  queryParams.value.pageNum = 1
  proxy.$refs["operlogRef"].sort(defaultSort.value.prop, defaultSort.value.order)
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.operId)
  multiple.value = !selection.length
}

/** 排序触发事件 */
function handleSortChange(column, prop, order) {
  queryParams.value.orderByColumn = column.prop
  queryParams.value.isAsc = column.order
  getList()
}

/** 详细按钮操作 */
function handleDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

/** 删除按钮操作 */
function handleDelete(row) {
  const operIds = row.operId || ids.value
  proxy.$modal.confirm('是否确认删除日志编号为"' + operIds + '"的数据项?').then(function () {
    return delOperlog(operIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 清空按钮操作 */
function handleClean() {
  proxy.$modal.confirm("是否确认清空所有操作日志数据项?").then(function () {
    return cleanOperlog()
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("清空成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("monitor/operlog/export",{
    ...queryParams.value,
  }, `config_${new Date().getTime()}.xlsx`)
}

getList()
</script>

<style lang="scss" scoped>
/* 覆盖全局 .app-container 的 padding: 20px */
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
</style>

<style lang="scss">
/* 针对北辰操作日志表格的公共组件覆盖（全局非 scoped，但仅对 .polaris-el-table 生效以起隔离保护作用） */
.polaris-el-table {
  /* 强行隐藏表格核心容器的横向溢出，在完全保留原有平移+放大悬浮动效的同时，彻底消除横向滚动条与左右滑动 */
  .el-table__inner-wrapper,
  .el-table__body-wrapper,
  .el-scrollbar__wrap {
    overflow-x: hidden !important;
  }

  /* 彻底屏蔽表格内部可能生成的横向滚动条组件，防范一切滚动条闪烁与左右滑动 */
  .el-scrollbar__bar.is-horizontal {
    display: none !important;
  }

  /* 调整行高，加大行内间距，使行高看起来更加舒适美观 */
  .el-table__row {
    td.el-table__cell {
      padding: 12px 0 !important;
    }
  }
}
</style>
