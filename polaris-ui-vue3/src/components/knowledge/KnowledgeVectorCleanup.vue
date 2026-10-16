<template>
  <el-collapse v-if="canManage" class="knowledge-vector-cleanup">
    <el-collapse-item title="向量集合治理 · 只读条件预览" name="vector-cleanup">
      <p>只读预览不会执行删除；受控清理需另行生成单集合计划，并明确确认。</p>
      <p>只查看已登记集合的本地清理条件，不扫描或认领历史集合，不调用模型，不删除向量、文件或文档。</p>
      <p>退役／已完成但未发布集合按服务端保留期检查（默认 24 小时）；活动引用、未结束任务和处理中快照受保护。</p>
      <el-button :loading="loading" :disabled="checking !== null" @click="preview">预览集合清理条件</el-button>
      <p v-if="error" role="alert">{{ error }}</p>
      <template v-if="report">
        <p role="status">本批查看 {{ report.items.length }} 条登记，本地条件满足 {{ report.localCandidateCount }} 条。此结果不是删除计划或授权。</p>
        <p>当前集合保留期：{{ retentionText(report.retentionHours) }}。{{ report.retentionHours < 24 ? '已缩短保留期，用于测试验收。' : '' }}</p>
        <p v-if="report.blocked" role="status">存在活动构建或未结束任务，已阻止列入本地候选；不会自动接管或恢复。</p>
        <p v-if="report.truncated" role="status">仅展示最早的 {{ report.batchLimit }} 条登记，结果已截断，不代表全部集合。</p>
        <p>本地条件更新时间：{{ time(report.observedAt) }}。归属核验只读取一个远端集合配置，匹配仅代表该时刻的证据，不能据此删除。</p>
        <div class="vector-preview-results">
        <el-table class="vector-preview-table" :data="report.items" row-key="buildGeneration" empty-text="暂无集合登记，不会自动认领旧集合">
          <el-table-column label="索引版本" width="100"><template #default="{row}">v{{ row.indexVersion }}</template></el-table-column>
          <el-table-column prop="buildGeneration" label="构建代次" width="100" />
          <el-table-column label="登记状态" min-width="140"><template #default="{row}">{{ stateText(row.state) }}</template></el-table-column>
          <el-table-column label="最早保留至" min-width="180"><template #default="{row}">{{ time(row.eligibleAfter) }}</template></el-table-column>
          <el-table-column label="本地检查" min-width="220"><template #default="{row}">{{ reasonText(row.reason) }}</template></el-table-column>
          <el-table-column label="远端归属（只读）" min-width="270">
            <template #default="{row}">
              <el-button size="small" :loading="checking === row.buildGeneration" :disabled="loading || checking !== null || !canVerify(row)" @click="verify(row)">核验归属</el-button>
              <span class="ownership-result" role="status">{{ verificationText(checks[row.buildGeneration]) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <ul v-if="report.items.length" class="vector-preview-cards" aria-label="集合清理检查结果">
          <li v-for="row in report.items" :key="row.buildGeneration">
            <div class="vector-card-heading">v{{ row.indexVersion }} · 构建代次 {{ row.buildGeneration }}</div>
            <dl>
              <dt>登记状态</dt><dd>{{ stateText(row.state) }}</dd>
              <dt>最早保留至</dt><dd>{{ time(row.eligibleAfter) }}</dd>
              <dt>本地检查</dt><dd>{{ reasonText(row.reason) }}</dd>
            </dl>
            <el-button size="small" :loading="checking === row.buildGeneration" :disabled="loading || checking !== null || !canVerify(row)" @click="verify(row)">核验归属</el-button>
            <p role="status">{{ verificationText(checks[row.buildGeneration]) }}</p>
          </li>
        </ul>
        <p v-else class="vector-preview-cards" role="status">暂无集合登记，不会自动认领旧集合</p>
        </div>
        <KnowledgeVectorCleanupExecution :knowledge="knowledge" :can-manage="canManage" :items="report.items" />
      </template>
    </el-collapse-item>
  </el-collapse>
</template>

<script>
import {previewKnowledgeVectorCleanup, verifyKnowledgeVectorOwnership} from '@/api/ai/knowledge'
import KnowledgeVectorCleanupExecution from './KnowledgeVectorCleanupExecution.vue'

const states = {RESERVED: '已登记待创建', CREATING: '创建中', WRITING: '写入中', BUILT: '构建完成待发布',
  ACTIVE: '当前已发布', RETIRED: '已退役', ABANDONED: '已放弃', UNKNOWN: '远端结果待确认'}
const reasons = {STATE_PROTECTED: '当前状态受保护', LEGACY_UNVERIFIED: '历史未标记，不自动认领',
  CREATE_UNCONFIRMED: '没有创建成功证据', BACKEND_UNSUPPORTED: '当前存储类型不纳入清理', ROUTE_CHANGED: '存储路由已变化',
  DEFINITION_INVALID: '登记信息异常，需人工核对', ACTIVE_REFERENCE: '仍被知识库引用（含软删除记录）',
  RETIREMENT_UNKNOWN: '退役时间不确定', RETENTION_PENDING: '尚未达到保留期限', ACTIVE_INDEX_TASK: '存在未结束任务或文档处理',
  SNAPSHOT_IN_PROGRESS: '存在处理中快照', LOCAL_CANDIDATE: '本地条件满足，仍不可据此删除', CLEANUP_SCHEMA_REQUIRED: '清理元数据未就绪，请执行 SQL 29',
  ALREADY_DELETED: '已确认清理', CLEANUP_RUNNING: '清理执行中或结果尚待确认', MANUAL_REQUIRED: '需人工核对，不自动重删',
  CLEANUP_UNRESOLVED: '清理状态异常或已达重试上限', RETRY_PENDING: '确定未派发，等待重试保留期'}
const verifications = {MATCHED: '本次标记匹配（非删除授权）', LOCAL_MATCHED: '仅本进程标记匹配', LOCAL_MISSING: '本进程证据缺失',
  LEGACY_UNVERIFIED: '历史未标记', UNRESOLVED: '构建／创建结果未确定', ROUTE_CHANGED: '存储路由已变化',
  MARKER_MISSING: '远端标记缺失', MARKER_MISMATCH: '远端标记不匹配', UNAVAILABLE: '远端读取不可确定',
  UNVERIFIED: '登记证据无效', CHANGED: '范围已变化，请重新预览', REQUEST_FAILED: '核验未完成，请稍后手动重试'}

export default {
  name: 'KnowledgeVectorCleanup',
  components: {KnowledgeVectorCleanupExecution},
  props: {knowledge: {type: Object, required: true}, canManage: Boolean},
  data() {return {report: null, checks: {}, loading: false, checking: null, error: '', revision: 0}},
  watch: {'knowledge.id'() {this.reset()}, 'knowledge.indexVersion'() {this.reset()}, 'knowledge.vectorCollection'() {this.reset()},
    'knowledge.indexStatus'() {this.reset()}, canManage() {this.reset()}},
  beforeUnmount() {this.reset()},
  methods: {
    reset() {this.revision++; this.report = null; this.checks = {}; this.loading = false; this.checking = null; this.error = ''},
    stateText(value) {return states[value] || '状态未知'},
    reasonText(value) {return reasons[value] || '检查结果未知，需人工核对'},
    verificationText(value) {return value ? verifications[value] || '核验状态未知' : '尚未核验'},
    retentionText(hours) {if (!Number.isFinite(hours) || hours <= 0) return '以各登记的最早保留时间为准';const minutes = Math.round(hours * 60);return minutes % 60 === 0 ? `${minutes / 60} 小时` : `${minutes} 分钟`},
    time(value) {if (!value) return '—';const date = new Date(value);return Number.isNaN(date.getTime()) ? '时间未知' : date.toLocaleString('zh-CN', {hour12: false})},
    canVerify(row) {return ['ACTIVE', 'RETIRED', 'BUILT', 'ABANDONED'].includes(row.state) && Number.isSafeInteger(row.buildGeneration) && row.buildGeneration > 0
      && !['LEGACY_UNVERIFIED', 'CREATE_UNCONFIRMED', 'BACKEND_UNSUPPORTED', 'ROUTE_CHANGED', 'DEFINITION_INVALID', 'ALREADY_DELETED', 'CLEANUP_RUNNING', 'CLEANUP_SCHEMA_REQUIRED'].includes(row.reason)},
    async preview() {
      const id = this.knowledge.id, revision = this.revision;
      if (!this.canManage || !id || this.loading || this.checking !== null) return;
      this.loading = true; this.report = null; this.checks = {}; this.error = '';
      try {
        const response = await previewKnowledgeVectorCleanup(id);
        if (revision === this.revision && id === this.knowledge.id) this.report = response.data;
      } catch (_) {
        if (revision === this.revision) this.error = '集合预览暂不可用；没有执行删除，请稍后手动刷新。';
      } finally {if (revision === this.revision) this.loading = false}
    },
    async verify(row) {
      const id = this.knowledge.id, revision = this.revision, generation = row.buildGeneration;
      if (!this.canManage || !id || !this.report?.items.includes(row) || !this.canVerify(row) || this.loading || this.checking !== null) return;
      this.checking = generation; delete this.checks[generation];
      try {
        const response = await verifyKnowledgeVectorOwnership(id, generation);
        if (revision === this.revision && id === this.knowledge.id) {
          const value = response.data;
          this.checks[generation] = value?.buildGeneration === generation && value?.indexVersion === row.indexVersion ? value.state : 'CHANGED';
          if (this.checks[generation] === 'CHANGED') {this.report = null;this.error = '范围已变化，请重新预览；不会执行删除。'}
        }
      } catch (_) {
        if (revision === this.revision) this.checks[generation] = 'REQUEST_FAILED';
      } finally {if (revision === this.revision) this.checking = null}
    }
  }
}
</script>

<style scoped>
.knowledge-vector-cleanup {--vector-accent: #4338ca; --vector-soft: #eef2ff; --vector-muted: #475569; --vector-disabled: #f1f5f9; margin: 0 0 20px; padding: 0 16px; border: 1px solid var(--el-border-color); border-radius: 12px; background: var(--el-bg-color); color: var(--el-text-color-primary)}
.knowledge-vector-cleanup p {line-height: 1.7; color: var(--el-text-color-regular); overflow-wrap: anywhere}
.ownership-result {display: inline-block; margin: 4px 0 4px 10px; color: var(--el-text-color-regular)}
.vector-preview-results {container-type: inline-size}
.vector-preview-cards {display: none; list-style: none; padding: 0; margin: 12px 0}
.vector-preview-cards li {padding: 12px; margin: 10px 0; border: 1px solid var(--el-border-color); border-radius: 8px; overflow-wrap: anywhere}
.vector-card-heading {font-weight: 600; color: var(--el-text-color-primary); line-height: 1.7}
.vector-preview-cards dl {margin: 10px 0; line-height: 1.7}
.vector-preview-cards dt {font-size: 12px; color: var(--el-text-color-secondary)}
.vector-preview-cards dd {margin: 0 0 8px; color: var(--el-text-color-regular)}
.vector-preview-cards p {margin: 8px 0 0}
@container (max-width: 1000px) {
  .vector-preview-table {display: none}
  .vector-preview-cards {display: block}
}
.knowledge-vector-cleanup :deep(.el-collapse-item__header), .knowledge-vector-cleanup :deep(.el-collapse-item__wrap) {background: var(--el-bg-color) !important; color: var(--el-text-color-primary) !important; border: 0}
.knowledge-vector-cleanup :deep(.el-button.el-button.el-button.el-button) {color: var(--vector-accent) !important; background: var(--el-bg-color) !important; border-color: var(--vector-accent) !important; background-image: none !important}
.knowledge-vector-cleanup :deep(.el-button.el-button.el-button.el-button:not(.is-disabled):is(:hover, :focus-visible)) {color: var(--vector-accent) !important; background: var(--vector-soft) !important}
.knowledge-vector-cleanup :deep(.el-button.el-button.el-button.el-button.is-disabled) {color: var(--vector-muted) !important; background: var(--vector-disabled) !important; border-color: var(--el-border-color) !important; opacity: 1 !important}
.knowledge-vector-cleanup :deep(.el-button span) {color: inherit !important}
.knowledge-vector-cleanup :deep(.el-button:focus-visible) {outline: 2px solid var(--vector-accent); outline-offset: 3px}
</style>
<style>
html.dark .knowledge-vector-cleanup {--vector-accent: #93c5fd; --vector-soft: #172554; --vector-muted: #94a3b8; --vector-disabled: #1e293b}
</style>
