<template>
  <div class="app-container tree-sidebar-manage-wrap">
    <tree-panel title="组织机构" :tree-data="deptOptions" search-placeholder="请输入部门名称" storage-key="dept-sidebar-width" :defaultExpandAll="true" @node-click="handleNodeClick" @refresh="getDeptTree" ref="deptTreeRef" />
    <div class="tree-sidebar-content">
      <div class="content-inner">
        <!-- 搜索过滤器玻璃卡片 -->
        <div class="polaris-filter-card" v-show="showSearch">
          <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="68px" class="polaris-filter-form">
            <el-form-item label="用户名称" prop="userName">
              <el-input v-model="queryParams.userName" placeholder="请输入用户名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="手机号码" prop="phonenumber">
              <el-input v-model="queryParams.phonenumber" placeholder="请输入手机号码" clearable style="width: 240px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="用户状态" clearable style="width: 240px">
                <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="创建时间" style="width: 308px">
              <el-date-picker v-model="dateRange" value-format="YYYY-MM-DD" type="daterange" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期"></el-date-picker>
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
              <el-button type="primary" icon="Plus" @click="handleAdd" v-hasPermi="['system:user:add']" class="action-btn-primary">新增</el-button>
              <el-button icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['system:user:edit']" class="action-btn-secondary">修改</el-button>
              <el-button type="danger" icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['system:user:remove']" class="action-btn-danger">删除</el-button>
              <el-button icon="Upload" @click="handleImport" v-hasPermi="['system:user:import']" class="action-btn-secondary">导入</el-button>
              <el-button icon="Download" @click="handleExport" v-hasPermi="['system:user:export']" class="action-btn-secondary">导出</el-button>
            </div>
            <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" :columns="columns" storageKey="xxxxxxxx"></right-toolbar>
          </div>

          <el-table v-loading="loading" :data="userList" @selection-change="handleSelectionChange" class="polaris-el-table" :key="tableKey">
            <el-table-column type="selection" width="50" align="center" />
            <el-table-column label="用户编号" align="center" key="userId" prop="userId" v-if="columns.userId.visible" />
            <el-table-column label="用户名称" align="center" key="userName" v-if="columns.userName.visible" :show-overflow-tooltip="true">
              <template #default="scope">
                <a class="link-type" @click="handleViewData(scope.row)">{{ scope.row.userName }}</a>
              </template>
           </el-table-column>
            <el-table-column label="用户昵称" align="center" key="nickName" prop="nickName" v-if="columns.nickName.visible" :show-overflow-tooltip="true" />
            <el-table-column label="部门" align="center" key="deptName" prop="dept.deptName" v-if="columns.deptName.visible" :show-overflow-tooltip="true" />
            <el-table-column label="手机号码" align="center" key="phonenumber" prop="phonenumber" v-if="columns.phonenumber.visible" width="120" />
            <el-table-column label="状态" align="center" key="status" v-if="columns.status.visible" width="100">
              <template #default="scope">
                <div class="status-cell clickable-status" @click="handleStatusChange(scope.row)">
                  <span :class="['pulse-light-ripple', scope.row.status === '0' ? 'pulse-active' : 'pulse-error']"></span>
                  <span class="status-label" :class="scope.row.status === '0' ? 'text-active' : 'text-error'">{{ scope.row.status === '0' ? '正常' : '停用' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" align="center" prop="createTime" v-if="columns.createTime.visible" width="160">
              <template #default="scope">
                <span>{{ parseTime(scope.row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-tooltip content="修改" placement="top" v-if="scope.row.userId !== 1">
                  <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:user:edit']"></el-button>
                </el-tooltip>
                <el-tooltip content="删除" placement="top" v-if="scope.row.userId !== 1">
                  <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:user:remove']"></el-button>
                </el-tooltip>
                <el-tooltip content="重置密码" placement="top" v-if="scope.row.userId !== 1">
                  <el-button link type="primary" icon="Key" @click="handleResetPwd(scope.row)" v-hasPermi="['system:user:resetPwd']"></el-button>
                </el-tooltip>
                <el-tooltip content="分配角色" placement="top" v-if="scope.row.userId !== 1">
                  <el-button link type="primary" icon="CircleCheck" @click="handleAuthRole(scope.row)" v-hasPermi="['system:user:edit']"></el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
          <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
        </div>
      </div>
    </div>

    <!-- 添加或修改用户配置对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body class="polaris-glass-dialog">
      <el-form :model="form" :rules="rules" ref="userRef" label-width="80px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="用户昵称" prop="nickName">
              <el-input v-model="form.nickName" placeholder="请输入用户昵称" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="归属部门" prop="deptId">
              <el-tree-select v-model="form.deptId" :data="enabledDeptOptions" :props="{ value: 'id', label: 'label', children: 'children' }" value-key="id" placeholder="请选择归属部门" clearable check-strictly />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="手机号码" prop="phonenumber">
              <el-input v-model="form.phonenumber" placeholder="请输入手机号码" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="请输入邮箱" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item v-if="form.userId == undefined" label="用户名称" prop="userName">
              <el-input v-model="form.userName" placeholder="请输入用户名称" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="form.userId == undefined" label="用户密码" prop="password" :rules="pwdValidator">
              <el-input v-model="form.password" placeholder="请输入用户密码" type="password" maxlength="20" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="用户性别">
              <el-select v-model="form.sex" placeholder="请选择">
                <el-option v-for="dict in sys_user_sex" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-select v-model="form.postIds" multiple placeholder="请选择">
                <el-option v-for="item in postOptions" :key="item.postId" :label="item.postName" :value="item.postId" :disabled="item.status == 1"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色">
              <el-select v-model="form.roleIds" multiple placeholder="请选择">
                <el-option v-for="item in roleOptions" :key="item.roleId" :label="item.roleName" :value="item.roleId" :disabled="item.status == 1"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容"></el-input>
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

    <!-- 用户详情抽屉 -->
    <user-view-drawer ref="userViewRef" />
    <!-- 用户导入对话框 -->
    <excel-import-dialog ref="importUserRef" title="用户导入" action="/system/user/importData" template-action="/system/user/importTemplate" template-file-name="user_template" update-support-label="是否更新已经存在的用户数据" @success="getList" />
  </div>
</template>

<script setup name="User">
import TreePanel from "@/components/TreePanel"
import ExcelImportDialog from "@/components/ExcelImportDialog"
import UserViewDrawer from "./view"
import {usePasswordRule} from "@/utils/passwordRule"
import {
  addUser,
  changeUserStatus,
  delUser,
  deptTreeSelect,
  getUser,
  listUser,
  resetUserPwd,
  updateUser
} from "@/api/system/user"

const router = useRouter()
const { proxy } = getCurrentInstance()
const { pwdValidator, pwdPromptValidator } = usePasswordRule()
const { sys_normal_disable, sys_user_sex } = useDict("sys_normal_disable", "sys_user_sex")

const userList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const dateRange = ref([])
const deptOptions = ref(undefined)
const enabledDeptOptions = ref(undefined)
const initPassword = ref(undefined)
const postOptions = ref([])
const roleOptions = ref([])
// 列显隐信息
const columns = ref({
  userId: { label: '用户编号', visible: true },
  userName: { label: '用户名称', visible: true },
  nickName: { label: '用户昵称', visible: true },
  deptName: { label: '部门', visible: true },
  phonenumber: { label: '手机号码', visible: true },
  status: { label: '状态', visible: true },
  createTime: { label: '创建时间', visible: true }
})
// 动态 key：列显隐变化时强制 el-table 重新渲染，解决列宽错乱问题
const tableKey = computed(() => Object.values(columns.value).map(c => c.visible ? '1' : '0').join(''))

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    userName: undefined,
    phonenumber: undefined,
    status: undefined,
    deptId: undefined
  },
  rules: {
    userName: [{ required: true, message: "用户名称不能为空", trigger: "blur" }, { min: 2, max: 20, message: "用户名称长度必须介于 2 和 20 之间", trigger: "blur" }],
    nickName: [{ required: true, message: "用户昵称不能为空", trigger: "blur" }],
    email: [{ type: "email", message: "请输入正确的邮箱地址", trigger: ["blur", "change"] }],
    phonenumber: [{ pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/, message: "请输入正确的手机号码", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询用户列表 */
function getList() {
  loading.value = true
  listUser(proxy.addDateRange(queryParams.value, dateRange.value)).then(res => {
    loading.value = false
    userList.value = res.rows
    total.value = res.total
  })
}

/** 查询部门下拉树结构 */
function getDeptTree() {
  deptTreeSelect().then(response => {
    deptOptions.value = response.data
    enabledDeptOptions.value = filterDisabledDept(JSON.parse(JSON.stringify(response.data)))
  })
}

/** 过滤禁用的部门 */
function filterDisabledDept(deptList) {
  return deptList.filter(dept => {
    if (dept.disabled) {
      return false
    }
    if (dept.children && dept.children.length) {
      dept.children = filterDisabledDept(dept.children)
    }
    return true
  })
}

/** 节点单击事件 */
function handleNodeClick(data) {
  queryParams.value.deptId = data.id
  handleQuery()
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
  queryParams.value.deptId = undefined
  proxy.$refs.deptTreeRef.setCurrentKey(null)
  handleQuery()
}

/** 删除按钮操作 */
function handleDelete(row) {
  const userIds = row.userId || ids.value
  proxy.$modal.confirm('是否确认删除用户编号为"' + userIds + '"的数据项？').then(function () {
    return delUser(userIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("system/user/export", {
    ...queryParams.value,
  },`user_${new Date().getTime()}.xlsx`)
}

/** 用户状态修改  */
function handleStatusChange(row) {
  let text = row.status === "0" ? "启用" : "停用"
  proxy.$modal.confirm('确认要"' + text + '""' + row.userName + '"用户吗?').then(function () {
    return changeUserStatus(row.userId, row.status)
  }).then(() => {
    proxy.$modal.msgSuccess(text + "成功")
  }).catch(function () {
    row.status = row.status === "0" ? "1" : "0"
  })
}

/** 更多操作 */
function handleCommand(command, row) {
  switch (command) {
    case "handleResetPwd":
      handleResetPwd(row)
      break
    case "handleAuthRole":
      handleAuthRole(row)
      break
    default:
      break
  }
}

/** 跳转角色分配 */
function handleAuthRole(row) {
  const userId = row.userId
  router.push("/system/user-auth/role/" + userId)
}

/** 重置密码按钮操作 */
function handleResetPwd(row) {
  proxy.$prompt(`请输入「${row.userName}」的新密码`, "重置密码", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    closeOnClickModal: false,
    inputValidator: pwdPromptValidator
  }).then(({ value }) => {
    resetUserPwd(row.userId, value).then(() => {
      proxy.$modal.msgSuccess("修改成功，新密码是：" + value)
    })
  }).catch(() => {})
}

/** 选择条数  */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.userId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 详情按钮操作 */
function handleViewData(row) {
  proxy.$refs["userViewRef"].open(row.userId)
}

/** 导入按钮操作 */
function handleImport() {
  proxy.$refs["importUserRef"].open()
}

/** 重置操作表单 */
function reset() {
  form.value = {
    userId: undefined,
    deptId: undefined,
    userName: undefined,
    nickName: undefined,
    password: undefined,
    phonenumber: undefined,
    email: undefined,
    sex: undefined,
    status: "0",
    remark: undefined,
    postIds: [],
    roleIds: []
  }
  proxy.resetForm("userRef")
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  getUser().then(response => {
    postOptions.value = response.posts
    roleOptions.value = response.roles
    open.value = true
    title.value = "添加用户"
    form.value.password = initPassword.value
  })
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const userId = row.userId || ids.value
  getUser(userId).then(response => {
    form.value = response.data
    postOptions.value = response.posts
    roleOptions.value = response.roles
    form.value.postIds = response.postIds
    form.value.roleIds = response.roleIds
    open.value = true
    title.value = "修改用户"
    form.value.password = ""
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["userRef"].validate(valid => {
    if (valid) {
      if (form.value.userId != undefined) {
        updateUser(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addUser(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

onMounted(() => {
  getDeptTree()
  getList()
  proxy.getConfigKey("sys.user.initPassword").then(response => {
    initPassword.value = response.msg
  })
})
</script>

<style lang="scss" scoped>
/* ==========================================================================
   北辰 Polaris 用户管理页面设计系统 (参照 Demo AgentMatrix 风格)
   ========================================================================== */

/* ===== 整体容器间距 ===== */
.tree-sidebar-manage-wrap {
  gap: 16px !important;
  padding: 16px !important;
  background: transparent !important;
  height: 100%;
}

/* 覆盖全局 .app-container 的 padding: 20px */
.app-container.tree-sidebar-manage-wrap {
  padding: 16px !important;
}

/* ===== 右侧内容区背景透明化 ===== */
.tree-sidebar-content {
  background: transparent !important;
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

/* ===== 对话框毛玻璃 ===== */
:deep(.polaris-glass-dialog) {
  border-radius: 20px !important;
  backdrop-filter: blur(40px) !important;
  background: rgba(255, 255, 255, 0.8) !important;
  border: 1px solid rgba(255, 255, 255, 0.5) !important;
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.08) !important;
  
  .dark & {
    background: rgba(15, 23, 42, 0.85) !important;
    border-color: rgba(255, 255, 255, 0.06) !important;
    box-shadow: 0 25px 60px rgba(0, 0, 0, 0.45) !important;
  }

  .el-dialog__header {
    padding: 20px 24px 14px;
    border-bottom: 1px solid rgba(0, 0, 0, 0.04);
    
    .dark & {
      border-bottom-color: rgba(255, 255, 255, 0.06);
    }
  }

  .el-dialog__title {
    font-size: 15px;
    font-weight: 800;
    letter-spacing: 0.02em;
    color: #0f172a;
    
    .dark & {
      color: #ffffff;
    }
  }

  .el-dialog__headerbtn {
    .el-dialog__close {
      font-size: 16px;
      transition: transform 0.3s;

      &:hover {
        transform: rotate(90deg) scale(1.1);
        color: #4f46e5;
        
        .dark & {
          color: #38bdf8;
        }
      }
    }
  }

  .el-dialog__body {
    padding: 20px 24px;
  }

  .el-form-item__label {
    font-size: 12px;
    font-weight: 700;
    color: #475569;
    
    .dark & {
      color: #cbd5e1;
    }
  }

  .el-input__wrapper,
  .el-select__wrapper {
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

  .el-textarea__inner {
    border-radius: 10px !important;
    background-color: #ffffff !important;
    border: 1px solid rgba(0, 0, 0, 0.08) !important;
    box-shadow: none !important;
    transition: border-color 0.3s;

    &:hover,
    &:focus {
      border-color: rgba(79, 70, 229, 0.4) !important;
    }
    
    .dark & {
      background-color: rgba(0, 0, 0, 0.35) !important;
      border-color: rgba(255, 255, 255, 0.08) !important;
      
      &:hover,
      &:focus {
        border-color: rgba(56, 189, 248, 0.4) !important;
      }
    }
  }

  .el-input__inner {
    font-size: 13px;
    color: #0f172a;
    
    .dark & {
      color: #cbd5e1;
    }
  }

  .el-radio-group {
    .el-radio {
      font-weight: 600;
    }
  }

  .el-tree-select {
    width: 100%;
  }
}

/* 对话框底部按钮 */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;

  .el-button {
    height: 36px;
    border-radius: 10px !important;
    font-size: 12px;
    font-weight: 700;
    transition: all 0.3s;
  }

  .el-button--primary {
    background-color: #4f46e5 !important;
    border-color: #4f46e5 !important;
    
    .dark & {
      background-color: #38bdf8 !important;
      border-color: #38bdf8 !important;
      color: #0f172a !important;
    }

    &:hover {
      background-color: #4338ca !important;
      box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2);
      
      .dark & {
        background-color: #0ea5e9 !important;
        box-shadow: 0 4px 12px rgba(56, 189, 248, 0.4);
      }
    }
  }

  .el-button:not(.el-button--primary) {
    border-color: rgba(0, 0, 0, 0.08) !important;
    background-color: transparent !important;
    color: #475569 !important;
    
    .dark & {
      border-color: rgba(255, 255, 255, 0.1) !important;
      color: #cbd5e1 !important;
    }

    &:hover {
      background-color: rgba(0, 0, 0, 0.02) !important;
      
      .dark & {
        background-color: rgba(255, 255, 255, 0.04) !important;
      }
    }
  }
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
