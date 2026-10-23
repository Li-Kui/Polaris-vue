<template>
  <section v-if="canManage" class="snapshot-cleanup-status" aria-label="快照文件清理记录">
    <h4>文件清理记录</h4>
    <p>只读取已登记的失败／退役载荷及清理记录，每页最多 20 条。查看状态不会删除、重试或恢复任务；是否可清理仍以重新预览为准。</p>
    <el-button :loading="loading" @click="load()">刷新文件清理记录</el-button>
    <template v-if="report">
      <p>状态读取时间：{{ time(report.observedAt) }}</p>
      <ul v-if="report.items.length">
        <li v-for="row in report.items" :key="row.snapshotId">
          <strong>索引 v{{ row.indexVersion }} · 文档 #{{ row.documentId }}</strong>
          <p>快照：{{ snapshotText(row.snapshotState) }}；清理：{{ stateText(row.cleanupState) }}；已尝试 {{ row.attempts ?? '未知' }} 次。</p>
          <p v-if="row.activeProtected">仍属于当前活动索引，禁止清理。</p>
          <p v-if="row.nextAttemptAt">最早可处理时间：{{ time(row.nextAttemptAt) }}。到时不会自动执行。</p>
          <p v-if="row.errorCode">{{ errorText(row.errorCode) }}</p>
        </li>
      </ul>
      <p v-else role="status">暂无符合展示范围的文件清理记录，不代表已扫描或清除了全部文件。</p>
      <el-button v-if="report.hasMore" :disabled="loading" @click="load(report.nextBeforeId)">查看更早记录</el-button>
    </template>
    <p v-if="error" role="alert">{{ error }}</p>
  </section>
</template>

<script>
import {getKnowledgeSnapshotCleanupStatus} from '@/api/ai/knowledge'

export default {
  name: 'KnowledgeSnapshotCleanupStatus',
  props: {knowledge: {type: Object, required: true}, canManage: Boolean},
  data() {return {report: null, loading: false, error: '', revision: 0}},
  watch: {'knowledge.id'() {this.reset()}, 'knowledge.indexVersion'() {this.reset()}, 'knowledge.vectorCollection'() {this.reset()}, canManage() {this.reset()}},
  beforeUnmount() {this.reset()},
  methods: {
    reset() {this.revision++;this.report = null;this.loading = false;this.error = ''},
    stateText(value) {return {NONE: '尚未处理', PENDING: '等待保留期', RETRY_WAIT: '等待显式重试', SUCCEEDED: '载荷已清理', MANUAL_REQUIRED: '需人工处理'}[value] || '状态未知，请人工核对'},
    snapshotText(value) {return {FAILED: '失败', RETIRED: '已退役', READY: '已入库', PREPARED: '处理中'}[value] || '状态未知'},
    errorText(value) {return {PAYLOAD_PATH_INVALID: '载荷路径或文件类型异常，需人工核对，不提供强制删除。', PAYLOAD_DELETE_FAILED: '文件清理未完成，请核对存储状态；不会自动重试。'}[value] || '清理结果需人工核对，不自动重复删除。'},
    time(value) {if (!value) return '—';if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value)) return value + '（北京时间）';const date = new Date(value);return Number.isNaN(date.getTime()) ? '时间未知' : date.toLocaleString('zh-CN', {timeZone: 'Asia/Shanghai', hour12: false}) + '（北京时间）'},
    async load(beforeId) {
      const id = this.knowledge.id, revision = this.revision;
      if (!this.canManage || !id || this.loading || beforeId != null && (!Number.isSafeInteger(beforeId) || beforeId <= 0)) return;
      this.loading = true;this.report = null;this.error = '';
      try {
        const response = await getKnowledgeSnapshotCleanupStatus(id, beforeId);
        if (revision === this.revision && id === this.knowledge.id) this.report = response.data;
      } catch (_) {if (revision === this.revision) this.error = '文件清理记录暂不可用，请稍后手动刷新；没有执行删除或重试。'}
      finally {if (revision === this.revision) this.loading = false}
    }
  }
}
</script>

<style scoped>
.snapshot-cleanup-status {padding: 16px; margin-top: 16px; border: 1px solid var(--el-border-color); border-radius: 10px; overflow-wrap: anywhere}
.snapshot-cleanup-status h4 {margin: 0 0 10px; color: var(--el-text-color-primary)}
.snapshot-cleanup-status ul {list-style: none; margin: 12px 0; padding: 0}
.snapshot-cleanup-status li {border: 1px solid var(--el-border-color); border-radius: 8px; padding: 12px; margin-bottom: 10px}
.snapshot-cleanup-status li p {margin: 8px 0 0; line-height: 1.7}
</style>
