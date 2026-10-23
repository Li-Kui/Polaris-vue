<template>
  <section v-if="contact" class="contact-card" aria-label="人工服务联系信息">
    <h3>{{ contact.name }}</h3>
    <p v-if="!preview">请通过以下方式自行联系人工客服，当前文字会话继续保留。</p>
    <p v-if="contact.serviceHours">服务时间：{{ contact.serviceHours }}</p>
    <p v-if="contact.note" class="note">{{ contact.note }}</p>
    <p v-if="contact.webUrl"><span v-if="preview">客服网页：{{ contact.webUrl }}</span><a v-else :href="contact.webUrl" target="_blank" rel="noopener noreferrer">打开客服网页（新窗口）</a></p>
    <p v-if="contact.phone">电话：{{ contact.phone }} <a v-if="!preview" :href="customerContactPhoneLink(contact.phone)">拨打电话</a></p>
    <p class="muted">{{ preview ? '此处仅预览联系信息，保存并发布后对新会话生效。' : '此入口提供联系信息，尚未接通人工坐席，也未自动发送本次对话内容。' }}</p>
  </section>
  <p v-else class="muted">人工联系信息暂不可用，可继续使用文字咨询。</p>
</template>
<script setup>
import {computed} from 'vue'
import {customerContactPhoneLink, customerContactSettings} from '@/utils/customerContactState'

const props = defineProps({ config: Object, preview: Boolean })
const contact = computed(() => { try { return customerContactSettings(props.config) } catch { return null } })
</script>
<style scoped>
.contact-card { border: 1px solid var(--el-border-color); border-radius: 6px; padding: 16px; margin: 16px 0; }
p { overflow-wrap: anywhere; line-height: 1.7; }
.note { white-space: pre-wrap; }
a { color: var(--customer-link-color, #4f46e5); text-decoration: underline; }
.muted { color: var(--el-text-color-secondary); font-size: 13px; }
</style>
