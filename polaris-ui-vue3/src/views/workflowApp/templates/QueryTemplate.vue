<template>
  <RuntimeWorkflowRunner
    :manifest="manifest"
    :share-code="shareCode"
    submit-text="查询"
    input-title="查询条件"
    result-title="查询结果"
    @completed="emit('completed', $event)"
  >
    <template #default="{ result }">
      <RuntimeQueryResult :output="result" />
    </template>
  </RuntimeWorkflowRunner>
</template>

<script setup>
import RuntimeWorkflowRunner from '@/components/workflow-app/RuntimeWorkflowRunner.vue'
import RuntimeQueryResult from '@/components/workflow-app/RuntimeQueryResult.vue'

defineProps({
  manifest: { type: Object, required: true },
  shareCode: { type: String, required: true }
})
const emit = defineEmits(['completed'])

</script>
