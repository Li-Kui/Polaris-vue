<template>
  <section aria-label="业务能力配置">
    <el-divider content-position="left">业务能力</el-divider>
    <p class="capability-hint">当前配置固定工作流版本、输入来源和输出字段。无外部资源的能力可在预览中测试；查询本人订单需另接业务身份与订单接口。</p>
    <div v-for="(item, index) in modelValue" :key="item.code" class="capability-row">
      <div><strong>{{ item.name }}</strong> <code>{{ item.code }}</code><p>{{ item.description }}</p><small>固定版本：{{ item.versionId }}</small></div>
      <div><el-button link type="primary" :disabled="opened || loading || saving" @click="edit(index)">编辑能力</el-button><el-button link :disabled="opened || loading || saving" @click="remove(index)">移除</el-button></div>
    </div>
    <el-button v-if="!opened" :disabled="modelValue.length >= 5 || loading" :loading="loading" @click="edit(null)">添加能力（{{ modelValue.length }}/5）</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-if="opened" v-loading="loading" class="capability-form">
      <el-form-item label="能力编码"><el-input v-model="draft.code" maxlength="32" placeholder="如 order_query，小写字母、数字及下划线" /></el-form-item>
      <el-form-item label="能力名称"><el-input v-model="draft.name" maxlength="64" /></el-form-item>
      <el-form-item label="用途说明"><el-input v-model="draft.description" type="textarea" :rows="2" maxlength="300" /></el-form-item>
      <el-form-item label="已发布工作流">
        <el-select v-model="draft.definitionId" filterable class="full-width" placeholder="选择当前租户工作流" @change="changeWorkflow" popper-class="customer-service-menu">
          <el-option v-for="item in workflows" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="固定发布版本">
        <el-select v-model="draft.versionId" class="full-width" :disabled="!draft.definitionId" placeholder="选择明确版本，不自动跟随最新" @change="changeVersion" popper-class="customer-service-menu">
          <el-option v-for="item in versions" :key="item.versionId" :label="`V${item.versionNo} · ${item.versionId}`" :value="item.versionId" />
        </el-select>
      </el-form-item>
      <el-alert v-if="!contract" title="选择受支持的发布版本后，才能配置输入与输出。" type="info" :closable="false" />
      <template v-else>
        <div v-for="input in contract.inputs" :key="input.name" class="capability-input">
          <label :for="`customer-capability-${input.name}`">{{ input.label || input.name }}（{{ input.name }} · {{ input.type }}{{ input.required ? ' · 必填' : '' }}）</label>
          <p v-for="hint in customerParameterHints(input)" :key="hint" class="capability-hint">{{ hint }}</p>
          <el-select :id="`customer-capability-${input.name}`" v-model="draft.inputSources[input.name]" :aria-label="`${input.name} 输入来源`" class="full-width" popper-class="customer-service-menu">
            <el-option label="用户填写" value="USER_INPUT" />
            <el-option v-if="input.type === 'integer'" label="服务端当前平台用户 ID" value="PLATFORM_USER_ID" />
            <el-option v-if="input.type === 'integer'" label="服务端当前租户 ID" value="TENANT_ID" />
          </el-select>
        </div>
        <el-form-item label="允许展示的输出字段">
          <el-select v-model="draft.outputFields" multiple class="full-width" placeholder="至少选择一项，最多 10 项" :multiple-limit="10" popper-class="customer-service-menu">
            <el-option v-for="field in contract.outputFields" :key="field" :label="field" :value="field" />
          </el-select>
        </el-form-item>
        <p class="capability-hint">平台用户 ID 不等于外部商城会员 ID。输出字段在执行接入时仍需按业务归属及结构校验。</p>
      </template>
      <div class="capability-actions"><el-button :disabled="saving" @click="cancel">取消能力编辑</el-button><el-button type="primary" :disabled="loading || !contract" :loading="saving" @click="save">检查并加入草稿</el-button></div>
    </div>
  </section>
</template>

<script setup>
import {onBeforeUnmount, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {
  checkCustomerCapability,
  getCustomerCapabilityContract,
  listCustomerCapabilityVersions,
  listCustomerCapabilityWorkflows
} from '@/api/platform/customerService'
import {
  customerCapabilityContract,
  customerCapabilityDraft,
  customerCapabilitySave
} from '@/utils/customerCapabilityState'
import {customerParameterHints} from '@/utils/customerParameterState'

const props = defineProps({ modelValue: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue', 'editing'])
const opened = ref(false), loading = ref(false), saving = ref(false), editingIndex = ref(null)
const draft = ref(customerCapabilityDraft()), contract = ref(null), workflows = ref([]), versions = ref([])
const error = ref('')
let requestSerial = 0

async function edit(index) {
  const serial = ++requestSerial
  loading.value = true
  error.value = ''
  contract.value = null
  versions.value = []
  try {
    const choices = (await listCustomerCapabilityWorkflows()).data || []
    if (serial !== requestSerial) return
    workflows.value = choices
    editingIndex.value = index
    draft.value = customerCapabilityDraft(index === null ? null : props.modelValue[index])
    opened.value = true
    emit('editing', true)
    if (draft.value.definitionId) {
      const availableVersions = (await listCustomerCapabilityVersions(draft.value.definitionId)).data || []
      if (serial !== requestSerial) return
      versions.value = availableVersions
      const result = (await getCustomerCapabilityContract(draft.value.definitionId, draft.value.versionId)).data
      if (serial !== requestSerial) return
      applyContract(result)
    }
  } catch {
    if (serial === requestSerial) error.value = '能力目录或固定版本契约加载失败，请检查接口提示后重试；原配置仍保留。'
  } finally { if (serial === requestSerial) loading.value = false }
}

async function changeWorkflow() {
  const serial = ++requestSerial
  draft.value.versionId = ''
  draft.value.inputSources = {}
  draft.value.outputFields = []
  contract.value = null
  versions.value = []
  loading.value = true
  error.value = ''
  try {
    const result = (await listCustomerCapabilityVersions(draft.value.definitionId)).data || []
    if (serial === requestSerial) versions.value = result
  } catch {
    if (serial === requestSerial) error.value = '发布版本加载失败，请重新选择工作流。'
  } finally { if (serial === requestSerial) loading.value = false }
}

async function changeVersion() {
  const serial = ++requestSerial
  contract.value = null
  draft.value.inputSources = {}
  draft.value.outputFields = []
  loading.value = true
  error.value = ''
  try {
    const result = (await getCustomerCapabilityContract(draft.value.definitionId, draft.value.versionId)).data
    if (serial === requestSerial) applyContract(result)
  } catch {
    if (serial === requestSerial) error.value = '当前发布版本不符合能力契约或已不可用，请检查接口提示后重新选择。'
  } finally { if (serial === requestSerial) loading.value = false }
}

function applyContract(value) {
  const updated = customerCapabilityContract(draft.value, value)
  if (!updated) return
  draft.value = updated
  contract.value = value
}

async function save() {
  let updated
  try { updated = customerCapabilitySave(props.modelValue, editingIndex.value, draft.value) }
  catch (error) { ElMessage.warning(error.message); return }
  saving.value = true
  error.value = ''
  const serial = requestSerial
  try {
    await checkCustomerCapability(customerCapabilityDraft(draft.value))
    if (serial !== requestSerial) return
    emit('update:modelValue', updated)
    cancel()
    ElMessage.success('能力已加入本地草稿，保存客服草稿后再发布生效')
  } catch {
    if (serial === requestSerial) error.value = '能力检查未通过，请修正输入映射、输出字段或版本；尚未加入草稿。'
  } finally { saving.value = false }
}

function remove(index) { emit('update:modelValue', props.modelValue.filter((_, position) => position !== index)) }
function cancel() { ++requestSerial; opened.value = false; loading.value = false; error.value = ''; emit('editing', false) }
onBeforeUnmount(() => { ++requestSerial })
</script>

<style scoped>
.capability-hint { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; }
.capability-row { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; border-bottom: 1px solid var(--el-border-color-lighter); padding: 12px 0; }
.capability-row p { margin: 6px 0; overflow-wrap: anywhere; }
.capability-row small { overflow-wrap: anywhere; }
.capability-form { margin-top: 16px; }
.capability-input { margin-bottom: 16px; }
.capability-input label { display: block; margin-bottom: 8px; font-size: 13px; }
.capability-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.full-width { width: 100%; }
</style>
