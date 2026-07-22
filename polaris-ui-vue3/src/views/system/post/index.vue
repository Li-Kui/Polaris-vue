<template>
   <div class="app-container no-sidebar-manage-wrap">
      <div class="content-inner">
         <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" class="polaris-filter-card polaris-filter-form">
            <el-form-item label="岗位编码" prop="postCode">
               <el-input
                  v-model="queryParams.postCode"
                  placeholder="请输入岗位编码"
                  clearable
                  style="width: 240px"
                  @keyup.enter="handleQuery"
               />
            </el-form-item>
            <el-form-item label="岗位名称" prop="postName">
               <el-input
                  v-model="queryParams.postName"
                  placeholder="请输入岗位名称"
                  clearable
                  style="width: 240px"
                  @keyup.enter="handleQuery"
               />
            </el-form-item>
            <el-form-item label="状态" prop="status">
               <el-select v-model="queryParams.status" placeholder="岗位状态" clearable style="width: 240px">
                  <el-option
                     v-for="dict in sys_normal_disable"
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
                     v-hasPermi="['system:post:add']"
                     class="action-btn-primary"
                  >新增</el-button>
                  <el-button
                     type="success"
                     icon="Edit"
                     :disabled="single"
                     @click="handleUpdate"
                     v-hasPermi="['system:post:edit']"
                     class="action-btn-secondary"
                  >修改</el-button>
                  <el-button
                     type="danger"
                     icon="Delete"
                     :disabled="multiple"
                     @click="handleDelete"
                     v-hasPermi="['system:post:remove']"
                     class="action-btn-secondary"
                  >删除</el-button>
                  <el-button
                     type="warning"
                     icon="Download"
                     @click="handleExport"
                     v-hasPermi="['system:post:export']"
                     class="action-btn-secondary"
                  >导出</el-button>
               </div>
               <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
            </div>

            <el-table v-loading="loading" :data="postList" @selection-change="handleSelectionChange" class="polaris-el-table">
               <el-table-column type="selection" width="55" align="center" />
               <el-table-column label="岗位编号" align="center" prop="postId" width="100" />
               <el-table-column label="岗位编码" align="center" prop="postCode" min-width="150" />
               <el-table-column label="岗位名称" align="center" prop="postName" min-width="180" />
               <el-table-column label="岗位排序" align="center" prop="postSort" width="120" />
               <el-table-column label="状态" align="center" prop="status" width="100">
                  <template #default="scope">
                     <div class="status-cell">
                        <span :class="['pulse-light-ripple', scope.row.status === '0' ? 'pulse-active' : 'pulse-error']"></span>
                        <span class="status-label" :class="scope.row.status === '0' ? 'text-active' : 'text-error'">{{ scope.row.status === '0' ? '正常' : '停用' }}</span>
                     </div>
                  </template>
               </el-table-column>
               <el-table-column label="创建时间" align="center" prop="createTime" width="180">
                  <template #default="scope">
                     <span>{{ parseTime(scope.row.createTime) }}</span>
                  </template>
               </el-table-column>
               <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
                  <template #default="scope">
                     <el-tooltip content="修改" placement="top">
                        <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:post:edit']"></el-button>
                     </el-tooltip>
                     <el-tooltip content="删除" placement="top">
                        <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:post:remove']"></el-button>
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

      <!-- 添加或修改岗位对话框 -->
      <el-dialog :title="title" v-model="open" width="500px" append-to-body class="polaris-glass-dialog">
         <el-form ref="postRef" :model="form" :rules="rules" label-width="80px">
            <el-form-item label="岗位名称" prop="postName">
               <el-input v-model="form.postName" placeholder="请输入岗位名称" />
            </el-form-item>
            <el-form-item label="岗位编码" prop="postCode">
               <el-input v-model="form.postCode" placeholder="请输入编码名称" />
            </el-form-item>
            <el-form-item label="岗位顺序" prop="postSort">
               <el-input-number v-model="form.postSort" controls-position="right" :min="0" />
            </el-form-item>
            <el-form-item label="岗位状态" prop="status">
               <el-radio-group v-model="form.status">
                  <el-radio
                     v-for="dict in sys_normal_disable"
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

<script setup name="Post">
import {addPost, delPost, getPost, listPost, updatePost} from "@/api/system/post"

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict("sys_normal_disable")

const postList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    postCode: undefined,
    postName: undefined,
    status: undefined
  },
  rules: {
    postName: [{ required: true, message: "岗位名称不能为空", trigger: "blur" }],
    postCode: [{ required: true, message: "岗位编码不能为空", trigger: "blur" }],
    postSort: [{ required: true, message: "岗位顺序不能为空", trigger: "blur" }],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询岗位列表 */
function getList() {
  loading.value = true
  listPost(queryParams.value).then(response => {
    postList.value = response.rows
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
    postId: undefined,
    postCode: undefined,
    postName: undefined,
    postSort: 0,
    status: "0",
    remark: undefined
  }
  proxy.resetForm("postRef")
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

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.postId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加岗位"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const postId = row.postId || ids.value
  getPost(postId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改岗位"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["postRef"].validate(valid => {
    if (valid) {
      if (form.value.postId != undefined) {
        updatePost(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addPost(form.value).then(() => {
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
  const postIds = row.postId || ids.value
  proxy.$modal.confirm('是否确认删除岗位编号为"' + postIds + '"的数据项？').then(function() {
    return delPost(postIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("system/post/export", {
    ...queryParams.value
  }, `post_${new Date().getTime()}.xlsx`)
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
/* 针对北辰岗位管理表格的公共组件覆盖（全局非 scoped，但仅对 .polaris-el-table 生效以起隔离保护作用） */
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

/* 局部重写数字输入框的增减按钮（不仅包含表格内，同样适配弹窗内，保证箭头在任何状态下都高对比度且清晰可见） */
.polaris-el-table,
.polaris-glass-dialog {
  .el-input-number {
    .el-input-number__increase,
    .el-input-number__decrease {
      background-color: #f8fafc !important;
      border-color: rgba(0, 0, 0, 0.05) !important;
      color: #64748b !important;

      &:hover {
        color: #4f46e5 !important;
        background-color: rgba(79, 70, 229, 0.05) !important;
      }
      
      &.is-disabled {
        color: #cbd5e1 !important;
        background-color: #f1f5f9 !important;
      }
    }
  }
}

/* 暗黑模式样式覆盖 */
.dark {
  .polaris-el-table,
  .polaris-glass-dialog {
    .el-input-number {
      .el-input-number__increase,
      .el-input-number__decrease {
        background-color: rgba(255, 255, 255, 0.05) !important;
        border-color: rgba(255, 255, 255, 0.08) !important;
        color: #94a3b8 !important;

        &:hover {
          color: #38bdf8 !important;
          background-color: rgba(56, 189, 248, 0.12) !important;
        }

        &.is-disabled {
          color: #475569 !important;
          background-color: rgba(255, 255, 255, 0.01) !important;
        }
      }
    }
  }
}
</style>
