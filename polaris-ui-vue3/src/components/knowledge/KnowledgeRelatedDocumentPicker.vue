<template>
  <details class="knowledge-related-picker">
    <summary>关联资料补全 <span v-if="modelValue.length">（已选 {{ modelValue.length }} 份）</span><span v-else>（可选）</span></summary>
    <p>仅本次显式补全同库资料，不自动推断关联。不扩大指定文件范围，不增加问题向量化次数；最多增加三次定向向量查询，仍不保证证据充分。</p>
    <el-select :model-value="modelValue" multiple filterable remote :remote-method="search" :multiple-limit="maxAllowed"
               :loading="loading" :disabled="disabled || !available || !indexReady || maxAllowed < 1"
               placeholder="按文件名筛选并选择需要联合核对的资料" @update:model-value="$emit('update:modelValue', $event)">
      <el-option v-for="doc in options" :key="doc.id" :value="String(doc.id)" :label="doc.name" />
    </el-select>
    <div class="knowledge-related-actions">
      <el-button link type="primary" :disabled="loading || disabled" @click="load">刷新可用资料</el-button>
      <el-button v-if="modelValue.length" link :disabled="disabled" @click="$emit('update:modelValue', [])">清空选择</el-button>
    </div>
    <p v-if="error" role="status">{{ error }}</p>
    <p v-else-if="!indexReady">索引尚未就绪，不能启用关联补全。</p>
    <p v-else-if="maxAllowed < 1 || modelValue.length > maxAllowed">返回数量需至少为关联文档数加一，请调整返回数量或减少选择。</p>
    <p v-else>最多选择 {{ maxAllowed }} 份。列表每次最多返回 100 份，请用文件名缩小范围；清空选择即使用原检索策略。</p>
  </details>
</template>

<script>
import {getKnowledgeRetrievalCapabilities, listDocuments} from '@/api/ai/knowledge'

export default {
  name: 'KnowledgeRelatedDocumentPicker',
  props: {knowledgeId: [Number, String], modelValue: {type: Array, default: () => []}, resultLimit: Number, disabled: Boolean},
  emits: ['update:modelValue', 'ready'],
  data() {return {available: false, indexReady: false, defaultResultLimit: 5, loading: false, error: '', options: [], sequence: 0}},
  computed: {
    maxAllowed() {return Math.max(0, Math.min(3, (this.resultLimit ?? this.defaultResultLimit) - 1))},
    ready() {return this.available && this.indexReady && !this.error && !this.disabled && this.modelValue.length <= this.maxAllowed}
  },
  watch: {
    knowledgeId: {immediate: true, handler() {this.sequence++; this.options = []; this.available = false; this.indexReady = false; this.loading = false; this.error = ''; this.$emit('update:modelValue', []); if (this.knowledgeId) this.load()}},
    ready: {immediate: true, handler(value) {this.$emit('ready', Boolean(value))}}
  },
  beforeUnmount() {this.sequence++; this.$emit('ready', false)},
  methods: {
    async load() {
      const sequence = ++this.sequence
      this.available = false; this.indexReady = false; this.error = ''; this.loading = true
      try {
        const response = await getKnowledgeRetrievalCapabilities(this.knowledgeId)
        if (sequence !== this.sequence) return
        if (response.data?.version !== 1 || response.data.strategy !== 'EXPLICIT_RELATED_V1'
          || !Number.isInteger(response.data.defaultResultLimit) || response.data.defaultResultLimit < 1
          || response.data.defaultResultLimit > 50) throw new Error('unavailable')
        this.available = true; this.indexReady = response.data.indexReady === true
        this.defaultResultLimit = response.data.defaultResultLimit
        await this.search('')
      } catch {
        if (sequence === this.sequence) {this.available = false; this.error = '关联补全入口暂不可用，请确认后端已更新并刷新；不会自动改用其他策略。'}
      } finally {if (sequence === this.sequence) this.loading = false}
    },
    async search(name) {
      if (!this.available || !this.knowledgeId) return
      const sequence = ++this.sequence
      this.loading = true; this.error = ''
      try {
        const response = await listDocuments({knowledgeBaseId: this.knowledgeId, name, pageNum: 1, pageSize: 100})
        if (sequence !== this.sequence) return
        const rows = response.data?.rows ?? response.rows ?? response.data?.records
        if (!Array.isArray(rows)) throw new Error('invalid')
        const retained = this.options.filter(doc => this.modelValue.includes(String(doc.id)))
        this.options = [...new Map([...retained, ...rows.filter(doc => doc.status === '2' && doc.moderationStatus === 'SAFE')].map(doc => [String(doc.id), doc])).values()]
      } catch {
        if (sequence === this.sequence) this.error = '资料列表暂不可用，请刷新或显式清空选择，不会发送未核对的关联计划。'
      } finally {if (sequence === this.sequence) this.loading = false}
    }
  }
}
</script>

<style scoped>
.knowledge-related-picker {margin: 8px 0; padding: 8px 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; color: var(--el-text-color-primary); font-size: 12px; background: var(--el-bg-color)}
.knowledge-related-picker summary {cursor: pointer; line-height: 1.8}
.knowledge-related-picker summary:focus-visible {outline: 2px solid var(--el-color-primary)}
.knowledge-related-picker p {line-height: 1.7; color: var(--el-text-color-regular); margin: 8px 0; overflow-wrap: anywhere}
.knowledge-related-picker :deep(.el-select) {width: 100%}
.knowledge-related-actions {margin-top: 8px}
.knowledge-related-actions :deep(.el-button.is-link) {color: var(--el-text-color-primary) !important; background: transparent !important}
.knowledge-related-actions :deep(.el-button.is-link > span) {color: inherit !important}
</style>
