<template>
   <div class="app-container no-sidebar-manage-wrap">
      <div class="content-inner">
         <!-- 搜索过滤器玻璃卡片 -->
         <div class="polaris-filter-card" v-show="showSearch">
            <el-form :model="queryParams" ref="queryRef" :inline="true" class="polaris-filter-form">
               <el-form-item label="菜单名称" prop="menuName">
                  <el-input
                     v-model="queryParams.menuName"
                     placeholder="请输入菜单名称"
                     clearable
                     style="width: 240px"
                     @keyup.enter="handleQuery"
                  />
               </el-form-item>
               <el-form-item label="状态" prop="status">
                  <el-select v-model="queryParams.status" placeholder="菜单状态" clearable style="width: 240px">
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
         </div>

         <!-- 数据表格玻璃卡片 -->
         <div class="polaris-table-card">
            <div class="polaris-action-row">
               <div class="actions-left">
                  <el-button
                     type="primary"
                     icon="Plus"
                     @click="handleAdd"
                     v-hasPermi="['system:menu:add']"
                     class="action-btn-primary"
                  >新增</el-button>
                  <el-button
                     icon="Check"
                     @click="handleSaveSort"
                     v-hasPermi="['system:menu:edit']"
                     class="action-btn-secondary"
                  >保存排序</el-button>
                  <el-button 
                     icon="Sort"
                     @click="toggleExpandAll"
                     class="action-btn-secondary"
                  >展开/折叠</el-button>
               </div>
               <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
            </div>

            <el-table
               v-if="refreshTable"
               v-loading="loading"
               :data="menuList"
               row-key="menuId"
               :default-expand-all="tableExpandAll"
               :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
               class="polaris-el-table"
            >
               <el-table-column prop="menuName" label="菜单名称" :show-overflow-tooltip="true" width="220">
                  <template #default="scope">
                     <svg-icon :icon-class="scope.row.icon" />
                     <span class="ml5">{{ scope.row.menuName }}</span>
                  </template>
               </el-table-column>
               <el-table-column prop="menuType" label="类型" width="100">
                  <template #default="scope">
                     <el-tag v-if="scope.row.menuType === 'M' && scope.row.isFrame === '0'" type="danger" size="small">外链</el-tag>
                     <el-tag v-else-if="scope.row.menuType === 'M'" type="primary" size="small">目录</el-tag>
                     <el-tag v-else-if="scope.row.menuType === 'C' && scope.row.isFrame === '0'" type="danger" size="small">外链</el-tag>
                     <el-tag v-else-if="scope.row.menuType === 'C'" type="success" size="small">菜单</el-tag>
                     <el-tag v-else-if="scope.row.menuType === 'F'" type="warning" size="small">按钮</el-tag>
                  </template>
               </el-table-column>
               <el-table-column prop="orderNum" label="排序" width="200">
                  <template #default="scope">
                     <el-input-number v-model="scope.row.orderNum" controls-position="right" :min="0" style="width: 88px" />
                  </template>
               </el-table-column>
               <el-table-column prop="perms" label="权限标识" :show-overflow-tooltip="true" />
               <el-table-column prop="component" label="组件路径" :show-overflow-tooltip="true" />
               <el-table-column prop="status" label="状态" width="100" align="center">
                  <template #default="scope">
                     <div class="status-cell">
                        <span :class="['pulse-light-ripple', scope.row.status === '0' ? 'pulse-active' : 'pulse-error']"></span>
                        <span class="status-label" :class="scope.row.status === '0' ? 'text-active' : 'text-error'">{{ scope.row.status === '0' ? '正常' : '停用' }}</span>
                     </div>
                  </template>
               </el-table-column>
               <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
                  <template #default="scope">
                     <el-tooltip content="修改" placement="top">
                        <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:menu:edit']"></el-button>
                     </el-tooltip>
                     <el-tooltip content="新增" placement="top">
                        <el-button link type="primary" icon="Plus" @click="handleAdd(scope.row)" v-hasPermi="['system:menu:add']"></el-button>
                     </el-tooltip>
                     <el-tooltip content="删除" placement="top">
                        <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:menu:remove']"></el-button>
                     </el-tooltip>
                  </template>
               </el-table-column>
            </el-table>
         </div>
      </div>

      <!-- 添加或修改菜单对话框 -->
      <el-dialog :title="title" v-model="open" width="680px" append-to-body class="polaris-glass-dialog">
         <el-form ref="menuRef" :model="form" :rules="rules" label-width="100px">
            <el-row>
               <el-col :span="24">
                  <el-form-item label="上级菜单">
                     <el-tree-select
                        v-model="form.parentId"
                        :data="menuOptions"
                        :props="{ value: 'menuId', label: 'menuName', children: 'children' }"
                        value-key="menuId"
                        placeholder="选择上级菜单"
                        check-strictly
                     />
                  </el-form-item>
               </el-col>
               <el-col :span="24">
                  <el-form-item label="菜单类型" prop="menuType">
                     <el-radio-group v-model="form.menuType">
                        <el-radio value="M">目录</el-radio>
                        <el-radio value="C">菜单</el-radio>
                        <el-radio value="F">按钮</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
               <el-col :span="12" v-if="form.menuType != 'F'">
                  <el-form-item label="菜单图标" prop="icon">
                     <el-popover
                        placement="bottom-start"
                        :width="540"
                        trigger="click"
                     >
                        <template #reference>
                           <el-input v-model="form.icon" placeholder="点击选择图标" @blur="showSelectIcon" readonly>
                              <template #prefix>
                                 <svg-icon
                                    v-if="form.icon"
                                    :icon-class="form.icon"
                                    class="el-input__icon"
                                    style="height: 32px;width: 16px;"
                                 />
                                 <el-icon v-else style="height: 32px;width: 16px;"><search /></el-icon>
                              </template>
                           </el-input>
                        </template>
                        <icon-select ref="iconSelectRef" @selected="selected" :active-icon="form.icon" />
                     </el-popover>
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="显示排序" prop="orderNum">
                     <el-input-number v-model="form.orderNum" controls-position="right" :min="0" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="菜单名称" prop="menuName">
                     <el-input v-model="form.menuName" placeholder="请输入菜单名称" />
                  </el-form-item>
               </el-col>
               <el-col :span="12" v-if="form.menuType == 'C'">
                  <el-form-item prop="routeName">
                     <template #label>
                        <span>
                           <el-tooltip content="默认不填则和路由地址相同：如地址为：`user`，则名称为`User`（注意：因为router会删除名称相同路由，为避免名字的冲突，特殊情况下请自定义，保证唯一性）" placement="top">
                              <el-icon><question-filled /></el-icon>
                           </el-tooltip>
                           路由名称
                        </span>
                     </template>
                     <el-input v-model="form.routeName" placeholder="请输入路由名称" />
                  </el-form-item>
               </el-col>
               <el-col :span="12" v-if="form.menuType != 'F'">
                  <el-form-item>
                     <template #label>
                        <span>
                           <el-tooltip content="选择是外链则路由地址需要以`http(s)://`开头" placement="top">
                              <el-icon><question-filled /></el-icon>
                           </el-tooltip>是否外链
                        </span>
                     </template>
                     <el-radio-group v-model="form.isFrame">
                        <el-radio value="0">是</el-radio>
                        <el-radio value="1">否</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
               <el-col :span="12" v-if="form.menuType != 'F'">
                  <el-form-item prop="path">
                     <template #label>
                        <span>
                           <el-tooltip content="访问的路由地址，如：`user`，如外网地址需内链访问则以`http(s)://`开头" placement="top">
                              <el-icon><question-filled /></el-icon>
                           </el-tooltip>
                           路由地址
                        </span>
                     </template>
                     <el-input v-model="form.path" placeholder="请输入路由地址" />
                  </el-form-item>
               </el-col>
               <el-col :span="12" v-if="form.menuType == 'C'">
                  <el-form-item prop="component">
                     <template #label>
                        <span>
                           <el-tooltip content="访问的组件路径，如：`system/user/index`，默认在`views`目录下" placement="top">
                              <el-icon><question-filled /></el-icon>
                           </el-tooltip>
                           组件路径
                        </span>
                     </template>
                     <el-input v-model="form.component" placeholder="请输入组件路径" />
                  </el-form-item>
               </el-col>
               <el-col :span="12" v-if="form.menuType != 'M'">
                  <el-form-item>
                     <el-input v-model="form.perms" placeholder="请输入权限标识" maxlength="100" />
                     <template #label>
                        <span>
                           <el-tooltip content="控制器中定义的权限字符，如：@PreAuthorize(`@ss.hasPermi('system:user:list')`)" placement="top">
                              <el-icon><question-filled /></el-icon>
                           </el-tooltip>
                           权限字符
                        </span>
                     </template>
                  </el-form-item>
               </el-col>
               <el-col :span="12" v-if="form.menuType == 'C'">
                  <el-form-item>
                     <el-input v-model="form.query" placeholder="请输入路由参数" maxlength="255" />
                     <template #label>
                        <span>
                           <el-tooltip content='访问路由的默认传递参数，如：`{"id": 1, "name": "ry"}`' placement="top">
                              <el-icon><question-filled /></el-icon>
                           </el-tooltip>
                           路由参数
                        </span>
                     </template>
                  </el-form-item>
               </el-col>
               <el-col :span="12" v-if="form.menuType == 'C'">
                  <el-form-item>
                     <template #label>
                        <span>
                           <el-tooltip content="选择是则会被`keep-alive`缓存，需要匹配组件的`name`和地址保持一致" placement="top">
                              <el-icon><question-filled /></el-icon>
                           </el-tooltip>
                           是否缓存
                        </span>
                     </template>
                     <el-radio-group v-model="form.isCache">
                        <el-radio value="0">缓存</el-radio>
                        <el-radio value="1">不缓存</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
               <el-col :span="12" v-if="form.menuType != 'F'">
                  <el-form-item>
                     <template #label>
                        <span>
                           <el-tooltip content="选择隐藏则路由将不会出现在侧边栏，但仍然可以访问" placement="top">
                              <el-icon><question-filled /></el-icon>
                           </el-tooltip>
                           显示状态
                        </span>
                     </template>
                     <el-radio-group v-model="form.visible">
                        <el-radio
                           v-for="dict in sys_show_hide"
                           :key="dict.value"
                           :value="dict.value"
                        >{{ dict.label }}</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item>
                     <template #label>
                        <span>
                           <el-tooltip content="选择停用则路由将不会出现在侧边栏，也不能被访问" placement="top">
                              <el-icon><question-filled /></el-icon>
                           </el-tooltip>
                           菜单状态
                        </span>
                     </template>
                     <el-radio-group v-model="form.status">
                        <el-radio
                           v-for="dict in sys_normal_disable"
                           :key="dict.value"
                           :value="dict.value"
                        >{{ dict.label }}</el-radio>
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
   </div>
</template>

<script setup name="Menu">
import {addMenu, delMenu, getMenu, listMenu, updateMenu, updateMenuSort} from "@/api/system/menu"
import SvgIcon from "@/components/SvgIcon"
import IconSelect from "@/components/IconSelect"

const { proxy } = getCurrentInstance()
const { sys_show_hide, sys_normal_disable } = useDict("sys_show_hide", "sys_normal_disable")

const menuList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const title = ref("")
const menuOptions = ref([])
const isExpandAll = ref(false)
const tableExpandAll = ref(false)
const refreshTable = ref(true)
const iconSelectRef = ref(null)
const originalOrders = ref({})

const data = reactive({
  form: {},
  queryParams: {
    menuName: undefined,
    visible: undefined
  },
  rules: {
    menuName: [{ required: true, message: "菜单名称不能为空", trigger: "blur" }],
    orderNum: [{ required: true, message: "菜单顺序不能为空", trigger: "blur" }],
    path: [{ required: true, message: "路由地址不能为空", trigger: "blur" }]
  },
})

const { queryParams, form, rules } = toRefs(data)

/** 查询菜单列表 */
function getList() {
  loading.value = true
  listMenu(queryParams.value).then(response => {
    menuList.value = proxy.handleTree(response.data, "menuId")
    recordOriginalOrders(menuList.value)
    loading.value = false
    tableExpandAll.value = isExpandAll.value
    nextTick(() => {
      // 挂载后重置控制变量为 false，阻断由于 orderNum 等属性修改导致重绘而触发的强制展开行为
      tableExpandAll.value = false
    })
  })
}

/** 查询菜单下拉树结构 */
function getTreeselect() {
  menuOptions.value = []
  listMenu().then(response => {
    const menu = { menuId: 0, menuName: "主类目", children: [] }
    menu.children = proxy.handleTree(response.data, "menuId")
    menuOptions.value.push(menu)
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
    menuId: undefined,
    parentId: 0,
    menuName: undefined,
    icon: undefined,
    menuType: "M",
    orderNum: undefined,
    isFrame: "1",
    isCache: "0",
    visible: "0",
    status: "0"
  }
  proxy.resetForm("menuRef")
}

/** 展示下拉图标 */
function showSelectIcon() {
  iconSelectRef.value.reset()
}

/** 选择图标 */
function selected(name) {
  form.value.icon = name
}

/** 搜索按钮操作 */
function handleQuery() {
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 新增按钮操作 */
function handleAdd(row) {
  reset()
  getTreeselect()
  if (row != null && row.menuId) {
    form.value.parentId = row.menuId
  } else {
    form.value.parentId = 0
  }
  open.value = true
  title.value = "添加菜单"
}

/** 展开/折叠操作 */
function toggleExpandAll() {
  refreshTable.value = false
  isExpandAll.value = !isExpandAll.value
  tableExpandAll.value = isExpandAll.value
  nextTick(() => {
    refreshTable.value = true
    nextTick(() => {
      // 重建完成后再次重置为 false，保障后续属性变动不触发强制展开
      tableExpandAll.value = false
    })
  })
}

/** 修改按钮操作 */
async function handleUpdate(row) {
  reset()
  await getTreeselect()
  getMenu(row.menuId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改菜单"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["menuRef"].validate(valid => {
    if (valid) {
      if (form.value.menuId != undefined) {
        updateMenu(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addMenu(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}


/** 递归记录原始排序 */
function recordOriginalOrders(list) {
  list.forEach(item => {
    originalOrders.value[item.menuId] = item.orderNum
    if (item.children && item.children.length) {
      recordOriginalOrders(item.children)
    }
  })
}

/** 保存排序 */
function handleSaveSort() {
  const changedMenuIds = []
  const changedOrderNums = []
  const collectChanged = (list) => {
    list.forEach(item => {
      if (String(originalOrders.value[item.menuId]) !== String(item.orderNum)) {
        changedMenuIds.push(item.menuId)
        changedOrderNums.push(item.orderNum)
      }
      if (item.children && item.children.length) {
        collectChanged(item.children)
      }
    })
  }
  collectChanged(menuList.value)
  if (changedMenuIds.length === 0) {
   proxy.$modal.msgWarning("未检测到排序修改")
    return
  }
  updateMenuSort({ menuIds: changedMenuIds.join(","), orderNums: changedOrderNums.join(",") }).then(() => {
   proxy.$modal.msgSuccess("排序保存成功")
    recordOriginalOrders(menuList.value)
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  proxy.$modal.confirm('是否确认删除名称为"' + row.menuName + '"的数据项?').then(function() {
    return delMenu(row.menuId)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
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
/* 针对北辰表格的公共组件覆盖（全局非 scoped，但仅对 .polaris-el-table 生效以起隔离保护作用） */
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

  .el-tag {
    font-weight: 700;
    border-radius: 8px;
    padding: 2px 8px;
  }
  .el-tag--danger {
    color: #ef4444 !important;
    background-color: rgba(239, 68, 68, 0.08) !important;
    border-color: rgba(239, 68, 68, 0.15) !important;
  }
  .el-tag--primary {
    color: #4f46e5 !important;
    background-color: rgba(79, 70, 229, 0.08) !important;
    border-color: rgba(79, 70, 229, 0.15) !important;
  }
  .el-tag--success {
    color: #10b981 !important;
    background-color: rgba(16, 185, 129, 0.08) !important;
    border-color: rgba(16, 185, 129, 0.15) !important;
  }
  .el-tag--warning {
    color: #d97706 !important;
    background-color: rgba(217, 119, 6, 0.08) !important;
    border-color: rgba(217, 119, 6, 0.15) !important;
  }

  /* 局部重写数字输入框的增减按钮，保证箭头在任何状态下都高对比度且清晰可见 */
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
  .polaris-el-table {
    .el-tag--danger {
      color: #f87171 !important;
      background-color: rgba(248, 113, 113, 0.15) !important;
      border-color: rgba(248, 113, 113, 0.25) !important;
    }
    .el-tag--primary {
      color: #38bdf8 !important;
      background-color: rgba(56, 189, 248, 0.15) !important;
      border-color: rgba(56, 189, 248, 0.25) !important;
    }
    .el-tag--success {
      color: #34d399 !important;
      background-color: rgba(52, 211, 153, 0.15) !important;
      border-color: rgba(52, 211, 153, 0.25) !important;
    }
    .el-tag--warning {
      color: #fbbf24 !important;
      background-color: rgba(251, 191, 36, 0.15) !important;
      border-color: rgba(251, 191, 36, 0.25) !important;
    }

    .el-input-number {
      .el-input-number__increase,
      .el-input-number__decrease {
        background-color: rgba(255, 255, 255, 0.03) !important;
        border-color: rgba(255, 255, 255, 0.08) !important;
        color: #94a3b8 !important;

        &:hover {
          color: #38bdf8 !important;
          background-color: rgba(56, 189, 248, 0.1) !important;
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
