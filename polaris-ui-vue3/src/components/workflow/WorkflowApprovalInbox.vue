<template>
  <div class="approval-inbox">
    <header class="approval-header">
      <div>
        <el-button link icon="Back" @click="$emit('back')">返回工作流</el-button>
        <h2>工作流审批箱</h2>
        <p>审批决定持久化后，执行才会从安全检查点继续。</p>
      </div>
      <div class="approval-filters">
        <el-select v-model="status" style="width: 150px" @change="loadTasks">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
          <el-option label="已超时" value="EXPIRED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
        <el-button icon="Refresh" :loading="loading" @click="loadTasks">刷新</el-button>
      </div>
    </header>

    <el-table v-loading="loading" :data="tasks" class="polaris-el-table">
      <el-table-column prop="approvalTaskId" label="审批任务" min-width="250" show-overflow-tooltip />
      <el-table-column prop="executionId" label="执行 ID" min-width="250" show-overflow-tooltip />
      <el-table-column label="审批策略" width="170">
        <template #default="{row}">
          {{ row.assigneeType }} / {{ row.approvalMode }}
        </template>
      </el-table-column>
      <el-table-column prop="deadline" label="截止时间" width="180" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{row}">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{row}">
          <template v-if="row.status === 'PENDING'">
            <el-button link type="success" @click="openDecision(row, 'APPROVE')">通过</el-button>
            <el-button link type="danger" @click="openDecision(row, 'REJECT')">拒绝</el-button>
          </template>
          <span v-else>已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogOpen" class="workflow-dialog" :title="decision === 'APPROVE' ? '通过审批' : '拒绝审批'" width="520px">
      <el-alert
        :type="decision === 'APPROVE' ? 'success' : 'warning'"
        :closable="false"
        show-icon
        title="提交后不可撤销；多人审批未达到阈值时，任务会继续等待。"
      />
      <el-input v-model="comment" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="审批意见（可选）" />
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button :type="decision === 'APPROVE' ? 'success' : 'danger'" :loading="deciding" @click="submitDecision">
          确认{{ decision === 'APPROVE' ? '通过' : '拒绝' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {decideWorkflowApproval, listWorkflowApprovals} from '@/api/ai/workflow'

export default {
  name: 'WorkflowApprovalInbox',
  emits: ['back'],
  data() {
    return {
      loading: false,
      deciding: false,
      status: 'PENDING',
      tasks: [],
      selectedTask: null,
      decision: 'APPROVE',
      comment: '',
      dialogOpen: false
    }
  },
  created() {
    this.loadTasks()
  },
  methods: {
    async loadTasks() {
      this.loading = true
      try {
        const response = await listWorkflowApprovals(this.status)
        this.tasks = response.data || []
      } finally {
        this.loading = false
      }
    },
    openDecision(task, decision) {
      this.selectedTask = task
      this.decision = decision
      this.comment = ''
      this.dialogOpen = true
    },
    async submitDecision() {
      this.deciding = true
      try {
        const response = await decideWorkflowApproval(this.selectedTask.approvalTaskId, {
          decision: this.decision,
          comment: this.comment || null,
          expectedLockVersion: this.selectedTask.lockVersion
        })
        if (response.data?.status === 'EXPIRED') {
          this.$message.warning('审批任务已超时，工作流已终止')
        } else {
          this.$message.success('审批决定已保存')
        }
        this.dialogOpen = false
        await this.loadTasks()
      } finally {
        this.deciding = false
      }
    },
    statusType(status) {
      return {
        PENDING: 'warning',
        APPROVED: 'success',
        REJECTED: 'danger',
        EXPIRED: 'info',
        CANCELLED: 'info'
      }[status] || 'info'
    }
  }
}
</script>

<style scoped>
.approval-inbox {
  padding: 24px;
  border: 1px solid var(--workflow-border, var(--el-border-color-light));
  border-radius: var(--workflow-radius, 18px);
  background: var(--workflow-surface, var(--el-bg-color));
  box-shadow: var(--workflow-shadow, none);
}

.approval-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.approval-header h2 {
  margin: 10px 0 6px;
}

.approval-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.approval-filters {
  display: flex;
  gap: 8px;
}

.el-alert {
  margin-bottom: 16px;
}
</style>
