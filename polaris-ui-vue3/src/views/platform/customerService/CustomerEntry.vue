<template>
  <main class="customer-service-page customer-entry">
    <header class="cs-panel-heading"><div><h1>{{ entry?.serviceName || '数字人客服' }}</h1><p>{{ entry?.name || '受控分享入口' }}</p></div><el-button @click="isDark = !isDark">{{ isDark ? '浅色模式' : '深色模式' }}</el-button></header>
    <p class="cs-page-note">受控接待 · 需本租户管理员登录 · 知识咨询与已选测试能力</p>
    <p v-if="loading">正在检查入口和登录权限…</p>
    <section class="cs-panel-section" v-else-if="blocked"><p>此页面不能直接嵌入，请使用渠道配置页生成的 iframe 代码。</p></section>
    <section class="cs-panel-section" v-else-if="loginRequired">
      <p>请先在新窗口登录北辰中台，再打开此入口。若浏览器拦截第三方 Cookie，请直接在新窗口使用接待页。</p>
      <div class="toolbar"><a :href="loginUrl" target="_blank" rel="noopener">在新窗口登录</a><a :href="entryUrl" target="_blank" rel="noopener">在新窗口打开接待页</a><el-button @click="load">重新检查登录</el-button></div>
    </section>
    <section class="cs-panel-section" v-else-if="error"><p>{{ error }}</p><el-button @click="load">重新检查入口</el-button></section>
    <template v-else-if="entry">
      <details class="cs-help"><summary>入口有效期与发布信息</summary><p>到期 {{ new Date(entry.expiresAt).toLocaleString() }}</p><p>固定版本 {{ entry.releaseId }}</p></details>
      <CustomerConversation v-if="started" :key="entry.code" :channel-code="entry.code" :service-name="entry.serviceName" :allow-tests="entry.capabilityCodes.length > 0" @closed="started = false" @unavailable="unavailable" />
      <div v-else><p>会话已结束。开始后会话有效期为 30 分钟，问答会使用租户模型配额。</p><el-button type="primary" @click="started = true">开始文字会话</el-button></div>
    </template>
  </main>
</template>
<script setup>
import {onMounted, ref} from 'vue'
import {useDark} from '@vueuse/core'
import {useRoute} from 'vue-router'
import {getPlatformToken} from '@/utils/auth'
import usePlatformUserStore from '@/store/modules/platformUser'
import {getCustomerChannelEntry} from '@/api/platform/customerService'
import {customerChannelFrameAllowed} from '@/utils/customerChannelState'
import './customerService.scss'
import CustomerConversation from './CustomerConversation.vue'

const isDark = useDark({ storageKey: 'polaris-theme-appearance', valueDark: 'dark', valueLight: 'light' })
const route = useRoute()
const entry = ref(null), loading = ref(false), loginRequired = ref(false), blocked = ref(false), error = ref(''), started = ref(false)
const code = String(route.params.code || '')
const entryUrl = `${window.location.origin}/platform/customer/${encodeURIComponent(code)}`
const loginUrl = `/platform/login?redirect=${encodeURIComponent(`/platform/customer/${code}`)}`
function allowedFrame() {
  if (window.parent === window) return true
  try { return customerChannelFrameAllowed(code, window.location.origin, window.parent.location.origin, window.parent.location.pathname) }
  catch { return false }
}
async function load() {
  if (loading.value) return
  started.value = false
  entry.value = null
  error.value = ''
  blocked.value = !allowedFrame()
  if (blocked.value) return
  loginRequired.value = !getPlatformToken()
  if (loginRequired.value) return
  loading.value = true
  try {
    const user = usePlatformUserStore()
    await user.getInfo()
    if (user.user.role !== 'admin') { error.value = '当前入口仅供本租户管理员使用。'; return }
    entry.value = (await getCustomerChannelEntry(code)).data
  } catch {
    loginRequired.value = !getPlatformToken()
    error.value = '入口不可用，可能已变更、停用、到期，或当前账号无权访问。'
  }
  finally { loading.value = false }
}
function unavailable() { started.value = false; entry.value = null; loginRequired.value = !getPlatformToken(); error.value = '渠道或登录权限已变化，当前会话已停止展示，请重新检查入口。' }
onMounted(load)
</script>
