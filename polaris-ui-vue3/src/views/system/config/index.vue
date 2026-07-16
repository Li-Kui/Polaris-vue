<template>
   <div class="app-container no-sidebar-manage-wrap">
      <div class="content-inner">
         <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px" class="polaris-filter-card polaris-filter-form">
            <el-form-item label="参数名称" prop="configName">
               <el-input
                  v-model="queryParams.configName"
                  placeholder="请输入参数名称"
                  clearable
                  style="width: 240px"
                  @keyup.enter="handleQuery"
               />
            </el-form-item>
            <el-form-item label="参数键名" prop="configKey">
               <el-input
                  v-model="queryParams.configKey"
                  placeholder="请输入参数键名"
                  clearable
                  style="width: 240px"
                  @keyup.enter="handleQuery"
               />
            </el-form-item>
            <el-form-item label="系统内置" prop="configType">
               <el-select v-model="queryParams.configType" placeholder="系统内置" clearable style="width: 240px">
                  <el-option
                     v-for="dict in sys_yes_no"
                     :key="dict.value"
                     :label="dict.label"
                     :value="dict.value"
                  />
               </el-select>
            </el-form-item>
            <el-form-item label="创建时间" style="width: 308px;">
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
                     type="primary"
                     icon="Plus"
                     @click="handleAdd"
                     v-hasPermi="['system:config:add']"
                     class="action-btn-primary"
                  >新增</el-button>
                  <el-button
                     type="success"
                     icon="Edit"
                     :disabled="single"
                     @click="handleUpdate"
                     v-hasPermi="['system:config:edit']"
                     class="action-btn-secondary"
                  >修改</el-button>
                  <el-button
                     type="danger"
                     icon="Delete"
                     :disabled="multiple"
                     @click="handleDelete"
                     v-hasPermi="['system:config:remove']"
                     class="action-btn-secondary"
                  >删除</el-button>
                  <el-button
                     type="warning"
                     icon="Download"
                     @click="handleExport"
                     v-hasPermi="['system:config:export']"
                     class="action-btn-secondary"
                  >导出</el-button>
                  <el-button
                     type="danger"
                     icon="Refresh"
                     @click="handleRefreshCache"
                     v-hasPermi="['system:config:remove']"
                     class="action-btn-secondary"
                  >刷新缓存</el-button>
               </div>
               <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
            </div>

            <el-table v-loading="loading" :data="configList" @selection-change="handleSelectionChange" class="polaris-el-table">
               <el-table-column type="selection" width="55" align="center" />
               <el-table-column label="参数主键" align="center" prop="configId" width="120" />
               <el-table-column label="参数名称" align="center" prop="configName" :show-overflow-tooltip="true" min-width="150" />
               <el-table-column label="参数键名" align="center" prop="configKey" :show-overflow-tooltip="true" min-width="180" />
               <el-table-column label="参数键值" align="center" prop="configValue" :show-overflow-tooltip="true" min-width="180" />
               <el-table-column label="系统内置" align="center" prop="configType" width="120">
                  <template #default="scope">
                     <span :class="['polaris-badge-neon', scope.row.configType === 'Y' ? 'neon-yes' : 'neon-no']">
                        {{ scope.row.configType === 'Y' ? '是' : '否' }}
                     </span>
                  </template>
               </el-table-column>
               <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" min-width="150" />
               <el-table-column label="创建时间" align="center" prop="createTime" width="180">
                  <template #default="scope">
                     <span>{{ parseTime(scope.row.createTime) }}</span>
                  </template>
               </el-table-column>
               <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
                  <template #default="scope">
                     <el-tooltip content="修改" placement="top">
                        <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:config:edit']"></el-button>
                     </el-tooltip>
                     <el-tooltip content="删除" placement="top">
                        <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:config:remove']"></el-button>
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

      <!-- 添加或修改参数配置对话框 -->
      <el-dialog :title="title" v-model="open" width="500px" append-to-body class="polaris-glass-dialog">
         <el-form ref="configRef" :model="form" :rules="rules" label-width="80px">
            <el-form-item label="参数名称" prop="configName">
               <el-input v-model="form.configName" placeholder="请输入参数名称" />
            </el-form-item>
            <el-form-item label="参数键名" prop="configKey">
               <el-input v-model="form.configKey" placeholder="请输入参数键名" />
            </el-form-item>
            <el-form-item label="参数键值" prop="configValue">
               <el-input v-model="form.configValue" type="textarea" placeholder="请输入参数键值" />
            </el-form-item>
            <el-form-item label="系统内置" prop="configType">
               <el-radio-group v-model="form.configType">
                  <el-radio
                     v-for="dict in sys_yes_no"
                     :key="dict.value"
                     :value="dict.value"
                  >{{ dict.label }}</el-radio>
               </el-radio-group>
            </el-form-item>
            <el-form-item label="备注" prop="remark">
               <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitForm">确 定</el-button>
               <el-button @click="cancel">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="Config">
import {addConfig, delConfig, getConfig, listConfig, refreshCache, updateConfig} from "@/api/system/config"

const { proxy } = getCurrentInstance()
const { sys_yes_no } = useDict("sys_yes_no")

const configList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const dateRange = ref([])

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    configName: undefined,
    configKey: undefined,
    configType: undefined
  },
  rules: {
    configName: [{ required: true, message: "参数名称不能为空", trigger: "blur" }],
    configKey: [{ required: true, message: "参数键名不能为空", trigger: "blur" }],
    configValue: [{ required: true, message: "参数键值不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询参数列表 */
function getList() {
  loading.value = true
  listConfig(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    configList.value = response.rows
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
    configId: undefined,
    configName: undefined,
    configKey: undefined,
    configValue: undefined,
    configType: "Y",
    remark: undefined
  }
  proxy.resetForm("configRef")
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

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.configId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加参数"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const configId = row.configId || ids.value
  getConfig(configId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改参数"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["configRef"].validate(valid => {
    if (valid) {
      if (form.value.configId != undefined) {
        updateConfig(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addConfig(form.value).then(response => {
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
  const configIds = row.configId || ids.value
  proxy.$modal.confirm('是否确认删除参数编号为"' + configIds + '"的数据项？').then(function () {
    return delConfig(configIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("system/config/export", {
    ...queryParams.value
  }, `config_${new Date().getTime()}.xlsx`)
}

/** 刷新缓存按钮操作 */
function handleRefreshCache() {
  refreshCache().then(() => {
    proxy.$modal.msgSuccess("刷新缓存成功")
  })
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
/* 针对北辰参数设置表格的公共组件覆盖（全局非 scoped，但仅对 .polaris-el-table 生效以起隔离保护作用） */
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

  /* 方案二：霓虹发光双态胶囊（亮色模式） */
  .polaris-badge-neon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 4px 12px;
    font-size: 12px;
    font-weight: 800;
    border-radius: 20px;
    line-height: 1.2;
    transition: all 0.3s ease;
    border: 1px solid transparent;

    /* 系统内置为“是” ── 亮蓝色霓虹发光效果 */
    &.neon-yes {
      background-color: rgba(6, 182, 212, 0.06) !important;
      color: #0891b2 !important;
      border-color: rgba(6, 182, 212, 0.25) !important;
      /* 亮色模式下的柔和外发光 */
      box-shadow: 0 0 8px rgba(6, 182, 212, 0.15), inset 0 0 4px rgba(6, 182, 212, 0.05) !important;
      text-shadow: 0 0 3px rgba(6, 182, 212, 0.1);
    }

    /* 系统内置为“否” ── 静谧磨砂无光 */
    &.neon-no {
      background-color: rgba(148, 163, 184, 0.06) !important;
      color: #64748b !important;
      border-color: rgba(148, 163, 184, 0.12) !important;
    }
  }
}

/* 暗黑模式样式覆盖 */
.dark {
  .polaris-el-table {
    .polaris-badge-neon {
      &.neon-yes {
        background-color: rgba(6, 182, 212, 0.12) !important;
        color: #22d3ee !important; // 更高饱和的冰蓝色
        border-color: rgba(34, 211, 238, 0.35) !important;
        /* 暗黑模式下极具未来感的霓虹外发光与内发光叠合 */
        box-shadow: 0 0 12px rgba(34, 211, 238, 0.35), inset 0 0 6px rgba(34, 211, 238, 0.15) !important;
        text-shadow: 0 0 6px rgba(34, 211, 238, 0.4);
      }

      &.neon-no {
        background-color: rgba(255, 255, 255, 0.03) !important;
        color: #94a3b8 !important;
        border-color: rgba(255, 255, 255, 0.06) !important;
      }
    }
  }
}
</style>
