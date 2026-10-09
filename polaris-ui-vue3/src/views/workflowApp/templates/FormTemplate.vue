<template>
  <RuntimeWorkflowRunner :manifest="manifest" :share-code="shareCode" stacked @completed="emit('completed', $event)" />
</template>

<script setup>
import RuntimeWorkflowRunner from '@/components/workflow-app/RuntimeWorkflowRunner.vue'

defineProps({ manifest: { type: Object, required: true }, shareCode: { type: String, required: true } })
const emit = defineEmits(['completed'])
</script>
