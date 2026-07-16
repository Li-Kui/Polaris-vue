<template>
   <div class="app-container no-sidebar-manage-wrap">
      <div class="content-inner">
         <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" class="polaris-filter-card polaris-filter-form">
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
               <el-select v-model="queryParams.jobGroup" placeholder="请选择任务组名" clearable style="width: 240px">
                  <el-option
                     v-for="dict in sys_job_group"
                     :key="dict.value"
                     :label="dict.label"
                     :value="dict.value"
                  />
               </el-select>
            </el-form-item>
            <el-form-item label="任务状态" prop="status">
               <el-select v-model="queryParams.status" placeholder="请选择任务状态" clearable style="width: 240px">
                  <el-option
                     v-for="dict in sys_job_status"
                     :key="dict.value"
                     :label="dict.label"
                     :value="dict.value"
                  />
               </el-select>
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
                     type="primary"
                     icon="Plus"
                     @click="handleAdd"
                     v-hasPermi="['monitor:job:add']"
                     class="action-btn-primary"
                  >新增</el-button>
                  <el-button
                     icon="Edit"
                     :disabled="single"
                     @click="handleUpdate"
                     v-hasPermi="['monitor:job:edit']"
                     class="action-btn-secondary"
                  >修改</el-button>
                  <el-button
                     type="danger"
                     icon="Delete"
                     :disabled="multiple"
                     @click="handleDelete"
                     v-hasPermi="['monitor:job:remove']"
                     class="action-btn-danger"
                  >删除</el-button>
                  <el-button
                     type="warning"
                     icon="Download"
                     @click="handleExport"
                     v-hasPermi="['monitor:job:export']"
                     class="action-btn-secondary"
                  >导出</el-button>
                  <el-button
                     type="info"
                     icon="Operation"
                     @click="handleJobLog"
                     v-hasPermi="['monitor:job:query']"
                     class="action-btn-secondary"
                  >日志</el-button>
               </div>
               <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
            </div>

            <el-table v-loading="loading" :data="jobList" @selection-change="handleSelectionChange" class="polaris-el-table">
               <el-table-column type="selection" width="55" align="center" />
               <el-table-column label="任务编号" width="100" align="center" prop="jobId" />
               <el-table-column label="任务名称" align="center" :show-overflow-tooltip="true" min-width="150">
                  <template #default="scope">
                     <a class="link-type" style="cursor:pointer" @click="handleView(scope.row)">{{ scope.row.jobName }}</a>
                  </template>
               </el-table-column>
               <el-table-column label="任务组名" align="center" prop="jobGroup" min-width="120">
                  <template #default="scope">
                     <dict-tag :options="sys_job_group" :value="scope.row.jobGroup" />
                  </template>
               </el-table-column>
               <el-table-column label="调用目标字符串" align="center" prop="invokeTarget" :show-overflow-tooltip="true" min-width="200" />
               <el-table-column label="cron执行表达式" align="center" prop="cronExpression" :show-overflow-tooltip="true" min-width="150" />
               <el-table-column label="状态" align="center" width="110">
                  <template #default="scope">
                     <el-switch
                        v-model="scope.row.status"
                        active-value="0"
                        inactive-value="1"
                        active-text="运行"
                        inactive-text="暂停"
                        inline-prompt
                        width="60"
                        @change="handleStatusChange(scope.row)"
                        class="polaris-status-switch"
                     ></el-switch>
                  </template>
               </el-table-column>
               <el-table-column label="操作" align="center" width="180" class-name="small-padding fixed-width">
                  <template #default="scope">
                     <el-tooltip content="修改" placement="top">
                        <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['monitor:job:edit']"></el-button>
                     </el-tooltip>
                     <el-tooltip content="删除" placement="top">
                        <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['monitor:job:remove']"></el-button>
                     </el-tooltip>
                     <el-tooltip content="执行一次" placement="top">
                        <el-button link type="primary" icon="CaretRight" @click="handleRun(scope.row)" v-hasPermi="['monitor:job:changeStatus']"></el-button>
                     </el-tooltip>
                     <el-tooltip content="调度日志" placement="top">
                        <el-button link type="primary" icon="Operation" @click="handleJobLog(scope.row)" v-hasPermi="['monitor:job:query']"></el-button>
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

   <!-- 添加或修改定时任务对话框 -->
   <el-dialog :title="title" v-model="open" width="820px" append-to-body class="polaris-glass-dialog">
         <el-form ref="jobRef" :model="form" :rules="rules" label-width="120px">
            <el-row>
               <el-col :span="12">
                  <el-form-item label="任务名称" prop="jobName">
                     <el-input v-model="form.jobName" placeholder="请输入任务名称" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="任务分组" prop="jobGroup">
                     <el-select v-model="form.jobGroup" placeholder="请选择">
                        <el-option
                           v-for="dict in sys_job_group"
                           :key="dict.value"
                           :label="dict.label"
                           :value="dict.value"
                        ></el-option>
                     </el-select>
                  </el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item prop="invokeTarget">
                     <template #label>
                        <span>
                           调用方法
                           <el-tooltip placement="top">
                              <template #content>
                                 <div>
                                    Bean调用示例：ryTask.ryParams('ry')
                                    <br />Class类调用示例：com.ruoyi.quartz.task.RyTask.ryParams('ry')
                                    <br />参数说明：支持字符串，布尔类型，长整型，浮点型，整型
                                 </div>
                              </template>
                              <el-icon><question-filled /></el-icon>
                           </el-tooltip>
                        </span>
                     </template>
                     <el-input v-model="form.invokeTarget" placeholder="请输入调用目标字符串" />
                  </el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="cron表达式" prop="cronExpression">
                     <el-input v-model="form.cronExpression" placeholder="请输入cron执行表达式">
                        <template #append>
                           <el-button type="primary" @click="handleShowCron">
                              生成表达式
                              <i class="el-icon-time el-icon--right"></i>
                           </el-button>
                        </template>
                     </el-input>
                  </el-form-item>
               </el-col>
               <el-col :span="24" v-if="form.jobId !== undefined">
                  <el-form-item label="状态">
                     <el-radio-group v-model="form.status">
                        <el-radio
                           v-for="dict in sys_job_status"
                           :key="dict.value"
                           :value="dict.value"
                        >{{ dict.label }}</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="执行策略" prop="misfirePolicy">
                     <el-radio-group v-model="form.misfirePolicy">
                        <el-radio value="1">立即执行</el-radio>
                        <el-radio value="2">执行一次</el-radio>
                        <el-radio value="3">放弃执行</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="是否并发" prop="concurrent">
                     <el-radio-group v-model="form.concurrent">
                        <el-radio value="0">允许</el-radio>
                        <el-radio value="1">禁止</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
            </el-row>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitForm">确 定</el-button>
               <el-button @click="cancel">取 消</el-button>
            </div>
         </template>
      </el-dialog>

     <el-dialog title="Cron表达式生成器" v-model="openCron" append-to-body destroy-on-close>
       <crontab ref="crontabRef" @hide="openCron=false" @fill="crontabFill" :expression="expression"></crontab>
     </el-dialog>

      <!-- 任务详细 -->
      <job-detail v-model:visible="openView" :row="form" type="job" />
</template>

<script setup name="Job">
import Crontab from '@/components/Crontab'
import JobDetail from './detail'
import {addJob, changeJobStatus, delJob, getJob, listJob, runJob, updateJob} from "@/api/monitor/job"

const router = useRouter()
const { proxy } = getCurrentInstance()
const { sys_job_group, sys_job_status } = useDict("sys_job_group", "sys_job_status")

const jobList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const openView = ref(false)
const openCron = ref(false)
const expression = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    jobName: undefined,
    jobGroup: undefined,
    status: undefined
  },
  rules: {
    jobName: [{ required: true, message: "任务名称不能为空", trigger: "blur" }],
    invokeTarget: [{ required: true, message: "调用目标字符串不能为空", trigger: "blur" }],
    cronExpression: [{ required: true, message: "cron执行表达式不能为空", trigger: "change" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询定时任务列表 */
function getList() {
  loading.value = true
  listJob(queryParams.value).then(response => {
    jobList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    jobId: undefined,
    jobName: undefined,
    jobGroup: undefined,
    invokeTarget: undefined,
    cronExpression: undefined,
    misfirePolicy: '1',
    concurrent: '1',
    status: "0"
  }
  proxy.resetForm("jobRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.jobId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

// 任务状态修改
function handleStatusChange(row) {
  let text = row.status === "0" ? "启用" : "停用"
  proxy.$modal.confirm('确认要"' + text + '""' + row.jobName + '"任务吗?').then(function () {
    return changeJobStatus(row.jobId, row.status)
  }).then(() => {
    proxy.$modal.msgSuccess(text + "成功")
  }).catch(function () {
    row.status = row.status === "0" ? "1" : "0"
  })
}

/* 立即执行一次 */
function handleRun(row) {
  proxy.$modal.confirm('确认要立即执行一次"' + row.jobName + '"任务吗?').then(function () {
    return runJob(row.jobId, row.jobGroup)
  }).then(() => {
    proxy.$modal.msgSuccess("执行成功")
  }).catch(() => {})
}

/** 任务详细信息 */
function handleView(row) {
  getJob(row.jobId).then(response => {
    form.value = response.data
    openView.value = true
  })
}

/** cron表达式按钮操作 */
function handleShowCron() {
  expression.value = form.value.cronExpression
  openCron.value = true
}

/** 确定后回传值 */
function crontabFill(value) {
  form.value.cronExpression = value
}

/** 任务日志列表查询 */
function handleJobLog(row) {
  const jobId = row.jobId || 0
  router.push('/monitor/job-log/index/' + jobId)
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加任务"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const jobId = row.jobId || ids.value
  getJob(jobId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改任务"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["jobRef"].validate(valid => {
    if (valid) {
      if (form.value.jobId != undefined) {
        updateJob(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addJob(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const jobIds = row.jobId || ids.value
  proxy.$modal.confirm('是否确认删除定时任务编号为"' + jobIds + '"的数据项?').then(function () {
    return delJob(jobIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("monitor/job/export", {
    ...queryParams.value,
  }, `job_${new Date().getTime()}.xlsx`)
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
/* 针对北辰定时任务表格的公共组件覆盖（全局非 scoped，但仅对 .polaris-el-table 生效以起隔离保护作用） */
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

  /* 北辰科技风 Switch 美化 */
  .polaris-status-switch.el-switch {
    height: 24px;
    
    .el-switch__core {
      border-radius: 12px;
      border: 1px solid rgba(0, 0, 0, 0.05);
      background-color: rgba(100, 116, 139, 0.15) !important; /* 暂停状态的磨砂灰 */
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      
      .el-switch__inner {
        padding: 0 8px;
        font-size: 11px;
        font-weight: 700;
        color: #64748b; /* 暂停状态的文本灰色 */
      }
      
      .el-switch__action {
        background-color: #ffffff;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        width: 14px;
        height: 14px;
      }
    }
    
    /* 运行状态（On） */
    &.is-checked {
      .el-switch__core {
        background-color: rgba(16, 185, 129, 0.15) !important; /* 运行状态的冰绿微光背景 */
        border-color: rgba(16, 185, 129, 0.3) !important;
        box-shadow: 0 0 10px rgba(16, 185, 129, 0.2);
        
        .el-switch__inner {
          color: #10b981 !important; /* 运行状态字色 */
          span {
            color: #10b981 !important;
          }
        }
        
        .el-switch__action {
          background-color: #10b981 !important; /* 绿圆钮 */
          box-shadow: 0 0 8px rgba(16, 185, 129, 0.4);
        }
      }
    }
    
    .dark & {
      .el-switch__core {
        background-color: rgba(255, 255, 255, 0.05) !important;
        border-color: rgba(255, 255, 255, 0.08) !important;
        
        .el-switch__inner {
          color: #94a3b8;
        }
      }
      
      &.is-checked {
        .el-switch__core {
          background-color: rgba(52, 211, 153, 0.15) !important;
          border-color: rgba(52, 211, 153, 0.25) !important;
          box-shadow: 0 0 12px rgba(52, 211, 153, 0.25);
          
          .el-switch__inner {
            color: #34d399 !important;
            span {
              color: #34d399 !important;
            }
          }
          
          .el-switch__action {
            background-color: #34d399 !important;
            box-shadow: 0 0 10px rgba(52, 211, 153, 0.5);
          }
        }
      }
    }
  }
}
</style>
