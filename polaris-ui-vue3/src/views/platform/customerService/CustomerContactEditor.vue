<template>
  <section aria-label="人工服务配置">
    <el-divider content-position="left">人工服务入口</el-divider>
    <el-form-item label="联系入口"><el-switch v-model="enabled" active-text="启用人工联系信息" /></el-form-item>
    <template v-if="enabled">
      <el-form-item label="入口名称"><el-input v-model="draft.name" maxlength="64" placeholder="例如：售后人工客服" /></el-form-item>
      <el-form-item label="客服网页"><el-input v-model="draft.webUrl" maxlength="500" placeholder="https://support.example.com，网页与电话至少填写一项" /></el-form-item>
      <el-form-item label="客服电话"><el-input v-model="draft.phone" maxlength="32" placeholder="支持数字、前导+、空格、括号和连字符" /></el-form-item>
      <el-form-item label="服务时间"><el-input v-model="draft.serviceHours" maxlength="120" placeholder="例如：工作日 09:00—18:00，仅作为联系说明" /></el-form-item>
      <el-form-item label="补充说明"><el-input v-model="draft.note" maxlength="500" type="textarea" :rows="2" placeholder="例如：联系时请准备订单编号，请勿填写密钥或个人账户凭据" /></el-form-item>
      <p class="muted">用户需主动打开网页或拨打电话。本功能提供联系入口，坐席实时接管及对话自动转交尚未接入。</p>
      <p v-if="error" class="muted" role="status">{{ error }}</p>
      <CustomerContactCard v-else :config="draft" preview />
    </template>
  </section>
</template>
<script setup>
import {computed, reactive, ref, watch} from 'vue'
import CustomerContactCard from './CustomerContactCard.vue'
import {customerContactConfiguration, customerContactSettings} from '@/utils/customerContactState'

const props = defineProps({ modelValue: Object })
const emit = defineEmits(['update:modelValue'])
const enabled = ref(!!props.modelValue), draft = reactive(customerContactConfiguration(props.modelValue || {}))
const error = computed(() => { try { customerContactSettings(draft); return '' } catch (invalid) { return invalid.message } })
watch([enabled, draft], () => emit('update:modelValue', enabled.value ? { ...draft } : null), { deep: true })
</script>
<style scoped>
.muted { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; }
</style>
