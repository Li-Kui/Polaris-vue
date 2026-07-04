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
        <i v-else class="el-icon-close"></i>
      </div>
      <span :style="{ borderColor: themeColor }" class="ai-btn-pulse"></span>
    </div>

    <!-- 聊天面板 -->
    <transition name="slide-fade">
      <div v-show="chatVisible" class="ai-chat-panel">
        <!-- 头部 -->
        <div :style="{ borderTopColor: themeColor }" class="panel-header">
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
                :disabled="isStreaming || messages.length === 0"
                class="action-btn"
                icon="el-icon-refresh-left"
                type="text"
                @click="handleClearChat"
              ></el-button>
            </el-tooltip>
            <!-- 折叠关闭 -->
            <el-button class="action-btn" icon="el-icon-minus" type="text" @click="chatVisible = false"></el-button>
          </div>
        </div>

        <!-- 模型与知识库小型配置区 -->
        <div class="panel-configs">
          <el-select
            v-model="selectedModelName"
            :disabled="isStreaming"
            class="config-select"
            placeholder="选择模型"
            size="mini"
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
            :disabled="isStreaming"
            class="config-select"
            clearable
            placeholder="关联知识库"
            size="mini"
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

        <!-- 消息区 -->
        <div ref="msgArea" class="panel-messages">
          <div v-if="messages.length === 0" class="welcome-container">
            <div :style="{ color: themeColor }" class="welcome-icon">✦</div>
            <h3>你好！我是 AI 助理</h3>
            <p>我可以回答问题、编写代码或提供决策支持。</p>
            <p class="welcome-hint">输入下方框内即可开始，聊天记录在关闭或重置后不会保留。</p>
          </div>
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
                <div v-if="msg.loading" class="loading-dots">
                  <span></span><span></span><span></span>
                </div>
                <div v-else-if="msg.error" class="error-text">
                  <i class="el-icon-warning"></i> {{ msg.error }}
                </div>
                <div v-else>
                  <!-- 深度思维链 -->
                  <div v-if="msg.reasoningContent" class="thinking-block">
                    <div class="thinking-header" @click="toggleThinkingExpanded(index)">
                      <i class="el-icon-cpu"></i>
                      <span>{{ msg.content ? '已完成深度思考' : '深度思考推理中...' }}</span>
                      <i :class="['el-icon-arrow-down', 'arrow', { 'is-active': msg.expanded }]"></i>
                    </div>
                    <el-collapse-transition>
                      <div v-show="msg.expanded" class="thinking-content" v-html="renderMarkdown(msg.reasoningContent)"></div>
                    </el-collapse-transition>
                  </div>
                  <!-- 正文 -->
                  <div class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
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
              @keydown.native="handleKeyDown"
            />
            <div class="input-actions">
              <!-- 联网搜索开关 -->
              <div v-if="currentModelSupportsSearch" class="search-toggle-wrapper">
                <span class="search-label">联网搜索</span>
                <el-switch
                  v-model="enableWebSearch"
                  active-color="#1890ff"
                  size="mini"
                  @change="handleWebSearchChange"
                ></el-switch>
              </div>
              <div v-else></div>

              <div class="action-buttons">
                <!-- 停止生成 -->
                <el-button
                  v-if="isStreaming"
                  circle
                  icon="el-icon-video-pause"
                  size="mini"
                  type="danger"
                  @click="handleStopMessage"
                ></el-button>
                <!-- 发送按钮 -->
                <el-button
                  v-else
                  :disabled="!inputText.trim()"
                  :style="{ backgroundColor: themeColor, borderColor: themeColor }"
                  circle
                  icon="el-icon-position"
                  size="mini"
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

      // 联网搜索
      enableWebSearch: false,

      currentReader: null,
      sseEventBuffer: null
    }
  },
  computed: {
    themeColor() {
      return this.$store.state.settings.theme || '#1890ff'
    },
    themeColorGradient() {
      const mainColor = this.themeColor
      return `linear-gradient(135deg, ${mainColor}, ${this.lightenColor(mainColor, 20)})`
    },
    themeColorShadow() {
      return `0 8px 24px rgba(24, 144, 255, 0.25)`
    },
    currentModelSupportsSearch() {
      if (!this.selectedModelName) return false
      const m = this.models.find(item => item.modelName === this.selectedModelName)
      return m && m.enableSearch === '1'
    }
  },
  created() {
    this.loadModels()
    this.loadKnowledgeBases()
    const savedPreference = localStorage.getItem('ai_chat_enable_web_search')
    this.enableWebSearch = savedPreference === 'true'
  },
  beforeDestroy() {
    this.abortStream()
  },
  methods: {
    lightenColor(hex, percent) {
      hex = hex.replace(/^\s*#|\s*$/g, '')
      if (hex.length === 3) {
        hex = hex.replace(/(.)/g, '$1$1')
      }
      let r = parseInt(hex.substr(0, 2), 16)
      let g = parseInt(hex.substr(2, 2), 16)
      let b = parseInt(hex.substr(4, 2), 16)

      r = Math.min(255, Math.floor(r + (255 - r) * (percent / 100)))
      g = Math.min(255, Math.floor(g + (255 - g) * (percent / 100)))
      b = Math.min(255, Math.floor(b + (255 - b) * (percent / 100)))

      return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`
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
          const defModel = this.models.find(m => m.isDefault === '1')
          if (defModel) {
            this.selectedModelName = defModel.modelName
          } else if (this.models.length > 0) {
            this.selectedModelName = this.models[0].modelName
          }
        }
      } catch (e) {
        console.error('加载大模型列表失败', e)
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

      try {
        // 首条消息懒加载会话
        if (!this.currentConvId) {
          const convRes = await createConversation(this.selectedModelName, this.selectedKbId)
          if (convRes.code === 200) {
            this.currentConvId = convRes.data.id
          } else {
            throw new Error('创建会话失败')
          }
        }

        const baseUrl = process.env.VUE_APP_BASE_API || ''
        const enableSearchParam = this.enableWebSearch && this.currentModelSupportsSearch
        const url = `${baseUrl}/ai/chat/stream?conversationId=${this.currentConvId}&message=${encodeURIComponent(text)}&enableSearch=${enableSearchParam}`
        const token = getToken()

        const response = await fetch(url, {
          method: 'GET',
          headers: { Authorization: 'Bearer ' + token }
        })

        if (!response.ok) throw new Error(`HTTP ${response.status}`)

        const reader = response.body.getReader()
        this.currentReader = reader
        const decoder = new TextDecoder('utf-8')
        let buffer = ''
        this.sseEventBuffer = null

        this.$set(this.messages, aiIndex, {
          ...this.messages[aiIndex],
          loading: false
        })

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
                const cur = this.messages[aiIndex]
                const processedData = data ? data.replace(/__SSE_NEWLINE__/g, '\n') : ''
                this.$set(this.messages, aiIndex, {
                  ...cur,
                  content: cur.content + processedData
                })
                this.$nextTick(() => this.scrollToBottom())
              } else if (event === 'reasoning') {
                const cur = this.messages[aiIndex]
                const processedData = data ? data.replace(/__SSE_NEWLINE__/g, '\n') : ''
                this.$set(this.messages, aiIndex, {
                  ...cur,
                  reasoningContent: (cur.reasoningContent || '') + processedData
                })
                this.$nextTick(() => this.scrollToBottom())
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
        this.$set(this.messages, aiIndex, {
          role: 'assistant',
          content: '',
          loading: false,
          error: errMsg
        })
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
          this.$set(this.messages, this.messages.length - 1, {
            ...lastMsg,
            loading: false
          })
        }
      }
    },
    abortStream() {
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
      this.$message.success('已清空当前会话，您可以重新开始聊天。')
    },
    toggleThinkingExpanded(index) {
      const cur = this.messages[index]
      this.$set(this.messages, index, {
        ...cur,
        expanded: !cur.expanded
      })
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
      let html = this.escapeHtml(text)

      // 1. 代码块
      html = html.replace(
        /```[\w]*\n?([\s\S]*?)```/g,
        '<pre class="code-block"><code>$1</code></pre>'
      )

      // 2. 行内代码
      html = html.replace(/`([^`\n]+)`/g, '<code class="inline-code">$1</code>')

      // 3. 标题
      html = html.replace(/^#\s*(.*?)$/gm, '<h4>$1</h4>')
      html = html.replace(/^##\s*(.*?)$/gm, '<h4>$1</h4>')
      html = html.replace(/^###\s*(.*?)$/gm, '<h5>$1</h5>')
      html = html.replace(/^####\s*(.*?)$/gm, '<h6>$1</h6>')

      // 4. 粗体与斜体
      html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      html = html.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>')

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
      html = html.replace(/^[-*] (.+)$/gm, '<li>$1</li>')
      html = html.replace(/(<li>[\s\S]*?<\/li>)/g, m => `<ul>${m}</ul>`)
      html = html.replace(/<\/ul>\s*<ul>/g, '')

      html = html.replace(/^\d+\. (.+)$/gm, '<ol-li>$1</ol-li>')
      html = html.replace(/(<ol-li>[\s\S]*?<\/ol-li>)/g, m => `<ol>${m}</ol>`)
      html = html.replace(/<\/ol>\s*<ol>/g, '')
      html = html.replace(/ol-li/g, 'li')

      // 7. 引用
      html = html.replace(/^&gt;\s+(.+)$/gm, '<blockquote>$1</blockquote>')
      html = html.replace(/<\/blockquote>\s*<blockquote>/g, '<br>')

      // 8. 换行
      html = html.replace(/\n{3,}/g, '\n\n')
      html = html.replace(/\n/g, '<br>')
      html = html.replace(/<br>\s*(<\/?(table|tr|thead|tbody|th|td|ul|ol|li|h1|h2|h3|h4|h5|h6|blockquote|hr))/gi, '$1')
      html = html.replace(/(<\/(table|tr|thead|tbody|th|td|ul|ol|li|h1|h2|h3|h4|h5|h6|blockquote|hr)>)\s*<br>/gi, '$1')

      return html
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
  width: 50px;
  height: 50px;
  border-radius: 50%;
  cursor: pointer;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  color: #fff;
  z-index: 10;

  &:hover {
    transform: scale(1.08) translateY(-2px);

    .ai-btn-pulse {
      animation: pulse 1.6s infinite;
    }
  }

  &.is-active {
    transform: rotate(90deg);
  }
}

.ai-btn-inner {
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;

  .ai-svg-icon {
    width: 22px;
    height: 22px;
    display: block;
  }
}

.ai-btn-pulse {
  position: absolute;
  top: -4px;
  left: -4px;
  right: -4px;
  bottom: -4px;
  border: 2px solid;
  border-radius: 50%;
  opacity: 0;
  pointer-events: none;
  transition: all 0.3s;
}

@keyframes pulse {
  0% {
    transform: scale(0.95);
    opacity: 0.5;
  }
  100% {
    transform: scale(1.2);
    opacity: 0;
  }
}

/* 聊天面板 */
.ai-chat-panel {
  position: absolute;
  right: 0;
  bottom: 64px;
  width: 380px;
  height: 550px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(230, 235, 245, 0.8);
  border-radius: 16px;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.12);
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
  border-top: 4px solid;
  border-bottom: 1px solid #f0f2f5;
  background: #ffffff;

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
        color: #303133;
      }

      .subtitle {
        font-size: 10px;
        color: #909399;
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
      color: #606266;
      padding: 4px;

      &:hover {
        color: #1890ff;
        background: #f5f7fa;
        border-radius: 4px;
      }

      &:disabled {
        color: #c0c4cc;
        background: transparent;
      }
    }
  }
}

/* 配置选择区 */
.panel-configs {
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid #f0f2f5;
  display: flex;
  justify-content: space-between;

  ::v-deep .config-select {
    .el-input__inner {
      border: 1px solid #e4e7ed;
      border-radius: 6px;
      background-color: #fff;
      font-size: 12px;
      height: 28px;
      line-height: 28px;
      padding-left: 8px;

      &:focus {
        border-color: #409eff;
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
  background: #f8fafc;
  scroll-behavior: smooth;

  /* 隐藏滚动条 */
  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.08);
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
  color: #606266;
  padding: 0 20px;

  .welcome-icon {
    font-size: 48px;
    margin-bottom: 16px;
    animation: float 3s ease-in-out infinite;
  }

  h3 {
    margin: 0 0 10px 0;
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }

  p {
    margin: 0 0 6px 0;
    font-size: 12px;
    line-height: 1.6;
    color: #777;
  }

  .welcome-hint {
    margin-top: 14px;
    padding: 8px 12px;
    background: #fff;
    border-radius: 8px;
    border: 1px solid #ebeef5;
    font-size: 11px;
    color: #909399;
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
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
    background: #e1f5fe;
    color: #0288d1;
    border: 1px solid #b3e5fc;
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
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
  word-break: break-word;

  &.user-bubble {
    background: #e3f2fd;
    color: #1e3a8a;
    border-radius: 16px 16px 4px 16px;
    border: 1px solid rgba(187, 222, 251, 0.6);
  }

  &.assistant-bubble {
    background: #ffffff;
    color: #2c3e50;
    border-radius: 16px 16px 16px 4px;
    border: 1px solid #eaeaea;
  }

  &.has-error {
    background: #fef0f0;
    border-color: #fde2e2;
  }
}

.error-text {
  color: #f56c6c;
  display: flex;
  align-items: center;
  gap: 6px;
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
    background-color: #909399;
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
  background: #f4f6f8;
  border-left: 3px solid #909399;
  border-radius: 4px;
  margin-bottom: 10px;
  padding: 6px 10px;

  .thinking-header {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    font-size: 12px;
    color: #7f8c8d;
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
    color: #555;
    line-height: 1.5;
    border-top: 1px dashed #e2e8f0;
    padding-top: 6px;
  }
}

/* 底部输入区 */
.panel-input {
  padding: 12px;
  background: #ffffff;
  border-top: 1px solid #f0f2f5;
  transition: border-color 0.3s;

  &.is-focused {
    border-top-color: #cbd5e1;
  }

  .input-container {
    display: flex;
    flex-direction: column;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    overflow: hidden;
    padding: 6px 8px 4px 8px;
    transition: all 0.3s;

    &:focus-within {
      border-color: #409eff;
      background: #ffffff;
      box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
    }
  }

  ::v-deep .el-textarea {
    .el-textarea__inner {
      border: none !important;
      background: transparent !important;
      padding: 0;
      font-size: 12.5px;
      color: #303133;
      box-shadow: none !important;
      line-height: 1.6;

      &::placeholder {
        color: #c0c4cc;
      }
    }
  }

  .input-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 6px;
    border-top: 1px solid rgba(0, 0, 0, 0.02);
    padding-top: 6px;

    .search-toggle-wrapper {
      display: flex;
      align-items: center;
      gap: 6px;

      .search-label {
        font-size: 11px;
        color: #909399;
      }
    }

    .action-buttons {
      display: flex;
      gap: 6px;
      margin-left: auto;
    }
  }
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

/* Markdown 部分元素样式覆盖 */
.markdown-body {
  font-size: 13px;

  ::v-deep {
    p {
      margin: 0 0 8px 0;
      &:last-child {
        margin-bottom: 0;
      }
    }

    code.inline-code {
      background: #f1f5f9;
      color: #e11d48;
      padding: 2px 4px;
      border-radius: 4px;
      font-family: monospace;
      font-size: 11.5px;
    }

    pre.code-block {
      background: #0f172a;
      color: #f8fafc;
      padding: 10px;
      border-radius: 8px;
      overflow-x: auto;
      font-family: monospace;
      font-size: 11.5px;
      margin: 8px 0;
    }

    ul, ol {
      margin: 4px 0 8px 0;
      padding-left: 20px;
    }

    li {
      margin-bottom: 2px;
    }

    blockquote {
      border-left: 4px solid #cbd5e1;
      padding-left: 8px;
      color: #64748b;
      margin: 6px 0;
      font-style: italic;
    }

    h4, h5, h6 {
      margin: 10px 0 6px 0;
      font-weight: 600;
    }

    /* 表格 */
    table.ai-chat-table {
      border-collapse: collapse;
      width: 100%;
      margin: 10px 0;
      font-size: 12px;

      th, td {
        border: 1px solid #e2e8f0;
        padding: 6px 10px;
        text-align: left;
      }

      th {
        background-color: #f1f5f9;
        font-weight: 600;
      }
    }
  }
}
</style>
