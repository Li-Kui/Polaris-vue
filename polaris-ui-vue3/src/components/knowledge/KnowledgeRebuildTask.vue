<template>
  <section v-if="canManage && task" class="knowledge-rebuild-task" aria-label="知识库索引任务">
    <div class="task-heading">
      <span>最近{{ taskKind }}任务</span>
      <el-tag :type="stateType" effect="plain">{{ stateText }}</el-tag>
      <span v-if="task.totalDocuments > 0">文档进度 {{ task.completedDocuments }} / {{ task.totalDocuments }}（已处理）</span>
      <el-button :loading="loading" size="small" @click="reload">刷新任务</el-button>
      <el-button v-if="task.canRecover" :loading="recovering" size="small" type="warning" @click="recover">确认重新构建</el-button>
    </div>
    <p class="task-id">任务 ID：{{ task.taskId }}</p>
    <p v-if="hint" class="task-hint">{{ hint }}</p>
    <p v-if="loadFailed" class="task-hint">任务状态暂不可用，请刷新；不会自动重新派发。</p>
  </section>
</template>

<script>
import {
  getLatestKnowledgeRebuildTask,
  newKnowledgeRebuildRequestKey,
  recoverKnowledgeRebuildTask
} from '@/api/ai/knowledge'

const labels = {QUEUED: '等待执行', RUNNING: '构建中', SUCCEEDED: '构建成功', FAILED: '构建失败',
  INVALIDATED: '来源已变化', INTERRUPTED: '任务已中断', REJECTED: '未进入队列'}
const hints = {FAILED: '本次构建未完成，请检查文档诊断后重新提交；不会自动重试模型。',
  INVALIDATED: '资料或配置已变化，旧候选未发布。请确认当前配置后重新提交。',
  INTERRUPTED: '任务租约已过期，远端请求结果可能未知。确认后将创建新任务并从头构建，可能再次产生向量模型费用。',
  REJECTED: '队列已满或暂不可用，本次任务未运行。请稍后重新提交。'}

export default {
  name: 'KnowledgeRebuildTask',
  props: {knowledge: {type: Object, required: true}, canManage: Boolean},
  emits: ['change', 'settled'],
  data() {return {task: null, loading: false, loadFailed: false, recovering: false, timer: null, revision: 0, recoveryKey: null}},
  computed: {
    active() {return ['QUEUED', 'RUNNING'].includes(this.task?.state)},
    taskKind() {return {IMPORT: '上传导入', STARTUP: '启动重建', REBUILD: '手动重建'}[this.task?.taskType] || '索引'},
    stateText() {return (this.task?.taskType === 'IMPORT'
      ? {RUNNING: '导入处理中', FAILED: '导入失败', SUCCEEDED: '导入处理完成'}[this.task?.state] : null)
      || labels[this.task?.state] || '状态未知'},
    stateType() {return this.task?.state === 'SUCCEEDED' ? 'success' : this.active ? 'primary' : 'warning'},
    hint() {
      if (this.task?.taskType === 'IMPORT') {
        if (this.task?.state === 'SUCCEEDED') return '处理完成不等于所有内容均已入库；安全隔离的文件不会向量化，请核对文档安全状态。';
        if (this.task?.state === 'FAILED') return '本次导入未完成，请检查文档诊断；不会自动重试模型。索引可用时不影响已有成功文档。';
      }
      return hints[this.task?.state] || ''
    }
  },
  watch: {
    'knowledge.id': {immediate: true, handler() {this.reset(); this.reload()}},
    canManage() {this.reset(); this.reload()}
  },
  beforeUnmount() {this.reset()},
  methods: {
    reset() {this.revision++; this.stop(); this.task = null; this.loading = false; this.recoveryKey = null; this.$emit('change', null)},
    stop() {if (this.timer) clearInterval(this.timer); this.timer = null},
    accept(task) {
      const previous = this.task;
      this.task = task; this.loadFailed = false; this.$emit('change', task);
      if (this.active) {
        if (!this.timer) this.timer = setInterval(() => this.reload(), 3000);
      } else {
        this.stop();
        if (previous && ['QUEUED', 'RUNNING'].includes(previous.state)) this.$emit('settled');
      }
    },
    async reload() {
      const id = this.knowledge.id, revision = this.revision;
      if (!id || !this.canManage || this.loading) return;
      this.loading = true;
      try {
        const response = await getLatestKnowledgeRebuildTask(id);
        if (revision === this.revision && id === this.knowledge.id) this.accept(response.data);
      } catch (_) {
        if (revision === this.revision) {this.loadFailed = true; this.stop()}
      } finally {if (revision === this.revision) this.loading = false}
    },
    async recover() {
      const id = this.knowledge.id, taskId = this.task?.taskId, revision = this.revision;
      if (!this.task?.canRecover || this.recovering) return;
      try {
        await this.$modal.confirm('此操作不是续跑：旧任务将失效，并从头重建全部文档；已发出的远端请求不能撤回，可能再次产生向量模型费用。确认继续吗？');
        if (revision !== this.revision || id !== this.knowledge.id || taskId !== this.task?.taskId) return;
        this.recovering = true;
        this.recoveryKey ||= newKnowledgeRebuildRequestKey();
        const response = await recoverKnowledgeRebuildTask(id, taskId, this.recoveryKey);
        if (revision !== this.revision) return;
        this.recoveryKey = null; this.accept(response.data);
        this.$modal.msgSuccess('已保存新的重建任务，请以任务状态为准');
      } catch (_) { /* 网络失败保留幂等键；不自动重试。 */ }
      finally {this.recovering = false}
    }
  }
}
</script>

<style scoped>
.knowledge-rebuild-task {--rebuild-accent: #4338ca; --rebuild-soft: #eef2ff; margin: 0 0 20px; padding: 16px; border: 1px solid var(--el-border-color); border-radius: 12px; background: var(--el-bg-color); color: var(--el-text-color-primary)}
.task-heading {display: flex; align-items: center; gap: 12px; flex-wrap: wrap}
.task-id {font-family: monospace; overflow-wrap: anywhere; font-size: 12px; color: var(--el-text-color-regular); margin: 12px 0 0}
.task-hint {font-size: 13px; line-height: 1.6; margin: 10px 0 0; color: var(--el-text-color-regular)}
.knowledge-rebuild-task :deep(.el-tag) {color: var(--rebuild-accent) !important; background: var(--rebuild-soft) !important; border-color: var(--rebuild-accent) !important}
.knowledge-rebuild-task :deep(.el-tag--success) {color: #166534 !important; background: #f0fdf4 !important; border-color: #86efac !important}
.knowledge-rebuild-task :deep(.el-tag--warning) {color: #78350f !important; background: #fef3c7 !important; border-color: #b45309 !important}
.knowledge-rebuild-task :deep(.el-button.el-button.el-button:not(.el-button--warning)) {color: var(--el-text-color-primary) !important; background: var(--el-bg-color) !important; border-color: var(--el-border-color) !important}
.knowledge-rebuild-task :deep(.el-button.el-button.el-button:not(.el-button--warning):not(.is-disabled):is(:hover, :focus-visible)) {color: var(--rebuild-accent) !important; background: var(--rebuild-soft) !important; border-color: var(--rebuild-accent) !important}
.knowledge-rebuild-task :deep(.el-button.el-button.el-button.el-button--warning) {color: #78350f !important; background: #fef3c7 !important; border-color: #b45309 !important}
.knowledge-rebuild-task :deep(.el-button.el-button.el-button.el-button--warning:not(.is-disabled):is(:hover, :focus-visible)) {color: #78350f !important; background: #fde68a !important}
.knowledge-rebuild-task :deep(.el-button:focus-visible) {outline: 2px solid var(--rebuild-accent); outline-offset: 3px}
.knowledge-rebuild-task :deep(.el-button span) {color: inherit !important}
</style>

<style>
/* 全局自定义 primary 可能为白色；任务交互使用独立且可读的主题色。 */
html.dark .knowledge-rebuild-task {--rebuild-accent: #93c5fd; --rebuild-soft: #172554}
</style>
