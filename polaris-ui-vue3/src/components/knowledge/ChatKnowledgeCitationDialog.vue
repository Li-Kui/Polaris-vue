<template>
  <el-drawer :model-value="true" class="knowledge-citation-drawer" modal-class="knowledge-citation-overlay" title="参考片段" size="min(560px, 100vw)" direction="rtl" append-to-body
             :close-on-click-modal="false" @close="emit('close')">
    <p v-if="selection.originId" class="citation-note">这是前文回答的原知识片段，本轮未重新检索。</p>
    <p class="citation-note">仅展示这条回答实际入选的片段；来源编号不等于结论已经核实。不会调用模型或重建索引。</p>
    <p v-if="preview?.documentName || selection.documentName" class="citation-heading">{{ preview?.documentName || selection.documentName }}</p>
    <nav v-if="selection.citationIds?.length > 1" class="citation-tabs" aria-label="切换同文档参考片段">
      <button v-for="id in selection.citationIds" :key="id" type="button" class="citation-tab"
              :aria-pressed="activeCitation === id" @click="selectCitation(id)">[{{ id }}]</button>
    </nav>
    <div v-loading="loading" class="citation-body">
      <el-alert v-if="error" type="warning" :closable="false" show-icon :title="error" />
      <template v-else-if="preview">
        <p class="citation-heading">[{{ preview.citationId }}] 实际入选片段</p>
        <p v-if="preview.locations?.length" class="citation-note">{{ preview.locations.join('、') }}</p>
        <p v-if="preview.occurrences?.version === 1" class="citation-note">
          此编号在原回答中{{ preview.occurrences.complete ? '出现' : '至少记录' }} {{ preview.occurrences.positions?.length || 0 }} 次。
          仅核对文字位置，不代表每处陈述获得支持。
        </p>
        <details v-if="preview.occurrences?.positions?.length" class="citation-metadata">
          <summary>查看原回答中的编号位置</summary>
          <ul><li v-for="(position, index) in preview.occurrences.positions" :key="`${position.markerStart}:${position.memberStart}`">
            第 {{ index + 1 }} 处：原文字符 {{ position.markerStart }}～{{ position.markerEnd }}（UTF-16，从 0 开始）
          </li></ul>
        </details>
        <details class="citation-metadata"><summary>技术信息</summary><p class="citation-note">索引 v{{ preview.indexVersion }} · 分块 {{ preview.chunkNo }}</p></details>
        <el-alert v-if="preview.truncated" type="info" :closable="false" title="本轮上下文预算裁剪了该片段，以下仅展示实际入选部分。" />
        <pre class="citation-text">{{ preview.text }}</pre>
      </template>
    </div>
    <template #footer><el-button @click="emit('close')">关闭</el-button></template>
  </el-drawer>
</template>

<script setup>
import {onBeforeUnmount, ref, watch} from 'vue'
import {getKnowledgeCitation} from '@/api/ai/chat'
import {getAgentChatHistoryCitation} from '@/api/ai/agentChat'

const props = defineProps({selection: {type: Object, required: true}})
const emit = defineEmits(['close'])
const loading = ref(false), error = ref(''), preview = ref(null), activeCitation = ref('')
let requestVersion = 0
async function loadPreview(selection, citationId) {
  const version = ++requestVersion
  activeCitation.value = citationId
  preview.value = null; error.value = ''; loading.value = true
  try {
    const result = selection.originId
      ? await getAgentChatHistoryCitation(selection.conversationId, selection.messageId, selection.originId, citationId)
      : await getKnowledgeCitation(selection.conversationId, selection.messageId, citationId)
    if (version !== requestVersion) return
    if (result.code !== 200 || !result.data) throw new Error('引用片段暂不可用，请关闭后重试')
    preview.value = result.data
  } catch (e) {
    if (version === requestVersion) error.value = '来源已失效、权限已变化或暂不可用；请重新提问，不以新版资料替代旧依据。'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
function selectCitation(citationId) {
  if (activeCitation.value === citationId || !/^S(?:[1-9]|[1-4][0-9]|50)$/.test(citationId) || !props.selection.citationIds?.includes(citationId)) return
  return loadPreview(props.selection, citationId)
}
watch(() => props.selection, selection => loadPreview(selection, selection.citationId), {immediate: true})
onBeforeUnmount(() => {requestVersion++})
</script>

<style scoped>
.citation-body {min-height: 120px}
.citation-note {color: var(--el-text-color-regular); font-size: 13px; line-height: 1.8; overflow-wrap: anywhere}
.citation-heading {font-weight: 600; color: var(--el-text-color-primary); overflow-wrap: anywhere}
.citation-text {white-space: pre-wrap; overflow-wrap: anywhere; padding: 16px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-fill-color-light); color: var(--el-text-color-primary); line-height: 1.8; font-family: inherit}
.citation-tabs {display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0}
.citation-tab {font: inherit; font-size: 13px; min-height: 32px; padding: 4px 12px; border: 1px solid var(--el-border-color); border-radius: 6px; color: var(--el-text-color-primary); background: var(--el-bg-color); cursor: pointer}
.citation-tab:hover, .citation-tab[aria-pressed="true"] {color: var(--citation-accent); border-color: var(--citation-accent); background: var(--citation-accent-soft)}
.citation-tab:focus-visible, .citation-metadata summary:focus-visible {outline: 2px solid var(--citation-accent); outline-offset: 2px}
.citation-metadata {font-size: 12px; color: var(--el-text-color-regular); margin: 10px 0}
.citation-metadata summary {cursor: pointer}
</style>

<style lang="scss">
// Teleport 弹窗独立覆盖旧全局玻璃背景和高优先级按钮悬停样式。
html .el-overlay.knowledge-citation-overlay {background: rgba(15, 23, 42, .12) !important;}
html.dark .el-overlay.knowledge-citation-overlay {background: rgba(0, 0, 0, .18) !important;}
html .el-drawer.knowledge-citation-drawer {
  --citation-accent: #4338ca;
  --citation-accent-soft: #eef2ff;
  --citation-warning: #92400e;
  --citation-warning-soft: #fffbeb;
  --citation-info: #475569;
  --citation-info-soft: #f1f5f9;
  background: var(--el-bg-color) !important;
  border: 1px solid var(--el-border-color-lighter) !important;
  color: var(--el-text-color-primary);
  backdrop-filter: none !important;
  &::before {display: none;}
  .el-drawer__title {color: var(--el-text-color-primary) !important;}
  .el-drawer__body {color: var(--el-text-color-primary); min-width: 0;}
  .el-alert--warning {color: var(--citation-warning) !important; background: var(--citation-warning-soft) !important;}
  .el-alert--info {color: var(--citation-info) !important; background: var(--citation-info-soft) !important;}
  .el-alert__title {color: inherit !important;}
  .el-button.el-button.el-button.el-button {
    color: var(--el-text-color-regular) !important;
    background: var(--el-bg-color) !important;
    background-image: none !important;
    border-color: var(--el-border-color) !important;
    box-shadow: none !important;
    > span {color: inherit !important;}
    &:not(.is-disabled):is(:hover, :focus-visible, :active) {
      color: var(--citation-accent) !important;
      background: var(--citation-accent-soft) !important;
      border-color: var(--citation-accent) !important;
    }
    &:focus-visible {outline: 2px solid var(--citation-accent); outline-offset: 2px;}
  }
}
html.dark .el-drawer.knowledge-citation-drawer {
  --citation-accent: #93c5fd;
  --citation-accent-soft: #1e293b;
  --citation-warning: #fcd34d;
  --citation-warning-soft: #362d1b;
  --citation-info: #cbd5e1;
  --citation-info-soft: #1e293b;
}
</style>
