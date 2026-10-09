<template>
  <div class="runtime-result">
    <div v-if="showToolbar" class="result-toolbar">
      <span>结果</span>
      <el-button size="small" @click="copyResult">复制结果</el-button>
    </div>
    <el-empty v-if="value == null || value === ''" description="执行已完成，未返回内容" :image-size="56" />
    <div v-else-if="outputType === 'image'" class="result-image">
      <el-image :src="displayImageUrl" :preview-src-list="[displayImageUrl]" fit="contain" />
    </div>
    <RuntimeContent v-else-if="outputType === 'text'" :content="String(singleValue)" />
    <div v-else-if="outputType === 'table'" class="result-table">
      <el-table :data="pageRows" border style="width: 100%" max-height="480">
        <el-table-column v-for="col in tableColumns" :key="col" :prop="col" :label="col" min-width="140" show-overflow-tooltip />
      </el-table>
      <el-pagination v-if="tableData.length > 20" v-model:current-page="page" :page-size="20" :total="tableData.length" layout="prev, pager, next" small />
    </div>
    <RuntimeObjectValue v-else :value="value" />
  </div>
</template>

<script setup>
import {computed, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import RuntimeContent from './RuntimeContent.vue'
import RuntimeObjectValue from './RuntimeObjectValue.vue'
import {copyRuntimeText, isRuntimeImageUrl, runtimeRows, unwrapRuntimeOutput} from '@/utils/workflowRuntime'
import {resolveWorkflowAssetUrl} from '@/utils/workflowAssetUrl'

const props = defineProps({
  output: { type: [Object, Array, String, Number, Boolean], default: null },
  showToolbar: { type: Boolean, default: true }
})
const page = ref(1)
watch(() => props.output, () => { page.value = 1 })
const value = computed(() => unwrapRuntimeOutput(props.output))
const singleValue = computed(() => {
  if (value.value && typeof value.value === 'object' && !Array.isArray(value.value)) {
    const keys = Object.keys(value.value)
    if (keys.length === 1 && typeof value.value[keys[0]] !== 'object') return value.value[keys[0]]
  }
  return value.value
})
const tableData = computed(() => runtimeRows(value.value) || [])
const displayImageUrl = computed(() => resolveWorkflowAssetUrl(singleValue.value, import.meta.env.VITE_APP_BASE_API))
const outputType = computed(() => {
  if (isRuntimeImageUrl(singleValue.value)) return 'image'
  if (singleValue.value == null || typeof singleValue.value !== 'object') return 'text'
  return tableData.value.length && tableData.value.every(row => row && typeof row === 'object' && !Array.isArray(row)) ? 'table' : 'json'
})
const tableColumns = computed(() => [...new Set(tableData.value.flatMap(row => Object.keys(row || {})))])
const pageRows = computed(() => tableData.value.slice((page.value - 1) * 20, page.value * 20))
const copyResult = async () => {
  try {
    await copyRuntimeText(typeof singleValue.value === 'object' ? JSON.stringify(value.value, null, 2) : singleValue.value ?? '')
    ElMessage.success('结果已复制')
  } catch (error) { ElMessage.error(error.message || '复制失败') }
}
</script>

<style scoped>
.runtime-result { color: var(--runtime-text, #1e293b); min-width: 0; }
.result-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; font-weight: 600; }
.result-image :deep(.el-image) { width: 100%; max-height: 480px; }
.result-table :deep(.el-pagination) { justify-content: flex-end; margin-top: 14px; }
</style>
