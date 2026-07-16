<template>
   <div class="app-container no-sidebar-manage-wrap">
      <div class="content-inner">
         <!-- 搜索过滤器玻璃卡片 -->
         <div class="polaris-filter-card" v-show="showSearch">
            <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="68px" class="polaris-filter-form">
               <el-form-item label="角色名称" prop="roleName">
                  <el-input
                     v-model="queryParams.roleName"
                     placeholder="请输入角色名称"
                     clearable
                     style="width: 240px"
                     @keyup.enter="handleQuery"
                  />
               </el-form-item>
               <el-form-item label="权限字符" prop="roleKey">
                  <el-input
                     v-model="queryParams.roleKey"
                     placeholder="请输入权限字符"
                     clearable
                     style="width: 240px"
                     @keyup.enter="handleQuery"
                  />
               </el-form-item>
               <el-form-item label="状态" prop="status">
                  <el-select
                     v-model="queryParams.status"
                     placeholder="角色状态"
                     clearable
                     style="width: 240px"
                  >
                     <el-option
                        v-for="dict in sys_normal_disable"
                        :key="dict.value"
                        :label="dict.label"
                        :value="dict.value"
                     />
                  </el-select>
               </el-form-item>
               <el-form-item label="创建时间" style="width: 308px">
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
         </div>

         <!-- 数据表格玻璃卡片 -->
         <div class="polaris-table-card">
            <div class="polaris-action-row">
               <div class="actions-left">
                  <el-button
                     type="primary"
                     icon="Plus"
                     @click="handleAdd"
                     v-hasPermi="['system:role:add']"
                     class="action-btn-primary"
                  >新增</el-button>
                  <el-button
                     icon="Edit"
                     :disabled="single"
                     @click="handleUpdate"
                     v-hasPermi="['system:role:edit']"
                     class="action-btn-secondary"
                  >修改</el-button>
                  <el-button
                     type="danger"
                     icon="Delete"
                     :disabled="multiple"
                     @click="handleDelete"
                     v-hasPermi="['system:role:remove']"
                     class="action-btn-danger"
                  >删除</el-button>
                  <el-button
                     icon="Download"
                     @click="handleExport"
                     v-hasPermi="['system:role:export']"
                     class="action-btn-secondary"
                  >导出</el-button>
               </div>
               <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
            </div>

            <!-- 表格数据 -->
            <el-table v-loading="loading" :data="roleList" @selection-change="handleSelectionChange" class="polaris-el-table">
               <el-table-column type="selection" width="55" align="center" />
               <el-table-column label="角色编号" prop="roleId" width="120" />
               <el-table-column label="角色名称" prop="roleName" :show-overflow-tooltip="true" width="150" />
               <el-table-column label="权限字符" prop="roleKey" :show-overflow-tooltip="true" width="150" />
               <el-table-column label="显示顺序" prop="roleSort" width="100" />
               <el-table-column label="状态" align="center" width="100">
                  <template #default="scope">
                     <div class="status-cell clickable-status" @click="handleStatusChange(scope.row)">
                        <span :class="['pulse-light-ripple', scope.row.status === '0' ? 'pulse-active' : 'pulse-error']"></span>
                        <span class="status-label" :class="scope.row.status === '0' ? 'text-active' : 'text-error'">{{ scope.row.status === '0' ? '正常' : '停用' }}</span>
                     </div>
                  </template>
               </el-table-column>
               <el-table-column label="创建时间" align="center" prop="createTime">
                  <template #default="scope">
                     <span>{{ parseTime(scope.row.createTime) }}</span>
                  </template>
               </el-table-column>
               <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
                  <template #default="scope">
                    <el-tooltip content="修改" placement="top" v-if="scope.row.roleId !== 1">
                      <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:role:edit']"></el-button>
                    </el-tooltip>
                    <el-tooltip content="删除" placement="top" v-if="scope.row.roleId !== 1">
                      <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:role:remove']"></el-button>
                    </el-tooltip>
                    <el-tooltip content="数据权限" placement="top" v-if="scope.row.roleId !== 1">
                      <el-button link type="primary" icon="CircleCheck" @click="handleDataScope(scope.row)" v-hasPermi="['system:role:edit']"></el-button>
                    </el-tooltip>
                    <el-tooltip content="分配用户" placement="top" v-if="scope.row.roleId !== 1">
                      <el-button link type="primary" icon="User" @click="handleAuthUser(scope.row)" v-hasPermi="['system:role:edit']"></el-button>
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

      <!-- 添加或修改角色配置对话框 -->
      <el-dialog :title="title" v-model="open" width="500px" append-to-body class="polaris-glass-dialog">
         <el-form ref="roleRef" :model="form" :rules="rules" label-width="100px">
            <el-form-item label="角色名称" prop="roleName">
               <el-input v-model="form.roleName" placeholder="请输入角色名称" />
            </el-form-item>
            <el-form-item prop="roleKey">
               <template #label>
                  <span>
                     <el-tooltip content="控制器中定义的权限字符，如：@PreAuthorize(`@ss.hasRole('admin')`)" placement="top">
                        <el-icon><question-filled /></el-icon>
                     </el-tooltip>
                     权限字符
                  </span>
               </template>
               <el-input v-model="form.roleKey" placeholder="请输入权限字符" />
            </el-form-item>
            <el-form-item label="角色顺序" prop="roleSort">
               <el-input-number v-model="form.roleSort" controls-position="right" :min="0" />
            </el-form-item>
            <el-form-item label="状态">
               <el-radio-group v-model="form.status">
                  <el-radio
                     v-for="dict in sys_normal_disable"
                     :key="dict.value"
                     :value="dict.value"
                  >{{ dict.label }}</el-radio>
               </el-radio-group>
            </el-form-item>
            <el-form-item label="菜单权限">
               <el-checkbox v-model="menuExpand" @change="handleCheckedTreeExpand($event, 'menu')">展开/折叠</el-checkbox>
               <el-checkbox v-model="menuNodeAll" @change="handleCheckedTreeNodeAll($event, 'menu')">全选/全不选</el-checkbox>
               <el-checkbox v-model="form.menuCheckStrictly" @change="handleCheckedTreeConnect($event, 'menu')">父子联动</el-checkbox>
               <el-tree
                  class="tree-border"
                  :data="menuOptions"
                  show-checkbox
                  ref="menuRef"
                  node-key="id"
                  :check-strictly="!form.menuCheckStrictly"
                  empty-text="加载中，请稍候"
                  :props="{ label: 'label', children: 'children' }"
               ></el-tree>
            </el-form-item>
            <el-form-item label="备注">
               <el-input v-model="form.remark" type="textarea" placeholder="请输入内容"></el-input>
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitForm">确 定</el-button>
               <el-button @click="cancel">取 消</el-button>
            </div>
         </template>
      </el-dialog>

      <!-- 分配角色数据权限对话框 -->
      <el-dialog :title="title" v-model="openDataScope" width="500px" append-to-body class="polaris-glass-dialog">
         <el-form :model="form" label-width="80px">
            <el-form-item label="角色名称">
               <el-input v-model="form.roleName" :disabled="true" />
            </el-form-item>
            <el-form-item label="权限字符">
               <el-input v-model="form.roleKey" :disabled="true" />
            </el-form-item>
            <el-form-item label="权限范围">
               <el-select v-model="form.dataScope" @change="dataScopeSelectChange">
                  <el-option
                     v-for="item in dataScopeOptions"
                     :key="item.value"
                     :label="item.label"
                     :value="item.value"
                  ></el-option>
               </el-select>
            </el-form-item>
            <el-form-item label="数据权限" v-show="form.dataScope == 2">
               <el-checkbox v-model="deptExpand" @change="handleCheckedTreeExpand($event, 'dept')">展开/折叠</el-checkbox>
               <el-checkbox v-model="deptNodeAll" @change="handleCheckedTreeNodeAll($event, 'dept')">全选/全不选</el-checkbox>
               <el-checkbox v-model="form.deptCheckStrictly" @change="handleCheckedTreeConnect($event, 'dept')">父子联动</el-checkbox>
               <el-tree
                  class="tree-border"
                  :data="deptOptions"
                  show-checkbox
                  default-expand-all
                  ref="deptRef"
                  node-key="id"
                  :check-strictly="!form.deptCheckStrictly"
                  empty-text="加载中，请稍候"
                  :props="{ label: 'label', children: 'children' }"
               ></el-tree>
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitDataScope">确 定</el-button>
               <el-button @click="cancelDataScope">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup name="Role">
import {
  addRole,
  changeRoleStatus,
  dataScope,
  delRole,
  deptTreeSelect,
  getRole,
  listRole,
  updateRole
} from "@/api/system/role"
import {roleMenuTreeselect, treeselect as menuTreeselect} from "@/api/system/menu"

const router = useRouter()
const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict("sys_normal_disable")

const roleList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const dateRange = ref([])
const menuOptions = ref([])
const menuExpand = ref(false)
const menuNodeAll = ref(false)
const deptExpand = ref(true)
const deptNodeAll = ref(false)
const deptOptions = ref([])
const openDataScope = ref(false)
const menuRef = ref(null)
const deptRef = ref(null)

/** 数据范围选项*/
const dataScopeOptions = ref([
  { value: "1", label: "全部数据权限" },
  { value: "2", label: "自定数据权限" },
  { value: "3", label: "本部门数据权限" },
  { value: "4", label: "本部门及以下数据权限" },
  { value: "5", label: "仅本人数据权限" }
])

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    roleName: undefined,
    roleKey: undefined,
    status: undefined
  },
  rules: {
    roleName: [{ required: true, message: "角色名称不能为空", trigger: "blur" }],
    roleKey: [{ required: true, message: "权限字符不能为空", trigger: "blur" }],
    roleSort: [{ required: true, message: "角色顺序不能为空", trigger: "blur" }]
  },
})

const { queryParams, form, rules } = toRefs(data)

/** 查询角色列表 */
function getList() {
  loading.value = true
  listRole(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    roleList.value = response.rows
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
  handleQuery()
}

/** 删除按钮操作 */
function handleDelete(row) {
  const roleIds = row.roleId || ids.value
  proxy.$modal.confirm('是否确认删除角色编号为"' + roleIds + '"的数据项?').then(function () {
    return delRole(roleIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("system/role/export", {
    ...queryParams.value,
  }, `role_${new Date().getTime()}.xlsx`)
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.roleId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 角色状态修改 */
function handleStatusChange(row) {
  let text = row.status === "0" ? "启用" : "停用"
  proxy.$modal.confirm('确认要"' + text + '""' + row.roleName + '"角色吗?').then(function () {
    return changeRoleStatus(row.roleId, row.status)
  }).then(() => {
    proxy.$modal.msgSuccess(text + "成功")
  }).catch(function () {
    row.status = row.status === "0" ? "1" : "0"
  })
}

/** 更多操作 */
function handleCommand(command, row) {
  switch (command) {
    case "handleDataScope":
      handleDataScope(row)
      break
    case "handleAuthUser":
      handleAuthUser(row)
      break
    default:
      break
  }
}

/** 分配用户 */
function handleAuthUser(row) {
  router.push("/system/role-auth/user/" + row.roleId)
}

/** 查询菜单树结构 */
function getMenuTreeselect() {
  menuTreeselect().then(response => {
    menuOptions.value = response.data
  })
}

/** 所有部门节点数据 */
function getDeptAllCheckedKeys() {
  // 目前被选中的部门节点
  let checkedKeys = deptRef.value.getCheckedKeys()
  // 半选中的部门节点
  let halfCheckedKeys = deptRef.value.getHalfCheckedKeys()
  checkedKeys.unshift.apply(checkedKeys, halfCheckedKeys)
  return checkedKeys
}

/** 重置新增的表单以及其他数据  */
function reset() {
  if (menuRef.value != undefined) {
    menuRef.value.setCheckedKeys([])
  }
  menuExpand.value = false
  menuNodeAll.value = false
  deptExpand.value = true
  deptNodeAll.value = false
  form.value = {
    roleId: undefined,
    roleName: undefined,
    roleKey: undefined,
    roleSort: 0,
    status: "0",
    menuIds: [],
    deptIds: [],
    menuCheckStrictly: true,
    deptCheckStrictly: true,
    remark: undefined
  }
  proxy.resetForm("roleRef")
}

/** 添加角色 */
function handleAdd() {
  reset()
  getMenuTreeselect()
  open.value = true
  title.value = "添加角色"
}

/** 修改角色 */
function handleUpdate(row) {
  reset()
  const roleId = row.roleId || ids.value
  const roleMenu = getRoleMenuTreeselect(roleId)
  getRole(roleId).then(response => {
    form.value = response.data
    form.value.roleSort = Number(form.value.roleSort)
    open.value = true
    nextTick(() => {
      roleMenu.then((res) => {
        let checkedKeys = res.checkedKeys
        checkedKeys.forEach((v) => {
          nextTick(() => {
            menuRef.value.setChecked(v, true, false)
          })
        })
      })
    })
  })
  title.value = "修改角色"
}

/** 根据角色ID查询菜单树结构 */
function getRoleMenuTreeselect(roleId) {
  return roleMenuTreeselect(roleId).then(response => {
    menuOptions.value = response.menus
    return response
  })
}

/** 根据角色ID查询部门树结构 */
function getDeptTree(roleId) {
  return deptTreeSelect(roleId).then(response => {
    deptOptions.value = response.depts
    return response
  })
}

/** 树权限（展开/折叠）*/
function handleCheckedTreeExpand(value, type) {
  if (type == "menu") {
    let treeList = menuOptions.value
    for (let i = 0; i < treeList.length; i++) {
      menuRef.value.store.nodesMap[treeList[i].id].expanded = value
    }
  } else if (type == "dept") {
    let treeList = deptOptions.value
    for (let i = 0; i < treeList.length; i++) {
      deptRef.value.store.nodesMap[treeList[i].id].expanded = value
    }
  }
}

/** 树权限（全选/全不选） */
function handleCheckedTreeNodeAll(value, type) {
  if (type == "menu") {
    menuRef.value.setCheckedNodes(value ? menuOptions.value : [])
  } else if (type == "dept") {
    deptRef.value.setCheckedNodes(value ? deptOptions.value : [])
  }
}

/** 树权限（父子联动） */
function handleCheckedTreeConnect(value, type) {
  if (type == "menu") {
    form.value.menuCheckStrictly = value ? true : false
  } else if (type == "dept") {
    form.value.deptCheckStrictly = value ? true : false
  }
}

/** 所有菜单节点数据 */
function getMenuAllCheckedKeys() {
  // 目前被选中的菜单节点
  let checkedKeys = menuRef.value.getCheckedKeys()
  // 半选中的菜单节点
  let halfCheckedKeys = menuRef.value.getHalfCheckedKeys()
  checkedKeys.unshift.apply(checkedKeys, halfCheckedKeys)
  return checkedKeys
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["roleRef"].validate(valid => {
    if (valid) {
      if (form.value.roleId != undefined) {
        form.value.menuIds = getMenuAllCheckedKeys()
        updateRole(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        form.value.menuIds = getMenuAllCheckedKeys()
        addRole(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 选择角色权限范围触发 */
function dataScopeSelectChange(value) {
  if (value !== "2") {
    deptRef.value.setCheckedKeys([])
  }
}

/** 分配数据权限操作 */
function handleDataScope(row) {
  reset()
  const deptTreeSelect = getDeptTree(row.roleId)
  getRole(row.roleId).then(response => {
    form.value = response.data
    openDataScope.value = true
    nextTick(() => {
      deptTreeSelect.then(res => {
        nextTick(() => {
          if (deptRef.value) {
            deptRef.value.setCheckedKeys(res.checkedKeys)
          }
        })
      })
    })
  })
  title.value = "分配数据权限"
}

/** 提交按钮（数据权限） */
function submitDataScope() {
  if (form.value.roleId != undefined) {
    form.value.deptIds = getDeptAllCheckedKeys()
    dataScope(form.value).then(() => {
      proxy.$modal.msgSuccess("修改成功")
      openDataScope.value = false
      getList()
    })
  }
}

/** 取消按钮（数据权限）*/
function cancelDataScope() {
  openDataScope.value = false
  reset()
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

/* ===== 搜索过滤器玻璃卡片 ===== */
.polaris-filter-card {
  padding: 18px 24px 12px;
  border-radius: 22px;
  backdrop-filter: blur(20px);
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.02);
  position: relative;
  top: 0;
  overflow: hidden;
  transition: top 0.4s cubic-bezier(0.25, 0.8, 0.25, 1), box-shadow 0.4s;

  &:hover {
    top: -2px;
    box-shadow: 0 12px 32px -4px rgba(0, 0, 0, 0.05);
  }

  .dark & {
    background: rgba(15, 23, 42, 0.45);
    border-color: rgba(255, 255, 255, 0.06);
    box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
    
    &:hover {
      box-shadow: 0 15px 45px rgba(0, 0, 0, 0.45);
    }
  }
}

/* 过滤表单 */
.polaris-filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 0;

  :deep(.el-form-item__label) {
    font-size: 12px;
    font-weight: 700;
    color: #64748b;
    
    .dark & {
      color: #94a3b8;
    }
  }

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    border-radius: 10px !important;
    background-color: #ffffff !important;
    border: 1px solid rgba(0, 0, 0, 0.08) !important;
    box-shadow: none !important;
    transition: border-color 0.3s;

    &:hover,
    &.is-focus {
      border-color: rgba(79, 70, 229, 0.4) !important;
    }
    
    .dark & {
      background-color: rgba(0, 0, 0, 0.35) !important;
      border-color: rgba(255, 255, 255, 0.08) !important;
      
      &:hover,
      &.is-focus {
        border-color: rgba(56, 189, 248, 0.4) !important;
      }
    }
  }

  :deep(.el-input__inner) {
    font-size: 12px;
    color: #0f172a;
    
    .dark & {
      color: #cbd5e1;
    }
  }

  :deep(.el-date-editor) {
    .el-input__wrapper {
      border-radius: 10px !important;
      background-color: #ffffff !important;
      border: 1px solid rgba(0, 0, 0, 0.08) !important;
      box-shadow: none !important;

      &:hover,
      &.is-focus {
        border-color: rgba(79, 70, 229, 0.4) !important;
      }
      
      .dark & {
        background-color: rgba(0, 0, 0, 0.35) !important;
        border-color: rgba(255, 255, 255, 0.08) !important;
        
        &:hover,
        &.is-focus {
          border-color: rgba(56, 189, 248, 0.4) !important;
        }
      }
    }
  }
}

/* 搜索/重置按钮 */
.polaris-query-btn {
  height: 32px;
  border-radius: 10px !important;
  font-size: 12px;
  font-weight: 600;
  background-color: #4f46e5 !important;
  border-color: #4f46e5 !important;
}

.polaris-reset-btn {
  height: 32px;
  border-radius: 10px !important;
  font-size: 12px;
  font-weight: 600;
  border-color: rgba(0, 0, 0, 0.08) !important;
  background-color: transparent !important;
  color: #475569 !important;

  &:hover {
    background-color: rgba(0, 0, 0, 0.02) !important;
  }
  
  .dark & {
    border-color: rgba(255, 255, 255, 0.1) !important;
    color: #cbd5e1 !important;
    
    &:hover {
      background-color: rgba(255, 255, 255, 0.04) !important;
    }
  }
}

/* 操作按钮行 */
.polaris-action-row {
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  width: 100%;
}

.actions-left {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* ===== 操作按钮样式 ===== */
.action-btn-primary,
.action-btn-danger,
.action-btn-secondary {
  height: 36px;
  border-radius: 12px !important;
  font-size: 12px;
  font-weight: 700;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.action-btn-primary {
  background-color: #4f46e5 !important;
  border-color: #4f46e5 !important;
  color: #ffffff !important;
  box-shadow: 0 4px 12px -2px rgba(79, 70, 229, 0.2);

  &:hover {
    background-color: #4338ca !important;
    box-shadow: 0 6px 16px -2px rgba(79, 70, 229, 0.3);
  }
}

.action-btn-danger {
  &:not(:disabled) {
    background-color: #ef4444 !important;
    border-color: #ef4444 !important;
    color: #ffffff !important;

    &:hover {
      background-color: #dc2626 !important;
      border-color: #dc2626 !important;
      box-shadow: 0 4px 12px rgba(239, 68, 68, 0.2);
    }
  }
}

.action-btn-secondary {
  border-color: rgba(0, 0, 0, 0.08) !important;
  background-color: #ffffff !important;
  color: #475569 !important;

  &:hover {
    background-color: rgba(79, 70, 229, 0.04) !important;
    border-color: rgba(79, 70, 229, 0.2) !important;
    color: #4f46e5 !important;
  }
  
  .dark & {
    border-color: rgba(255, 255, 255, 0.08) !important;
    background-color: rgba(255, 255, 255, 0.04) !important;
    color: #cbd5e1 !important;
    
    &:hover {
      background-color: rgba(56, 189, 248, 0.06) !important;
      border-color: rgba(56, 189, 248, 0.2) !important;
      color: #38bdf8 !important;
    }
  }
}

/* ===== 数据表格玻璃卡片 ===== */
.polaris-table-card {
  padding: 20px 24px;
  border-radius: 22px;
  backdrop-filter: blur(20px);
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  position: relative;
  top: 0;
  transition: top 0.4s cubic-bezier(0.25, 0.8, 0.25, 1), box-shadow 0.4s;

  &:hover {
    top: -2px;
    box-shadow: 0 12px 32px -4px rgba(0, 0, 0, 0.05);
  }

  .dark & {
    background: rgba(15, 23, 42, 0.45);
    border-color: rgba(255, 255, 255, 0.06);
    box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
    
    &:hover {
      box-shadow: 0 15px 45px rgba(0, 0, 0, 0.45);
    }
  }
}

/* ===== 表格透明化样式 ===== */
.polaris-el-table {
  background: transparent !important;

  :deep(tr),
  :deep(th),
  :deep(td) {
    background: transparent !important;
    border-bottom-color: rgba(0, 0, 0, 0.04) !important;
    color: #334155;
    
    .dark & {
      border-bottom-color: rgba(255, 255, 255, 0.06) !important;
      color: #cbd5e1;
    }
  }

  :deep(th) {
    font-weight: 700;
    font-size: 12px;
    padding: 12px 0;
    color: #64748b;
    
    .dark & {
      color: #94a3b8;
    }

    .cell {
      font-weight: 700;
    }
  }

  :deep(td) {
    padding: 14px 0;
    font-size: 13px;
  }

  :deep(.el-table__row) {
    transition: transform 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);

    &:hover {
      transform: scale(1.003) translateX(2px);
    }
  }

  :deep(.el-table__row):hover > td {
    background-color: rgba(79, 70, 229, 0.015) !important;
    
    .dark & {
      background-color: rgba(56, 189, 248, 0.02) !important;
    }
  }

  &::before,
  &::after {
    display: none !important;
  }

  :deep(.el-table__inner-wrapper::before) {
    display: none !important;
  }

  :deep(.el-table__body-wrapper) {
    &::-webkit-scrollbar {
      width: 6px;
      height: 6px;
    }
    &::-webkit-scrollbar-thumb {
      border-radius: 99px;
      background-color: rgba(79, 70, 229, 0.12);
      
      .dark & {
        background-color: rgba(56, 189, 248, 0.15);
      }
    }
  }
}

/* 表格内链接 */
.link-type {
  color: #4f46e5;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  transition: color 0.2s;

  &:hover {
    color: #4338ca;
    text-decoration: underline;
  }
}

/* 表格操作按钮 */
:deep(.small-padding) {
  .el-button {
    font-weight: 700;

    &[type="primary"],
    &.el-button--primary {
      color: #4f46e5 !important;

      &:hover {
        color: #4338ca !important;
      }
    }
  }
}

/* ===== 分页样式 ===== */
:deep(.pagination-container) {
  padding: 16px 0 0 0 !important;
  background: transparent !important;
}



/* ===== 雷达多层光波呼吸灯 ===== */
.status-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: center;
}

.clickable-status {
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.2s ease;
  user-select: none;
  display: inline-flex;
  align-items: center;

  &:hover {
    background-color: rgba(79, 70, 229, 0.05);
    transform: scale(1.05);
  }
}

.status-label {
  font-size: 12px;
  font-weight: 700;
}

.text-active {
  color: #10b981;
}

.text-error {
  color: #ef4444;
}

.pulse-light-ripple {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  position: relative;
  display: inline-block;
  
  &::before, &::after {
    content: '';
    position: absolute;
    inset: -4px;
    border-radius: 50%;
    opacity: 0;
  }
  
  &.pulse-active {
    background-color: #10b981;
    box-shadow: 0 0 6px #10b981;
    &::before { background-color: #10b981; animation: pulse-radar 2s infinite ease-out; }
    &::after { background-color: #10b981; animation: pulse-radar 2s infinite ease-out 1s; }
  }
  
  &.pulse-error {
    background-color: #ef4444;
    box-shadow: 0 0 8px #ef4444;
    &::before { background-color: #ef4444; animation: pulse-radar 1.3s infinite ease-out; }
    &::after { background-color: #ef4444; animation: pulse-radar 1.3s infinite ease-out 0.65s; }
  }
}

@keyframes pulse-radar {
  0% {
    transform: scale(0.5);
    opacity: 0.8;
  }
  100% {
    transform: scale(2.2);
    opacity: 0;
  }
}
</style>
