<template>
  <RuntimeWorkflowRunner
    :manifest="manifest"
    :share-code="shareCode"
    submit-text="生成作品"
    input-title="作品生成参数"
    result-title="作品画廊"
    @completed="emit('completed', $event)"
  >
    <template #default="{ result }">
      <RuntimeImageGrid v-if="imageUrls(result).length" :urls="imageUrls(result)" :show-download="manifest.pageConfig?.showDownload !== false" />
      <el-alert v-else-if="hasOutputMapping" title="指定输出字段没有返回有效图片，请管理员检查图片输出映射" type="warning" :closable="false" show-icon />
      <RuntimeResult v-else :output="result" />
    </template>
  </RuntimeWorkflowRunner>
</template>

<script setup>
import {computed} from 'vue'
import RuntimeWorkflowRunner from '@/components/workflow-app/RuntimeWorkflowRunner.vue'
import RuntimeResult from '@/components/workflow-app/RuntimeResult.vue'
import RuntimeImageGrid from '@/components/workflow-app/RuntimeImageGrid.vue'
import {runtimeMappedImageUrls} from '@/utils/workflowRuntime'

const props = defineProps({
  manifest: { type: Object, required: true },
  shareCode: { type: String, required: true }
})
const emit = defineEmits(['completed'])
const imageUrls = value => runtimeMappedImageUrls(value, props.manifest.pageConfig)
const hasOutputMapping = computed(() => Object.hasOwn(props.manifest.pageConfig?.outputMapping || {}, 'images'))

</script>
