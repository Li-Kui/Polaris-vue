<template>
   <div class="app-container no-sidebar-manage-wrap">
      <div class="content-inner">
         <el-form :model="queryParams" ref="queryRef" :inline="true" class="polaris-filter-card polaris-filter-form">
            <el-form-item label="登录地址" prop="ipaddr">
               <el-input
                  v-model="queryParams.ipaddr"
                  placeholder="请输入登录地址"
                  clearable
                  style="width: 240px"
                  @keyup.enter="handleQuery"
               />
            </el-form-item>
            <el-form-item label="用户名称" prop="userName">
               <el-input
                  v-model="queryParams.userName"
                  placeholder="请输入用户名称"
                  clearable
                  style="width: 240px"
                  @keyup.enter="handleQuery"
               />
            </el-form-item>
            <el-form-item>
               <el-button type="primary" icon="Search" @click="handleQuery" class="polaris-query-btn">搜索</el-button>
               <el-button icon="Refresh" @click="resetQuery" class="polaris-reset-btn">重置</el-button>
            </el-form-item>
         </el-form>
      <div class="polaris-table-card">
         <el-table
            v-loading="loading"
            :data="onlineList.slice((pageNum - 1) * pageSize, pageNum * pageSize)"
            style="width: 100%;"
            class="polaris-el-table"
         >
            <el-table-column label="序号" width="100" type="index" align="center">
               <template #default="scope">
                  <span>{{ (pageNum - 1) * pageSize + scope.$index + 1 }}</span>
               </template>
            </el-table-column>
            <el-table-column label="会话编号" align="center" prop="tokenId" :show-overflow-tooltip="true" min-width="150" />
            <el-table-column label="登录名称" align="center" prop="userName" :show-overflow-tooltip="true" min-width="120" />
            <el-table-column label="所属部门" align="center" prop="deptName" :show-overflow-tooltip="true" min-width="120" />
            <el-table-column label="主机" align="center" prop="ipaddr" :show-overflow-tooltip="true" min-width="120" />
            <el-table-column label="登录地点" align="center" prop="loginLocation" :show-overflow-tooltip="true" min-width="120" />
            <el-table-column label="操作系统" align="center" prop="os" :show-overflow-tooltip="true" min-width="100" />
            <el-table-column label="浏览器" align="center" prop="browser" :show-overflow-tooltip="true" min-width="100" />
            <el-table-column label="登录时间" align="center" prop="loginTime" width="180">
               <template #default="scope">
                  <span>{{ parseTime(scope.row.loginTime) }}</span>
               </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="100" class-name="small-padding fixed-width">
               <template #default="scope">
                  <el-tooltip content="强退" placement="top">
                     <el-button link type="primary" icon="Delete" @click="handleForceLogout(scope.row)" v-hasPermi="['monitor:online:forceLogout']"></el-button>
                  </el-tooltip>
               </template>
            </el-table-column>
         </el-table>

         <pagination v-show="total > 0" :total="total" v-model:page="pageNum" v-model:limit="pageSize" />
      </div>
   </div>
</div>
</template>

<script setup name="Online">
import {forceLogout, list as initData} from "@/api/monitor/online"

const { proxy } = getCurrentInstance()

const onlineList = ref([])
const loading = ref(true)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const queryParams = ref({
  ipaddr: undefined,
  userName: undefined
})

/** 查询登录日志列表 */
function getList() {
  loading.value = true
  initData(queryParams.value).then(response => {
    onlineList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  pageNum.value = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 强退按钮操作 */
function handleForceLogout(row) {
  proxy.$modal.confirm('是否确认强退名称为"' + row.userName + '"的用户?').then(function () {
    return forceLogout(row.tokenId)
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
/* 针对北辰在线用户表格的公共组件覆盖（全局非 scoped，但仅对 .polaris-el-table 生效以起隔离保护作用） */
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
