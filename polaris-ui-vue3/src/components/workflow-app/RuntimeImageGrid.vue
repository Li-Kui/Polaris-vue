<template>
  <div>
    <div class="image-grid-toolbar"><span>{{ urls.length }} 张图片 · 点击放大预览</span></div>
    <div class="runtime-image-grid">
      <figure v-for="(url, index) in displayUrls" :key="url">
        <el-image :src="url" :preview-src-list="displayUrls" :initial-index="index" :alt="`结果图片 ${index + 1}`" fit="contain" preview-teleported>
          <template #error><span class="image-error">图片无法加载，请尝试打开原图</span></template>
        </el-image>
        <figcaption><span>图片 {{ index + 1 }}</span><a v-if="showDownload" :href="url" target="_blank" rel="noopener noreferrer">打开原图</a></figcaption>
      </figure>
    </div>
  </div>
</template>

<script setup>
import {computed} from 'vue'
import {resolveWorkflowAssetUrl} from '@/utils/workflowAssetUrl'

const props = defineProps({ urls: { type: Array, default: () => [] }, showDownload: { type: Boolean, default: true } })
const displayUrls = computed(() => props.urls.map(url => resolveWorkflowAssetUrl(url, import.meta.env.VITE_APP_BASE_API)))
</script>

<style scoped>
.image-grid-toolbar { color: var(--runtime-secondary, #64748b); font-size: 12px; margin-bottom: 14px; }
.runtime-image-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 1fr)); gap: 16px; }
figure { margin: 0; min-width: 0; border: 1px solid var(--runtime-border, #dbe2ea); border-radius: 16px; overflow: hidden; background: var(--runtime-surface, #fff); }
figure :deep(.el-image) { display: block; width: 100%; height: 260px; background: var(--runtime-muted, #f1f5f9); }
figcaption { padding: 16px; display: flex; justify-content: space-between; gap: 8px; font-size: 12px; color: var(--runtime-secondary, #64748b); }
figcaption a { color: var(--workflow-primary, #4f46e5); text-decoration: underline; }
.image-error { color: var(--runtime-secondary, #64748b); padding: 20px; text-align: center; }
</style>
