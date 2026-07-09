<template>
  <div class="app-container tree-sidebar-manage-wrap">
    <tree-panel ref="deptTreeRef" :defaultExpandAll="true" :tree-data="deptOptions" search-placeholder="请输入部门名称" storage-key="dept-sidebar-width" title="组织机构" @refresh="getDeptTree" @node-click="handleNodeClick" />
    <div class="tree-sidebar-content">
      <div class="content-inner">
        <div class="polaris-table-card">
          <!-- 🔍 顶部条件筛选区域 -->
          <div v-show="showSearch" class="search-section">
            <el-form ref="queryForm" :inline="true" :model="queryParams" label-width="68px" size="small">
              <el-form-item label="用户名称" prop="userName">
                <el-input v-model="queryParams.userName" clearable placeholder="请输入用户名称" style="width: 150px" @keyup.enter.native="handleQuery" />
              </el-form-item>
              <el-form-item label="手机号码" prop="phonenumber">
                <el-input v-model="queryParams.phonenumber" clearable placeholder="请输入手机号码" style="width: 150px" @keyup.enter.native="handleQuery" />
              </el-form-item>
              <el-form-item label="状态" prop="status">
                <el-select v-model="queryParams.status" clearable placeholder="用户状态" style="width: 120px">
                  <el-option v-for="dict in dict.type.sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="创建时间">
                <el-date-picker v-model="dateRange" end-placeholder="结束日期" range-separator="-" start-placeholder="开始日期" style="width: 240px" type="daterange" value-format="yyyy-MM-dd"></el-date-picker>
              </el-form-item>
              <el-form-item class="search-btn-group">
                <el-button icon="el-icon-search" size="mini" type="primary" @click="handleQuery">搜索</el-button>
                <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
              </el-form-item>
            </el-form>
            <el-divider class="polaris-divider" />
          </div>

          <!-- ⚙️ 操作按钮与表格内容区域 -->
          <div class="table-section">
            <el-row :gutter="10" class="mb8">
              <el-col :span="1.5">
                <el-button v-hasPermi="['system:user:add']" icon="el-icon-plus" plain size="mini" type="primary" @click="handleAdd">新增</el-button>
              </el-col>
              <el-col :span="1.5">
                <el-button v-hasPermi="['system:user:edit']" :disabled="single" icon="el-icon-edit" plain size="mini" type="success" @click="handleUpdate">修改</el-button>
              </el-col>
              <el-col :span="1.5">
                <el-button v-hasPermi="['system:user:remove']" :disabled="multiple" icon="el-icon-delete" plain size="mini" type="danger" @click="handleDelete">删除</el-button>
              </el-col>
              <el-col :span="1.5">
                <el-button v-hasPermi="['system:user:import']" icon="el-icon-upload2" plain size="mini" type="info" @click="handleImport">导入</el-button>
              </el-col>
              <el-col :span="1.5">
                <el-button v-hasPermi="['system:user:export']" icon="el-icon-download" plain size="mini" type="warning" @click="handleExport">导出</el-button>
              </el-col>
              <right-toolbar :columns="columns" :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
            </el-row>

            <el-table v-loading="loading" :data="userList" @selection-change="handleSelectionChange">
              <el-table-column align="center" type="selection" width="50" />
              <el-table-column v-if="columns.userId.visible" key="userId" align="center" label="用户编号" prop="userId" />
              <el-table-column v-if="columns.userName.visible" key="userName" :show-overflow-tooltip="true" align="center" label="用户名称">
                <template slot-scope="scope">
                  <a class="link-type" style="cursor:pointer" @click="handleViewData(scope.row)">{{ scope.row.userName }}</a>
                </template>
              </el-table-column>
              <el-table-column v-if="columns.nickName.visible" key="nickName" :show-overflow-tooltip="true" align="center" label="用户昵称" prop="nickName" />
              <el-table-column v-if="columns.deptName.visible" key="deptName" :show-overflow-tooltip="true" align="center" label="部门" prop="dept.deptName" />
              <el-table-column v-if="columns.phonenumber.visible" key="phonenumber" align="center" label="手机号码" prop="phonenumber" width="120" />
              <el-table-column v-if="columns.status.visible" key="status" align="center" label="状态">
                <template slot-scope="scope">
                  <el-switch v-model="scope.row.status" active-value="0" inactive-value="1" @change="handleStatusChange(scope.row)"></el-switch>
                </template>
              </el-table-column>
              <el-table-column v-if="columns.createTime.visible" align="center" label="创建时间" prop="createTime" width="160">
                <template slot-scope="scope">
                  <span>{{ parseTime(scope.row.createTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column align="center" class-name="small-padding fixed-width" label="操作" width="160">
                <template v-if="scope.row.userId !== 1" slot-scope="scope">
                  <el-button v-hasPermi="['system:user:edit']" icon="el-icon-edit" size="mini" type="text" @click="handleUpdate(scope.row)">修改</el-button>
                  <el-button v-hasPermi="['system:user:remove']" icon="el-icon-delete" size="mini" type="text" @click="handleDelete(scope.row)">删除</el-button>
                  <el-dropdown v-hasPermi="['system:user:resetPwd', 'system:user:edit']" size="mini" @command="(command) => handleCommand(command, scope.row)">
                    <el-button icon="el-icon-d-arrow-right" size="mini" type="text">更多</el-button>
                    <el-dropdown-menu slot="dropdown">
                      <el-dropdown-item v-hasPermi="['system:user:resetPwd']" command="handleResetPwd" icon="el-icon-key">重置密码</el-dropdown-item>
                      <el-dropdown-item v-hasPermi="['system:user:edit']" command="handleAuthRole" icon="el-icon-circle-check">分配角色</el-dropdown-item>
                    </el-dropdown-menu>
                  </el-dropdown>
                </template>
              </el-table-column>
            </el-table>
            <pagination v-show="total > 0" :limit.sync="queryParams.pageSize" :page.sync="queryParams.pageNum" :total="total" @pagination="getList" />
          </div>
        </div>
      </div>
    </div>

    <!-- 添加或修改用户配置对话框 -->
    <el-dialog :title="title" :visible.sync="open" append-to-body custom-class="polaris-form-dialog" width="600px">
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="用户昵称" prop="nickName">
              <el-input v-model="form.nickName" maxlength="30" placeholder="请输入用户昵称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="归属部门" prop="deptId">
              <treeselect v-model="form.deptId" :options="enabledDeptOptions" :show-count="true" placeholder="请选择归属部门" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="手机号码" prop="phonenumber">
              <el-input v-model="form.phonenumber" maxlength="11" placeholder="请输入手机号码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" maxlength="50" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item v-if="form.userId == undefined" label="用户名称" prop="userName">
              <el-input v-model="form.userName" maxlength="30" placeholder="请输入用户名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="form.userId == undefined" :rules="pwdValidator" label="用户密码" prop="password">
              <el-input v-model="form.password" maxlength="20" placeholder="请输入用户密码" show-password type="password" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="用户性别">
              <el-select v-model="form.sex" placeholder="请选择性别">
                <el-option v-for="dict in dict.type.sys_user_sex" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in dict.type.sys_normal_disable" :key="dict.value" :label="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-select v-model="form.postIds" multiple placeholder="请选择岗位">
                <el-option v-for="item in postOptions" :key="item.postId" :disabled="item.status == 1" :label="item.postName" :value="item.postId" ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色">
              <el-select v-model="form.roleIds" multiple placeholder="请选择角色">
                <el-option v-for="item in roleOptions" :key="item.roleId" :disabled="item.status == 1" :label="item.roleName" :value="item.roleId"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="请输入内容" type="textarea"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 用户详情抽屉 -->
    <user-view-drawer ref="userViewRef" />
    <!-- 用户导入对话框 -->
    <excel-import-dialog ref="importUserRef" action="/system/user/importData" template-action="/system/user/importTemplate" template-file-name="user_template" title="用户导入" update-support-label="是否更新已经存在的用户数据" @success="getList" />
  </div>
</template>

<script>
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
import Treeselect from "@riophae/vue-treeselect"
import "@riophae/vue-treeselect/dist/vue-treeselect.css"
import TreePanel from "@/components/TreePanel"
import ExcelImportDialog from "@/components/ExcelImportDialog"
import UserViewDrawer from "./view"
import passwordRule from "@/utils/passwordRule"

export default {
  name: "User",
  mixins: [passwordRule],
  dicts: ['sys_normal_disable', 'sys_user_sex'],
  components: { Treeselect, TreePanel, ExcelImportDialog, UserViewDrawer },
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 用户表格数据
      userList: null,
      // 弹出层标题
      title: "",
      // 所有部门树选项
      deptOptions: undefined,
      // 过滤掉已禁用部门树选项
      enabledDeptOptions: undefined,
      // 是否显示弹出层
      open: false,
      // 默认密码
      initPassword: undefined,
      // 日期范围
      dateRange: [],
      // 岗位选项
      postOptions: [],
      // 角色选项
      roleOptions: [],
      // 表单参数
      form: {},
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        userName: undefined,
        phonenumber: undefined,
        status: undefined,
        deptId: undefined
      },
      // 列信息
      columns: {
        userId: { label: '用户编号', visible: true },
        userName: { label: '用户名称', visible: true },
        nickName: { label: '用户昵称', visible: true },
        deptName: { label: '部门', visible: true },
        phonenumber: { label: '手机号码', visible: true },
        status: { label: '状态', visible: true },
        createTime: { label: '创建时间', visible: true }
      },
      // 表单校验
      rules: {
        userName: [
          { required: true, message: "用户名称不能为空", trigger: "blur" },
          { min: 2, max: 20, message: '用户名称长度必须介于 2 和 20 之间', trigger: 'blur' }
        ],
        nickName: [
          { required: true, message: "用户昵称不能为空", trigger: "blur" }
        ],
        email: [
          {
            type: "email",
            message: "请输入正确的邮箱地址",
            trigger: ["blur", "change"]
          }
        ],
        phonenumber: [
          {
            pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/,
            message: "请输入正确的手机号码",
            trigger: "blur"
          }
        ]
      }
    }
  },
  created() {
    this.getList()
    this.getDeptTree()
    this.getConfigKey("sys.user.initPassword").then(response => {
      this.initPassword = response.msg
    })
  },
  methods: {
    /** 查询用户列表 */
    getList() {
      this.loading = true
      listUser(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
        this.userList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    /** 查询部门下拉树结构 */
    getDeptTree() {
      deptTreeSelect().then(response => {
        this.deptOptions = response.data
        this.enabledDeptOptions = this.filterDisabledDept(JSON.parse(JSON.stringify(response.data)))
      })
    },
    // 过滤禁用的部门
    filterDisabledDept(deptList) {
      return deptList.filter(dept => {
        if (dept.disabled) {
          return false
        }
        if (dept.children && dept.children.length) {
          dept.children = this.filterDisabledDept(dept.children)
        }
        return true
      })
    },
    // 节点单击事件
    handleNodeClick(data) {
      this.queryParams.deptId = data.id
      this.handleQuery()
    },
    // 用户状态修改
    handleStatusChange(row) {
      let text = row.status === "0" ? "启用" : "停用"
      this.$modal.confirm('确认要"' + text + '""' + row.userName + '"用户吗？').then(function() {
        return changeUserStatus(row.userId, row.status)
      }).then(() => {
        this.$modal.msgSuccess(text + "成功")
      }).catch(function() {
        row.status = row.status === "0" ? "1" : "0"
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
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
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.dateRange = []
      this.resetForm("queryForm")
      this.queryParams.deptId = undefined
      this.$refs.deptTreeRef.setCurrentKey(null)
      this.handleQuery()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.userId)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    // 更多操作触发
    handleCommand(command, row) {
      switch (command) {
        case "handleResetPwd":
          this.handleResetPwd(row)
          break
        case "handleAuthRole":
          this.handleAuthRole(row)
          break
        default:
          break
      }
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      getUser().then(response => {
        this.postOptions = response.posts
        this.roleOptions = response.roles
        this.open = true
        this.title = "添加用户"
        this.form.password = this.initPassword
      })
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const userId = row.userId || this.ids
      getUser(userId).then(response => {
        this.form = response.data
        this.postOptions = response.posts
        this.roleOptions = response.roles
        this.$set(this.form, "postIds", response.postIds)
        this.$set(this.form, "roleIds", response.roleIds)
        this.open = true
        this.title = "修改用户"
        this.form.password = ""
      })
    },
    /** 重置密码按钮操作 */
    handleResetPwd(row) {
      this.$prompt(`请输入「${row.userName}」的新密码`, "重置密码", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        closeOnClickModal: false,
        inputValidator: this.pwdPromptValidator
      }).then(({ value }) => {
        resetUserPwd(row.userId, value).then(() => {
          this.$modal.msgSuccess("修改成功，新密码是：" + value)
        })
      }).catch(() => {})
    },
    /** 分配角色操作 */
    handleAuthRole(row) {
      const userId = row.userId
      this.$router.push("/system/user-auth/role/" + userId)
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.userId != undefined) {
            updateUser(this.form).then(() => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addUser(this.form).then(() => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const userIds = row.userId || this.ids
      this.$modal.confirm('是否确认删除用户编号为"' + userIds + '"的数据项？').then(function() {
        return delUser(userIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('system/user/export', {
        ...this.queryParams
      }, `user_${new Date().getTime()}.xlsx`)
    },
    /** 详情按钮操作 */
    handleViewData(row) {
      this.$refs.userViewRef.open(row.userId)
    },
    /** 导入按钮操作 */
    handleImport() {
      this.$refs.importUserRef.open()
    }
  }
}
</script>
