<template>
  <div class="query-result">
    <div v-if="rows" class="query-toolbar">
      <span>共 {{ rows.length }} 条记录</span>
      <el-button size="small" :disabled="!rows.length" @click="exportRows">导出 CSV</el-button>
    </div>
    <el-radio-group v-if="numericFields.length" v-model="view" class="query-view" aria-label="查询结果展示方式">
      <el-radio-button value="table">数据表格</el-radio-button><el-radio-button value="chart">图表分析</el-radio-button>
    </el-radio-group>
    <template v-if="view === 'chart' && numericFields.length">
      <div class="chart-options">
        <label>分类字段<el-select v-model="category" :teleported="false"><el-option v-for="field in fields" :key="field" :label="field" :value="field" /></el-select></label>
        <label>数值字段<el-select v-model="metric" :teleported="false"><el-option v-for="field in numericFields" :key="field" :label="field" :value="field" /></el-select></label>
        <label>图表类型<el-select v-model="chartType" :teleported="false"><el-option label="柱状图" value="bar" /><el-option label="折线图" value="line" /></el-select></label>
      </div>
      <div ref="chartRef" class="query-chart" role="img" :aria-label="`${category}与${metric}的${chartType === 'bar' ? '柱状图' : '折线图'}`"></div>
      <p class="query-hint">展示前 50 条记录；完整数据可切换表格或导出。</p>
    </template>
    <el-empty v-else-if="rows && !rows.length" description="未找到匹配数据，请调整查询条件" :image-size="60" />
    <RuntimeResult v-else :output="rows || output" />
  </div>
</template>

<script setup>
import {computed, inject, nextTick, onMounted, onUnmounted, ref, watch} from 'vue'
import * as echarts from 'echarts'
import RuntimeResult from './RuntimeResult.vue'
import {downloadRuntimeFile, runtimeCsv, runtimeRows} from '@/utils/workflowRuntime'

const props = defineProps({ output: { type: [Object, Array, String, Number, Boolean], default: null } })
const theme = inject('runtimeTheme', ref('light'))
const rows = computed(() => {
  const value = runtimeRows(props.output)
  return value?.every(row => row && typeof row === 'object' && !Array.isArray(row)) ? value : null
})
const fields = computed(() => [...new Set((rows.value || []).flatMap(row => Object.keys(row)))].filter(key => (rows.value || []).every(row => row[key] == null || typeof row[key] !== 'object')))
const numericFields = computed(() => fields.value.filter(key => rows.value.some(row => typeof row[key] === 'number' && Number.isFinite(row[key]))
  && rows.value.every(row => row[key] == null || (typeof row[key] === 'number' && Number.isFinite(row[key])))))
const view = ref('table')
const category = ref('')
const metric = ref('')
const chartType = ref('bar')
const chartRef = ref(null)
let chart = null
let observer = null
watch(() => props.output, () => {
  view.value = 'table'
  category.value = fields.value.find(key => !numericFields.value.includes(key)) || fields.value[0] || ''
  metric.value = numericFields.value[0] || ''
}, { immediate: true })
const renderChart = async () => {
  await nextTick()
  observer?.disconnect()
  chart?.dispose()
  chart = null
  if (!chartRef.value || view.value !== 'chart' || !metric.value) return
  chart = echarts.init(chartRef.value)
  const data = rows.value.slice(0, 50)
  const text = theme.value === 'dark' ? '#e2e8f0' : '#334155'
  chart.setOption({
    color: [theme.value === 'dark' ? '#38bdf8' : '#4f46e5'],
    tooltip: { trigger: 'axis', renderMode: 'richText' },
    grid: { left: 45, right: 20, top: 24, bottom: 65, containLabel: true },
    xAxis: { type: 'category', data: data.map((row, index) => String(row[category.value] ?? index + 1)), axisLabel: { color: text, rotate: data.length > 8 ? 30 : 0 } },
    yAxis: { type: 'value', axisLabel: { color: text }, splitLine: { lineStyle: { color: theme.value === 'dark' ? '#334155' : '#e2e8f0' } } },
    series: [{ name: metric.value, type: chartType.value, data: data.map(row => row[metric.value] ?? null), connectNulls: false }]
  })
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(chartRef.value)
}
watch([view, category, metric, chartType, theme], renderChart)
onMounted(renderChart)
onUnmounted(() => { observer?.disconnect(); chart?.dispose() })
const exportRows = () => downloadRuntimeFile(runtimeCsv(rows.value), '查询结果.csv', 'text/csv;charset=utf-8')
</script>

<style scoped>
.query-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; color: var(--runtime-secondary, #64748b); font-size: 13px; }
.query-view { margin-bottom: 18px; }
.chart-options { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; }
.chart-options label { display: flex; flex-direction: column; gap: 8px; font-size: 12px; color: var(--runtime-secondary, #64748b); }
.query-chart { width: 100%; height: 340px; margin-top: 12px; }
.query-hint { color: var(--runtime-secondary, #64748b); font-size: 12px; }
</style>
