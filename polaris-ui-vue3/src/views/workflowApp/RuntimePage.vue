<template>
  <div ref="pageRef" class="runtime-page" :class="[`theme-${theme}`]">
    <!-- 加载中 -->
    <div v-if="loading" class="runtime-loading">
      <el-skeleton :rows="5" animated />
    </div>
    <!-- 错误 -->
    <div v-else-if="error" class="runtime-error">
      <el-result icon="error" :title="error" sub-title="请检查链接是否正确或联系管理员">
        <template #extra>
          <el-button @click="loadManifest">重试</el-button>
        </template>
      </el-result>
    </div>
    <!-- 正常 -->
    <template v-else>
      <RuntimeHeader v-if="!isEmbed" :title="manifest.pageConfig?.title || manifest.shareName || '应用'">
        <el-button class="runtime-theme-toggle" :aria-label="theme === 'dark' ? '切换亮色模式' : '切换暗色模式'" @click="themeOverride = theme === 'dark' ? 'light' : 'dark'">
          <el-icon><Sunny v-if="theme === 'dark'" /><Moon v-else /></el-icon><span>{{ theme === 'dark' ? '亮色模式' : '暗色模式' }}</span>
        </el-button>
      </RuntimeHeader>
      <div class="runtime-body" :class="{ 'is-embed': isEmbed }">
        <component
          :is="currentTemplate"
          v-if="currentTemplate"
          :manifest="manifest"
          :share-code="shareCode"
          @completed="notifyCompleted"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import {computed, defineAsyncComponent, onMounted, onUnmounted, provide, ref} from 'vue'
import {useRoute} from 'vue-router'
import {getShareManifest} from '@/api/workflowApp/runtime'
import {Moon, Sunny} from '@element-plus/icons-vue'
import RuntimeHeader from '@/components/workflow-app/RuntimeHeader.vue'

const route = useRoute()
const shareCode = computed(() => route.params.shareCode)
const isEmbed = computed(() => route.query.embed === '1')

const loading = ref(true)
const error = ref('')
const manifest = ref({})
const themeOverride = ref('')
const pageRef = ref(null)
let resizeObserver = null
const colorScheme = window.matchMedia('(prefers-color-scheme: dark)')
const systemDark = ref(colorScheme.matches)
const updateColorScheme = event => { systemDark.value = event.matches }

const templateMap = {
  form: defineAsyncComponent(() => import('./templates/FormTemplate.vue')),
  chat: defineAsyncComponent(() => import('./templates/ChatTemplate.vue')),
  image: defineAsyncComponent(() => import('./templates/ImageTemplate.vue')),
  compare: defineAsyncComponent(() => import('./templates/CompareTemplate.vue')),
  gallery: defineAsyncComponent(() => import('./templates/GalleryTemplate.vue')),
  report: defineAsyncComponent(() => import('./templates/ReportTemplate.vue')),
  task: defineAsyncComponent(() => import('./templates/TaskTemplate.vue')),
  query: defineAsyncComponent(() => import('./templates/QueryTemplate.vue'))
}

const loadManifest = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await getShareManifest(shareCode.value)
    manifest.value = res.data || res
    notifyParent('polaris:ready')
  } catch (err) {
    error.value = err.message || '加载失败'
    notifyParent('polaris:error', { message: error.value })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  colorScheme.addEventListener('change', updateColorScheme)
  if (window.parent !== window && !hasSameOriginParent()) {
    error.value = '请使用管理员生成的嵌入代码'
    loading.value = false
    return
  }
  if (shareCode.value) {
    loadManifest()
  } else {
    error.value = '分享码不存在'
    loading.value = false
  }
  if (isEmbed.value && window.ResizeObserver) {
    resizeObserver = new ResizeObserver(entries => {
      const height = Math.ceil(entries[0]?.contentRect?.height || 0)
      if (height > 0) notifyParent('polaris:resize', { height })
    })
    if (pageRef.value) resizeObserver.observe(pageRef.value)
  }
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  colorScheme.removeEventListener('change', updateColorScheme)
})

const notifyParent = (type, payload = {}) => {
  if (isEmbed.value && window.parent !== window) {
    window.parent.postMessage({ type, ...payload }, window.location.origin)
  }
}

const hasSameOriginParent = () => {
  try {
    return window.parent.location.origin === window.location.origin
  } catch (error) {
    return false
  }
}

const notifyCompleted = payload => {
  notifyParent('polaris:completed', {
    executionId: payload?.execution?.executionId,
    status: payload?.execution?.status
  })
}

const theme = computed(() => {
  if (themeOverride.value) return themeOverride.value
  const configured = manifest.value?.pageConfig?.theme || 'auto'
  return configured === 'auto' ? (systemDark.value ? 'dark' : 'light') : configured
})

const currentTemplate = computed(() => {
  return templateMap[manifest.value?.pageType] || templateMap.form
})
provide('runtimeTheme', theme)
</script>

<style scoped>
.runtime-page {
  --runtime-surface: #ffffff;
  --runtime-muted: #f1f5f9;
  --runtime-text: #1e293b;
  --runtime-secondary: #64748b;
  --runtime-border: #dbe2ea;
  --runtime-primary-soft: #eeecff;
  --runtime-success-soft: #ecfdf3;
  --runtime-shadow: 0 8px 30px rgb(15 23 42 / 4%);
  --workflow-primary: #4f46e5;
  --workflow-primary-text: #ffffff;
  --workflow-success: #15803d;
  --workflow-danger: #b91c1c;
  --el-color-primary: #4f46e5;
  --el-bg-color: #ffffff;
  --el-bg-color-overlay: #ffffff;
  --el-fill-color-light: #f1f5f9;
  --el-fill-color-blank: #ffffff;
  --el-text-color-primary: #1e293b;
  --el-text-color-regular: #334155;
  --el-text-color-secondary: #64748b;
  --el-border-color: #dbe2ea;
  --el-border-color-light: #dbe2ea;
  --el-border-color-lighter: #e2e8f0;
  --el-fill-color: #f1f5f9;
  --el-fill-color-lighter: #f8fafc;
  --el-fill-color-extra-light: #f8fafc;
  min-height: 100vh;
  background: linear-gradient(180deg, #f2f1fc 0, #f6f7fb 300px);
  color: var(--runtime-text);
  display: flex;
  flex-direction: column;
}
.theme-dark {
  --runtime-surface: #172033;
  --runtime-muted: #0f172a;
  --runtime-text: #f1f5f9;
  --runtime-secondary: #cbd5e1;
  --runtime-border: #334155;
  --runtime-primary-soft: #1e314d;
  --runtime-success-soft: #13372c;
  --runtime-shadow: 0 8px 30px rgb(0 0 0 / 15%);
  --workflow-primary: #38bdf8;
  --workflow-primary-text: #0f172a;
  --workflow-success: #86efac;
  --workflow-danger: #fca5a5;
  --el-color-primary: #38bdf8;
  --el-bg-color: #172033;
  --el-bg-color-overlay: #172033;
  --el-fill-color-light: #0f172a;
  --el-fill-color-blank: #172033;
  --el-text-color-primary: #f1f5f9;
  --el-text-color-regular: #e2e8f0;
  --el-text-color-secondary: #cbd5e1;
  --el-border-color: #334155;
  --el-border-color-light: #334155;
  --el-border-color-lighter: #334155;
  --el-fill-color: #0f172a;
  --el-fill-color-lighter: #1e293b;
  --el-fill-color-extra-light: #1e293b;
  background: linear-gradient(180deg, #101b30 0, #0b1220 300px);
}
.runtime-page :deep(.el-button.el-button--primary) {
  background: var(--workflow-primary) !important;
  border-color: var(--workflow-primary) !important;
  color: var(--workflow-primary-text) !important;
}
.runtime-page :deep(.el-button.el-button--primary.is-disabled) {
  opacity: 0.55;
}
.runtime-page :deep(.el-button:not(.el-button--primary)) {
  background: var(--runtime-surface) !important;
  border-color: var(--runtime-border) !important;
  color: var(--runtime-text) !important;
}
.runtime-page :deep(.el-button:focus-visible) {
  outline: 2px solid var(--workflow-primary);
  outline-offset: 2px;
}
.runtime-page :deep(.el-input__wrapper),
.runtime-page :deep(.el-select__wrapper),
.runtime-page :deep(.el-textarea__inner) {
  background: var(--runtime-surface) !important;
  color: var(--runtime-text) !important;
  border-color: var(--runtime-border) !important;
}
.runtime-page :deep(.el-input__inner),
.runtime-page :deep(.el-form-item__label) {
  color: var(--runtime-text) !important;
}
.runtime-page :deep(.json-validation) {
  color: var(--workflow-success) !important;
}
.runtime-page :deep(.json-validation.is-error) {
  color: var(--workflow-danger) !important;
}
.runtime-page :deep(.el-select-dropdown__item),
.runtime-page :deep(.el-pagination button),
.runtime-page :deep(.el-pager li) {
  color: var(--runtime-text) !important;
  background: var(--runtime-surface) !important;
}
.runtime-page :deep(.el-pager li.is-active) {
  color: var(--workflow-primary) !important;
}
.runtime-page :deep(.el-select-dropdown__item.is-selected) {
  color: var(--workflow-primary) !important;
  background: var(--runtime-primary-soft) !important;
}
.runtime-page :deep(.chat-attachment-dialog) {
  background: var(--runtime-surface) !important;
  color: var(--runtime-text) !important;
  border: 1px solid var(--runtime-border);
}
.runtime-page :deep(.chat-attachment-dialog .el-dialog__title),
.runtime-page :deep(.chat-attachment-dialog .el-dialog__body) {
  color: var(--runtime-text) !important;
}
.runtime-page :deep(.chat-attachment-dialog .el-dialog__footer .el-button.el-button--primary),
.runtime-page :deep(.chat-attachment-dialog .el-dialog__footer .el-button.el-button--primary:hover:not(.is-disabled)),
.runtime-page :deep(.chat-attachment-dialog .el-dialog__footer .el-button.el-button--primary:focus-visible) {
  background: var(--workflow-primary) !important;
  border-color: var(--workflow-primary) !important;
  color: var(--workflow-primary-text) !important;
}
.runtime-page :deep(.el-table) {
  --el-table-bg-color: var(--runtime-surface);
  --el-table-tr-bg-color: var(--runtime-surface);
  --el-table-header-bg-color: var(--runtime-muted);
  --el-table-row-hover-bg-color: var(--runtime-muted);
  --el-table-text-color: var(--runtime-text);
  --el-table-header-text-color: var(--runtime-secondary);
  --el-table-border-color: var(--runtime-border);
}
.runtime-page :deep(.el-table th.el-table__cell) { background: var(--runtime-muted) !important; color: var(--runtime-secondary) !important; }
.runtime-page :deep(.el-tag) { background: var(--runtime-muted) !important; border-color: var(--runtime-border) !important; color: var(--runtime-text) !important; }
.runtime-page :deep(.el-tag--success) { background: var(--runtime-success-soft) !important; color: var(--workflow-success) !important; }
.runtime-page :deep(.el-tag--danger) { color: var(--workflow-danger) !important; }
.runtime-page :deep(.el-tag__content) { color: inherit !important; }
.runtime-page :deep(.el-empty__description p) { color: var(--runtime-secondary) !important; }
.runtime-page :deep(.el-alert--success) { background: var(--runtime-muted); color: var(--workflow-success); }
.runtime-page :deep(.el-alert--error) { background: var(--runtime-muted); color: var(--workflow-danger); }
.runtime-page :deep(.form-section),
.runtime-page :deep(.result-section),
.runtime-page :deep(.runner-input),
.runtime-page :deep(.runner-output),
.runtime-page :deep(.runtime-header),
.runtime-page :deep(.runtime-progress),
.runtime-page :deep(.chat-template),
.runtime-page :deep(.chat-input),
.runtime-page :deep(.suggested-questions),
.runtime-page :deep(.task-history) {
  background: var(--runtime-surface);
  color: var(--runtime-text);
  border-color: var(--runtime-border);
}
.runtime-page :deep(.title),
.runtime-page :deep(.form-description),
.runtime-page :deep(.runner-description),
.runtime-page :deep(.events-list) {
  color: var(--runtime-secondary);
}
.runtime-theme-toggle {
  border-radius: 10px;
  gap: 6px;
}
.runtime-page :deep(.form-section .el-button--primary) {
  width: 100%;
}
.runtime-page :deep(.workflow-execution-input) {
  min-height: 0;
}
.runtime-page :deep(.el-button) { min-height: 38px; border-radius: 10px; }
.runtime-page :deep(.runner-actions .el-button) { min-height: 46px; font-weight: 600; }
.runtime-page :deep(.el-input__wrapper), .runtime-page :deep(.el-select__wrapper) { min-height: 44px; border-radius: 10px; }
.runtime-page :deep(.el-textarea__inner) { border-radius: 10px; padding: 12px 14px; line-height: 1.7; }
.runtime-page :deep(.el-form-item__label) { font-weight: 500; font-size: 14px; }
.runtime-page :deep(.is-simple .input-mode-switch) { background: var(--runtime-muted); width: auto; display: inline-flex; padding: 4px; }
.runtime-page :deep(.is-simple .input-mode-switch .el-radio-button) { width: auto; }
.runtime-page :deep(.is-simple .input-mode-switch .el-radio-button__inner) { font-size: 12px; padding: 9px 16px; }
.runtime-page :deep(.is-simple .input-mode-panel .schema-config) { margin-top: 20px; }
.runtime-page :deep(.el-button:not(.el-button--primary):hover:not(.is-disabled)) { border-color: var(--workflow-primary) !important; color: var(--workflow-primary) !important; }
@media (max-width: 600px) { .runtime-theme-toggle span { display: none; } }
.runtime-page :deep(.json-toolbar) {
  flex-wrap: wrap;
}
.runtime-page :deep(.el-radio-button.is-active .el-radio-button__inner) {
  color: var(--workflow-primary-text) !important;
  background: var(--workflow-primary) !important;
  border-color: var(--workflow-primary) !important;
}
.runtime-page :deep(.el-radio-button:not(.is-active) .el-radio-button__inner) {
  color: var(--runtime-text) !important;
  background: var(--runtime-surface) !important;
}
.runtime-loading, .runtime-error {
  padding: 50px;
  display: flex;
  justify-content: center;
}
.runtime-body {
  flex: 1;
  overflow: auto;
}
.runtime-body.is-embed {
  height: 100vh;
}
</style>
