<template>
  <div ref="pageRef" class="runtime-page" :class="[`theme-${theme}`, `page-${pageType}`, { 'is-embed': isEmbed }]">
    <div class="runtime-shell">
      <RuntimeHeader v-if="!isEmbed">
        <el-dropdown placement="bottom-end" trigger="click" :teleported="false" popper-class="runtime-theme-menu" @command="changeTheme">
          <el-button class="runtime-theme-toggle" :aria-label="`切换主题，当前${themeLabel}`" :title="`当前${themeLabel}`">
            <el-icon><Sunny v-if="theme === 'dark'" /><Moon v-else /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="choice in themeChoices" :key="choice.value" :command="choice.value" :class="{ 'is-selected': themeMode === choice.value }">
                <el-icon><component :is="choice.icon" /></el-icon><span>{{ choice.label }}</span>
                <el-icon v-if="themeMode === choice.value"><Check /></el-icon>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </RuntimeHeader>
      <div v-if="loading" class="runtime-loading" aria-label="正在加载应用"><el-skeleton :rows="5" animated /></div>
      <div v-else-if="error" class="runtime-error">
        <el-result icon="error" :title="error" sub-title="请检查链接是否正确或联系管理员">
          <template #extra><el-button @click="loadManifest">重试</el-button></template>
        </el-result>
      </div>
      <template v-else>
        <RuntimeAppIntro :page-type="pageType" :title="manifest.pageConfig?.title || manifest.shareName" :description="manifest.pageConfig?.description" />
        <main class="runtime-body">
          <component :is="currentTemplate" v-if="currentTemplate" :manifest="manifest" :share-code="shareCode" @completed="notifyCompleted" />
        </main>
      </template>
      <footer v-if="!isEmbed" class="runtime-footer">由北辰 AI 提供支持</footer>
    </div>
  </div>
</template>

<script setup>
import {computed, defineAsyncComponent, onMounted, onUnmounted, provide, ref} from 'vue'
import {useRoute} from 'vue-router'
import {getShareManifest} from '@/api/workflowApp/runtime'
import {Check, Monitor, Moon, Sunny} from '@element-plus/icons-vue'
import RuntimeHeader from '@/components/workflow-app/RuntimeHeader.vue'
import RuntimeAppIntro from '@/components/workflow-app/RuntimeAppIntro.vue'

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

const configuredTheme = computed(() => ['light', 'dark'].includes(manifest.value?.pageConfig?.theme) ? manifest.value.pageConfig.theme : 'auto')
const themeMode = computed(() => themeOverride.value || configuredTheme.value)
const theme = computed(() => themeMode.value === 'auto' ? (systemDark.value ? 'dark' : 'light') : themeMode.value)
const themeChoices = [
  { value: 'auto', label: '跟随系统', icon: Monitor },
  { value: 'light', label: '亮色模式', icon: Sunny },
  { value: 'dark', label: '暗色模式', icon: Moon }
]
const themeLabel = computed(() => themeChoices.find(choice => choice.value === themeMode.value)?.label)
const changeTheme = value => { if (themeChoices.some(choice => choice.value === value)) themeOverride.value = value }
const pageType = computed(() => Object.hasOwn(templateMap, manifest.value?.pageType) ? manifest.value.pageType : 'form')

const currentTemplate = computed(() => {
  return templateMap[pageType.value]
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
  --workflow-warning: #a16207;
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
  min-height: 100dvh;
  box-sizing: border-box;
  padding: 24px 16px;
  color-scheme: light;
  background: linear-gradient(180deg, #f2f1fc 0, #f6f7fb 300px);
  color: var(--runtime-text);
  display: flex;
  flex-direction: column;
  align-items: center;
}
.theme-dark {
  color-scheme: dark;
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
  --workflow-warning: #fcd34d;
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
.runtime-page :deep(.form-description),
.runtime-page :deep(.runner-description),
.runtime-page :deep(.events-list) {
  color: var(--runtime-secondary);
}
.runtime-theme-toggle { width: 38px; height: 38px; padding: 0; border-radius: 10px; font-size: 18px; }
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
.runtime-page :deep(.is-simple .input-mode-panel), .runtime-page :deep(.is-simple .input-mode-panel .schema-config) { margin-top: 0; }
.runtime-page :deep(.el-button:not(.el-button--primary):hover:not(.is-disabled)) { border-color: var(--workflow-primary) !important; color: var(--workflow-primary) !important; }

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
  padding: 32px 22px;
  display: flex;
  justify-content: center;
}
.runtime-shell { display: flex; flex-direction: column; width: 420px; max-width: 100%; min-height: calc(100dvh - 48px); background: var(--runtime-surface); border: 1px solid var(--runtime-border); border-radius: 22px; box-shadow: var(--runtime-shadow); overflow: visible; }
.runtime-body { flex: 1; min-width: 0; }
.runtime-footer { flex-shrink: 0; margin: auto 22px 0; padding: 18px 0 max(18px, env(safe-area-inset-bottom)); border-top: 1px solid var(--runtime-border); color: var(--runtime-secondary); text-align: center; font-size: 12px; line-height: 1.5; }
.page-chat .runtime-shell { height: calc(100dvh - 48px); min-height: 620px; }
.page-chat .runtime-body { display: flex; min-height: 0; overflow: hidden; }
.page-chat :deep(.runtime-app-intro) { flex-shrink: 0; padding-top: 20px; padding-bottom: 18px; }
.runtime-page.is-embed { padding: 0; min-height: 0; background: var(--runtime-surface); }
.is-embed .runtime-shell { width: 100%; min-height: 0; border: 0; border-radius: 0; box-shadow: none; }
.page-chat.is-embed .runtime-shell { height: 100dvh; min-height: 0; }
@media (max-width: 480px) {
  .runtime-page { padding: 0; }
  .runtime-shell { width: 100%; min-height: 100dvh; border: 0; border-radius: 0; box-shadow: none; }
  .page-chat .runtime-shell { height: 100dvh; min-height: 0; }
}
.runtime-page :deep(.runtime-theme-menu) { min-width: 160px; max-width: calc(100vw - 32px); background: var(--runtime-surface); border-color: var(--runtime-border); }
.runtime-page :deep(.runtime-theme-menu .el-dropdown-menu) { padding: 6px; background: var(--runtime-surface); }
.runtime-page :deep(.runtime-theme-menu .el-dropdown-menu__item) { display: flex; gap: 9px; min-height: 40px; border-radius: 7px; color: var(--runtime-text) !important; background: transparent !important; }
.runtime-page :deep(.runtime-theme-menu .el-dropdown-menu__item:hover), .runtime-page :deep(.runtime-theme-menu .is-selected), .runtime-page :deep(.runtime-theme-menu .el-dropdown-menu__item:focus) { background: var(--runtime-primary-soft) !important; color: var(--workflow-primary) !important; }
.runtime-page :deep(.runtime-theme-menu .el-popper__arrow::before) { background: var(--runtime-surface); border-color: var(--runtime-border); }
.runtime-page :deep(.el-input__wrapper), .runtime-page :deep(.el-select__wrapper), .runtime-page :deep(.el-textarea__inner) { box-shadow: 0 0 0 1px var(--runtime-border) inset; }
.runtime-page :deep(.el-input__wrapper.is-focus), .runtime-page :deep(.el-select__wrapper.is-focused), .runtime-page :deep(.el-textarea__inner:focus) { box-shadow: 0 0 0 1px var(--workflow-primary) inset; }
.runtime-page :deep(.el-input__inner::placeholder), .runtime-page :deep(.el-textarea__inner::placeholder), .runtime-page :deep(.el-select__placeholder.is-transparent) { color: var(--runtime-secondary); opacity: .85; }
.runtime-page :deep(.el-form-item) { margin-bottom: 22px; }
.runtime-page :deep(.el-input__count), .runtime-page :deep(.el-input-number__decrease), .runtime-page :deep(.el-input-number__increase) { background: var(--runtime-muted); color: var(--runtime-secondary); border-color: var(--runtime-border); }
.runtime-page :deep(.el-select-dropdown), .runtime-page :deep(.el-select__popper), .runtime-page :deep(.el-popper__arrow::before) { background: var(--runtime-surface); border-color: var(--runtime-border); }
.runtime-page :deep(.el-select-dropdown__item.is-hovering) { background: var(--runtime-primary-soft) !important; }
.runtime-page :deep(.el-select__popper .el-select-dropdown__item.is-disabled) { opacity: .5; }
.runtime-page :deep(.runner-actions .el-button--primary) { box-shadow: 0 4px 12px color-mix(in srgb, var(--workflow-primary) 16%, transparent); }
.runtime-page :deep(.el-button:disabled) { cursor: not-allowed; }
.runtime-page :deep(.el-switch) { --el-switch-on-color: var(--workflow-primary); --el-switch-off-color: var(--runtime-border); }
.runtime-page :deep(.el-alert) { overflow-wrap: anywhere; }
.runtime-page :deep(.el-skeleton) { --el-skeleton-color: var(--runtime-muted); --el-skeleton-to-color: var(--runtime-border); width: 100%; }
.runtime-page :deep(.el-result__title p) { color: var(--runtime-text); }
.runtime-page :deep(.el-result__subtitle p) { color: var(--runtime-secondary); }
@media (prefers-reduced-motion: reduce) { .runtime-page :deep(*) { scroll-behavior: auto; transition: none !important; } }
.runtime-page :deep(.el-button.is-link) { min-height: 32px; padding: 4px 0; background: transparent !important; border-color: transparent !important; box-shadow: none !important; color: var(--runtime-secondary) !important; }
.runtime-page :deep(.task-item-actions .el-button.is-link) { color: var(--workflow-primary) !important; }
.runtime-page :deep(.el-tag--warning) { background: var(--runtime-muted) !important; color: var(--workflow-warning) !important; }
.runtime-page :deep(.el-tag--info) { background: var(--runtime-primary-soft) !important; color: var(--workflow-primary) !important; }
.runtime-page :deep(.el-message) { --el-message-bg-color: var(--runtime-surface); --el-message-border-color: var(--runtime-border); --el-message-text-color: var(--runtime-text); max-width: calc(100vw - 32px); background: var(--runtime-surface); border-color: var(--runtime-border); }
.runtime-page :deep(.el-message__content) { color: var(--runtime-text); overflow-wrap: anywhere; }
.runtime-page :deep(.el-form-item__content) { min-width: 0; }
.runtime-page :deep(.el-form-item__label) { height: auto; line-height: 1.6; margin-bottom: 8px; }
.runtime-page :deep(.field-label) { min-width: 0; white-space: normal; overflow-wrap: anywhere; }
.runtime-page :deep(.el-button) { max-width: 100%; }
.runtime-page :deep(.el-button > span) { min-width: 0; white-space: normal; overflow-wrap: anywhere; }
.runtime-page :deep(.query-toolbar), .runtime-page :deep(.result-toolbar) { flex-wrap: wrap; }
</style>
