<template>
  <div class="ai-chat-wrapper">

    <!-- ========== 左侧会话栏 ========== -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <el-button
          :disabled="isStreaming"
          :loading="creatingConv"
          class="btn-new-chat"
          @click="handleNewConversation"
        >
          <i v-if="!creatingConv" class="el-icon-edit"></i>
          新对话
        </el-button>
      </div>

      <div
        v-loading="loadingConvs"
        class="conv-list"
        element-loading-text="加载中..."
      >
        <div v-if="!loadingConvs && conversations.length === 0" class="conv-empty">
          暂无对话记录
        </div>

        <transition-group name="conv-fade" tag="div">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            :class="{ active: currentConvId === conv.id }"
            class="conv-item"
            @click="handleSelectConversation(conv.id)"
          >
            <i class="el-icon-chat-dot-round conv-icon"></i>
            <span class="conv-title">{{ conv.title }}</span>
            <div class="conv-actions" @click.stop>
              <el-tooltip :open-delay="300" content="重命名" placement="top">
                <el-button
                  class="conv-action-btn"
                  type="text"
                  @click="openRenameDialog(conv)"
                >
                  <i class="el-icon-edit"></i>
                </el-button>
              </el-tooltip>
              <el-tooltip :open-delay="300" content="删除" placement="top">
                <el-button
                  class="conv-action-btn danger"
                  type="text"
                  @click="handleDeleteConversation(conv.id)"
                >
                  <i class="el-icon-delete"></i>
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </transition-group>
      </div>
    </aside>

    <!-- ========== 主聊天区 ========== -->
    <main class="chat-main">
      <!-- 顶部知识库关联与模型指示栏 -->
      <div v-if="currentConvId" class="chat-header-bar" style="padding: 12px 20px; background: #ffffff; border-bottom: 1px solid #f2f5f8; display: flex; align-items: center; justify-content: space-between; flex-shrink: 0;">
        <span class="chat-header-indicator" style="font-size: 14px; font-weight: 600; color: #2d3748; display: flex; align-items: center; gap: 6px;">
          <i class="el-icon-chat-line-round"></i>
          AI 助手对话中
        </span>
        <div style="display: flex; gap: 8px; align-items: center;">
          <el-tag
            v-if="currentConvModel"
            class="model-indicator-tag"
            effect="plain"
            size="small"
            style="font-weight: 600; border: 1px solid #cbd5e0; color: #4a5568; background-color: #f7fafc;"
            type="info"
          >
            <i class="el-icon-cpu"></i> 模型: {{ currentConvModel }}
          </el-tag>
          <el-tag
            v-if="currentKbName"
            class="kb-indicator-tag"
            effect="dark"
            size="small"
            style="font-weight: 600; border: none; background: linear-gradient(135deg, #10b981 0%, #059669 100%);"
            type="success"
          >
            <i class="el-icon-collection"></i> 知识库: {{ currentKbName }}
          </el-tag>
        </div>
      </div>

      <!-- 欢迎页 -->
      <transition name="fade">
        <div v-if="!currentConvId" class="welcome-screen">
          <div class="welcome-glow"></div>
          <div class="welcome-avatar">✦</div>
          <h2 class="welcome-title">你好，我是 AI 助手</h2>
          <p class="welcome-subtitle">请配置或选择您要使用的模型及知识库，随时开启智能对话</p>

          <!-- 初始化配置启动卡片 -->
          <div class="welcome-setup-card" style="margin-top: 30px; width: 480px; padding: 25px; background: rgba(255, 255, 255, 0.9); border-radius: 16px; box-shadow: 0 10px 30px rgba(31, 38, 135, 0.06); border: 1px solid rgba(226, 232, 240, 0.8); display: flex; flex-direction: column; gap: 16px; text-align: left;">
            <div class="setup-item">
              <label style="font-size: 13px; font-weight: 600; color: #4a5568; margin-bottom: 6px; display: block;"><i class="el-icon-cpu" style="color: #3b82f6;"></i> 选择 AI 大模型</label>
              <el-select
                v-model="selectedModelName"
                placeholder="请选择要使用的大语言模型"
                size="medium"
                style="width: 100%;"
                @change="handleModelOrKbChange"
              >
                <el-option
                  v-for="item in models"
                  :key="item.id"
                  :label="item.name + ' (' + item.modelName + ')'"
                  :value="item.modelName"
                />
              </el-select>
            </div>

            <div class="setup-item">
              <label style="font-size: 13px; font-weight: 600; color: #4a5568; margin-bottom: 6px; display: block;"><i class="el-icon-collection" style="color: #10b981;"></i> 关联知识库 (可选)</label>
              <el-select
                v-model="selectedKbId"
                clearable
                placeholder="未关联知识库"
                size="medium"
                style="width: 100%;"
                @change="handleModelOrKbChange"
              >
                <el-option
                  v-for="item in knowledgeBases"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </div>

            <div class="setup-item">
              <label style="font-size: 13px; font-weight: 600; color: #4a5568; margin-bottom: 6px; display: block;"><i class="el-icon-s-operation" style="color: #8b5cf6;"></i> 选用智能体工作流 (可选)</label>
              <el-select
                v-model="selectedWorkflowCode"
                clearable
                placeholder="常规对话模式"
                size="medium"
                style="width: 100%;"
              >
                <el-option
                  v-for="item in workflows"
                  :key="item.workflowCode"
                  :label="item.workflowName"
                  :value="item.workflowCode"
                />
              </el-select>
            </div>

            <el-button
              icon="el-icon-chat-dot-round"
              style="margin-top: 10px; width: 100%; height: 42px; font-size: 14px; font-weight: 600; border-radius: 8px; border: none; background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%); box-shadow: 0 4px 12px rgba(37, 99, 235, 0.15);"
              type="primary"
              @click="handleNewConversation"
            >
              开启新对话
            </el-button>
          </div>
        </div>
      </transition>

      <!-- 消息列表 -->
      <div v-show="currentConvId" ref="messagesAreaRef" class="messages-area">
        <div v-if="loadingMessages" class="messages-loading">
          <i class="el-icon-loading"></i>
          <span>加载消息中...</span>
        </div>

        <template v-else>
          <transition-group class="messages-inner" name="msg-slide" tag="div">
            <div
              v-for="(msg, index) in messages"
              :key="index"
              :class="msg.role"
              class="msg-row"
            >
              <!-- AI 头像 -->
              <div v-if="msg.role === 'assistant'" class="avatar ai-av">✦</div>

              <!-- 气泡 -->
              <div
                :class="[msg.role + '-bubble', { 'has-error': msg.error }]"
                class="bubble"
              >
                <!-- AI：loading 三点 -->
                <div v-if="msg.loading" class="loading-container">
                  <div class="loading-dots">
                    <span></span><span></span><span></span>
                  </div>
                  <div v-if="msg.statusMsg" class="loading-status-text">
                    {{ msg.statusMsg }}
                  </div>
                </div>
                <!-- AI：错误状态 -->
                <div v-else-if="msg.error" class="error-msg">
                  <i class="el-icon-warning"></i>
                  {{ msg.error }}
                </div>
                <!-- AI：正常内容 -->
                <div v-else-if="msg.role === 'assistant'">
                  <div v-if="msg.statusMsg" class="loading-status-text" style="margin-bottom: 8px;">
                    <i class="el-icon-loading"></i> {{ msg.statusMsg }}
                  </div>
                  <!-- 智能体工作流执行步骤 -->
                  <div v-if="msg.workflowSteps && msg.workflowSteps.length > 0" class="workflow-steps-container">
                    <div class="workflow-header">
                      <i class="el-icon-s-operation"></i>
                      <span>智能体工作流执行链路</span>
                    </div>
                    <div class="workflow-steps-list">
                      <div 
                        v-for="(step, stepIdx) in msg.workflowSteps" 
                        :key="stepIdx"
                        :class="['workflow-step-item', step.status]"
                      >
                        <div class="step-icon">
                          <i v-if="step.status === 'running'" class="el-icon-loading"></i>
                          <i v-else-if="step.status === 'success'" class="el-icon-circle-check"></i>
                          <i v-else-if="step.status === 'error'" class="el-icon-circle-close"></i>
                          <i v-else class="el-icon-time"></i>
                        </div>
                        <div class="step-content">
                          <div class="step-title">
                            <span class="step-name">{{ step.name }}</span>
                            <span class="step-code">({{ step.code }})</span>
                          </div>
                          <!-- 正在调用的系统工具展示 -->
                          <div v-if="step.activeTool" class="step-tool-badge">
                            <i class="el-icon-folder-opened"></i> 正在调用系统工具: <span class="tool-name">{{ step.activeTool }}</span>
                          </div>
                          <!-- 节点输出的思考过程 -->
                          <div v-if="step.thinking" class="step-thinking-box">
                            <div class="thinking-title">思考过程：</div>
                            <div class="thinking-text">{{ step.thinking }}</div>
                          </div>
                          <!-- 节点输出的内容 -->
                          <div v-if="step.content && step.status !== 'success'" class="step-content-box">
                            <div class="content-text" v-html="renderMarkdown(step.content)"></div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <!-- 高端科技感思考过程展示 -->
                  <div v-if="msg.reasoningContent" class="thinking-container">
                    <div
                      class="thinking-header"
                      @click="$set(msg, 'thinkingExpanded', msg.thinkingExpanded === undefined ? false : !msg.thinkingExpanded)"
                    >
                      <div class="thinking-title-left">
                        <i class="el-icon-cpu thinking-icon"></i>
                        <span class="thinking-title-text">
                          {{ !msg.content ? '深度思考推理中...' : '深度思维链推演完毕' }}
                        </span>
                      </div>
                      <div class="thinking-title-right">
                        <span v-if="msg.streaming && !msg.content" class="thinking-status">
                          <i class="el-icon-loading"></i> 正在生成步骤
                        </span>
                        <i :class="['el-icon-arrow-down', 'collapse-arrow', { 'is-active': (msg.thinkingExpanded === undefined ? !msg.content : msg.thinkingExpanded) }]"></i>
                      </div>
                    </div>
                    <el-collapse-transition>
                      <div v-show="msg.thinkingExpanded === undefined ? !msg.content : msg.thinkingExpanded" class="thinking-content-wrapper">
                        <div class="thinking-left-line"></div>
                        <div class="markdown-body mini-markdown thinking-markdown" v-html="renderMarkdown(msg.reasoningContent)"></div>
                      </div>
                    </el-collapse-transition>
                  </div>
                  <div
                    :class="{ 'typing-cursor': msg.streaming }"
                    class="markdown-body"
                    v-html="renderMarkdown(msg.content)"
                  ></div>
                  <!-- 报告操作工具栏 -->
                  <div v-if="!msg.loading && !msg.error && isReportMessage(msg.content)" class="msg-tools">
                    <el-button
                      class="tool-btn report-btn"
                      icon="el-icon-document"
                      type="text"
                      @click="openReportView(msg.content)"
                    >
                      以精美报告形式查看
                    </el-button>
                  </div>
                </div>
                <!-- 用户消息包装（支持附件卡片展现） -->
                <div v-else class="user-bubble-wrapper">
                  <div v-if="msg.fileName" class="msg-attachment-card-wrapper">
                    <!-- 如果是图片附件 -->
                    <div v-if="isImageFile(msg.fileName)" class="msg-image-attachment">
                      <el-image
                        :preview-src-list="[msg.fileUrl ? (msg.fileUrl.startsWith('http') ? msg.fileUrl : (uploadUrl.replace('/common/upload', '') + msg.fileUrl)) : '']"
                        :src="msg.fileUrl ? (msg.fileUrl.startsWith('http') ? msg.fileUrl : (uploadUrl.replace('/common/upload', '') + msg.fileUrl)) : ''"
                        class="chat-inline-image"
                        fit="cover"
                      >
                        <div slot="placeholder" class="image-slot">
                          加载中<span class="dot">...</span>
                        </div>
                      </el-image>
                      <div class="image-name-badge">{{ msg.fileName }}</div>
                    </div>
                    <!-- 其他普通文档/PDF 附件 -->
                    <div v-else class="msg-attachment-card">
                      <i class="el-icon-document attachment-card-icon"></i>
                      <div class="attachment-card-info">
                        <span :title="msg.fileName" class="attachment-card-name">{{ msg.fileName }}</span>
                        <span class="attachment-card-desc">
                          {{ msg.fileName.toLowerCase().endsWith('.pdf') ? '已成功关联 PDF 多模态图文解析' : '已成功关联此对话解析' }}
                        </span>
                      </div>
                      <el-link
                        v-if="msg.fileUrl"
                        :href="msg.fileUrl.startsWith('http') ? msg.fileUrl : (uploadUrl.replace('/common/upload', '') + msg.fileUrl)"
                        :underlined="false"
                        class="attachment-card-download"
                        icon="el-icon-download"
                        target="_blank"
                        type="primary"
                      ></el-link>
                    </div>
                  </div>
                  <span class="user-text">{{ msg.content }}</span>
                </div>
              </div>

              <!-- 用户头像 -->
              <div v-if="msg.role === 'user'" class="avatar user-av">我</div>
            </div>
          </transition-group>
        </template>
      </div>

      <!-- 输入区域 -->
      <div v-show="currentConvId" class="input-area">
        <!-- 待发送附件预览栏 -->
        <div v-if="attachment" class="attachment-preview-bar">
          <div class="attachment-tag">
            <template v-if="isImageFile(attachment.name)">
              <el-image
                :src="attachment.url ? (attachment.url.startsWith('http') ? attachment.url : (uploadUrl.replace('/common/upload', '') + attachment.url)) : ''"
                class="preview-inline-image"
                fit="cover"
              />
            </template>
            <i v-else class="el-icon-document"></i>
            <span :title="attachment.name" class="file-name">{{ attachment.name }}</span>
            <i class="el-icon-close remove-btn" @click="handleRemoveAttachment"></i>
          </div>
        </div>

        <!-- 现代化极简输入框（上下分层设计，内置模型/知识库配置工具栏） -->
        <div :class="{ focused: inputFocused }" class="input-box-modern">
          <!-- 语音录制浮层 -->
          <transition name="fade">
            <div v-if="isListening" class="voice-listening-overlay">
              <div class="voice-wave-container">
                <span class="wave-bar bar-1"></span>
                <span class="wave-bar bar-2"></span>
                <span class="wave-bar bar-3"></span>
                <span class="wave-bar bar-4"></span>
                <span class="wave-bar bar-5"></span>
              </div>
              <div class="voice-listening-text">
                <span class="listening-pulse"></span>
                正在倾听中：{{ voiceTempText || '请说话...' }}
              </div>
              <div class="voice-listening-actions">
                <el-button circle icon="el-icon-close" size="mini" title="取消并清空本段语音" type="danger" @click="cancelVoiceInput"></el-button>
                <el-button circle icon="el-icon-check" size="mini" title="完成识别" type="success" @click="stopVoiceInput"></el-button>
              </div>
            </div>
          </transition>
          <!-- 上层：文本输入区域 -->
          <div class="input-modern-text-wrapper">
            <el-input
              ref="inputRef"
              v-model="inputText"
              :autosize="{ minRows: 2, maxRows: 6 }"
              :disabled="isStreaming"
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              resize="none"
              type="textarea"
              @blur="inputFocused = false"
              @focus="inputFocused = true"
              @keydown.native="handleKeyDown"
            />
          </div>

          <!-- 下层：操作工具栏 -->
          <div class="input-modern-tools-wrapper">
            <div class="tools-left">
              <!-- 附件上传按钮 -->
              <el-upload
                :action="uploadUrl"
                :before-upload="beforeAttachmentUpload"
                :disabled="isStreaming || uploadingAttachment"
                :headers="uploadHeaders"
                :on-error="handleAttachmentError"
                :on-success="handleAttachmentSuccess"
                :show-file-list="false"
                class="attachment-uploader-modern"
              >
                <el-button
                  :disabled="isStreaming || uploadingAttachment"
                  :loading="uploadingAttachment"
                  class="btn-attach-modern"
                  type="text"
                >
                  <i v-if="!uploadingAttachment" class="el-icon-paperclip"></i>
                </el-button>
              </el-upload>

              <!-- 麦克风语音输入按钮 -->
              <el-button
                :disabled="isStreaming || uploadingAttachment"
                class="btn-voice-modern"
                title="语音输入"
                type="text"
                @click="startVoiceInput"
              >
                <i class="el-icon-mic"></i>
              </el-button>

              <!-- 模型选择药丸 -->
              <el-popover
                v-model="showModelPopover"
                placement="top-start"
                title="选择 AI 核心大脑"
                width="240"
                trigger="click"
                popper-class="pill-selector-popper popper-model"
              >
                <div class="popper-selector-list">
                  <div
                    v-for="item in models"
                    :key="item.id"
                    :class="['popper-selector-item', { 'is-active': selectedModelName === item.modelName }]"
                    @click="selectedModelName = item.modelName; handleModelOrKbChange(); showModelPopover = false"
                  >
                    <i class="el-icon-cpu item-icon"></i>
                    <span class="item-name">{{ item.name }}</span>
                    <i v-if="selectedModelName === item.modelName" class="el-icon-check check-icon"></i>
                  </div>
                </div>
                <button slot="reference" :disabled="isStreaming" class="config-pill-btn pill-model">
                  <i class="el-icon-cpu"></i>
                  <span class="pill-label">{{ getSelectedModelLabel() }}</span>
                  <i class="el-icon-arrow-down pill-arrow"></i>
                </button>
              </el-popover>

              <!-- 知识库选择药丸 -->
              <el-popover
                v-model="showKbPopover"
                placement="top-start"
                title="关联专属知识库"
                width="240"
                trigger="click"
                popper-class="pill-selector-popper popper-kb"
              >
                <div class="popper-selector-list">
                  <div
                    :class="['popper-selector-item', { 'is-active': !selectedKbId }]"
                    @click="selectedKbId = null; handleModelOrKbChange(); showKbPopover = false"
                  >
                    <i class="el-icon-folder-delete item-icon"></i>
                    <span class="item-name">不挂载任何知识库</span>
                    <i v-if="!selectedKbId" class="el-icon-check check-icon"></i>
                  </div>
                  <div
                    v-for="item in knowledgeBases"
                    :key="item.id"
                    :class="['popper-selector-item', { 'is-active': selectedKbId === item.id }]"
                    @click="selectedKbId = item.id; handleModelOrKbChange(); showKbPopover = false"
                  >
                    <i class="el-icon-collection item-icon"></i>
                    <span class="item-name">{{ item.name }}</span>
                    <i v-if="selectedKbId === item.id" class="el-icon-check check-icon"></i>
                  </div>
                </div>
                <button slot="reference" :disabled="isStreaming" :class="['config-pill-btn pill-kb', { 'is-active': selectedKbId }]">
                  <i class="el-icon-collection"></i>
                  <span class="pill-label">{{ getSelectedKbLabel() }}</span>
                  <i class="el-icon-arrow-down pill-arrow"></i>
                </button>
              </el-popover>

              <!-- 工作流选择药丸 -->
              <el-popover
                v-model="showWorkflowPopover"
                placement="top-start"
                title="选用智能体工作流"
                width="240"
                trigger="click"
                popper-class="pill-selector-popper popper-workflow"
              >
                <div class="popper-selector-list">
                  <div
                    :class="['popper-selector-item', { 'is-active': !selectedWorkflowCode }]"
                    @click="selectedWorkflowCode = ''; showWorkflowPopover = false"
                  >
                    <i class="el-icon-chat-dot-round item-icon"></i>
                    <span class="item-name">直接常规提问</span>
                    <i v-if="!selectedWorkflowCode" class="el-icon-check check-icon"></i>
                  </div>
                  <div
                    v-for="item in workflows"
                    :key="item.workflowCode"
                    :class="['popper-selector-item', { 'is-active': selectedWorkflowCode === item.workflowCode }]"
                    @click="selectedWorkflowCode = item.workflowCode; showWorkflowPopover = false"
                  >
                    <i class="el-icon-connection item-icon"></i>
                    <span class="item-name">{{ item.workflowName }}</span>
                    <i v-if="selectedWorkflowCode === item.workflowCode" class="el-icon-check check-icon"></i>
                  </div>
                </div>
                <button slot="reference" :disabled="isStreaming" :class="['config-pill-btn pill-workflow', { 'is-active': selectedWorkflowCode }]">
                  <i class="el-icon-connection"></i>
                  <span class="pill-label">{{ getSelectedWorkflowLabel() }}</span>
                  <i class="el-icon-arrow-down pill-arrow"></i>
                </button>
              </el-popover>

              <!-- 联网搜索快速切换按钮 -->
              <button
                v-if="currentModelSupportsSearch"
                :class="['config-pill-btn pill-search', { 'is-active': enableWebSearch }]"
                :disabled="isStreaming"
                @click="toggleWebSearch"
              >
                <i class="el-icon-search"></i>
                <span class="pill-label">联网搜索</span>
              </button>
            </div>

            <div class="tools-right">
              <!-- 停止生成/终止等待按钮 -->
              <el-button
                v-if="canStop"
                class="btn-send-modern btn-stop-modern"
                type="danger"
                @click="handleStopMessage"
              >
                <i class="el-icon-video-pause" style="font-size: 14px;"></i>
              </el-button>
              <!-- 发送按钮 -->
              <el-button
                v-else
                :disabled="canStop || !inputText.trim() || uploadingAttachment"
                class="btn-send-modern"
                @click="handleSendMessage"
              >
                <svg fill="currentColor" height="15" viewBox="0 0 24 24" width="15">
                  <path d="M2.01 3L2 10l15 2-15 2 .01 7L23 12 2.01 3z"/>
                </svg>
              </el-button>
            </div>
          </div>
        </div>
        <p class="input-hint">AI 生成内容仅供参考，请注意甄别</p>
      </div>
    </main>

    <!-- ========== 精美报告预览弹窗 ========== -->
    <el-dialog
      :append-to-body="true"
      :fullscreen="true"
      :visible.sync="reportVisible"
      custom-class="pretty-report-dialog"
      title="AI 智能报告生成器"
      @close="handleReportClose"
    >
      <div class="report-toolbar">
        <el-button icon="el-icon-printer" size="small" type="primary" @click="handlePrintReport">打印报告 / 导出 PDF</el-button>
        <el-button icon="el-icon-download" size="small" type="success" @click="handleDownloadHtmlReport">导出静态 HTML</el-button>
        <el-button icon="el-icon-document-copy" size="small" @click="handleCopyHtmlReport">复制 HTML 源码</el-button>
        <el-button icon="el-icon-close" size="small" @click="reportVisible = false">关闭</el-button>
      </div>

      <div id="report-print-area" class="report-preview-page">
        <div class="report-paper">
          <!-- 页眉装点 -->
          <div class="paper-header">
            <span class="confidential-tag">内部绝密 / AI 智能分析</span>
            <span class="report-serial">编号：AI-REP-{{ currentReportId }}</span>
          </div>

          <div class="paper-title-area">
            <div class="paper-badge">
              <i class="el-icon-document-checked"></i> REPORT
            </div>
            <h1 class="paper-title">{{ reportTitle }}</h1>
            <div class="paper-meta">
              <span><strong>生成人：</strong>{{ currentUserName }}</span>
              <span><strong>生成时间：</strong>{{ formatReportTime() }}</span>
              <span><strong>会话来源：</strong>{{ conversationTitle }}</span>
            </div>
          </div>

          <div class="paper-divider">
            <span class="divider-circle"></span>
          </div>

          <!-- ECharts 可视化数据分析对比图表 -->
          <div v-if="hasChartData" class="report-chart-section">
            <div class="section-top-bar">
              <div class="section-title">
                <i class="el-icon-data-line chart-icon-accent"></i> 数据对比可视化直观分析
              </div>
              <div class="chart-options">
                <el-radio-group v-model="activeChartType" size="mini" @change="switchChartType">
                  <el-radio-button label="bar"><i class="el-icon-data-analysis"></i> 对比柱状图</el-radio-button>
                  <el-radio-button label="line"><i class="el-icon-share"></i> 趋势折线图</el-radio-button>
                </el-radio-group>
              </div>
            </div>
            <div id="pretty-report-chart" class="pretty-chart-box"></div>
          </div>

          <div class="paper-content markdown-body" v-html="renderMarkdown(reportContent)"></div>

          <div class="paper-footer">
            <p>※ 本报告由 RuoYi-AI 大模型内容引擎分析生成，其内容仅供参考，不构成任何最终决策 and 操作建议。 ※</p>
            <p class="footer-page-num">Page 1 of 1</p>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- ========== 重命名弹窗 ========== -->
    <el-dialog
      :close-on-click-modal="false"
      :visible.sync="renameDialogVisible"
      title="重命名会话"
      width="400px"
      @opened="focusRenameInput"
    >
      <el-input
        ref="renameInputRef"
        v-model="renameTitle"
        maxlength="50"
        placeholder="请输入新名称"
        show-word-limit
        @keyup.enter.native="submitRename"
      />
      <template slot="footer">
        <el-button @click="renameDialogVisible = false">取 消</el-button>
        <el-button
          :disabled="!renameTitle.trim()"
          type="primary"
          @click="submitRename"
        >确 认</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script>
import {getToken} from '@/utils/auth'
import {
  createConversation,
  deleteConversation,
  listConversations,
  listMessages,
  renameConversation,
  updateConversationConfig
} from '@/api/ai/chat'
import {listKnowledge} from '@/api/ai/knowledge'
import {listAvailableModel} from '@/api/ai/model'
import {listActiveWorkflows} from '@/api/ai/workflow'

export default {
  name: 'AiChat',
  data() {
    return {
      // 知识库选项与当前选择
      knowledgeBases: [],
      selectedKbId: null,

      // 可选大模型与当前选择
      models: [],
      selectedModelName: null,
      selectedWorkflowCode: '',
      workflows: [],
      showModelPopover: false,
      showKbPopover: false,
      showWorkflowPopover: false,

      conversations: [],
      loadingConvs: false,
      creatingConv: false,
      currentConvId: null,
      messages: [],
      loadingMessages: false,
      inputText: '',
      inputFocused: false,
      isStreaming: false,
      renameDialogVisible: false,
      renameTitle: '',
      renameTargetId: null,
      // 附件上传相关
      uploadUrl: process.env.VUE_APP_BASE_API + "/common/upload",
      uploadHeaders: { Authorization: "Bearer " + getToken() },
      attachment: null,
      uploadingAttachment: false,
      // 报告预览相关
      reportVisible: false,
      reportContent: '',
      reportTitle: '',
      currentReportId: '',
      hasChartData: false,
      activeChartType: 'bar',
      chartConfig: null,
      reportChartInstance: null,
      enableWebSearch: false,
      // 语音输入相关
      isListening: false,
      voiceTempText: '',
      voiceBaseText: ''
    }
  },
  computed: {
    currentUserName() {
      try {
        return this.$store.state.user.nickName || this.$store.state.user.name || '系统用户'
      } catch (e) {
        return '系统用户'
      }
    },
    conversationTitle() {
      const c = this.conversations.find(conv => conv.id === this.currentConvId)
      return c ? c.title : '默认会话'
    },
    currentKbName() {
      if (!this.currentConvId || this.conversations.length === 0) return ''
      const c = this.conversations.find(conv => conv.id === this.currentConvId)
      if (c && c.knowledgeBaseId) {
        const kb = this.knowledgeBases.find(k => k.id === c.knowledgeBaseId)
        return kb ? kb.name : ''
      }
      return ''
    },
    currentConvModel() {
      if (!this.currentConvId || this.conversations.length === 0) return ''
      const c = this.conversations.find(conv => conv.id === this.currentConvId)
      return c ? c.model : ''
    },
    currentModelSupportsSearch() {
      if (!this.selectedModelName) return false
      const m = this.models.find(item => item.modelName === this.selectedModelName)
      return m && m.enableSearch === '1'
    },
    canStop() {
      if (this.isStreaming) return true
      if (!this.messages || this.messages.length === 0) return false
      const lastMsg = this.messages[this.messages.length - 1]
      return lastMsg.role === 'assistant' && (lastMsg.loading || lastMsg.streaming || (lastMsg.statusMsg && !lastMsg.content))
    }
  },
  created() {
    this.currentReader = null
    this.sseEventBuffer = null
    this.loadKnowledgeBases()
    this.loadModels()
    this.loadWorkflows()
    // 读取联网搜索的偏好设置
    const savedPreference = localStorage.getItem('ai_chat_enable_web_search')
    this.enableWebSearch = savedPreference === 'true'
  },
  mounted() {
    this.loadConvList(true)
  },
  beforeDestroy() {
    this.abortStream()
    this.cleanupVoiceInput()
  },
  methods: {
    // ──────────────────────────────────────────
    // 会话管理
    // ──────────────────────────────────────────
    async loadConvList(autoSelect = false) {
      this.loadingConvs = true
      try {
        const res = await listConversations()
        if (res.code === 200) {
          this.conversations = res.data || []
          if (autoSelect && this.conversations.length > 0 && !this.currentConvId) {
            await this.selectConversation(this.conversations[0].id)
          }
        }
      } catch (error) {
        this.$message.error('加载会话列表失败')
      } finally {
        this.loadingConvs = false
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

    async loadModels() {
      try {
        const res = await listAvailableModel()
        if (res.code === 200) {
          this.models = (res.data.rows || res.data || []).filter(m => m.isDefaultEmbedding !== '1' && !m.modelName.toLowerCase().includes('embed'))
          if (this.models.length === 0) {
            const defaultModelName = process.env.VUE_APP_DEFAULT_MODEL || 'deepseek-chat'
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
        const defaultModelName = process.env.VUE_APP_DEFAULT_MODEL || 'deepseek-chat'
        this.models = [{
          id: 'default',
          name: '默认模型',
          modelName: defaultModelName
        }]
        this.selectedModelName = defaultModelName
      }
    },

    async loadWorkflows() {
      try {
        const res = await listActiveWorkflows()
        if (res.code === 200) {
          this.workflows = res.data || []
        }
      } catch (e) {
        console.error('加载工作流列表失败', e)
      }
    },

    async handleNewConversation() {
      if (this.isStreaming) return
      this.creatingConv = true
      try {
        const res = await createConversation(this.selectedModelName, this.selectedKbId)
        if (res.code === 200) {
          this.currentConvId = res.data.id
          this.messages = []
          await this.loadConvList()
          this.$nextTick(() => this.focusInput())
        } else {
          this.$message.error('新建失败：' + (res.msg || res.code))
        }
      } catch (error) {
        this.$message.error('新建失败，请重试')
      } finally {
        this.creatingConv = false
      }
    },

    async handleSelectConversation(id) {
      if (this.isStreaming || id === this.currentConvId) return
      await this.selectConversation(id)
    },

    async selectConversation(id) {
      this.currentConvId = id
      const c = this.conversations.find(conv => conv.id === id)
      if (c) {
        this.selectedModelName = c.model || null
        this.selectedKbId = c.knowledgeBaseId || null
      }
      await this.loadMessageList(id)
    },

    getSelectedModelLabel() {
      if (!this.selectedModelName) return '选择 AI 模型';
      const found = this.models.find(m => m.modelName === this.selectedModelName);
      return found ? found.name : this.selectedModelName;
    },
    getSelectedKbLabel() {
      if (!this.selectedKbId) return '关联知识库';
      const found = this.knowledgeBases.find(k => k.id === this.selectedKbId);
      return found ? found.name : '已关联知识库';
    },
    getSelectedWorkflowLabel() {
      if (!this.selectedWorkflowCode) return '常规对话';
      const found = this.workflows.find(w => w.workflowCode === this.selectedWorkflowCode);
      return found ? found.workflowName : '已选工作流';
    },

    async handleModelOrKbChange() {
      // 只有在当前选中了某会话时，才需要向后端同步已有会话的模型与知识库配置
      if (this.currentConvId) {
        try {
          const res = await updateConversationConfig(this.currentConvId, this.selectedModelName, this.selectedKbId)
          if (res.code === 200) {
            // 重新刷新会话列表以更新顶部状态条等数据的显示
            await this.loadConvList()
          } else {
            this.$message.error('切换失败：' + (res.msg || res.code))
          }
        } catch (e) {
          console.error(e)
          this.$message.error('会话配置切换失败，请重试')
        }
      }
    },

    handleWebSearchChange(val) {
      localStorage.setItem('ai_chat_enable_web_search', val ? 'true' : 'false')
    },

    toggleWebSearch() {
      if (this.isStreaming) return
      this.enableWebSearch = !this.enableWebSearch
      this.handleWebSearchChange(this.enableWebSearch)
    },

    async loadMessageList(convId) {
      this.loadingMessages = true
      this.messages = []
      try {
        const res = await listMessages(convId)
        if (res.code === 200) {
          this.messages = (res.data || []).map(m => ({
            role: m.role,
            content: m.content,
            loading: false,
            streaming: false,
            error: null
          }))
          this.$nextTick(() => {
            this.scrollToBottom()
            this.focusInput()
          })
        }
      } catch (error) {
        this.$message.error('加载消息失败')
      } finally {
        this.loadingMessages = false
      }
    },

    openRenameDialog(conv) {
      this.renameTargetId = conv.id
      this.renameTitle = conv.title
      this.renameDialogVisible = true
    },

    async submitRename() {
      const title = this.renameTitle.trim()
      if (!title) return
      try {
        const res = await renameConversation(this.renameTargetId, title)
        if (res.code === 200) {
          this.renameDialogVisible = false
          await this.loadConvList()
          this.$message.success('重命名成功')
        } else {
          this.$message.error('重命名失败：' + (res.msg || res.code))
        }
      } catch (error) {
        this.$message.error('重命名失败，请重试')
      }
    },

    async handleDeleteConversation(id) {
      try {
        await this.$confirm('确认删除该对话及所有消息？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch (error) {
        return  // 用户取消
      }
      try {
        const res = await deleteConversation(id)
        if (res.code === 200) {
          if (this.currentConvId === id) {
            this.currentConvId = null
            this.messages = []
          }
          await this.loadConvList()
          this.$message.success('删除成功')
        } else {
          this.$message.error('删除失败：' + (res.msg || res.code))
        }
      } catch (error) {
        this.$message.error('删除失败，请重试')
      }
    },

    // ──────────────────────────────────────────
    // 发送消息 —— Fetch SSE（携带 JWT）
    // ──────────────────────────────────────────
    handleKeyDown(e) {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        this.handleSendMessage()
      }
    },

    async handleSendMessage() {
      if (this.isStreaming || !this.currentConvId || this.uploadingAttachment) return
      const text = this.inputText.trim()
      if (!text) return

      const attachedFile = this.attachment
      this.inputText = ''
      this.attachment = null
      this.isStreaming = true

      // 追加用户气泡
      const displayContent = attachedFile ? `${text}\n\n📎 附件: ${attachedFile.name}` : text
      this.messages.push({ role: 'user', content: displayContent, loading: false, streaming: false, error: null })

      // 追加 AI loading 占位
      const aiIndex = this.messages.length
      this.messages.push({ role: 'assistant', content: '', reasoningContent: '', loading: true, streaming: false, error: null })
      this.$nextTick(() => this.scrollToBottom())

      const baseUrl = process.env.VUE_APP_BASE_API || ''
      const enableSearchParam = this.enableWebSearch && this.currentModelSupportsSearch
      
      const isWorkflowMode = !!this.selectedWorkflowCode
      let url = ''
      if (isWorkflowMode) {
        url = `${baseUrl}/ai/workflow/stream?workflowCode=${this.selectedWorkflowCode}&message=${encodeURIComponent(text)}`
      } else {
        url = `${baseUrl}/ai/chat/stream?conversationId=${this.currentConvId}&message=${encodeURIComponent(text)}&enableSearch=${enableSearchParam}`
        if (attachedFile) {
          url += `&fileUrl=${encodeURIComponent(attachedFile.url)}`
        }
      }
      const token = getToken()

      try {
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

        // loading → streaming
        this.$set(this.messages, aiIndex, {
          ...this.messages[aiIndex],
          loading: false,
          streaming: true
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

              if (isWorkflowMode) {
                // --- 智能体工作流模式专属解析 ---
                if (event === 'node_start') {
                  const cur = this.messages[aiIndex]
                  const steps = cur.workflowSteps || []
                  const parts = data.split('|')
                  const nodeCode = parts[0]
                  const nodeName = parts[1] || nodeCode
                  steps.push({
                    code: nodeCode,
                    name: nodeName,
                    status: 'running',
                    content: '',
                    thinking: ''
                  })
                  this.$set(this.messages[aiIndex], 'workflowSteps', steps)
                  this.$set(this.messages[aiIndex], 'statusMsg', `智能体「${nodeName}」正在处理...`)
                  this.$nextTick(() => this.scrollToBottom())
                } else if (event === 'node_tool') {
                  const cur = this.messages[aiIndex]
                  const steps = cur.workflowSteps || []
                  const parts = data.split('|')
                  const nodeCode = parts[0]
                  const toolName = parts[1] || ''
                  const step = steps.find(s => s.code === nodeCode)
                  if (step) {
                    this.$set(step, 'activeTool', toolName)
                  }
                  this.$set(this.messages[aiIndex], 'workflowSteps', steps)
                  this.$set(this.messages[aiIndex], 'statusMsg', `智能体「${step ? step.name : nodeCode}」正在调用工具: ${toolName}`)
                  this.$nextTick(() => this.scrollToBottom())
                } else if (event === 'node_thinking') {
                  const cur = this.messages[aiIndex]
                  const steps = cur.workflowSteps || []
                  const parts = data.split('|')
                  const nodeCode = parts[0]
                  const chunk = parts[1] ? parts[1].replace(/__SSE_NEWLINE__/g, '\n') : ''
                  const step = steps.find(s => s.code === nodeCode)
                  if (step) {
                    step.thinking = (step.thinking || '') + chunk
                  }
                  this.$set(this.messages[aiIndex], 'workflowSteps', steps)
                  this.$nextTick(() => this.scrollToBottom())
                } else if (event === 'node_chunk') {
                  const cur = this.messages[aiIndex]
                  const steps = cur.workflowSteps || []
                  const parts = data.split('|')
                  const nodeCode = parts[0]
                  const chunk = parts[1] ? parts[1].replace(/__SSE_NEWLINE__/g, '\n') : ''
                  const step = steps.find(s => s.code === nodeCode)
                  if (step) {
                    step.content = (step.content || '') + chunk
                  }
                  // 也追加到消息的总内容中供聊天界面常规显示
                  this.$set(this.messages[aiIndex], 'content', cur.content + chunk)
                  this.$set(this.messages[aiIndex], 'workflowSteps', steps)
                  this.$nextTick(() => this.scrollToBottom())
                } else if (event === 'node_done') {
                  const cur = this.messages[aiIndex]
                  const steps = cur.workflowSteps || []
                  const nodeCode = data.trim()
                  const step = steps.find(s => s.code === nodeCode)
                  if (step) {
                    step.status = 'success'
                  }
                  this.$set(this.messages[aiIndex], 'workflowSteps', steps)
                  this.$set(this.messages[aiIndex], 'statusMsg', '')
                  this.$nextTick(() => this.scrollToBottom())
                } else if (event === 'node_error') {
                  const cur = this.messages[aiIndex]
                  const steps = cur.workflowSteps || []
                  const parts = data.split('|')
                  const nodeCode = parts[0]
                  const errMsg = parts[1] || '执行异常'
                  const step = steps.find(s => s.code === nodeCode)
                  if (step) {
                    step.status = 'error'
                    step.content = (step.content || '') + `\n\n❌ 节点异常: ${errMsg}`
                  }
                  this.$set(this.messages[aiIndex], 'workflowSteps', steps)
                  this.$nextTick(() => this.scrollToBottom())
                } else if (event === 'workflow_done') {
                  this.$set(this.messages[aiIndex], 'streaming', false)
                  this.isStreaming = false
                  this.currentReader = null
                  this.loadConvList()
                  return
                } else if (event === 'error') {
                  throw new Error(data.trim() || '工作流执行失败')
                }
              } else {
                // --- 常规聊天问答模式 ---
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
                } else if (event === 'status') {
                  const cur = this.messages[aiIndex]
                  this.$set(this.messages, aiIndex, {
                    ...cur,
                    statusMsg: data || ''
                  })
                } else if (event === 'done') {
                  this.$set(this.messages, aiIndex, {
                    ...this.messages[aiIndex],
                    streaming: false
                  })
                  this.isStreaming = false
                  this.currentReader = null
                  this.loadConvList()
                  return
                } else if (event === 'error') {
                  throw new Error(data.trim() || 'AI 服务异常')
                }
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
          streaming: false,
          error: errMsg
        })
        this.$message.error('AI 响应失败：' + errMsg)
      } finally {
        this.isStreaming = false
        this.currentReader = null
        this.$nextTick(() => this.focusInput())
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
            streaming: false,
            loading: false,
            statusMsg: '',
            content: lastMsg.content || '（已停止生成/终止等待）'
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

    // ──────────────────────────────────────────
    // Markdown 轻量渲染
    // ──────────────────────────────────────────
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

      // 3. 标题 (Markdown #, ##, ###, ####)
      html = html.replace(/^#\s*(.*?)$/gm, '<h1>$1</h1>')
      html = html.replace(/^##\s*(.*?)$/gm, '<h2>$1</h2>')
      html = html.replace(/^###\s*(.*?)$/gm, '<h3>$1</h3>')
      html = html.replace(/^####\s*(.*?)$/gm, '<h4>$1</h4>')

      // 4. 粗体
      html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      // 5. 斜体
      html = html.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>')

      // 6. 简单的 Markdown 表格解析逻辑
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
            tableHtml += '<table class="report-table"><thead><tr>'
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

      // 7. 无序列表与有序列表
      html = html.replace(/^[-*] (.+)$/gm, '<li>$1</li>')
      html = html.replace(/(<li>[\s\S]*?<\/li>)/g, m => `<ul>${m}</ul>`)
      html = html.replace(/<\/ul>\s*<ul>/g, '')

      html = html.replace(/^\d+\. (.+)$/gm, '<ol-li>$1</ol-li>')
      html = html.replace(/(<ol-li>[\s\S]*?<\/ol-li>)/g, m => `<ol>${m}</ol>`)
      html = html.replace(/<\/ol>\s*<ol>/g, '')
      html = html.replace(/ol-li/g, 'li')

      // 8. 引用块
      html = html.replace(/^&gt;\s+(.+)$/gm, '<blockquote>$1</blockquote>')
      html = html.replace(/<\/blockquote>\s*<blockquote>/g, '<br>')

      // 9. 水平线与换行
      html = html.replace(/^---+$/gm, '<hr>')

      // 合并三个及以上的换行，压缩多余空行
      html = html.replace(/\n{3,}/g, '\n\n')
      html = html.replace(/\n/g, '<br>')

      // 清除表格、标题、列表等块级 HTML 标签前后的 br 换行，防止由于 br 堆积引起的排版空隙
      html = html.replace(/<br>\s*(<\/?(table|tr|thead|tbody|th|td|ul|ol|li|h1|h2|h3|h4|blockquote|hr))/gi, '$1')
      html = html.replace(/(<\/(table|tr|thead|tbody|th|td|ul|ol|li|h1|h2|h3|h4|blockquote|hr)>)\s*<br>/gi, '$1')

      return html
    },

    // ──────────────────────────────────────────
    // 报告相关处理方法
    // ──────────────────────────────────────────
    // 智能识别是否为报告类型消息
    isReportMessage(content) {
      if (!content || content.length < 300) return false

      const highWeightKeywords = ['分析报告', '竞品分析', '竞品对比', '对比报告', '分析总结', '报告摘要', '对比分析', '比价报告', '数据报告']
      for (const kw of highWeightKeywords) {
        if (content.includes(kw)) return true
      }

      const midWeightKeywords = ['竞品', '对比', '分析', '报告', '价格', '毛重', 'sku', '属性']
      let count = 0
      for (const kw of midWeightKeywords) {
        if (content.includes(kw)) {
          count++
        }
      }
      return count >= 3
    },

    // 解析 Markdown 数值对比表格
    parseTablesForCharts(content) {
      if (!content) return null

      const lines = content.split('\n')
      const tables = []
      let currentTable = null

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        if (line.startsWith('|') && line.endsWith('|')) {
          const cells = line.split('|').map(c => c.trim()).filter((c, idx, arr) => idx > 0 && idx < arr.length - 1)
          if (cells.length > 0) {
            if (!currentTable) {
              currentTable = { headers: cells, rows: [], isSeparatorNext: false }
            } else {
              const isSeparator = cells.every(cell => /^:?-+:?$/.test(cell) || cell === '')
              if (isSeparator) {
                currentTable.isSeparatorNext = true
              } else {
                currentTable.rows.push(cells)
              }
            }
          }
        } else {
          if (currentTable) {
            if (currentTable.rows.length > 0) {
              tables.push(currentTable)
            }
            currentTable = null
          }
        }
      }
      if (currentTable && currentTable.rows.length > 0) {
        tables.push(currentTable)
      }

      if (tables.length === 0) return null

      const validTables = tables.filter(t => {
        return t.rows.some(row => {
          return row.slice(1).some(cell => {
            const num = parseFloat(cell.replace(/[^\d.-]/g, ''))
            return !isNaN(num)
          })
        })
      })

      if (validTables.length === 0) return null
      const target = validTables[0]

      const xAxisData = target.rows.map(row => row[0])
      const seriesNames = target.headers.slice(1)

      const seriesDataList = seriesNames.map((name, sIdx) => {
        const data = target.rows.map(row => {
          const cellVal = row[sIdx + 1] || '0'
          const num = parseFloat(cellVal.replace(/[^\d.-]/g, ''))
          return isNaN(num) ? 0 : num
        })
        return {
          name: name,
          type: 'bar',
          data: data,
          barMaxWidth: 30,
          itemStyle: {
            borderRadius: [4, 4, 0, 0]
          }
        }
      })

      return {
        xAxisData,
        series: seriesDataList,
        legendData: seriesNames
      }
    },

    openReportView(content) {
      this.reportContent = content

      // 尝试自动提取报告大标题
      const titleMatch = content.match(/^#+\s+(.+)$/m)
      if (titleMatch && titleMatch[1]) {
        this.reportTitle = titleMatch[1].trim()
      } else {
        const currentConv = this.conversations.find(c => c.id === this.currentConvId)
        this.reportTitle = currentConv ? currentConv.title : '智能数据分析报告'
      }

      this.currentReportId = Math.random().toString(36).substring(2, 10).toUpperCase()
      this.reportVisible = true

      // 解析表格数据并初始化图表
      const parsed = this.parseTablesForCharts(content)
      if (parsed) {
        this.hasChartData = true
        this.chartConfig = parsed
        this.activeChartType = 'bar'
        this.$nextTick(() => {
          this.initReportChart()
        })
      } else {
        this.hasChartData = false
        this.chartConfig = null
      }
    },

    initReportChart() {
      if (!this.chartConfig) return
      this.$nextTick(() => {
        const chartDom = document.getElementById('pretty-report-chart')
        if (!chartDom) return

        if (this.reportChartInstance) {
          this.reportChartInstance.dispose()
        }

        const echarts = require('echarts')
        this.reportChartInstance = echarts.init(chartDom)

        const option = {
          tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'shadow' }
          },
          legend: {
            data: this.chartConfig.legendData,
            bottom: 0,
            icon: 'roundRect'
          },
          grid: {
            left: '3%',
            right: '4%',
            bottom: '12%',
            top: '8%',
            containLabel: true
          },
          xAxis: {
            type: 'category',
            data: this.chartConfig.xAxisData,
            axisLabel: { interval: 0, rotate: 15, color: '#6b7280' },
            axisLine: { lineStyle: { color: '#e5e7eb' } }
          },
          yAxis: {
            type: 'value',
            axisLabel: { color: '#6b7280' },
            splitLine: { lineStyle: { type: 'dashed', color: '#f3f4f6' } }
          },
          color: ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'],
          series: this.chartConfig.series.map(s => ({
            ...s,
            type: this.activeChartType
          }))
        }

        this.reportChartInstance.setOption(option)
        window.addEventListener('resize', this.resizeReportChart)
      })
    },

    switchChartType() {
      this.initReportChart()
    },

    resizeReportChart() {
      if (this.reportChartInstance) {
        this.reportChartInstance.resize()
      }
    },

    handleReportClose() {
      window.removeEventListener('resize', this.resizeReportChart)
      if (this.reportChartInstance) {
        this.reportChartInstance.dispose()
        this.reportChartInstance = null
      }
      this.reportVisible = false
    },

    formatReportTime() {
      const d = new Date()
      return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') + ' ' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
    },

    handlePrintReport() {
      this.$nextTick(() => {
        window.print()
      })
    },

    handleCopyHtmlReport() {
      const reportElement = document.querySelector('#report-print-area .report-paper')
      if (!reportElement) {
        this.$message.warning('报告渲染失败，无法复制')
        return
      }

      const styleTag = '<style>\n' +
        '  .report-paper { padding: 40px; font-family: sans-serif; color: #1f2937; line-height: 1.8; max-width: 800px; margin: 0 auto; background: #fff; }\n' +
        '  h1 { font-size: 2rem; color: #1e3a8a; border-bottom: 2px solid #e5e7eb; padding-bottom: 12px; }\n' +
        '  h2 { font-size: 1.5rem; color: #1e40af; margin-top: 30px; }\n' +
        '  table { width:100%; border-collapse:collapse; margin:20px 0; }\n' +
        '  th, td { padding: 12px; border: 1px solid #e5e7eb; text-align: left; }\n' +
        '  th { background: #f9fafb; font-weight: bold; }\n' +
        '  blockquote { padding: 10px 20px; background: #f3f4f6; border-left: 4px solid #3b82f6; color: #4b5563; }\n' +
        '</style>'

      const fullHtml = '<html><head><meta charset="utf-8">' + styleTag + '</head><body>' + reportElement.innerHTML + '</body></html>'

      const textarea = document.createElement('textarea')
      textarea.value = fullHtml
      document.body.appendChild(textarea)
      textarea.select()
      try {
        document.execCommand('copy')
        this.$message.success('已将精美 HTML 报告源码复制到剪贴板！')
      } catch (err) {
        this.$message.error('复制失败，请重试')
      }
      document.body.removeChild(textarea)
    },

    handleDownloadHtmlReport() {
      const reportElement = document.querySelector('#report-print-area .report-paper')
      if (!reportElement) {
        this.$message.warning('报告渲染失败')
        return
      }

      let prettyHtml = '<!DOCTYPE html>\n' +
        '<html lang="zh-CN">\n' +
        '<head>\n' +
        '  <meta charset="UTF-8">\n' +
        '  <meta name="viewport" content="width=device-width, initial-scale=1.0">\n' +
        '  <title>' + (this.reportTitle || 'AI智能报告') + '</title>\n' +
        '  <style>\n' +
        '    body {\n' +
        '      background: #f0f2f5;\n' +
        '      margin: 0;\n' +
        '      padding: 40px 20px;\n' +
        '      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;\n' +
        '      color: #1f2937;\n' +
        '    }\n' +
        '    .report-paper {\n' +
        '      max-width: 850px;\n' +
        '      margin: 0 auto;\n' +
        '      background: #ffffff;\n' +
        '      padding: 50px;\n' +
        '      border-radius: 12px;\n' +
        '      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);\n' +
        '      border: 1px solid rgba(0, 0, 0, 0.05);\n' +
        '    }\n' +
        '    .paper-header {\n' +
        '      display: flex;\n' +
        '      justify-content: space-between;\n' +
        '      font-size: 0.8rem;\n' +
        '      color: #9ca3af;\n' +
        '      border-bottom: 1px solid #f3f4f6;\n' +
        '      padding-bottom: 10px;\n' +
        '      margin-bottom: 30px;\n' +
        '    }\n' +
        '    .paper-title-area {\n' +
        '      margin-bottom: 30px;\n' +
        '    }\n' +
        '    .paper-badge {\n' +
        '      display: inline-block;\n' +
        '      padding: 4px 12px;\n' +
        '      font-size: 0.75rem;\n' +
        '      font-weight: bold;\n' +
        '      color: #fff;\n' +
        '      background: #2563eb;\n' +
        '      border-radius: 4px;\n' +
        '      margin-bottom: 12px;\n' +
        '    }\n' +
        '    .paper-title {\n' +
        '      font-size: 2.2rem;\n' +
        '      font-weight: 800;\n' +
        '      color: #111827;\n' +
        '      margin: 0 0 15px 0;\n' +
        '    }\n' +
        '    .paper-meta {\n' +
        '      display: flex;\n' +
        '      flex-wrap: wrap;\n' +
        '      gap: 20px;\n' +
        '      font-size: 0.85rem;\n' +
        '      color: #6b7280;\n' +
        '      background: #f9fafb;\n' +
        '      padding: 12px 16px;\n' +
        '      border-radius: 6px;\n' +
        '    }\n' +
        '    .paper-divider {\n' +
        '      height: 2px;\n' +
        '      background: #e5e7eb;\n' +
        '      margin: 30px 0;\n' +
        '      position: relative;\n' +
        '    }\n' +
        '    .divider-circle {\n' +
        '      position: absolute;\n' +
        '      left: 50%;\n' +
        '      top: 50%;\n' +
        '      transform: translate(-50%, -50%);\n' +
        '      width: 10px;\n' +
        '      height: 10px;\n' +
        '      background: #2563eb;\n' +
        '      border-radius: 50%;\n' +
        '      border: 4px solid #fff;\n' +
        '    }\n' +
        '    .paper-content {\n' +
        '      line-height: 1.8;\n' +
        '      font-size: 1.05rem;\n' +
        '    }\n' +
        '    .paper-content h1, .paper-content h2, .paper-content h3 {\n' +
        '      color: #1e3a8a;\n' +
        '      margin-top: 30px;\n' +
        '      margin-bottom: 15px;\n' +
        '    }\n' +
        '    .paper-content h2 {\n' +
        '      border-bottom: 2px solid #eff6ff;\n' +
        '      padding-bottom: 8px;\n' +
        '    }\n' +
        '    .paper-content p {\n' +
        '      margin: 0 0 16px 0;\n' +
        '    }\n' +
        '    .paper-content table {\n' +
        '      width: 100%;\n' +
        '      border-collapse: collapse;\n' +
        '      margin: 24px 0;\n' +
        '      border-radius: 6px;\n' +
        '      overflow: hidden;\n' +
        '      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);\n' +
        '    }\n' +
        '    .paper-content th {\n' +
        '      background: #f8fafc;\n' +
        '      color: #475569;\n' +
        '      font-weight: 600;\n' +
        '      padding: 12px 16px;\n' +
        '      border-bottom: 2px solid #e2e8f0;\n' +
        '      text-align: left;\n' +
        '    }\n' +
        '    .paper-content td {\n' +
        '      padding: 12px 16px;\n' +
        '      border-bottom: 1px solid #f1f5f9;\n' +
        '    }\n' +
        '    .paper-content tr:hover {\n' +
        '      background: #f8fafc;\n' +
        '    }\n' +
        '    .paper-content blockquote {\n' +
        '      margin: 20px 0;\n' +
        '      padding: 16px 20px;\n' +
        '      background: #eff6ff;\n' +
        '      border-left: 4px solid #3b82f6;\n' +
        '      border-radius: 0 6px 6px 0;\n' +
        '      color: #1e40af;\n' +
        '    }\n' +
        '    .paper-footer {\n' +
        '      margin-top: 50px;\n' +
        '      border-top: 1px solid #f3f4f6;\n' +
        '      padding-top: 20px;\n' +
        '      text-align: center;\n' +
        '      font-size: 0.8rem;\n' +
        '      color: #9ca3af;\n' +
        '    }\n' +
        '    .footer-page-num {\n' +
        '      margin-top: 8px;\n' +
        '      font-weight: bold;\n' +
        '    }\n' +
        '    @media print {\n' +
        '      body { background: #fff; padding: 0; }\n' +
        '      .report-paper { box-shadow: none; border: none; padding: 0; }\n' +
        '    }\n' +
        '  </style>\n' +
        '</head>\n' +
        '<body>\n' +
        '  <div class="report-paper">\n' +
        reportElement.innerHTML +
        '  </div>\n' +
        '</body>\n' +
        '</html>'

      const blob = new Blob([prettyHtml], { type: 'text/html;charset=utf-8' })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = (this.reportTitle || 'AI智能报告') + '.html'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      this.$message.success('精美 HTML 报告下载成功！')
    },

    // ──────────────────────────────────────────
    // 工具
    // ──────────────────────────────────────────
    scrollToBottom() {
      const el = this.$refs.messagesAreaRef
      if (el) el.scrollTop = el.scrollHeight
    },

    focusInput() {
      if (this.$refs.inputRef && this.$refs.inputRef.$el) {
        const textarea = this.$refs.inputRef.$el.querySelector('textarea')
        if (textarea) {
          textarea.focus()
        }
      }
    },

    focusRenameInput() {
      if (this.$refs.renameInputRef) {
        this.$refs.renameInputRef.focus()
      }
    },

    // ──────────────────────────────────────────
    // 附件上传逻辑
    // ──────────────────────────────────────────
    beforeAttachmentUpload(file) {
      if (this.isStreaming) {
        this.$message.warning('正在对话中，暂不支持上传附件')
        return false
      }
      if (this.attachment) {
        this.$message.warning('请先删除已有附件，再上传新附件')
        return false
      }

      // 校验文件名中不能有逗号
      if (file.name.includes(',')) {
        this.$message.error('文件名不正确，不能包含英文逗号!')
        return false
      }

      // 限制 10MB
      const isLt10M = file.size / 1024 / 1024 < 10
      if (!isLt10M) {
        this.$message.error('附件大小不能超过 10MB!')
        return false
      }

      // 校验文件格式
      const allowedExts = ['pdf', 'docx', 'xlsx', 'xls', 'txt', 'md', 'json', 'xml', 'csv', 'html', 'java', 'py', 'js', 'ts', 'png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp']
      const nameParts = file.name.split('.')
      const ext = nameParts[nameParts.length - 1].toLowerCase()
      if (!allowedExts.includes(ext)) {
        this.$message.error('暂不支持该类型的文件解析，目前仅支持：' + allowedExts.join(', '))
        return false
      }

      this.uploadingAttachment = true
      return true
    },

    isImageFile(fileName) {
      if (!fileName) return false
      const ext = fileName.split('.').pop().toLowerCase()
      return ['png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp'].includes(ext)
    },

    handleAttachmentSuccess(res, file) {
      this.uploadingAttachment = false
      if (res.code === 200) {
        this.attachment = {
          name: file.name,
          url: res.fileName
        }
        this.$message.success('文件上传成功')
      } else {
        this.$message.error(res.msg || '文件上传失败')
      }
    },

    handleAttachmentError(err) {
      this.uploadingAttachment = false
      this.$message.error('文件上传接口调用失败')
    },

    handleRemoveAttachment() {
      this.attachment = null
    },

    // ──────────────────────────────────────────
    // 语音输入（Web Speech API）逻辑
    // ──────────────────────────────────────────
    startVoiceInput() {
      if (this.isStreaming) return
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition || window.mozSpeechRecognition || window.msSpeechRecognition
      if (!SpeechRecognition) {
        this.$message.warning('您的浏览器暂不支持原生语音识别，建议使用 Chrome、Edge 或 Safari 浏览器。')
        return
      }

      this.voiceBaseText = this.inputText // 暂存当前的输入框内容
      this.voiceTempText = ''
      this.isListening = true

      try {
        const recognition = new SpeechRecognition()
        recognition.continuous = true
        recognition.interimResults = true
        recognition.lang = 'zh-CN'

        recognition.onstart = () => {
          console.log('Speech recognition started')
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
          // 实时将已识别的内容拼接到输入框中
          this.inputText = this.voiceBaseText + newlyRecognized
        }

        recognition.onerror = (event) => {
          console.error('Speech recognition error', event.error)
          if (event.code === 'not-allowed' || event.error === 'not-allowed') {
            this.$message.error('麦克风权限被拒绝，请在浏览器设置中开启麦克风权限！')
          } else if (event.error === 'network') {
            this.$message.error('语音识别网络连接超时，请检查网络（Chrome 语音识别可能需要连接谷歌服务器）')
          } else if (event.error === 'no-speech') {
            // 没检测到说话，不作为错误抛出，保持状态或提示
          } else {
            this.$message.error('语音识别异常：' + event.error)
          }
          this.cleanupVoiceInput()
        }

        recognition.onend = () => {
          console.log('Speech recognition ended')
          this.cleanupVoiceInput()
        }

        this.$options.recognitionInstance = recognition
        recognition.start()
      } catch (err) {
        console.error('Failed to start speech recognition', err)
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
      this.inputText = this.voiceBaseText // 恢复之前的文本
      this.isListening = false
    },

    cleanupVoiceInput() {
      this.isListening = false
      if (this.$options.recognitionInstance) {
        this.$options.recognitionInstance.onstart = null
        this.$options.recognitionInstance.onresult = null
        this.$options.recognitionInstance.onerror = null
        this.$options.recognitionInstance.onend = null
        this.$options.recognitionInstance = null
      }
    }
  }
}
</script>

<style scoped>
/* ===== 整体布局 ===== */
.ai-chat-wrapper {
  display: flex;
  /* 适配 RuoYi-Vue3 的 layout：顶部 navbar 50px + tabs-nav 34px + 内边距 */
  height: calc(100vh - 84px);
  background: #f5f7fa;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB',
               'Microsoft YaHei', sans-serif;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 226px;
  min-width: 226px;
  background: #ffffff;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  padding: 14px 12px 10px;
  border-bottom: 1px solid #f2f4f7;
}

.btn-new-chat {
  width: 100% !important;
  background: #f0f7ff !important;
  border: 1.5px dashed #409eff !important;
  color: #409eff !important;
  border-radius: 9px !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  gap: 6px !important;
  height: 38px !important;
  transition: all 0.2s !important;
}
.btn-new-chat:hover:not(:disabled) {
  background: #d9ecff !important;
  border-color: #66b1ff !important;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 8px 8px;
  min-height: 0;
}

.conv-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 28px 0;
}

/* 会话条目 */
.conv-item {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  transition: background 0.15s;
  user-select: none;
  gap: 7px;
  margin-bottom: 2px;
  position: relative;
}
.conv-item:hover { background: #f5f7fa; }
.conv-item.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 500;
}

.conv-icon {
  font-size: 13px;
  flex-shrink: 0;
  opacity: 0.6;
}

.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.conv-actions {
  display: none;
  align-items: center;
  gap: 0;
  flex-shrink: 0;
}
.conv-item:hover .conv-actions,
.conv-item.active .conv-actions {
  display: flex;
}

.conv-action-btn {
  padding: 3px 4px !important;
  height: auto !important;
  color: #909399 !important;
  font-size: 13px !important;
}
.conv-action-btn:hover { color: #409eff !important; }
.conv-action-btn.danger:hover { color: #f56c6c !important; }

/* 侧栏列表过渡 */
.conv-fade-enter-active,
.conv-fade-leave-active { transition: all 0.2s; }
.conv-fade-enter-from  { opacity: 0; transform: translateX(-8px); }
.conv-fade-leave-to    { opacity: 0; transform: translateX(-8px); }

/* 高端 AI 思考块容器 */
.thinking-container {
  background: #fcfbf9;
  border: 1px solid #eadecc;
  border-radius: 12px;
  margin-bottom: 16px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(181, 137, 91, 0.04);
  transition: all 0.3s ease;
}

.thinking-container:hover {
  box-shadow: 0 4px 16px rgba(181, 137, 91, 0.08);
}

/* 思考框头部 */
.thinking-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, #fbfaf7 0%, #f7f4ed 100%);
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid rgba(234, 222, 204, 0.5);
  transition: background 0.3s ease;
}

.thinking-header:hover {
  background: linear-gradient(135deg, #f7f4ed 0%, #efeae0 100%);
}

.thinking-title-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 科技感大脑图标 */
.thinking-icon {
  font-size: 16px;
  color: #b5895b;
  text-shadow: 0 0 8px rgba(181, 137, 91, 0.3);
  animation: pulse 2s infinite ease-in-out;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.15); opacity: 1; }
}

.thinking-title-text {
  font-size: 13px;
  font-weight: 600;
  color: #8c6a46;
  letter-spacing: 0.5px;
}

.thinking-title-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.thinking-status {
  font-size: 12px;
  color: #b5895b;
}

/* 旋转箭头动画 */
.collapse-arrow {
  font-size: 13px;
  color: #b5895b;
  transition: transform 0.3s cubic-bezier(0.2, 0, 0, 1);
}

.collapse-arrow.is-active {
  transform: rotate(-180deg);
}

/* 思考内容区包装 */
.thinking-content-wrapper {
  display: flex;
  padding: 14px 16px;
  background-color: #faf9f6;
  position: relative;
}

/* 左侧设计感推演竖线 */
.thinking-left-line {
  width: 2px;
  background: linear-gradient(to bottom, #eadecc 0%, rgba(234, 222, 204, 0.1) 100%);
  margin-right: 14px;
  flex-shrink: 0;
  border-radius: 1px;
}

/* 思考 Markdown 样式细化 */
.thinking-markdown {
  flex-grow: 1;
  font-size: 12px !important;
  line-height: 1.625 !important;
  color: #6e5a47 !important;
}

/* 让 Markdown 中的列表和引用在思考区更加清秀 */
.thinking-markdown p {
  margin: 0 0 10px 0 !important;
}
.thinking-markdown p:last-child {
  margin-bottom: 0 !important;
}

/* ===== 主聊天区 ===== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  background: #f5f7fa;
  position: relative;
}

/* 欢迎页 */
.welcome-screen {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  z-index: 1;
}

.welcome-glow {
  position: absolute;
  width: 400px;
  height: 400px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.06) 0%, transparent 70%);
  pointer-events: none;
}

.welcome-avatar {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6c3be4 0%, #409eff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  color: #fff;
  box-shadow: 0 8px 32px rgba(64, 158, 255, 0.25);
  margin-bottom: 6px;
}

.welcome-title {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0;
  letter-spacing: -0.3px;
}

.welcome-subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.welcome-tips {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

.tip-card {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.tip-card:hover {
  border-color: #409eff;
  color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.12);
  transform: translateY(-1px);
}

/* 欢迎页过渡 */
.fade-enter-active, .fade-leave-active { transition: opacity 0.25s; }
.fade-enter-from, .fade-leave-to       { opacity: 0; }

/* ===== 消息列表 ===== */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 0 10px;
  min-height: 0;
}

.messages-inner {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.messages-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #909399;
  font-size: 13px;
  padding: 48px 0;
}

/* 消息行过渡 */
.msg-slide-enter-active { transition: all 0.22s ease; }
.msg-slide-enter-from   { opacity: 0; transform: translateY(10px); }

/* 消息行 */
.msg-row {
  display: flex;
  align-items: flex-start;
  padding: 12px 44px;
  gap: 16px;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}
.msg-row.user {
  justify-content: flex-end;
}

/* 头像 */
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.05);
  transition: transform 0.2s ease;
}
.avatar:hover {
  transform: scale(1.05);
}
.user-av {
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  color: #fff;
  font-size: 11px;
}
.ai-av {
  background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%);
  color: #fff;
  font-size: 16px;
  box-shadow: 0 0 12px rgba(139, 92, 246, 0.3);
}

/* 气泡 */
.bubble {
  max-width: 75%;
  padding: 12px 18px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  box-sizing: border-box;
}
.user-bubble {
  background: linear-gradient(135deg, #6366f1 0%, #3b82f6 100%);
  color: #ffffff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.15);
  border: 1px solid rgba(99, 102, 241, 0.05);
}
.assistant-bubble {
  background: #ffffff;
  color: #1e293b;
  border-bottom-left-radius: 4px;
  border: 1px solid #f1f5f9;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02);
}

.user-bubble-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.msg-attachment-card {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 8px;
  gap: 10px;
  max-width: 280px;
  width: 100%;
  box-sizing: border-box;
  margin-bottom: 2px;
  text-align: left;
}

.attachment-card-icon {
  font-size: 22px;
  color: #ffffff;
  opacity: 0.95;
}

.attachment-card-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.attachment-card-name {
  font-size: 12px;
  font-weight: 600;
  color: #ffffff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-card-desc {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 1px;
}

.attachment-card-download {
  color: #ffffff !important;
  opacity: 0.85;
  font-size: 14px;
  transition: opacity 0.2s;
}

.attachment-card-download:hover {
  opacity: 1;
}
.assistant-bubble {
  max-width: 85% !important;
  background: #fff;
  color: #303133;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.06);
}
.has-error {
  background: #fff5f5;
  border: 1px solid #fde2e2;
}

/* 打字光标 */
.typing-cursor::after {
  content: '▋';
  font-size: 12px;
  animation: blink 0.8s step-start infinite;
  margin-left: 2px;
  color: #409eff;
  vertical-align: baseline;
}
@keyframes blink { 50% { opacity: 0; } }

/* 三点加载 */
.loading-dots {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 2px 0;
}
.loading-dots span {
  display: inline-block;
  width: 7px;
  height: 7px;
  background: #c0c4cc;
  border-radius: 50%;
  animation: dotBounce 1.2s infinite ease-in-out;
}
.loading-dots span:nth-child(2) { animation-delay: 0.18s; }
.loading-dots span:nth-child(3) { animation-delay: 0.36s; }
@keyframes dotBounce {
  0%, 60%, 100% { transform: translateY(0);    }
  30%           { transform: translateY(-7px); }
}

/* 错误提示 */
.error-msg {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #f56c6c;
  font-size: 13px;
}

/* Markdown 样式 */
.markdown-body ::v-deep pre.code-block {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 14px 18px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 13px;
  margin: 8px 0;
  font-family: 'Fira Code', 'JetBrains Mono', 'Courier New', monospace;
  line-height: 1.65;
}
.markdown-body ::v-deep code.inline-code {
  background: #f0f2f5;
  color: #e83e8c;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Fira Code', 'Courier New', monospace;
}
.markdown-body ::v-deep ul {
  margin: 6px 0;
  padding-left: 20px;
}
.markdown-body ::v-deep li { margin: 3px 0; }
.markdown-body ::v-deep strong { font-weight: 600; color: #1a1a2e; }
.markdown-body ::v-deep em { font-style: italic; color: #606266; }
.markdown-body ::v-deep hr {
  border: none;
  border-top: 1px solid #ebeef5;
  margin: 10px 0;
}

/* ===== 输入框底部配置气泡药丸 (Config Pills) ===== */
.config-pill-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 12px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  outline: none;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
  margin-right: 6px;
}
.config-pill-btn:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
  color: #334155;
  transform: translateY(-1px);
}
.config-pill-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

/* 药丸小 icon 与文字 */
.config-pill-btn i {
  font-size: 13px;
  color: #94a3b8;
  transition: color 0.2s ease;
}
.config-pill-btn .pill-label {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.config-pill-btn .pill-arrow {
  font-size: 11px;
  opacity: 0.7;
}

/* 模型大脑药丸 (淡雅蓝紫主题) */
.pill-model {
  background: #f5f3ff;
  border-color: #ddd6fe;
  color: #7c3aed;
}
.pill-model i {
  color: #8b5cf6;
}
.pill-model:hover {
  background: #ede9fe;
  border-color: #c084fc;
}

/* 知识库药丸 (淡雅草绿主题) */
.pill-kb.is-active {
  background: #ecfdf5;
  border-color: #a7f3d0;
  color: #059669;
}
.pill-kb.is-active i {
  color: #10b981;
}
.pill-kb.is-active:hover {
  background: #d1fae5;
  border-color: #6ee7b7;
}

/* 工作流药丸 (淡雅琥珀主题) */
.pill-workflow.is-active {
  background: #fffbeb;
  border-color: #fde68a;
  color: #d97706;
}
.pill-workflow.is-active i {
  color: #f59e0b;
}
.pill-workflow.is-active:hover {
  background: #fef3c7;
  border-color: #fcd34d;
}

/* 联网搜索药丸 (淡雅海洋蓝主题) */
.pill-search.is-active {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #2563eb;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.08);
}
.pill-search.is-active i {
  color: #3b82f6;
}
.pill-search.is-active:hover {
  background: #dbeafe;
  border-color: #93c5fd;
}

/* 弹出层全局统一定制 */
.pill-selector-popper {
  border-radius: 16px !important;
  background: rgba(255, 255, 255, 0.98) !important;
  backdrop-filter: blur(12px) !important;
  box-shadow: 0 20px 40px -15px rgba(0, 0, 0, 0.08), 0 15px 25px -10px rgba(0, 0, 0, 0.04) !important;
  border: 1px solid rgba(226, 232, 240, 0.8) !important;
  padding: 10px !important;
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1) !important;
}

.pill-selector-popper .el-popover__title {
  font-size: 11px;
  font-weight: 800;
  color: #94a3b8;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
  padding: 4px 8px;
  border-bottom: 1px solid #f8fafc;
}

.popper-selector-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
  max-height: 240px;
  overflow-y: auto;
}

.popper-selector-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  background: transparent;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  user-select: none;
  transform: translateX(0);
  box-sizing: border-box;
}

/* 柔和侧滑 Hover 动效，让选择充满质感 */
.popper-selector-item:hover {
  background: #f8fafc;
  transform: translateX(4px);
}

.popper-selector-item:active {
  transform: scale(0.97);
}

.popper-selector-item .item-icon {
  font-size: 14px;
  color: #94a3b8;
  flex-shrink: 0;
  transition: color 0.2s ease;
}

.popper-selector-item .item-name {
  font-size: 13px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.popper-selector-item .check-icon {
  font-size: 13px;
  font-weight: bold;
  flex-shrink: 0;
}

/* === 核心大脑激活态 (淡蓝紫主题) === */
.popper-model .popper-selector-item.is-active {
  background: #f5f3ff;
  color: #7c3aed;
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(124, 58, 237, 0.08);
}
.popper-model .popper-selector-item.is-active .item-icon {
  color: #8b5cf6;
}
.popper-model .popper-selector-item.is-active .check-icon {
  color: #7c3aed;
}

/* === 专属知识库激活态 (淡雅绿主题) === */
.popper-kb .popper-selector-item.is-active {
  background: #ecfdf5;
  color: #059669;
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(5, 150, 105, 0.08);
}
.popper-kb .popper-selector-item.is-active .item-icon {
  color: #10b981;
}
.popper-kb .popper-selector-item.is-active .check-icon {
  color: #059669;
}

/* === 智能体工作流激活态 (琥珀橙主题) === */
.popper-workflow .popper-selector-item.is-active {
  background: #fffbeb;
  color: #d97706;
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(217, 119, 6, 0.08);
}
.popper-workflow .popper-selector-item.is-active .item-icon {
  color: #f59e0b;
}
.popper-workflow .popper-selector-item.is-active .check-icon {
  color: #d97706;
}

/* ===== 输入区域 ===== */
.input-area {
  padding: 10px 44px 14px;
  background: #f5f7fa;
  flex-shrink: 0;
}

/* ===== 现代化极简输入框（上下分层设计） ===== */
.input-box-modern {
  position: relative; /* 配合语音输入绝对定位浮层 */
  max-width: 812px;
  margin: 0 auto;
  background: #ffffff;
  border: 1.5px solid #dcdfe6;
  border-radius: 16px;
  padding: 12px 14px 10px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03);
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
}

.input-box-modern.focused {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.08);
}

.input-modern-text-wrapper {
  width: 100%;
}

/* 覆盖 textarea 默认样式，实现零边框透明背景 */
.input-modern-text-wrapper ::v-deep .el-textarea__inner {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 !important;
  font-size: 14px !important;
  line-height: 1.65 !important;
  color: #303133 !important;
  font-family: inherit !important;
  resize: none !important;
}

.input-modern-text-wrapper ::v-deep .el-textarea__inner::placeholder {
  color: #c0c4cc;
}

/* 下层工具栏 */
.input-modern-tools-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #f1f3f7;
  padding-top: 10px;
}

.tools-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tools-right {
  display: flex;
  align-items: center;
}

/* 附件上传按钮 */
.btn-attach-modern {
  font-size: 16px !important;
  color: #606266 !important;
  padding: 0 !important;
  margin: 0 4px 0 0 !important;
  transition: color 0.2s !important;
}
.btn-attach-modern:hover {
  color: #409eff !important;
}

/* 语音输入按钮 */
.btn-voice-modern {
  font-size: 16px !important;
  color: #606266 !important;
  padding: 0 !important;
  margin: 0 4px 0 0 !important;
  transition: color 0.2s !important;
}
.btn-voice-modern:hover {
  color: #409eff !important;
}

/* ===== 语音识别浮层 ===== */
.voice-listening-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(4px);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-radius: 12px;
  border: 1px solid rgba(64, 158, 255, 0.2);
}

.voice-wave-container {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  width: 60px;
}

.wave-bar {
  display: inline-block;
  width: 3px;
  height: 8px;
  background-color: #409eff;
  border-radius: 2px;
  animation: wave-pulse 1.2s ease-in-out infinite;
}

.wave-bar.bar-1 { animation-delay: 0.1s; }
.wave-bar.bar-2 { animation-delay: 0.3s; height: 16px; }
.wave-bar.bar-3 { animation-delay: 0.6s; height: 24px; }
.wave-bar.bar-4 { animation-delay: 0.2s; height: 14px; }
.wave-bar.bar-5 { animation-delay: 0.4s; height: 8px; }

@keyframes wave-pulse {
  0%, 100% {
    transform: scaleY(1);
  }
  50% {
    transform: scaleY(2.2);
  }
}

.voice-listening-text {
  flex: 1;
  margin: 0 15px;
  font-size: 13px;
  color: #606266;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 8px;
}

.listening-pulse {
  width: 8px;
  height: 8px;
  background-color: #e6a23c;
  border-radius: 50%;
  animation: pulse-dot 1.5s infinite;
}

@keyframes pulse-dot {
  0% {
    transform: scale(0.9);
    opacity: 0.6;
  }
  50% {
    transform: scale(1.3);
    opacity: 1;
  }
  100% {
    transform: scale(0.9);
    opacity: 0.6;
  }
}

.voice-listening-actions {
  display: flex;
  gap: 10px;
}

/* 内置极简透明下拉框 */
.tool-select-modern ::v-deep .el-input__inner {
  border: 1px solid #e2e8f0 !important;
  background-color: #f8fafc !important;
  padding: 0 10px !important;
  height: 26px !important;
  line-height: 26px !important;
  font-size: 12px !important;
  font-weight: 500 !important;
  color: #4a5568 !important;
  border-radius: 12px !important;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tool-select-modern ::v-deep .el-input__inner:hover {
  border-color: #cbd5e0 !important;
  background-color: #f1f5f9 !important;
}

.tool-select-modern ::v-deep .el-input__icon {
  line-height: 26px !important;
  font-size: 11px !important;
}

/* 发送按钮 */
.btn-send-modern {
  width: 32px !important;
  height: 32px !important;
  min-width: 32px !important;
  padding: 0 !important;
  border-radius: 50% !important;
  background: #409eff !important;
  border-color: #409eff !important;
  color: #fff !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  transition: all 0.2s !important;
}

.btn-send-modern:hover:not(:disabled) {
  background: #66b1ff !important;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.25) !important;
  transform: translateY(-1px);
}

.btn-send-modern:active:not(:disabled) {
  transform: scale(0.95) !important;
}

.btn-send-modern:disabled {
  background: #f1f3f7 !important;
  border-color: #e2e8f0 !important;
  color: #cbd5e0 !important;
  cursor: not-allowed !important;
}

.btn-stop-modern {
  background: #ff4d4f !important;
  border-color: #ff4d4f !important;
}

.btn-stop-modern:hover:not(:disabled) {
  background: #ff7875 !important;
  box-shadow: 0 2px 8px rgba(255, 77, 79, 0.25) !important;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.loading-status-text {
  font-size: 13px;
  color: #909399;
  animation: statusPulse 1.5s infinite ease-in-out;
}

@keyframes statusPulse {
  0% { opacity: 0.6; }
  50% { opacity: 1; }
  100% { opacity: 0.6; }
}

.input-hint {
  max-width: 812px;
  margin: 5px auto 0;
  font-size: 11px;
  color: #c0c4cc;
  text-align: center;
}

/* ===== 多模态图片预览与展示样式 ===== */
.msg-attachment-card-wrapper {
  margin-bottom: 10px;
}

.msg-image-attachment {
  position: relative;
  display: inline-block;
  max-width: 260px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  background-color: #f5f7fa;
}

.msg-image-attachment:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}

.chat-inline-image {
  display: block;
  width: 100%;
  max-height: 200px;
  cursor: zoom-in;
}

.image-name-badge {
  padding: 4px 8px;
  font-size: 11px;
  color: #606266;
  background: #f0f2f5;
  border-top: 1px solid #e4e7ed;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: center;
}

.preview-inline-image {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  object-fit: cover;
}

.image-slot {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 120px;
  background: #f5f7fa;
  color: #909399;
  font-size: 12px;
}

/* ===== 待发送附件预览栏 ===== */
.attachment-preview-bar {
  max-width: 812px;
  margin: 0 auto 6px;
  display: flex;
  justify-content: flex-start;
  align-items: center;
}

.attachment-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #eef5fe;
  border: 1px solid #d9ecff;
  border-radius: 8px;
  color: #409eff;
  font-size: 13px;
  max-width: 300px;
}

.attachment-tag .file-name {
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  flex: 1;
}

.attachment-tag .remove-btn {
  cursor: pointer;
  color: #a0cfff;
  transition: color 0.2s;
}

.attachment-tag .remove-btn:hover {
  color: #f56c6c;
}

/* ===== 附件上传按钮 ===== */
.attachment-uploader {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 2px;
}

.btn-attach {
  font-size: 20px !important;
  color: #909399 !important;
  padding: 0 !important;
  height: 36px;
  width: 36px;
  display: flex !important;
  align-items: center;
  justify-content: center;
  transition: color 0.2s !important;
}

.btn-attach:hover:not(:disabled) {
  color: #409eff !important;
}

.btn-attach:disabled {
  color: #c0c4cc !important;
  cursor: not-allowed !important;
}


/* ===== 滚动条 ===== */
.conv-list::-webkit-scrollbar,
.messages-area::-webkit-scrollbar {
  width: 4px;
}
.conv-list::-webkit-scrollbar-track,
.messages-area::-webkit-scrollbar-track {
  background: transparent;
}
.conv-list::-webkit-scrollbar-thumb,
.messages-area::-webkit-scrollbar-thumb {
  background: #e4e7ed;
  border-radius: 4px;
}

/* ===== 报告查看器 & 气泡工具栏样式 ===== */
.msg-tools {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px dashed #ebeef5;
  padding-top: 6px;
}

.report-btn {
  color: #409eff !important;
  font-weight: 500;
  font-size: 13px !important;
  padding: 4px 8px !important;
  border-radius: 4px;
  background: #ecf5ff;
}

.report-btn:hover {
  background: #d9ecff;
  color: #66b1ff !important;
}

/* 全屏报告 Dialog */
.pretty-report-dialog {
  background: #f3f4f6 !important;
}

.pretty-report-dialog .el-dialog__header {
  background: #ffffff;
  border-bottom: 1px solid #e4e7ed;
  padding: 16px 20px;
}

.pretty-report-dialog .el-dialog__title {
  font-weight: 600;
  color: #303133;
}

.pretty-report-dialog .el-dialog__body {
  padding: 0 !important;
  height: calc(100vh - 55px);
  display: flex;
  flex-direction: column;
}

/* 工具栏 */
.report-toolbar {
  background: #ffffff;
  padding: 12px 24px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  gap: 12px;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(0,0,0,0.02);
  z-index: 10;
}

/* 预览区页面 */
.report-preview-page {
  flex: 1;
  overflow-y: auto;
  padding: 40px 20px;
  background: #f3f4f6;
  display: flex;
  justify-content: center;
}

/* 仿纸张设计 */
.report-paper {
  background: #ffffff;
  width: 100%;
  max-width: 820px;
  min-height: 1000px;
  padding: 60px 50px;
  box-sizing: border-box;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(0, 0, 0, 0.03);
  position: relative;
  display: flex;
  flex-direction: column;
}

/* 页眉装点 */
.paper-header {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #909399;
  border-bottom: 1px solid #f2f6fc;
  padding-bottom: 8px;
  margin-bottom: 40px;
}

.confidential-tag {
  letter-spacing: 0.1em;
  font-weight: 600;
  color: #e6a23c;
}

.report-serial {
  font-family: monospace;
}

/* 标题区 */
.paper-title-area {
  margin-bottom: 30px;
}

.paper-badge {
  display: inline-block;
  padding: 4px 10px;
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  color: #ffffff;
  font-size: 11px;
  font-weight: bold;
  border-radius: 4px;
  margin-bottom: 16px;
  letter-spacing: 0.05em;
  box-shadow: 0 4px 10px rgba(24, 144, 255, 0.2);
}

.paper-title {
  font-size: 28px;
  font-weight: 800;
  color: #1f2d3d;
  margin: 0 0 16px 0;
  line-height: 1.4;
  letter-spacing: -0.01em;
}

.paper-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  font-size: 13px;
  color: #606266;
  background: #f8fafc;
  padding: 12px 20px;
  border-radius: 6px;
  border-left: 3px solid #1890ff;
}

.paper-meta span strong {
  color: #303133;
}

/* 漂亮的分割线 */
.paper-divider {
  height: 1px;
  background: #e4e7ed;
  margin: 40px 0;
  position: relative;
}

.divider-circle {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 8px;
  background: #1890ff;
  border-radius: 50%;
  border: 4px solid #ffffff;
  box-shadow: 0 0 0 1px #e4e7ed;
}

/* 主内容正文 */
.paper-content {
  flex: 1;
  font-size: 15px;
  color: #2c3e50;
  line-height: 1.8;
}

.paper-content ::v-deep h1,
.paper-content ::v-deep h2,
.paper-content ::v-deep h3,
.paper-content ::v-deep h4 {
  color: #1a1a2e;
  margin-top: 32px;
  margin-bottom: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.paper-content ::v-deep h1 {
  font-size: 22px;
  border-bottom: 2px solid #f0f2f5;
  padding-bottom: 10px;
}

.paper-content ::v-deep h2 {
  font-size: 18px;
  border-bottom: 2px solid #eff6ff;
  padding-bottom: 6px;
  position: relative;
}

.paper-content ::v-deep h2::after {
  content: "";
  position: absolute;
  left: 0;
  bottom: -2px;
  width: 40px;
  height: 2px;
  background: #1890ff;
}

.paper-content ::v-deep h3 {
  font-size: 16px;
}

.paper-content ::v-deep p {
  margin: 0 0 16px;
}

/* 表格定制 */
.paper-content ::v-deep table {
  width: 100% !important;
  border-collapse: separate !important;
  border-spacing: 0 !important;
  margin: 28px 0 !important;
  border-radius: 12px !important;
  overflow: hidden !important;
  border: 1px solid #e2e8f0 !important;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02) !important;
}

.paper-content ::v-deep th {
  background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%) !important;
  color: #ffffff !important;
  font-weight: 600 !important;
  padding: 14px 18px !important;
  border: none !important;
  text-align: left !important;
  font-size: 14px !important;
}

.paper-content ::v-deep td {
  padding: 13px 18px !important;
  border-bottom: 1px solid #f1f5f9 !important;
  border-right: 1px solid #f1f5f9 !important;
  font-size: 14px !important;
  color: #334155 !important;
}

.paper-content ::v-deep tr:last-child td {
  border-bottom: none !important;
}

.paper-content ::v-deep tr td:last-child {
  border-right: none !important;
}

.paper-content ::v-deep tr:nth-child(even) {
  background-color: #f8fafc !important;
}

.paper-content ::v-deep tr:hover {
  background: #f1f5f9 !important;
}

/* 引用块 */
.paper-content ::v-deep blockquote {
  margin: 20px 0;
  padding: 16px 20px;
  background: #eef5fe;
  border-left: 4px solid #1890ff;
  border-radius: 0 8px 8px 0;
  color: #1e3a8a;
}

/* 页脚声明 */
.paper-footer {
  margin-top: 60px;
  border-top: 1px solid #f2f6fc;
  padding-top: 20px;
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
}

.footer-page-num {
  margin-top: 6px;
  font-weight: bold;
}

/* ECharts 可视化数据分析对比图表 */
.report-chart-section {
  margin: 32px 0;
  padding: 24px;
  background: #fcfcfd;
  border: 1px solid #eef2f6;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.015);
}

.section-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 12px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 8px;
}

.chart-icon-accent {
  color: #3b82f6;
  font-size: 17px;
  text-shadow: 0 0 8px rgba(59, 130, 246, 0.2);
}

.pretty-chart-box {
  width: 100%;
  height: 380px;
  background: transparent;
  transition: all 0.3s ease;
}

/* ──────────────────────────────────────────
   打印专属媒体查询
   ────────────────────────────────────────── */
@media print {
  /* 隐藏非打印区域 */
  body * {
    visibility: hidden;
  }
  /* 只让打印区可见，且其内部的所有元素可见 */
  #report-print-area, #report-print-area * {
    visibility: visible;
  }
  /* 打印区域定位于页面最上角 */
  #report-print-area {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    padding: 0;
    margin: 0;
  }
  .report-paper {
    box-shadow: none;
    border: none;
    padding: 0;
    width: 100%;
  }
  .report-toolbar, .el-dialog__header, .el-dialog__close {
    display: none !important;
  }
}

/* 联网搜索按钮切换器样式 */
.web-search-btn-modern {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 20px;
  cursor: pointer;
  outline: none;
  background-color: #f3f4f6;
  border: 1px dashed #cbd5e0;
  color: #718096;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}

.web-search-btn-modern:hover:not(:disabled) {
  background-color: #e5e7eb;
  border-color: #a0aec0;
  color: #4a5568;
}

.web-search-btn-modern.is-active {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border: 1px solid #bfdbfe;
  color: #1d4ed8;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.1);
}

.web-search-btn-modern.is-active:hover:not(:disabled) {
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  border-color: #3b82f6;
  color: #1e40af;
}

.web-search-btn-modern:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 智能体工作流流转高级样式 */
.workflow-steps-container {
  background: rgba(248, 247, 250, 0.75);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(225, 219, 236, 0.5);
  border-radius: 16px;
  margin-bottom: 18px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(139, 92, 246, 0.03);
  transition: all 0.3s ease;
}
.workflow-steps-container:hover {
  box-shadow: 0 12px 36px rgba(139, 92, 246, 0.07);
}
.workflow-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f8f7fa 0%, #e8def8 100%);
  border-bottom: 1px solid rgba(225, 219, 236, 0.4);
  font-size: 13px;
  font-weight: 600;
  color: #6d28d9;
}
.workflow-steps-list {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.workflow-step-item {
  display: flex;
  gap: 12px;
  position: relative;
}
.workflow-step-item:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 9px;
  top: 22px;
  bottom: -20px;
  width: 2px;
  background: #e2e8f0;
}
.workflow-step-item.running:not(:last-child)::after {
  background: linear-gradient(to bottom, #3b82f6, rgba(59, 130, 246, 0.1), #3b82f6);
  background-size: 100% 200%;
  animation: lineFlow 1.5s infinite linear;
}
@keyframes lineFlow {
  0% { background-position: 0% 0%; }
  100% { background-position: 0% 200%; }
}
.workflow-step-item.success:not(:last-child)::after {
  background: #10b981;
}
.step-icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 2px solid #cbd5e1;
  z-index: 1;
  font-size: 11px;
  color: #64748b;
  transition: all 0.3s;
}
.workflow-step-item.running .step-icon {
  border-color: #3b82f6;
  color: #3b82f6;
  box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.4), 
              0 0 0 4px rgba(59, 130, 246, 0.2);
  animation: pulseGlow 1.8s infinite cubic-bezier(0.4, 0, 0.6, 1);
}
@keyframes pulseGlow {
  0% { box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.4), 0 0 0 0 rgba(59, 130, 246, 0.2); }
  100% { box-shadow: 0 0 0 6px rgba(59, 130, 246, 0), 0 0 0 12px rgba(59, 130, 246, 0); }
}
.workflow-step-item.success .step-icon {
  border-color: #10b981;
  background: #10b981;
  color: #fff;
  box-shadow: 0 2px 6px rgba(16, 185, 129, 0.2);
}
.workflow-step-item.error .step-icon {
  border-color: #ef4444;
  background: #ef4444;
  color: #fff;
  box-shadow: 0 2px 6px rgba(239, 68, 68, 0.2);
}
.step-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.step-title {
  display: flex;
  align-items: center;
  gap: 6px;
}
.step-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}
.workflow-step-item.running .step-name {
  color: #2563eb;
}
.step-code {
  font-size: 11px;
  color: #94a3b8;
}
.step-tool-badge {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 4px;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  padding: 3px 8px;
  font-size: 11px;
  color: #1e40af;
  margin-top: 4px;
  font-weight: 500;
  box-shadow: 0 1px 2px rgba(59, 130, 246, 0.05);
}
.step-tool-badge i {
  color: #2563eb;
}
.step-tool-badge .tool-name {
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
  font-weight: 600;
}
.step-thinking-box {
  background: #fdfbf7;
  border-left: 3px solid #f59e0b;
  border-radius: 4px;
  padding: 10px 14px;
  margin-top: 6px;
  box-shadow: inset 0 1px 3px rgba(0,0,0,0.01);
}
.step-thinking-box .thinking-title {
  font-size: 11px;
  font-weight: 600;
  color: #b5895b;
  margin-bottom: 3px;
}
.step-thinking-box .thinking-text {
  font-size: 12px;
  color: #64748b;
  white-space: pre-wrap;
  line-height: 1.6;
}
.step-content-box {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 12px;
  margin-top: 6px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.01);
}
.step-content-box .content-text {
  font-size: 12px;
  color: #334155;
  line-height: 1.6;
}
</style>
