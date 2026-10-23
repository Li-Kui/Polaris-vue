<template>
  <details class="knowledge-sources">
    <summary class="knowledge-sources-summary">
      <el-icon class="knowledge-sources-icon"><Document /></el-icon>
      <span class="knowledge-sources-label">参考资料</span>
      <span class="knowledge-sources-count">{{ groups.length }} 份文档 · {{ fragmentCount }} 个片段</span>
      <span v-if="!message.streaming && referencedCount" class="knowledge-sources-used">回答标注 {{ referencedCount }} 份</span>
      <span class="knowledge-sources-toggle"><span class="sources-when-closed">展开</span><span class="sources-when-open">收起</span></span>
      <el-icon class="knowledge-sources-chevron"><ArrowDown /></el-icon>
      <span class="knowledge-sources-disclaimer">参考片段不代表结论已核实</span>
    </summary>
    <div class="knowledge-sources-content">
      <ul class="knowledge-source-list">
        <li v-for="doc in groups" :key="doc.key" class="knowledge-source-row">
          <el-icon class="knowledge-source-file"><Document /></el-icon>
          <div class="knowledge-source-info">
            <span class="knowledge-source-name" :title="doc.docName">{{ doc.docName }}</span>
            <span class="knowledge-source-meta">
              {{ message.streaming ? '生成中，待核对' : doc.referencedIds.length ? '回答已标注' : '本轮其他参考' }}
              <span v-if="!message.streaming && doc.referencedIds.length"> {{ formatKnowledgeCitations({citationIds: doc.referencedIds}) }}</span>
              <span v-if="doc.citationIds.length"> · 共 {{ doc.citationIds.length }} 个片段</span>
              <span v-else> · 旧记录暂无片段绑定</span>
              <span v-if="doc.matchType === 'MENTION'"> · 提及</span>
              <span v-else-if="doc.confidence != null && Number.isFinite(Number(doc.confidence))"> · 匹配分 {{ Number(doc.confidence).toFixed(3) }}</span>
            </span>
          </div>
          <el-button link type="primary" class="knowledge-source-open" :disabled="!canOpen || !doc.citationIds.length"
                     @click="emit('open', {citationId: doc.referencedIds[0] || doc.citationIds[0], citationIds: doc.citationIds, documentName: doc.docName})">
            查看片段
          </el-button>
        </li>
      </ul>
      <p v-if="notice?.kind === 'checked'" class="knowledge-sources-note">{{ notice.text }}</p>
      <p class="knowledge-sources-note">来源编号用于核对文档；匹配分表示相关度，不代表片段足以支持答案。来源失效时不会改用新版。</p>
    </div>
  </details>
</template>

<script setup>
import {computed} from 'vue'
import {ArrowDown, Document} from '@element-plus/icons-vue'
import {formatKnowledgeCitations, knowledgeCitationNotice, knowledgeSourceGroups} from '@/utils/knowledgeEvidence'

const props = defineProps({message: {type: Object, required: true}})
const emit = defineEmits(['open'])
const groups = computed(() => knowledgeSourceGroups(props.message.attachedDocs, props.message.content))
const fragmentCount = computed(() => new Set(groups.value.flatMap(doc => doc.citationIds)).size)
const referencedCount = computed(() => groups.value.filter(doc => doc.referencedIds.length).length)
const canOpen = computed(() => Boolean(props.message.id && !props.message.streaming && props.message.moderationStatus !== 'INTERRUPTED_BLOCKED'))
const notice = computed(() => knowledgeCitationNotice(props.message.citationValidation, props.message.streaming, true))
</script>

<style scoped>
.knowledge-sources {margin-top: 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; background: var(--el-fill-color-blank); color: var(--el-text-color-primary); min-width: 0}
.knowledge-sources-summary {display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 12px 14px; cursor: pointer; list-style: none; font-size: 13px; line-height: 1.6; border-radius: 10px}
.knowledge-sources-summary::-webkit-details-marker {display: none}
.knowledge-sources-summary:hover {background: var(--el-fill-color-light)}
.knowledge-sources-summary:focus-visible {outline: 2px solid var(--el-color-primary); outline-offset: 2px}
.knowledge-sources-icon {color: var(--el-text-color-regular)}
.knowledge-sources-label {font-weight: 600}
.knowledge-sources-disclaimer {flex-basis: 100%; color: var(--el-text-color-regular); font-size: 12px; line-height: 1.5}
.knowledge-sources-count, .knowledge-sources-used {color: var(--el-text-color-regular); font-size: 12px}
.knowledge-sources-used {padding-left: 8px; border-left: 1px solid var(--el-border-color)}
.knowledge-sources-toggle {margin-left: auto; color: var(--el-text-color-regular); font-size: 12px}
.knowledge-sources-chevron {color: var(--el-text-color-regular); transition: transform .2s}
.knowledge-sources[open] .knowledge-sources-chevron {transform: rotate(180deg)}
.knowledge-sources .sources-when-open, .knowledge-sources[open] .sources-when-closed {display: none}
.knowledge-sources[open] .sources-when-open {display: inline}
.knowledge-sources-content {padding: 0 14px 12px; border-top: 1px solid var(--el-border-color-lighter)}
.knowledge-sources-note {font-size: 12px; line-height: 1.7; color: var(--el-text-color-regular); margin: 10px 0; overflow-wrap: anywhere}
.knowledge-source-list {list-style: none; padding: 0; margin: 0}
.knowledge-source-row {display: flex; align-items: center; gap: 10px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter)}
.knowledge-source-row:last-child {border-bottom: none}
.knowledge-source-file {flex: 0 0 auto; color: var(--el-text-color-regular)}
.knowledge-source-info {display: flex; flex: 1; flex-direction: column; min-width: 0; gap: 4px}
.knowledge-source-name {font-size: 13px; font-weight: 500; overflow-wrap: anywhere}
.knowledge-source-meta {font-size: 12px; line-height: 1.6; color: var(--el-text-color-regular); overflow-wrap: anywhere}
@media (max-width: 600px) {
  .knowledge-sources-used {border-left: none; padding-left: 0}
  .knowledge-source-row {flex-wrap: wrap}
  .knowledge-source-info {flex-basis: calc(100% - 28px)}
  .knowledge-source-open {margin-left: 24px !important}
}
</style>

<style>
html .knowledge-sources .knowledge-source-open.el-button.is-link {
  color: #3730a3 !important; background: #eef2ff !important; border: 1px solid #c7d2fe !important;
  border-radius: 6px; padding: 6px 10px; min-height: 32px; margin: 0; font-size: 12px; flex: 0 0 auto;
}
html .knowledge-sources .knowledge-source-open.el-button.is-link > span {color: inherit !important}
html .knowledge-sources .knowledge-source-open.el-button.is-link:not(.is-disabled):not(:disabled):is(:hover, :active, :focus-visible) {color: #312e81 !important; background: #e0e7ff !important; text-decoration: underline}
html .knowledge-sources .knowledge-source-open.el-button.is-link:focus-visible {outline: 2px solid #4f46e5; outline-offset: 2px}
html .knowledge-sources .knowledge-source-open.el-button.is-link:is(.is-disabled, :disabled) {color: #475569 !important; opacity: 1; cursor: not-allowed}
html.dark .knowledge-sources .knowledge-source-open.el-button.is-link {color: #c7d2fe !important; background: #252544 !important; border-color: #6366f1 !important}
html.dark .knowledge-sources .knowledge-source-open.el-button.is-link:not(.is-disabled):not(:disabled):is(:hover, :active, :focus-visible) {color: #e0e7ff !important; background: #37305d !important}
html.dark .knowledge-sources .knowledge-source-open.el-button.is-link:is(.is-disabled, :disabled) {color: #94a3b8 !important}
html.dark .knowledge-sources .knowledge-source-open.el-button.is-link:focus-visible {outline-color: #a5b4fc}
</style>
