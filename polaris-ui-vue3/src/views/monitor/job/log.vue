<template>
   <div class="app-container no-sidebar-manage-wrap">
      <div class="content-inner">
         <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px" class="polaris-filter-card polaris-filter-form">
            <el-form-item label="任务名称" prop="jobName">
               <el-input
                  v-model="queryParams.jobName"
                  placeholder="请输入任务名称"
                  clearable
                  style="width: 240px"
                  @keyup.enter="handleQuery"
               />
            </el-form-item>
            <el-form-item label="任务组名" prop="jobGroup">
               <el-select
                  v-model="queryParams.jobGroup"
                  placeholder="请选择任务组名"
                  clearable
                  style="width: 240px"
               >
                  <el-option
                     v-for="dict in sys_job_group"
                     :key="dict.value"
                     :label="dict.label"
                     :value="dict.value"
                  />
               </el-select>
            </el-form-item>
            <el-form-item label="执行状态" prop="status">
               <el-select
                  v-model="queryParams.status"
                  placeholder="请选择执行状态"
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
            <el-form-item label="执行时间" style="width: 308px">
               <el-date-picker
                  v-model="dateRange"
                  value-format="YYYY-MM-DD"
                  type="daterange"
                  range-separator="-"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
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
                     icon="Delete"
                     :disabled="multiple"
                     @click="handleDelete"
                     v-hasPermi="['monitor:job:remove']"
                     class="action-btn-danger"
                  >删除</el-button>
                  <el-button
                     icon="Delete"
                     @click="handleClean"
                     v-hasPermi="['monitor:job:remove']"
                     class="action-btn-danger"
                  >清空</el-button>
                  <el-button
                     icon="Download"
                     @click="handleExport"
                     v-hasPermi="['monitor:job:export']"
                     class="action-btn-secondary"
                  >导出</el-button>
                  <el-button 
                     icon="Close"
                     @click="handleClose"
                     class="action-btn-secondary"
                  >关闭</el-button>
               </div>
               <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
            </div>

            <el-table v-loading="loading" :data="jobLogList" @selection-change="handleSelectionChange" class="polaris-el-table">
               <el-table-column type="selection" width="55" align="center" />
               <el-table-column label="日志编号" width="100" align="center" prop="jobLogId" />
               <el-table-column label="任务名称" align="center" prop="jobName" :show-overflow-tooltip="true" min-width="150" />
               <el-table-column label="任务组名" align="center" prop="jobGroup" :show-overflow-tooltip="true" min-width="120">
                  <template #default="scope">
                     <dict-tag :options="sys_job_group" :value="scope.row.jobGroup" />
                  </template>
               </el-table-column>
               <el-table-column label="调用目标字符串" align="center" prop="invokeTarget" :show-overflow-tooltip="true" min-width="200" />
               <el-table-column label="日志信息" align="center" prop="jobMessage" :show-overflow-tooltip="true" min-width="150" />
               <el-table-column label="执行状态" align="center" prop="status" width="100">
                  <template #default="scope">
                     <div class="status-cell">
                        <span :class="['pulse-light-ripple', scope.row.status === '0' ? 'pulse-active' : 'pulse-error']"></span>
                        <span class="status-label" :class="scope.row.status === '0' ? 'text-active' : 'text-error'">{{ scope.row.status === '0' ? '正常' : '失败' }}</span>
                     </div>
                  </template>
               </el-table-column>
               <el-table-column label="执行时间" align="center" prop="createTime" width="180">
                  <template #default="scope">
                     <span>{{ parseTime(scope.row.createTime) }}</span>
                  </template>
               </el-table-column>
               <el-table-column label="操作" align="center" width="100" class-name="small-padding fixed-width">
                  <template #default="scope">
                     <el-tooltip content="详细" placement="top">
                        <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['monitor:job:query']"></el-button>
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
   </div>

   <!-- 调度日志详细 -->
   <job-detail v-model:visible="open" :row="form" type="log" />
</template>

<script setup name="JobLog">
import JobDetail from './detail'
import {getJob} from "@/api/monitor/job"
import {cleanJobLog, delJobLog, listJobLog} from "@/api/monitor/jobLog"

const { proxy } = getCurrentInstance()
const { sys_common_status, sys_job_group } = useDict("sys_common_status", "sys_job_group")

const jobLogList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const dateRange = ref([])
const route = useRoute()

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    dictName: undefined,
    dictType: undefined,
    status: undefined
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询调度日志列表 */
function getList() {
  loading.value = true
  listJobLog(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    jobLogList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

// 返回按钮
function handleClose() {
  const obj = { path: "/monitor/job" }
  proxy.$tab.closeOpenPage(obj)
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
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.jobLogId)
  multiple.value = !selection.length
}

/** 详细按钮操作 */
function handleView(row) {
  open.value = true
  form.value = row
}

/** 删除按钮操作 */
function handleDelete(row) {
  proxy.$modal.confirm('是否确认删除调度日志编号为"' + ids.value + '"的数据项?').then(function () {
    return delJobLog(ids.value)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 清空按钮操作 */
function handleClean() {
  proxy.$modal.confirm("是否确认清空所有调度日志数据项?").then(function () {
    return cleanJobLog()
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("清空成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("monitor/jobLog/export", {
    ...queryParams.value,
  }, `job_log_${new Date().getTime()}.xlsx`)
}

(() => {
  const jobId = route.params && route.params.jobId
  if (jobId !== undefined && jobId != 0) {
    getJob(jobId).then(response => {
      queryParams.value.jobName = response.data.jobName
      queryParams.value.jobGroup = response.data.jobGroup
      getList()
    })
  } else {
    getList()
  }
})()
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
/* 针对北辰调度日志表格的公共组件覆盖（全局非 scoped，但仅对 .polaris-el-table 生效以起隔离保护作用） */
.polaris-el-table {
  /* 强行隐藏表格核心容器 of x 溢出 */
  .el-table__inner-wrapper,
  .el-table__body-wrapper,
  .el-scrollbar__wrap {
    overflow-x: hidden !important;
  }

  /* 彻底屏蔽表格内部可能生成的横向滚动条组件，防范滚动条闪化与左右滑动 */
  .el-scrollbar__bar.is-horizontal {
    display: none !important;
  }

  /* 调整行高，加大行内间距，使行高看起来更加舒适美观 */
  .el-table__row {
    td.el-table__cell {
      padding: 12px 0 !important;
    }
  }

  /* 解决表格内 el-tag (如任务组名) 在亮色模式下白底白字看不清的问题，统一重构为高对比度极光微磨砂徽章 */
  .el-tag {
    border-radius: 6px !important;
    font-weight: 700 !important;
    padding: 2px 8px !important;
    height: auto !important;
    line-height: 1.3 !important;
    
    /* 默认/primary 类型的 tag */
    &.el-tag--primary, & {
      background-color: rgba(79, 70, 229, 0.08) !important;
      border: 1px solid rgba(79, 70, 229, 0.15) !important;
      color: #4f46e5 !important;
      
      .dark & {
        background-color: rgba(56, 189, 248, 0.1) !important;
        border-color: rgba(56, 189, 248, 0.2) !important;
        color: #38bdf8 !important;
      }
    }
    
    /* success 类型的 tag */
    &.el-tag--success {
      background-color: rgba(16, 185, 129, 0.08) !important;
      border: 1px solid rgba(16, 185, 129, 0.15) !important;
      color: #10b981 !important;
      
      .dark & {
        background-color: rgba(52, 211, 153, 0.1) !important;
        border-color: rgba(52, 211, 153, 0.2) !important;
        color: #34d399 !important;
      }
    }
    
    /* info 类型的 tag */
    &.el-tag--info {
      background-color: rgba(100, 116, 139, 0.08) !important;
      border: 1px solid rgba(100, 116, 139, 0.15) !important;
      color: #64748b !important;
      
      .dark & {
        background-color: rgba(148, 163, 184, 0.1) !important;
        border-color: rgba(148, 163, 184, 0.2) !important;
        color: #94a3b8 !important;
      }
    }
    
    /* warning 类型的 tag */
    &.el-tag--warning {
      background-color: rgba(245, 158, 11, 0.08) !important;
      border: 1px solid rgba(245, 158, 11, 0.15) !important;
      color: #f59e0b !important;
      
      .dark & {
        background-color: rgba(251, 191, 36, 0.1) !important;
        border-color: rgba(251, 191, 36, 0.2) !important;
        color: #fbbf24 !important;
      }
    }
    
    /* danger 类型的 tag */
    &.el-tag--danger {
      background-color: rgba(239, 68, 68, 0.08) !important;
      border: 1px solid rgba(239, 68, 68, 0.15) !important;
      color: #ef4444 !important;
      
      .dark & {
        background-color: rgba(248, 113, 113, 0.1) !important;
        border-color: rgba(248, 113, 113, 0.2) !important;
        color: #f87171 !important;
      }
    }
  }
}
</style>
