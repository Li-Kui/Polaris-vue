import usePlatformUserStore from '@/store/modules/platformUser'
import {isPlatformConsolePath} from '@/utils/consoleRoute'
import {
    cancelAgentChat,
    getAgentChatRequest,
    getAgentChatRun,
    prepareAgentChat,
    streamAgentChat
} from '@/api/ai/agentChat'
import {agentChatInput, chatStageLabel, freshChatOptions, isActiveChatRequest} from './agentChat'

// 只保存所属主体/会话下的请求定位符；正文、外发内容、令牌和许可不写入浏览器存储。
function pointerKey(vm, conversation = vm.currentConvId) {
  const store = usePlatformUserStore()
  const tenant = store.tenant?.id || store.user?.tenantId
  const actor = store.user?.id
  return tenant && actor && conversation ? `polaris.agent-chat:${tenant}:${actor}:${conversation}` : null
}
function remember(vm, id, conversation) {
  const key = pointerKey(vm, conversation)
  try {if (key) id ? sessionStorage.setItem(key, id) : sessionStorage.removeItem(key)} catch { /* 存储不可用仍可从消息运行 ID 核对。 */ }
}
export default {
  data() {return {collaboration: freshChatOptions(), collaborationConfirmation: null, collaborationObserved: null,
    collaborationController: null, collaborationStopping: false, collaborationPreparing: false, collaborationVersion: 0}},
  computed: {
    collaborationAllowed() {return isPlatformConsolePath(this.$route.path) && usePlatformUserStore().user?.role === 'admin'},
    collaborationPending() {return isActiveChatRequest(this.collaborationObserved)},
    collaborationAgentName() {return this.agents.find(a => String(a.id) === String(this.collaboration.agentId))?.agentName || '未选择智能体'}
  },
  watch: {
    currentConvId() {
      this.collaborationVersion++
      this.collaboration = freshChatOptions(); this.collaborationObserved = null; this.collaborationConfirmation = null
    },
    'collaboration.enabled'(enabled) {
      if (enabled) {
        if (!this.collaboration.agentId) this.collaboration.agentId = this.agents.find(a => a.agentCode === this.selectedAgentCode)?.id || null
      }
    }
  },
  beforeUnmount() {
    this.collaborationVersion++
    this.abortCollaboration()
  },
  methods: {
    async restoreCollaboration(conversation, messages) {
      if (!this.collaborationAllowed || String(conversation) !== String(this.currentConvId)) return
      const version = ++this.collaborationVersion, key = pointerKey(this, conversation)
      let requestId
      try {requestId = key ? sessionStorage.getItem(key) : null} catch { /* 仅定位符恢复。 */ }
      if (requestId && /^[A-Za-z0-9_.:-]{1,64}$/.test(requestId)) {
        try {
          const result = await getAgentChatRequest(requestId)
          if (version !== this.collaborationVersion) return
          if (result.code === 200) {
            this.collaborationObserved = result.data
            this.collaboration = {...this.collaboration, enabled: true, agentId: result.data.agentId,
              answerMode: result.data.status === 'SUCCEEDED' ? 'CONTINUE_HISTORY' : result.data.answerMode}
            return
          }
        } catch {
          if (version === this.collaborationVersion) {
            this.collaborationObserved = {requestId, status: 'UNKNOWN'}
            this.$message.warning('原协作状态暂不可用，请刷新或停止原请求；不会重新执行')
          }
          return
        }
      }
      // 新标签页可能没有定位符；最新用户消息也可能属于运行中或失败的协作。
      // 只看会话最后一条，避免跳过失败请求或普通消息而误恢复更早的成功回答。
      const last = messages.at(-1)
      if (last?.agentRunId) {
        try {
          const result = await getAgentChatRun(last.agentRunId)
          if (version !== this.collaborationVersion || result.code !== 200) return
          this.collaborationObserved = result.data; remember(this, result.data.requestId, conversation)
          this.collaboration = {...this.collaboration, enabled: true, agentId: result.data.agentId,
            answerMode: result.data.status === 'SUCCEEDED' ? 'CONTINUE_HISTORY' : result.data.answerMode}
        } catch { /* 已归档回答仍可单独读取来源。 */ }
      }
    },
    async refreshCollaboration() {
      const current = this.collaborationObserved, conversation = this.currentConvId, version = this.collaborationVersion
      if (!current?.requestId || this.isStreaming) return
      try {
        const result = await getAgentChatRequest(current.requestId)
        if (version !== this.collaborationVersion || String(conversation) !== String(this.currentConvId)) return
        this.collaborationObserved = result.data
        if (!isActiveChatRequest(result.data)) await this.loadMessageList(conversation)
      } catch {this.$message.warning('暂时无法核对原状态，请稍后刷新；不会自动重试执行')}
    },
    async prepareCollaboration() {
      if (!this.collaborationAllowed || this.isStreaming || this.collaborationPending) return
      if (this.attachments.length || this.relatedDocumentIds.length || this.uploadingAttachment) {
        this.$message.warning('协作对话暂不接收附件或指定资料，请先移除；可使用所选知识库')
        return
      }
      let input
      try {input = agentChatInput({conversationId: this.currentConvId, question: this.inputText, knowledgeBaseId: this.selectedKbId,
        options: this.collaboration, requestKey: crypto.randomUUID()})}
      catch (error) {this.$message.warning(error.message); return}
      const version = ++this.collaborationVersion, conversation = this.currentConvId
      this.isStreaming = true; this.collaborationPreparing = true
      try {
        const result = await prepareAgentChat(input)
        if (version !== this.collaborationVersion || String(conversation) !== String(this.currentConvId)) {
          if (result.data?.status === 'PREPARED') cancelAgentChat(result.data.requestId).catch(() => {})
          return
        }
        if (result.code !== 200 || result.data.status !== 'PREPARED') throw new Error('确认已变化，请核对原状态')
        this.collaborationObserved = result.data; remember(this, result.data.requestId, conversation)
        this.collaborationConfirmation = {input, view: result.data}
      } catch (error) {
        if (version === this.collaborationVersion) {this.$message.warning(error.message || '本轮确认失败'); this.isStreaming = false}
      } finally {
        if (version === this.collaborationVersion) this.collaborationPreparing = false
      }
    },
    async dismissCollaboration() {
      if (this.collaborationStopping) return
      const id = this.collaborationConfirmation?.view.requestId
      if (!id) return
      this.collaborationStopping = true
      try {const result = await cancelAgentChat(id); this.collaborationObserved = result.data}
      catch {this.$message.warning('取消确认未核实，请刷新状态；不会发送本轮')}
      finally {this.collaborationConfirmation = null; this.collaborationStopping = false; this.isStreaming = false}
    },
    async sendConfirmedCollaboration() {
      const confirmation = this.collaborationConfirmation
      if (!confirmation || this.collaborationController || this.collaborationStopping) return
      if (new Date(confirmation.view.expiresAt).getTime() <= Date.now()) {await this.dismissCollaboration(); this.$message.warning('本轮确认已过期，请重新发送'); return}
      this.collaborationConfirmation = null
      const {input, view} = confirmation, conversation = input.conversationId, version = this.collaborationVersion
      const controller = new AbortController(); this.collaborationController = controller
      this.inputText = ''; this.isStreaming = true
      this.messages.push({role: 'assistant', content: '', loading: true, streaming: true, statusMsg: '正在核对输入', agentRunId: null})
      const answer = this.messages[this.messages.length - 1]
      let userSaved = false
      try {
        await streamAgentChat(view.requestId, input, async (event, payload) => {
          if (version !== this.collaborationVersion) return
          if (event === 'started') {
            answer.agentRunId = payload.runId
            this.collaborationObserved = {...view, status: 'RUNNING', runId: payload.runId}
          } else if (event === 'stage') {
            answer.statusMsg = chatStageLabel(payload.stage, payload.callNo)
            answer.loading = true; this.$nextTick(() => this.scrollToBottom())
          } else if (event === 'user_message_saved' && !userSaved) {
            userSaved = true
            this.messages.splice(this.messages.indexOf(answer), 0, {id: payload.messageId, role: 'user', content: input.question, agentRunId: answer.agentRunId})
          } else if (event === 'message_saved') answer.id = payload.messageId
          else if (event === 'message') {answer.content = payload.text || ''; answer.loading = false; answer.statusMsg = ''}
          else if (event === 'done' || event === 'existing') this.collaborationObserved = payload
          // sources 事件只有指针，展示使用受当前权限控制的来源接口。
        }, controller.signal)
      } catch (error) {
        if (error.name !== 'AbortError') this.$message.warning('连接未完成，正在核对原请求状态；不会重新发送')
      } finally {
        if (version === this.collaborationVersion) {
          try {
            const result = await getAgentChatRequest(view.requestId)
            if (version === this.collaborationVersion) this.collaborationObserved = result.data
          } catch {this.$message.warning('原请求状态暂不可用，请稍后刷新；不要重复发送')}
          if (version === this.collaborationVersion) {
            if (this.collaborationObserved?.status === 'SUCCEEDED') this.collaboration.answerMode = 'CONTINUE_HISTORY'
            this.collaboration.allowExternalTools = false; this.collaboration.externalQuery = ''; this.collaboration.inheritPreviousContext = false
            this.collaboration.contextStartMessageId = null
            if (!userSaved && !this.inputText) this.inputText = input.question
            this.isStreaming = false; this.collaborationController = null
            await this.loadMessageList(conversation); await this.loadConvList()
            this.$nextTick(() => this.focusInput())
          }
        }
      }
    },
    async stopCollaboration() {
      if (this.collaborationStopping) return
      if (this.collaborationPreparing) {
        this.collaborationVersion++; this.collaborationPreparing = false; this.isStreaming = false
        return
      }
      if (this.collaborationConfirmation) return this.dismissCollaboration()
      const id = this.collaborationObserved?.requestId
      if (!id) return
      const version = this.collaborationVersion, conversation = this.currentConvId, controller = this.collaborationController
      this.collaborationStopping = true
      try {
        const result = await cancelAgentChat(id)
        if (version !== this.collaborationVersion || String(conversation) !== String(this.currentConvId)) return
        this.collaborationObserved = result.data
        controller?.abort()
        if (!controller) await this.loadMessageList(conversation)
      } catch {this.$message.warning('停止尚未核实，请刷新原状态')}
      finally {this.collaborationStopping = false}
    },
    abortCollaboration() {
      const id = this.collaborationObserved?.requestId
      // 页面卸载只取消本页持有的流/确认；恢复状态的观察页面不拥有执行许可。
      if (this.collaborationController || this.collaborationConfirmation) {
        if (id) cancelAgentChat(id).catch(() => {})
        this.collaborationController?.abort(); this.collaborationController = null
      }
    }
  }
}
