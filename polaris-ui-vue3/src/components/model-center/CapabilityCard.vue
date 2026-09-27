<template>
  <el-card class="capability-card" shadow="never">
    <template #header>
      <div class="header">
        <div><b>{{ item.schema.name }}</b></div>
        <el-tag :type="item.schema.kind === 'INVOCATION' ? 'primary' : 'success'" round>{{ item.schema.kind === 'INVOCATION' ? '调用能力' : '增强特性' }}</el-tag>
      </div>
    </template>
    <el-form label-position="top">
    <SchemaFormRenderer
      ref="renderer"
      :schema="item.schema.schema"
      :ui-schema="item.schema.uiSchema"
      :model-value="item.config"
      :options="item.options"
      @update:model-value="$emit('update-config', $event)"
    />
    </el-form>
  </el-card>
</template>

<script setup>
import {ref} from 'vue'
import SchemaFormRenderer from './SchemaFormRenderer.vue'

defineProps({ item: { type: Object, required: true } })
defineEmits(['update-config'])
const renderer = ref()
defineExpose({ hasUnsupportedComponent: () => renderer.value?.hasUnsupportedComponent?.() === true })
</script>

<style scoped>
.capability-card { margin-bottom: 14px; border-color: var(--mc-border); border-radius: 16px; color: var(--mc-text); background: var(--mc-surface); box-shadow: 0 8px 24px rgba(15, 23, 42, .05); }
.capability-card :deep(.el-card__header) { border-bottom-color: var(--mc-border); background: var(--mc-surface-muted); }
.capability-card :deep(.el-card__body) { background: var(--mc-surface); }
.header { display: flex; align-items: center; justify-content: space-between; }
.header b { color: var(--mc-text); }.header span { margin-left: 8px; color: var(--mc-muted); font: 12px ui-monospace, monospace; }
.header :deep(.el-tag) { border-color: rgba(37, 99, 235, .25) !important; color: var(--mc-accent) !important; background: var(--mc-accent-soft) !important; }
</style>
