<template>
  <div class="runtime-progress" role="status" aria-live="polite">
    <span class="progress-icon"><el-icon class="is-loading"><Loading /></el-icon></span>
    <div><h3>正在处理您的请求</h3><p>完成后将在这里展示结果，等待期间可以取消。</p></div>
  </div>
</template>

<script setup>
import {Loading} from '@element-plus/icons-vue'

defineProps({ events: { type: Array, default: () => [] }, status: { type: String, default: 'idle' } })
</script>

<style scoped>
.runtime-progress { display: flex; align-items: center; gap: 16px; padding: 24px 0; color: var(--runtime-text, #1e293b); }
.progress-icon { display: grid; place-items: center; flex: 0 0 48px; height: 48px; border-radius: 16px; background: var(--runtime-primary-soft, #eeecff); color: var(--workflow-primary, #4f46e5); font-size: 24px; }
h3 { margin: 0 0 8px; font-size: 16px; font-weight: 600; }
p { margin: 0; color: var(--runtime-secondary, #64748b); font-size: 13px; line-height: 1.7; }
@media (prefers-reduced-motion: reduce) { .is-loading { animation: none; } }
</style>
