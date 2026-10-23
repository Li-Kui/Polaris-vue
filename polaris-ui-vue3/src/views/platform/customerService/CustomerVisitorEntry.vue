<template>
  <main class="customer-service-page customer-entry">
    <header class="cs-panel-heading"><div><h1>{{ entry?.serviceName || '数字人客服' }}</h1><p>{{ entry?.name || '在线咨询' }}</p></div><el-button @click="isDark = !isDark">{{ isDark ? '浅色模式' : '深色模式' }}</el-button></header>
    <p v-if="loading" role="status">正在准备接待入口…</p>
    <section v-else-if="blocked" class="cs-panel-section"><p>请使用网站提供的客服入口或正确的嵌入代码。</p></section>
    <section v-else-if="error" class="cs-panel-section"><p role="alert">{{ error }}</p><el-button @click="load">重新检查入口</el-button></section>
    <template v-else-if="entry">
      <CustomerConversation v-if="started" :key="generation" :visitor-client="client" :visitor-voice-enabled="entry.speechEnabled === true" :max-turns="entry.messageLimit" :service-name="entry.serviceName" :allow-tests="false" @closed="started = false" @unavailable="unavailable" />
      <section v-else class="cs-panel-section"><h2>{{ finished ? '本次咨询已结束' : '你好，很高兴为你服务' }}</h2><p>可咨询服务范围内的问题。每次接待最长30分钟、最多{{ entry.messageLimit }}轮，开始后可以查看本次对话的资料来源。</p><el-button type="primary" @click="start">{{ finished ? '重新开始咨询' : '开始咨询' }}</el-button></section>
    </template>
  </main>
</template>
<script setup>
import {onMounted, ref, shallowRef} from 'vue'
import {useDark} from '@vueuse/core'
import {useRoute} from 'vue-router'
import {createCustomerVisitorClient} from '@/utils/customerVisitorClient'
import {customerChannelFrameAllowed} from '@/utils/customerChannelState'
import CustomerConversation from './CustomerConversation.vue'
import './customerService.scss'

const isDark = useDark({ storageKey: 'polaris-theme-appearance', valueDark: 'dark', valueLight: 'light' })
const code = String(useRoute().params.code || '')
const entry = ref(null), error = ref(''), blocked = ref(false), loading = ref(false), started = ref(false), finished = ref(false), generation = ref(0), client = shallowRef(null)
function allowedFrame() {
  if (window.parent === window) return true
  try { return customerChannelFrameAllowed(code, window.location.origin, window.parent.location.origin, window.parent.location.pathname) }
  catch { return false }
}
async function load() {
  if (loading.value) return
  blocked.value = !allowedFrame(); if (blocked.value) return
  loading.value = true; error.value = ''; entry.value = null
  try { entry.value = (await createCustomerVisitorClient(code).entry()).data }
  catch { error.value = '此接待入口暂不可用，可能已停用、到期或访问过于频繁。' }
  finally { loading.value = false }
}
function start() { client.value = createCustomerVisitorClient(code); generation.value++; finished.value = true; started.value = true }
function unavailable() { started.value = false; error.value = '本次接待已不可用，请重新检查入口。' }
onMounted(load)
</script>
