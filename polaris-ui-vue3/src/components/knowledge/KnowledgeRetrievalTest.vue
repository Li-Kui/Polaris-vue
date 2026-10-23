<template>
  <section class="knowledge-retrieval-test">
    <el-collapse v-model="expanded">
      <el-collapse-item title="检索测试 · 查看实际命中片段，不生成回答" name="test">
        <el-alert type="info" :closable="false" show-icon>
          使用当前知识库绑定的向量模型（ID：{{ knowledge.embeddingModelId || '未绑定' }}）处理测试问题，可能发送至外部模型服务并产生费用，模型准备也可能进行维度探测；不调用回答模型，本功能不持久化测试问题或结果，外部服务留存按其自身策略执行。
          此处仅展示本次索引命中的片段，不是全文解析预览。
        </el-alert>
        <p v-if="!canTest" class="knowledge-retrieval-test__notice">仅系统超级管理员或当前租户管理员可进行检索测试。</p>
        <p v-else-if="knowledge.indexStatus !== 'READY'" class="knowledge-retrieval-test__notice">索引尚未就绪，请先检查知识库状态；本测试不会自动重建索引。</p>
        <el-form label-position="top" @submit.prevent="runTest">
          <el-form-item label="测试问题">
            <el-input v-model="query" type="textarea" :rows="3" maxlength="2000" show-word-limit
                      placeholder="输入一个希望从当前资料中找到依据的问题" :disabled="!canTest" />
          </el-form-item>
          <div class="knowledge-retrieval-test__parameters">
            <el-form-item label="最多返回片段">
              <el-input-number v-model="topK" :min="1" :max="20" :disabled="!canTest" />
            </el-form-item>
            <el-form-item label="最低向量匹配分">
              <el-input-number v-model="minScore" :min="0" :max="1" :step="0.05" :precision="2" :disabled="!canTest" />
            </el-form-item>
            <span>仅用于本次测试，不修改知识库或工作流配置。匹配分不代表答案正确率。</span>
          </div>
          <KnowledgeRelatedDocumentPicker v-model="relatedDocumentIds" :knowledge-id="knowledge.id" :result-limit="topK"
                                          :disabled="!canTest || loading" @ready="relatedReady = $event" />
          <el-checkbox v-model="embeddingConfirmed" :disabled="!canTest">
            我确认将测试问题发送至当前向量模型服务，接受可能产生的费用
          </el-checkbox>
          <div class="knowledge-retrieval-test__actions">
            <el-button type="primary" :loading="loading" :disabled="!canRun" @click="runTest">开始检索测试</el-button>
            <el-button :disabled="loading || (!query && !result && !error)" @click="clearTest">清空测试</el-button>
          </div>
        </el-form>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <el-alert v-if="stale && result" title="问题、参数或知识库索引已变化，以下为上一次测试结果，请重新测试。"
                  type="warning" :closable="false" show-icon />
        <div v-if="result" class="knowledge-retrieval-test__result" aria-live="polite">
          <header>
            <strong>{{ result.state === 'FOUND' ? `找到 ${result.results.length} 条可用片段` : '本次没有可用命中' }}</strong>
            <span>索引 v{{ result.indexVersion }} · {{ result.strategy }} · 总耗时 {{ result.durationMs }} ms</span>
          </header>
          <p class="knowledge-retrieval-test__summary">
            候选 {{ result.candidateCount }} · 有效性过滤 {{ result.filteredCount }} · 去重 {{ result.deduplicatedCount }}
            · 模型准备 {{ result.prepareMs }} ms · 问题向量化 {{ result.embeddingMs }} ms · 向量检索 {{ result.searchMs }} ms
          </p>
          <p v-if="result.relatedCoverage?.requestedDocuments" class="knowledge-retrieval-test__notice">
            指定关联资料 {{ result.relatedCoverage.requestedDocuments }} 份，命中 {{ result.relatedCoverage.matchedDocuments }} 份；
            实际向量查询 {{ result.relatedCoverage.vectorQueryCount }} 次。命中不代表证据完整或结论已核实。
          </p>
          <p v-if="result.state === 'EMPTY'" class="knowledge-retrieval-test__notice">
            这不等于资料不存在。可核对文件是否成功入库，或调整问法及测试参数；有效性过滤后的候选不会自动补足到设定数量。
          </p>
          <article v-for="hit in result.results" :key="hit.rank" class="knowledge-retrieval-test__hit">
            <div><strong>{{ hit.rank }}. {{ hit.documentName }}</strong><span>向量匹配分 {{ formatScore(hit.score) }}</span></div>
            <small>文档 ID {{ hit.documentId }} · {{ hit.chunkNo == null ? '片段位置未知' : `片段序号 ${hit.chunkNo}（从 0 开始）` }}</small>
            <pre>{{ hit.content }}</pre>
          </article>
          <small>诊断标识：{{ result.traceId }}。以上是检索参考资料，并非已验证的答案引用。</small>
        </div>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script>
import {testKnowledgeRetrieval} from '@/api/ai/knowledge'
import KnowledgeRelatedDocumentPicker from './KnowledgeRelatedDocumentPicker.vue'

export default {
  name: 'KnowledgeRetrievalTest',
  components: {KnowledgeRelatedDocumentPicker},
  props: {knowledge: {type: Object, required: true}, canTest: {type: Boolean, default: false}},
  data() {
    return {expanded: [], query: '', topK: 5, minScore: 0.5, embeddingConfirmed: false,
      loading: false, result: null, error: '', stale: false, requestSequence: 0, relatedDocumentIds: [], relatedReady: false}
  },
  computed: {
    canRun() {
      return this.canTest && this.knowledge.indexStatus === 'READY' && !this.loading
        && !!this.query.trim() && this.query.length <= 2000 && this.embeddingConfirmed
        && Number.isInteger(this.topK) && this.topK >= 1 && this.topK <= 20
        && Number.isFinite(this.minScore) && this.minScore >= 0 && this.minScore <= 1
        && (!this.relatedDocumentIds.length || (this.relatedReady && this.topK >= this.relatedDocumentIds.length + 1))
    },
    indexSnapshot() {
      return JSON.stringify([this.knowledge.id, this.knowledge.indexStatus, this.knowledge.indexVersion,
        this.knowledge.embeddingModelId, this.knowledge.embeddingModelRevision, this.knowledge.embeddingSchemaHash])
    }
  },
  watch: {
    'knowledge.id': {immediate: true, handler() {
      this.clearTest()
      this.topK = Math.max(1, Math.min(20, this.knowledge.retrievalTopK || 5))
      this.minScore = this.knowledge.retrievalMinScore == null ? 0.5 : this.knowledge.retrievalMinScore
    }},
    indexSnapshot() { this.invalidateResult() },
    query() { this.invalidateResult() },
    topK() { this.invalidateResult() },
    minScore() { this.invalidateResult() },
    relatedDocumentIds: {deep: true, handler() {this.invalidateResult()}},
    canTest(value) { if (!value) this.clearTest() }
  },
  beforeUnmount() { this.requestSequence++ },
  methods: {
    invalidateResult() {
      this.requestSequence++
      if (this.result) this.stale = true
      this.error = ''
    },
    clearTest() {
      this.requestSequence++
      this.query = ''; this.embeddingConfirmed = false
      this.relatedDocumentIds = []
      this.result = null; this.error = ''; this.stale = false
    },
    formatScore(value) { return Number.isFinite(value) ? value.toFixed(3) : '—' },
    async runTest() {
      if (!this.canRun) return
      const sequence = ++this.requestSequence
      this.loading = true; this.error = ''; this.result = null; this.stale = false
      try {
        const related = [...this.relatedDocumentIds]
        const response = await testKnowledgeRetrieval(this.knowledge.id, {query: this.query.trim(),
          topK: this.topK, minScore: this.minScore, embeddingConfirmed: this.embeddingConfirmed,
          ...(related.length ? {relatedDocumentIds: related} : {})})
        if (sequence !== this.requestSequence) return
        if (!response.data || !Array.isArray(response.data.results)
          || !['FOUND', 'EMPTY'].includes(response.data.state)) throw new Error('检索服务返回无效结果，请稍后重试')
        if (related.length && response.data.strategy !== 'EXPLICIT_RELATED_V1') throw new Error('后端未确认关联补全策略，本次结果不能按关联验收')
        this.result = response.data
      } catch (error) {
        if (sequence !== this.requestSequence) return
        this.error = error?.response?.data?.msg || error?.message || '检索失败，请检查模型或索引后重试'
      } finally {
        // 修改问题会让响应失效，但旧调用结束前仍禁止再次派发，避免重复计费。
        this.loading = false
      }
    }
  }
}
</script>

<style scoped lang="scss">
.knowledge-retrieval-test { margin-bottom: 20px; padding: 0 16px; border: 1px solid var(--el-border-color); border-radius: 10px; background: var(--el-bg-color); }
.knowledge-retrieval-test :deep(.el-collapse) { border: none; }
.knowledge-retrieval-test :deep(.el-collapse-item__header) { color: var(--el-text-color-primary); font-weight: 600; background: transparent; }
.knowledge-retrieval-test :deep(.el-collapse-item__wrap) { background: transparent; }
.knowledge-retrieval-test :deep(.el-form) { margin-top: 16px; }
.knowledge-retrieval-test__parameters { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; }
.knowledge-retrieval-test__parameters > span, .knowledge-retrieval-test small, .knowledge-retrieval-test__summary { color: var(--el-text-color-secondary); }
.knowledge-retrieval-test__actions { display: flex; gap: 8px; margin: 16px 0; }
.knowledge-retrieval-test .knowledge-retrieval-test__actions :deep(.el-button.el-button.el-button) {
  color: var(--el-text-color-regular) !important;
  background: var(--el-bg-color) !important;
  border: 1px solid var(--el-border-color) !important;
  box-shadow: none !important;
  > span { color: inherit !important; }
  &:not(.is-disabled):is(:hover, :focus-visible, :active) {
    color: #4338ca !important;
    background: #eef2ff !important;
    border-color: #4f46e5 !important;
  }
  &.el-button--primary:not(.is-disabled) {
    color: #fff !important;
    background: #4f46e5 !important;
    border-color: #4f46e5 !important;
    &:is(:hover, :focus-visible, :active) { background: #4338ca !important; border-color: #4338ca !important; }
  }
  &.is-disabled.is-disabled, &.is-disabled.is-disabled:is(:hover, :focus, :active) {
    color: var(--el-text-color-regular) !important;
    background: var(--el-fill-color-light) !important;
    border-color: var(--el-border-color) !important;
    opacity: 1 !important;
  }
  &:focus-visible { outline: 2px solid #4f46e5; outline-offset: 2px; }
}
.knowledge-retrieval-test :deep(.el-alert) { color: var(--el-text-color-regular); }
.knowledge-retrieval-test__result { margin-top: 16px; }
.knowledge-retrieval-test__result header, .knowledge-retrieval-test__hit > div { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; color: var(--el-text-color-primary); }
.knowledge-retrieval-test__notice { margin: 12px 0; color: var(--el-text-color-regular); }
.knowledge-retrieval-test__hit { margin: 12px 0; padding: 12px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-fill-color-light); }
.knowledge-retrieval-test__hit pre { margin: 10px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; max-height: 300px; overflow-y: auto; color: var(--el-text-color-primary); }
.knowledge-retrieval-test :deep(.el-checkbox) { max-width: 100%; height: auto; }
.knowledge-retrieval-test :deep(.el-checkbox__label) { white-space: normal; line-height: 1.6; }
</style>
