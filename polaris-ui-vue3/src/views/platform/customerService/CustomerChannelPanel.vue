<template>
  <el-drawer :model-value="true" class="customer-service-preview customer-channel-drawer" :class="{ 'has-detail': !!editing || !!selected }" title="分享渠道" size="min(1080px, 100vw)" @close="emit('closed')">
    <header class="cs-panel-heading"><div><h2>{{ app.config.name }}</h2><p>管理独立接待链接与网站嵌入渠道</p></div><span class="cs-status is-accent">按渠道控制访问</span></header>
    <details class="cs-help"><summary>访问范围与渠道生效规则</summary><p>管理员渠道要求本租户登录；访客渠道允许免登录知识咨询，启用后将公开固定发布版本的知识、人物及人工联系信息。访客会话独立，不授予平台用户或订单权限。新渠道固定创建时的客服发布版本，修改后旧渠道会话失效，已有测试任务不会因此取消。</p></details>
    <section class="cs-panel-section cs-channel-list">
    <div class="toolbar"><el-button type="primary" :disabled="busy" @click="edit(null)">新建渠道</el-button><el-button :loading="loading" @click="load">刷新渠道</el-button></div>
    <div class="cs-mobile-rows cs-channel-cards"><article v-for="row in channels" :key="row.id"><h3>{{ row.name }}</h3><p><span class="cs-status" :class="{ 'is-live': row.status === 'ACTIVE' && !expired(row) }">{{ expired(row) ? '已到期' : row.status === 'ACTIVE' ? '启用' : '停用' }}</span><span class="muted">{{ row.accessMode === 'VISITOR' ? '访客咨询 · ' : '管理员 · ' }}{{ row.capabilityCodes.length ? `${row.capabilityCodes.length} 项测试` : '知识咨询' }}</span></p><div class="toolbar"><el-button @click="edit(row)">配置</el-button><el-button type="primary" @click="showLinks(row)">链接与嵌入</el-button></div></article></div>
    <el-table class="cs-channel-table" :data="channels" v-loading="loading" row-key="id">
      <el-table-column prop="name" label="渠道" min-width="140" />
      <el-table-column label="访问方式" width="120"><template #default="{ row }">{{ row.accessMode === 'VISITOR' ? '访客咨询' : '管理员登录' }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }">{{ expired(row) ? '已到期' : row.status === 'ACTIVE' ? '启用' : '停用' }}</template></el-table-column>
      <el-table-column label="能力" width="100"><template #default="{ row }">{{ row.capabilityCodes.length ? `${row.capabilityCodes.length} 项测试` : '知识咨询' }}</template></el-table-column>
      <el-table-column label="操作" min-width="160"><template #default="{ row }"><el-button link type="primary" @click="edit(row)">配置</el-button><el-button link type="primary" @click="showLinks(row)">链接与嵌入</el-button></template></el-table-column>
    </el-table>
    </section><div class="cs-channel-layout">
    <el-form v-if="editing" label-position="top" class="editor cs-panel-section" @submit.prevent="save">
      <h3>{{ editing.id ? '修改渠道' : '新建渠道（默认停用）' }}</h3>
      <p v-if="editing.releaseId" class="muted">固定客服发布版本：{{ editing.releaseId }}</p>
      <el-form-item label="访问方式"><el-select v-model="form.accessMode" popper-class="customer-service-menu" @change="form.accessMode === 'VISITOR' && (form.capabilityCodes = [])"><el-option label="租户管理员登录" value="TENANT_ADMIN" /><el-option label="访客免登录知识咨询" value="VISITOR" /></el-select></el-form-item>
      <template v-if="form.accessMode === 'VISITOR'"><el-form-item label="每日最多新建会话（UTC自然日）"><el-input-number v-model="form.visitorDailySessions" :min="1" :max="10000" /></el-form-item><el-form-item label="每次接待最多问答轮数"><el-input-number v-model="form.visitorMessageLimit" :min="1" :max="50" /></el-form-item>
        <el-form-item label="开放访客语音"><el-switch v-model="form.visitorSpeechEnabled" active-text="允许主动录音与回答播报" inactive-text="仅文字咨询" /></el-form-item>
        <el-form-item v-if="form.visitorSpeechEnabled" label="每次接待最多语音请求"><el-input-number v-model="form.visitorSpeechLimit" :min="1" :max="40" /></el-form-item>
        <p class="muted">语音默认关闭，开启后沿用固定发布的识别与播报模型；录音及合成会使用供应商额度。请求上限合计识别、播报分段和取消记录，限额同时受租户模型配额约束。嵌入语音需HTTPS（或本机）及每层iframe允许麦克风。确认知识可对外展示后再启用渠道。</p></template>
      <el-form-item label="渠道名称"><el-input v-model="form.name" maxlength="64" /></el-form-item>
      <el-form-item label="到期时间（最长 90 天）"><el-date-picker popper-class="customer-service-menu" v-model="form.expiresAt" type="datetime" placeholder="选择渠道到期时间" /></el-form-item>
      <el-form-item label="允许嵌入的来源（每行一个，最多 10 个）"><el-input v-model="form.origins" type="textarea" :rows="3" placeholder="https://example.com&#10;http://localhost:4173" /></el-form-item>
      <p class="muted">精确填写协议、域名及端口，不含路径或通配符；留空仅允许本站嵌入。前后端需同源代理，浏览器需支持 CSP frame-ancestors。</p>
      <el-form-item v-if="form.accessMode !== 'VISITOR'" label="允许的测试能力">
        <el-checkbox-group v-model="form.capabilityCodes"><el-checkbox v-for="item in editing.availableCapabilities || []" :key="item.code" :value="item.code">{{ item.name }}</el-checkbox></el-checkbox-group>
        <span v-if="!editing.id" class="muted">新建仅开放知识咨询；保存后可从固定版本中选择测试能力。</span>
        <span v-else-if="!editing.availableCapabilities.length" class="muted">此发布版本未配置测试能力。</span>
      </el-form-item>
      <el-form-item label="渠道状态"><el-switch v-model="form.active" active-text="启用" inactive-text="停用" :disabled="!editing.id" /></el-form-item>
      <div class="toolbar"><el-button type="primary" :loading="busy" @click="save">保存渠道</el-button><el-button :disabled="busy" @click="editing = null">取消编辑</el-button></div>
    </el-form>
    <section v-if="selected" class="links cs-panel-section" aria-label="链接与嵌入代码">
      <div class="cs-section-heading"><h3>{{ selected.name }} · 链接与嵌入</h3><el-button link @click="selected = null">返回渠道列表</el-button></div>
      <p>访问范围：{{ selected.accessMode === 'VISITOR' ? '访客免登录知识咨询' : '租户管理员登录' }}。到期时间：{{ new Date(selected.expiresAt).toLocaleString() }}。</p>
      <p v-if="selected.status !== 'ACTIVE' || expired(selected)" class="muted">渠道当前不可用，请先配置并启用。</p>
      <el-form label-position="top">
        <el-form-item label="独立接待链接"><el-input :model-value="links.entry" readonly /></el-form-item>
        <el-form-item v-if="links.widget" label="网站悬浮客服代码"><el-input :model-value="links.widget" type="textarea" :rows="3" readonly /></el-form-item>
        <el-form-item label="iframe 嵌入代码"><el-input :model-value="links.html" type="textarea" :rows="4" readonly /></el-form-item>
      </el-form>
      <div class="toolbar"><el-button @click="copy(links.entry)">复制链接</el-button><el-button @click="copy(links.html)">复制嵌入代码</el-button><el-button v-if="links.widget" @click="copy(links.widget)">复制悬浮代码</el-button><a :href="links.entry" target="_blank" rel="noopener">打开接待页</a></div>
      <p class="muted">嵌入不向宿主传递登录令牌、问题、答案或来源。访客不依赖第三方 Cookie，刷新页面需重新开始；管理员渠道需登录。本批不建立宿主业务身份桥接。</p>
    </section></div>
  </el-drawer>
</template>
<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {createCustomerChannel, listCustomerChannels, updateCustomerChannel} from '@/api/platform/customerService'
import {customerChannelLinks, customerChannelSettings} from '@/utils/customerChannelState'

const props = defineProps({ app: { type: Object, required: true } })
const emit = defineEmits(['closed'])
const channels = ref([]), loading = ref(false), busy = ref(false), editing = ref(null), selected = ref(null), links = ref({})
const form = reactive({ name: '', expiresAt: null, origins: '', capabilityCodes: [], active: false, accessMode: 'TENANT_ADMIN', visitorDailySessions: 200, visitorMessageLimit: 20, visitorSpeechEnabled: false, visitorSpeechLimit: 20 })
const expired = row => new Date(row.expiresAt).getTime() <= Date.now()
async function load() {
  loading.value = true
  try { channels.value = (await listCustomerChannels(props.app.id)).data || [] } finally { loading.value = false }
}
function edit(row) {
  selected.value = null
  editing.value = row ? { ...row } : {}
  Object.assign(form, { name: row?.name || '', expiresAt: new Date(row?.expiresAt || Date.now() + 7 * 86400000), origins: (row?.allowedOrigins || []).join('\n'), capabilityCodes: [...(row?.capabilityCodes || [])], active: row?.status === 'ACTIVE', accessMode: row?.accessMode || 'TENANT_ADMIN', visitorDailySessions: row?.visitorDailySessions || 200, visitorMessageLimit: row?.visitorMessageLimit || 20, visitorSpeechEnabled: row?.visitorSpeechEnabled === true, visitorSpeechLimit: row?.visitorSpeechLimit || 20 })
}
async function save() {
  if (busy.value) return
  let settings
  try { settings = customerChannelSettings(form) } catch (error) { ElMessage.warning(error.message); return }
  busy.value = true
  try {
    const response = editing.value.id ? await updateCustomerChannel(editing.value.id, editing.value.revision, form.active ? 'ACTIVE' : 'DISABLED', settings)
      : await createCustomerChannel(props.app.id, settings)
    edit(response.data)
    selected.value = null
    await load()
    ElMessage.success('渠道已保存；旧渠道会话需重新打开')
  } finally { busy.value = false }
}
function showLinks(row) {
  editing.value = null
  try { links.value = customerChannelLinks(window.location.origin, import.meta.env.VITE_APP_BASE_API, row.code, row.accessMode); selected.value = row }
  catch (error) { ElMessage.warning(error.message) }
}
async function copy(text) {
  try { await navigator.clipboard.writeText(text); ElMessage.success('已复制') }
  catch { ElMessage.warning('无法自动复制，请选中上方文本手动复制') }
}
onMounted(load)
</script>
