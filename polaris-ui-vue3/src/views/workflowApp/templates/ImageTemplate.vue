<template>
  <RuntimeWorkflowRunner
    :manifest="manifest"
    :share-code="shareCode"
    submit-text="生成图片"
    input-title="图片生成参数"
    result-title="生成结果"
    :disabled="uploading || !!mappingError"
    :hidden-fields="[modeField, sizeField, countField, imageInputName].filter(Boolean)"
    @completed="emit('completed', $event)"
  >
    <template #before-input="{ input, setInputField, running }">
      <el-alert v-if="mappingError" :title="mappingError" type="error" :closable="false" show-icon />
      <div v-if="modeField || sizeField || countField" class="image-options">
        <label v-if="modeField">生成方式
          <el-select v-model="input[modeField]" :disabled="running" :teleported="false" placeholder="选择生成方式">
            <el-option v-for="mode in modes" :key="mode" :label="modeLabels[mode] || mode" :value="mode" />
          </el-select>
        </label>
        <label v-if="sizeField">图片尺寸
          <el-select v-model="input[sizeField]" :disabled="running" :teleported="false" placeholder="选择尺寸">
            <el-option v-for="size in sizes" :key="size" :label="size" :value="size" />
          </el-select>
        </label>
        <label v-if="countField">生成数量
          <el-input-number v-model="input[countField]" :min="1" :max="maxCount" :disabled="running" />
        </label>
      </div>
      <el-upload
        v-if="imageInputName"
        class="image-upload"
        :show-file-list="false"
        :http-request="options => upload(options, setInputField)"
        :disabled="running || uploading"
        accept="image/png,image/jpeg,image/gif,image/bmp"
      >
        <el-button :disabled="running" :loading="uploading">{{ inputImage(input) ? '更换输入图片' : '上传输入图片' }}</el-button>
        <span v-if="inputImage(input)" class="upload-success">已上传</span>
      </el-upload>
      <el-image v-if="inputImage(input)" class="source-preview" :src="assetUrl(inputImage(input))" fit="contain" alt="输入图片预览" />
    </template>
    <template #default="{ result }">
      <RuntimeImageGrid v-if="imageUrls(result).length" :urls="imageUrls(result)" :show-download="manifest.pageConfig?.showDownload !== false" />
      <el-alert v-else-if="hasOutputMapping" title="指定输出字段没有返回有效图片，请管理员检查图片输出映射" type="warning" :closable="false" show-icon />
      <RuntimeResult v-else :output="result" />
    </template>
  </RuntimeWorkflowRunner>
</template>

<script setup>
import {computed, ref} from 'vue'
import {ElMessage} from 'element-plus'
import RuntimeWorkflowRunner from '@/components/workflow-app/RuntimeWorkflowRunner.vue'
import RuntimeResult from '@/components/workflow-app/RuntimeResult.vue'
import RuntimeImageGrid from '@/components/workflow-app/RuntimeImageGrid.vue'
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
const fields = computed(() => props.manifest.inputSchema?.properties || {})
const resolveField = (type, candidates) => {
  const configured = props.manifest.pageConfig?.inputMapping?.[type]
  return configured ? (Object.hasOwn(fields.value, configured) ? configured : undefined) : candidates.find(name => fields.value[name])
}
const modeField = computed(() => resolveField('mode', ['mode', 'generationMode']))
const sizeField = computed(() => resolveField('size', ['size', 'imageSize']))
const countField = computed(() => resolveField('count', ['count', 'n', 'generateCount']))
const modeLabels = { text_to_image: '文生图', image_to_image: '图生图', background_replacement: '更换背景' }
const modes = computed(() => props.manifest.pageConfig?.enabledModes || fields.value[modeField.value]?.enum || ['text_to_image', 'image_to_image', 'background_replacement'])
const sizes = computed(() => props.manifest.pageConfig?.sizeOptions || fields.value[sizeField.value]?.enum || ['512x512', '1024x1024'])
const maxCount = computed(() => Math.max(1, Math.min(Number(props.manifest.pageConfig?.maxGenerateCount) || 4, Number(fields.value[countField.value]?.maximum) || 100)))
const mappingError = computed(() => {
  try { runtimeImageInputField(props.manifest.inputSchema, props.manifest.pageConfig) }
  catch (error) { return error.message }
  for (const key of ['mode', 'size', 'count']) {
    const configured = props.manifest.pageConfig?.inputMapping?.[key]
    if (configured && !Object.hasOwn(fields.value, configured)) return `输入字段映射 ${key} 无效，请管理员检查配置`
  }
  return ''
})
const imageInputName = computed(() => {
  try { return runtimeImageInputField(props.manifest.inputSchema, props.manifest.pageConfig) }
  catch { return undefined }
})
const imageUrls = value => runtimeMappedImageUrls(value, props.manifest.pageConfig)
const inputImage = input => runtimeInputValue(props.manifest.inputSchema, input, imageInputName.value)
const assetUrl = value => resolveWorkflowAssetUrl(value, import.meta.env.VITE_APP_BASE_API)
const hasOutputMapping = computed(() => Object.hasOwn(props.manifest.pageConfig?.outputMapping || {}, 'images'))

const upload = async (options, setInputField) => {
  uploading.value = true
  try {
    const response = await uploadShareFile(props.shareCode, options.file)
    setInputField(imageInputName.value, (response.data || response).url)
    ElMessage.success('图片上传成功')
    options.onSuccess?.(response)
  } catch (error) {
    ElMessage.error(error.message || '图片上传失败')
    options.onError?.(error)
  } finally {
    uploading.value = false
  }
}

</script>

<style scoped>
.image-upload {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.upload-success { color: var(--workflow-success, #15803d); font-size: 13px; }
.image-options { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin-bottom: 18px; }
.image-options label { display: flex; flex-direction: column; gap: 8px; font-size: 13px; color: var(--runtime-text, #1e293b); }
.image-options :deep(.el-select), .image-options :deep(.el-input-number) { width: 100%; }
.source-preview { width: 100%; height: 160px; margin-bottom: 16px; background: var(--runtime-muted, #f1f5f9); border-radius: 8px; }
</style>
