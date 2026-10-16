<template>
  <el-dialog v-model="visible" class="knowledge-snapshot-dialog" title="文档诊断与入库分块" width="min(960px, 94vw)" append-to-body @closed="clear">
    <section v-loading="loading" class="document-snapshot">
      <el-alert :closable="false" type="info" title="仅查看已保存的实际入库分块，不重新解析文件，不调用模型。位置无法精确确认时不猜页码。" />
      <div class="snapshot-toolbar">
        <span>{{ document?.name }}</span>
        <el-button :disabled="loading || !document?.id" @click="load">刷新诊断</el-button>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-else-if="result?.state === 'LEGACY_NO_SNAPSHOT'" :closable="false" type="warning"
                title="当前索引没有可用快照。需由管理员明确重建后查看；这里不会自动重建或伪造历史分块。" />
      <el-alert v-else-if="result?.state === 'UNAVAILABLE'" :closable="false" type="warning"
                :title="`当前文档或索引不可预览${result.errorCode ? '（' + errorText(result.errorCode) + '）' : ''}。请先检查解析、安全检测和索引状态。`" />
      <template v-if="result?.state === 'READY'">
        <p>索引 v{{ result.indexVersion }} · {{ result.parsedChars }} 字符 · {{ result.chunkCount }} 块 · {{ result.parserVersion }}</p>
        <el-alert v-for="warning in warnings" :key="warning" :title="warningText(warning)" :closable="false" type="warning" />
        <article v-for="chunk in result.chunks" :key="chunk.chunkNo" class="snapshot-chunk">
          <header>分块 #{{ chunk.chunkNo }} · {{ chunk.locations?.length ? chunk.locations.join(' / ') : '未确认精确位置' }}</header>
          <pre>{{ chunk.text }}</pre>
        </article>
        <el-pagination :current-page="page" :page-size="10" :total="result.chunkCount" layout="total, prev, pager, next"
                       :disabled="loading" @current-change="changePage" />
      </template>
    </section>
  </el-dialog>
</template>

<script>
import {getDocumentSnapshot} from '@/api/ai/knowledge'

export default {
  name: 'KnowledgeDocumentSnapshot',
  props: {modelValue: Boolean, document: Object, knowledgeId: [Number, String]},
  emits: ['update:modelValue'],
  data() {return {loading: false, error: '', result: null, page: 1, sequence: 0}},
  computed: {
    visible: {get() {return this.modelValue}, set(value) {this.$emit('update:modelValue', value)}},
    warnings() {try {return JSON.parse(this.result?.warningsJson || '[]')} catch {return []}}
  },
  watch: {
    modelValue(value) {if (value) {this.page = 1; this.load()} else this.clear()},
    'document.id'() {this.clear(); if (this.modelValue) this.load()},
    knowledgeId() {this.clear(); this.visible = false}
  },
  beforeUnmount() {this.clear()},
  methods: {
    clear() {this.sequence++; this.loading = false; this.result = null; this.error = ''; this.page = 1},
    changePage(page) {this.page = page; this.load()},
    async load() {
      if (!this.modelValue || !this.document?.id) return
      const sequence = ++this.sequence, documentId = this.document.id
      this.loading = true; this.error = ''; this.result = null
      try {
        const response = await getDocumentSnapshot(documentId, {page: this.page, pageSize: 10})
        if (sequence !== this.sequence || !this.modelValue || documentId !== this.document?.id) return
        this.result = response.data
      } catch (error) {
        if (sequence === this.sequence && this.modelValue) this.error = error?.message || '文档诊断暂不可用'
      } finally {if (sequence === this.sequence) this.loading = false}
    },
    errorText(code) {
      return ({FILE_MISSING: '物理文件缺失', UNSUPPORTED_TYPE: '不支持此格式', FILE_TOO_LARGE: '超过 10MB 解析上限',
        RESOURCE_LIMIT: '超过解析资源上限', OCR_REQUIRED: '未提取到 PDF 文本，需要 OCR',
        INVALID_TEXT_ENCODING: '文本不是有效 UTF-8', EMPTY_CONTENT: '正文为空', PARSE_FAILED: '解析失败',
        INDEX_WRITE_FAILED: '入库未完整完成', CHUNK_LIMIT: '分块数量超出资源预算', PAYLOAD_CLEANUP_PENDING: '失败快照待清理'})[code] || code
    },
    warningText(code) {
      return ({PLAIN_TEXT_LAYOUT: '当前格式按纯文本保留，尚未完成结构化 Markdown/CSV 语义解析。',
        PDF_EMPTY_PAGE: 'PDF 部分页未提取到文字，未执行 OCR。', PDF_READING_ORDER_UNVERIFIED: 'PDF 阅读顺序仍需人工核对。',
        WORD_HEADERS_FOOTERS_NOT_INCLUDED: '本批只提取 Word 正文段落和表格，不包含页眉页脚。',
        TABLE_MERGES_UNVERIFIED: '合并单元格语义尚未验证，请核对原资料。', FORMULA_CACHED_VALUE: '公式仅使用缓存值，没有执行公式或外部引用。',
        FORMULA_CACHE_MISSING: '部分公式没有可用缓存值，已留空，没有计算或编造为 0。',
        TABLE_HEADER_SEMANTICS_UNVERIFIED: '已保留工作表与行边界，表头关联语义仍需后续增强。'})[code] || code
    }
  }
}
</script>

<style scoped lang="scss">
.document-snapshot {
  color: var(--el-text-color-primary);
  .snapshot-toolbar {display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 16px 0;}
  .el-alert {margin-bottom: 12px;}
  .snapshot-chunk {border: 1px solid var(--el-border-color); border-radius: 8px; padding: 14px; margin: 12px 0;}
  header {font-weight: 600; margin-bottom: 10px;}
  pre {white-space: pre-wrap; overflow-wrap: anywhere; font-family: inherit; margin: 0; max-height: 320px; overflow: auto;}
}
</style>

<style lang="scss">
// Teleport 后独立限定到本弹窗；覆盖旧全局玻璃背景及高优先级按钮规则。
html .el-dialog.knowledge-snapshot-dialog {
  --snapshot-accent: #4338ca;
  --snapshot-accent-soft: #eef2ff;
  --snapshot-warning: #92400e;
  --snapshot-warning-soft: #fffbeb;
  --snapshot-info: #475569;
  --snapshot-info-soft: #f1f5f9;
  background: var(--el-bg-color) !important;
  border: 1px solid var(--el-border-color-lighter) !important;
  color: var(--el-text-color-primary);
  backdrop-filter: none !important;

  &::before {display: none;}
  .el-dialog__title {color: var(--el-text-color-primary) !important;}
  .el-dialog__body {color: var(--el-text-color-primary);}
  .el-alert--warning {color: var(--snapshot-warning) !important; background: var(--snapshot-warning-soft) !important;}
  .el-alert--info {color: var(--snapshot-info) !important; background: var(--snapshot-info-soft) !important;}
  .el-alert__title {color: inherit !important;}
  .el-button.el-button.el-button.el-button {
    background-image: none !important;
    box-shadow: none !important;
    color: var(--el-text-color-regular) !important;
    background: var(--el-bg-color) !important;
    border-color: var(--el-border-color) !important;
    > span {color: inherit !important;}
    &:not(.is-disabled):is(:hover, :focus-visible, :active) {
      color: var(--snapshot-accent) !important;
      background: var(--snapshot-accent-soft) !important;
      border-color: var(--snapshot-accent) !important;
    }
    &:focus-visible {outline: 2px solid var(--snapshot-accent); outline-offset: 2px;}
    &.is-disabled.is-disabled {
      color: var(--el-text-color-placeholder) !important;
      background: var(--el-fill-color-light) !important;
      border-color: var(--el-border-color-lighter) !important;
    }
  }
}
html.dark .el-dialog.knowledge-snapshot-dialog {
  --snapshot-accent: #93c5fd;
  --snapshot-accent-soft: #1e293b;
  --snapshot-warning: #fcd34d;
  --snapshot-warning-soft: #362d1b;
  --snapshot-info: #cbd5e1;
  --snapshot-info-soft: #1e293b;
}
</style>
