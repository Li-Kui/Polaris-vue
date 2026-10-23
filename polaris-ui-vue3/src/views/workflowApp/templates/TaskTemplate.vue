<template>
  <div>
    <RuntimeWorkflowRunner ref="runner" :manifest="manifest" :share-code="shareCode" submit-text="提交任务" input-title="任务参数" result-title="任务结果"
      @started="recordTask" @settled="recordTask" @completed="emit('completed', $event)">
      <template #default="{ result }"><RuntimeResult :output="runtimeTaskResult(result)" /></template>
    </RuntimeWorkflowRunner>
    <section class="task-history">
      <div class="task-heading"><h2>最近任务</h2><span>仅在当前浏览器标签页保留最近 10 条，刷新后可恢复状态</span></div>
      <ol v-if="tasks.length" class="task-list" aria-label="最近任务">
        <li v-for="(row, index) in tasks" :key="row.executionId" class="task-item">
          <span class="task-status-icon" :class="`is-${statusType(row.status)}`" aria-hidden="true">
            <el-icon :class="{ 'is-loading': row.status === 'RUNNING' }"><component :is="statusIcon(row.status)" /></el-icon>
          </span>
          <div class="task-summary"><strong>任务 {{ row.taskNumber || tasks.length - index }}</strong><time>{{ formatTime(row.createTime) }}</time></div>
          <div class="task-item-actions">
            <el-tag :type="statusType(row.status)" size="small" :title="row.status">{{ statusLabels[row.status] || '处理中' }}</el-tag>
            <el-button link size="small" :disabled="runner?.isActive" @click="runner.resume(row.executionId)">查看 / 恢复</el-button>
          </div>
        </li>
      </ol>
      <el-empty v-else description="还没有任务，填写参数后提交第一个任务" :image-size="56" />
      <p class="task-note">列表状态为最近一次记录，点击“查看 / 恢复”获取最新状态。本页不保存任务输入与结果。</p>
    </section>
  </div>
</template>

<script setup>
import {ref} from 'vue'
import {CircleCheck, CircleClose, Clock, Loading, Warning} from '@element-plus/icons-vue'
import RuntimeWorkflowRunner from '@/components/workflow-app/RuntimeWorkflowRunner.vue'
import RuntimeResult from '@/components/workflow-app/RuntimeResult.vue'
import {runtimeTaskResult} from '@/utils/workflowRuntime'

const props = defineProps({ manifest: { type: Object, required: true }, shareCode: { type: String, required: true } })
const emit = defineEmits(['completed'])
const runner = ref(null)
const storageKey = `polaris:share-tasks:${props.shareCode}`
const readTasks = () => {
  try {
    const stored = JSON.parse(sessionStorage.getItem(storageKey) || '[]')
    return Array.isArray(stored) ? stored.filter(task => task && typeof task.executionId === 'string').slice(0, 10) : []
  } catch { return [] }
}
const tasks = ref(readTasks())
const statusType = status => status === 'SUCCEEDED' ? 'success' : ['FAILED', 'REJECTED'].includes(status) ? 'danger' : status === 'NEEDS_ATTENTION' ? 'warning' : 'info'
const statusIcon = status => status === 'SUCCEEDED' ? CircleCheck : ['FAILED', 'REJECTED'].includes(status) ? CircleClose : status === 'NEEDS_ATTENTION' ? Warning : status === 'RUNNING' ? Loading : Clock
const statusLabels = { NEEDS_ATTENTION: '待确认', RETRY_WAIT: '等待重试', SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消', REJECTED: '已拒绝', QUEUED: '排队中', RUNNING: '运行中', WAITING_TIMER: '等待处理', WAITING_APPROVAL: '等待审批', WAITING_EVENT: '等待处理' }
const recordTask = ({ execution }) => {
  const previous = tasks.value.find(task => task.executionId === execution.executionId)
  const taskNumber = previous?.taskNumber || Math.max(tasks.value.length, ...tasks.value.map(task => task.taskNumber || 0)) + 1
  const record = { executionId: execution.executionId, taskNumber, status: execution.status, createTime: execution.createTime || previous?.createTime || new Date().toISOString() }
  tasks.value = [record, ...tasks.value.filter(task => task.executionId !== execution.executionId)].slice(0, 10)
  try { sessionStorage.setItem(storageKey, JSON.stringify(tasks.value)) } catch { /* 存储被禁用时保留当前页面记录 */ }
}
const formatTime = value => value ? new Date(value).toLocaleString() : '—'
</script>

<style scoped>
.task-history { margin: 0 22px 24px; padding: 24px 0 0; border-top: 1px solid var(--runtime-border); background: var(--runtime-surface, #fff); }
.task-heading { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.task-heading h2 { margin: 0; font-size: 17px; color: var(--runtime-text, #1e293b); }
.task-heading span, .task-note { font-size: 12px; color: var(--runtime-secondary, #64748b); line-height: 1.7; }
.task-note { margin: 14px 0 0; }
.task-list { margin: 0; padding: 0; list-style: none; }
.task-item { display: flex; align-items: center; gap: 10px; padding: 16px 0; border-bottom: 1px solid var(--runtime-border); }
.task-item:last-child { border-bottom: 0; }
.task-status-icon { display: grid; place-items: center; flex: 0 0 32px; height: 32px; border-radius: 10px; color: var(--workflow-primary); background: var(--runtime-primary-soft); font-size: 18px; }
.task-status-icon.is-success { color: var(--workflow-success); background: var(--runtime-success-soft); }
.task-status-icon.is-danger { color: var(--workflow-danger); background: var(--runtime-muted); }
.task-summary { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 6px; }
.task-summary strong { color: var(--runtime-text); font-size: 14px; }
.task-summary time { color: var(--runtime-secondary); font-size: 11px; overflow-wrap: anywhere; }
.task-item-actions { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.task-item-actions :deep(.el-button) { min-height: 32px; padding: 0; margin: 0; font-size: 12px; }
@media (prefers-reduced-motion: reduce) { .is-loading { animation: none; } }
</style>
