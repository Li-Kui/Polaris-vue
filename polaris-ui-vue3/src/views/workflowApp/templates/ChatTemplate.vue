<template>
  <div class="chat-template">
    <div class="chat-toolbar">
      <div class="chat-toolbar-main"><strong>与 AI 助手对话</strong><span>{{ cancelling ? '正在停止生成' : isActive ? '正在思考您的问题' : '在本次对话中保持上下文' }}</span></div>
      <el-button :icon="Plus" :disabled="isActive || pendingRecovery || status === 'attention' || uploading || (!messages.length && !attachments.length)" @click="newConversation">新对话</el-button>
    </div>
    <div ref="messagesRef" class="chat-messages" role="log" aria-label="对话记录" aria-live="polite">
      <div v-if="!messages.length" class="chat-welcome">
        <el-icon class="welcome-icon"><Monitor /></el-icon>
        <h2>{{ manifest.pageConfig?.title || manifest.shareName || '开始对话' }}</h2>
        <p>{{ manifest.pageConfig?.description || manifest.pageConfig?.welcomeMessage || '你好！请问有什么我可以帮你的？' }}</p>
        <div v-if="suggestedQuestions.length" class="suggested-questions">
          <el-button v-for="question in suggestedQuestions" :key="question" round @click="useSuggestedQuestion(question)">{{ question }}</el-button>
        </div>
      </div>
      <div v-for="(msg, index) in messages" :key="msg.id" :class="['message', msg.role === 'user' ? 'user-msg' : 'system-msg']">
        <div class="avatar"><el-icon><User v-if="msg.role === 'user'" /><Monitor v-else /></el-icon></div>
        <div class="message-body">
          <div class="message-label">{{ msg.role === 'user' ? '你' : 'AI 助手' }}</div>
          <div v-if="msg.attachments?.length" class="message-attachments">
            <button v-for="file in msg.attachments" :key="file.token" type="button" class="attachment-card" :aria-label="`查看附件 ${file.name}`" @click="previewAttachment(file)">
              <img v-if="file.previewUrl" :src="file.previewUrl" :alt="file.name" />
              <el-icon v-else><Document /></el-icon>
              <span class="attachment-info"><strong>{{ file.name }}</strong><small>{{ chatAttachmentSize(file.size) }}{{ file.truncated ? ' · 已截取前 28000 字符' : '' }}</small></span>
            </button>
          </div>
          <div class="bubble" :class="{ 'is-error': msg.state === 'error' }">
            <RuntimeContent v-if="msg.content" :content="msg.content" />
            <span v-else class="typing"><el-icon class="is-loading"><Loading /></el-icon> {{ cancelling ? '正在停止…' : '正在思考…' }}</span>
            <RuntimeImageGrid v-if="msg.images?.length" class="chat-result-images" :urls="msg.images" :show-download="manifest.pageConfig?.showDownload !== false" />
          </div>
          <div v-if="msg.role === 'bot' && msg.state !== 'pending'" class="message-actions">
            <el-button size="small" :icon="CopyDocument" :disabled="!msg.content" @click="copyMessage(msg)">复制</el-button>
            <el-button v-if="msg.state === 'error' && !msg.recovered && index === messages.length - 1" size="small" :disabled="isActive || uploading || attachments.length > 0" @click="retryMessage(msg)">重新发送</el-button>
          </div>
        </div>
      </div>
    </div>
    <div v-if="pendingRecovery" class="chat-recover" role="status">
      <span>本页有上次执行记录。仅恢复状态与结果，不恢复历史对话或附件，也不会重新调用模型。</span>
      <el-button size="small" @click="recoverPrevious">恢复上次执行</el-button>
    </div>
    <div v-if="status === 'disconnected' && !cancelling" class="chat-recover">
      <span>{{ errorMessage }}</span><el-button size="small" @click="resume(execution.executionId)">恢复状态</el-button>
    </div>
    <div v-if="status === 'attention'" class="chat-recover">
      <span>管理员确认后，可读取原执行结果，不会重新生成。</span><el-button size="small" @click="resume(execution.executionId)">读取确认后状态</el-button>
    </div>
    <div v-if="status === 'unavailable'" class="chat-recover" role="status">
      <span>无法确认原任务状态。清除记录不会取消原任务，也不会重新生成；请勿直接重复生成。</span>
      <el-button size="small" @click="newConversation">清除本页记录</el-button>
    </div>
    <div class="chat-input">
      <div v-if="attachments.length" class="pending-attachments" aria-label="待发送附件">
        <div v-for="file in attachments" :key="file.id" class="attachment-card" :class="{ 'is-error': file.error }">
          <button type="button" class="attachment-preview" :disabled="!file.token" :aria-label="`预览附件 ${file.name}`" @click="previewAttachment(file)">
            <img v-if="file.previewUrl" :src="file.previewUrl" :alt="file.name" />
            <el-icon v-else><Document /></el-icon>
            <span class="attachment-info"><strong>{{ file.name }}</strong><small>{{ file.error || (file.uploading ? '上传及解析中…' : chatAttachmentSize(file.size) + (file.truncated ? ' · 已截取前 28000 字符' : '')) }}</small></span>
          </button>
          <el-button :icon="Close" circle size="small" :disabled="file.uploading || file.removing || isActive" :loading="file.removing" :aria-label="`移除附件 ${file.name}`" @click="removeAttachment(file)" />
        </div>
      </div>
      <div class="chat-composer">
        <el-input v-model="inputText" type="textarea" :autosize="{ minRows: 2, maxRows: 6 }" :disabled="inputLocked"
          aria-label="输入问题" placeholder="输入你的问题…" @keydown.enter="handleEnter" />
        <el-upload ref="uploadRef" :auto-upload="false" :show-file-list="false" :accept="chatAttachmentAccept" multiple :disabled="inputLocked || uploading || attachments.length >= chatAttachmentLimit" :on-change="addAttachment">
          <el-button :icon="Paperclip" :disabled="inputLocked || uploading || attachments.length >= chatAttachmentLimit" :loading="uploading" class="attachment-button" aria-label="添加附件" title="添加图片或文档" />
        </el-upload>
        <el-button v-if="isActive" :icon="VideoPause" :disabled="!execution?.executionId" :loading="cancelling" class="send-btn" @click="cancel">停止</el-button>
        <el-button v-else type="primary" :icon="Promotion" :disabled="!canSend" class="send-btn" @click="sendMessage()">发送</el-button>
      </div>
    </div>
    <div class="chat-input-hint"><span>每条消息最多 5 个附件 · 单文件 10MB · 图片需模型支持视觉</span><span>Enter 发送 · Shift+Enter 换行</span></div>
    <el-dialog v-model="previewVisible" :title="previewFile?.name || '附件预览'" width="min(720px, 92vw)" class="chat-attachment-dialog">
      <img v-if="previewFile?.previewUrl" :src="previewFile.previewUrl" :alt="previewFile.name" class="attachment-full-image" />
      <p v-else>文档将由后端解析后交给 AI。可下载核对原文件；扫描版 PDF 暂不支持文字提取。</p>
      <template #footer><el-button type="primary" :icon="Download" :loading="downloading" @click="downloadAttachment(previewFile)">下载原文件</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import {computed, nextTick, onUnmounted, reactive, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {
  Close,
  CopyDocument,
  Document,
  Download,
  Loading,
  Monitor,
  Paperclip,
  Plus,
  Promotion,
  User,
  VideoPause
} from '@element-plus/icons-vue'
import RuntimeContent from '@/components/workflow-app/RuntimeContent.vue'
import RuntimeImageGrid from '@/components/workflow-app/RuntimeImageGrid.vue'
import {useShareExecution} from '@/components/workflow-app/useShareExecution'
import {copyRuntimeText, extractRuntimeText, runtimeMappedImageUrls} from '@/utils/workflowRuntime'
import {getShareChatAttachment, removeShareChatAttachment, uploadShareChatAttachment} from '@/api/workflowApp/runtime'
import {
  chatAttachmentAccept,
  chatAttachmentError,
  chatAttachmentLimit,
  chatAttachmentSize,
  chatExecutionInput
} from '@/utils/workflowChatAttachments'

const props = defineProps({ manifest: { type: Object, required: true }, shareCode: { type: String, required: true } })
const emit = defineEmits(['completed'])
const messages = ref([])
const inputText = ref('')
const attachments = ref([])
const uploadRef = ref(null)
const previewFile = ref(null)
const previewVisible = ref(false)
const downloading = ref(false)
const previewUrls = new Set()
let uploadQueue = Promise.resolve()
let attachmentId = 0
const uploading = computed(() => attachments.value.some(file => file.uploading || file.removing))
const canSend = computed(() => !pendingRecovery.value && !['attention', 'unavailable'].includes(status.value) && !uploading.value && !attachments.value.some(file => file.error)
  && (!!inputText.value.trim() || attachments.value.some(file => file.token)))
const messagesRef = ref(null)
let activeMessage = null
let messageId = 0
const suggestedQuestions = computed(() => Array.isArray(props.manifest.pageConfig?.suggestedQuestions) ? props.manifest.pageConfig.suggestedQuestions : [])

const settleMessage = payload => {
  if (!activeMessage) return
  const succeeded = payload.execution.status === 'SUCCEEDED'
  const attention = payload.execution.status === 'NEEDS_ATTENTION'
  activeMessage.state = succeeded ? 'done' : attention ? 'attention' : payload.execution.status === 'CANCELLED' ? 'cancelled' : 'error'
  activeMessage.images = succeeded ? runtimeMappedImageUrls(payload.result, props.manifest.pageConfig) : []
  activeMessage.content = succeeded ? extractRuntimeText(payload.result) || (activeMessage.images.length ? '图片已生成。' : '本次未返回文字内容，请换个问题或联系应用管理员。')
    : attention ? '执行结果需要管理员确认，服务商可能已完成处理。请勿重复发送，以免重复生成或计费。'
      : payload.execution.status === 'CANCELLED' ? '本次生成已停止。' : payload.execution.errorMessage || '处理失败，请重试'
  scrollToBottom()
}
const { status, execution, errorMessage, cancelling, isActive, pendingRecovery, forgetRecovery, clearUnavailable, run, resume, cancel } = useShareExecution(() => props.shareCode, {
  onMessage: event => {
    if (event.eventType === 'NODE_OUTPUT' && activeMessage) {
      const text = extractRuntimeText(event.payloadJson)
      if (text) activeMessage.content = text
      scrollToBottom()
    }
  },
  onSettled: settleMessage,
  onCompleted: payload => emit('completed', payload),
  onUnavailable: error => {
    if (activeMessage) { activeMessage.state = 'unavailable'; activeMessage.images = []; activeMessage.content = error.message || '该执行已不可访问，请联系管理员' }
  },
  onError: error => {
    if (activeMessage) { activeMessage.state = 'error'; activeMessage.content = error.message || '发送失败，请重试' }
  }
})
watch(status, value => {
  if (value === 'disconnected' && !cancelling.value && activeMessage && !activeMessage.content) activeMessage.content = '连接中断，任务可能仍在运行。'
})
const inputLocked = computed(() => isActive.value || !!pendingRecovery.value || ['attention', 'unavailable'].includes(status.value))

const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
}
const recoverPrevious = async () => {
  const id = pendingRecovery.value
  if (!id || isActive.value) return
  messages.value.push({ id: ++messageId, role: 'bot', content: '', state: 'pending', recovered: true })
  activeMessage = messages.value[messages.value.length - 1]
  scrollToBottom()
  await resume(id)
}
const sendMessage = async () => {
  if (!canSend.value || isActive.value) return
  const text = inputText.value.trim() || '请阅读附件并总结主要内容。'
  const files = [...attachments.value]
  messages.value.push({ id: ++messageId, role: 'user', content: text, attachments: files, state: 'done' })
  const request = chatExecutionInput(messages.value.filter(message => !message.recovered))
  messages.value.push({ id: ++messageId, role: 'bot', content: '', prompt: text, attachmentsForRetry: files, state: 'pending' })
  activeMessage = messages.value[messages.value.length - 1]
  inputText.value = ''
  attachments.value = []
  scrollToBottom()
  await run(request.input, { attachments: request.attachments })
}
const handleEnter = event => {
  if (event.shiftKey || event.isComposing) return
  event.preventDefault()
  sendMessage()
}
const copyMessage = async message => {
  try { await copyRuntimeText(message.content); ElMessage.success('回复已复制') }
  catch (error) { ElMessage.error(error.message || '复制失败') }
}
const retryMessage = message => {
  if (isActive.value || uploading.value || attachments.value.length) return
  messages.value.splice(-2, 2)
  inputText.value = message.prompt
  attachments.value = [...(message.attachmentsForRetry || [])]
  sendMessage()
}
const useSuggestedQuestion = question => { inputText.value = question; sendMessage() }

const addAttachment = file => {
  if (inputLocked.value) return
  const error = chatAttachmentError(file.raw, attachments.value.length)
  uploadRef.value?.clearFiles()
  if (error) { ElMessage.warning(error); return }
  const item = reactive({ id: ++attachmentId, name: file.raw.name, size: file.raw.size, uploading: true, error: '', token: '' })
  if (/\.(png|jpe?g|gif|bmp)$/i.test(item.name)) {
    item.previewUrl = URL.createObjectURL(file.raw)
    previewUrls.add(item.previewUrl)
  }
  attachments.value.push(item)
  // 顺序上传，避免初次匿名会话尚未收到 Cookie 时并发产生不同访客标识。
  uploadQueue = uploadQueue.then(async () => {
    try {
      const response = await uploadShareChatAttachment(props.shareCode, file.raw)
      const result = response.data || response
      Object.assign(item, result, { name: result.name || item.name })
    } catch (error) {
      item.error = error.message || '上传失败，请移除后重试'
      ElMessage.error(item.error)
    } finally { item.uploading = false }
  })
}
const revokePreview = file => {
  if (file.previewUrl) { URL.revokeObjectURL(file.previewUrl); previewUrls.delete(file.previewUrl) }
}
const removeAttachment = async file => {
  if (file.uploading || file.removing || isActive.value) return
  file.removing = true
  try {
    if (file.token) await removeShareChatAttachment(props.shareCode, file.token)
    attachments.value = attachments.value.filter(item => item.id !== file.id)
    revokePreview(file)
  } catch (error) { ElMessage.error(error.message || '移除失败，请重试') }
  finally { file.removing = false }
}
const previewAttachment = file => { previewFile.value = file; previewVisible.value = true }
const downloadAttachment = async file => {
  if (!file?.token || downloading.value) return
  downloading.value = true
  try {
    const blob = await getShareChatAttachment(props.shareCode, file.token)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.name
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) { ElMessage.error(error.message || '下载失败或附件已过期') }
  finally { downloading.value = false }
}
const newConversation = async () => {
  if (isActive.value || pendingRecovery.value || status.value === 'attention') return
  // 已发送附件供当前会话重试使用，清空时由短期存储到期清理，不删除其他会话文件。
  for (const file of [...attachments.value]) await removeAttachment(file)
  if (attachments.value.length) return
  for (const url of previewUrls) URL.revokeObjectURL(url)
  previewUrls.clear()
  messages.value = []
  inputText.value = ''
  previewVisible.value = false
  forgetRecovery()
  clearUnavailable()
}
onUnmounted(() => { for (const url of previewUrls) URL.revokeObjectURL(url) })
</script>

<style scoped>
.chat-template { display: flex; flex-direction: column; height: calc(100dvh - 112px); min-height: 460px; max-width: 1024px; margin: 24px auto; background: var(--runtime-surface, #fff); border: 1px solid var(--runtime-border, #dbe2ea); border-radius: 24px; box-shadow: var(--runtime-shadow); overflow: hidden; }
.chat-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 18px 28px; border-bottom: 1px solid var(--runtime-border, #dbe2ea); }
.chat-toolbar-main { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.chat-toolbar-main strong { font-size: 14px; font-weight: 600; color: var(--runtime-text); }
.chat-toolbar-main span { color: var(--runtime-secondary); font-size: 12px; }
.chat-messages { flex: 1; min-height: 0; padding: 30px 32px; overflow-y: auto; scrollbar-width: thin; }
.chat-welcome { max-width: 580px; margin: 44px auto; text-align: center; }
.welcome-icon { display: inline-flex; width: 64px; height: 64px; border-radius: 20px; background: var(--runtime-primary-soft); font-size: 32px; color: var(--workflow-primary, #4f46e5); }
.chat-welcome h2 { font-size: 26px; font-weight: 600; letter-spacing: -0.5px; line-height: 1.4; color: var(--runtime-text, #1e293b); }
.chat-welcome p { color: var(--runtime-secondary, #64748b); line-height: 1.7; }
.suggested-questions { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; margin-top: 24px; }
.suggested-questions :deep(.el-button) { margin: 0; max-width: 100%; height: auto; white-space: normal; padding: 12px 16px; }
.message { display: flex; gap: 12px; margin-bottom: 28px; }
.user-msg { flex-direction: row-reverse; }
.avatar { flex: 0 0 36px; height: 36px; border-radius: 12px; background: var(--runtime-primary-soft, #eeecff); display: flex; align-items: center; justify-content: center; color: var(--workflow-primary, #4f46e5); }
.user-msg .avatar { background: var(--runtime-muted); color: var(--runtime-secondary); }
.message-body { min-width: 0; max-width: 82%; }
.message-label { font-size: 11px; color: var(--runtime-secondary, #64748b); margin-bottom: 6px; }
.user-msg .message-label { text-align: right; }
.bubble { padding: 16px 20px; border-radius: 4px 18px 18px; background: var(--runtime-muted, #f1f5f9); color: var(--runtime-text, #1e293b); }
.user-msg .bubble { border-radius: 18px 4px 18px 18px; background: var(--runtime-primary-soft, #eeecff); }
.bubble.is-error { border: 1px solid var(--workflow-danger, #b91c1c); }
.chat-result-images { margin-top: 14px; width: min(480px, 100%); }
.typing { display: flex; align-items: center; gap: 8px; color: var(--runtime-secondary, #64748b); }
.message-actions { display: flex; flex-wrap: wrap; align-items: start; gap: 8px; margin-top: 8px; }
.message-actions :deep(.el-button) { margin: 0; }
.chat-input { padding: 8px 28px; }
.pending-attachments, .message-attachments { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
.attachment-card { display: flex; align-items: center; gap: 8px; min-width: 0; max-width: 100%; padding: 8px; border: 1px solid var(--runtime-border); border-radius: 12px; background: var(--runtime-surface); color: var(--runtime-text); text-align: left; }
button.attachment-card, .attachment-preview { cursor: pointer; }
.attachment-card.is-error { border-color: var(--workflow-danger); }
.attachment-card img, .attachment-preview img { width: 40px; height: 40px; object-fit: cover; border-radius: 8px; }
.attachment-card .el-icon { font-size: 24px; color: var(--workflow-primary); flex-shrink: 0; }
.attachment-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.attachment-info strong { font-size: 12px; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.attachment-info small { font-size: 11px; color: var(--runtime-secondary); max-width: 240px; overflow-wrap: anywhere; }
.attachment-preview { display: flex; align-items: center; gap: 8px; padding: 0; border: 0; min-width: 0; background: transparent; color: inherit; text-align: left; }
.attachment-button { min-height: 44px; margin: 0 0 4px; }
.attachment-full-image { display: block; max-width: 100%; max-height: 65vh; object-fit: contain; margin: auto; }
.chat-composer { display: flex; align-items: flex-end; gap: 12px; border: 1px solid var(--runtime-border); border-radius: 16px; padding: 8px; background: var(--runtime-surface); }
.chat-composer:focus-within { border-color: var(--workflow-primary); box-shadow: 0 0 0 3px var(--runtime-primary-soft); }
.chat-input :deep(textarea) { resize: none; box-shadow: none !important; border: 0 !important; }
.send-btn { min-height: 44px; margin: 0 4px 4px 0; }
.chat-input-hint { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 4px 12px; padding: 2px 32px 20px; color: var(--runtime-secondary, #64748b); font-size: 11px; line-height: 1.5; }
.chat-recover { padding: 12px 24px; font-size: 12px; color: var(--runtime-secondary, #64748b); display: flex; flex-wrap: wrap; gap: 10px; }
@media (max-width: 1100px) { .chat-template { margin: 24px; } }
@media (max-width: 600px) { .chat-template { height: calc(100dvh - 88px); min-height: 0; margin: 12px; border-radius: 18px; } .chat-toolbar, .chat-recover { padding: 14px; } .chat-toolbar-main { gap: 4px; flex-direction: column; align-items: flex-start; } .chat-messages { padding: 20px 14px; } .chat-input { padding: 8px 12px; } .chat-input-hint { padding: 0 16px 14px; } .message { gap: 8px; } .message-body { max-width: calc(100% - 44px); } .bubble { padding: 12px 14px; } .chat-welcome { margin: 28px auto; } .chat-welcome h2 { font-size: 22px; } .chat-composer { gap: 4px; padding: 4px; } .send-btn { padding: 10px; } }
</style>
