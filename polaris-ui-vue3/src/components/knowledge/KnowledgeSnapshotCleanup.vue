<template>
  <el-collapse v-if="canManage" class="knowledge-cleanup">
    <el-collapse-item title="快照清理 · 先预览，确认后才删除" name="cleanup">
      <p>仅清理有记录的失败／退役私有快照正文，不删除原文件、文档、当前活动索引或向量，也不扫描无归属文件。</p>
      <p>新待清理记录默认等待 24 小时，每批最多 20 条；文件删除不可恢复，不调用模型，不自动执行或重试。</p>
      <div class="cleanup-toolbar">
        <el-button :loading="loading" :disabled="running" @click="preview">预览可清理快照</el-button>
        <el-button type="warning" :loading="running" :disabled="loading || !plan || plan.blocked || plan.eligibleCount === 0" @click="execute">确认清理本批</el-button>
      </div>
      <p v-if="plan?.blocked" role="status">存在活动构建或尚未结束的文档处理，已暂停清理；不会接管或恢复旧任务。</p>
      <p v-else-if="plan" role="status">本批可清理 {{ plan.eligibleCount }} 条。当前活动索引的快照受保护；没有候选时不会删除文件。</p>
      <p v-if="result" role="status">本批处理 {{ result.attempted }} 条，已清理 {{ result.cleaned }} 条，等待重试 {{ result.retryWaiting }} 条，需人工处理 {{ result.manualRequired }} 条。</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <KnowledgeSnapshotCleanupStatus :knowledge="knowledge" :can-manage="canManage" />
    </el-collapse-item>
  </el-collapse>
</template>

<script>
import {executeKnowledgeSnapshotCleanup, previewKnowledgeSnapshotCleanup} from '@/api/ai/knowledge'
import KnowledgeSnapshotCleanupStatus from './KnowledgeSnapshotCleanupStatus.vue'

export default {
  name: 'KnowledgeSnapshotCleanup',
  components: {KnowledgeSnapshotCleanupStatus},
  props: {knowledge: {type: Object, required: true}, canManage: Boolean},
  data() {return {plan: null, result: null, error: '', loading: false, running: false, revision: 0}},
  watch: {'knowledge.id'() {this.reset()}, 'knowledge.indexVersion'() {this.reset()}, 'knowledge.vectorCollection'() {this.reset()}, canManage() {this.reset()}},
  beforeUnmount() {this.reset()},
  methods: {
    reset() {this.revision++; this.plan = null; this.result = null; this.error = ''; this.loading = false; this.running = false},
    async preview() {
      const id = this.knowledge.id, revision = this.revision;
      if (!this.canManage || !id || this.loading || this.running) return;
      this.loading = true; this.plan = null; this.result = null; this.error = '';
      try {
        const response = await previewKnowledgeSnapshotCleanup(id);
        if (revision === this.revision && id === this.knowledge.id) this.plan = response.data;
      } catch (_) {
        if (revision === this.revision) this.error = '清理预览暂不可用；没有删除文件，请稍后手动刷新预览。';
      } finally {if (revision === this.revision) this.loading = false}
    },
    async execute() {
      const id = this.knowledge.id, planId = this.plan?.planId, revision = this.revision;
      if (!this.canManage || !id || this.loading || this.running || !planId || this.plan.blocked || this.plan.eligibleCount < 1) return;
      this.running = true; this.error = ''; let submitted = false;
      try {
        await this.$modal.confirm(`仅删除本批 ${this.plan.eligibleCount} 条已失败或退役且不属于当前活动索引的私有快照正文；文件删除不可恢复，不会删除原文件、文档或向量。确认清理吗？`);
        if (revision !== this.revision || id !== this.knowledge.id || planId !== this.plan?.planId) return;
        submitted = true;
        const response = await executeKnowledgeSnapshotCleanup(id, planId);
        if (revision === this.revision && id === this.knowledge.id) {this.result = response.data; this.plan = null}
      } catch (_) {
        if (submitted && revision === this.revision) {
          this.plan = null;
          this.error = '执行结果未确认或清理范围已变化，请重新预览；不会自动再次执行。';
        }
      } finally {if (revision === this.revision) this.running = false}
    }
  }
}
</script>

<style scoped>
.knowledge-cleanup {--cleanup-accent: #4338ca; --cleanup-soft: #eef2ff; --cleanup-muted: #475569; --cleanup-disabled: #f1f5f9; margin: 0 0 20px; padding: 0 16px; border: 1px solid var(--el-border-color); border-radius: 12px; background: var(--el-bg-color); color: var(--el-text-color-primary)}
.knowledge-cleanup p {line-height: 1.7; color: var(--el-text-color-regular); overflow-wrap: anywhere}
.cleanup-toolbar {display: flex; gap: 12px; flex-wrap: wrap; margin: 12px 0}
.knowledge-cleanup :deep(.el-collapse-item__header), .knowledge-cleanup :deep(.el-collapse-item__wrap) {background: var(--el-bg-color) !important; color: var(--el-text-color-primary) !important; border: 0}
.knowledge-cleanup :deep(.el-button.el-button.el-button.el-button) {color: var(--cleanup-accent) !important; background: var(--el-bg-color) !important; border-color: var(--cleanup-accent) !important; background-image: none !important}
.knowledge-cleanup :deep(.el-button.el-button.el-button.el-button:not(.is-disabled):is(:hover, :focus-visible)) {color: var(--cleanup-accent) !important; background: var(--cleanup-soft) !important}
.knowledge-cleanup :deep(.el-button.el-button.el-button.el-button--warning:not(.is-disabled)) {color: #78350f !important; background: #fef3c7 !important; border-color: #b45309 !important}
.knowledge-cleanup :deep(.el-button.el-button.el-button.el-button--warning:not(.is-disabled):is(:hover, :focus-visible)) {background: #fde68a !important}
.knowledge-cleanup :deep(.el-button.el-button.el-button.el-button.is-disabled) {color: var(--cleanup-muted) !important; background: var(--cleanup-disabled) !important; border-color: var(--el-border-color) !important; opacity: 1 !important}
.knowledge-cleanup :deep(.el-button span) {color: inherit !important}
.knowledge-cleanup :deep(.el-button:focus-visible) {outline: 2px solid var(--cleanup-accent); outline-offset: 3px}
</style>
<style>
html.dark .knowledge-cleanup {--cleanup-accent: #93c5fd; --cleanup-soft: #172554; --cleanup-muted: #94a3b8; --cleanup-disabled: #1e293b}
</style>
