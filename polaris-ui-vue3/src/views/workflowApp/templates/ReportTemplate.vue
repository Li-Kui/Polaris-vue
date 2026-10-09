<template>
  <RuntimeWorkflowRunner
    :manifest="manifest"
    :share-code="shareCode"
    submit-text="生成报告"
    input-title="报告参数"
    result-title="报告结果"
    stacked
    @completed="emit('completed', $event)"
  >
    <template #default="{ result }">
      <RuntimeReportResult :output="result" :title="manifest.pageConfig?.resultTitle || manifest.pageConfig?.title || manifest.shareName" />
    </template>
  </RuntimeWorkflowRunner>
</template>

<script setup>
import RuntimeWorkflowRunner from '@/components/workflow-app/RuntimeWorkflowRunner.vue'
import RuntimeReportResult from '@/components/workflow-app/RuntimeReportResult.vue'

defineProps({
  manifest: { type: Object, required: true },
  shareCode: { type: String, required: true }
})
const emit = defineEmits(['completed'])

</script>
