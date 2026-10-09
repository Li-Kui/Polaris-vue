<template>
  <div ref="reportElement" class="report-result">
    <div class="report-toolbar">
      <span>{{ title || '报告内容' }}</span>
      <div>
        <el-button size="small" @click="copyReport">复制报告</el-button>
        <el-button size="small" :loading="exportingPdf" :disabled="exportingPdf" @click="exportPdf">导出 PDF</el-button>
        <el-button size="small" @click="exportReport">导出原始数据</el-button>
      </div>
    </div>
    <PolarisReportEngine v-if="structured" :class="{ dark: theme === 'dark' }" :report-title="title" :content="content" :structured-schema="structured" theme="glass-light" />
    <RuntimeContent v-else-if="content" :content="content" />
    <RuntimeResult v-else :output="output" :show-toolbar="false" />
  </div>
</template>

<script setup>
import {computed, inject, ref} from 'vue'
import {ElMessage} from 'element-plus'
import RuntimeContent from './RuntimeContent.vue'
import RuntimeResult from './RuntimeResult.vue'
import PolarisReportEngine from '@/views/ai/report/PolarisReportEngine.vue'
import {
  copyRuntimeText,
  downloadRuntimeFile,
  extractRuntimeText,
  parseRuntimeValue,
  runtimeRows,
  unwrapRuntimeOutput
} from '@/utils/workflowRuntime'
import {exportRuntimeReportPdf} from '@/utils/workflowReportPdf'

const props = defineProps({ output: { type: [Object, Array, String, Number, Boolean], default: null }, title: { type: String, default: '' } })
const theme = inject('runtimeTheme', ref('light'))
const reportElement = ref(null)
const exportingPdf = ref(false)
const normalized = computed(() => unwrapRuntimeOutput(props.output))
const content = computed(() => extractRuntimeText(normalized.value))
const structured = computed(() => {
  const value = parseRuntimeValue(normalized.value?.report || normalized.value?.structuredSchema || normalized.value)
  return value && ['executiveSummary', 'kpiCards', 'kpiGroup', 'reportStats', 'visualizations', 'chartData', 'compareMatrix', 'swot', 'actionPlan'].some(key => value[key]) ? value : null
})
const reportText = computed(() => content.value || JSON.stringify(normalized.value, null, 2) || '')
const copyReport = async () => {
  try { await copyRuntimeText(reportText.value); ElMessage.success('报告已复制') }
  catch (error) { ElMessage.error(error.message || '复制失败') }
}
const exportReport = () => downloadRuntimeFile(reportText.value, content.value ? '报告.md' : '报告.json')
const exportPdf = async () => {
  if (exportingPdf.value) return
  exportingPdf.value = true
  try {
    await exportRuntimeReportPdf(reportElement.value, {
      title: props.title,
      rows: structured.value || content.value ? null : runtimeRows(normalized.value)
    })
    ElMessage.success('PDF 已生成，浏览器开始下载')
  } catch (error) {
    ElMessage.error(error.message || 'PDF 生成失败，请稍后重试或导出原始数据')
  } finally {
    exportingPdf.value = false
  }
}
</script>

<style scoped>
.report-toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 20px; color: var(--runtime-text, #1e293b); font-size: 14px; font-weight: 600; }
.report-toolbar > div { display: flex; flex-wrap: wrap; gap: 8px; }
.report-toolbar :deep(.el-button) { margin: 0; }
.report-result :deep(.polaris-report-engine.theme-glass-light) { background: var(--runtime-muted, #f1f5f9); color: var(--runtime-text, #1e293b); padding: 18px; }
.report-result :deep(.summary-glass-card), .report-result :deep(.kpi-stat-card), .report-result :deep(.compare-matrix-card), .report-result :deep(.chart-block-card), .report-result :deep(.action-plan-card), .report-result :deep(.swot-matrix-card), .report-result :deep(.content-section-card) { background: var(--runtime-surface, #fff) !important; color: var(--runtime-text, #1e293b) !important; border-color: var(--runtime-border, #dbe2ea) !important; }
.report-result :deep(.header-title), .report-result :deep(.summary-content), .report-result :deep(.kpi-label), .report-result :deep(.kpi-value), .report-result :deep(.section-html-body), .report-result :deep(.section-title), .report-result :deep(.column-title), .report-result :deep(.column-list) { color: var(--runtime-text, #1e293b) !important; }
.report-result :deep(.polaris-report-engine.theme-glass-light .el-table .el-table__cell) { background: var(--runtime-surface, #fff) !important; color: var(--runtime-text, #1e293b) !important; border-color: var(--runtime-border, #dbe2ea) !important; }
.report-result :deep(.polaris-report-engine.theme-glass-light .el-table th.el-table__cell) { background: var(--runtime-muted, #f1f5f9) !important; }
.report-result :deep(.polaris-report-engine.theme-glass-light .el-table .cell) { color: var(--runtime-text, #1e293b) !important; }
.report-result :deep(.kpi-stat-icon) { background: var(--runtime-muted, #f1f5f9) !important; color: var(--workflow-primary, #4f46e5) !important; }
@media (max-width: 600px) {
  .report-result :deep(.polaris-report-engine.theme-glass-light) { padding: 12px; }
  .report-result :deep(.engine-report-banner) { padding: 18px !important; }
  .report-result :deep(.banner-title) { font-size: 20px !important; line-height: 1.5 !important; }
  .report-result :deep(.banner-badge) { font-size: 11px !important; padding: 6px 8px !important; }
  .report-result :deep(.card-header) { flex-wrap: wrap; gap: 8px; }
  .report-result :deep(.header-title) { font-size: 14px !important; overflow-wrap: anywhere; }
  .report-result :deep(.kpi-grid-container) { grid-template-columns: 1fr !important; }
}
</style>
