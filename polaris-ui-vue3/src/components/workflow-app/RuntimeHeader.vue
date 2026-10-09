<template>
  <header class="runtime-header">
    <div class="header-inner">
      <div class="header-brand"><span class="brand-icon"><el-icon><Connection /></el-icon></span><div class="title">{{ title }}</div></div>
      <div class="header-actions"><slot /></div>
    </div>
  </header>
</template>

<script setup>
import {Connection} from '@element-plus/icons-vue'

defineProps({
  title: {
    type: String,
    default: '应用'
  }
})
</script>

<style scoped>
.runtime-header {
  min-height: 64px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: var(--runtime-surface, #fff);
  border-bottom: 1px solid var(--runtime-border, #e4e7ed);
  position: sticky;
  top: 0;
  z-index: 100;
}
.title {
  font-size: 16px;
  font-weight: 600;
  color: var(--runtime-text, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.header-inner { display: flex; align-items: center; justify-content: space-between; gap: 20px; width: 100%; max-width: 1152px; margin: 0 auto; }
.header-brand { display: flex; align-items: center; gap: 12px; min-width: 0; }
.brand-icon { display: grid; place-items: center; flex: 0 0 34px; height: 34px; border-radius: 10px; background: var(--runtime-primary-soft, #eeecff); color: var(--workflow-primary, #4f46e5); font-size: 20px; }
.header-actions { flex-shrink: 0; }
@media (max-width: 600px) { .runtime-header { padding: 0 16px; } .header-inner { gap: 12px; } .header-brand { gap: 8px; } .title { font-size: 14px; } }
</style>
