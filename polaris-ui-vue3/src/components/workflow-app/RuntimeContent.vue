<template>
  <div class="runtime-content" v-html="renderRuntimeMarkdown(content)"></div>
</template>

<script setup>
import {renderRuntimeMarkdown} from '@/utils/workflowRuntime'

defineProps({ content: { type: String, default: '' } })
</script>

<style scoped>
.runtime-content { line-height: 1.75; overflow-wrap: anywhere; color: var(--runtime-text, #1e293b); }
.runtime-content :deep(p) { margin: 0 0 10px; white-space: pre-wrap; }
.runtime-content :deep(p:last-child) { margin-bottom: 0; }
.runtime-content :deep(h1), .runtime-content :deep(h2), .runtime-content :deep(h3) { margin: 16px 0 10px; line-height: 1.4; }
.runtime-content :deep(h1) { font-size: 28px; }
.runtime-content :deep(h2) { font-size: 21px; }
.runtime-content :deep(h3) { font-size: 17px; }
.runtime-content :deep(:first-child) { margin-top: 0; }
.runtime-content :deep(pre) { padding: 14px; border-radius: 8px; overflow: auto; background: var(--runtime-muted, #f1f5f9); white-space: pre; }
.runtime-content :deep(code) { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.9em; }
.runtime-content :deep(a) { color: var(--workflow-primary, #4f46e5); text-decoration: underline; }
.runtime-content :deep(ul) { padding-left: 22px; }
@media (max-width: 600px) { .runtime-content :deep(h1) { font-size: 24px; } .runtime-content :deep(h2) { font-size: 19px; } }
</style>
