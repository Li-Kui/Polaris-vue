<template>
  <RuntimeWorkflowRunner
    :manifest="manifest"
    :share-code="shareCode"
    submit-text="开始处理"
    input-title="原图与处理参数"
    result-title="前后对比"
    :disabled="uploading || !!mappingError"
    :hidden-fields="[imageInputName].filter(Boolean)"
    @completed="emit('completed', $event)"
  >
    <template #before-input="{ input, setInputField, running }">
      <el-alert v-if="mappingError" :title="mappingError" type="error" :closable="false" show-icon />
      <el-upload
        v-if="imageInputName"
        :show-file-list="false"
        :http-request="options => upload(options, setInputField)"
        :disabled="running || uploading"
        accept="image/png,image/jpeg,image/gif,image/bmp"
      >
        <el-button :loading="uploading" :disabled="running">{{ inputImage(input) ? '更换原图' : '选择原图' }}</el-button>
      </el-upload>
      <p v-if="!mappingError && !inputImage(input)" class="compare-hint">先上传原图，再填写处理参数。完成后可拖动滑块比较效果。</p>
      <el-image v-if="inputImage(input)" class="source-preview" :src="assetUrl(inputImage(input))" fit="contain" alt="原图预览" />
    </template>
    <template #default="{ result, submittedInput }">
      <ImageCompare
        v-if="inputImage(submittedInput) && resultUrl(result, inputImage(submittedInput))"
        :source-url="inputImage(submittedInput)"
        :result-url="resultUrl(result, inputImage(submittedInput))"
        :source-label="manifest.pageConfig?.sourceLabel || '原图'"
        :result-label="manifest.pageConfig?.resultLabel || '效果图'"
      />
      <div v-if="inputImage(submittedInput) && resultUrl(result, inputImage(submittedInput))" class="compare-result-actions">
        <span>拖动滑块，或使用方向键查看前后差异</span>
        <a v-if="manifest.pageConfig?.showDownload !== false" :href="assetUrl(resultUrl(result, inputImage(submittedInput)))" target="_blank" rel="noopener noreferrer">打开效果原图</a>
      </div>
      <el-alert v-else-if="hasOutputMapping" title="指定输出字段没有返回有效效果图，请管理员检查图片输出映射" type="warning" :closable="false" show-icon />
      <RuntimeResult v-else :output="result" />
    </template>
  </RuntimeWorkflowRunner>
</template>

<script setup>
import {computed, ref} from 'vue'
import {ElMessage} from 'element-plus'
import RuntimeWorkflowRunner from '@/components/workflow-app/RuntimeWorkflowRunner.vue'
import RuntimeResult from '@/components/workflow-app/RuntimeResult.vue'
import ImageCompare from '@/components/workflow-app/ImageCompare.vue'
import {runtimeImageInputField, runtimeMappedImageUrls} from '@/utils/workflowRuntime'
import {uploadShareFile} from '@/api/workflowApp/runtime'
import {runtimeInputValue} from '@/utils/workflowInputMapping'
import {resolveWorkflowAssetUrl} from '@/utils/workflowAssetUrl'

const props = defineProps({
  manifest: { type: Object, required: true },
  shareCode: { type: String, required: true }
})
const emit = defineEmits(['completed'])

const uploading = ref(false)
const sourceUrl = ref('')
const hasOutputMapping = computed(() => Object.hasOwn(props.manifest.pageConfig?.outputMapping || {}, 'resultImage'))
const mappingError = computed(() => {
  try { return runtimeImageInputField(props.manifest.inputSchema, props.manifest.pageConfig) ? '' : '工作流未定义输入图片字段，请管理员检查已发布输入定义' }
  catch (error) { return error.message }
})
const imageInputName = computed(() => {
  try { return runtimeImageInputField(props.manifest.inputSchema, props.manifest.pageConfig) }
  catch { return undefined }
})
const inputImage = input => runtimeInputValue(props.manifest.inputSchema, input, imageInputName.value)
const assetUrl = value => resolveWorkflowAssetUrl(value, import.meta.env.VITE_APP_BASE_API)

const upload = async (options, setInputField) => {
  uploading.value = true
  try {
    const response = await uploadShareFile(props.shareCode, options.file)
    sourceUrl.value = (response.data || response).url
    setInputField(imageInputName.value, sourceUrl.value)
    options.onSuccess?.(response)
  } catch (error) {
    ElMessage.error(error.message || '图片上传失败')
    options.onError?.(error)
  } finally {
    uploading.value = false
  }
}

const resultUrl = (value, source) => {
  const urls = runtimeMappedImageUrls(value, props.manifest.pageConfig, 'resultImage')
  return hasOutputMapping.value ? urls[0] || '' : urls.find(url => url !== source) || ''
}
</script>

<style scoped>
.source-preview { width: 100%; height: 160px; margin: 12px 0; background: var(--runtime-muted, #f1f5f9); border-radius: 12px; }
.compare-hint, .compare-result-actions { color: var(--runtime-secondary, #64748b); font-size: 12px; line-height: 1.7; }
.compare-result-actions { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; margin-top: 12px; }
.compare-result-actions a { color: var(--workflow-primary, #4f46e5); text-decoration: underline; }
</style>
