<template>
  <div class="ai-chat-wrapper">
    <!-- 现代化弥散渐变 + 神经网络突触背景 -->
    <div class="mesh-gradient-bg">
      <div class="glow-orb orb-1"></div>
      <div class="glow-orb orb-2"></div>
      <div class="glow-orb orb-3"></div>
      
      <svg class="neural-network-svg" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="line-grad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#38bdf8" stop-opacity="0.1" />
            <stop offset="50%" stop-color="#818cf8" stop-opacity="0.3" />
            <stop offset="100%" stop-color="#ec4899" stop-opacity="0.1" />
          </linearGradient>
          <linearGradient id="line-grad-light" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#4f46e5" stop-opacity="0.1" />
            <stop offset="50%" stop-color="#a855f7" stop-opacity="0.2" />
            <stop offset="100%" stop-color="#6366f1" stop-opacity="0.1" />
          </linearGradient>
        </defs>
        <g class="neural-lines">
          <line x1="10%" y1="20%" x2="30%" y2="40%" stroke="url(#line-grad)" stroke-width="1" />
          <line x1="30%" y1="40%" x2="50%" y2="15%" stroke="url(#line-grad)" stroke-width="1.5" />
          <line x1="30%" y1="40%" x2="25%" y2="80%" stroke="url(#line-grad)" stroke-width="1" />
          <line x1="50%" y1="15%" x2="70%" y2="45%" stroke="url(#line-grad)" stroke-width="1" />
          <line x1="70%" y1="45%" x2="90%" y2="30%" stroke="url(#line-grad)" stroke-width="1.5" />
          <line x1="70%" y1="45%" x2="60%" y2="85%" stroke="url(#line-grad)" stroke-width="1" />
          <line x1="25%" y1="80%" x2="60%" y2="85%" stroke="url(#line-grad)" stroke-width="1" />
          <line x1="60%" y1="85%" x2="90%" y2="70%" stroke="url(#line-grad)" stroke-width="1" />
        </g>
        <g class="neural-nodes">
          <circle cx="10%" cy="20%" r="3" class="synapse-node node-slow" />
          <circle cx="30%" cy="40%" r="4" class="synapse-node node-fast" />
          <circle cx="50%" cy="15%" r="3.5" class="synapse-node node-pulse" />
          <circle cx="25%" cy="80%" r="3.5" class="synapse-node node-slow" />
          <circle cx="70%" cy="45%" r="5" class="synapse-node node-fast" />
          <circle cx="90%" cy="30%" r="3" class="synapse-node node-pulse" />
          <circle cx="60%" cy="85%" r="4.5" class="synapse-node node-slow" />
          <circle cx="90%" cy="70%" r="3.5" class="synapse-node node-fast" />
        </g>
      </svg>
      <div class="mesh-grid-overlay"></div>
    </div>

    <!-- ========== 左侧会话栏 ========== -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <template v-if="!isBatchMode">
          <el-button
            :disabled="isStreaming"
            :loading="creatingConv"
            class="btn-new-chat"
            @click="handleNewConversation"
          >
            <el-icon v-if="!creatingConv"><edit /></el-icon>
            新对话
          </el-button>
          <el-tooltip content="批量管理" placement="top">
            <el-button
              class="btn-batch-toggle"
              circle
              size="default"
              :disabled="isStreaming || !conversations.length"
              @click="toggleBatchMode(true)"
            >
              <el-icon><files /></el-icon>
            </el-button>
          </el-tooltip>
        </template>

        <template v-else>
          <div class="batch-header-bar">
            <el-checkbox
              v-model="isSelectAll"
              :indeterminate="isIndeterminate"
            >
              全选
            </el-checkbox>
            <span class="batch-count-tip" v-if="selectedConvIds.length">
              已选 {{ selectedConvIds.length }} 项
            </span>
            <div class="batch-header-actions">
              <el-button
                size="small"
                type="danger"
                plain
                :disabled="!selectedConvIds.length"
                :loading="batchDeleting"
                @click="handleBatchDelete"
              >
                删除
              </el-button>
              <el-button
                size="small"
                circle
                icon="Close"
                @click="toggleBatchMode(false)"
              ></el-button>
            </div>
          </div>
        </template>
      </div>

      <div
        v-loading="loadingConvs"
        class="conv-list"
        :class="{ 'in-batch-mode': isBatchMode }"
        element-loading-text="加载中..."
      >
        <div v-if="!loadingConvs && conversations.length === 0" class="conv-empty">
          暂无对话记录
        </div>

        <transition-group name="conv-fade" tag="div">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            :class="{ active: !isBatchMode && currentConvId === conv.id, selected: isBatchMode && selectedConvIds.includes(conv.id) }"
            class="conv-item"
            @click="isBatchMode ? toggleConvSelection(conv.id) : handleSelectConversation(conv.id)"
          >
            <el-checkbox
              v-if="isBatchMode"
              :model-value="selectedConvIds.includes(conv.id)"
              class="conv-checkbox"
              @change="toggleConvSelection(conv.id)"
              @click.stop
            />
            <el-icon v-else class="conv-icon"><chat-dot-round /></el-icon>
            <span class="conv-title">{{ conv.title }}</span>
            <div v-if="!isBatchMode" class="conv-actions" @click.stop>
              <el-tooltip :open-delay="300" content="重命名" placement="top">
                <el-button
                  class="conv-action-btn"
                  link
                  @click="openRenameDialog(conv)"
                >
                  <el-icon><edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip :open-delay="300" content="删除" placement="top">
                <el-button
                  class="conv-action-btn danger"
                  link
                  @click="handleDeleteConversation(conv.id)"
                >
                  <el-icon><delete /></el-icon>
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
      <div v-if="currentConvId" class="chat-header-bar">
        <span class="chat-header-indicator">
          <el-icon><chat-line-round /></el-icon>
          AI 助手对话中
        </span>
        <div class="header-tags-row">
          <el-tag
            v-if="currentConvModel"
            class="model-indicator-tag"
            effect="plain"
            size="default"
            type="info"
          >
            <el-icon class="tag-icon"><cpu /></el-icon>
            <span>模型: {{ currentConvModel }}</span>
          </el-tag>
          <el-tag
            v-if="selectedAgentCode"
            effect="light"
            size="default"
            type="primary"
            style="display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; vertical-align: middle;"
          >
            <el-icon class="tag-icon"><cpu /></el-icon>
            <span>智能体: {{ getSelectedAgentOrWorkflowLabel() }}</span>
          </el-tag>
          <el-tag
            v-else-if="selectedWorkflowCode"
            effect="light"
            size="default"
            type="warning"
            style="display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; vertical-align: middle;"
          >
            <el-icon class="tag-icon"><connection /></el-icon>
            <span>工作流: {{ getSelectedAgentOrWorkflowLabel() }}</span>
          </el-tag>
          <el-tag
            v-else
            effect="plain"
            size="default"
            style="display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; vertical-align: middle; background: rgba(99, 102, 241, 0.1); border-color: rgba(99, 102, 241, 0.3); color: #6366f1;"
          >
            <el-icon class="tag-icon"><operation /></el-icon>
            <span>自动路由模式</span>
          </el-tag>
          <el-tag
            v-if="currentKbName"
            class="kb-indicator-tag"
            effect="dark"
            size="default"
            type="success"
          >
            <el-icon class="tag-icon"><collection /></el-icon>
            <span>知识库: {{ currentKbName }}</span>
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
          <div class="welcome-setup-card">
            <div class="setup-item">
              <label class="setup-label"><el-icon class="setup-icon icon-blue"><cpu /></el-icon> 选择 AI 大模型</label>
              <el-select
                v-model="selectedModelConfigId"
                placeholder="请选择要使用的大语言模型"
                size="default"
                style="width: 100%;"
                @change="handleModelOrKbChange"
              >
                <el-option
                  v-for="item in models"
                  :key="item.id"
                  :label="item.name + ' (' + item.modelName + ')'"
                  :value="item.id"
                />
              </el-select>
            </div>

            <div class="setup-item">
              <label class="setup-label"><el-icon class="setup-icon icon-green"><collection /></el-icon> 关联知识库 (可选)</label>
              <el-select
                v-model="selectedKbId"
                clearable
                placeholder="未关联知识库"
                size="default"
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
              <label class="setup-label"><el-icon class="setup-icon icon-purple"><operation /></el-icon> 选用智能体工作流 (可选)</label>
              <el-select
                v-model="selectedWorkflowCode"
                clearable
                placeholder="自动智能路由模式 (无需指定)"
                size="default"
                style="width: 100%;"
              >
                <el-option
                  v-for="item in workflows"
                  :key="item.workflowCode"
                  :label="item.workflowName"
                  :value="item.workflowCode"
                />
              </el-select>
              <div style="font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; display: flex; align-items: center; gap: 4px;">
                <el-icon><info-filled /></el-icon>
                <span>未选时自动识别意图按需加载工具，所选智能体与工作流 100% 优先执行</span>
              </div>
            </div>

            <el-button
              icon="ChatDotRound"
              class="btn-start-chat"
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
          <el-icon class="is-loading"><loading /></el-icon>
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
                  <el-icon><warning /></el-icon>
                  {{ msg.error }}
                </div>
                <!-- AI：正常内容 -->
                <div v-else-if="msg.role === 'assistant'">
                  <div v-if="msg.statusMsg" class="loading-status-text" style="margin-bottom: 8px;">
                    <el-icon class="is-loading"><loading /></el-icon> {{ msg.statusMsg }}
                  </div>
                  <!-- 智能体工作流执行步骤 -->
                  <div v-if="msg.workflowSteps && msg.workflowSteps.length > 0" class="workflow-steps-container">
                    <div class="workflow-header">
                      <el-icon><operation /></el-icon>
                      <span>智能体工作流执行链路</span>
                    </div>
                    <div class="workflow-steps-list">
                      <div 
                        v-for="(step, stepIdx) in msg.workflowSteps" 
                        :key="stepIdx"
                        :class="['workflow-step-item', step.status]"
                      >
                        <div class="step-icon">
                          <el-icon v-if="step.status === 'running'" class="is-loading"><loading /></el-icon>
                          <el-icon v-else-if="step.status === 'success'"><circle-check /></el-icon>
                          <el-icon v-else-if="step.status === 'error'"><circle-close /></el-icon>
                          <el-icon v-else><clock /></el-icon>
                        </div>
                        <div class="step-content">
                          <div class="step-title">
                            <span class="step-name">{{ step.name }}</span>
                          </div>
                          <!-- 正在调用的系统工具展示 -->
                          <div v-if="step.activeTool" class="step-tool-badge">
                            <el-icon><folder-opened /></el-icon> 正在调用系统工具: <span class="tool-name">{{ translateToolName(step.activeTool) }}</span>
                          </div>
                          <!-- 节点输出的思考过程 -->
                          <div v-if="step.thinking" class="step-thinking-box">
                            <div class="thinking-title">思考过程：</div>
                            <div class="thinking-text">{{ step.thinking }}</div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <!-- 高端科技感思考过程展示 -->
                  <div v-if="msg.reasoningContent" class="thinking-container">
                    <div
                      class="thinking-header"
                      @click="msg.thinkingExpanded = msg.thinkingExpanded === undefined ? false : !msg.thinkingExpanded"
                    >
                      <div class="thinking-title-left">
                        <el-icon class="thinking-icon"><cpu /></el-icon>
                        <span class="thinking-title-text">
                          {{ !msg.content ? '深度思考推理中...' : '深度思维链推演完毕' }}
                        </span>
                      </div>
                      <div class="thinking-title-right">
                        <span v-if="msg.streaming && !msg.content" class="thinking-status">
                          <el-icon class="is-loading"><loading /></el-icon> 正在生成步骤
                        </span>
                        <el-icon :class="['collapse-arrow', { 'is-active': (msg.thinkingExpanded === undefined ? !msg.content : msg.thinkingExpanded) }]"><arrow-down /></el-icon>
                      </div>
                    </div>
                    <el-collapse-transition>
                      <div v-show="msg.thinkingExpanded === undefined ? !msg.content : msg.thinkingExpanded" class="thinking-content-wrapper">
                        <div class="thinking-left-line"></div>
                        <div class="markdown-body mini-markdown thinking-markdown" v-html="renderMarkdown(msg.reasoningContent)"></div>
                      </div>
                    </el-collapse-transition>
                  </div>
                  <!-- 如果是画图任务标识的消息 -->
                  <div v-if="isImageTaskMessage(msg.content)" class="image-task-panel">
                    <div v-if="extractTextBeforeTaskJson(msg.content)" class="markdown-body text-before-task" v-html="renderMarkdown(extractTextBeforeTaskJson(msg.content))"></div>
                    <div v-for="task in parseTaskInfos(msg.content)" :key="task.taskId" class="image-task-card-wrapper">
                      <!-- 骨架屏加载态 -->
                      <div v-if="task.isPending || task.status === '0'" class="image-skeleton-card">
                        <div class="glow-shimmer"></div>
                        <div class="skeleton-content">
                          <el-icon class="is-loading"><loading /></el-icon>
                          <span class="loading-text">正在绘制灵感画面...</span>
                          <span class="elapsed-time-text" v-if="task.elapsedTime !== undefined">已用时: {{ task.elapsedTime }}s</span>
                          <span class="prompt-text" v-if="task.prompt">“{{ task.prompt }}”</span>
                        </div>
                      </div>
                      
                      <!-- 成功大图卡片 -->
                      <div v-else-if="task.status === '1'" class="image-success-card">
                        <el-image 
                          :src="resolveImageUrl(task.imageUrl)" 
                          :preview-src-list="[resolveImageUrl(task.imageUrl)]" 
                          fit="contain" 
                          class="generated-img-view"
                          preview-teleported
                        >
                          <template #placeholder>
                            <div class="image-slot-loading">
                              <el-icon class="is-loading"><loading /></el-icon>加载图片中...
                            </div>
                          </template>
                        </el-image>
                        <!-- 耗时 Badge -->
                        <div class="elapsed-badge" v-if="task.elapsedTime">
                          <el-icon><clock /></el-icon>
                          <span>生成耗时: {{ task.elapsedTime }}s</span>
                        </div>
                        <div class="img-hover-actions">
                          <el-button circle size="small" icon="Download" title="下载图片" @click="handleDownload(resolveImageUrl(task.imageUrl))" />
                          <el-button round size="small" icon="Refresh" title="重新生成" class="btn-img-regenerate" @click="handleRegenerate(task, msg, index)">重新生成</el-button>
                        </div>
                      </div>

                      <!-- 生图参数配置卡片 (status === '3') -->
                      <div v-else-if="task.status === '3'" class="image-config-card">
                        <div class="config-header">
                          <el-icon><setting /></el-icon>
                          <span class="config-title">绘图配置参数</span>
                        </div>
                        <div class="config-body">
                          <div class="config-item">
                            <span class="config-label">生图提示词：</span>
                            <span class="config-value prompt-value">{{ task.prompt }}</span>
                          </div>
                          <div class="config-row">
                            <div class="config-item">
                              <span class="config-label">尺寸：</span>
                              <span class="config-value">{{ task.width }} x {{ task.height }}</span>
                            </div>
                            <div class="config-item">
                              <span class="config-label">步数：</span>
                              <span class="config-value">{{ task.steps }}</span>
                            </div>
                          </div>
                          <div class="config-item">
                            <span class="config-label">模型：</span>
                            <span class="config-value">{{ task.model }}</span>
                          </div>
                        </div>
                        <div class="config-footer">
                          <el-button type="primary" size="small" icon="DocumentCopy" @click="handleCopyText(task.prompt)">复制提示词</el-button>
                          <span class="config-tip">提示：当前聊天模型可能暂不支持直接绘图，建议复制提示词后切换为其他支持工具调用的模型</span>
                        </div>
                      </div>

                      <!-- 失败卡片 -->
                      <div v-else class="image-fail-card">
                        <el-icon class="fail-icon"><circle-close /></el-icon>
                        <span class="fail-desc">生成失败: {{ task.errorMsg || '画图服务响应异常或超时' }}</span>
                        <span class="elapsed-time-fail" v-if="task.elapsedTime">共耗时: {{ task.elapsedTime }}s</span>
                        <el-button 
                          type="danger" 
                          plain 
                          size="small" 
                          class="btn-regenerate"
                          icon="Refresh"
                          @click="handleRegenerate(task, msg, index)"
                        >重新生成</el-button>
                      </div>
                    </div>
                  </div>

                  <!-- 普通 Markdown 渲染 -->
                  <div
                    v-else
                    :class="{ 'typing-cursor': msg.streaming }"
                    class="markdown-body"
                    v-html="renderMarkdown(msg.content)"
                  ></div>
                  <!-- 报告操作工具栏 -->
                  <div v-if="!msg.loading && !msg.error && isReportMessage(msg.content)" class="report-action-card" @click="openReportView(msg.content)">
                    <div class="report-card-body">
                      <div class="report-card-left">
                        <div class="report-card-icon-wrapper">
                          <el-icon class="report-icon-svg"><document /></el-icon>
                        </div>
                        <div class="report-card-info">
                          <div class="report-card-title">
                            分析报告已生成
                            <span class="report-pill-badge">精美报告</span>
                          </div>
                          <div class="report-card-desc">系统已为您提炼核心指标与可视化图表</div>
                        </div>
                      </div>
                      <div class="report-card-right">
                        <span class="action-text">点击查看报告</span>
                        <el-icon class="action-arrow"><arrow-right /></el-icon>
                      </div>
                    </div>
                  </div>
                  <!-- 审批控制面板 -->
                  <div v-if="msg.requireApproval && msg.approved === null" class="approval-card-panel">
                    <div class="approval-title-box">
                      <div class="approval-title-text">
                        <el-icon class="approval-warning-icon"><warning /></el-icon>
                        <span>工作流已挂起，等待您的人工审核</span>
                      </div>
                      <div class="approval-node-badge">
                        <span class="badge-label">挂起节点</span>
                        <span class="badge-value">{{ getStepName(msg, msg.currentNodeCode) }}</span>
                      </div>
                    </div>
                    
                    <div class="approval-input-wrapper">
                      <el-input
                        type="textarea"
                        v-model="msg.approvalFeedback"
                        placeholder="请输入您的审核意见或调优反馈（非必填）"
                        rows="2"
                        class="approval-textarea"
                      ></el-input>
                    </div>

                    <div class="approval-action-bar">
                      <el-button 
                        class="approval-btn btn-reject"
                        size="default" 
                        icon="Close" 
                        :disabled="msg.status === 'resuming'"
                        @click="submitApproval(msg, false)"
                      >驳回审批</el-button>
                      <el-button 
                        class="approval-btn btn-approve"
                        size="default" 
                        icon="Check" 
                        :loading="msg.status === 'resuming'"
                        @click="submitApproval(msg, true)"
                      >同意执行</el-button>
                    </div>
                  </div>
                  <!-- 已审批状态展示 -->
                  <div v-else-if="msg.requireApproval && msg.approved !== null" class="approval-status-panel">
                    <div class="status-header">
                      <el-icon class="status-icon"><circle-check /></el-icon>
                      <span>已于 {{ msg.approvalTime }} 处理完毕。决策：<strong>{{ msg.approved ? '同意通过' : '驳回申请' }}</strong></span>
                    </div>
                    <div v-if="msg.approvalFeedback" class="status-feedback">
                      <span class="feedback-label">审核意见:</span>
                      <span class="feedback-value">“{{ msg.approvalFeedback }}”</span>
                    </div>
                  </div>
                </div>
                <!-- 用户消息包装（支持附件卡片展现） -->
                <div v-else class="user-bubble-wrapper">
                  <div v-if="msg.fileName" class="msg-attachment-card-wrapper">
                    <div v-for="(fileItem, fileIdx) in getAttachmentList(msg)" :key="fileIdx" class="msg-attachment-card-item">
                      <!-- 如果是图片附件 -->
                      <div v-if="isImageFile(fileItem.name)" class="msg-image-attachment">
                        <el-image
                          :preview-src-list="[fileItem.url ? (fileItem.url.startsWith('http') ? fileItem.url : (uploadUrl.replace('/common/upload', '') + fileItem.url)) : '']"
                          :src="fileItem.url ? (fileItem.url.startsWith('http') ? fileItem.url : (uploadUrl.replace('/common/upload', '') + fileItem.url)) : ''"
                          class="chat-inline-image"
                          fit="contain"
                          preview-teleported
                        >
                          <template #placeholder>
                            <div class="image-slot">
                              加载中<span class="dot">...</span>
                            </div>
                          </template>
                        </el-image>
                        <div class="image-name-badge">{{ fileItem.name }}</div>
                      </div>
                      <!-- 其他普通文档/PDF 附件 -->
                      <div v-else class="msg-attachment-card">
                        <el-icon class="attachment-card-icon"><document /></el-icon>
                        <div class="attachment-card-info">
                          <span :title="fileItem.name" class="attachment-card-name">{{ fileItem.name }}</span>
                          <span class="attachment-card-desc">
                            {{ fileItem.name.toLowerCase().endsWith('.pdf') ? '已成功关联 PDF 多模态图文解析' : '已成功关联此对话解析' }}
                          </span>
                        </div>
                        <el-link
                          v-if="fileItem.url"
                          :href="fileItem.url.startsWith('http') ? fileItem.url : (uploadUrl.replace('/common/upload', '') + fileItem.url)"
                          :underlined="false"
                          class="attachment-card-download"
                          icon="Download"
                          target="_blank"
                          type="primary"
                        ></el-link>
                      </div>
                    </div>
                  </div>
                  <span class="user-text">{{ cleanUserContent(msg.content) }}</span>
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
        <div v-if="attachments && attachments.length" class="attachment-preview-bar">
          <div v-for="(item, index) in attachments" :key="index" class="attachment-tag">
            <template v-if="isImageFile(item.name)">
              <el-image
                :src="item.url ? (item.url.startsWith('http') ? item.url : (uploadUrl.replace('/common/upload', '') + item.url)) : ''"
                class="preview-inline-image"
                fit="cover"
              />
            </template>
            <el-icon v-else><document /></el-icon>
            <span :title="item.name" class="file-name">{{ item.name }}</span>
            <el-icon class="remove-btn" @click="handleRemoveAttachment(index)"><close /></el-icon>
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
                <el-button circle icon="Close" size="small" title="取消并清空本段语音" type="danger" @click="cancelVoiceInput"></el-button>
                <el-button circle icon="Check" size="small" title="完成识别" type="success" @click="stopVoiceInput"></el-button>
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
              @keydown="handleKeyDown"
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
                multiple
                class="attachment-uploader-modern"
              >
                <el-button
                  :disabled="isStreaming || uploadingAttachment"
                  :loading="uploadingAttachment"
                  class="btn-attach-modern"
                  link
                >
                  <el-icon v-if="!uploadingAttachment"><paperclip /></el-icon>
                </el-button>
              </el-upload>

              <!-- 麦克风语音输入按钮 -->
              <el-button
                :disabled="isStreaming || uploadingAttachment"
                class="btn-voice-modern"
                title="语音输入"
                link
                @click="startVoiceInput"
              >
                <el-icon><mic /></el-icon>
              </el-button>

              <!-- 模型选择药丸 -->
              <el-popover
                v-model:visible="showModelPopover"
                placement="top-start"
                title="选择 AI 核心大脑"
                width="240"
                trigger="click"
                popper-class="pill-selector-popper popper-model"
                @show="loadModels"
              >
                <template #reference>
                  <button :disabled="isStreaming" class="config-pill-btn pill-model">
                    <el-icon><cpu /></el-icon>
                    <span class="pill-label">{{ getSelectedModelLabel() }}</span>
                    <el-icon class="pill-arrow"><arrow-down /></el-icon>
                  </button>
                </template>
                <div class="popper-selector-list">
                  <div
                    v-for="item in models"
                    :key="item.id"
                    :class="['popper-selector-item', { 'is-active': selectedModelConfigId === item.id }]"
                    @click="selectedModelConfigId = item.id; handleModelOrKbChange(); showModelPopover = false"
                  >
                    <el-icon class="item-icon"><cpu /></el-icon>
                    <span class="item-name">{{ item.name }}</span>
                    <el-icon v-if="selectedModelConfigId === item.id" class="check-icon"><check /></el-icon>
                  </div>
                </div>
              </el-popover>

              <!-- 知识库选择药丸 -->
              <el-popover
                v-model:visible="showKbPopover"
                placement="top-start"
                title="关联专属知识库"
                width="240"
                trigger="click"
                popper-class="pill-selector-popper popper-kb"
                @show="loadKnowledgeBases"
              >
                <template #reference>
                  <button :disabled="isStreaming" :class="['config-pill-btn pill-kb', { 'is-active': selectedKbId }]">
                    <el-icon><collection /></el-icon>
                    <span class="pill-label">{{ getSelectedKbLabel() }}</span>
                    <el-icon class="pill-arrow"><arrow-down /></el-icon>
                  </button>
                </template>
                <div class="popper-selector-list">
                  <div
                    :class="['popper-selector-item', { 'is-active': !selectedKbId }]"
                    @click="selectedKbId = null; handleModelOrKbChange(); showKbPopover = false"
                  >
                    <el-icon class="item-icon"><folder-delete /></el-icon>
                    <span class="item-name">不挂载任何知识库</span>
                    <el-icon v-if="!selectedKbId" class="check-icon"><check /></el-icon>
                  </div>
                  <div
                    v-for="item in knowledgeBases"
                    :key="item.id"
                    :class="['popper-selector-item', { 'is-active': selectedKbId === item.id }]"
                    @click="selectedKbId = item.id; handleModelOrKbChange(); showKbPopover = false"
                  >
                    <el-icon class="item-icon"><collection /></el-icon>
                    <span class="item-name">{{ item.name }}</span>
                    <el-icon v-if="selectedKbId === item.id" class="check-icon"><check /></el-icon>
                  </div>
                </div>
              </el-popover>

              <!-- 智能体与工作流选择药丸 -->
              <el-popover
                v-model:visible="showWorkflowPopover"
                placement="top-start"
                width="340"
                trigger="click"
                popper-class="pill-selector-popper popper-workflow-modern"
                @show="handleShowAgentWorkflowPopover"
              >
                <template #reference>
                  <button :disabled="isStreaming" :class="['config-pill-btn pill-workflow', { 'is-active': selectedWorkflowCode || selectedAgentCode }]">
                    <el-icon><cpu v-if="selectedAgentCode" /><connection v-else-if="selectedWorkflowCode" /><operation v-else /></el-icon>
                    <span class="pill-label">{{ getSelectedAgentOrWorkflowLabel() }}</span>
                    <el-icon class="pill-arrow"><arrow-down /></el-icon>
                  </button>
                </template>

                <div class="popover-modern-container">
                  <!-- 顶部三段式 Segmented Tab 切页导航 -->
                  <div class="popover-tabs-nav">
                    <button
                      :class="['tab-nav-btn', { active: popoverTab === 'auto' }]"
                      @click="popoverTab = 'auto'"
                    >
                      <el-icon><operation /></el-icon> ⚡ 自动路由
                    </button>
                    <button
                      :class="['tab-nav-btn', { active: popoverTab === 'agent' }]"
                      @click="popoverTab = 'agent'"
                    >
                      <el-icon><cpu /></el-icon> 智能体 ({{ agents ? agents.length : 0 }})
                    </button>
                    <button
                      :class="['tab-nav-btn', { active: popoverTab === 'workflow' }]"
                      @click="popoverTab = 'workflow'"
                    >
                      <el-icon><connection /></el-icon> 工作流 ({{ workflows ? workflows.length : 0 }})
                    </button>
                  </div>

                  <!-- 内容区 A：自动智能路由模式 -->
                  <div v-if="popoverTab === 'auto'" class="popover-tab-body">
                    <div
                      :class="['auto-mode-card', { active: !selectedWorkflowCode && !selectedAgentCode }]"
                      @click="selectMode('', ''); showWorkflowPopover = false"
                    >
                      <div class="card-head">
                        <div class="head-left">
                          <el-icon class="mode-icon"><operation /></el-icon>
                          <span class="mode-title">自动智能路由模式</span>
                        </div>
                        <el-icon v-if="!selectedWorkflowCode && !selectedAgentCode" class="check-icon"><check /></el-icon>
                      </div>
                      <div class="card-desc">
                        无需手动挑选。提问时系统自动识别意图，按需精准装配最佳工具（如用户查询、AI生图、联网搜索等）。
                      </div>
                    </div>
                  </div>

                  <!-- 内容区 B：智能体列表 (带搜索框与固定高度滚动) -->
                  <div v-else-if="popoverTab === 'agent'" class="popover-tab-body">
                    <div class="popover-search-row">
                      <el-input
                        v-model="agentSearchKey"
                        placeholder="搜索智能体名称..."
                        size="small"
                        prefix-icon="Search"
                        clearable
                      />
                    </div>
                    <div class="popover-scroll-list">
                      <div v-if="filteredAgents.length === 0" class="empty-hint">未找到匹配的智能体</div>
                      <div
                        v-for="item in filteredAgents"
                        :key="item.agentCode"
                        :class="['popover-list-item', { active: selectedAgentCode === item.agentCode }]"
                        @click="selectMode('agent', item.agentCode); showWorkflowPopover = false"
                      >
                        <div class="item-left">
                          <el-icon class="item-icon"><cpu /></el-icon>
                          <span class="item-name" :title="item.agentName">{{ item.agentName }}</span>
                        </div>
                        <el-icon v-if="selectedAgentCode === item.agentCode" class="check-icon"><check /></el-icon>
                      </div>
                    </div>
                  </div>

                  <!-- 内容区 C：工作流列表 (带搜索框与固定高度滚动) -->
                  <div v-else-if="popoverTab === 'workflow'" class="popover-tab-body">
                    <div class="popover-search-row">
                      <el-input
                        v-model="workflowSearchKey"
                        placeholder="搜索工作流名称..."
                        size="small"
                        prefix-icon="Search"
                        clearable
                      />
                    </div>
                    <div class="popover-scroll-list">
                      <div v-if="filteredWorkflows.length === 0" class="empty-hint">未找到匹配的工作流</div>
                      <div
                        v-for="item in filteredWorkflows"
                        :key="item.workflowCode"
                        :class="['popover-list-item', { active: selectedWorkflowCode === item.workflowCode }]"
                        @click="selectMode('workflow', item.workflowCode); showWorkflowPopover = false"
                      >
                        <div class="item-left">
                          <el-icon class="item-icon"><connection /></el-icon>
                          <span class="item-name" :title="item.workflowName">{{ item.workflowName }}</span>
                        </div>
                        <el-icon v-if="selectedWorkflowCode === item.workflowCode" class="check-icon"><check /></el-icon>
                      </div>
                    </div>
                  </div>
                </div>
              </el-popover>

              <!-- 联网搜索快速切换按钮 -->
              <button
                v-if="currentModelSupportsSearch"
                :class="['config-pill-btn pill-search', { 'is-active': enableWebSearch }]"
                :disabled="isStreaming"
                @click="toggleWebSearch"
              >
                <el-icon><search /></el-icon>
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
                <el-icon><video-pause /></el-icon>
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

    <!-- ========== 通用精美报告预览局部面板 (只在右侧内容区全屏，不遮挡左侧系统菜单) ========== -->
    <transition name="el-zoom-in-bottom">
      <div v-if="reportVisible" class="pretty-report-panel-local">
        <!-- 精致工具栏 -->
        <div class="report-toolbar-v2">
          <div class="report-toolbar-inner">
            <div class="toolbar-left">
              <div class="toolbar-brand">
                <el-icon class="brand-icon"><data-board /></el-icon>
                <span class="brand-title">AI 智能分析报告</span>
              </div>
              <span class="toolbar-divider-line"></span>
              <span class="toolbar-report-id">NO. {{ currentReportId }}</span>
            </div>
            <div class="toolbar-right">
              <el-button class="toolbar-btn" size="small" type="warning" plain :loading="aiRefining" @click="handleAiRefineReport">
                <el-icon v-if="!aiRefining"><magic-stick /></el-icon> ✨ AI 深度重塑美化
              </el-button>
              <el-button class="toolbar-btn" size="small" type="primary" plain @click="handleSaveReportToDb">
                <el-icon><folder-add /></el-icon> 保存至云端
              </el-button>
              <el-button class="toolbar-btn" size="small" type="primary" @click="handleExportPdf">
                <el-icon><download /></el-icon> 导出 PDF
              </el-button>
              <el-button class="toolbar-btn-close" size="small" circle @click="handleReportClose">
                <el-icon><close /></el-icon>
              </el-button>
            </div>
          </div>
        </div>

        <!-- 全屏暗色磨砂蒙层 + 居中浮动 AI 重塑思考看板弹窗 -->
        <transition name="el-fade-in">
          <div v-if="aiRefining" class="ai-refine-modal-backdrop">
            <div class="ai-refine-stepper-panel">
              <div class="stepper-header">
                <div class="stepper-title">
                  <el-icon class="is-loading title-spinner"><loading /></el-icon>
                  <span class="title-text">✨ AI 大模型正在深度重塑分析报告...</span>
                </div>
                <span class="stepper-tag">步骤推演与 100% 格式校验中</span>
              </div>
              <div class="stepper-body">
                <div class="step-item" :class="{ 'step-active': aiRefineStep === 1, 'step-done': aiRefineStep > 1 }">
                  <el-icon v-if="aiRefineStep > 1" class="step-icon done-icon"><circle-check-filled /></el-icon>
                  <el-icon v-else-if="aiRefineStep === 1" class="step-icon loading-icon is-loading"><loading /></el-icon>
                  <span v-else class="step-icon dot-icon"></span>
                  <span class="step-label">1. 读取上下文与核心数据</span>
                </div>
                <span class="step-arrow">➔</span>
                <div class="step-item" :class="{ 'step-active': aiRefineStep === 2, 'step-done': aiRefineStep > 2 }">
                  <el-icon v-if="aiRefineStep > 2" class="step-icon done-icon"><circle-check-filled /></el-icon>
                  <el-icon v-else-if="aiRefineStep === 2" class="step-icon loading-icon is-loading"><loading /></el-icon>
                  <span v-else class="step-icon dot-icon"></span>
                  <span class="step-label">2. 提炼 KPI & 高管决策摘要</span>
                </div>
                <span class="step-arrow">➔</span>
                <div class="step-item" :class="{ 'step-active': aiRefineStep === 3, 'step-done': aiRefineStep > 3 }">
                  <el-icon v-if="aiRefineStep > 3" class="step-icon done-icon"><circle-check-filled /></el-icon>
                  <el-icon v-else-if="aiRefineStep === 3" class="step-icon loading-icon is-loading"><loading /></el-icon>
                  <span v-else class="step-icon dot-icon"></span>
                  <span class="step-label">3. 构建数据可视化面板</span>
                </div>
              </div>
            </div>
          </div>
        </transition>

        <div id="report-print-area" class="report-preview-page">
          <div class="report-paper">
            <!-- 页眉装饰条 -->
            <div class="paper-header-v2">
              <div class="header-gradient-bar"></div>
              <div class="header-info-row">
                <span class="confidential-tag-v2">
                  <el-icon><lock /></el-icon> 内部报告 · AI 智能分析
                </span>
                <span class="report-serial-v2">编号：AI-REP-{{ currentReportId }}</span>
              </div>
            </div>

            <!-- 标题区 -->
            <div class="paper-title-area-v2">
              <div class="paper-badge-v2">
                <el-icon><notebook /></el-icon>
                <span>ANALYSIS REPORT</span>
              </div>
              <h1 class="paper-title-v2">{{ reportTitle }}</h1>
              <div class="paper-meta-v2">
                <div class="meta-item">
                  <el-icon><user /></el-icon>
                  <span>生成人：admin</span>
                </div>
                <div class="meta-item">
                  <el-icon><calendar /></el-icon>
                  <span>生成时间：{{ formatReportTime() }}</span>
                </div>
                <div class="meta-item">
                  <el-icon><chat-dot-round /></el-icon>
                  <span>会话来源：{{ currentUserName }} 的分析请求</span>
                </div>
              </div>
            </div>

            <!-- 统计概览卡片 -->
            <div v-if="reportStats" class="report-stats-row">
              <div v-for="(stat, idx) in reportStats" :key="idx" class="stat-card" :class="'stat-card-' + stat.color">
                <div class="stat-icon-box">
                  <span class="stat-emoji">{{ stat.emoji }}</span>
                </div>
                <div class="stat-info">
                  <span class="stat-value">{{ stat.value }}</span>
                  <span class="stat-label">{{ stat.label }}</span>
                </div>
              </div>
            </div>

            <!-- 精美分隔线 -->
            <div class="paper-divider-v2">
              <span class="divider-dot"></span>
              <span class="divider-dot"></span>
              <span class="divider-dot"></span>
            </div>

            <!-- ECharts 可视化数据图表 -->
            <div v-if="hasChartData" class="report-chart-section-v2">
              <div class="chart-section-header">
                <div class="chart-section-title">
                  <span class="section-icon-wrapper">
                    <el-icon><trend-charts /></el-icon>
                  </span>
                  <span>数据可视化分析</span>
                </div>
                <div class="chart-type-switcher">
                  <el-radio-group v-if="chartConfig && chartConfig.mode !== 'pie'" v-model="activeChartType" size="small" @change="switchChartType">
                    <el-radio-button label="bar"><el-icon><histogram /></el-icon> 柱状图</el-radio-button>
                    <el-radio-button label="line"><el-icon><data-line /></el-icon> 折线图</el-radio-button>
                  </el-radio-group>
                  <span v-else class="chart-type-badge">
                    <el-icon><pie-chart /></el-icon> 分布统计
                  </span>
                </div>
              </div>
              <div id="pretty-report-chart" class="pretty-chart-box-v2"></div>
            </div>

            <!-- AI 智能提炼的高管极简摘要 Banner -->
            <div v-if="refinedSchema && refinedSchema.executiveSummary" class="executive-summary-banner" style="margin-bottom: 20px; background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%); border-left: 4px solid #0284c7; padding: 16px 20px; border-radius: 8px;">
              <div class="summary-header" style="display: flex; align-items: center; gap: 8px; font-weight: 700; color: #0369a1; font-size: 15px; margin-bottom: 8px;">
                <el-icon><opportunity /></el-icon>
                <span>高管极简摘要与决策建议 (Executive Summary)</span>
              </div>
              <div class="summary-body" style="color: #334155; font-size: 14px; line-height: 1.6;">
                {{ refinedSchema.executiveSummary }}
              </div>
            </div>

            <!-- 正文内容卡片化分段展示 -->
            <div class="paper-content-v2-container">
              <div v-for="(section, idx) in reportSections" :key="idx" class="report-content-card">
                <div class="markdown-body" v-html="renderMarkdown(section)"></div>
              </div>
            </div>

            <!-- AI 智能提取的行动计划看板 (Action Plan) -->
            <div v-if="refinedSchema && refinedSchema.actionPlan && refinedSchema.actionPlan.length > 0" class="report-action-plan-section" style="margin-top: 25px; background: #fafafa; border: 1px solid #f0f0f0; padding: 20px; border-radius: 8px;">
              <div class="action-plan-header" style="display: flex; align-items: center; gap: 8px; font-size: 16px; font-weight: 700; color: #1e293b; margin-bottom: 12px;">
                <el-icon style="color: #10b981;"><checked /></el-icon>
                <span>建议改进与行动计划看板 (Action Plan)</span>
              </div>
              <el-table :data="refinedSchema.actionPlan" border stripe style="width: 100%;">
                <el-table-column label="优先级" prop="priority" width="100" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.priority === 'P1' ? 'danger' : (scope.row.priority === 'P2' ? 'warning' : 'info')" effect="dark" size="small">
                      {{ scope.row.priority }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="改进措施 / 行动建议" prop="action" min-width="220" />
                <el-table-column label="建议责任部门/人" prop="owner" width="160" align="center" />
              </el-table>
            </div>

            <!-- 页脚 -->
            <div class="paper-footer-v2">
              <div class="footer-gradient-line"></div>
              <p class="footer-disclaimer">本报告由 AI 大模型内容引擎分析生成，仅供参考，不构成最终决策依据</p>
              <p class="footer-brand">Powered by <strong>Polaris-AI</strong> · {{ formatReportTime() }}</p>
            </div>
          </div>
        </div>
      </div>
    </transition>

    <!-- ========== 重命名弹窗 ========== -->
    <el-dialog
      :close-on-click-modal="false"
      v-model="renameDialogVisible"
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
        @keyup.enter="submitRename"
      />
      <template #footer>
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
import * as echarts from 'echarts'
import {getToken} from '@/utils/auth'
import {
  createConversation,
  deleteConversation,
  deleteConversationsBatch,
  listActiveAgents,
  listConversations,
  listMessages,
  renameConversation,
  updateConversationConfig
} from '@/api/ai/chat'
import {listKnowledge} from '@/api/ai/knowledge'
import {listAvailableModel} from '@/api/ai/model'
import {listActiveWorkflows} from '@/api/ai/workflow'
import {refineReport, saveReport} from '@/api/ai/report'
import request from '@/utils/request'

export default {
  name: 'AiChat',
  data() {
    return {
      // 知识库选项与当前选择
      knowledgeBases: [],
      selectedKbId: null,

      // 可选大模型与当前选择
      models: [],
      selectedModelConfigId: null,
      selectedWorkflowCode: '',
      selectedAgentCode: '',
      workflows: [],
      agents: [],
      toolDictionary: {},
      currentWorkflowThreadId: null,
      showModelPopover: false,
      showKbPopover: false,
      showWorkflowPopover: false,

      // 现代化 Popover 智能体与工作流 Tab 选项卡及搜索
      popoverTab: 'auto',
      agentSearchKey: '',
      workflowSearchKey: '',

      conversations: [],
      loadingConvs: false,
      creatingConv: false,
      currentConvId: null,

      // 批量删除历史会话管理
      isBatchMode: false,
      selectedConvIds: [],
      batchDeleting: false,
      messages: [],
      loadingMessages: false,
      inputText: '',
      inputFocused: false,
      isStreaming: false,
      renameDialogVisible: false,
      renameTitle: '',
      renameTargetId: null,
      attachments: [],
      uploadingAttachment: false,
      uploadingCount: 0,
      uploadUrl: (import.meta.env.VITE_APP_BASE_API || '') + "/common/upload",
      uploadHeaders: { Authorization: "Bearer " + getToken() },
      activePolls: {},
      taskStateMap: {},
      // 报告预览相关
      reportVisible: false,
      reportContent: '',
      reportTitle: '',
      currentReportId: '',
      hasChartData: false,
      activeChartType: 'bar',
      chartConfig: null,
      reportChartInstance: null,
      reportSections: [],
      reportStats: null,
      aiRefining: false,
      aiRefineStep: 0,
      aiRefinementInProgress: false,
      refinedSchema: null,
      enableWebSearch: false,
      // 语音输入相关
      isListening: false,
      voiceTempText: '',
      voiceBaseText: ''
    }
  },
  computed: {
    isSelectAll: {
      get() {
        return this.conversations.length > 0 && this.selectedConvIds.length === this.conversations.length
      },
      set(val) {
        if (val) {
          this.selectedConvIds = this.conversations.map(c => c.id)
        } else {
          this.selectedConvIds = []
        }
      }
    },
    isIndeterminate() {
      return this.selectedConvIds.length > 0 && this.selectedConvIds.length < this.conversations.length
    },
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
      if (c && c.modelConfigId) {
        const m = this.models.find(item => item.id === c.modelConfigId)
        return m ? m.name : (c.model || '')
      }
      return c ? (c.model || '') : ''
    },
    currentModelSupportsSearch() {
      if (!this.selectedModelConfigId) return false
      const m = this.models.find(item => item.id === this.selectedModelConfigId)
      return m && m.enableSearch === '1'
    },
    filteredAgents() {
      if (!this.agentSearchKey) return this.agents || []
      const k = this.agentSearchKey.toLowerCase().trim()
      return (this.agents || []).filter(a =>
        (a.agentName && a.agentName.toLowerCase().includes(k)) ||
        (a.agentCode && a.agentCode.toLowerCase().includes(k))
      )
    },
    filteredWorkflows() {
      if (!this.workflowSearchKey) return this.workflows || []
      const k = this.workflowSearchKey.toLowerCase().trim()
      return (this.workflows || []).filter(w =>
        (w.workflowName && w.workflowName.toLowerCase().includes(k)) ||
        (w.workflowCode && w.workflowCode.toLowerCase().includes(k))
      )
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
    this.loadToolDictionary()
    // 读取联网搜索的偏好设置
    const savedPreference = localStorage.getItem('ai_chat_enable_web_search')
    this.enableWebSearch = savedPreference === 'true'
  },
  mounted() {
    this.loadConvList(true)
    this.loadAgents()
    document.body.classList.add('ai-chat-page')
  },
  activated() {
    // 监听 Tab 唤醒生命周期，在从模型/知识库管理页切回时静默同步最新配置
    this.loadModels()
    this.loadKnowledgeBases()
    this.loadWorkflows()
    this.loadAgents()
  },
  beforeUnmount() {
    this.abortStream()
    this.cleanupVoiceInput()
    if (this.activePolls) {
      Object.keys(this.activePolls).forEach(id => clearInterval(this.activePolls[id]))
    }
    document.body.classList.remove('ai-chat-page')
  },
  methods: {
    isImageTaskMessage(content) {
      if (!content) return false;
      // 1. 系统内置的异步生图轮询格式 (支持含有 type: image-task，或者包含 taskId 且 taskId 含有 img_ 标识)
      if (content.includes('"type":"image-task"') || content.includes('"type": "image-task"') || 
         (content.includes('"taskId":') && content.includes('"status":') && content.includes('img_'))) {
        return true;
      }
      // 2. 兼容中转直出/模型自返回的图片 URL 或 Base64 格式
      if (content.includes('"image":') && 
         (content.includes('http://') || content.includes('https://') || content.includes('data:image/') || content.includes('base64,'))) {
        return true;
      }
      // 3. 兼容模型直接返回的生图参数配置格式
      if (content.includes('"prompt":') && (content.includes('"cfg_scale":') || content.includes('"sampler":') || content.includes('"model":'))) {
        return true;
      }
      return false;
    },

    // 统一解析图片 URL：绝对地址原样返回，/profile 相对路径补上后端 baseUrl
    resolveImageUrl(url) {
      if (!url) return ''
      if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:')) {
        return url
      }
      const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
      return baseUrl + url
    },
    parseTaskInfos(content) {
      if (!content) return [];
      
      const results = [];
      
      // 1. 先尝试捕获直接返回的图片 URL 或 Base64 图像
      if (content.includes('"image":') && 
         (content.includes('http://') || content.includes('https://') || content.includes('data:image/') || content.includes('base64,'))) {
        try {
          const regex = /\{[\s\S]*?"image"\s*:\s*"[\s\S]*?"[\s\S]*?\}/g;
          let match;
          while ((match = regex.exec(content)) !== null) {
            const rawData = JSON.parse(match[0]);
            if (rawData.image) {
              results.push({
                taskId: 'direct_' + Math.random().toString(36).substring(2, 9),
                isPending: false,
                status: '1', // 直接设为成功态
                imageUrl: rawData.image,
                prompt: '生成的图片',
                errorMsg: ''
              });
            }
          }
        } catch (err) {
          console.error(">>> 解析图像 JSON 数据异常: ", err);
        }
      }

      // 2. 捕获直接返回的生图配置参数
      if (content.includes('"prompt":') && (content.includes('"cfg_scale":') || content.includes('"sampler":') || content.includes('"model":'))) {
        try {
          const regex = /\{[\s\S]*?"prompt"\s*:\s*"[\s\S]*?"[\s\S]*?\}/g;
          let match;
          while ((match = regex.exec(content)) !== null) {
            const rawData = JSON.parse(match[0]);
            if (rawData.prompt && !rawData.image) { // 避免和带 image 字段重复
              results.push({
                taskId: 'config_' + Math.random().toString(36).substring(2, 9),
                isPending: false,
                status: '3', // 状态 3：代表参数配置卡片
                prompt: rawData.prompt,
                width: rawData.width || 512,
                height: rawData.height || 512,
                steps: rawData.steps || 20,
                model: rawData.model || 'stable-diffusion',
                errorMsg: ''
              });
            }
          }
        } catch (err) {
          console.error(">>> 解析图像参数配置 JSON 异常: ", err);
        }
      }

      // 3. 捕获系统的异步轮询任务
      try {
        const regex = /\{[\s\S]*?"taskId"\s*:\s*"(img_[a-zA-Z0-9_]+?)"[\s\S]*?\}/g;
        let match;
        while ((match = regex.exec(content)) !== null) {
          try {
            const rawData = JSON.parse(match[0]);
            const taskId = rawData.taskId;
            if (!taskId) continue;

            if (this.taskStateMap[taskId]) {
              results.push({ isPending: false, ...this.taskStateMap[taskId] });
              continue;
            }

            const initialTask = {
              taskId: taskId,
              prompt: rawData.prompt || 'AI 绘图',
              status: rawData.status === 'fail' ? '2' : '0', // 0-进行中, 1-成功, 2-失败
              imageUrl: '',
              errorMsg: rawData.errorMsg || '',
              startTime: Date.now(),
              elapsedTime: 0,
              conversationId: this.currentConvId
            };

            this.taskStateMap[taskId] = initialTask;

            if (initialTask.status === '0') {
              this.startPolling(taskId);
            }

            results.push({ isPending: false, ...initialTask });
          } catch (e) {
            console.error(">>> 解析单任务 JSON 异常: ", e);
          }
        }
      } catch (err) {
        console.error(">>> 匹配任务正则表达式异常: ", err);
      }

      return results;
    },
    // 保留单任务兼容方法，避免外部零星引用导致报错
    parseTaskInfo(content) {
      const infos = this.parseTaskInfos(content);
      return infos.length > 0 ? infos[0] : { isPending: true };
    },
    extractTextBeforeTaskJson(content) {
      if (!content) return '';
      let cleaned = content.replace(/\{[\s\S]*?"taskId"\s*:\s*"(img_[a-zA-Z0-9_]+?)"[\s\S]*?\}/g, '');
      return cleaned.trim();
    },

    startPolling(taskId) {
      if (this.activePolls[taskId]) return;
      
      let pollCount = 0;
      const maxPolls = 90; // 最大轮询 90 次（每 2 秒一次，共计 180 秒 / 3 分钟）
      
      const poll = async () => {
        pollCount++;
        
        // 动态计算已用时并更新状态
        if (this.taskStateMap[taskId] && this.taskStateMap[taskId].startTime) {
          this.taskStateMap[taskId].elapsedTime = Math.round((Date.now() - this.taskStateMap[taskId].startTime) / 1000);
        }

        if (pollCount > maxPolls) {
          console.warn(`>>> 绘图任务 ${taskId} 轮询超时，主动终止。`);
          this.stopPolling(taskId);
          this.taskStateMap[taskId] = {
            ...this.taskStateMap[taskId],
            status: '2', // 将状态置为失败态
            errorMsg: '生成超时，请检查后台图像生成服务'
          };
          return;
        }

        try {
          const res = await request({
            url: `/ai/chat/image-task/status/${taskId}`,
            method: 'get'
          });
          if (res && res.code === 200) {
            const data = res.data;
            const taskStart = this.taskStateMap[taskId] ? this.taskStateMap[taskId].startTime : Date.now();
            const elapsed = Math.round((Date.now() - taskStart) / 1000);
            const convId = this.taskStateMap[taskId] ? this.taskStateMap[taskId].conversationId : this.currentConvId;
            
            this.taskStateMap[taskId] = {
              taskId: taskId,
              prompt: data.prompt,
              status: data.status,
              imageUrl: data.imageUrl,
              errorMsg: data.errorMsg,
              startTime: taskStart,
              elapsedTime: elapsed,
              conversationId: convId // 轮询覆盖时保留会话 ID
            };
            if (data.status === '1' || data.status === '2') {
              this.stopPolling(taskId);
            }
          }
        } catch (e) {
          console.error('获取画图状态异常:', e);
        }
      };

      poll();
      this.activePolls[taskId] = setInterval(poll, 2000);
    },

    stopPolling(taskId) {
      if (this.activePolls[taskId]) {
        clearInterval(this.activePolls[taskId]);
        delete this.activePolls[taskId];
      }
    },

    stopPollingByConversation(convId) {
      if (!convId || !this.activePolls) return;
      Object.keys(this.activePolls).forEach(taskId => {
        const task = this.taskStateMap[taskId];
        if (task && task.conversationId === convId) {
          this.stopPolling(taskId);
        }
      });
    },

    handleDownload(url) {
      const a = document.createElement('a');
      a.href = url;
      a.download = `AI-Generated-${Date.now()}.png`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    },

    handleRegenerate(task, msg, msgIndex) {
      if (this.isStreaming || this.uploadingAttachment) {
        this.$message.warning('正在对话或上传中，请稍候...')
        return
      }

      // 兼容 prompt 纯字符串或 task 对象的入参
      let promptText = ''
      if (typeof task === 'string') {
        promptText = task
      } else if (task && task.prompt) {
        promptText = task.prompt
      } else if (msg && msg.content) {
        promptText = msg.content
      }

      if (!promptText) return

      // 1. 优先提取当前任务或对应历史 user 消息中的参考原图
      let attachedFiles = []
      if (task && typeof task === 'object' && (task.fileUrl || task.refImageUrls)) {
        const urls = (task.fileUrl || task.refImageUrls).split(',')
        const names = (task.fileName || '').split(',')
        attachedFiles = urls.map((url, i) => ({
          name: names[i] || `原参考图${i + 1}.jpg`,
          url: url
        })).filter(f => f.url)
      }

      // 如果任务本身未记录，向上追溯上一条 user 消息的附件
      if (attachedFiles.length === 0 && msgIndex !== undefined && msgIndex > 0) {
        for (let i = msgIndex - 1; i >= 0; i--) {
          const prevMsg = this.messages[i]
          if (prevMsg && prevMsg.role === 'user' && prevMsg.fileUrl) {
            const names = (prevMsg.fileName || '').split(',')
            const urls = prevMsg.fileUrl.split(',')
            attachedFiles = names.map((name, i) => ({
              name: name || `原参考图${i + 1}.jpg`,
              url: urls[i] || ''
            })).filter(f => f.url)
            break
          }
        }
      }

      // 如果没有传递 msgIndex，兜底遍历找到最后一条包含附件的 user 消息
      if (attachedFiles.length === 0 && this.messages && this.messages.length > 0) {
        for (let i = this.messages.length - 1; i >= 0; i--) {
          const m = this.messages[i]
          if (m && m.role === 'user' && m.fileUrl) {
            const names = (m.fileName || '').split(',')
            const urls = m.fileUrl.split(',')
            attachedFiles = names.map((name, i) => ({
              name: name || `原参考图${i + 1}.jpg`,
              url: urls[i] || ''
            })).filter(f => f.url)
            break
          }
        }
      }

      // 2. 清理 Prompt 前缀，将其填入输入框，并载入参考原图附件
      let cleanPrompt = promptText
      if (cleanPrompt.startsWith('帮我画：')) {
        cleanPrompt = cleanPrompt.replace('帮我画：', '')
      } else if (cleanPrompt.startsWith('帮我画')) {
        cleanPrompt = cleanPrompt.replace('帮我画', '')
      }

      this.inputText = cleanPrompt.trim()
      this.attachments = attachedFiles

      // 3. 自动滚动并聚焦到输入框，供用户修改调整后再发送
      this.$nextTick(() => {
        this.scrollToBottom()
        this.focusInput()
      })

      this.$message.info('提示词与参考原图已装载至输入框，可修改提示词后按 Enter 重新生成')
    },

    getStepName(message, code) {
      if (this.toolDictionary && this.toolDictionary[code]) {
        return this.toolDictionary[code];
      }
      const localMap = {
        'intent_router': '意图分发员',
        'sys_user_analyst': '系统用户审计师',
        'sys_user_query': '系统用户查询员',
        'sys_user_audit': '系统用户审计师'
      };
      if (localMap[code]) return localMap[code];
      const step = (message.workflowSteps || []).find(s => s.code === code);
      return step ? step.name : code;
    },
    translateToolName(toolName) {
      if (!toolName) return '';
      if (this.toolDictionary && this.toolDictionary[toolName]) {
        return this.toolDictionary[toolName];
      }
      const map = {
        'queryUserList': '查询系统用户列表',
        'querySystemUser': '查询系统用户列表',
        'auditUserRole': '审计用户角色权限',
        'getUserAuditReport': '获取用户审计报告'
      };
      return map[toolName] || toolName;
    },
    generateUuid() {
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
      });
    },

    async submitApproval(message, approve) {
      const feedback = (message.approvalFeedback || '').trim()
      message.status = 'resuming'
      
      const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
      const url = `${baseUrl}/ai/workflow/resume?workflowCode=${message.workflowCode}&threadId=${message.threadId}&conversationId=${this.currentConvId}`
      const token = getToken()
      
      try {
        const response = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + token
          },
          body: JSON.stringify({
            approve: approve,
            feedback: feedback
          })
        })
        
        if (!response.ok) {
          const text = await response.text()
          throw new Error(text || `HTTP ${response.status}`)
        }
        
        message.approved = approve
        message.approvalTime = new Date().toLocaleTimeString()
        message.status = ''
        
        const reader = response.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''
        let currentEvent = ''
        
        this.isStreaming = true
        message.streaming = true
        
        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop()
          
          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              let data = ''
              if (line.startsWith('data: ')) {
                data = line.slice(6)
              } else {
                data = line.slice(5)
              }
              const event = currentEvent || 'message'
              
              if (event === 'node_start') {
                const steps = message.workflowSteps || []
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
                message.workflowSteps = steps
                message.content = '' // 新节点开始时重置主消息区内容，只展示当前节点的流式回复
                message.statusMsg = `智能体「${nodeName}」正在处理...`
              } else if (event === 'node_tool') {
                const steps = message.workflowSteps || []
                const parts = data.split('|')
                const nodeCode = parts[0]
                const toolName = parts[1] || ''
                const step = steps.find(s => s.code === nodeCode)
                if (step) {
                  step.activeTool = toolName
                }
                message.workflowSteps = steps
                message.statusMsg = `智能体「${step ? step.name : nodeCode}」正在调用工具: ${toolName}`
              } else if (event === 'node_thinking') {
                const steps = message.workflowSteps || []
                const parts = data.split('|')
                const nodeCode = parts[0]
                const chunk = parts[1] ? parts[1].replace(/__SSE_NEWLINE__/g, '\n') : ''
                const step = steps.find(s => s.code === nodeCode)
                if (step) {
                  step.thinking = (step.thinking || '') + chunk
                }
                message.workflowSteps = steps
              } else if (event === 'node_chunk') {
                const steps = message.workflowSteps || []
                const parts = data.split('|')
                const nodeCode = parts[0]
                const chunk = parts[1] ? parts[1].replace(/__SSE_NEWLINE__/g, '\n') : ''
                const step = steps.find(s => s.code === nodeCode)
                if (step) {
                  step.content = (step.content || '') + chunk
                }
                const isRouter = nodeCode.toLowerCase().includes('router') || nodeCode.toLowerCase().includes('decision');
                const isJson = step && step.content && step.content.trim().startsWith('{');
                if (!isRouter && !isJson) {
                  message.content = step ? step.content : (message.content + chunk)
                } else {
                  message.content = ''
                }
                message.workflowSteps = steps
              } else if (event === 'node_done') {
                const steps = message.workflowSteps || []
                const nodeCode = data.trim()
                const step = steps.find(s => s.code === nodeCode)
                if (step) {
                  step.status = 'success'
                  step.activeTool = null
                }
                message.workflowSteps = steps
                message.statusMsg = ''
              } else if (event === 'node_error') {
                const steps = message.workflowSteps || []
                const parts = data.split('|')
                const nodeCode = parts[0]
                const errMsg = parts[1] || '执行异常'
                const step = steps.find(s => s.code === nodeCode)
                if (step) {
                  step.status = 'error'
                  step.content = (step.content || '') + `\n\n❌ 节点异常: ${errMsg}`
                }
                message.workflowSteps = steps
              } else if (event === 'node_interrupt') {
                const steps = message.workflowSteps || []
                const parts = data.split('|')
                const nodeCode = parts[0]
                const step = steps.find(s => s.code === nodeCode)
                if (step) {
                  step.status = 'paused'
                }
                message.workflowSteps = steps
                message.statusMsg = ''
                message.requireApproval = true
                message.currentNodeCode = nodeCode
                message.approved = null
                message.approvalFeedback = ''
                
                this.isStreaming = false
                message.streaming = false
                if (reader) {
                  reader.cancel()
                }
                return
              } else if (event === 'workflow_done') {
                message.streaming = false
                this.isStreaming = false
                this.loadConvList()
                return
              } else if (event === 'error') {
                throw new Error(data.trim() || '工作流执行失败')
              }
            }
          }
        }
      } catch (err) {
        console.error('恢复审批流失败', err)
        this.$message.error('流式恢复失败：' + err.message)
        message.status = 'error'
        this.isStreaming = false
        message.streaming = false
      }
    },

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
            const defaultModelName = import.meta.env.VITE_APP_DEFAULT_MODEL || 'deepseek-chat'
            this.models = [{
              id: null,
              name: '默认模型',
              modelName: defaultModelName
            }]
            this.selectedModelConfigId = null
          } else {
            const currentExist = this.models.find(m => m.id === this.selectedModelConfigId)
            if (!currentExist) {
              const defModel = this.models.find(m => m.isDefault === '1')
              if (defModel) {
                this.selectedModelConfigId = defModel.id
              } else if (this.models.length > 0) {
                this.selectedModelConfigId = this.models[0].id
              }
            }
          }
        }
      } catch (e) {
        console.error('加载大模型列表失败', e)
        const defaultModelName = import.meta.env.VITE_APP_DEFAULT_MODEL || 'deepseek-chat'
        this.models = [{
          id: null,
          name: '默认模型',
          modelName: defaultModelName
        }]
        this.selectedModelConfigId = null
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

    async loadAgents() {
      try {
        const res = await listActiveAgents()
        if (res.code === 200) {
          this.agents = res.data || []
        }
      } catch (e) {
        console.error('加载智能体列表失败', e)
      }
    },

    handleShowAgentWorkflowPopover() {
      this.loadWorkflows()
      this.loadAgents()
    },

    async selectMode(type, code) {
      if (type === 'agent') {
        this.selectedAgentCode = code
        this.selectedWorkflowCode = ''
      } else if (type === 'workflow') {
        this.selectedWorkflowCode = code
        this.selectedAgentCode = ''
      } else {
        this.selectedAgentCode = ''
        this.selectedWorkflowCode = ''
      }
      if (this.currentConvId) {
        await this.handleModelOrKbChange()
      }
    },

    getSelectedAgentOrWorkflowLabel() {
      if (this.selectedAgentCode) {
        const agent = this.agents.find(a => a.agentCode === this.selectedAgentCode)
        return agent ? agent.agentName : '专属智能体'
      }
      if (this.selectedWorkflowCode) {
        const wf = this.workflows.find(w => w.workflowCode === this.selectedWorkflowCode)
        return wf ? wf.workflowName : '工作流'
      }
      return '自动智能模式'
    },

    async loadToolDictionary() {
      try {
        const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
        const token = getToken()
        const response = await fetch(`${baseUrl}/ai/agent/tools/dictionary`, {
          method: 'GET',
          headers: {
            'Authorization': 'Bearer ' + token
          }
        })
        if (response.ok) {
          const res = await response.json()
          if (res.code === 200 && res.data) {
            this.toolDictionary = res.data
          }
        }
      } catch (err) {
        console.error('加载工具翻译字典失败:', err)
      }
    },

    async handleNewConversation() {
      if (this.isStreaming) return
      this.creatingConv = true
      try {
        const res = await createConversation(this.selectedModelConfigId, this.selectedKbId)
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
        this.selectedModelConfigId = c.modelConfigId || null
        this.selectedKbId = c.knowledgeBaseId || null
        this.selectedAgentCode = c.agentCode || ''
        this.selectedWorkflowCode = c.workflowCode || ''
      } else {
        this.selectedAgentCode = ''
        this.selectedWorkflowCode = ''
      }
      await this.loadMessageList(id)
    },

    getSelectedModelLabel() {
      if (!this.selectedModelConfigId) {
        const def = this.models.find(m => m.id === null || m.isDefault === '1')
        return def ? def.name : '选择 AI 模型';
      }
      const found = this.models.find(m => m.id === this.selectedModelConfigId);
      return found ? found.name : '选择 AI 模型';
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
      // 只有在当前选中了某会话时，才需要向后端同步已有会话的模型与知识库、智能体与工作流配置
      if (this.currentConvId) {
        try {
          const res = await updateConversationConfig(
            this.currentConvId,
            this.selectedModelConfigId,
            this.selectedKbId,
            this.selectedAgentCode,
            this.selectedWorkflowCode
          )
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
            fileName: m.fileName || null,
            fileUrl: m.fileUrl || null,
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
          this.stopPollingByConversation(id) // 定向销毁被删除会话关联的所有生图轮询定时器
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

    /** 切换批量管理模式 */
    toggleBatchMode(enable) {
      this.isBatchMode = enable
      this.selectedConvIds = []
    },

    /** 切换单个会话的勾选状态 */
    toggleConvSelection(id) {
      const idx = this.selectedConvIds.indexOf(id)
      if (idx > -1) {
        this.selectedConvIds.splice(idx, 1)
      } else {
        this.selectedConvIds.push(id)
      }
    },

    /** 执行批量删除历史会话 */
    async handleBatchDelete() {
      if (!this.selectedConvIds.length) return

      const count = this.selectedConvIds.length
      try {
        await this.$confirm(`确认要批量删除选中的 ${count} 条历史会话吗？删除后关联消息不可恢复！`, '批量删除警告', {
          confirmButtonText: '确定删除',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch (error) {
        return  // 用户取消
      }

      this.batchDeleting = true
      try {
        const res = await deleteConversationsBatch(this.selectedConvIds)
        if (res.code === 200) {
          // 销毁被删除会话关联的所有轮询
          this.selectedConvIds.forEach(id => {
            this.stopPollingByConversation(id)
          })

          // 如果当前选择的会话被删除了，清空消息主视图
          if (this.selectedConvIds.includes(this.currentConvId)) {
            this.currentConvId = null
            this.messages = []
          }

          this.$message.success(`成功批量删除 ${count} 条会话`)
          this.toggleBatchMode(false)
          await this.loadConvList()
        } else {
          this.$message.error('批量删除失败：' + (res.msg || res.code))
        }
      } catch (error) {
        this.$message.error('批量删除失败，请重试')
      } finally {
        this.batchDeleting = false
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

      const attachedFiles = this.attachments || []
      this.inputText = ''
      this.attachments = []
      this.isStreaming = true

      // 追加用户气泡
      let displayContent = text
      this.messages.push({
        role: 'user',
        content: displayContent,
        loading: false,
        streaming: false,
        error: null,
        fileName: attachedFiles.map(f => f.name).join(','),
        fileUrl: attachedFiles.map(f => f.url).join(',')
      })

      // 追加 AI loading 占位
      const aiIndex = this.messages.length
      this.messages.push({ role: 'assistant', content: '', reasoningContent: '', loading: true, streaming: false, error: null })
      this.$nextTick(() => this.scrollToBottom())

      const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
      const enableSearchParam = this.enableWebSearch && this.currentModelSupportsSearch
      
      const isWorkflowMode = !!this.selectedWorkflowCode
      let url = ''
      if (isWorkflowMode) {
        const threadId = this.currentWorkflowThreadId || this.generateUuid()
        this.currentWorkflowThreadId = threadId
        url = `${baseUrl}/ai/workflow/stream?workflowCode=${this.selectedWorkflowCode}&message=${encodeURIComponent(text)}&threadId=${threadId}&conversationId=${this.currentConvId}`
        if (attachedFiles.length > 0) {
          const fileUrls = attachedFiles.map(f => f.url).join(',')
          url += `&fileUrl=${encodeURIComponent(fileUrls)}`
        }
      } else {
        url = `${baseUrl}/ai/chat/stream?conversationId=${this.currentConvId}&message=${encodeURIComponent(text)}&enableSearch=${enableSearchParam}`
        if (this.selectedAgentCode) {
          url += `&agentCode=${encodeURIComponent(this.selectedAgentCode)}`
        }
        if (attachedFiles.length > 0) {
          const fileUrls = attachedFiles.map(f => f.url).join(',')
          url += `&fileUrl=${encodeURIComponent(fileUrls)}`
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
        this.messages[aiIndex].loading = false
        this.messages[aiIndex].streaming = true

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
                  this.messages[aiIndex].workflowSteps = steps
                  this.messages[aiIndex].content = '' // 新节点开始时重置主消息区内容，只展示当前节点的流式回复
                  this.messages[aiIndex].statusMsg = `智能体「${nodeName}」正在处理...`
                  this.$nextTick(() => this.scrollToBottom())
                } else if (event === 'node_tool') {
                  const cur = this.messages[aiIndex]
                  const steps = cur.workflowSteps || []
                  const parts = data.split('|')
                  const nodeCode = parts[0]
                  const toolName = parts[1] || ''
                  const step = steps.find(s => s.code === nodeCode)
                  if (step) {
                    step.activeTool = toolName
                  }
                  this.messages[aiIndex].workflowSteps = steps
                  this.messages[aiIndex].statusMsg = `智能体「${step ? step.name : nodeCode}」正在调用工具: ${toolName}`
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
                  this.messages[aiIndex].workflowSteps = steps
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
                  const isRouter = nodeCode.toLowerCase().includes('router') || nodeCode.toLowerCase().includes('decision');
                  const isJson = step && step.content && step.content.trim().startsWith('{');
                  if (!isRouter && !isJson) {
                    this.messages[aiIndex].content = step ? step.content : (cur.content + chunk)
                  } else {
                    this.messages[aiIndex].content = ''
                  }
                  this.messages[aiIndex].workflowSteps = steps
                  this.$nextTick(() => this.scrollToBottom())
                } else if (event === 'node_done') {
                  const cur = this.messages[aiIndex]
                  const steps = cur.workflowSteps || []
                  const nodeCode = data.trim()
                  const step = steps.find(s => s.code === nodeCode)
                  if (step) {
                    step.status = 'success'
                    step.activeTool = null
                  }
                  this.messages[aiIndex].workflowSteps = steps
                  this.messages[aiIndex].statusMsg = ''
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
                  this.messages[aiIndex].workflowSteps = steps
                  this.$nextTick(() => this.scrollToBottom())
                } else if (event === 'node_interrupt') {
                  const cur = this.messages[aiIndex]
                  const steps = cur.workflowSteps || []
                  const parts = data.split('|')
                  const nodeCode = parts[0]
                  const step = steps.find(s => s.code === nodeCode)
                  if (step) {
                    step.status = 'paused'
                  }
                  this.messages[aiIndex].workflowSteps = steps
                  this.messages[aiIndex].statusMsg = ''
                  this.messages[aiIndex].requireApproval = true
                  this.messages[aiIndex].threadId = this.currentWorkflowThreadId
                  this.messages[aiIndex].workflowCode = this.selectedWorkflowCode
                  this.messages[aiIndex].currentNodeCode = nodeCode
                  this.messages[aiIndex].approved = null
                  this.messages[aiIndex].approvalFeedback = ''

                  this.isStreaming = false
                  this.messages[aiIndex].streaming = false
                  this.currentWorkflowThreadId = null // 重置，下次新发时新起
                  if (this.currentReader) {
                    this.currentReader.cancel()
                  }
                  this.currentReader = null
                  return
                } else if (event === 'workflow_done') {
                  this.messages[aiIndex].streaming = false
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
                  this.messages[aiIndex].content = cur.content + processedData
                  this.$nextTick(() => this.scrollToBottom())
                } else if (event === 'reasoning') {
                  const cur = this.messages[aiIndex]
                  const processedData = data ? data.replace(/__SSE_NEWLINE__/g, '\n') : ''
                  this.messages[aiIndex].reasoningContent = (cur.reasoningContent || '') + processedData
                  this.$nextTick(() => this.scrollToBottom())
                } else if (event === 'status') {
                  this.messages[aiIndex].statusMsg = data || ''
                } else if (event === 'done') {
                  this.messages[aiIndex].streaming = false
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
        this.messages[aiIndex].role = 'assistant'
        this.messages[aiIndex].content = ''
        this.messages[aiIndex].loading = false
        this.messages[aiIndex].streaming = false
        this.messages[aiIndex].error = errMsg
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
          lastMsg.streaming = false
          lastMsg.loading = false
          lastMsg.statusMsg = ''
          lastMsg.content = lastMsg.content || '（已停止生成/终止等待）'
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
      
      // 全局工具/节点/智能体英文方法名动态自动识别与替换为中文名称
      let cleanText = text
      if (this.toolDictionary && Object.keys(this.toolDictionary).length > 0) {
        // 对 key 长度由长到短排序，防止长词的子串被部分替换发生混乱
        const keys = Object.keys(this.toolDictionary).sort((a, b) => b.length - a.length)
        keys.forEach(key => {
          if (key && key.trim()) {
            const regex = new RegExp(key, 'g')
            cleanText = cleanText.replace(regex, this.toolDictionary[key])
          }
        })
      }

      let html = this.escapeHtml(cleanText)

      // 1. 转义安全之后，立即执行精致数据表格的深度转换
      html = this.parseAndFormatTables(html)

      // 2. 代码块
      html = html.replace(
        /```[\w]*\n?([\s\S]*?)```/g,
        '<pre class="code-block"><code>$1</code></pre>'
      )

      // 3. 行内代码
      html = html.replace(/`([^`\n]+)`/g, '<code class="inline-code">$1</code>')

      // 4. 标题 (Markdown #, ##, ###, ####) - 去掉尾部 $ 锚点，强健匹配，并支持至少一个空格分割
      html = html.replace(/^#\s+(.+)/gm, '<h1>$1</h1>')
      html = html.replace(/^##\s+(.+)/gm, '<h2>$1</h2>')
      html = html.replace(/^###\s+(.+)/gm, '<h3>$1</h3>')
      html = html.replace(/^####\s+(.+)/gm, '<h4>$1</h4>')

      // 5. 粗体与斜体
      html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      html = html.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>')

      // 5.1 过滤清除独立的 --- / *** 分割线裸文本，避免在界面裸露出多余字符串
      html = html.replace(/^(?:---|[*]{3,}|_{3,})\s*$/gm, '')

      // 6. 超链接
      html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (match, text, url) => {
        let href = url
        if (url.startsWith('http://') || url.startsWith('https://')) {
          if (url.includes('/upload/') && (url.includes('/profile/') || url.includes('/pro'))) {
            url = url.replace(/^https?:\/\/[^\/]+/, '')
          }
        }
        if (url.includes('/upload/') && !url.startsWith('/profile/')) {
          url = url.replace(/^\/pro[^\/]*\/upload\//, '/profile/upload/')
        }
        if (url.startsWith('/profile')) {
          const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
          href = baseUrl + url
        }
        return `<a href="${href}" target="_blank" class="markdown-link" style="color: #3b82f6; font-weight: 600; text-decoration: underline; margin: 0 4px;">${text}</a>`
      })

      // 7. 无序列表与有序列表（支持任意缩进空格，避免源码外露）
      html = html.replace(/^\s*[-*] (.+)$/gm, '<li>$1</li>')
      html = html.replace(/(<li>[\s\S]*?<\/li>)/g, m => `<ul>${m}</ul>`)
      html = html.replace(/<\/ul>\s*<ul>/g, '')

      html = html.replace(/^\s*\d+\. (.+)$/gm, '<ol-li>$1</ol-li>')
      html = html.replace(/(<ol-li>[\s\S]*?<\/ol-li>)/g, m => `<ol>${m}</ol>`)
      html = html.replace(/<\/ol>\s*<ol>/g, '')
      html = html.replace(/ol-li/g, 'li')

      // 8. 引用块（支持任意前导空格）
      html = html.replace(/^\s*&gt;\s+(.+)$/gm, '<blockquote>$1</blockquote>')
      html = html.replace(/<\/blockquote>\s*<blockquote>/g, '<br>')

      // 9. 水平线与换行
      html = html.replace(/^---+$/gm, '<hr>')
      html = html.replace(/\n{3,}/g, '\n\n')
      html = html.replace(/\n/g, '<br>')

      // 清除表格、标题、列表等块级 HTML 标签前后的 br 换行，防止由于 br 堆积引起的排版空隙
      html = html.replace(/<br>\s*(<\/?(table|tr|thead|tbody|th|td|ul|ol|li|h1|h2|h3|h4|blockquote|hr))/gi, '$1')
      html = html.replace(/(<\/(table|tr|thead|tbody|th|td|ul|ol|li|h1|h2|h3|h4|blockquote|hr)>)\s*<br>/gi, '$1')

      return html
    },

    // 智能解析精致数据表格（Markdown 和 空格文本对齐均适用）
    parseAndFormatTables(text) {
      if (!text) return text
      
      const lines = text.split('\n')
      const newLines = []
      let i = 0
      
      while (i < lines.length) {
        let line = lines[i].trim()
        
        // 检测表格开始条件 (必须包含竖线，且下一行是经典的表头隔离特征行)
        if (line.startsWith('|') && i + 1 < lines.length) {
          let nextLine = lines[i + 1].trim()
          
          // 使用零歧义的标准表格分割匹配正则
          if (nextLine.startsWith('|') && /^[|\s-:]+$/.test(nextLine)) {
            const rawHeaders = line.split('|').slice(1, -1).map(c => c.trim())
            let tableHtml = '<table class="report-table"><thead><tr>'
            rawHeaders.forEach(th => {
              tableHtml += `<th>${th}</th>`
            })
            tableHtml += '</tr></thead><tbody>'
            
            // 跳过表头隔离行
            i += 2 
            
            // 循环读取数据行
            while (i < lines.length) {
              let dataLine = lines[i].trim()
              if (dataLine.startsWith('|') && dataLine.endsWith('|')) {
                const cells = dataLine.split('|').slice(1, -1).map(c => {
                  let cellVal = c.trim()
                  // 如果包含状态标签，进一步转译为彩色精美状态胶囊
                  if (cellVal === '是' || cellVal === '正常' || cellVal === '已分配' || cellVal === '成功') {
                    return `<span class="report-badge badge-success">✅ ${cellVal}</span>`
                  } else if (cellVal === '否' || cellVal === '异常' || cellVal === '失败') {
                    return `<span class="report-badge badge-danger">❌ ${cellVal}</span>`
                  } else if (cellVal === '未知' || cellVal === '挂起' || cellVal === '待定') {
                    return `<span class="report-badge badge-warning">⚠️ ${cellVal}</span>`
                  }
                  return cellVal
                })
                
                tableHtml += '<tr>'
                cells.forEach(td => {
                  tableHtml += `<td>${td}</td>`
                })
                tableHtml += '</tr>'
                i++
              } else {
                break
              }
            }
            
            tableHtml += '</tbody></table>'
            // 作为一个无换行的大整体块塞入，防范中途渲染折行 Bug
            newLines.push(tableHtml)
            continue
          }
        }
        
        newLines.push(lines[i])
        i++
      }
      
      return newLines.join('\n')
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

    // 解析 Markdown 数值对比表格（仅对具有实际对比价值的数值型表格生成 ECharts 图表）
    parseTablesForCharts(content) {
      if (!content) return null

      const tables = this.extractStructuredTablesWithHeaders(content)
      if (!tables || tables.length === 0) return null

      // 筛选出第一列为名称、后面列包含有价值数值的分析型表格
      const validTables = tables.filter(t => {
        if (t.headers.length < 2 || t.rows.length < 1) return false
        // 判断后面是否有真正的可度量数值（如数量、百分比、金额、得分、占比等）
        let numericCellCount = 0
        t.rows.forEach(row => {
          row.slice(1).forEach(cell => {
            const clean = cell.replace(/[^\d.-]/g, '')
            if (clean !== '' && !isNaN(parseFloat(clean))) {
              numericCellCount++
            }
          })
        })
        // 至少有 50% 以上的数值单元格，且不能是纯列表ID/序号表格
        const totalNumCells = t.rows.length * (t.headers.length - 1)
        const isHeaderLikeId = t.headers.some(h => /ID|序号|账号|用户名|IP|时间|日期/i.test(h))
        return (numericCellCount / totalNumCells >= 0.4) && !isHeaderLikeId
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

    // 辅助解析带表头的结构化表格
    extractStructuredTablesWithHeaders(content) {
      if (!content) return []
      
      const lines = content.split('\n')
      const tables = []
      let i = 0
      
      while (i < lines.length) {
        let line = lines[i].trim()
        if (line.startsWith('|') && i + 1 < lines.length) {
          let nextLine = lines[i + 1].trim()
          if (nextLine.startsWith('|') && /^[|\s-:]+$/.test(nextLine)) {
            const headers = line.split('|').slice(1, -1).map(c => c.trim())
            const tableRows = []
            i += 2 
            
            while (i < lines.length) {
              let dataLine = lines[i].trim()
              if (dataLine.startsWith('|') && dataLine.endsWith('|')) {
                const cells = dataLine.split('|').slice(1, -1).map(c => c.trim())
                tableRows.push(cells)
                i++
              } else {
                break
              }
            }
            if (headers.length > 0 && tableRows.length > 0) {
              tables.push({ headers, rows: tableRows })
            }
            continue
          }
        }
        i++
      }
      return tables
    },

    openReportView(content) {
      // 1. 统一处理换行符，并强力过滤报告最顶部的 AI 寒暄客套前缀（如 "好的，我先从系统中查询..."）
      let normalizedContent = content ? content.replace(/\r\n/g, '\n').trim() : ''

      // 自动过滤报告第一个 Markdown 标题 (# 或 ##) 之前的所有 AI 客套与过程说明段落
      const firstHeadingIndex = normalizedContent.search(/^#{1,3}\s+/m)
      if (firstHeadingIndex > 0) {
        normalizedContent = normalizedContent.substring(firstHeadingIndex).trim()
      } else {
        // 若无标准的 # 标题，匹配清除常见的开场白模式
        normalizedContent = normalizedContent.replace(/^(好的|收到|已为您|以下是为您|好的，已|好的，我|已成功|首先)[^\n\:]*[\:\：\n]\s*/gi, '').trim()
      }

      this.reportContent = normalizedContent

      // 2. 强力提取报告大标题（兼容 # 标题、#Emoji 标题、无空格标题等多种格式）
      const titleMatch = normalizedContent.match(/^#\s*([^\n]+)$/m)
      if (titleMatch && titleMatch[1]) {
        this.reportTitle = titleMatch[1].replace(/^[🛡️📊📑📋📝\s]+/, '').trim()
      } else {
        const currentConv = this.conversations.find(c => c.id === this.currentConvId)
        this.reportTitle = currentConv ? currentConv.title : '智能数据分析报告'
      }

      this.currentReportId = Math.random().toString(36).substring(2, 10).toUpperCase()

      // 3. 超强容错正文拆分逻辑（支持 ##一、/ ## 1. / ### 各种二级、三级标题格式）
      if (normalizedContent) {
        // 清理最顶部独立的一张大标题行 (# xxx)
        let cleanContent = normalizedContent.replace(/^#\s*[^\n]+\n?/, '').trim()

        // 强力剥离正文开头与头部区域重复的元数据段落（兼容包含粗体 **、列表 -、引用 > 等多种 Markdown 格式）
        for (let i = 0; i < 6; i++) {
          cleanContent = cleanContent.replace(/^[\s\*\-\>]*[\*\_]*(报告生成时间|评估人|评估范围|生成时间|报告时间|报告编号|编制部门|报告作者|创建人|评估对象|评估周期)[\*\_]*\s*[\：\:][^\n]*\n?/gi, '').trim()
        }

        // 使用宽泛正则拆分章节：匹配行首的 ## 或 ### 标题
        const rawSections = cleanContent.split(/(?=^#{2,3}\s*[^\n]+)/gm)
        let parsedSections = rawSections
          .map(s => s.trim())
          .filter(s => {
            if (!s) return false
            // 若某段非以 ## 开头，且只包含符号或被剔除后的残余废字符，清空过滤
            if (!s.startsWith('#')) {
              const textOnly = s.replace(/[\s\*\-\>\:\：\.\,\n]/g, '')
              if (textOnly.length < 3) return false
            }
            return true
          })

        // 清洗末尾的交互/寒暄问句
        if (parsedSections.length > 0) {
          const lastIndex = parsedSections.length - 1
          let lastSection = parsedSections[lastIndex]
          const paragraphs = lastSection.split(/\n\s*\n/).map(p => p.trim()).filter(p => p.length > 0)
          if (paragraphs.length > 1) {
            const lastPara = paragraphs[paragraphs.length - 1]
            const isChatter = /[\?？吗😊]|告诉我|有帮助|需要我|深入的分析|进一步的操作/i.test(lastPara)
            if (isChatter) {
              paragraphs.pop()
              parsedSections[lastIndex] = paragraphs.join('\n\n')
            }
          }
        }

        // 兜底防御：若未切分出章节，绝对不清空内容，将完整 cleanContent 作为一个整体渲染！
        if (parsedSections.length === 0 && cleanContent.length > 0) {
          parsedSections = [cleanContent]
        }

        this.reportSections = parsedSections
      } else {
        this.reportSections = []
      }

      // 4. 精准提取指标卡片
      this.reportStats = this.extractReportStats(normalizedContent)

      this.reportVisible = true

      // 5. 解析表格数据并初始化图表
      const parsed = this.parseTablesForCharts(normalizedContent)
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

    // 辅助解析真正的表格结构数据（避免正则扫描产生的多算少算偏差 Bug）
    extractStructuredTables(content) {
      const tables = this.extractStructuredTablesWithHeaders(content)
      return tables.map(t => t.rows)
    },

    // 精准生成概览统计卡片数据
    extractReportStats(content) {
      if (!content) return null

      // 通过精准结构解析器获取表格
      const tables = this.extractStructuredTables(content)
      const tableCount = tables.length
      
      // 累计所有表格的有效数据条数
      let totalRows = 0
      tables.forEach(t => {
        totalRows += t.length
      })

      // 计算章节数（匹配所有 # / ## / ### 标题，保证非 0）
      const sectionMatches = content.match(/^#{1,3}\s*[^\n]+/gm)
      const sectionCount = sectionMatches ? sectionMatches.length : (this.reportSections.length || 1)

      // 字数统计
      const wordCount = content.replace(/\s+/g, '').length

      return [
        { label: '分析章节', value: sectionCount, emoji: '📚', color: 'indigo' },
        { label: '分析字数', value: wordCount, emoji: '✍️', color: 'blue' },
        { label: '数据表格', value: tableCount, emoji: '📊', color: 'emerald' },
        { label: '数据行数', value: totalRows, emoji: '⚡', color: 'amber' }
      ]
    },

    initReportChart() {
      if (!this.chartConfig) return
      this.$nextTick(() => {
        const chartDom = document.getElementById('pretty-report-chart')
        if (!chartDom) return

        if (this.reportChartInstance) {
          this.reportChartInstance.dispose()
        }

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

    handleExportPdf() {
      const element = document.getElementById('report-print-area')
      const fileName = `AI智能分析报告_${this.currentReportId || 'REP'}.pdf`

      if (window.html2pdf && element) {
        const opt = {
          margin:       [10, 10, 10, 10],
          filename:     fileName,
          image:        { type: 'jpeg', quality: 0.98 },
          html2canvas:  { scale: 2, useCORS: true },
          jsPDF:        { unit: 'mm', format: 'a4', orientation: 'portrait' }
        }
        window.html2pdf().set(opt).from(element).save()
      } else {
        // 尝试加载 html2pdf.js 库直接文件下载；若在无网离线环境下则优雅调起标准 PDF 保存窗口
        if (!window.html2pdfLoading) {
          window.html2pdfLoading = true
          const script = document.createElement('script')
          script.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2pdf.js/0.10.1/html2pdf.bundle.min.js'
          script.onload = () => {
            if (window.html2pdf && element) {
              const opt = {
                margin:       [10, 10, 10, 10],
                filename:     fileName,
                image:        { type: 'jpeg', quality: 0.98 },
                html2canvas:  { scale: 2, useCORS: true },
                jsPDF:        { unit: 'mm', format: 'a4', orientation: 'portrait' }
              }
              window.html2pdf().set(opt).from(element).save()
            } else {
              window.print()
            }
          }
          script.onerror = () => {
            window.print()
          }
          document.head.appendChild(script)
        } else {
          window.print()
        }
      }
    },

    async handleSaveReportToDb() {
      if (!this.reportContent) {
        this.$message.warning('无法保存，报告内容为空')
        return
      }
      try {
        const currentConv = (this.conversations || []).find(c => c.id === this.currentConvId)
        const agentCodeToSave = this.selectedAgentCode || (currentConv && currentConv.agentCode) || 'POLARIS-ANALYST'
        const payload = {
          reportCode: 'REP-' + (this.currentReportId || Date.now()),
          reportTitle: this.reportTitle || 'AI 智能分析报告',
          conversationId: this.currentConvId,
          agentCode: agentCodeToSave,
          reportContent: this.reportContent,
          reportStats: JSON.stringify(this.reportStats || []),
          createTime: new Date().toISOString().replace('T', ' ').substring(0, 19)
        }
        const res = await saveReport(payload)
        if (res.code === 200 || res.data) {
          this.$message.success('报告已成功保存归档至数据库！可在“报告中心”随时查阅。')
        } else {
          this.$message.error(res.msg || '保存报告失败')
        }
      } catch (err) {
        console.error('保存报告失败', err)
        this.$message.error('保存报告出现异常：' + (err.message || '网络连接超时'))
      }
    },

    async handleAiRefineReport() {
      if (!this.reportContent) {
        this.$message.warning('报告内容为空，无法美化')
        return
      }
      this.aiRefining = true
      this.aiRefineStep = 1
      this.aiRefinementInProgress = true

      // 动态推进思考步骤看板
      const t1 = setTimeout(() => { if (this.aiRefinementInProgress) this.aiRefineStep = 2 }, 1500)
      const t2 = setTimeout(() => { if (this.aiRefinementInProgress) this.aiRefineStep = 3 }, 3500)

      try {
        const res = await refineReport(this.reportContent)
        if (res.code === 200 && res.data) {
          let schema = null
          if (typeof res.data === 'object') {
            schema = res.data
          } else {
            let cleanText = String(res.data)
            const firstBrace = cleanText.indexOf('{')
            const lastBrace = cleanText.lastIndexOf('}')
            if (firstBrace !== -1 && lastBrace !== -1 && lastBrace > firstBrace) {
              cleanText = cleanText.substring(firstBrace, lastBrace + 1)
            }
            schema = JSON.parse(cleanText)
          }

          console.log('>>> [原生对象解析成功]:', schema)
          this.refinedSchema = schema
          this.aiRefineStep = 4 // 完成所有步骤

          if (schema.kpiCards && Array.isArray(schema.kpiCards) && schema.kpiCards.length > 0) {
            this.reportStats = schema.kpiCards.map(k => ({
              label: k.label || k.title,
              value: k.value || '0',
              emoji: k.status === 'danger' ? '🚨' : (k.status === 'warning' ? '⚠️' : '📊'),
              color: k.status === 'danger' ? 'rose' : (k.status === 'warning' ? 'amber' : 'indigo')
            }))
          }

          if (schema.visualizations && Array.isArray(schema.visualizations) && schema.visualizations.length > 0) {
            const viz = schema.visualizations[0]
            if (viz.chartData && viz.chartData.categories && viz.chartData.series) {
              this.hasChartData = true
              this.chartConfig = {
                xAxisData: viz.chartData.categories,
                series: viz.chartData.series.map(s => ({
                  name: s.name,
                  type: viz.chartType === 'line' ? 'line' : 'bar',
                  data: s.data,
                  barMaxWidth: 30
                })),
                legendData: viz.chartData.series.map(s => s.name)
              }
              this.$nextTick(() => {
                this.initReportChart()
              })
            }
          }

          setTimeout(() => {
            this.aiRefining = false
            this.aiRefinementInProgress = false
            this.$forceUpdate()
            this.$message.success('✨ AI 已成功重塑分析报告！提炼高管摘要与可视化图表。')
          }, 600)

        } else {
          this.aiRefining = false
          this.aiRefinementInProgress = false
          this.$message.error(res.msg || 'AI 美化重塑失败')
        }
      } catch (err) {
        this.aiRefining = false
        this.aiRefinementInProgress = false
        console.error('AI 重塑报告失败', err)
        this.$message.error('AI 重塑报告失败，请稍后重试')
      } finally {
        clearTimeout(t1)
        clearTimeout(t2)
      }
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
    cleanUserContent(content) {
      if (!content) return ''
      return content.split('\n\n📎 附件:')[0].split('\n📎 附件:')[0].split('📎 附件:')[0].trim()
    },

    beforeAttachmentUpload(file) {
      if (this.isStreaming) {
        this.$message.warning('正在对话中，暂不支持上传附件')
        return false
      }
      const currentTotal = (this.attachments ? this.attachments.length : 0) + this.uploadingCount
      if (currentTotal >= 5) {
        this.$message.warning('单次对话最多允许关联 5 个附件')
        return false
      }

      // 校验文件名中不能有逗号
      if (file.name.includes(',')) {
        this.$message.error(`文件 "${file.name}" 名称包含英文逗号，已被拦截`)
        return false
      }

      // 限制 10MB
      const isLt10M = file.size / 1024 / 1024 < 10
      if (!isLt10M) {
        this.$message.error(`文件 "${file.name}" 大小超过 10MB 限制!`)
        return false
      }

      // 校验文件格式
      const allowedExts = ['pdf', 'docx', 'xlsx', 'xls', 'txt', 'md', 'json', 'xml', 'csv', 'html', 'java', 'py', 'js', 'ts', 'png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp']
      const nameParts = file.name.split('.')
      const ext = nameParts[nameParts.length - 1].toLowerCase()
      if (!allowedExts.includes(ext)) {
        this.$message.error(`文件 "${file.name}" 格式不支持，支持类型：` + allowedExts.join(', '))
        return false
      }

      this.uploadingCount++
      this.uploadingAttachment = true
      return true
    },

    isImageFile(fileName) {
      if (!fileName) return false
      const ext = fileName.split('.').pop().toLowerCase()
      return ['png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp'].includes(ext)
    },

    getAttachmentList(msg) {
      if (!msg.fileName || !msg.fileUrl) return []
      const names = msg.fileName.split(',')
      const urls = msg.fileUrl.split(',')
      return names.map((name, i) => ({
        name: name,
        url: urls[i] || ''
      }))
    },

    handleAttachmentSuccess(res, file) {
      this.uploadingCount = Math.max(0, this.uploadingCount - 1)
      if (this.uploadingCount === 0) {
        this.uploadingAttachment = false
      }
      if (res.code === 200) {
        if (!this.attachments) {
          this.attachments = []
        }
        this.attachments.push({
          name: file.name,
          url: res.url
        })
        this.$message.success(`文件 "${file.name}" 上传成功`)
      } else {
        this.$message.error(res.msg || `文件 "${file.name}" 上传失败`)
      }
    },

    handleAttachmentError(err, file) {
      this.uploadingCount = Math.max(0, this.uploadingCount - 1)
      if (this.uploadingCount === 0) {
        this.uploadingAttachment = false
      }
      const fileName = file ? file.name : ''
      this.$message.error(fileName ? `文件 "${fileName}" 上传失败` : '文件上传接口调用失败')
    },

    handleRemoveAttachment(index) {
      if (this.attachments) {
        this.attachments.splice(index, 1)
      }
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

<style lang="scss" scoped>
/* ===== AI 绘图卡片相关样式 ===== */
.image-task-panel {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  max-width: 100%;
}

.image-task-card-wrapper {
  flex: 0 0 auto;
  max-width: 320px;
}

.image-skeleton-card {
  position: relative;
  width: 320px;
  height: 320px;
  border-radius: 12px;
  background: rgba(226, 232, 240, 0.65); /* 亮色底色 */
  border: 1px solid rgba(15, 23, 42, 0.08); /* 亮色微弱边框 */
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(8px);
  transition: all 0.3s ease;
}

.glow-shimmer {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0) 0%,
    rgba(99, 102, 241, 0.1) 50%,
    rgba(255, 255, 255, 0) 100%
  );
  background-size: 200% 100%;
  animation: shimmer-anim 2.5s infinite linear;
}

@keyframes shimmer-anim {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

.skeleton-content {
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #334155; /* 亮色模式下使用深灰，确保可读 */
  padding: 16px;
  text-align: center;
  transition: color 0.3s ease;

  .el-icon {
    font-size: 24px;
    color: #4f46e5; /* 亮色模式下使用较深的主题紫蓝色 */
    margin-bottom: 12px;
    transition: color 0.3s ease;
  }
}

.loading-text {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 6px;
  letter-spacing: 0.5px;
}

.prompt-text {
  font-size: 12px;
  color: #475569; /* 亮色模式下使用更清晰的次级文字颜色 */
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.3s ease;
}

/* 兼容暗色模式下的可视度与磨砂玻璃质感 */
:global(.dark) .image-skeleton-card,
:global(.theme-dark) .image-skeleton-card {
  background: rgba(15, 23, 42, 0.45) !important;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
}

:global(.dark) .skeleton-content,
:global(.theme-dark) .skeleton-content {
  color: #94a3b8 !important;
}

:global(.dark) .skeleton-content .el-icon,
:global(.theme-dark) .skeleton-content .el-icon {
  color: #6366f1 !important;
}

:global(.dark) .prompt-text,
:global(.theme-dark) .prompt-text {
  color: #64748b !important;
}

:global(.dark) .elapsed-time-text,
:global(.theme-dark) .elapsed-time-text {
  color: #94a3b8 !important;
  background: rgba(255, 255, 255, 0.06) !important;
}

:global(.dark) .image-config-card,
:global(.theme-dark) .image-config-card {
  background: rgba(30, 41, 59, 0.7) !important;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
}

:global(.dark) .image-config-card .config-header,
:global(.theme-dark) .image-config-card .config-header {
  color: #818cf8 !important;
}

:global(.dark) .image-config-card .config-body,
:global(.theme-dark) .image-config-card .config-body {
  color: #cbd5e1 !important;
}

:global(.dark) .image-config-card .config-value,
:global(.theme-dark) .image-config-card .config-value {
  background: rgba(255, 255, 255, 0.06) !important;
}

:global(.dark) .image-config-card .config-footer,
:global(.theme-dark) .image-config-card .config-footer {
  border-top: 1px solid rgba(255, 255, 255, 0.08) !important;
}

.image-success-card {
  position: relative;
  width: 320px;
  height: 320px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.generated-img-view {
  width: 100%;
  height: 100%;
  transition: transform 0.3s ease;
  cursor: zoom-in;
  object-fit: contain;
}

.image-success-card:hover .generated-img-view {
  transform: scale(1.02);
}

.img-hover-actions {
  position: absolute;
  bottom: -40px;
  left: 0; right: 0;
  height: 40px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.8));
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 12px;
  gap: 8px;
  transition: bottom 0.2s ease;
  z-index: 2;
}

/* 蒙层内的下载/刷新按钮：固定使用深色玻璃质感配色，不随亮暗主题切换，
   避免选中(hover/active)后与浅色态混色导致图标看不清 */
.img-hover-actions .el-button {
  --el-button-bg-color: rgba(255, 255, 255, 0.12);
  --el-button-border-color: rgba(255, 255, 255, 0.35);
  --el-button-text-color: #ffffff;
  --el-button-hover-bg-color: rgba(255, 255, 255, 0.28);
  --el-button-hover-border-color: rgba(255, 255, 255, 0.6);
  --el-button-hover-text-color: #ffffff;
  --el-button-active-bg-color: rgba(255, 255, 255, 0.4);
  --el-button-active-border-color: rgba(255, 255, 255, 0.7);
  --el-button-active-text-color: #ffffff;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.image-success-card:hover .img-hover-actions {
  bottom: 0;
}

/* 图片预览放大后的操作按钮（关闭/上一张/下一张/放大缩小旋转等）：
   固定使用深色玻璃质感配色，不随亮暗主题变量变化，避免暗黑模式下
   背景变浅导致白色图标看不清 */
:global(.el-image-viewer__btn) {
  background-color: rgba(0, 0, 0, 0.6) !important;
  border-color: rgba(255, 255, 255, 0.3) !important;
  color: #ffffff !important;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  opacity: 0.9;
}

:global(.el-image-viewer__btn:hover) {
  background-color: rgba(0, 0, 0, 0.8) !important;
  opacity: 1;
}

:global(.el-image-viewer__actions) {
  background-color: rgba(0, 0, 0, 0.6) !important;
  border-color: rgba(255, 255, 255, 0.3) !important;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

:global(.el-image-viewer__actions__inner) {
  color: #ffffff !important;
}

.image-slot-loading {
  width: 100%;
  height: 100%;
  background: rgba(15, 23, 42, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 12px;
}

.image-fail-card {
  position: relative;
  width: 320px;
  min-height: 140px;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-sizing: border-box;
  margin: 4px 0;
  
  /* 亮色模式卡片背景与边框 */
  background: linear-gradient(135deg, rgba(254, 242, 242, 0.95) 0%, rgba(254, 226, 226, 0.85) 100%);
  border: 1px solid rgba(248, 113, 113, 0.35);
  box-shadow: 0 2px 10px rgba(239, 68, 68, 0.08);

  /* 暗色模式卡片背景与边框 */
  :global(.dark) &,
  :global(.theme-dark) &,
  .dark &,
  .theme-dark & {
    background: rgba(30, 41, 59, 0.85);
    border: 1px solid rgba(239, 68, 68, 0.35);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
  }

  .fail-icon {
    font-size: 26px;
    color: #dc2626; /* 亮色下清晰深红 */

    :global(.dark) &,
    :global(.theme-dark) &,
    .dark &,
    .theme-dark & {
      color: #f87171;
    }
  }

  .fail-desc {
    font-size: 13px;
    font-weight: 500;
    line-height: 1.4;
    text-align: center;
    color: #991b1b; /* 亮色模式高对比度深红，极度清晰 */
    margin-bottom: 2px;

    :global(.dark) &,
    :global(.theme-dark) &,
    .dark &,
    .theme-dark & {
      color: #fca5a5;
    }
  }

  .elapsed-time-fail {
    font-size: 11px;
    color: #991b1b;
    background: rgba(239, 68, 68, 0.12);
    padding: 2px 8px;
    border-radius: 12px;
    font-family: monospace;

    :global(.dark) &,
    :global(.theme-dark) &,
    .dark &,
    .theme-dark & {
      color: #94a3b8;
      background: rgba(15, 23, 42, 0.4);
    }
  }

  /* 重新生成按钮：亮/暗模式独立适配与高质感渐变 */
  .btn-regenerate {
    margin-top: 4px;
    border-radius: 20px;
    padding: 6px 18px;
    font-weight: 500;
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

    /* 亮色模式按钮 */
    --el-button-bg-color: rgba(239, 68, 68, 0.1);
    --el-button-border-color: rgba(239, 68, 68, 0.35);
    --el-button-text-color: #dc2626;
    --el-button-hover-bg-color: #dc2626;
    --el-button-hover-border-color: #dc2626;
    --el-button-hover-text-color: #ffffff;
    --el-button-active-bg-color: #b91c1c;
    --el-button-active-border-color: #b91c1c;
    --el-button-active-text-color: #ffffff;
    box-shadow: 0 2px 6px rgba(220, 38, 38, 0.12);

    /* 暗色模式按钮 */
    :global(.dark) &,
    :global(.theme-dark) &,
    .dark &,
    .theme-dark & {
      --el-button-bg-color: rgba(239, 68, 68, 0.22);
      --el-button-border-color: rgba(239, 68, 68, 0.5);
      --el-button-text-color: #fee2e2;
      --el-button-hover-bg-color: rgba(239, 68, 68, 0.45);
      --el-button-hover-border-color: rgba(239, 68, 68, 0.75);
      --el-button-hover-text-color: #ffffff;
      --el-button-active-bg-color: rgba(239, 68, 68, 0.6);
      --el-button-active-border-color: rgba(239, 68, 68, 0.9);
      --el-button-active-text-color: #ffffff;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
    }

    &:hover {
      transform: translateY(-1px);
    }
  }
}

.elapsed-time-text {
  font-size: 11px;
  color: #64748b; /* 亮色下 slate-500 */
  margin-top: 4px;
  margin-bottom: 8px;
  background: rgba(15, 23, 42, 0.04);
  padding: 2px 8px;
  border-radius: 20px;
  font-family: monospace;
}

.elapsed-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(4px);
  color: #ffffff;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 4px;
  z-index: 3;
  pointer-events: none;
  border: 1px solid rgba(255, 255, 255, 0.1);
  font-family: monospace;
}

.elapsed-time-fail {
  font-size: 11px;
  color: #ef4444;
  opacity: 0.8;
  margin-bottom: 4px;
  font-family: monospace;
}

.image-config-card {
  width: 320px;
  border-radius: 12px;
  background: rgba(241, 245, 249, 0.85); /* 亮色底色 */
  border: 1px solid rgba(15, 23, 42, 0.08);
  padding: 14px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
  backdrop-filter: blur(8px);
}

.config-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #4f46e5;
  font-weight: 600;
  font-size: 14px;
  
  .el-icon {
    font-size: 16px;
  }
}

.config-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: #334155;
}

.config-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.config-label {
  color: #64748b;
  font-weight: 500;
}

.config-value {
  background: rgba(15, 23, 42, 0.04);
  padding: 4px 8px;
  border-radius: 6px;
  font-family: monospace;
  word-break: break-all;
}

.prompt-value {
  max-height: 48px;
  overflow-y: auto;
  white-space: pre-wrap;
}

.config-footer {
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid rgba(15, 23, 42, 0.05);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.config-tip {
  font-size: 10px;
  color: #94a3b8;
  text-align: center;
}


/* ==========================================================================
   针对 AI 对话页面，取消外层滚动条，隐藏版权信息
   ========================================================================== */
:global(.app-main:has(.ai-chat-wrapper)) {
  overflow: hidden !important;
}
:global(.app-main:has(.ai-chat-wrapper) .copyright) {
  display: none !important;
}

/* ===== 整体布局 ===== */
.ai-chat-wrapper {
  display: flex;
  height: calc(100vh - 120px) !important;
  background: transparent !important;
  overflow: hidden;
  position: relative;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}

/* ===== 现代化弥散渐变 + 神经网络连线背景 ===== */
.mesh-gradient-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: 1;
  pointer-events: none;
}

.neural-network-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 3;
}

.synapse-node {
  fill: var(--polaris-brand-color);
  filter: drop-shadow(0 0 6px var(--polaris-brand-color));
  opacity: 0.85;

  &.node-slow { animation: nodeBreath 4s infinite alternate ease-in-out; }
  &.node-fast { animation: nodeBreath 2s infinite alternate ease-in-out; }
  &.node-pulse { animation: nodeRadarPulse 3s infinite ease-out; }
}

@keyframes nodeBreath {
  0% { transform: scale(1); opacity: 0.5; }
  100% { transform: scale(1.2); opacity: 0.95; }
}

@keyframes nodeRadarPulse {
  0% { r: 2px; opacity: 0.8; }
  100% { r: 6px; opacity: 0; }
}

.glow-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.35;
  transition: all 1s ease;
  mix-blend-mode: screen;

  .theme-light & {
    mix-blend-mode: multiply;
    opacity: 0.12;
  }
}

.orb-1 {
  width: 600px;
  height: 600px;
  top: -10%;
  left: -10%;
  animation: float-orb-1 25s infinite alternate ease-in-out;
  background: radial-gradient(circle, var(--polaris-brand-color) 0%, transparent 70%);
}

.orb-2 {
  width: 650px;
  height: 650px;
  bottom: -20%;
  right: -10%;
  animation: float-orb-2 30s infinite alternate ease-in-out;
  background: radial-gradient(circle, #0ea5e9 0%, transparent 70%);
}

.orb-3 {
  width: 500px;
  height: 500px;
  top: 40%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: float-orb-3 20s infinite alternate ease-in-out;
  background: radial-gradient(circle, #a855f7 0%, transparent 70%);
}

@keyframes float-orb-1 {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(80px, 50px) scale(1.1); }
}

@keyframes float-orb-2 {
  0% { transform: translate(0, 0) scale(1.1); }
  100% { transform: translate(-60px, -80px) scale(0.9); }
}

@keyframes float-orb-3 {
  0% { transform: translate(-50%, -50%) rotate(0deg); }
  100% { transform: translate(-40%, -45%) rotate(180deg); }
}

.mesh-grid-overlay {
  position: absolute;
  inset: 0;
  background-image: 
    radial-gradient(circle at 1px 1px, rgba(255, 255, 255, 0.02) 1px, transparent 0),
    linear-gradient(to right, rgba(255, 255, 255, 0.008) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(255, 255, 255, 0.008) 1px, transparent 1px);
  background-size: 50px 50px, 50px 50px, 50px 50px;
  z-index: 4;

  .theme-light & {
    background-image: 
      radial-gradient(circle at 1px 1px, rgba(0, 0, 0, 0.015) 1px, transparent 0),
      linear-gradient(to right, rgba(0, 0, 0, 0.008) 1px, transparent 1px),
      linear-gradient(to bottom, rgba(0, 0, 0, 0.008) 1px, transparent 1px);
  }
}

/* ===== 侧边栏 (Glassmorphism) ===== */
.sidebar {
  width: 240px;
  min-width: 240px;
  background: var(--polaris-card-bg) !important;
  backdrop-filter: blur(25px);
  -webkit-backdrop-filter: blur(25px);
  border-right: 1px solid var(--polaris-card-border) !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 10;
}

.sidebar-header {
  padding: 14px 12px 12px;
  border-bottom: 1px solid var(--polaris-inner-border);
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 62px;
  box-sizing: border-box;
}

.btn-new-chat {
  flex: 1;
  min-width: 0;
  background: rgba(255, 255, 255, 0.02) !important;
  border: 1px dashed var(--polaris-card-border) !important;
  color: var(--polaris-text-main) !important;
  border-radius: 10px !important;
  font-size: 13px !important;
  font-weight: 600 !important;
  gap: 6px !important;
  height: 38px !important;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1) !important;
  backdrop-filter: blur(5px);
  
  &:hover:not(:disabled) {
    background: var(--polaris-brand-hover) !important;
    border-color: var(--polaris-brand-color) !important;
    color: var(--polaris-brand-color) !important;
    box-shadow: 0 4px 12px var(--polaris-brand-glow) !important;
    transform: translateY(-1px);
  }
}

.btn-batch-toggle {
  flex-shrink: 0;
  border-radius: 10px !important;
  border: 1px solid var(--polaris-card-border) !important;
  background: rgba(255, 255, 255, 0.02) !important;
  color: var(--polaris-text-sub) !important;
  width: 38px !important;
  height: 38px !important;
  transition: all 0.2s ease !important;

  &:hover:not(:disabled) {
    color: var(--polaris-brand-color) !important;
    border-color: var(--polaris-brand-color) !important;
    background: var(--polaris-brand-hover) !important;
  }
}

.batch-header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 6px;

  .el-checkbox {
    margin-right: 0;
    --el-checkbox-text-color: var(--polaris-text-main);
  }
}

.batch-count-tip {
  font-size: 12px;
  color: var(--polaris-brand-color);
  font-weight: 600;
  background: var(--polaris-brand-hover);
  padding: 2px 6px;
  border-radius: 6px;
  white-space: nowrap;
}

.batch-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px;
  min-height: 0;
}

.conv-empty {
  text-align: center;
  color: var(--polaris-text-sub);
  font-size: 12px;
  padding: 36px 0;
  font-weight: 500;
}

.conv-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  color: var(--polaris-text-sub);
  transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
  user-select: none;
  gap: 8px;
  margin-bottom: 4px;
  position: relative;
  border-left: 3px solid transparent;

  &:hover {
    background: var(--polaris-brand-hover) !important;
    color: var(--polaris-text-main);
  }

  &.active {
    background: var(--polaris-brand-hover) !important;
    color: var(--polaris-brand-color) !important;
    font-weight: 700;
    border-left-color: var(--polaris-brand-color);
  }

  &.selected {
    background: rgba(239, 68, 68, 0.08) !important;
    border-left-color: #ef4444 !important;
    color: var(--polaris-text-main) !important;

    :global(.dark) &,
    :global(.theme-dark) &,
    .dark &,
    .theme-dark & {
      background: rgba(239, 68, 68, 0.15) !important;
    }
  }

  .conv-checkbox {
    margin-right: 2px;
    height: auto;
  }
}

.conv-icon {
  font-size: 14px;
  flex-shrink: 0;
  opacity: 0.8;
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
  gap: 2px;
  flex-shrink: 0;
}

.conv-item:hover .conv-actions,
.conv-item.active .conv-actions {
  display: flex;
}

.conv-action-btn {
  padding: 4px !important;
  height: auto !important;
  color: var(--polaris-text-sub) !important;
  font-size: 13px !important;
  transition: color 0.2s !important;

  &:hover {
    color: var(--polaris-brand-color) !important;
  }

  &.danger:hover {
    color: var(--polaris-danger-color) !important;
  }
}

/* 侧栏列表过滤动效 */
.conv-fade-enter-active,
.conv-fade-leave-active { transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1); }
.conv-fade-enter-from  { opacity: 0; transform: translateX(-10px); }
.conv-fade-leave-to    { opacity: 0; transform: translateX(-10px); }

/* ===== 主聊天区 ===== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  background: transparent !important;
  position: relative;
  z-index: 10;
}

/* 顶部指示栏 */
.chat-header-bar {
  padding: 14px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  background: var(--polaris-card-bg) !important;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--polaris-inner-border) !important;
}

.chat-header-indicator {
  font-size: 14.5px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--polaris-text-main);
  letter-spacing: 0.5px;
}

.header-tags-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.model-indicator-tag, .kb-indicator-tag {
  font-weight: 700;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  gap: 6px;
  white-space: nowrap;
  height: 28px;
  line-height: 28px;
  padding: 0 12px;
  border-radius: 14px;
  border: none !important;
  font-size: 11.5px;

  :deep(.el-tag__content) {
    display: inline-flex !important;
    align-items: center !important;
    gap: 4px !important;
  }
}

.model-indicator-tag {
  background: var(--polaris-brand-hover) !important;
  color: var(--polaris-brand-color) !important;
}

.kb-indicator-tag {
  background: rgba(16, 185, 129, 0.1) !important;
  color: #10b981 !important;
}

.tag-icon {
  margin: 0 !important;
  font-size: 13px;
}

/* 欢迎页与大卡片 */
.welcome-screen {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  z-index: 1;
}

.welcome-glow {
  position: absolute;
  width: 450px;
  height: 450px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--polaris-brand-glow) 0%, transparent 70%);
  pointer-events: none;
}

.welcome-avatar {
  width: 72px;
  height: 72px;
  border-radius: 22px;
  background: linear-gradient(135deg, var(--polaris-brand-color) 0%, #818cf8 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: #fff;
  box-shadow: 0 12px 36px var(--polaris-brand-glow);
  margin-bottom: 8px;
  font-weight: 800;
}

.welcome-title {
  font-size: 26px;
  font-weight: 800;
  color: var(--polaris-text-main);
  margin: 0;
  letter-spacing: -0.5px;
}

.welcome-subtitle {
  font-size: 14px;
  color: var(--polaris-text-sub);
  margin: 0;
  font-weight: 500;
}

/* 初始化配置卡片 */
.welcome-setup-card {
  margin-top: 32px;
  width: 480px;
  padding: 30px;
  background: var(--polaris-card-bg) !important;
  border: 1px solid var(--polaris-card-border) !important;
  border-radius: 22px !important;
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.12) !important;
  backdrop-filter: blur(35px);
  -webkit-backdrop-filter: blur(35px);
  display: flex;
  flex-direction: column;
  gap: 18px;
  text-align: left;
}

.setup-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.setup-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--polaris-text-main);
  display: flex;
  align-items: center;
  gap: 6px;
}

.setup-icon {
  font-size: 14px;
  
  &.icon-blue { color: var(--polaris-brand-color); }
  &.icon-green { color: #10b981; }
  &.icon-purple { color: #a855f7; }
}

/* 配置卡片里的下拉框美化 */
.welcome-setup-card :deep(.el-select__wrapper) {
  border-radius: 12px !important;
  background-color: rgba(0, 0, 0, 0.15) !important;
  border: 1px solid var(--polaris-card-border) !important;
  box-shadow: none !important;

  .theme-light & {
    background-color: #ffffff !important;
  }
}

.btn-start-chat {
  margin-top: 12px;
  width: 100%;
  height: 44px;
  font-size: 14.5px;
  font-weight: 700;
  border-radius: 12px;
  border: none !important;
  background: linear-gradient(135deg, var(--polaris-brand-color) 0%, #818cf8 100%) !important;
  box-shadow: 0 6px 16px var(--polaris-brand-glow) !important;
  color: #fff !important;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px var(--polaris-brand-glow) !important;
    opacity: 0.95;
  }
}

/* 欢迎页渐变动画 */
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease; }
.fade-enter-from, .fade-leave-to       { opacity: 0; }

/* ===== 消息渲染列表 ===== */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0 32px;
  min-height: 0;
}

.messages-inner {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.messages-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--polaris-text-sub);
  font-size: 13px;
  padding: 48px 0;
  font-weight: 600;
}

.msg-slide-enter-active { transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1); }
.msg-slide-enter-from   { opacity: 0; transform: translateY(12px); }

.msg-row {
  display: flex;
  align-items: flex-start;
  padding: 14px 48px;
  gap: 18px;
  max-width: 1100px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;

  &.user {
    justify-content: flex-end;
  }
}

.avatar {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);

  &:hover {
    transform: scale(1.08) rotate(5deg);
  }

  &.user-av {
    background: linear-gradient(135deg, var(--polaris-brand-color) 0%, #818cf8 100%);
    color: #fff;
    font-size: 11px;
  }

  &.ai-av {
    background: linear-gradient(135deg, #0ea5e9 0%, var(--polaris-brand-color) 100%);
    color: #fff;
    font-size: 16px;
    box-shadow: 0 4px 14px var(--polaris-brand-glow);
  }
}

.bubble {
  max-width: 78%;
  padding: 14px 20px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
  box-sizing: border-box;
}

.user-bubble {
  background: linear-gradient(135deg, var(--polaris-brand-color) 0%, #818cf8 100%) !important;
  color: #ffffff !important;
  border-bottom-right-radius: 4px;
  box-shadow: 0 6px 18px var(--polaris-brand-glow) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;

  .user-text {
    color: #ffffff !important;
  }
}

.assistant-bubble {
  max-width: 82% !important;
  background: var(--polaris-card-bg) !important;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--polaris-card-border) !important;
  color: var(--polaris-text-main) !important;
  border-bottom-left-radius: 4px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.03) !important;
}

.has-error {
  border-color: var(--polaris-danger-color) !important;
  background: rgba(239, 68, 68, 0.05) !important;
}

/* 思考链展示框 */
.thinking-container {
  background: rgba(0, 0, 0, 0.12) !important;
  border: 1px solid var(--polaris-inner-border) !important;
  border-radius: 12px;
  margin-bottom: 16px;
  overflow: hidden;
  transition: all 0.3s ease;

  .theme-light & {
    background: rgba(255, 255, 255, 0.4) !important;
  }

  &:hover {
    border-color: var(--polaris-thinking-color) !important;
    box-shadow: 0 4px 12px var(--polaris-thinking-glow) !important;
  }
}

.thinking-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.02) !important;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid var(--polaris-inner-border) !important;
}

.thinking-title-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.thinking-icon {
  font-size: 15px;
  color: var(--polaris-thinking-color);
  animation: brainPulse 2s infinite ease-in-out;
}

@keyframes brainPulse {
  0%, 100% { transform: scale(1); opacity: 0.75; }
  50% { transform: scale(1.15); opacity: 1; }
}

.thinking-title-text {
  font-size: 12px;
  font-weight: 700;
  color: var(--polaris-thinking-color);
  letter-spacing: 0.5px;
}

.thinking-status {
  font-size: 11px;
  color: var(--polaris-thinking-color);
  font-weight: 600;
}

.collapse-arrow {
  font-size: 12px;
  color: var(--polaris-thinking-color);
  transition: transform 0.3s cubic-bezier(0.2, 0, 0, 1);

  &.is-active {
    transform: rotate(-180deg);
  }
}

.thinking-content-wrapper {
  display: flex;
  padding: 12px 14px;
  background-color: transparent !important;
}

.thinking-left-line {
  width: 2px;
  background: linear-gradient(to bottom, var(--polaris-thinking-color) 0%, transparent 100%);
  margin-right: 12px;
  flex-shrink: 0;
  border-radius: 1px;
}

.thinking-markdown {
  flex-grow: 1;
  font-size: 12px !important;
  line-height: 1.65 !important;
  color: var(--polaris-text-sub) !important;
}

/* 附件卡片样式 */
.user-bubble-wrapper {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
}

.msg-attachment-card-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.msg-image-attachment {
  max-width: 300px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  position: relative;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  text-align: left;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  }

  .chat-inline-image {
    width: 100%;
    height: 180px;
    display: block;
    object-fit: contain;
    cursor: zoom-in;
  }

  .image-name-badge {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    background: rgba(0, 0, 0, 0.6);
    backdrop-filter: blur(4px);
    -webkit-backdrop-filter: blur(4px);
    color: #fff;
    font-size: 11px;
    padding: 6px 10px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.msg-attachment-card {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.15) !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
  border-radius: 10px;
  gap: 12px;
  max-width: 300px;
  width: 100%;
  box-sizing: border-box;
  text-align: left;
}

.attachment-card-name {
  font-size: 12.5px;
  font-weight: 700;
  color: #ffffff;
}

.attachment-card-desc {
  font-size: 10.5px;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 2px;
}

/* 深度打字光标 */
.typing-cursor::after {
  content: '▋';
  font-size: 12px;
  animation: blink 0.8s step-start infinite;
  margin-left: 2px;
  color: var(--polaris-brand-color);
  vertical-align: baseline;
}

.loading-dots span {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: var(--polaris-text-sub);
  border-radius: 50%;
  animation: dotBounce 1.2s infinite ease-in-out;
}

/* Markdown 代码高亮 */
.markdown-body :deep(pre.code-block) {
  background: #0f172a !important;
  color: #e2e8f0 !important;
  padding: 16px 20px;
  border-radius: 10px;
  overflow-x: auto;
  font-size: 13px;
  margin: 12px 0;
  font-family: 'JetBrains Mono', monospace;

  .theme-light & {
    background: #f8fafc !important;
    color: #334155 !important;
    border: 1px solid rgba(0,0,0,0.05);
  }
}

.markdown-body :deep(code.inline-code) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: var(--polaris-brand-color) !important;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 12.5px;
  font-family: 'JetBrains Mono', monospace;

  .theme-light & {
    background: rgba(0, 0, 0, 0.04) !important;
  }
}

/* 待发送附件预览栏 */
.attachment-preview-bar {
  max-width: 820px;
  margin: 0 auto 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-start;
}

.attachment-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--polaris-card-bg);
  border: 1px solid var(--polaris-card-border);
  padding: 6px 12px;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  max-width: 100%;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);

  .preview-inline-image {
    width: 32px;
    height: 32px;
    border-radius: 4px;
    object-fit: cover;
    flex-shrink: 0;
  }

  .el-icon {
    font-size: 16px;
    color: var(--polaris-text-sub);
  }

  .file-name {
    font-size: 12px;
    color: var(--polaris-text-main);
    max-width: 150px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 500;
  }

  .remove-btn {
    cursor: pointer;
    font-size: 14px;
    color: var(--polaris-text-sub);
    transition: color 0.2s;
    margin-left: 4px;

    &:hover {
      color: var(--polaris-danger-color);
    }
  }
}

/* ===== 输入区盒 ===== */
.input-area {
  padding: 12px 48px 24px;
  background: transparent;
  border-top: 1px solid var(--polaris-inner-border) !important;
  flex-shrink: 0;
}

.input-box-modern {
  position: relative;
  max-width: 820px;
  margin: 0 auto;
  background: var(--polaris-card-bg) !important;
  border: 1px solid var(--polaris-card-border) !important;
  border-radius: 16px;
  padding: 14px 18px 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08) !important;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  box-sizing: border-box;

  &.focused {
    border-color: var(--polaris-brand-color) !important;
    box-shadow: 0 0 0 3px var(--polaris-brand-glow) !important;
  }
}

/* 精美药丸按钮 */
.config-pill-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 12px;
  border-radius: 14px;
  border: 1px solid var(--polaris-card-border) !important;
  background: rgba(0, 0, 0, 0.05) !important;
  color: var(--polaris-text-sub) !important;
  font-size: 11.5px;
  font-weight: 700;
  cursor: pointer;
  outline: none;
  transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
  margin-right: 4px;

  .theme-light & {
    background: #ffffff !important;
  }

  &:hover {
    background: var(--polaris-brand-hover) !important;
    border-color: var(--polaris-brand-color) !important;
    color: var(--polaris-text-main) !important;
  }

  &.pill-model {
    background: var(--polaris-brand-hover) !important;
    border-color: var(--polaris-brand-color) !important;
    color: var(--polaris-brand-color) !important;
  }

  &.is-active {
    background: var(--polaris-brand-hover) !important;
    border-color: var(--polaris-brand-color) !important;
    color: var(--polaris-brand-color) !important;
    
    &.pill-kb {
      background: rgba(16, 185, 129, 0.1) !important;
      border-color: #10b981 !important;
      color: #10b981 !important;
    }

    &.pill-workflow {
      background: rgba(168, 85, 247, 0.1) !important;
      border-color: #a855f7 !important;
      color: #a855f7 !important;
    }

    &.pill-search {
      background: var(--polaris-brand-hover) !important;
      border-color: var(--polaris-brand-color) !important;
      color: var(--polaris-brand-color) !important;
    }
  }
}

.input-modern-text-wrapper :deep(.el-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 !important;
  font-size: 14px !important;
  line-height: 1.65 !important;
  color: var(--polaris-text-main) !important;
  font-family: inherit !important;
  resize: none !important;
}

.input-modern-text-wrapper :deep(.el-textarea__inner::placeholder) {
  color: var(--polaris-text-sub) !important;
  opacity: 0.6;
}

.input-modern-tools-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid var(--polaris-inner-border);
  padding-top: 10px;
}

.tools-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.btn-attach-modern, .btn-voice-modern {
  font-size: 18px !important;
  color: var(--polaris-text-sub) !important;
  padding: 6px !important;
  margin: 0 !important;
  transition: all 0.2s !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  height: 30px !important;
  width: 30px !important;
  border-radius: 50% !important;

  &:hover {
    background: var(--polaris-brand-hover) !important;
    color: var(--polaris-brand-color) !important;
  }
}

/* 发送/停止按钮 */
.btn-send-modern {
  width: 32px !important;
  height: 32px !important;
  min-width: 32px !important;
  padding: 0 !important;
  border-radius: 50% !important;
  background: var(--polaris-brand-color) !important;
  border: none !important;
  color: #fff !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  transition: all 0.2s cubic-bezier(0.25, 0.8, 0.25, 1) !important;
  box-shadow: 0 4px 10px var(--polaris-brand-glow) !important;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    opacity: 0.9;
  }

  &:disabled {
    box-shadow: none !important;
    cursor: not-allowed !important;

    // 亮色模式下禁用状态
    .theme-light & {
      background: #e2e8f0 !important;
      color: #94a3b8 !important;
    }

    // 暗色模式下禁用状态
    .dark &,
    .theme-dark & {
      background: rgba(255, 255, 255, 0.06) !important;
      color: #475569 !important;
    }
  }
}

.btn-stop-modern {
  // 默认（主要是暗色模式）
  background: rgba(239, 68, 68, 0.15) !important;
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
  color: #f87171 !important; /* 亮红色，暗色中对比鲜明且柔和 */
  box-shadow: 0 0 12px rgba(239, 68, 68, 0.25) !important;

  // 亮色模式下
  .theme-light & {
    background: #ef4444 !important;
    border: none !important;
    color: #ffffff !important; /* 红色背景配白色图标，保证高对比度 */
    box-shadow: 0 4px 10px rgba(239, 68, 68, 0.35) !important;
  }

  // 显式指定暗色模式以确保优先级
  .dark &,
  .theme-dark & {
    background: rgba(239, 68, 68, 0.15) !important;
    border: 1px solid rgba(239, 68, 68, 0.3) !important;
    color: #f87171 !important;
    box-shadow: 0 0 12px rgba(239, 68, 68, 0.25) !important;
  }

  &:hover:not(:disabled) {
    opacity: 0.95 !important;
    
    .theme-light & {
      background: #dc2626 !important;
      border: none !important;
    }
    
    .dark &,
    .theme-dark & {
      background: rgba(239, 68, 68, 0.3) !important;
      border-color: rgba(239, 68, 68, 0.6) !important;
      color: #ffffff !important;
      box-shadow: 0 0 16px rgba(239, 68, 68, 0.4) !important;
    }
  }
}

/* 语音输入覆盖层 */
.voice-listening-overlay {
  position: absolute;
  inset: 0;
  background: var(--polaris-card-bg) !important;
  backdrop-filter: blur(25px);
  -webkit-backdrop-filter: blur(25px);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  border-radius: 16px;
  border: 1px solid var(--polaris-brand-color);
}

.voice-listening-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--polaris-text-main);
  display: flex;
  align-items: center;
  gap: 8px;
}

.input-hint {
  max-width: 820px;
  margin: 6px auto 0;
  font-size: 11px;
  color: var(--polaris-text-sub);
  text-align: center;
  font-weight: 500;
}

/* 滚动条 */
.conv-list::-webkit-scrollbar,
.messages-area::-webkit-scrollbar,
.popper-selector-list::-webkit-scrollbar {
  width: 4px;
}
.conv-list::-webkit-scrollbar-track,
.messages-area::-webkit-scrollbar-track,
.popper-selector-list::-webkit-scrollbar-track {
  background: transparent;
}
.conv-list::-webkit-scrollbar-thumb,
.messages-area::-webkit-scrollbar-thumb,
.popper-selector-list::-webkit-scrollbar-thumb {
  background: var(--polaris-inner-border);
  border-radius: 4px;
}

/* 下拉框气泡重写 */
:global(.pill-selector-popper) {
  border-radius: 16px !important;
  background: var(--polaris-card-bg) !important;
  backdrop-filter: blur(35px) !important;
  -webkit-backdrop-filter: blur(35px) !important;
  border: 1px solid var(--polaris-card-border) !important;
  box-shadow: 0 15px 45px rgba(0, 0, 0, 0.2) !important;
  padding: 12px !important;
}

:global(.pill-selector-popper .el-popover__title) {
  font-size: 11px;
  font-weight: 800;
  color: var(--polaris-text-sub) !important;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
  padding: 0 0 6px 0;
  border-bottom: 1px solid var(--polaris-inner-border) !important;
}

.popper-selector-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.popper-selector-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  background: transparent;
  color: var(--polaris-text-main) !important;
  font-size: 13px;
  transition: all 0.2s ease;
  user-select: none;

  &:hover {
    background: var(--polaris-brand-hover) !important;
    color: var(--polaris-brand-color) !important;
  }

  &.is-active {
    background: var(--polaris-brand-hover) !important;
    color: var(--polaris-brand-color) !important;
    font-weight: 700;
  }
}

.popper-selector-item .item-icon {
  font-size: 14px;
  color: var(--polaris-text-sub);
  flex-shrink: 0;
}

.popper-selector-item.is-active .item-icon {
  color: var(--polaris-brand-color) !important;
}

.popper-selector-item .item-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.popper-selector-item .check-icon {
  font-size: 13px;
  font-weight: bold;
  color: var(--polaris-brand-color);
  flex-shrink: 0;
}

/* 智能体工作流高级样式 */
.workflow-steps-container {
  background: rgba(0, 0, 0, 0.1) !important;
  border: 1px solid var(--polaris-inner-border) !important;
  border-radius: 12px;
  margin-bottom: 18px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.02);

  .theme-light & {
    background: #ffffff !important;
  }
}

.workflow-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.02);
  border-bottom: 1px solid var(--polaris-inner-border);
  font-size: 13.5px;
  font-weight: 700;
  color: var(--polaris-text-main);
}

.workflow-steps-list {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: transparent !important;
}

.workflow-step-item {
  display: flex;
  gap: 12px;
  position: relative;
  
  &:not(:last-child)::after {
    content: '';
    position: absolute;
    left: 9px;
    top: 22px;
    bottom: -22px;
    width: 2px;
    background: var(--polaris-inner-border);
  }

  &.success:not(:last-child)::after {
    background: #0d9488;
  }
}

.step-icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--polaris-bg);
  border: 2px solid var(--polaris-card-border);
  z-index: 1;
  font-size: 11px;
  color: var(--polaris-text-sub);
}

.workflow-step-item.running .step-icon {
  border-color: var(--polaris-brand-color);
  color: var(--polaris-brand-color);
  box-shadow: 0 0 0 4px var(--polaris-brand-glow);
}

.workflow-step-item.success .step-icon {
  border-color: #0d9488;
  background: #0d9488;
  color: #ffffff;
}

.step-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--polaris-text-main);
}

.workflow-step-item.running .step-name {
  color: var(--polaris-brand-color);
}

.step-tool-badge {
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.2);
  color: #10b981;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.step-thinking-box {
  background: rgba(0, 0, 0, 0.15);
  border-left: 3px solid var(--polaris-thinking-color);
  padding: 8px 12px;
  border-radius: 4px;
  margin-top: 4px;

  .theme-light & {
    background: #fafaf9;
  }

  .thinking-title {
    font-size: 10.5px;
    font-weight: 700;
    color: var(--polaris-thinking-color);
  }

  .thinking-text {
    font-size: 12px;
    color: var(--polaris-text-sub);
    line-height: 1.6;
  }
}

/* 审批面板 */
.approval-card-panel {
  margin-top: 18px;
  padding: 16px;
  background: var(--polaris-card-bg) !important;
  border-radius: 12px;
  border: 1px solid var(--polaris-card-border) !important;
  box-shadow: 0 8px 24px rgba(0,0,0,0.05);
}

.approval-title-text {
  color: var(--polaris-text-main) !important;
  font-weight: 700;
  font-size: 14px;
}

.approval-textarea :deep(.el-textarea__inner) {
  border-radius: 10px;
  border: 1px solid var(--polaris-card-border) !important;
  background-color: rgba(0, 0, 0, 0.2) !important;
  font-size: 13px;
  color: var(--polaris-text-main) !important;

  .theme-light & {
    background-color: #ffffff !important;
  }
}

.btn-reject {
  background-color: rgba(0,0,0,0.05) !important;
  border: 1px solid var(--polaris-card-border) !important;
  color: var(--polaris-text-sub) !important;
  font-weight: 600;

  &:hover {
    background-color: var(--polaris-danger-color) !important;
    border-color: var(--polaris-danger-color) !important;
    color: #fff !important;
  }
}

.btn-approve {
  background-color: #10b981 !important;
  border: none !important;
  color: #ffffff !important;
  font-weight: 600;

  &:hover {
    background-color: #059669 !important;
  }
}

.approval-status-panel {
  margin-top: 12px;
  padding: 10px 14px;
  background: rgba(16, 185, 129, 0.1) !important;
  border-radius: 8px;
  border: 1px solid rgba(16, 185, 129, 0.2) !important;
  font-size: 13px;
  color: #10b981 !important;
}

/* ========== 📄 通用精美报告预览局部面板 (不遮挡侧边栏) ========== */
.pretty-report-panel-local {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 90;
  background: var(--polaris-bg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: -4px 0 30px rgba(0, 0, 0, 0.05);
}

/* — 精致工具栏 — */
.report-toolbar-v2 {
  background: var(--polaris-card-bg);
  backdrop-filter: blur(25px);
  -webkit-backdrop-filter: blur(25px);
  border: 1px solid var(--polaris-card-border) !important;
  border-radius: 12px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.04) !important;
  z-index: 10;
  position: relative;
  padding: 10px 20px;
  max-width: 900px;
  width: calc(100% - 64px);
  margin: 16px auto 0;
}

.report-toolbar-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
  box-sizing: border-box;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.toolbar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-icon {
  font-size: 22px;
  color: #6366f1;
  filter: drop-shadow(0 2px 6px rgba(99, 102, 241, 0.2));
  .dark &, .theme-dark & { color: #818cf8; }
}

.brand-title {
  font-size: 15px;
  font-weight: 800;
  background: linear-gradient(135deg, #4f46e5, #8b5cf6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 0.04em;
  .dark &, .theme-dark & {
    background: linear-gradient(135deg, #a5b4fc, #c084fc);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

.toolbar-divider-line {
  width: 1px;
  height: 18px;
  background: #e2e8f0;
  .dark &, .theme-dark & { background: #334155; }
}

.toolbar-report-id {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.08em;
  font-family: 'SF Mono', 'Fira Code', monospace;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
  .dark &, .theme-dark & {
    background: #1e293b;
    color: #94a3b8;
  }
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-btn {
  border-radius: 8px !important;
  font-size: 12px !important;
  font-weight: 700 !important;
  padding: 8px 16px !important;
  height: 32px !important;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1) !important;
  border: 1px solid rgba(0, 0, 0, 0.06) !important;
  background: #fafafa !important;
  color: #475569 !important;

  .el-icon {
    font-size: 13px;
    margin-right: 4px;
  }

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
    background: #ffffff !important;
    color: #6366f1 !important;
    border-color: rgba(99, 102, 241, 0.3) !important;
  }

  .dark &, .theme-dark & {
    background: #1e293b !important;
    border-color: rgba(255, 255, 255, 0.06) !important;
    color: #cbd5e1 !important;
    &:hover {
      background: #243249 !important;
      color: #818cf8 !important;
      border-color: rgba(129, 140, 248, 0.3) !important;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
    }
  }

  // 特殊按钮分色
  &:nth-child(1) { // 打印 PDF
    background: rgba(99, 102, 241, 0.04) !important;
    border-color: rgba(99, 102, 241, 0.12) !important;
    color: #6366f1 !important;
    &:hover {
      background: #6366f1 !important;
      color: #ffffff !important;
      border-color: #6366f1 !important;
      box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
    }
  }

  &:nth-child(2) { // 导出 HTML
    background: rgba(16, 185, 129, 0.04) !important;
    border-color: rgba(16, 185, 129, 0.12) !important;
    color: #10b981 !important;
    &:hover {
      background: #10b981 !important;
      color: #ffffff !important;
      border-color: #10b981 !important;
      box-shadow: 0 4px 12px rgba(16, 185, 129, 0.2);
    }
  }
}

.toolbar-btn-close {
  border: 1px solid rgba(0, 0, 0, 0.06) !important;
  background: rgba(239, 68, 68, 0.04) !important;
  color: #ef4444 !important;
  width: 32px !important;
  height: 32px !important;
  transition: all 0.25s ease !important;

  &:hover {
    transform: rotate(90deg) scale(1.05);
    background: #ef4444 !important;
    border-color: #ef4444 !important;
    color: #ffffff !important;
    box-shadow: 0 4px 12px rgba(239, 68, 68, 0.2);
  }
  .dark &, .theme-dark & {
    border-color: rgba(255, 255, 255, 0.06) !important;
  }
}

/* — 报告预览主体 — */
.report-preview-page {
  background: var(--polaris-bg) !important;
  padding: 16px 32px 32px;
  overflow-y: auto;
  flex: 1;
}

/* — 报告仿真纸张设计 — */
.report-paper {
  background: var(--polaris-card-bg) !important;
  border: 1px solid var(--polaris-card-border) !important;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.04);
  color: var(--polaris-text-main);
  max-width: 900px;
  margin: 0 auto 40px;
  border-radius: 20px;
  overflow: hidden;
  transition: all 0.3s ease;
  position: relative;

  .dark &, .theme-dark & {
    box-shadow: 0 16px 48px rgba(0, 0, 0, 0.35);
  }
}

/* — 页眉装饰条 — */
.paper-header-v2 {
  position: relative;
}

.header-gradient-bar {
  height: 6px;
  background: linear-gradient(90deg, #6366f1, #a855f7, #ec4899);
}

.header-info-row {
  display: flex;
  justify-content: space-between;
  padding: 18px 40px 10px;
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
  letter-spacing: 0.05em;
  border-bottom: 1px solid rgba(0, 0, 0, 0.03);
  .dark &, .theme-dark & { border-bottom-color: rgba(255, 255, 255, 0.02); }
}

.confidential-tag-v2 {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #6366f1;
  .dark &, .theme-dark & { color: #818cf8; }
}

/* — 标题区与元信息 — */
.paper-title-area-v2 {
  padding: 20px 40px 16px;
  background: linear-gradient(to bottom, rgba(99, 102, 241, 0.02), transparent);
}

.paper-badge-v2 {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(99, 102, 241, 0.08);
  color: #6366f1;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.05em;
}

.paper-title-v2 {
  font-size: 22px;
  font-weight: 800;
  color: #0f172a;
  margin: 10px 0 12px;
  line-height: 1.35;
  .dark &, .theme-dark & { color: #f8fafc; }
}

.paper-meta-v2 {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  font-size: 12px;
  color: #64748b;
  .dark &, .theme-dark & { color: #94a3b8; }

  .meta-item {
    display: flex;
    align-items: center;
    gap: 6px;
    .el-icon {
      font-size: 14px;
      color: #94a3b8;
    }
  }
}

/* — 统计指标卡片 — */
.report-stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 0 40px;
  margin: 12px 0;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid rgba(0, 0, 0, 0.04);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.01);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
  }

  .dark &, .theme-dark & {
    background: rgba(30, 41, 59, 0.4);
    border-color: rgba(255, 255, 255, 0.04);
  }

  .stat-icon-box {
    width: 38px;
    height: 38px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
  }

  .stat-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .stat-value {
    font-size: 18px;
    font-weight: 800;
    line-height: 1.2;
    color: #0f172a;
    .dark &, .theme-dark & { color: #f8fafc; }
  }

  .stat-label {
    font-size: 11px;
    color: #64748b;
    .dark &, .theme-dark & { color: #94a3b8; }
  }

  /* 品牌色系统 */
  &.stat-card-indigo {
    border-left: 3.5px solid #6366f1;
    .stat-icon-box { background: rgba(99, 102, 241, 0.06); }
  }
  &.stat-card-blue {
    border-left: 3.5px solid #3b82f6;
    .stat-icon-box { background: rgba(59, 130, 246, 0.06); }
  }
  &.stat-card-emerald {
    border-left: 3.5px solid #10b981;
    .stat-icon-box { background: rgba(16, 185, 129, 0.06); }
  }
  &.stat-card-amber {
    border-left: 3.5px solid #f59e0b;
    .stat-icon-box { background: rgba(245, 158, 11, 0.06); }
  }
}

/* — 分隔点 — */
.paper-divider-v2 {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 16px 0;

  .divider-dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: #cbd5e1;
    .dark &, .theme-dark & { background: #475569; }
  }
}

/* — 图表区 — */
.report-chart-section-v2 {
  margin: 12px 40px;
  padding: 20px;
  border-radius: 16px;
  background: rgba(99, 102, 241, 0.01);
  border: 1px dashed rgba(99, 102, 241, 0.12);

  .dark &, .theme-dark & {
    background: rgba(99, 102, 241, 0.02);
    border-color: rgba(99, 102, 241, 0.2);
  }
}

/* — 📊 图表类型切换器美化（兼容亮暗色双主题，防止文字背景同色看不见） — */
.chart-type-switcher {
  :deep(.el-radio-group) {
    background: rgba(255, 255, 255, 0.04) !important;
    border: 1px solid rgba(255, 255, 255, 0.08) !important;
    padding: 3px;
    border-radius: 8px;
    display: inline-flex;
    align-items: center;

    .theme-light & {
      background: rgba(0, 0, 0, 0.04) !important;
      border: 1px solid rgba(0, 0, 0, 0.04) !important;
    }
  }

  :deep(.el-radio-button) {
    border: none !important;
    box-shadow: none !important;

    .el-radio-button__inner {
      background: transparent !important;
      border: none !important;
      color: var(--polaris-text-sub) !important;
      border-radius: 6px !important;
      font-size: 12px;
      font-weight: 700;
      padding: 6px 14px !important;
      height: auto !important;
      line-height: 1.5;
      transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
      box-shadow: none !important;
      display: inline-flex;
      align-items: center;
      gap: 4px;

      .theme-light & {
        color: #64748b !important;
      }

      &:hover {
        color: var(--polaris-brand-color) !important;
      }
    }

    &.is-active {
      .el-radio-button__inner {
        background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%) !important;
        color: #ffffff !important;
        box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3) !important;

        .theme-light & {
          background: #ffffff !important;
          color: #4f46e5 !important;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08) !important;
          border: 1px solid rgba(79, 70, 229, 0.12) !important;
        }
      }
    }
  }
}

.chart-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.chart-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 800;
  color: #1e3a8a;
  .dark &, .theme-dark & { color: #93c5fd; }

  .section-icon-wrapper {
    width: 24px;
    height: 24px;
    border-radius: 6px;
    background: rgba(99, 102, 241, 0.08);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #6366f1;
  }
}

.pretty-chart-box-v2 {
  height: 280px;
  width: 100%;
}

/* — 正文卡片包装器 — */
.paper-content-v2-container {
  padding: 8px 40px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* — 每一个正文独立卡片 — */
.report-content-card {
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(0, 0, 0, 0.03);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.01);
  border-radius: 16px;
  padding: 24px 28px;
  line-height: 1.85;
  font-size: 14px;
  color: #334155;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.04);
  }

  .dark &, .theme-dark & {
    background: rgba(30, 41, 59, 0.4);
    border-color: rgba(255, 255, 255, 0.04);
    color: #cbd5e1;
    &:hover {
      box-shadow: 0 8px 24px rgba(99, 102, 241, 0.08);
    }
  }

  :deep(h1) {
    font-size: 22px;
    font-weight: 800;
    color: #1e293b;
    margin: 32px 0 16px;
    padding-bottom: 10px;
    border-bottom: 2px solid rgba(99, 102, 241, 0.12);
    .dark &, .theme-dark & { color: #f1f5f9; border-bottom-color: rgba(99, 102, 241, 0.2); }
  }

  :deep(h2) {
    font-size: 18px;
    font-weight: 700;
    color: #1e3a8a;
    margin: 28px 0 14px;
    padding-left: 12px;
    border-left: 3px solid #6366f1;
    .dark &, .theme-dark & { color: #93c5fd; border-left-color: #818cf8; }
  }

  :deep(h3) {
    font-size: 16px;
    font-weight: 700;
    color: #334155;
    margin: 24px 0 10px;
    .dark &, .theme-dark & { color: #e2e8f0; }
  }

  :deep(p) {
    margin: 0 0 14px;
    color: #475569;
    .dark &, .theme-dark & { color: #cbd5e1; }
  }

  :deep(ul), :deep(ol) {
    margin: 8px 0 16px;
    padding-left: 24px;
    li {
      margin-bottom: 6px;
      color: #475569;
      .dark &, .theme-dark & { color: #cbd5e1; }
    }
  }

  :deep(blockquote) {
    margin: 16px 0;
    padding: 14px 20px;
    background: rgba(99, 102, 241, 0.04);
    border-left: 4px solid #6366f1;
    border-radius: 0 10px 10px 0;
    color: #475569;
    font-style: italic;
    .dark &, .theme-dark & {
      background: rgba(99, 102, 241, 0.06);
      border-left-color: #818cf8;
      color: #94a3b8;
    }
  }

  :deep(code) {
    font-family: 'SF Mono', 'Fira Code', monospace;
    font-size: 12.5px;
    padding: 2.5px 6px;
    background: rgba(99, 102, 241, 0.04);
    color: #6366f1;
    border-radius: 4px;
    .dark &, .theme-dark & {
      background: rgba(99, 102, 241, 0.08);
      color: #818cf8;
    }
  }

  :deep(pre) {
    background: #1e293b;
    color: #cbd5e1;
    padding: 16px 20px;
    border-radius: 12px;
    overflow-x: auto;
    margin: 18px 0;
    box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.2);
    code {
      background: transparent;
      color: #cbd5e1;
      padding: 0;
    }
  }

  :deep(hr) {
    border: none;
    height: 1px;
    background: linear-gradient(90deg, transparent, #e2e8f0, transparent);
    margin: 24px 0;
    .dark &, .theme-dark & { background: linear-gradient(90deg, transparent, #334155, transparent); }
  }

  :deep(strong) {
    font-weight: 700;
    color: #1e293b;
    .dark &, .theme-dark & { color: #f1f5f9; }
  }

  /* ===== 📊 智能报告可视化精致表格样式 ===== */
  :deep(.report-table) {
    width: 100%;
    border-collapse: separate;
    border-spacing: 0;
    margin: 20px 0;
    border-radius: 12px;
    overflow: hidden;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02);
    border: 1px solid rgba(0, 0, 0, 0.05);
    font-size: 13px;

    .dark &, .theme-dark & {
      box-shadow: 0 4px 25px rgba(0, 0, 0, 0.15);
      border: 1px solid rgba(255, 255, 255, 0.05);
    }

    thead {
      tr {
        background: linear-gradient(to bottom, #f8fafc, #f1f5f9);
        .dark &, .theme-dark & {
          background: linear-gradient(to bottom, rgba(30, 41, 59, 0.6), rgba(15, 23, 42, 0.8));
        }
      }
      th {
        padding: 12px 16px;
        font-weight: 700;
        color: #334155;
        text-align: left;
        border-bottom: 1.5px solid rgba(0, 0, 0, 0.06);
        letter-spacing: 0.03em;
        .dark &, .theme-dark & {
          color: #f1f5f9;
          border-bottom: 1.5px solid rgba(255, 255, 255, 0.08);
        }
      }
    }

    tbody {
      tr {
        background-color: #ffffff;
        transition: background-color 0.2s ease;
        &:last-child td { border-bottom: none; }
        &:hover { background-color: #f8fafc; }

        .dark &, .theme-dark & {
          background-color: rgba(30, 41, 59, 0.25);
          &:hover { background-color: rgba(255, 255, 255, 0.02); }
        }
      }
      td {
        padding: 12px 16px;
        color: #4b5563;
        border-bottom: 1px solid rgba(0, 0, 0, 0.04);
        vertical-align: middle;
        .dark &, .theme-dark & {
          color: #cbd5e1;
          border-bottom: 1px solid rgba(255, 255, 255, 0.04);
        }
      }
    }
  }

  /* ===== 🏷 可视化报告状态胶囊徽章 ===== */
  :deep(.report-badge) {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 4px 10px;
    border-radius: 8px;
    font-size: 11px;
    font-weight: 800;
    line-height: 1;
    letter-spacing: 0.02em;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.01);

    &.badge-danger {
      background-color: rgba(239, 68, 68, 0.08) !important;
      color: #ef4444 !important;
      border: 1px solid rgba(239, 68, 68, 0.16) !important;
    }
    &.badge-warning {
      background-color: rgba(245, 158, 11, 0.08) !important;
      color: #d97706 !important;
      border: 1px solid rgba(245, 158, 11, 0.16) !important;
    }
    &.badge-success {
      background-color: rgba(16, 185, 129, 0.08) !important;
      color: #10b981 !important;
      border: 1px solid rgba(16, 185, 129, 0.16) !important;
    }
    &.badge-primary {
      background-color: rgba(59, 130, 246, 0.08) !important;
      color: #3b82f6 !important;
      border: 1px solid rgba(59, 130, 246, 0.16) !important;
    }
  }
}

/* — 页脚 — */
.paper-footer-v2 {
  padding: 24px 40px 28px;
  text-align: center;
}

.footer-gradient-line {
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(99, 102, 241, 0.15), transparent);
  margin-bottom: 14px;
}

.footer-disclaimer {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 6px;
  letter-spacing: 0.02em;
}

.footer-brand {
  font-size: 12px;
  color: #64748b;
  strong {
    color: #6366f1;
    font-weight: 700;
  }
  .dark &, .theme-dark & { color: #94a3b8; }
}

/* ==========================================================================
   智能分析报告操作卡片美化样式
   ========================================================================== */
.report-action-card {
  margin-top: 14px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.12) 0%, rgba(168, 85, 247, 0.06) 100%) !important;
  border: 1px solid rgba(99, 102, 241, 0.28) !important;
  border-radius: 12px;
  padding: 14px 18px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  position: relative;
  overflow: hidden;
  box-sizing: border-box;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05) !important;

  .theme-light & {
    background: linear-gradient(135deg, rgba(99, 102, 241, 0.06) 0%, rgba(168, 85, 247, 0.03) 100%) !important;
    border: 1px solid rgba(99, 102, 241, 0.16) !important;
    box-shadow: 0 4px 12px rgba(99, 102, 241, 0.03) !important;
  }

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.08), transparent);
    transform: translateX(-100%);
    transition: transform 0.6s ease;
  }

  &:hover {
    transform: translateY(-2px);
    border-color: rgba(99, 102, 241, 0.45) !important;
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.18) !important;

    &::before {
      transform: translateX(100%);
    }

    .action-arrow {
      transform: translateX(4px);
    }
    
    .report-card-icon-wrapper {
      transform: scale(1.05);
      box-shadow: 0 4px 14px rgba(99, 102, 241, 0.45);
    }
  }

  .report-card-body {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    width: 100%;
    flex-wrap: wrap;
  }

  .report-card-left {
    display: flex;
    align-items: center;
    gap: 14px;
    flex: 1;
    min-width: 240px;
  }

  .report-card-icon-wrapper {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 10px rgba(99, 102, 241, 0.25);
    transition: all 0.3s ease;
    flex-shrink: 0;

    .report-icon-svg {
      font-size: 20px;
      color: #ffffff;
    }
  }

  .report-card-info {
    display: flex;
    flex-direction: column;
    gap: 4px;
    text-align: left;
  }

  .report-card-title {
    font-size: 14px;
    font-weight: 700;
    color: var(--polaris-text-main);
    display: flex;
    align-items: center;
    gap: 8px;
    line-height: 1.4;
  }

  .report-pill-badge {
    font-size: 10px;
    font-weight: 600;
    color: #ffffff;
    background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
    padding: 2px 6px;
    border-radius: 6px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    box-shadow: 0 2px 4px rgba(79, 70, 229, 0.2);
  }

  .report-card-desc {
    font-size: 12px;
    color: var(--polaris-text-sub);
    line-height: 1.4;
    opacity: 0.85;
  }

  .report-card-right {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 700;
    color: var(--polaris-brand-color);
    flex-shrink: 0;

    .action-arrow {
      font-size: 12px;
      transition: transform 0.3s ease;
    }
  }
}

.popper-group-header {
  font-size: 11px;
  font-weight: 700;
  color: #8b5cf6;
  padding: 8px 12px 4px 12px;
  letter-spacing: 0.5px;
  display: flex;
  align-items: center;
  gap: 6px;
  border-top: 1px solid rgba(226, 232, 240, 0.6);
  margin-top: 6px;
  &:first-child {
    border-top: none;
    margin-top: 0;
  }
}

/* ==========================================================================
   AI 深度重塑全屏暗色磨砂蒙层与居中浮动弹窗（完全隔离底层原页面）
   ========================================================================== */
.ai-refine-modal-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 3000;
  background: rgba(15, 23, 42, 0.65);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.ai-refine-stepper-panel {
  width: 100%;
  max-width: 640px;
  margin: 0;
  padding: 24px 28px;
  background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 60%, #311b92 100%);
  border: 1px solid rgba(168, 85, 247, 0.45);
  border-radius: 16px;
  box-shadow: 0 25px 60px -15px rgba(0, 0, 0, 0.65), 0 0 35px rgba(124, 58, 237, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.2);
  color: #ffffff;
  position: relative;
  overflow: hidden;
  animation: modal-pop-in 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: -100%;
    width: 100%;
    height: 2px;
    background: linear-gradient(90deg, transparent, #a855f7, #3b82f6, transparent);
    animation: shimmer-top-bar 2.5s infinite linear;
  }
}

@keyframes modal-pop-in {
  0% {
    opacity: 0;
    transform: scale(0.92) translateY(12px);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

@keyframes shimmer-top-bar {
  0% { left: -100%; }
  100% { left: 100%; }
}

.stepper-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;

  .stepper-title {
    display: flex;
    align-items: center;
    gap: 10px;
    font-weight: 700;
    font-size: 15px;
    color: #f3e8ff;
    letter-spacing: 0.3px;

    .title-spinner {
      font-size: 18px;
      color: #c084fc;
    }
  }

  .stepper-tag {
    font-size: 12px;
    font-weight: 600;
    color: #e9d5ff;
    background: rgba(168, 85, 247, 0.25);
    border: 1px solid rgba(168, 85, 247, 0.4);
    padding: 3px 10px;
    border-radius: 12px;
    letter-spacing: 0.5px;
  }
}

.stepper-body {
  display: flex;
  align-items: center;
  justify-content: space-around;
  gap: 12px;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(10px);
  padding: 12px 18px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);

  .step-item {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: #94a3b8;
    transition: all 0.3s ease;

    .step-icon {
      font-size: 16px;
    }

    .done-icon {
      color: #34d399;
    }

    .loading-icon {
      color: #c084fc;
    }

    .dot-icon {
      display: inline-block;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #475569;
    }

    &.step-active {
      color: #f3e8ff;
      font-weight: 700;
    }

    &.step-done {
      color: #34d399;
      font-weight: 600;
    }
  }

  .step-arrow {
    color: #818cf8;
    font-weight: 700;
    font-size: 12px;
    opacity: 0.8;
  }
}

/* 现代化 Tab 切换 + 搜索 + 固定高度平滑滚动 Popover */
:global(.popper-workflow-modern) {
  padding: 10px !important;
  border-radius: 14px !important;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15) !important;
}

.popover-modern-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.popover-tabs-nav {
  display: flex;
  background: rgba(0, 0, 0, 0.04);
  padding: 3px;
  border-radius: 10px;
  gap: 3px;

  :global(.dark) &,
  :global(.theme-dark) & {
    background: rgba(255, 255, 255, 0.06);
  }

  .tab-nav-btn {
    flex: 1;
    border: none;
    background: transparent;
    font-size: 11.5px;
    font-weight: 600;
    color: var(--el-text-color-secondary);
    padding: 6px 0;
    border-radius: 8px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 4px;
    transition: all 0.2s ease;

    &:hover {
      color: var(--el-text-color-primary);
    }

    &.active {
      background: var(--el-bg-color, #ffffff);
      color: #6366f1;
      font-weight: 700;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);

      :global(.dark) &,
      :global(.theme-dark) & {
        background: #1e293b;
        color: #818cf8;
      }
    }
  }
}

.popover-tab-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.auto-mode-card {
  background: rgba(99, 102, 241, 0.05);
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: 10px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(99, 102, 241, 0.09);
  }

  &.active {
    border-color: #6366f1;
    background: rgba(99, 102, 241, 0.12);
  }

  .card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;

    .head-left {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #6366f1;
      font-weight: 700;
      font-size: 13px;
    }

    .check-icon {
      color: #6366f1;
      font-weight: bold;
    }
  }

  .card-desc {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    line-height: 1.4;
  }
}

.popover-search-row {
  margin-bottom: 2px;
}

.popover-scroll-list {
  max-height: 240px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-right: 2px;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.15);
    border-radius: 4px;
  }

  .empty-hint {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
    text-align: center;
    padding: 20px 0;
  }

  .popover-list-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 10px;
    border-radius: 8px;
    cursor: pointer;
    transition: background 0.2s ease;

    &:hover {
      background: var(--el-fill-color-light, rgba(0, 0, 0, 0.04));
    }

    &.active {
      background: rgba(99, 102, 241, 0.1);
      color: #6366f1;
      font-weight: 600;

      .item-icon {
        color: #6366f1;
      }
    }

    .item-left {
      display: flex;
      align-items: center;
      gap: 8px;
      overflow: hidden;

      .item-icon {
        font-size: 14px;
        color: var(--el-text-color-secondary);
        flex-shrink: 0;
      }

      .item-name {
        font-size: 12.5px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }

    .check-icon {
      color: #6366f1;
      font-weight: bold;
      flex-shrink: 0;
    }
  }
}

/* 顶部 Header 状态 Tag 强力修正（彻底解决 Element Plus el-tag 内部文字与图标换行错位 Bug） */
.header-tags-row {
  display: flex !important;
  align-items: center !important;
  gap: 8px !important;
  flex-wrap: nowrap !important;
}

.header-tags-row :deep(.el-tag),
.header-tags-row .el-tag {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  height: 28px !important;
  line-height: 28px !important;
  padding: 0 10px !important;
  white-space: nowrap !important;
  vertical-align: middle !important;
}

.header-tags-row :deep(.el-tag__content),
.header-tags-row .el-tag__content {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  gap: 4px !important;
  height: 100% !important;
  line-height: 1 !important;
  white-space: nowrap !important;
}

.header-tags-row :deep(.tag-icon),
.header-tags-row .tag-icon {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  margin-right: 2px !important;
  font-size: 14px !important;
}
</style>

<!-- 打印专用全局样式，确保导出/打印 PDF 时仅渲染报告主体并隐藏系统侧边栏、顶部导航与工具栏 -->
<style>
@media print {
  /* 1. 将 body 内所有无关系统元素隐藏 */
  body * {
    visibility: hidden !important;
  }

  /* 2. 仅恢复报告打印区域及其子元素可见 */
  #report-print-area,
  #report-print-area * {
    visibility: visible !important;
  }

  /* 3. 将报告区域绝对定位至打印窗口最左上方，满幅无阴影铺开 */
  #report-print-area {
    position: absolute !important;
    left: 0 !important;
    top: 0 !important;
    width: 100% !important;
    margin: 0 !important;
    padding: 0 !important;
    background: #ffffff !important;
    box-shadow: none !important;
  }

  .report-paper {
    width: 100% !important;
    max-width: 100% !important;
    margin: 0 !important;
    padding: 10mm 15mm !important;
    box-shadow: none !important;
    border: none !important;
    border-radius: 0 !important;
    background: #ffffff !important;
  }

  /* 4. 隐藏报告工具栏和关闭按钮 */
  .report-toolbar-v2,
  .report-toolbar-inner,
  .toolbar-btn-close {
    display: none !important;
    visibility: hidden !important;
  }

  /* 5. 解除外层 Layout 容器的高度与 overflow 截断，避免长报告多页截断 */
  html, body, #app, .app-wrapper, .main-container, .app-main, .chat-container, .report-drawer-body, .el-overlay {
    height: auto !important;
    min-height: auto !important;
    overflow: visible !important;
    position: static !important;
    background: #ffffff !important;
    padding: 0 !important;
    margin: 0 !important;
  }

  /* 6. 页面边距与纸张规范优化 */
  @page {
    size: A4 portrait;
    margin: 10mm;
  }
}
</style>
