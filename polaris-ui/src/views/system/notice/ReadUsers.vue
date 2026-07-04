<template>
  <el-dialog :title="`「${noticeTitle}」已读用户`" :visible.sync="visible" append-to-body top="6vh" width="760px" @close="handleClose">
    <el-form ref="queryForm" :inline="true" :model="queryParams" size="small" style="margin-bottom: 4px;">
      <el-form-item prop="searchValue">
        <el-input
          v-model="queryParams.searchValue"
          clearable
          placeholder="登录名称 / 用户名称"
          prefix-icon="el-icon-search"
          style="width: 220px;"
          @clear="handleQuery"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button icon="el-icon-search" size="mini" type="primary" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
      <el-form-item style="float: right; margin-right: 0;">
        <span class="read-stat">
          共 <strong>{{ total }}</strong> 人已读
        </span>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="userList" height="340px" size="small" stripe>
      <el-table-column align="center" label="序号" type="index" width="55" />
      <el-table-column :show-overflow-tooltip="true" align="center" label="登录名称" prop="userName" />
      <el-table-column :show-overflow-tooltip="true" align="center" label="用户名称" prop="nickName" />
      <el-table-column :show-overflow-tooltip="true" align="center" label="所属部门" prop="deptName" />
      <el-table-column align="center" label="手机号码" prop="phonenumber" width="120" />
      <el-table-column align="center" label="阅读时间" prop="readTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.readTime) }}</span>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :limit.sync="queryParams.pageSize" :page.sync="queryParams.pageNum" :total="total" style="padding: 6px 0px;" @pagination="getList"/>
  </el-dialog>
</template>

<script>
import { listNoticeReadUsers } from "@/api/system/notice"

export default {
  name: "ReadUsers",
  data() {
    return {
      visible: false,
      loading: false,
      noticeId: undefined,
      noticeTitle: "",
      total: 0,
      userList: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        noticeId: undefined,
        searchValue: undefined
      }
    }
  },
  methods: {
    open(row) {
      this.noticeId = row.noticeId
      this.noticeTitle = row.noticeTitle
      this.queryParams.noticeId = row.noticeId
      this.queryParams.searchValue = undefined
      this.queryParams.pageNum = 1
      this.visible = true
      this.getList()
    },
    getList() {
      this.loading = true
      listNoticeReadUsers(this.queryParams).then(res => {
        this.userList = res.rows
        this.total = res.total
      }).finally(() => {
        this.loading = false
      })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleClose() {
      this.userList = []
      this.total = 0
      this.queryParams.searchValue = undefined
    }
  }
}
</script>

<style scoped>
.read-stat {
  font-size: 13px;
  color: #606266;
  line-height: 28px;
}
.read-stat strong {
  color: #409eff;
  font-size: 15px;
  margin: 0 2px;
}
</style>
