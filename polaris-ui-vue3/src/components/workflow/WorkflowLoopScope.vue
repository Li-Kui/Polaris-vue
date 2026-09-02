<template>
  <section class="loop-scope-frame">
    <header>
      <span><el-icon><Refresh /></el-icon><strong>{{ data.label }}</strong></span>
      <small>循环范围 · 每轮完成后自动进入下一项</small>
    </header>
  </section>
</template>

<script>
import {Refresh} from '@element-plus/icons-vue'

export default {
  name: 'WorkflowLoopScope',
  components: {Refresh},
  props: {data: {type: Object, required: true}}
}
</script>

<style scoped>
.loop-scope-frame{width:100%;height:100%;border:1.5px dashed color-mix(in srgb,var(--workflow-primary,var(--el-color-primary)) 55%,transparent);border-radius:18px;background:color-mix(in srgb,var(--workflow-primary-soft,var(--el-color-primary-light-9)) 55%,transparent);pointer-events:none}.loop-scope-frame header{height:34px;padding:0 12px;display:flex;align-items:center;justify-content:space-between;gap:12px;color:var(--workflow-primary,var(--el-color-primary))}.loop-scope-frame header span{display:flex;align-items:center;gap:6px}.loop-scope-frame header strong{font-size:12px}.loop-scope-frame header small{font-size:10px;color:var(--el-text-color-secondary)}
</style>
