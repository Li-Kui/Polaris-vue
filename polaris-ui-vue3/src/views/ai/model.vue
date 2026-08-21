<template>
  <div class="app-container ai-model-manager no-sidebar-manage-wrap">
    <!-- 1. 列表模式/表格模式视图 -->
    <div v-if="viewMode === 'card' || viewMode === 'table'" class="content-inner">
      <!-- 顶部高效筛选与操作栏 -->
      <div class="polaris-filter-card">
        <el-form :model="queryParams" ref="queryForm" :inline="true" class="polaris-filter-form">
          <el-form-item label="模型名称">
            <el-input v-model="queryParams.name" placeholder="请输入配置名称" clearable @keyup.enter="handleQuery" style="width: 180px;"/>
          </el-form-item>
          <el-form-item label="提供商">
            <el-select v-model="queryParams.provider" placeholder="请选择提供商" clearable style="width: 150px;">
              <el-option label="DeepSeek" value="deepseek"/>
              <el-option label="阿里云通义" value="dashscope"/>
              <el-option label="OpenAI" value="openai"/>
              <el-option label="Ollama (本地)" value="ollama"/>
              <el-option label="火山引擎 Ark" value="ark"/>
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery" class="polaris-query-btn">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery" class="polaris-reset-btn">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 中间操作行与数据容器 (被毛玻璃框包围起来) -->
      <div class="synapse-card-grid-wrapper polaris-table-card">
        <!-- 全局 SVG 线性渐变定义 -->
        <svg style="width: 0; height: 0; position: absolute;" aria-hidden="true" focusable="false">
          <defs>
            <linearGradient id="temp-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#8b5cf6" />
              <stop offset="100%" stop-color="#ec4899" />
            </linearGradient>
            <linearGradient id="tool-gradient-active" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#10b981" />
              <stop offset="100%" stop-color="#06b6d4" />
            </linearGradient>
            <linearGradient id="tool-gradient-error" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#ef4444" />
              <stop offset="100%" stop-color="#f97316" />
            </linearGradient>
          </defs>
        </svg>

        <!-- 中间共享操作行：新增模型按钮与视图切换器 -->
        <div class="matrix-actions-bar" style="margin-top: 0; width: 100%;">
          <div class="actions-left">
            <el-button type="primary" class="action-btn-primary" icon="Plus" @click="handleAdd">
              新增模型
            </el-button>
          </div>
          <div class="actions-right">
            <div class="view-mode-toggle-row">
              <button
                :class="['toggle-view-btn', { active: viewMode === 'card' }]"
                @click="viewMode = 'card'"
              >
                🧠 三维星图
              </button>
              <button
                :class="['toggle-view-btn', { active: viewMode === 'table' }]"
                @click="viewMode = 'table'"
              >
                📊 经典表格
              </button>
            </div>
          </div>
        </div>

        <!-- 动态视图包裹层 -->
        <div v-loading="loading" class="model-view-dynamic-container">
          <!-- 无模型数据空状态 -->
          <div v-if="modelList.length === 0" class="empty-state">
            <div class="empty-icon">🤖</div>
            <p>暂无模型配置，点击上方“新增模型”按钮开始配置您的 AI 模型吧！</p>
          </div>

          <template v-else>
            <!-- 🧠 三维北辰星图卡片视图 -->
            <div v-if="viewMode === 'card'" class="synapse-card-grid">
              <div
                v-for="item in modelList"
                :key="item.id"
                :class="['synapse-glass-card', item.status === '1' ? (item.isDefault === '1' ? (item.modelType === 'CHAT' ? 'status-border-chat' : (item.modelType === 'EMBEDDING' ? 'status-border-embed' : 'status-border-image')) : 'status-border-active') : 'status-border-inactive']"
              >
                <!-- 卡片流光反射 -->
                <div class="card-shimmer-ray"></div>
                
                <div class="card-header-row">
                  <span :class="['card-code', getProviderTagType(item.provider)]">
                    <span v-if="item.provider === 'deepseek'">🌀 </span>
                    <span v-else-if="item.provider === 'dashscope'">✦ </span>
                    <span v-else-if="item.provider === 'openai'">⚛ </span>
                    <span v-else-if="item.provider === 'ollama'">🦙 </span>
                    <span v-else-if="item.provider === 'ark'">🌋 </span>
                    {{ getProviderLabel(item.provider) }}
                  </span>
                  
                  <!-- 雷达多层呼吸灯 -->
                  <div class="status-cell clickable-status" @click="toggleStatus(item)">
                    <span :class="['pulse-light-ripple', item.status === '1' ? 'pulse-active' : 'pulse-error']"></span>
                    <span class="status-badge-text" :class="item.status === '1' ? 'text-active' : 'text-error'">
                      {{ item.status === '1' ? '正常' : '禁用' }}
                    </span>
                  </div>
                </div>

                <div class="card-body">
                  <h4 class="card-name" :title="item.name">{{ item.name }}</h4>
                  <div class="card-model-chip-badge">
                    <el-icon class="model-cpu-icon"><cpu /></el-icon>
                    <span>{{ item.modelName }}</span>
                  </div>
                  
                  <div class="param-row code-info-row">
                    <span class="param-label"><el-icon><key /></el-icon> API Key</span>
                    <span class="param-val code-text mask-text">{{ item.apiKey ? '••••••••••••••••' : '未设置' }}</span>
                  </div>
                  <div v-if="item.baseUrl" class="param-row code-info-row">
                    <span class="param-label"><el-icon><link-icon /></el-icon> API 地址</span>
                    <span :title="item.baseUrl" class="param-val url-text">{{ item.baseUrl }}</span>
                  </div>

                  <!-- 环形仪表盘 -->
                  <div class="card-stats-gauges">
                    <!-- 温度仪表 -->
                    <div class="radial-gauge-item">
                      <div class="radial-circle-box">
                        <svg viewBox="0 0 36 36">
                          <path
                            class="circle-bg"
                            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          />
                          <path
                            class="circle-fill"
                            stroke="url(#temp-gradient)"
                            :stroke-dasharray="`${(item.temperature || 0.7) * 50}, 100`"
                            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          />
                        </svg>
                        <div class="radial-gauge-text">{{ item.temperature }}</div>
                      </div>
                      <span class="gauge-label">随机温度</span>
                    </div>
                    
                    <!-- Max Tokens 载荷 -->
                    <div class="radial-gauge-item">
                      <div class="radial-circle-box">
                        <svg viewBox="0 0 36 36">
                          <path
                            class="circle-bg"
                            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          />
                          <path
                            class="circle-fill"
                            stroke="url(#tool-gradient-active)"
                            :stroke-dasharray="`${Math.min(((item.maxTokens || 2048) / 8192) * 100, 100)}, 100`"
                            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          />
                        </svg>
                        <div class="radial-gauge-text" style="font-size: 9px;">{{ item.maxTokens }}</div>
                      </div>
                      <span class="gauge-label">Max Tokens</span>
                    </div>
                  </div>

                  <!-- 技术参数 Pill 标签 -->
                  <div class="param-pill-row">
                    <el-tag v-if="item.enableThinking === '1'" effect="plain" size="small" type="warning" class="tech-pill">
                      思考: {{ getReasoningEffortLabel(item.reasoningEffort) }}
                    </el-tag>
                    <el-tag v-if="item.enableSearch === '1'" effect="plain" size="small" type="success" class="tech-pill">
                      联网搜索
                    </el-tag>
                    <template v-if="!isPlatform">
                      <el-tag v-if="item.deptId" effect="plain" size="small" type="primary" class="tech-pill">
                        {{ getDeptName(item.deptId) }}
                      </el-tag>
                      <el-tag v-else effect="plain" size="small" type="info" class="tech-pill">
                        全局共享
                      </el-tag>
                    </template>
                    <template v-if="item.modelType === 'IMAGE'">
                      <el-tag
                        v-for="cap in jsonToArray(item.imageCapabilities)"
                        :key="cap"
                        effect="plain"
                        size="small"
                        type="warning"
                        class="tech-pill"
                      >{{ getImageCapabilityLabel(cap) }}</el-tag>
                    </template>
                  </div>

                  <!-- 系统提示词预览 (Mac 代码终端自适应风格) -->
                  <div v-if="item.systemPrompt" class="prompt-preview-terminal">
                    <div class="terminal-header">
                      <div class="terminal-dots">
                        <span class="dot-red"></span>
                        <span class="dot-yellow"></span>
                        <span class="dot-green"></span>
                      </div>
                      <span class="terminal-title">System Prompt</span>
                      <button class="terminal-copy-btn" @click.stop="copyPrompt(item.systemPrompt)" title="复制系统提示词">
                        <el-icon><document-copy /></el-icon>
                      </button>
                    </div>
                    <div class="terminal-body">
                      <p :title="item.systemPrompt" class="prompt-content-text">{{ item.systemPrompt }}</p>
                    </div>
                  </div>
                </div>

                <!-- 卡片底部默认状态与操作 -->
                <div class="card-footer-row-actions">
                  <div class="default-tags-area">
                    <!-- 聊天模型默认状态 -->
                    <template v-if="item.modelType === 'CHAT'">
                      <el-tag v-if="item.isDefault === '1'" class="active-tag-chat" effect="dark" size="small" type="success">
                        <el-icon><chat-dot-round /></el-icon>
                        <span>默认聊天</span>
                      </el-tag>
                      <el-button v-else class="footer-action-btn" link size="small" @click="handleSetDefaultChat(item)">
                        设为默认聊天
                      </el-button>
                    </template>

                    <!-- 向量模型默认状态 -->
                    <template v-else-if="item.modelType === 'EMBEDDING'">
                      <el-tag v-if="item.isDefault === '1'" class="active-tag-embed" effect="dark" size="small" type="primary">
                        <el-icon><collection /></el-icon>
                        <span>默认向量</span>
                      </el-tag>
                      <el-button v-else class="footer-action-btn color-primary" link size="small" @click="handleSetDefaultEmbedding(item)">
                        设为默认向量
                      </el-button>
                    </template>

                    <!-- 绘图模型默认状态 -->
                    <template v-else-if="item.modelType === 'IMAGE'">
                      <el-tag v-if="item.isDefault === '1'" class="active-tag-chat" effect="dark" size="small" type="warning">
                        <span>默认绘图</span>
                      </el-tag>
                      <el-button v-else class="footer-action-btn color-warning" link size="small" @click="handleSetDefaultImage(item)">
                        设为默认绘图
                      </el-button>
                    </template>
                  </div>

                  <div class="action-buttons-row">
                    <el-button type="primary" link class="card-op-edit-pill" @click="handleUpdate(item)">
                      <el-icon><edit /></el-icon>
                      <span>编辑</span>
                    </el-button>
                    <el-button type="danger" link class="card-op-delete-pill" @click="handleDelete(item)">
                      <el-icon><delete /></el-icon>
                      <span>删除</span>
                    </el-button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 📊 经典数据表格视图 -->
            <div v-else-if="viewMode === 'table'" class="polaris-table-card-table-wrap">
              <el-table :data="modelList" class="polaris-el-table">
                <el-table-column label="配置名称" prop="name" min-width="150" :show-overflow-tooltip="true" />
                <el-table-column label="提供商" width="120">
                  <template #default="{ row }">
                    <span :class="['card-code', getProviderTagType(row.provider)]">{{ getProviderLabel(row.provider) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="模型名称" prop="modelName" width="160" :show-overflow-tooltip="true">
                  <template #default="{ row }">
                    <span class="model-badge">{{ row.modelName }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="最大 Tokens" prop="maxTokens" width="110" />
                <el-table-column label="随机温度" prop="temperature" width="100" />
                <el-table-column label="思考模式" width="120">
                  <template #default="{ row }">
                    <el-tag v-if="row.enableThinking === '1'" size="small" type="warning">开启 ({{ getReasoningEffortLabel(row.reasoningEffort) }})</el-tag>
                    <el-tag v-else size="small" type="info">关闭</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="运行状态" width="110">
                  <template #default="{ row }">
                    <div class="status-cell clickable-status" @click="toggleStatus(row)">
                      <span :class="['pulse-light-ripple', row.status === '1' ? 'pulse-active' : 'pulse-error']"></span>
                      <span class="status-label" :class="row.status === '1' ? 'text-active' : 'text-error'">
                        {{ row.status === '1' ? '正常' : '禁用' }}
                      </span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="系统默认" width="200">
                  <template #default="{ row }">
                    <div class="default-tags-cell">
                      <!-- 聊天模型默认控制 -->
                      <template v-if="row.modelType === 'CHAT'">
                        <el-tag v-if="row.isDefault === '1'" size="small" type="success">默认聊天</el-tag>
                        <el-button v-else link size="small" class="op-btn-default-set" @click="handleSetDefaultChat(row)">设为默认聊天</el-button>
                      </template>

                      <!-- 向量模型默认控制 -->
                      <template v-else-if="row.modelType === 'EMBEDDING'">
                        <el-tag v-if="row.isDefault === '1'" size="small" type="primary">默认向量</el-tag>
                        <el-button v-else link size="small" class="op-btn-default-set-embed" @click="handleSetDefaultEmbedding(row)">设为默认向量</el-button>
                      </template>

                      <!-- 绘图模型默认控制 -->
                      <template v-else-if="row.modelType === 'IMAGE'">
                        <el-tag v-if="row.isDefault === '1'" size="small" type="warning">默认绘图</el-tag>
                        <el-button v-else link size="small" class="op-btn-default-set-image" @click="handleSetDefaultImage(row)">设为默认绘图</el-button>
                      </template>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right">
                  <template #default="{ row }">
                    <div class="table-op-actions">
                      <el-button type="primary" link class="op-btn-edit" @click="handleUpdate(row)">编辑</el-button>
                      <el-button type="danger" link class="op-btn-delete" @click="handleDelete(row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <!-- 分页组件 -->
            <pagination
              v-show="total > 0"
              :total="total"
              v-model:page="queryParams.pageNum"
              v-model:limit="queryParams.pageSize"
              @pagination="getList"
            />
          </template>
        </div>
      </div>
    </div>

    <!-- 2. 独立整屏模型配置工作台 (取代原本的弹窗式配置，提供沉浸式极客面板) -->
    <div v-else-if="viewMode === 'edit'" key="edit-view" class="agent-workbench-container">
      <!-- 顶部控制 Header -->
      <div class="workbench-header">
        <div class="header-left">
          <el-button icon="Back" size="small" circle @click="cancel" class="back-btn"></el-button>
          <span class="workbench-title-text">{{ form.id ? '配置 AI 模型核心参数' : '创建全新 AI 模型配置' }}</span>
        </div>
        <div class="header-right">
          <el-button size="small" icon="Close" @click="cancel" class="cancel-action-btn">取消返回</el-button>
          <el-button type="primary" size="small" class="action-btn-primary" icon="CircleCheck" @click="submitForm">保存配置</el-button>
        </div>
      </div>

      <!-- 双栏配置工作区 -->
      <el-form ref="form" :model="form" :rules="rules" label-position="top" class="workbench-body">
        <!-- 左栏：基础对接参数 (面板) -->
        <div class="editor-left-pane">
          <div class="pane-card">
            <div class="pane-card-header">
              <span class="header-dot"></span>
              <h5>基础对接参数</h5>
            </div>
            
            <el-form-item label="配置名称" prop="name">
              <el-input v-model="form.name" placeholder="例如：DeepSeek官方对话、阿里云通用向量"/>
            </el-form-item>
            
            <el-form-item label="连接方式" prop="accessMode">
              <el-radio-group v-model="form.accessMode">
                <el-radio label="direct">🔗 直连厂商</el-radio>
                <el-radio label="relay">🔀 中转站</el-radio>
              </el-radio-group>
              <div v-if="form.accessMode === 'relay'" style="font-size: 12px; color: #909399; margin-top: 4px;">
                💡 中转站模式下，提供商请选择中转站背后实际对接的厂商
              </div>
            </el-form-item>

            <el-form-item label="提供商" prop="provider">
              <el-select v-model="form.provider" placeholder="请选择提供商" style="width: 100%;">
                <el-option label="DeepSeek" value="deepseek"/>
                <el-option label="阿里云通义" value="dashscope"/>
                <el-option label="OpenAI" value="openai"/>
                <el-option v-if="form.accessMode !== 'relay'" label="Ollama (本地部署)" value="ollama"/>
                <el-option label="火山引擎 Ark" value="ark"/>
              </el-select>
            </el-form-item>

            <el-form-item label="API Key" prop="apiKey">
              <el-input v-model="form.apiKey" placeholder="输入 API Key（脱敏存储）" show-password/>
            </el-form-item>

            <el-form-item label="API Base URL">
              <el-input v-model="form.baseUrl" :placeholder="form.provider === 'ollama' ? 'http://localhost:11434' : '不填则使用官方默认地址'"/>
            </el-form-item>

            <el-form-item label="模型名称" prop="modelName">
              <div style="display: flex; gap: 8px; width: 100%;">
                <el-select
                  v-model="form.modelName"
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  :placeholder="remoteModelList.length > 0 ? '从列表选择或手动输入' : '手动输入或点右侧获取'"
                  style="flex: 1;"
                >
                  <el-option
                    v-for="m in remoteModelList"
                    :key="m"
                    :label="m"
                    :value="m"
                  />
                </el-select>
                <el-button
                  :icon="Connection"
                  :loading="fetchingModels"
                  :disabled="!canFetchModels"
                  @click="handleFetchModels"
                  class="action-btn-primary"
                  style="flex-shrink: 0;"
                >
                  {{ fetchingModels ? '获取中...' : '获取模型' }}
                </el-button>
              </div>
            </el-form-item>

            <el-form-item v-if="!isPlatform" label="归属部门">
              <el-tree-select
                v-model="form.deptId"
                :data="deptOptions"
                :props="{ value: 'id', label: 'label', children: 'children' }"
                value-key="id"
                placeholder="留空表示全局共享模型"
                clearable
                check-strictly
                style="width: 100%;"
              />
            </el-form-item>
          </div>
        </div>

        <!-- 右栏：高级参数负载 -->
        <div class="editor-right-pane">
          <!-- 属性面板一：交互载荷 -->
          <div class="pane-card card-space-margin">
            <div class="pane-card-header">
              <span class="header-dot purple-dot"></span>
              <h5>基础交互载荷</h5>
            </div>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="Max Tokens" prop="maxTokens">
                  <el-input-number v-model="form.maxTokens" :max="32768" :min="256" style="width: 100%;" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最大历史消息数" prop="maxHistoryMessages">
                  <el-input-number v-model="form.maxHistoryMessages" :max="100" :min="1" style="width: 100%;" />
                </el-form-item>
              </el-col>
              <el-col :span="24" style="margin-top: 10px;">
                <el-form-item label="随机温度 (Temperature)" prop="temperature">
                  <div class="temp-slider-box">
                    <el-slider
                      v-model="form.temperature"
                      :max="2"
                      :step="0.05"
                      show-input
                      input-size="small"
                    />
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
          </div>

          <!-- 属性面板二：系统扩展特性 -->
          <div class="pane-card card-space-margin">
            <div class="pane-card-header">
              <span class="header-dot teal-dot"></span>
              <h5>高级扩展特性</h5>
            </div>
            <el-row :gutter="20">
              <!-- 思考模式 (Reasoning) -->
              <el-col :span="12">
                <el-form-item label="思考模式 (Reasoning)" prop="enableThinking">
                  <el-radio-group v-model="form.enableThinking">
                    <el-radio label="1">开启</el-radio>
                    <el-radio label="0">关闭</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col v-if="form.enableThinking === '1'" :span="12">
                <el-form-item label="思考强度 (Effort)" prop="reasoningEffort">
                  <el-select v-model="form.reasoningEffort" placeholder="请选择思考强度" style="width: 100%;">
                    <el-option label="默认" value=""/>
                    <el-option label="低强度 (low)" value="low"/>
                    <el-option label="中强度 (medium)" value="medium"/>
                    <el-option label="高强度 (high)" value="high"/>
                    <el-option label="极大强度 (max)" value="max"/>
                  </el-select>
                </el-form-item>
              </el-col>

              <!-- 模型用途与默认值 -->
              <el-col :span="12" style="margin-top: 10px;">
                <el-form-item label="模型用途" prop="modelType">
                  <el-select v-model="form.modelType" placeholder="请选择模型用途" style="width: 100%;">
                    <el-option label="文本对话模型 (CHAT)" value="CHAT"/>
                    <el-option label="向量检索模型 (EMBEDDING)" value="EMBEDDING"/>
                    <el-option label="图像生成模型 (IMAGE)" value="IMAGE"/>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12" style="margin-top: 10px;">
                <el-form-item label="在途并发上限" prop="maxConcurrency">
                  <el-input-number v-model="form.maxConcurrency" :min="1" :max="50" placeholder="留空则按厂商默认" style="width: 100%;" />
                </el-form-item>
              </el-col>
              <el-col v-if="form.modelType !== 'EMBEDDING'" :span="12" style="margin-top: 10px;">
                <el-form-item label="设为默认模型" prop="isDefault">
                  <el-radio-group v-model="form.isDefault">
                    <el-radio label="1">是</el-radio>
                    <el-radio label="0">否</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>

              <!-- 大模型启用工具箱 -->
              <el-col :span="24" v-if="form.modelType === 'CHAT'" style="margin-top: 10px;">
                <el-form-item label="启用大模型工具" prop="enabledTools">
                  <el-checkbox-group v-model="enabledToolsArray">
                    <el-checkbox label="web_search">网络实时搜索</el-checkbox>
                    <el-checkbox label="image_generate">AI 图像生成 (绘图工具)</el-checkbox>
                  </el-checkbox-group>
                </el-form-item>
              </el-col>

              <!-- 联网搜索 Key 配置（联级显示） -->
              <el-col :span="12" v-if="form.modelType === 'CHAT' && enabledToolsArray && enabledToolsArray.includes('web_search')" style="margin-top: 10px;">
                <el-form-item label="联网搜索 Key" prop="searchKey">
                  <el-input v-model="form.searchKey" placeholder="输入 Tavily 等联网搜索的 API Key" show-password/>
                </el-form-item>
              </el-col>
            </el-row>
          </div>

          <!-- 属性面板：向量模型参数（仅 EMBEDDING 类型显示） -->
          <div v-if="form.modelType === 'EMBEDDING'" class="pane-card card-space-margin">
            <div class="pane-card-header">
              <span class="header-dot teal-dot"></span>
              <h5>向量模型参数</h5>
            </div>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="输出维度" prop="embeddingDimension">
                  <el-input-number
                    v-model="form.embeddingDimension"
                    :min="1"
                    :max="65536"
                    controls-position="right"
                    placeholder="例如 1024"
                    style="width: 100%;"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="维度模式" prop="embeddingDimensionMode">
                  <el-select v-model="form.embeddingDimensionMode" style="width: 100%;">
                    <el-option label="使用模型默认维度" value="MODEL_DEFAULT" />
                    <el-option label="向提供商请求指定维度" value="REQUEST" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最大输入 Token" prop="embeddingMaxInputTokens">
                  <el-input-number
                    v-model="form.embeddingMaxInputTokens"
                    :min="1"
                    :max="1000000"
                    controls-position="right"
                    placeholder="留空则不校验"
                    style="width: 100%;"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="批量分片数" prop="embeddingBatchSize">
                  <el-input-number
                    v-model="form.embeddingBatchSize"
                    :min="1"
                    :max="2048"
                    controls-position="right"
                    style="width: 100%;"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </div>

          <!-- 属性面板：图像能力配置（仅 IMAGE 类型显示） -->
          <div v-if="form.modelType === 'IMAGE'" class="pane-card card-space-margin">
            <div class="pane-card-header">
              <span class="header-dot purple-dot"></span>
              <h5>图像能力配置</h5>
            </div>
            <el-form-item label="支持的生成能力">
              <el-checkbox-group v-model="imageCapabilitiesArray">
                <el-checkbox
                  v-for="opt in imageCapabilityOptions"
                  :key="opt.value"
                  :label="opt.value"
                >{{ opt.label }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="默认出图尺寸">
              <el-select v-model="form.defaultImageSize" placeholder="默认 1024x1024" clearable style="width: 100%;">
                <el-option label="1024x1024 (1:1 方形)" value="1024x1024"/>
                <el-option label="1280x720 (16:9 横屏)" value="1280x720"/>
                <el-option label="720x1280 (9:16 竖屏)" value="720x1280"/>
                <el-option label="1024x768 (4:3)" value="1024x768"/>
                <el-option label="768x1024 (3:4)" value="768x1024"/>
              </el-select>
            </el-form-item>
            <el-form-item label="模型质量标签">
              <el-checkbox-group v-model="modelFeaturesArray">
                <el-checkbox
                  v-for="opt in modelFeatureOptions"
                  :key="opt.value"
                  :label="opt.value"
                >{{ opt.label }}</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="模型备注">
              <el-input
                v-model="form.modelDescription"
                :rows="2"
                type="textarea"
                placeholder="管理员可见的模型备注，不参与路由。"
                maxlength="255"
                show-word-limit
              />
            </el-form-item>
          </div>

          <!-- 属性面板三：系统专属提示词指令 -->
          <div class="pane-card">
            <div class="pane-card-header">
              <span class="header-dot orange-dot"></span>
              <h5>专属系统提示词 (System Prompt Instructions)</h5>
            </div>
            <el-form-item label="系统提示词指令" prop="systemPrompt">
              <el-input
                v-model="form.systemPrompt"
                :rows="6"
                placeholder="该模型专属系统提示词，设置后会覆盖全局提示词配置。"
                type="textarea"
                class="tech-prompt-textarea"
              />
            </el-form-item>
          </div>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script>
import {
  addModel,
  delModel,
  fetchRemoteModels,
  getModel,
  listModel,
  setDefaultChat,
  setDefaultEmbedding,
  setDefaultImage,
  updateModel
} from '@/api/ai/model'
import {deptTreeSelect} from '@/api/system/user'
import {
  ChatDotRound,
  ChatLineRound,
  Collection,
  Connection,
  Cpu,
  Delete,
  DocumentCopy,
  Edit,
  Key,
  Link as LinkIcon,
  OfficeBuilding
} from '@element-plus/icons-vue'

export default {
  name: 'AiModelConfig',
  components: {
    LinkIcon,
    Cpu,
    Key,
    ChatLineRound,
    Connection,
    OfficeBuilding,
    ChatDotRound,
    Collection,
    Edit,
    Delete,
    DocumentCopy
  },
  watch: {
    // 提供商变更时清空已拉取的模型列表（不同提供商模型不同，需重新拉取）
    'form.provider'() {
      this.remoteModelList = [];
    }
  },
  computed: {
    isPlatform() {
      return this.isPlatformMode();
    },
    // 是否可以点击获取模型按钮：提供商必填 + (API Key 必填 || Ollama 无需 Key) + (OpenAI 中转必须填 URL)
    canFetchModels() {
      if (!this.form.provider) return false;
      if (this.form.provider === 'ollama') return true;
      // 中转站模式或 OpenAI 提供商必须填写 baseUrl
      if ((this.form.accessMode === 'relay' || this.form.provider === 'openai') && !this.form.baseUrl) return false;
      return !!this.form.apiKey;
    },
    enabledToolsArray: {
      get() {
        if (!this.form || !this.form.enabledTools) return [];
        return this.form.enabledTools.split(',').filter(Boolean);
      },
      set(val) {
        if (this.form) {
          this.form.enabledTools = val ? val.join(',') : '';
        }
      }
    },
    imageCapabilitiesArray: {
      get() {
        return this.jsonToArray(this.form && this.form.imageCapabilities);
      },
      set(val) {
        if (this.form) {
          this.form.imageCapabilities = val && val.length ? JSON.stringify(val) : null;
        }
      }
    },
    modelFeaturesArray: {
      get() {
        return this.jsonToArray(this.form && this.form.modelFeatures);
      },
      set(val) {
        if (this.form) {
          this.form.modelFeatures = val && val.length ? JSON.stringify(val) : null;
        }
      }
    }
  },
  data() {
    return {
      // 视图模式 card: 三维星图, table: 经典表格, edit: 独立整屏配置工作台
      viewMode: 'card',
      // 远程拉取的可用模型名称列表
      remoteModelList: [],
      // 模型列表拉取中 loading 状态
      fetchingModels: false,
      // 部门树选项
      deptOptions: [],
      // 遮罩层
      loading: true,
      // 模型表格数据
      modelList: [],
      // 弹出层标题
      title: '',
      // 是否显示弹出层
      open: false,
      // 总条数
      total: 0,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        name: undefined,
        provider: undefined
      },
      // 图像生成能力选项（对应后端 ImageGenerationMode 枚举）
      imageCapabilityOptions: [
        { value: 'text_to_image', label: '文生图' },
        { value: 'image_to_image', label: '图生图' },
        { value: 'multi_image', label: '多图生成' },
        { value: 'image_edit', label: '指令改图' },
        { value: 'inpainting', label: '局部重绘' },
        { value: 'object_removal', label: '消除' },
        { value: 'outpainting', label: '扩图' },
        { value: 'background_replacement', label: '换背景' },
        { value: 'upscale', label: '高清放大' },
        { value: 'restoration', label: '照片修复' }
      ],
      // 模型质量标签选项（不参与路由，仅用于推荐排序展示）
      modelFeatureOptions: [
        { value: 'text_rendering', label: '文字渲染强' },
        { value: 'photorealistic', label: '写实照片' },
        { value: 'character_consistency', label: '角色一致性' },
        { value: 'fast_generation', label: '快速出图' },
        { value: 'high_resolution', label: '高分辨率' }
      ],
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        name: [
          { required: true, message: '配置名称不能为空', trigger: 'blur' }
        ],
        provider: [
          { required: true, message: '提供商不能为空', trigger: 'change' }
        ],
        modelName: [
          { required: true, message: '模型名称不能为空', trigger: 'change' }
        ]
      }
    }
  },
  created() {
    this.getList()
    // 仅在管理后台模式下查询部门树，中台模式下无需查询后台部门
    if (!this.isPlatformMode()) {
      this.getDeptTree()
    }
  },
  methods: {
    isPlatformMode() {
      return this.$route && this.$route.path && this.$route.path.startsWith('/platform')
    },
    isEmbeddingModel(modelName) {
      if (!modelName) return false
      return modelName.toLowerCase().includes('embed')
    },
    // 兼容解析：JSON 数组字符串 / 逗号分隔字符串 / 数组，统一转成数组
    jsonToArray(val) {
      if (!val) return [];
      if (Array.isArray(val)) return val;
      try {
        const parsed = JSON.parse(val);
        return Array.isArray(parsed) ? parsed : [];
      } catch (e) {
        return String(val).split(',').filter(Boolean);
      }
    },
    // 能力 code → 中文名
    getImageCapabilityLabel(code) {
      const hit = this.imageCapabilityOptions.find(o => o.value === code);
      return hit ? hit.label : code;
    },
    /** 查询模型配置列表 */
    getList() {
      this.loading = true
      listModel(this.queryParams).then(response => {
        if (response.code === 200) {
          this.modelList = response.data.rows || response.data || []
          this.total = response.data.total || this.modelList.length
        } else {
          this.modelList = response.data.rows || response.data || []
          this.total = response.total || this.modelList.length
        }
        this.loading = false
      }).catch(err => {
        console.error(err)
        this.loading = false
      })
    },
    // 提供商翻译成可读Label
    getProviderLabel(provider) {
      const map = {
        deepseek: 'DeepSeek',
        dashscope: '阿里云通义',
        openai: 'OpenAI',
        ollama: 'Ollama',
        ark: '火山引擎 Ark'
      }
      return map[provider] || provider
    },
    // 翻译思考强度
    getReasoningEffortLabel(effort) {
      const map = {
        low: '低 (low)',
        medium: '中 (medium)',
        high: '高 (high)',
        max: '极高 (max)'
      }
      return map[effort] || '默认'
    },
    /** 查询部门下拉树结构 */
    getDeptTree() {
      deptTreeSelect().then(response => {
        this.deptOptions = response.data || []
      }).catch(() => {
        this.deptOptions = []
      })
    },
    // 递归获取部门名称
    getDeptName(deptId) {
      if (!deptId || !this.deptOptions || this.deptOptions.length === 0) return '部门专属'
      const findName = (list) => {
        for (const item of list) {
          if (item.id === deptId) return item.label
          if (item.children && item.children.length > 0) {
            const name = findName(item.children)
            if (name) return name
          }
        }
        return null
      }
      return findName(this.deptOptions) || '部门专属'
    },
    // 取消按钮
    cancel() {
      this.viewMode = 'card'
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        id: undefined,
        name: undefined,
        accessMode: 'direct',
        provider: 'deepseek',
        modelName: undefined,
        apiKey: undefined,
        baseUrl: undefined,
        maxTokens: 2048,
        temperature: 0.7,
        maxHistoryMessages: 20,
        systemPrompt: undefined,
        enableThinking: '0',
        reasoningEffort: undefined,
        enableSearch: '0',
        searchKey: undefined,
        deptId: undefined,
        modelType: 'CHAT',
        embeddingDimension: undefined,
        embeddingDimensionMode: 'MODEL_DEFAULT',
        embeddingMaxInputTokens: undefined,
        embeddingBatchSize: 16,
        enabledTools: undefined,
        defaultImageSize: '1024x1024',
        imageCapabilities: undefined,
        modelFeatures: undefined,
        modelDescription: undefined,
        isDefault: '0',
        maxConcurrency: undefined,
        status: '1'
      }
      this.remoteModelList = []
      this.resetForm('form')
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        name: undefined,
        provider: undefined
      }
      this.handleQuery()
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.viewMode = 'edit'
      this.title = '添加 AI 模型配置'
    },
    /** 拉取远程可用模型列表 */
    handleFetchModels() {
      this.fetchingModels = true
      fetchRemoteModels({
        provider: this.form.provider,
        apiKey: this.form.apiKey,
        baseUrl: this.form.baseUrl,
        accessMode: this.form.accessMode
      }).then(res => {
        if (res.code === 200 && res.data && res.data.length > 0) {
          this.remoteModelList = res.data
          this.$modal.msgSuccess(`成功获取 ${res.data.length} 个可用模型`)
        } else {
          this.$modal.msgWarning(res.msg || '未获取到模型列表，请检查 API Key 或网络连接')
        }
      }).catch(err => {
        console.error(err)
        this.$modal.msgError('获取模型列表失败：' + (err.message || '请检查 API Key 和 Base URL'))
      }).finally(() => {
        this.fetchingModels = false
      })
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      getModel(row.id).then(response => {
        this.form = response.data
        this.viewMode = 'edit'
        this.title = '修改 AI 模型配置'
      })
    },
    /** 提交表单 */
    submitForm() {
      this.$refs['form'].validate(valid => {
        if (valid) {
          if (this.form.modelType === 'CHAT') {
            this.form.enableSearch = this.enabledToolsArray.includes('web_search') ? '1' : '0';
          } else {
            this.form.enableSearch = '0';
          }
          // 非图像模型清空图像能力字段，避免类型切换后残留脏数据
          if (this.form.modelType !== 'IMAGE') {
            this.form.imageCapabilities = null;
            this.form.modelFeatures = null;
            this.form.modelDescription = null;
          }
          if (this.isPlatform) {
            this.form.deptId = undefined;
          }
          if (this.form.id != null) {
            updateModel(this.form).then(() => {
              this.$modal.msgSuccess('修改成功')
              this.viewMode = 'card'
              this.getList()
            })
          } else {
            addModel(this.form).then(() => {
              this.$modal.msgSuccess('新增成功')
              this.viewMode = 'card'
              this.getList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      this.$modal.confirm(`是否确认删除配置名称为 "${row.name}" 的模型配置数据项？`).then(() => {
        return delModel(row.id)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('删除成功')
      }).catch(() => {})
    },
    /** 状态开启与禁用修改 */
    toggleStatus(row) {
      row.status = row.status === "1" ? "0" : "1";
      this.handleStatusChange(row);
    },
    /** 状态修改操作 */
    handleStatusChange(row) {
      const text = row.status === '1' ? '启用' : '停用'
      this.$modal.confirm(`确认要"${text}"模型 "${row.name}" 吗？`).then(() => {
        return updateModel({ id: row.id, status: row.status })
      }).then(() => {
        this.$modal.msgSuccess(text + '成功')
        this.getList()
      }).catch(() => {
        row.status = row.status === '1' ? '0' : '1'
      })
    },
    /** 设为默认聊天模型 */
    handleSetDefaultChat(row) {
      this.$modal.confirm(`确认将模型 "${row.name}" 设置为系统默认的聊天对话模型吗？`).then(() => {
        return setDefaultChat(row.id)
      }).then(() => {
        this.$modal.msgSuccess('默认聊天模型切换成功')
        this.getList()
      }).catch(() => {})
    },
    /** 设为默认向量模型 */
    handleSetDefaultEmbedding(row) {
      this.$modal.confirm(`确认将模型 "${row.name}" 设置为系统默认的向量 Embedding 模型吗？`).then(() => {
        return setDefaultEmbedding(row.id)
      }).then(() => {
        this.$modal.msgSuccess('默认向量模型切换成功')
        this.getList()
      }).catch(() => {})
    },
    /** 设为默认绘图模型 */
    handleSetDefaultImage(row) {
      this.$modal.confirm(`确认将模型 "${row.name}" 设置为系统默认的图像生成 IMAGE 模型吗？`).then(() => {
        return setDefaultImage(row.id)
      }).then(() => {
        this.$modal.msgSuccess('默认绘图模型切换成功')
        this.getList()
      }).catch(() => {})
    },
    /** 获取提供商 pill 色彩类名 */
    getProviderTagType(provider) {
      if (!provider) return 'tag-slate';
      const p = provider.toLowerCase();
      if (p === 'deepseek') return 'tag-teal';
      if (p === 'dashscope') return 'tag-purple';
      if (p === 'openai') return 'tag-indigo';
      if (p === 'ollama') return 'tag-amber';
      if (p === 'ark') return 'tag-orange';
      return 'tag-slate';
    },
    /** 复制系统提示词 */
    copyPrompt(text) {
      if (!text) return;
      navigator.clipboard.writeText(text).then(() => {
        this.$message.success("系统提示词已复制到剪贴板");
      }).catch(err => {
        console.error(err);
        this.$message.error("复制失败，请手动复制");
      });
    }
  }
}
</script>

<style lang="scss">
/* 解决多选框选中文案在白底面板下显示为白色的全局样式最高优先级覆写 */
html body .agent-workbench-container,
html body .ai-model-manager,
html body .el-dialog,
html body .pane-card {
  .el-checkbox {
    .el-checkbox__label {
      color: #334155 !important; /* 强制覆盖未选中时显示为深 Slate 灰色 */
    }
    &.is-checked {
      .el-checkbox__label {
        color: #4f46e5 !important; /* 强制覆盖选中时显示为明亮 Indigo 紫蓝色 */
      }
    }
  }
}
</style>

<style lang="scss" scoped>
@use "@/assets/styles/polaris-ai.scss";

.ai-model-manager {
  background: transparent !important;
  min-height: calc(100vh - 84px);
}


.model-view-dynamic-container {
  width: 100%;
}

/* 极简空状态 */
.empty-state {
  text-align: center;
  padding: 80px 20px;
  background: rgba(255, 255, 255, 0.5) !important;
  border-radius: 20px;
  border: 1px dashed rgba(0, 0, 0, 0.1) !important;
  width: 100%;
  margin-top: 10px;
  position: relative;
  overflow: hidden;

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.25) !important;
    border-color: rgba(255, 255, 255, 0.08) !important;
  }
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  display: inline-block;
  animation: float-icon 3s ease-in-out infinite;
}

.empty-state p {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: #64748b;

  .dark &,
  .theme-dark & {
    color: #cbd5e1;
  }
}

@keyframes float-icon {
  0%, 100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-6px) scale(1.03);
  }
}

/* ===== 🧠 三维北辰星图卡片视图布局 ===== */
.synapse-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(290px, 1fr));
  gap: 20px;
  width: 100%;
  padding-bottom: 10px;
}

/* 三维玻璃卡片 */
.synapse-glass-card {
  border-radius: 24px;
  padding: 24px;
  backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
  position: relative;
  overflow: hidden;
  transition: all 0.5s cubic-bezier(0.25, 0.8, 0.25, 1);
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 10px 30px -5px rgba(0, 0, 0, 0.03);

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.3);
    border: 1px solid rgba(255, 255, 255, 0.05);
    box-shadow: 0 15px 40px -10px rgba(0, 0, 0, 0.3);
  }
  
  &:hover {
    transform: translateY(-6px) scale(1.02) !important;
    
    .card-shimmer-ray {
      transform: skewX(-20deg) translateX(300px);
    }
    
    .radial-gauge-item {
      transform: translateY(-2px) scale(1.02);
    }
  }

  /* 各种状态边框发光映射 */
  &.status-border-chat {
    border-color: rgba(16, 185, 129, 0.35);
    &:hover {
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.06), 0 0 15px rgba(16, 185, 129, 0.2) !important;
      .dark &, .theme-dark & {
        box-shadow: 0 20px 48px rgba(0, 0, 0, 0.55), 0 0 25px rgba(16, 185, 129, 0.22) !important;
      }
    }
  }
  &.status-border-embed {
    border-color: rgba(59, 130, 246, 0.35);
    &:hover {
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.06), 0 0 15px rgba(59, 130, 246, 0.2) !important;
      .dark &, .theme-dark & {
        box-shadow: 0 20px 48px rgba(0, 0, 0, 0.55), 0 0 25px rgba(59, 130, 246, 0.22) !important;
      }
    }
  }
  &.status-border-image {
    border-color: rgba(245, 158, 11, 0.35);
    &:hover {
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.06), 0 0 15px rgba(245, 158, 11, 0.2) !important;
      .dark &, .theme-dark & {
        box-shadow: 0 20px 48px rgba(0, 0, 0, 0.55), 0 0 25px rgba(245, 158, 11, 0.22) !important;
      }
    }
  }
  &.status-border-active {
    border-color: rgba(139, 92, 246, 0.3);
    &:hover {
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.06), 0 0 15px rgba(139, 92, 246, 0.2) !important;
      .dark &, .theme-dark & {
        box-shadow: 0 20px 48px rgba(0, 0, 0, 0.55), 0 0 25px rgba(139, 92, 246, 0.22) !important;
      }
    }
  }
  &.status-border-inactive {
    border-color: rgba(239, 68, 68, 0.35);
    &:hover {
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.06), 0 0 15px rgba(239, 68, 68, 0.2) !important;
      .dark &, .theme-dark & {
        box-shadow: 0 20px 48px rgba(0, 0, 0, 0.55), 0 0 25px rgba(239, 68, 68, 0.22) !important;
      }
    }
  }
}


/* 提供商药丸型芯片标签 */
.card-code {
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
  padding: 2px 8px;
  border-radius: 6px;

  &.tag-purple {
    background: rgba(139, 92, 246, 0.06);
    color: #8b5cf6;
    border: 1px solid rgba(139, 92, 246, 0.12);
  }
  &.tag-amber {
    background: rgba(245, 158, 11, 0.06);
    color: #d97706;
    border: 1px solid rgba(245, 158, 11, 0.12);
  }
  &.tag-teal {
    background: rgba(20, 184, 166, 0.06);
    color: #0d9488;
    border: 1px solid rgba(20, 184, 166, 0.12);
  }
  &.tag-rose {
    background: rgba(244, 63, 94, 0.06);
    color: #e11d48;
    border: 1px solid rgba(244, 63, 94, 0.12);
  }
  &.tag-indigo {
    background: rgba(79, 70, 229, 0.06);
    color: #4f46e5;
    border: 1px solid rgba(79, 70, 229, 0.12);
  }
  &.tag-slate {
    background: rgba(100, 116, 139, 0.06);
    color: #475569;
    border: 1px solid rgba(100, 116, 139, 0.12);
  }

  .dark &,
  .theme-dark & {
    &.tag-purple {
      background: rgba(139, 92, 246, 0.12);
      color: #a78bfa;
      border-color: rgba(139, 92, 246, 0.2);
    }
    &.tag-amber {
      background: rgba(245, 158, 11, 0.12);
      color: #fbbf24;
      border-color: rgba(245, 158, 11, 0.2);
    }
    &.tag-teal {
      background: rgba(20, 184, 166, 0.12);
      color: #2dd4bf;
      border-color: rgba(20, 184, 166, 0.2);
    }
    &.tag-rose {
      background: rgba(244, 63, 94, 0.12);
      color: #fb7185;
      border-color: rgba(244, 63, 94, 0.2);
    }
    &.tag-indigo {
      background: rgba(99, 102, 241, 0.12);
      color: #818cf8;
      border-color: rgba(99, 102, 241, 0.2);
    }
    &.tag-slate {
      background: rgba(148, 163, 184, 0.12);
      color: #cbd5e1;
      border-color: rgba(148, 163, 184, 0.2);
    }
  }
}


.card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 !important;
}

.card-name {
  font-size: 15px;
  font-weight: 800;
  margin: 0;
  color: #0f172a;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;

  .dark &,
  .theme-dark & {
    color: #cbd5e1;
  }
}


/* 参数属性行 */
.code-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12.5px;
}
.param-label {
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  
  .dark &, .theme-dark & { color: #94a3b8; }
}

.code-text {
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
  background: rgba(0,0,0,0.03);
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11.5px;
  border: 1px solid rgba(0,0,0,0.04);
  white-space: nowrap;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #0f172a;

  .dark &, .theme-dark & {
    background: rgba(255,255,255,0.03);
    border-color: rgba(255,255,255,0.05);
    color: #cbd5e1;
  }
}

.url-text {
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 150px;
  
  .dark &, .theme-dark & { color: #94a3b8; }
}

/* 环形参数仪表盘 */
.card-stats-gauges {
  display: flex;
  justify-content: space-around;
  margin: 6px 0;
}

.radial-gauge-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  transition: transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  
  .gauge-label {
    font-size: 10px;
    font-weight: 700;
    color: #64748b;

    .dark &,
    .theme-dark & {
      color: #94a3b8;
    }
  }
}

.radial-circle-box {
  width: 54px;
  height: 54px;
  position: relative;
  
  svg {
    width: 100%;
    height: 100%;
  }
  
  .circle-bg {
    fill: none;
    stroke-width: 2.8;
    stroke: rgba(0, 0, 0, 0.04);

    .dark &,
    .theme-dark & {
      stroke: rgba(255, 255, 255, 0.04);
    }
  }
  
  .circle-fill {
    fill: none;
    stroke-linecap: round;
    stroke-width: 2.8;
    transition: stroke-dasharray 0.3s ease, stroke 0.3s ease;
  }
}


.synapse-glass-card:hover .radial-circle-box svg {
  filter: drop-shadow(0 0 3px rgba(139, 92, 246, 0.15));
}

/* 技术选项药丸 */
.param-pill-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tech-pill {
  font-weight: 700;
  border-radius: 20px;
}


/* 卡片页脚布局 */
.card-footer-row-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px dashed rgba(0, 0, 0, 0.06);
  padding-top: 12px;
  margin-top: auto;

  .dark &,
  .theme-dark & {
    border-top-color: rgba(255, 255, 255, 0.05);
  }
}

.default-tags-area {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  
  .el-tag {
    font-weight: 700;
    white-space: nowrap !important;
    height: 24px !important;
    padding: 0 8px !important;
    border-radius: 6px !important;
    
    :deep(.el-tag__content) {
      display: inline-flex !important;
      align-items: center !important;
      justify-content: center !important;
      gap: 4px;
      height: 100%;
      line-height: 1 !important;
      
      span {
        line-height: 1 !important;
        display: inline-block;
      }
    }
  }
}

.active-tag-chat {
  border: none !important;
  background: linear-gradient(135deg, #10b981, #059669) !important;
  box-shadow: 0 2px 6px rgba(16, 185, 129, 0.2);
  color: #ffffff !important;
}

.active-tag-embed {
  border: none !important;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8) !important;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.2);
  color: #ffffff !important;
}

.footer-action-btn {
  font-size: 11px;
  font-weight: 700;
  color: #10b981 !important;
  padding: 0;
  white-space: nowrap !important;

  &.color-primary {
    color: #4f46e5 !important;
    white-space: nowrap !important;

    .dark &, .theme-dark & { color: #38bdf8 !important; }
  }
}

.action-buttons-row {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* 精致药丸型卡片操作按钮 */
.card-op-edit-pill,
.card-op-delete-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 12px !important;
  border-radius: 14px !important;
  font-size: 11px;
  font-weight: 700;
  border: 1px solid transparent !important;
  transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
  cursor: pointer;
  background-color: transparent !important;
}

.card-op-edit-pill {
  color: #4f46e5 !important;
  
  &:hover {
    color: #4338ca !important;
    background-color: rgba(79, 70, 229, 0.05) !important;
    border-color: rgba(79, 70, 229, 0.15) !important;
    transform: translateY(-1px);
  }

  .dark &,
  .theme-dark & {
    color: #38bdf8 !important;

    &:hover {
      color: #7dd3fc !important;
      background-color: rgba(56, 189, 248, 0.08) !important;
      border-color: rgba(56, 189, 248, 0.15) !important;
    }
  }
}

.card-op-delete-pill {
  color: #ef4444 !important;

  &:hover {
    color: #dc2626 !important;
    background-color: rgba(239, 68, 68, 0.05) !important;
    border-color: rgba(239, 68, 68, 0.15) !important;
    transform: translateY(-1px);
  }

  .dark &,
  .theme-dark & {
    &:hover {
      color: #f87171 !important;
      background-color: rgba(239, 68, 68, 0.08) !important;
      border-color: rgba(239, 68, 68, 0.15) !important;
    }
  }
}

/* ===== 经典表格视图中的模型配置样式 ===== */
.polaris-table-card-table-wrap {
  width: 100%;
}
.op-btn-default-set {
  color: #10b981 !important;
  font-weight: 700;
  font-size: 11px;
}
.op-btn-default-set-embed {
  color: #4f46e5 !important;
  font-weight: 700;
  font-size: 11px;

  .dark &, .theme-dark & { color: #38bdf8 !important; }
}

.default-tags-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  
  .el-tag {
    font-weight: 700;
  }
}

/* ===== 聚合操作栏 (检索卡片正下方) ===== */
.matrix-actions-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 12px 0 16px; /* 增加适度外边距，形成舒适的呼吸感 */
  padding: 0 4px;
}

.actions-left {
  display: flex;
  align-items: center;
}

.actions-right {
  display: flex;
  align-items: center;
}

/* 视图切换器按钮 */
.view-mode-toggle-row {
  display: flex;
  gap: 4px;
  padding: 4px;
  border-radius: 12px;
  align-items: center;
  background-color: rgba(0, 0, 0, 0.03);
  height: 36px;
  box-sizing: border-box;

  .dark &,
  .theme-dark & {
    background-color: rgba(255, 255, 255, 0.03);
  }
}

.toggle-view-btn {
  background: transparent;
  border: none;
  font-size: 11px;
  font-weight: 700;
  padding: 0 12px;
  height: 28px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.2, 0.8, 0.2, 1);
  color: #64748b;
  display: flex;
  align-items: center;
  justify-content: center;

  &.active {
    background-color: #ffffff;
    color: #4f46e5;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  }

  .dark &,
  .theme-dark & {
    color: #94a3b8;

    &.active {
      background-color: rgba(255, 255, 255, 0.05);
      color: #38bdf8;
      box-shadow: none;
    }
  }
}

/* ===== 独立整屏大屏配置工作台 (Model Workbench) ===== */
.agent-workbench-container {
  animation: fadeIn 0.4s ease;
  background-color: transparent;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.workbench-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 18px;
  padding: 14px 24px;
  margin-bottom: 24px;
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.02);

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.35);
    border-color: rgba(255, 255, 255, 0.05);
    box-shadow: none;
  }

  .action-btn-primary {
    .dark &,
    .theme-dark & {
      background: linear-gradient(135deg, #38bdf8, #818cf8) !important;
      border-color: transparent !important;
      color: #0f172a !important;
      box-shadow: 0 4px 14px rgba(56, 189, 248, 0.25);

      &:hover {
        background: linear-gradient(135deg, #7dd3fc, #93c5fd) !important;
        box-shadow: 0 6px 18px rgba(56, 189, 248, 0.35);
      }
    }
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.workbench-title-text {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;

  .dark &,
  .theme-dark & {
    color: #cbd5e1;
  }
}

.cancel-action-btn {
  border-radius: 10px !important;
  font-size: 12px;
  font-weight: 700;
  border-color: rgba(0, 0, 0, 0.08) !important;
  background-color: transparent !important;
  color: #475569 !important;
  height: 32px;
  transition: all 0.3s;

  &:hover {
    background-color: rgba(0, 0, 0, 0.02) !important;
    border-color: rgba(0, 0, 0, 0.15) !important;
  }

  .dark &,
  .theme-dark & {
    border-color: rgba(255, 255, 255, 0.08) !important;
    color: #cbd5e1 !important;
    
    &:hover {
      background-color: rgba(255, 255, 255, 0.04) !important;
      border-color: rgba(255, 255, 255, 0.15) !important;
    }
  }
}

.workbench-body {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.editor-left-pane {
  width: 420px;
  flex-shrink: 0;
}

.editor-right-pane {
  flex: 1;
}

.pane-card {
  border-radius: 20px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(255, 255, 255, 0.55);
  box-shadow: 0 4px 15px rgba(0,0,0,0.01) !important;
  backdrop-filter: blur(20px);
  padding: 24px;

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.3);
    border-color: rgba(255, 255, 255, 0.05);
  }
}

.card-space-margin {
  margin-bottom: 24px;
}

.pane-card-header {
  font-size: 13.5px;
  font-weight: 700;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  border-bottom: 1px dashed rgba(0, 0, 0, 0.06);
  padding-bottom: 12px;

  .header-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background-color: #8b5cf6;
    display: inline-block;
    box-shadow: 0 0 8px rgba(139, 92, 246, 0.6);

    &.purple-dot {
      background-color: #a78bfa;
      box-shadow: 0 0 8px rgba(167, 139, 250, 0.6);
    }
    &.teal-dot {
      background-color: #14b8a6;
      box-shadow: 0 0 8px rgba(20, 184, 166, 0.6);
    }
    &.orange-dot {
      background-color: #f97316;
      box-shadow: 0 0 8px rgba(249, 115, 22, 0.6);
    }
  }

  h5 {
    margin: 0;
    font-size: 13.5px;
    font-weight: 800;
  }

  .dark &,
  .theme-dark & {
    color: #cbd5e1;
    border-bottom-color: rgba(255, 255, 255, 0.05);
  }
}

.temp-slider-box {
  padding: 14px 20px;
  background: rgba(0, 0, 0, 0.01);
  border: 1px solid rgba(0, 0, 0, 0.04);
  border-radius: 12px;

  .dark &,
  .theme-dark & {
    background: rgba(255, 255, 255, 0.01);
    border-color: rgba(255, 255, 255, 0.02);
  }
}

.tech-prompt-textarea :deep(.el-textarea__inner) {
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
  font-size: 12.5px;
  background-color: #ffffff !important;
  color: #0f172a !important;
  border: 1px solid rgba(0, 0, 0, 0.08) !important;
  border-radius: 12px;
  padding: 14px 16px;
  line-height: 1.6;

  .dark &,
  .theme-dark & {
    background-color: rgba(0, 0, 0, 0.25) !important;
    color: #cbd5e1 !important;
    border-color: rgba(255, 255, 255, 0.06) !important;
  }
}

.tech-prompt-textarea :deep(.el-textarea__inner:focus) {
  border-color: #4f46e5 !important;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.15) !important;

  .dark &,
  .theme-dark & {
    border-color: #38bdf8 !important;
    box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.15) !important;
  }
}

.back-btn {
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 2px 5px rgba(0,0,0,0.02);
  transition: all 0.2s;
  height: 28px;
  width: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;

  &:hover {
    transform: scale(1.05);
    border-color: #8b5cf6;
    color: #8b5cf6;
  }

  .dark &,
  .theme-dark & {
    border-color: rgba(255, 255, 255, 0.08);
    background-color: transparent;
    color: #cbd5e1;
    
    &:hover {
      border-color: #38bdf8;
      color: #38bdf8;
    }
  }
}

</style>
