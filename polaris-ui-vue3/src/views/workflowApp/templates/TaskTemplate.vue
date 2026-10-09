<template>
  <div>
    <RuntimeWorkflowRunner ref="runner" :manifest="manifest" :share-code="shareCode" submit-text="提交任务" input-title="任务参数" result-title="任务结果"
      @started="recordTask" @settled="recordTask" @completed="emit('completed', $event)">
      <template #default="{ result }"><RuntimeResult :output="runtimeTaskResult(result)" /></template>
    </RuntimeWorkflowRunner>
    <section class="task-history">
      <div class="task-heading"><h2>最近任务</h2><span>仅在当前浏览器标签页保留最近 10 条，刷新后可恢复状态</span></div>
      <el-table v-if="tasks.length" :data="tasks" size="small" max-height="360">
        <el-table-column label="任务" min-width="140"><template #default="{ row, $index }">任务 {{ row.taskNumber || tasks.length - $index }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'SUCCEEDED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'">{{ statusLabels[row.status] || '处理中' }}</el-tag></template></el-table-column>
        <el-table-column label="提交时间" min-width="160"><template #default="{ row }">{{ formatTime(row.createTime) }}</template></el-table-column>
        <el-table-column label="操作" width="110"><template #default="{ row }"><el-button size="small" :disabled="runner?.isActive" @click="runner.resume(row.executionId)">查看 / 恢复</el-button></template></el-table-column>
      </el-table>
      <el-empty v-else description="还没有任务，填写参数后提交第一个任务" :image-size="56" />
      <p class="task-note">列表状态为最近一次记录，点击“查看 / 恢复”获取最新状态。本页不保存任务输入与结果。</p>
    </section>
  </div>
</template>

<script setup>
import {ref} from 'vue'
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
const statusLabels = { SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消', REJECTED: '已拒绝', QUEUED: '排队中', RUNNING: '运行中', WAITING_TIMER: '等待处理', WAITING_APPROVAL: '等待审批', WAITING_EVENT: '等待处理' }
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
.task-history { max-width: 1152px; margin: 0 auto 36px; padding: 24px 28px; border: 1px solid var(--runtime-border, #dbe2ea); border-radius: 20px; background: var(--runtime-surface, #fff); box-shadow: var(--runtime-shadow); }
.task-heading { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.task-heading h2 { margin: 0; font-size: 17px; color: var(--runtime-text, #1e293b); }
.task-heading span, .task-note { font-size: 12px; color: var(--runtime-secondary, #64748b); line-height: 1.7; }
.task-note { margin: 14px 0 0; }
@media (max-width: 800px) { .task-history { margin: 0 14px 20px; padding: 18px; } }
</style>
