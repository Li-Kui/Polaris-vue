<template>
  <el-dialog :model-value="modelValue" class="workflow-contract-dialog" title="编辑流程输入输出契约" width="min(720px, 94vw)" :close-on-click-modal="false" @update:model-value="$emit('update:modelValue', $event)">
    <el-alert title="应用仅修改当前草稿，仍需保存、校验并发布。修改输入输出可能影响节点映射及现有调用方。" type="info" :closable="false" />
    <p>客服参数可在输入属性中配置 title（名称）、description（说明）、examples（示例）及 enum、长度或数值约束。示例仅供说明，不会自动填入。</p>
    <el-form label-position="top">
      <el-form-item label="流程输入 Schema（JSON）"><el-input v-model="inputText" type="textarea" :rows="9" spellcheck="false" /></el-form-item>
      <el-form-item label="流程输出映射（JSON）"><el-input v-model="outputText" type="textarea" :rows="7" spellcheck="false" /></el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer><el-button @click="$emit('update:modelValue', false)">取消</el-button><el-button type="primary" @click="apply">应用到草稿</el-button></template>
  </el-dialog>
</template>

<script setup>
import {ref, watch} from 'vue'
import {parseWorkflowContract} from './workflowContract'

const props = defineProps({ modelValue: Boolean, inputs: Object, outputs: Object })
const emit = defineEmits(['update:modelValue', 'apply'])
const inputText = ref(''), outputText = ref(''), error = ref('')
watch(() => props.modelValue, opened => {
  if (!opened) return
  inputText.value = JSON.stringify(props.inputs || { type: 'object', properties: {} }, null, 2)
  outputText.value = JSON.stringify(props.outputs || {}, null, 2)
  error.value = ''
})
function apply() {
  try {
    const contract = parseWorkflowContract(inputText.value, outputText.value)
    emit('apply', contract)
    emit('update:modelValue', false)
  } catch {
    error.value = '契约格式无效：输入须为含 properties 的 object Schema，输出须为字段映射对象；请检查 JSON 语法。'
  }
}
</script>

<style lang="scss">
html .workflow-contract-dialog.el-dialog .el-button.el-button.el-button--primary {
  color: #fff !important;
  background: #4f46e5 !important;
  border-color: #4f46e5 !important;
}
</style>
