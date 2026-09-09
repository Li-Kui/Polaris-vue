<template>
  <div class="ai-float-chat-container">
    <!-- 悬浮球 -->
    <div
      :class="{ 'is-active': chatVisible }"
      :style="{ background: themeColorGradient, boxShadow: themeColorShadow }"
      class="ai-float-btn"
      @click="toggleChat"
    >
      <div class="ai-btn-inner">
        <svg v-if="!chatVisible" class="ai-svg-icon" fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M20 2H4C2.9 2 2 2.9 2 4V22L6 18H20C21.1 18 22 17.1 22 16V4C22 2.9 21.1 2 20 2Z" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"/>
          <path d="M10.5 7L11.7 9.3L14 10.5L11.7 11.7L10.5 14L9.3 11.7L7 10.5L9.3 9.3L10.5 7Z" fill="currentColor"/>
          <path d="M15.5 12L16.1 13.1L17.2 13.5L16.1 13.9L15.5 15L14.9 13.9L13.8 13.5L14.9 13.1L15.5 12Z" fill="currentColor"/>
        </svg>
        <el-icon v-else><close /></el-icon>
      </div>
      <span :style="{ borderColor: themeColor }" class="ai-btn-pulse"></span>
    </div>

    <!-- 聊天面板 (科技感暗黑毛玻璃风格) -->
    <transition name="slide-fade">
      <div v-show="chatVisible" class="ai-chat-panel">
        <!-- 头部 -->
        <div class="panel-header">
          <div class="header-left">
            <span :style="{ background: themeColorGradient }" class="avatar-mini">✦</span>
            <div class="title-wrapper">
              <span class="title">AI 智能助手</span>
              <span class="subtitle">即时对话 • 无负担</span>
            </div>
          </div>
          <div class="header-right">
            <!-- 清空当前对话 -->
            <el-tooltip content="清空当前对话" placement="top">
              <el-button
                :disabled="isStreaming || (messages.length === 0 && !selectedWorkflowCode)"
                class="action-btn"
                icon="RefreshLeft"
                link
                @click="handleClearChat"
              ></el-button>
            </el-tooltip>
            <!-- 折叠关闭 -->
            <el-button class="action-btn" icon="Minus" link @click="chatVisible = false"></el-button>
          </div>
        </div>

        <!-- 配置栏：模型、知识库与工作流配置区 (两行自适应布局，防止溢出) -->
        <div class="panel-configs">
          <div class="config-row">
            <el-select
              v-model="selectedModelName"
              :disabled="isStreaming || !!selectedWorkflowCode"
              class="config-select"
              placeholder="选择模型"
              size="small"
              style="width: 49%"
              @change="handleModelOrKbChange"
            >
              <el-option
                v-for="item in models"
                :key="item.id"
                :label="'🤖 ' + item.name"
                :value="item.modelName"
              />
            </el-select>
            <el-select
              v-model="selectedKbId"
              :disabled="isStreaming || !!selectedWorkflowCode"
              class="config-select"
              clearable
              placeholder="关联知识库"
              size="small"
              style="width: 49%; margin-left: 2%"
              @change="handleModelOrKbChange"
            >
              <el-option
                v-for="item in knowledgeBases"
                :key="item.id"
                :label="'📚 ' + item.name"
                :value="item.id"
              />
            </el-select>
          </div>
          <div class="config-row" style="margin-top: 6px;">
            <el-select
              v-model="selectedWorkflowCode"
              :disabled="isStreaming"
              class="config-select"
              clearable
              placeholder="选用智能体工作流 (可选)"
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="item in workflows"
                :key="item.workflowCode"
                :label="'⚡ ' + item.workflowName"
                :value="item.workflowCode"
              />
            </el-select>
          </div>
        </div>

        <!-- 消息区 -->
        <div ref="msgArea" class="panel-messages">
          <!-- 欢迎页 -->
          <div v-if="messages.length === 0" class="welcome-container">
            <div class="welcome-glow"></div>
            <div :style="{ color: '#dfb889' }" class="welcome-icon">✦</div>
            <h3>你好！我是 AI 助理</h3>
            <p>我可以回答问题、编写代码或提供决策支持。</p>
            <p class="welcome-hint">
              <span v-if="selectedWorkflowCode">🚀 已选用工作流: <b>{{ currentWorkflowName }}</b></span>
              <span v-else>输入下方框内即可开始，聊天记录在关闭或重置后不会保留。</span>
            </p>
          </div>
          
          <!-- 消息列表 -->
          <div v-else class="messages-list">
            <div
              v-for="(msg, index) in messages"
              :key="index"
              :class="['msg-row', msg.role]"
            >
              <!-- AI 头像 -->
              <div v-if="msg.role === 'assistant'" :style="{ background: themeColorGradient }" class="avatar ai-av">✦</div>

              <!-- 气泡 -->
              <div :class="['bubble', msg.role + '-bubble', { 'has-error': msg.error }]">
                <div v-if="msg.loading" class="loading-wrap">
                  <div class="loading-dots">
                    <span></span><span></span><span></span>
                  </div>
                  <div v-if="msg.statusMsg" class="loading-status-text">{{ msg.statusMsg }}</div>
                </div>
                <div v-else-if="msg.error" class="error-text">
                  <el-icon><warning /></el-icon> {{ msg.error }}
                </div>
                <div v-else>
                  <!-- 深度思维链 -->
                  <div v-if="msg.reasoningContent" class="thinking-block">
                    <div class="thinking-header" @click="toggleThinkingExpanded(index)">
                      <el-icon><cpu /></el-icon>
                      <span>{{ msg.content ? '已完成深度思考' : '深度思考推理中...' }}</span>
                      <el-icon :class="['arrow', { 'is-active': msg.expanded }]"><arrow-down /></el-icon>
                    </div>
                    <el-collapse-transition>
                      <div v-show="msg.expanded" class="thinking-content" v-html="renderMarkdown(msg.reasoningContent)"></div>
                    </el-collapse-transition>
                  </div>
                  <!-- 正文 -->
                  <div v-if="msg.statusMsg" class="loading-status-text inline-status">{{ msg.statusMsg }}</div>
                  <div class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
                  <div v-if="msg.searchSources && msg.searchSources.length" class="search-sources-panel">
                    <div class="search-sources-header" @click="toggleSearchSources(msg)">
                      <span>已搜索 {{ msg.searchSourceCount || msg.searchSources.length }} 个网页</span>
                      <span class="search-sources-action">{{ msg.searchSourcesExpanded ? '收起' : '来源' }}</span>
                    </div>
                    <div v-if="msg.searchQuery && msg.searchSourcesExpanded" class="search-query">{{ msg.searchQuery }}</div>
                    <div v-if="msg.searchSourcesExpanded" class="search-source-list">
                      <a
                        v-for="source in msg.searchSources"
                        :key="source.index || source.url"
                        class="search-source-item"
                        :href="safeSourceUrl(source.url)"
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        <span class="source-index">{{ source.index }}</span>
                        <span class="source-body">
                          <span class="source-title">{{ source.title || source.url }}</span>
                          <span class="source-url">{{ getSourceHost(source.url) }}</span>
                        </span>
                      </a>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 用户头像 -->
              <div v-if="msg.role === 'user'" class="avatar user-av">我</div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div :class="{ 'is-focused': inputFocused }" class="panel-input">
          <div class="input-container">
            <el-input
              v-model="inputText"
              :disabled="isStreaming"
              :rows="2"
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              resize="none"
              type="textarea"
              @blur="inputFocused = false"
              @focus="inputFocused = true"
              @keydown="handleKeyDown"
            />
            <div class="input-actions">
              <!-- 左侧操作区：录音按钮 + 联网搜索 -->
              <div style="display: flex; align-items: center; gap: 8px;">
                <!-- 语音输入 -->
                <el-tooltip :content="isListening ? '停止听取并识别' : '语音输入'" placement="top">
                  <el-button
                    :class="{ 'pulse-active': isListening }"
                    :type="isListening ? 'danger' : 'info'"
                    circle
                    size="small"
                    style="font-size: 13px; border: none; background-color: rgba(255,255,255,0.06); color: #f8fafc;"
                    @click="isListening ? stopVoiceInput() : startVoiceInput()"
                  >
                    <el-icon v-if="isListening" class="is-loading"><loading /></el-icon>
                    <el-icon v-else><microphone /></el-icon>
                  </el-button>
                </el-tooltip>
                
                <!-- 联网搜索开关 (工作流模式下屏蔽) -->
                <div v-if="currentModelSupportsSearch && !selectedWorkflowCode" class="search-toggle-wrapper">
                  <span class="search-label">联网搜索</span>
                  <el-switch
                    v-model="enableWebSearch"
                    active-color="#3b82f6"
                    size="small"
                    @change="handleWebSearchChange"
                  ></el-switch>
                </div>
              </div>

              <!-- 右侧按钮：停止/发送 -->
              <div class="action-buttons">
                <!-- 停止生成 -->
                <el-button
                  v-if="isStreaming"
                  circle
                  icon="VideoPause"
                  size="small"
                  type="danger"
                  @click="handleStopMessage"
                ></el-button>
                <!-- 发送按钮 -->
                <el-button
                  v-else
                  :disabled="!inputText.trim()"
                  :style="{ backgroundColor: themeColor, borderColor: themeColor }"
                  circle
                  icon="Position"
                  size="small"
                  type="primary"
                  @click="handleSendMessage"
                ></el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import {getToken} from '@/utils/auth'
import {listAvailableModel} from '@/api/ai/model'
import {listKnowledge} from '@/api/ai/knowledge'
import {createConversation, updateConversationConfig} from '@/api/ai/chat'
import {cancelWorkflowExecution, listActiveWorkflows, streamWorkflowExecution} from '@/api/ai/workflow'

import useSettingsStore from '@/store/modules/settings'
import {sanitizeUrl} from '@/utils/safeUrl'

export default {
  name: 'AiFloatChat',
  data() {
    return {
      chatVisible: false,
      inputFocused: false,
      inputText: '',
      isStreaming: false,
      currentConvId: null,
      messages: [],

      // 模型与知识库
      models: [],
      selectedModelName: null,
      knowledgeBases: [],
      selectedKbId: null,

      // 工作流
      workflows: [],
      selectedWorkflowCode: null,

      // 语音识别录音
      isListening: false,
      voiceBaseText: '',
      voiceTempText: '',

      // 联网搜索
      enableWebSearch: false,

      currentReader: null,
      sseEventBuffer: null,
      workflowAbortController: null,
      currentWorkflowExecutionId: null
    }
  },
  computed: {
    settingsStore() {
      return useSettingsStore()
    },
    themeColor() {
      return this.settingsStore.theme || '#1890ff'
    },
    themeColorGradient() {
      return 'linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 50%, var(--el-color-primary-dark-2) 100%)'
    },
    themeColorShadow() {
      return '0 8px 32px var(--el-color-primary-light-5), 0 0 15px var(--el-color-primary-light-7)'
    },
    currentModelSupportsSearch() {
      if (!this.selectedModelName) return false
      const m = this.models.find(item => item.modelName === this.selectedModelName)
      return m && m.enableSearch === '1'
    },
    currentWorkflowName() {
      if (!this.selectedWorkflowCode) return ''
      const w = this.workflows.find(item => item.workflowCode === this.selectedWorkflowCode)
      return w ? w.workflowName : ''
    }
  },
  created() {
    this.loadModels()
    this.loadKnowledgeBases()
    this.loadActiveWorkflows()
    const savedPreference = localStorage.getItem('ai_chat_enable_web_search')
    this.enableWebSearch = savedPreference === 'true'
  },
  beforeUnmount() {
    this.abortStream()
    this.cleanupVoiceInput()
  },
  methods: {
    lightenColor(hex, percent) {
      hex = hex.replace(/^\s*#|\s*$/g, '')
      if (hex.length === 3) {
        hex = hex.replace(/(.)/g, '$1$1')
      }
      let r = parseInt(hex.substr(0, 2), 16),
        g = parseInt(hex.substr(2, 2), 16),
        b = parseInt(hex.substr(4, 2), 16)
      return `#${(0x1000000 +
        Math.round((255 - r) * (percent / 100) + r) * 0x10000 +
        Math.round((255 - g) * (percent / 100) + g) * 0x100 +
        Math.round((255 - b) * (percent / 100) + b)
      )
        .toString(16)
        .slice(1)}`
    },
    toggleChat() {
      this.chatVisible = !this.chatVisible
      if (this.chatVisible) {
        this.$nextTick(() => {
          this.scrollToBottom()
        })
      }
    },
    async loadModels() {
      try {
        const res = await listAvailableModel()
        if (res.code === 200) {
          this.models = (res.data.rows || res.data || []).filter(m => m.isDefaultEmbedding !== '1' && !m.modelName.toLowerCase().includes('embed'))
          if (this.models.length === 0) {
            const defaultModelName = import.meta.env.VITE_APP_DEFAULT_MODEL || 'deepseek-chat'
            this.models = [{
              id: 'default',
              name: '默认模型',
              modelName: defaultModelName
            }]
            this.selectedModelName = defaultModelName
          } else {
            const defModel = this.models.find(m => m.isDefault === '1')
            if (defModel) {
              this.selectedModelName = defModel.modelName
            } else if (this.models.length > 0) {
              this.selectedModelName = this.models[0].modelName
            }
          }
        }
      } catch (e) {
        console.error('加载大模型列表失败', e)
        const defaultModelName = import.meta.env.VITE_APP_DEFAULT_MODEL || 'deepseek-chat'
        this.models = [{
          id: 'default',
          name: '默认模型',
          modelName: defaultModelName
        }]
        this.selectedModelName = defaultModelName
      }
    },
    async loadKnowledgeBases() {
      try {
        const res = await listKnowledge()
        if (res.code === 200) {
          this.knowledgeBases = res.data.rows || []
        }
      } catch (e) {
        console.error('加载知识库失败', e)
      }
    },
    async loadActiveWorkflows() {
      try {
        const res = await listActiveWorkflows()
        if (res.code === 200) {
          this.workflows = res.data || []
        }
      } catch (e) {
        console.error('加载工作流列表失败', e)
      }
    },
    async handleModelOrKbChange() {
      if (this.currentConvId) {
        try {
          await updateConversationConfig(this.currentConvId, this.selectedModelName, this.selectedKbId)
        } catch (e) {
          console.error('同步会话配置失败', e)
        }
      }
    },
    handleWebSearchChange(val) {
      localStorage.setItem('ai_chat_enable_web_search', val ? 'true' : 'false')
    },
    handleKeyDown(e) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        this.handleSendMessage()
      }
    },
    async handleSendMessage() {
      if (this.isStreaming) return
      const text = this.inputText.trim()
      if (!text) return

      this.inputText = ''
      this.isStreaming = true

      // 用户消息气泡
      this.messages.push({ role: 'user', content: text, error: null })

      // AI 占位
      const aiIndex = this.messages.length
      this.messages.push({
        role: 'assistant',
        content: '',
        reasoningContent: '',
        loading: true,
        expanded: true,
        error: null
      })
      this.$nextTick(() => this.scrollToBottom())

      const isWorkflowMode = !!this.selectedWorkflowCode

      try {
        // 普通聊天和工作流都必须绑定会话，保证消息与待审批执行可恢复。
        if (!this.currentConvId) {
          const convRes = await createConversation(this.selectedModelName, this.selectedKbId)
          if (convRes.code === 200) {
            this.currentConvId = convRes.data.id
          } else {
            throw new Error('创建会话失败')
          }
        }

        const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
        const enableSearchParam = this.enableWebSearch && this.currentModelSupportsSearch

        if (isWorkflowMode) {
          const workflowController = new AbortController()
          this.workflowAbortController = workflowController
          this.messages[aiIndex].loading = false
          await streamWorkflowExecution({
            workflowCode: this.selectedWorkflowCode,
            message: text,
            conversationId: this.currentConvId,
            testRun: false
          }, (event, envelope) => {
            const payload = envelope.payload || {}
            if (envelope.executionId) {
              this.currentWorkflowExecutionId = envelope.executionId
            }
            if (event === 'node_chunk') {
              this.messages[aiIndex].content += payload.text || ''
            } else if (event === 'node_thinking') {
              this.messages[aiIndex].reasoningContent =
                (this.messages[aiIndex].reasoningContent || '') + (payload.text || '')
            } else if (event === 'status') {
              this.messages[aiIndex].statusMsg = payload.message || ''
            } else if (event === 'search_sources') {
              this.messages[aiIndex].searchQuery = payload.query || ''
              this.messages[aiIndex].searchSourceCount =
                payload.count || (payload.sources || []).length
              this.messages[aiIndex].searchSources = payload.sources || []
            } else if (event === 'workflow_done') {
              this.messages[aiIndex].content = payload.result || this.messages[aiIndex].content
              this.currentWorkflowExecutionId = null
            } else if (event === 'node_interrupt') {
              this.messages[aiIndex].content += '\n\n工作流正在等待人工审批，请在 AI 对话页面处理。'
              this.workflowAbortController = null
            } else if (event === 'error') {
              throw new Error(payload.message || '工作流执行失败')
            }
            this.$nextTick(() => this.scrollToBottom())
          }, workflowController.signal)
          return
        }

        const token = getToken()

        const payload = {
          conversationId: this.currentConvId,
          message: text,
          enableSearch: enableSearchParam,
          attachmentTokens: []
        }

        const response = await fetch(`${baseUrl}/ai/chat/stream`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json;charset=UTF-8',
            'Authorization': 'Bearer ' + token
          },
          body: JSON.stringify(payload)
        })

        if (!response.ok) throw new Error(`HTTP ${response.status}`)

        const reader = response.body.getReader()
        this.currentReader = reader
        const decoder = new TextDecoder('utf-8')
        let buffer = ''
        this.sseEventBuffer = null

        this.messages[aiIndex].loading = false

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop()

          for (const line of lines) {
            if (line.startsWith('event:')) {
              this.sseEventBuffer = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              let data = ''
              if (line.startsWith('data: ')) {
                data = line.slice(6)
              } else {
                data = line.slice(5)
              }
              const event = this.sseEventBuffer || 'message'

              if (event === 'message') {
                const processedData = data ? data.replace(/__SSE_NEWLINE__/g, '\n') : ''
                this.messages[aiIndex].content += processedData
                this.$nextTick(() => this.scrollToBottom())
              } else if (event === 'reasoning') {
                const processedData = data ? data.replace(/__SSE_NEWLINE__/g, '\n') : ''
                this.messages[aiIndex].reasoningContent = (this.messages[aiIndex].reasoningContent || '') + processedData
                this.$nextTick(() => this.scrollToBottom())
              } else if (event === 'status') {
                this.messages[aiIndex].statusMsg = data || ''
              } else if (event === 'moderation_blocked') {
                let blockInfo = {}
                try {
                  blockInfo = JSON.parse(data || '{}')
                } catch (e) {
                  blockInfo = { message: data }
                }
                const warnMsg = blockInfo.message || '内容触发安全策略，已为您终止输出'
                this.$message.warning(warnMsg)
                const cur = this.messages[aiIndex]
                if (cur.content) {
                  cur.content = cur.content + '\n\n' + `【已拦截：${warnMsg}】`
                } else {
                  cur.content = `【已拦截：${warnMsg}】`
                }
                cur.loading = false
                this.isStreaming = false
                this.currentReader = null
                return
              } else if (event === 'search_sources') {
                try {
                  const payload = JSON.parse(data || '{}')
                  this.messages[aiIndex].searchQuery = payload.query || ''
                  this.messages[aiIndex].searchSourceCount = payload.count || (payload.sources || []).length
                  this.messages[aiIndex].searchSources = payload.sources || []
                  this.$nextTick(() => this.scrollToBottom())
                } catch (err) {
                  console.warn('解析联网搜索来源失败', err)
                }
              } else if (event === 'done') {
                this.isStreaming = false
                this.currentReader = null
                return
              } else if (event === 'error') {
                throw new Error(data.trim() || 'AI 服务异常')
              }
            } else if (line.trim() === '') {
              this.sseEventBuffer = null
            }
          }
        }

      } catch (e) {
        if (e.name === 'AbortError') return
        const errMsg = e.message || '服务异常，请重试'
        this.messages[aiIndex].loading = false
        this.messages[aiIndex].error = errMsg
        this.$message.error('AI 响应失败：' + errMsg)
      } finally {
        this.isStreaming = false
        this.currentReader = null
      }
    },
    handleStopMessage() {
      this.abortStream()
      this.isStreaming = false
      if (this.messages.length > 0) {
        const lastMsg = this.messages[this.messages.length - 1]
        if (lastMsg.role === 'assistant') {
          lastMsg.loading = false
        }
      }
    },
    getSourceHost(url) {
      if (!url) return ''
      try {
        return new URL(url).hostname.replace(/^www\./, '')
      } catch (e) {
        return url
      }
    },
    safeSourceUrl(url) {
      return sanitizeUrl(url, { allowMailto: false, allowRelative: false })
    },
    toggleSearchSources(msg) {
      msg.searchSourcesExpanded = !msg.searchSourcesExpanded
    },
    normalizeMarkdownSyntax(text) {
      if (!text) return ''
      return text
        .replace(/\r\n/g, '\n')
        .split('\n')
        .map(line => {
          let normalized = line
          normalized = normalized.replace(/^(#{1,4})([^#\s].*)$/, '$1 $2')
          normalized = normalized.replace(/^(\s*)[-*](\S.*)$/, '$1- $2')
          normalized = normalized.replace(/^(\s*\d+\.)(\S.*)$/, '$1 $2')
          return normalized
        })
        .join('\n')
    },
    abortStream() {
      if (this.workflowAbortController) {
        this.workflowAbortController.abort()
        this.workflowAbortController = null
        if (this.currentWorkflowExecutionId) {
          cancelWorkflowExecution(this.currentWorkflowExecutionId).catch(() => {})
          this.currentWorkflowExecutionId = null
        }
      }
      if (this.currentReader) {
        try {
          this.currentReader.cancel()
        } catch (_) {}
        this.currentReader = null
      }
      this.isStreaming = false
    },
    handleClearChat() {
      this.handleStopMessage()
      this.messages = []
      this.currentConvId = null
      this.selectedWorkflowCode = null
      this.$message.success('已清空当前会话，您可以重新开始聊天。')
    },
    toggleThinkingExpanded(index) {
      this.messages[index].expanded = !this.messages[index].expanded
    },
    scrollToBottom() {
      const area = this.$refs.msgArea
      if (area) {
        area.scrollTop = area.scrollHeight
      }
    },
    escapeHtml(str) {
      return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
    },
    renderMarkdown(text) {
      if (!text) return ''
      let html = this.escapeHtml(this.normalizeMarkdownSyntax(text))

      // 1. 代码块
      html = html.replace(
        /```[\w]*\n?([\s\S]*?)```/g,
        '<pre class="code-block"><code>$1</code></pre>'
      )

      // 2. 行内代码
      html = html.replace(/`([^`\n]+)`/g, '<code class="inline-code">$1</code>')

      // 3. 标题
      html = html.replace(/^####\s+(.+)$/gm, '<h6>$1</h6>')
      html = html.replace(/^###\s+(.+)$/gm, '<h5>$1</h5>')
      html = html.replace(/^##\s+(.+)$/gm, '<h4>$1</h4>')
      html = html.replace(/^#\s+(.+)$/gm, '<h4>$1</h4>')

      // 4. 粗体与斜体
      html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      html = html.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>')

      // 4.5 超链接
      html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (match, text, url) => {
        let href = url
        // 自动纠错一：剥离外部域名，防范大模型幻想拼凑 klingai.com 或其他无关域名等情况
        if (url.startsWith('http://') || url.startsWith('https://')) {
          if (url.includes('/upload/') && (url.includes('/profile/') || url.includes('/pro'))) {
            url = url.replace(/^https?:\/\/[^\/]+/, '')
          }
        }
        // 自动纠错二：将错改的 /proXXX/upload/ 路径还原回系统的 /profile/upload/
        if (url.includes('/upload/') && !url.startsWith('/profile/')) {
          url = url.replace(/^\/pro[^\/]*\/upload\//, '/profile/upload/')
        }
        if (url.startsWith('/profile')) {
          const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
          href = baseUrl + url
        }
        href = sanitizeUrl(href)
        if (!href) return text
        return `<a href="${href}" target="_blank" rel="noopener noreferrer" class="markdown-link" style="color: #3b82f6; font-weight: 600; text-decoration: underline; margin: 0 4px;">${text}</a>`
      })

      // 5. 表格
      const lines = html.split('\n')
      let inTable = false
      let tableHtml = ''
      for (let i = 0; i < lines.length; i++) {
        let line = lines[i].trim()
        if (line.startsWith('|') && line.endsWith('|')) {
          if (line.match(/^\|[\s-|-]*\|$/)) {
            lines[i] = ''
            continue
          }
          const cells = line.split('|').slice(1, -1).map(c => c.trim())
          if (!inTable) {
            inTable = true
            tableHtml += '<table class="ai-chat-table"><thead><tr>'
            cells.forEach(c => { tableHtml += `<th>${c}</th>` })
            tableHtml += '</tr></thead><tbody>'
          } else {
            tableHtml += '<tr>'
            cells.forEach(c => { tableHtml += `<td>${c}</td>` })
            tableHtml += '</tr>'
          }
          lines[i] = ''
        } else {
          if (inTable) {
            inTable = false
            tableHtml += '</tbody></table>'
            lines[i] = tableHtml + '\n' + lines[i]
            tableHtml = ''
          }
        }
      }
      if (inTable) {
        tableHtml += '</tbody></table>'
        lines.push(tableHtml)
      }
      html = lines.join('\n')

      // 6. 列表
      html = html.replace(/^\s*[-*] (.+)$/gm, '<li>$1</li>')
      html = html.replace(/(<li>[\s\S]*?<\/li>)/g, m => `<ul>${m}</ul>`)
      html = html.replace(/<\/ul>\s*<ul>/g, '')

      html = html.replace(/^\s*\d+\. (.+)$/gm, '<li>$1</li>')
      html = html.replace(/(<li>[\s\S]*?<\/li>)/g, m => `<ol>${m}</ol>`)
      html = html.replace(/<\/ol>\s*<ol>/g, '')

      // 7. 换行
      html = html.replace(/\n/g, '<br>')
      return html
    },

    // ──────────────────────────────────────────
    // 语音输入核心逻辑
    // ──────────────────────────────────────────
    startVoiceInput() {
      if (this.isStreaming) return
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition || window.mozSpeechRecognition || window.msSpeechRecognition
      if (!SpeechRecognition) {
        this.$message.warning('您的浏览器暂不支持原生语音识别，建议使用 Chrome、Edge 或 Safari 浏览器。')
        return
      }

      this.voiceBaseText = this.inputText
      this.voiceTempText = ''
      this.isListening = true

      try {
        const recognition = new SpeechRecognition()
        recognition.continuous = true
        recognition.interimResults = true
        recognition.lang = 'zh-CN'

        recognition.onstart = () => {
          console.log('Speech recognition started in float window')
        }

        recognition.onresult = (event) => {
          let interimTranscript = ''
          let finalTranscript = ''
          for (let i = event.resultIndex; i < event.results.length; ++i) {
            if (event.results[i].isFinal) {
              finalTranscript += event.results[i][0].transcript
            } else {
              interimTranscript += event.results[i][0].transcript
            }
          }
          const newlyRecognized = finalTranscript + interimTranscript
          this.voiceTempText = newlyRecognized
          this.inputText = this.voiceBaseText + newlyRecognized
        }

        recognition.onerror = (event) => {
          console.error('Speech recognition error in float window', event.error)
          if (event.code === 'not-allowed' || event.error === 'not-allowed') {
            this.$message.error('麦克风权限被拒绝，请在浏览器设置中开启麦克风权限！')
          } else if (event.error === 'network') {
            this.$message.error('语音识别网络连接超时，请检查网络（Chrome 语音识别可能需要连接谷歌服务器）')
          } else if (event.error === 'no-speech') {
            // 正常逻辑
          } else {
            this.$message.error('语音识别异常：' + event.error)
          }
          this.cleanupVoiceInput()
        }

        recognition.onend = () => {
          console.log('Speech recognition ended in float window')
          this.cleanupVoiceInput()
        }

        this.$options.recognitionInstance = recognition
        recognition.start()
      } catch (err) {
        console.error('Failed to start speech recognition in float window', err)
        this.$message.error('无法启动语音识别：' + (err.message || err))
        this.cleanupVoiceInput()
      }
    },

    stopVoiceInput() {
      if (this.$options.recognitionInstance) {
        try {
          this.$options.recognitionInstance.stop()
        } catch (e) {
          console.error(e)
        }
      }
      this.isListening = false
    },

    cancelVoiceInput() {
      if (this.$options.recognitionInstance) {
        try {
          this.$options.recognitionInstance.abort()
        } catch (e) {
          console.error(e)
        }
      }
      this.inputText = this.voiceBaseText
      this.isListening = false
    },

    cleanupVoiceInput() {
      this.isListening = false
      if (this.$options.recognitionInstance) {
        this.$options.recognitionInstance = null
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.ai-float-chat-container {
  position: fixed;
  right: 24px;
  bottom: 40px;
  z-index: 2002;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  user-select: none;
}

/* 悬浮球 */
.ai-float-btn {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  cursor: pointer;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  color: #fff;
  z-index: 10;
  background-size: 200% 200% !important;
  animation: auroraFlow 6s ease infinite, shadowPulse 4s ease-in-out infinite;

  &:hover {
    transform: scale(1.1) translateY(-3px);

    .ai-btn-pulse {
      animation: pulse 1.6s infinite;
    }
  }

  &.is-active {
    transform: rotate(90deg) scale(0.95);
    animation: shadowPulse 4s ease-in-out infinite;
  }
}

.ai-btn-inner {
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;

  .ai-svg-icon {
    width: 23px;
    height: 23px;
    display: block;
    filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.15));
    transition: transform 0.3s ease;
  }
}

.ai-float-btn:hover .ai-svg-icon {
  transform: rotate(8deg) scale(1.05);
}

.ai-btn-pulse {
  position: absolute;
  top: -4px;
  left: -4px;
  right: -4px;
  bottom: -4px;
  border: 2px solid rgba(124, 58, 237, 0.4);
  background: radial-gradient(circle, rgba(6, 182, 212, 0.1) 0%, transparent 80%);
  border-radius: 50%;
  opacity: 0;
  pointer-events: none;
  transition: all 0.3s;
}

@keyframes auroraFlow {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

@keyframes shadowPulse {
  0%, 100% {
    box-shadow: 0 8px 32px rgba(124, 58, 237, 0.45), 0 0 15px rgba(6, 182, 212, 0.25);
  }
  50% {
    box-shadow: 0 12px 40px rgba(124, 58, 237, 0.65), 0 0 25px rgba(6, 182, 212, 0.45);
  }
}

@keyframes pulse {
  0% {
    transform: scale(0.95);
    opacity: 0.8;
    border-color: rgba(124, 58, 237, 0.5);
    box-shadow: 0 0 0 0 rgba(124, 58, 237, 0.3);
  }
  100% {
    transform: scale(1.35);
    opacity: 0;
    border-color: rgba(6, 182, 212, 0);
    box-shadow: 0 0 20px 10px rgba(6, 182, 212, 0);
  }
}

/* 聊天面板 - 高端暗色系毛玻璃设计 */
.ai-chat-panel {
  position: absolute;
  right: 0;
  bottom: 64px;
  width: 390px;
  height: 570px;
  background: rgba(15, 23, 42, 0.94);
  backdrop-filter: blur(25px);
  -webkit-backdrop-filter: blur(25px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.45);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transform-origin: right bottom;
  user-select: text;
}

/* 面板头部 */
.panel-header {
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background: transparent;

  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;

    .avatar-mini {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      font-weight: bold;
    }

    .title-wrapper {
      display: flex;
      flex-direction: column;

      .title {
        font-size: 14px;
        font-weight: 600;
        color: #ffffff;
      }

      .subtitle {
        font-size: 10px;
        color: rgba(255, 255, 255, 0.45);
        margin-top: 1px;
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 4px;

    .action-btn {
      font-size: 16px;
      color: rgba(255, 255, 255, 0.7);
      padding: 6px;
      border-radius: 6px;
      transition: all 0.2s;

      &:hover {
        color: #3b82f6;
        background: rgba(255, 255, 255, 0.06);
      }

      &:disabled {
        color: rgba(255, 255, 255, 0.2);
        background: transparent;
      }
    }
  }
}

/* 配置选择区 (两行自适应) */
.panel-configs {
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.02);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;

  .config-row {
    display: flex;
    width: 100%;
  }

  :deep(.config-select) {
    .el-input__inner {
      border: 1px solid rgba(255, 255, 255, 0.08) !important;
      border-radius: 6px;
      background-color: rgba(255, 255, 255, 0.04) !important;
      color: #e2e8f0 !important;
      font-size: 11px;
      height: 28px;
      line-height: 28px;
      padding-left: 6px;
      padding-right: 18px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;

      &:focus {
        border-color: #3b82f6 !important;
      }
    }

    .el-input__icon {
      line-height: 28px;
    }
  }
}

/* 消息内容区 */
.panel-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: transparent;
  scroll-behavior: smooth;
  position: relative;

  &::-webkit-scrollbar {
    width: 5px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 3px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
}

/* 欢迎页 */
.welcome-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  height: 100%;
  color: #f8fafc;
  padding: 0 20px;
  position: relative;
  z-index: 1;

  .welcome-glow {
    position: absolute;
    width: 180px;
    height: 180px;
    background: radial-gradient(circle, rgba(59, 130, 246, 0.1) 0%, transparent 70%);
    z-index: -1;
  }

  .welcome-icon {
    font-size: 44px;
    margin-bottom: 16px;
    animation: float 3s ease-in-out infinite;
    text-shadow: 0 0 10px rgba(223, 184, 137, 0.2);
  }

  h3 {
    margin: 0 0 10px 0;
    font-size: 16px;
    font-weight: 600;
    color: #ffffff;
  }

  p {
    margin: 0 0 6px 0;
    font-size: 12px;
    line-height: 1.6;
    color: rgba(255, 255, 255, 0.6);
  }

  .welcome-hint {
    margin-top: 14px;
    padding: 8px 12px;
    background: rgba(255, 255, 255, 0.03);
    border-radius: 8px;
    border: 1px solid rgba(255, 255, 255, 0.06);
    font-size: 11px;
    color: rgba(255, 255, 255, 0.4);
    
    b {
      color: #3b82f6;
    }
  }
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

/* 消息气泡列表 */
.messages-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;

  .avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    flex-shrink: 0;
    font-weight: 550;
  }

  .ai-av {
    color: #fff;
  }

  .user-av {
    background: rgba(59, 130, 246, 0.15);
    color: #93c5fd;
    border: 1px solid rgba(59, 130, 246, 0.3);
  }

  &.user {
    justify-content: flex-end;
  }
}

.bubble {
  max-width: 78%;
  padding: 10px 14px;
  font-size: 13.5px;
  line-height: 1.6;
  word-break: break-word;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);

  &.user-bubble {
    background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-dark-2) 100%);
    color: #ffffff;
    border-radius: 16px 16px 4px 16px;
    border: 1px solid rgba(255, 255, 255, 0.08);
  }

  &.assistant-bubble {
    background: rgba(255, 255, 255, 0.04);
    color: #f1f5f9;
    border-radius: 16px 16px 16px 4px;
    border: 1px solid rgba(255, 255, 255, 0.06);
  }

  &.has-error {
    background: rgba(239, 68, 68, 0.1);
    border-color: rgba(239, 68, 68, 0.2);
  }
}

.error-text {
  color: #f87171;
  display: flex;
  align-items: center;
  gap: 6px;
}

.loading-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.loading-status-text {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.4;

  &.inline-status {
    margin-bottom: 8px;
  }
}

.search-sources-panel {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.search-sources-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 8px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 12.5px;
  font-weight: 700;
  color: #e2e8f0;
  background: rgba(99, 102, 241, 0.12);
}

.search-sources-action {
  flex-shrink: 0;
  font-size: 11px;
  color: #94a3b8;
}

.search-query {
  margin-top: 3px;
  font-size: 11.5px;
  color: #94a3b8;
}

.search-source-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 9px;
}

.search-source-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 7px 8px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  color: #f1f5f9;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.035);
}

.source-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  flex-shrink: 0;
  font-size: 10.5px;
  font-weight: 700;
  color: #fff;
  background: var(--el-color-primary);
}

.source-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.source-title {
  font-size: 12px;
  line-height: 1.35;
}

.source-url {
  font-size: 11px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* loading 三点动画 */
.loading-dots {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;

  span {
    width: 6px;
    height: 6px;
    background-color: rgba(255, 255, 255, 0.5);
    border-radius: 50%;
    display: inline-block;
    animation: bounce 1.4s infinite ease-in-out both;

    &:nth-child(1) { animation-delay: -0.32s; }
    &:nth-child(2) { animation-delay: -0.16s; }
  }
}

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1.0); }
}

/* 深度思维链 */
.thinking-block {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-left: 3px solid #dfb889;
  border-radius: 4px;
  margin-bottom: 10px;
  padding: 6px 10px;

  .thinking-header {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    font-size: 12px;
    color: #dfb889;
    user-select: none;

    .arrow {
      margin-left: auto;
      transition: transform 0.3s;

      &.is-active {
        transform: rotate(180deg);
      }
    }
  }

  .thinking-content {
    margin-top: 8px;
    font-size: 11.5px;
    color: rgba(255, 255, 255, 0.7);
    line-height: 1.5;
    border-top: 1px dashed rgba(255, 255, 255, 0.08);
    padding-top: 6px;
  }
}

/* 底部输入区 */
.panel-input {
  padding: 12px;
  background: transparent;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  transition: border-color 0.3s;

  &.is-focused {
    border-top-color: rgba(255, 255, 255, 0.12);
  }

  .input-container {
    display: flex;
    flex-direction: column;
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 10px;
    overflow: hidden;
    padding: 6px 8px 4px 8px;
    transition: all 0.3s;

    &:focus-within {
      border-color: #3b82f6;
      background: rgba(255, 255, 255, 0.05);
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
    }
  }

  :deep(.el-textarea) {
    .el-textarea__inner {
      border: none !important;
      background: transparent !important;
      padding: 0;
      font-size: 12.5px;
      color: #f8fafc;
      box-shadow: none !important;
      line-height: 1.6;

      &::placeholder {
        color: rgba(255, 255, 255, 0.35);
      }
    }
  }

  .input-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 6px;
    border-top: 1px solid rgba(255, 255, 255, 0.05);
    padding-top: 6px;

    .search-toggle-wrapper {
      display: flex;
      align-items: center;
      gap: 6px;

      .search-label {
        font-size: 11px;
        color: rgba(255, 255, 255, 0.45);
      }
    }

    .action-buttons {
      display: flex;
      gap: 6px;
      margin-left: auto;
    }
  }
}

/* 语音识别呼吸灯 */
@keyframes recordPulse {
  0% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.5);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(239, 68, 68, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0);
  }
}
.pulse-active {
  animation: recordPulse 1.2s infinite ease-in-out;
  background-color: #ef4444 !important;
}

/* 动画效果 slide-fade */
.slide-fade-enter-active {
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}
.slide-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.7, 0, 0.84, 0);
}
.slide-fade-enter {
  transform: scale(0.8) translateY(30px);
  opacity: 0;
}
.slide-fade-leave-to {
  transform: scale(0.8) translateY(30px);
  opacity: 0;
}

/* Markdown 样式 */
.markdown-body {
  font-size: 13px;
  line-height: 1.7;

  :deep(h4), :deep(h5), :deep(h6) {
    margin: 12px 0 7px 0;
    font-weight: 700;
    line-height: 1.35;
    color: #f8fafc;
    letter-spacing: 0;
  }

  :deep(h4) {
    padding-left: 8px;
    border-left: 3px solid var(--el-color-primary);
  }

  :deep(p) {
    margin: 0 0 8px 0;
    &:last-child {
      margin-bottom: 0;
    }
  }

  :deep(code.inline-code) {
    background: rgba(255, 255, 255, 0.08);
    color: #f43f5e;
    padding: 2px 4px;
    border-radius: 4px;
    font-family: monospace;
    font-size: 11.5px;
  }

  :deep(pre.code-block) {
    background: #0f172a;
    color: #f8fafc;
    padding: 10px;
    border-radius: 8px;
    overflow-x: auto;
    font-family: monospace;
    font-size: 11.5px;
    margin: 8px 0;
    border: 1px solid rgba(255, 255, 255, 0.05);
  }

  :deep(ul), :deep(ol) {
    margin: 6px 0 10px 0;
    padding-left: 20px;
  }

  :deep(li) {
    margin-bottom: 4px;
  }

  /* 表格 */
  :deep(table.ai-chat-table) {
    border-collapse: collapse;
    width: 100%;
    margin: 10px 0;
    font-size: 12px;

    th, td {
      border: 1px solid rgba(255, 255, 255, 0.1);
      padding: 6px 10px;
      text-align: left;
    }

    th {
      background-color: rgba(255, 255, 255, 0.06);
      font-weight: 600;
      color: #ffffff;
    }
    
    td {
      color: rgba(255, 255, 255, 0.85);
    }
  }
}
</style>
