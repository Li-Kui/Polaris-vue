<template>
  <aside v-if="notice" role="status" class="citation-validation-notice" :class="`citation-validation-notice--${notice.kind}`">
    {{ notice.text }}
  </aside>
</template>

<script setup>
import {computed} from 'vue'
import {knowledgeCitationNotice} from '@/utils/knowledgeEvidence'

const props = defineProps({validation: Object, streaming: Boolean, hasSources: Boolean, hideChecked: Boolean})
const notice = computed(() => {
  const value = knowledgeCitationNotice(props.validation, props.streaming, props.hasSources)
  return props.hideChecked && value?.kind === 'checked' ? null : value
})
</script>

<style scoped>
.citation-validation-notice {margin: 10px 0; padding: 10px 12px; border: 1px solid var(--el-border-color); border-left: 3px solid var(--el-color-info); border-radius: 6px; background: var(--el-fill-color-light); color: var(--el-text-color-primary); font-size: 12px; line-height: 1.8; overflow-wrap: anywhere}
.citation-validation-notice--warning {border-left-color: var(--el-color-warning)}
.citation-validation-notice--checked {border-left-color: var(--el-color-primary)}
</style>
