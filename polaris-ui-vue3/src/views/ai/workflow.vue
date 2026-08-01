<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <!-- 1. 顶部高效筛选与操作栏 (完全恢复系统公共原装样式) -->
      <div v-if="viewMode !== 'edit'" class="polaris-filter-card">
          <el-form :model="queryParams" ref="queryForm" :inline="true" class="polaris-filter-form">
            <el-form-item label="工作流编码">
              <el-input v-model="queryParams.workflowCode" placeholder="请输入编码" clearable @keyup.enter="handleQuery" style="width: 180px;"/>
            </el-form-item>
            <el-form-item label="工作流名称">
              <el-input v-model="queryParams.workflowName" placeholder="请输入名称" clearable @keyup.enter="handleQuery" style="width: 180px;"/>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px;">
                <el-option label="正常" value="1"/>
                <el-option label="禁用" value="0"/>
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery" class="polaris-query-btn">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery" class="polaris-reset-btn">重置</el-button>
            </el-form-item>
          </el-form>
        </div>


        <transition name="view-mode-fade" mode="out-in">
          <!-- 🧠 三维北辰星图卡片视图 -->
          <div v-if="viewMode === 'card'" key="card-view" class="synapse-card-grid-wrapper polaris-table-card">
            <!-- 共享操作行：新增工作流与视图切换器 (错落有致) -->
            <div class="matrix-actions-bar" style="margin-top: 0; width: 100%;">
              <div class="actions-left">
                <el-button type="primary" class="action-btn-primary" icon="Plus" @click="handleAdd">
                  新增工作流
                </el-button>
              </div>
              <div class="actions-right">
                <div class="view-mode-toggle-row">
                  <button
                    :class="['toggle-view-btn', { active: viewMode === 'card' }]"
                    @click="viewMode = 'card'; lastListViewMode = 'card'"
                  >
                    🧠 三维星图
                  </button>
                  <button
                    :class="['toggle-view-btn', { active: viewMode === 'table' }]"
                    @click="viewMode = 'table'; lastListViewMode = 'table'"
                  >
                    📊 经典表格
                  </button>
                </div>
              </div>
            </div>

            <!-- 全局 SVG 渐变与滤镜定义 (用于迷你拓扑图连线与节点发光) -->
            <svg style="width: 0; height: 0; position: absolute;" aria-hidden="true" focusable="false">
              <defs>
                <!-- 连线渐变：默认渐变与条件分支渐变 -->
                <linearGradient id="edge-gradient-normal" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stop-color="#818cf8" />
                  <stop offset="100%" stop-color="#38bdf8" />
                </linearGradient>
                <linearGradient id="edge-gradient-cond" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stop-color="#f59e0b" />
                  <stop offset="100%" stop-color="#f97316" />
                </linearGradient>

                <!-- 节点渐变 -->
                <linearGradient id="node-grad-term" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#4f46e5" />
                  <stop offset="100%" stop-color="#818cf8" />
                </linearGradient>
                <linearGradient id="node-grad-agent-light" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#ffffff" />
                  <stop offset="100%" stop-color="#f8fafc" />
                </linearGradient>
                <linearGradient id="node-grad-agent-dark" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="rgba(30, 41, 59, 0.95)" />
                  <stop offset="100%" stop-color="rgba(15, 23, 42, 0.85)" />
                </linearGradient>
                <linearGradient id="node-grad-classifier" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#7c3aed" />
                  <stop offset="100%" stop-color="#c084fc" />
                </linearGradient>
                <linearGradient id="node-grad-java" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#10b981" />
                  <stop offset="100%" stop-color="#34d399" />
                </linearGradient>
              </defs>
            </svg>

            <div v-loading="loading" class="synapse-card-grid-container">
              <div v-if="workflowList.length === 0" class="empty-state">
                <div class="empty-icon">⚙️</div>
                <p>暂无工作流编排，点击上方"新增工作流"按钮开始设计您的 AI 智能体工作流吧！</p>
              </div>

              <div class="synapse-card-grid" v-else>
                <div
                  v-for="item in workflowList"
                  :key="item.id"
                  :class="['synapse-glass-card', item.status === '1' ? 'status-border-active' : 'status-border-error']"
                >
                  <!-- 卡片高亮流光线 -->
                  <div class="card-shimmer-ray"></div>
                  
                  <div class="card-header-row">
                    <span class="card-code">{{ item.workflowCode }}</span>
                    <!-- 雷达多层呼吸灯 -->
                    <div class="status-cell clickable-status" @click="toggleStatus(item)">
                      <span :class="['pulse-light-ripple', item.status === '1' ? 'pulse-active' : 'pulse-error']"></span>
                      <span class="status-badge-text" :class="item.status === '1' ? 'text-active' : 'text-error'">
                        {{ item.status === '1' ? '正常' : '禁用' }}
                      </span>
                    </div>
                  </div>

                  <div class="card-body">
                    <h4 class="card-name" :title="item.workflowName">{{ item.workflowName }}</h4>
                    
                    <!-- 流程核心指标徽章 -->
                    <div class="workflow-stats-chips" v-if="item._miniGraph">
                      <span class="stat-chip-pill chip-purple">
                        <el-icon><cpu /></el-icon>
                        <span>智能体 x{{ item._miniGraph.agentCount }}</span>
                      </span>
                      <span class="stat-chip-pill chip-blue" v-if="item._miniGraph.classifierCount > 0">
                        <el-icon><share /></el-icon>
                        <span>意图路由 x{{ item._miniGraph.classifierCount }}</span>
                      </span>
                      <span class="stat-chip-pill chip-green" v-if="item._miniGraph.javaCount > 0">
                        <el-icon><connection /></el-icon>
                        <span>系统组件 x{{ item._miniGraph.javaCount }}</span>
                      </span>
                    </div>

                    <!-- 流程备注说明 -->
                    <div class="desc-box">
                      <p :class="['desc-text', { 'no-desc': !item.description }]" :title="item.description || '暂无描述说明'">
                        {{ item.description || '暂无描述说明，请点击编辑编排开始设计流程描述' }}
                      </p>
                    </div>

                    <!-- 只读迷你拓扑图（真实展示分支结构） -->
                    <div class="mini-graph-visualization">
                      <span class="pipeline-label" style="font-size: 11px; font-weight: 700; color: var(--polaris-text-sub); display: block; margin-bottom: 6px;">
                        执行走向：
                      </span>
                      <div class="mini-graph-canvas" v-if="item._miniGraph">
                        <svg :width="item._miniGraph.width" :height="item._miniGraph.height" class="mini-graph-svg" style="margin: auto 0; flex-shrink: 0;">
                          <g v-for="(e, ei) in item._miniGraph.edges" :key="'e'+ei">
                            <path
                              :d="`M ${e.x1} ${e.y1} C ${e.x1} ${(e.y1+e.y2)/2}, ${e.x2} ${(e.y1+e.y2)/2}, ${e.x2} ${e.y2}`"
                              fill="none"
                              :class="['mini-graph-edge-path', e.cond ? 'edge-cond' : 'edge-normal']"
                            />
                          </g>
                          <g v-for="(n, ni) in item._miniGraph.nodes" :key="'n'+ni">
                            <rect
                              :x="n.x - item._miniGraph.nodeW/2" :y="n.y - item._miniGraph.nodeH/2"
                              :width="item._miniGraph.nodeW" :height="item._miniGraph.nodeH"
                              :rx="n.type === 'term' ? 12 : 6"
                              :class="['mini-node', 'mini-node-' + n.type]"
                            />
                            <text :x="n.x" :y="n.y + 3" text-anchor="middle"
                                  :class="['mini-node-text', (n.type === 'term' || n.type === 'classifier' || n.type === 'java') ? 'mini-node-text-light' : 'mini-node-text-' + n.type]">
                              {{ n.name.length > 6 ? n.name.slice(0,6) + '…' : n.name }}
                            </text>
                          </g>
                        </svg>
                      </div>
                    </div>
                  </div>

                  <!-- 卡片底部操作按钮 -->
                  <div class="card-footer-actions">
                    <el-button type="primary" link class="card-op-edit-pill" @click="handleUpdate(item)">
                      <el-icon><edit /></el-icon>
                      <span>编辑编排</span>
                    </el-button>
                    <el-button type="danger" link class="card-op-delete-pill" @click="handleDelete(item)">
                      <el-icon><delete /></el-icon>
                      <span>删除</span>
                    </el-button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 三维星图视图下的分页组件 -->
            <pagination
              v-show="total > 0"
              :total="total"
              v-model:page="queryParams.pageNum"
              v-model:limit="queryParams.pageSize"
              @pagination="getList"
            />
          </div>

          <!-- 📊 经典表格视图 -->
          <div v-else-if="viewMode === 'table'" key="table-view" class="polaris-table-card">
            <!-- 共享操作行：新增工作流与视图切换器 -->
            <div class="matrix-actions-bar" style="margin-top: 0; width: 100%;">
              <div class="actions-left">
                <el-button type="primary" class="action-btn-primary" icon="Plus" @click="handleAdd">
                  新增工作流
                </el-button>
              </div>
              <div class="actions-right">
                <div class="view-mode-toggle-row">
                  <button
                    :class="['toggle-view-btn', { active: viewMode === 'card' }]"
                    @click="viewMode = 'card'; lastListViewMode = 'card'"
                  >
                    🧠 三维星图
                  </button>
                  <button
                    :class="['toggle-view-btn', { active: viewMode === 'table' }]"
                    @click="viewMode = 'table'; lastListViewMode = 'table'"
                  >
                    📊 经典表格
                  </button>
                </div>
              </div>
            </div>
            <el-table v-loading="loading" :data="workflowList" class="polaris-el-table">
              <el-table-column label="工作流唯一编码" prop="workflowCode" width="160" />
              <el-table-column label="工作流名称" prop="workflowName" min-width="180" :show-overflow-tooltip="true" />
              <el-table-column label="描述" prop="description" min-width="220" :show-overflow-tooltip="true">
                <template #default="{ row }">
                  <span>{{ row.description || '暂无描述' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="运行状态" width="120">
                <template #default="{ row }">
                  <div class="status-cell clickable-status" @click="toggleStatus(row)">
                    <span :class="['pulse-light-ripple', row.status === '1' ? 'pulse-active' : 'pulse-error']"></span>
                    <span class="status-label" :class="row.status === '1' ? 'text-active' : 'text-error'">
                      {{ row.status === '1' ? '正常' : '禁用' }}
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" prop="createTime" width="150">
                <template #default="{ row }">
                  <span>{{ formatDate(row.createTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" fixed="right" align="center">
                <template #default="{ row }">
                  <div class="table-op-actions">
                    <el-button type="primary" link class="op-btn-edit" @click="handleUpdate(row)">编辑编排</el-button>
                    <span class="op-divider"></span>
                    <el-button type="danger" link class="op-btn-delete" @click="handleDelete(row)">删除</el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <!-- 经典表格视图下的分页组件 -->
            <pagination
              v-show="total > 0"
              :total="total"
              v-model:page="queryParams.pageNum"
              v-model:limit="queryParams.pageSize"
              @pagination="getList"
            />
    </div>
    <div v-else-if="viewMode === 'edit'" key="edit-view" class="workflow-workbench-container">
      <!-- 顶部控制条 -->
      <div class="workbench-header">
        <div class="header-left">
          <el-button icon="Back" size="small" circle @click="cancel" class="back-btn"></el-button>
          <span class="workbench-title-text">{{ form.id ? '编辑智能体工作流管线' : '创建智能体工作流管线' }}</span>
        </div>
        <div class="header-right">
          <el-button size="small" icon="Close" @click="cancel" class="cancel-action-btn">取消返回</el-button>
          <el-button type="primary" size="small" class="action-btn-primary" icon="CircleCheck" @click="submitForm">保存并发布</el-button>
        </div>
      </div>

      <!-- 两栏布局：左基础配置 + 右全屏画布 -->
      <div class="workbench-body">
        <!-- 左侧面板：元数据基础配置 -->
        <div class="editor-left-pane">
          <el-card class="pane-card" shadow="never">
            <template #header>
              <div class="pane-card-header">
                <span><el-icon><info-filled /></el-icon> 工作流基础配置</span>
              </div>
            </template>
            <el-form ref="form" :model="form" :rules="rules" label-position="top">
              <el-form-item label="工作流名称" prop="workflowName">
                <el-input v-model="form.workflowName" placeholder="如: 系统用户安全审计流" />
              </el-form-item>
              <el-form-item label="工作流唯一编码" prop="workflowCode">
                <el-input :disabled="!!form.id" v-model="form.workflowCode" placeholder="如: sys_user_audit" />
              </el-form-item>
              <el-form-item label="工作流描述" prop="description">
                <el-input v-model="form.description" type="textarea" :rows="4" placeholder="简要说明此流程的业务用途..." />
              </el-form-item>
              <el-form-item label="开启状态">
                <el-radio-group v-model="form.status">
                  <el-radio label="1">正常启用</el-radio>
                  <el-radio label="0">禁用</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="备注说明" prop="remark">
                <el-input v-model="form.remark" placeholder="备注信息" />
              </el-form-item>
            </el-form>
          </el-card>
        </div>

        <!-- 右侧：全屏 Vue Flow 编排画布 -->
        <div class="editor-canvas-pane">
          <!-- 画布工具栏 -->
          <div class="canvas-toolbar">
            <span class="canvas-title-text"><el-icon><connection /></el-icon> 拖拽节点可移动位置，拖拽 handle 可连线，双击连线可编辑条件</span>
            <div class="toolbar-actions">
              <el-button size="small" icon="Grid" @click="autoLayout">自动对齐</el-button>
              <el-button size="small" icon="FullScreen" @click="fitView">适应屏幕</el-button>
              <el-button size="small" icon="VideoPlay" @click="openTestRun">试运行</el-button>
              <el-dropdown trigger="click" @command="addNodeStep">
                <el-button type="primary" size="small" class="action-btn-primary" icon="Plus">
                  添加节点<el-icon class="el-icon--right"><arrow-down /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="agent"><el-icon><cpu /></el-icon> 智能体节点</el-dropdown-item>
                    <el-dropdown-item command="classifier"><el-icon><share /></el-icon> 意图分类节点</el-dropdown-item>
                    <el-dropdown-item v-if="javaExecutors.length" command="java"><el-icon><connection /></el-icon> Java 业务节点</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>

          <!-- Vue Flow 画布 -->
          <div class="vf-canvas-wrapper">
            <VueFlow
              ref="vueFlowRef"
              v-model:nodes="vfNodes"
              v-model:edges="vfEdges"
              :fit-view-on-init="true"
              :nodes-connectable="true"
              :edges-updatable="true"
              :zoom-on-scroll="true"
              :zoom-on-pinch="true"
              :pan-on-drag="true"
              :prevent-scrolling="true"
              :min-zoom="0.2"
              :max-zoom="2"
              :default-edge-options="defaultEdgeOptions"
              class="vue-flow-editor"
              @connect="onConnect"
              @edge-double-click="onEdgeDoubleClick"
              @node-click="onCanvasNodeClick"
              @node-drag-stop="onNodeDragStop"
              @pane-click="onPaneClick"
            >
              <Background pattern-color="rgba(255,255,255,0.06)" :gap="20" />
            </VueFlow>

            <!-- 节点属性浮层 Popover (固定在画布右上角) -->
            <transition name="popover-fade">
              <div
                v-if="popover.visible && popover.node"
                class="node-popover"
                @click.stop
              >
                <div class="popover-header">
                  <span class="popover-title"><el-icon><setting-icon /></el-icon> 节点属性</span>
                  <el-button size="small" circle icon="Close" @click="closePopover" class="popover-close-btn" />
                </div>
                <div class="popover-body">
                  <!-- 分类路由节点专属配置 -->
                  <el-form v-if="popover.node.type === 'classifier'" label-position="top" size="small">
                    <el-form-item label="节点名称">
                      <el-input v-model="popover.node.clfName" placeholder="如：意图分发" @change="onClassifierChange" />
                    </el-form-item>
                    <el-form-item label="分类使用模型">
                      <el-select v-model="popover.node.modelConfigId" placeholder="默认使用系统模型" clearable style="width:100%;">
                        <el-option v-for="m in availableModels" :key="m.id" :label="m.name" :value="m.id" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="分支出口（选择目标节点，含义自动预填可改）">
                      <div class="branch-list" style="width:100%;">
                        <div v-for="(b, bi) in popover.node.branches" :key="b.slug" class="branch-item" style="display:flex;gap:6px;margin-bottom:6px;align-items:center;">
                          <el-select v-model="b.targetId" size="small" placeholder="目标节点" style="width:120px;" @change="onBranchTargetChange(b)">
                            <el-option v-for="t in classifierTargets" :key="t.id" :label="t.name" :value="t.id" />
                          </el-select>
                          <el-input v-model="b.label" size="small" placeholder="分支含义" style="flex:1;" @change="syncClassifierBranches" />
                          <el-button size="small" circle icon="Close" @click="removeBranch(bi)" />
                        </div>
                        <el-button size="small" icon="Plus" style="width:100%;" @click="addBranch">添加分支</el-button>
                      </div>
                    </el-form-item>
                    <el-form-item label="默认兜底路径（都不匹配时走）">
                      <el-select v-model="popover.node.defaultTarget" size="small" placeholder="可不设" clearable style="width:100%;" @change="syncClassifierBranches">
                        <el-option v-for="t in classifierTargets" :key="t.id" :label="t.name" :value="t.id" />
                      </el-select>
                    </el-form-item>
                    <div class="popover-divider"></div>
                    <el-button type="danger" size="small" icon="Delete" style="width:100%;" @click="deletePopoverNode">删除此节点</el-button>
                  </el-form>
                  <!-- 智能体/Java 节点配置 -->
                  <el-form v-else label-position="top" size="small">
                    <el-form-item label="指向执行组件/智能体">
                      <el-select v-model="popover.node.ref" placeholder="请选择智能体 / 代码组件" style="width:100%;" @change="onPopoverRefChange">
                        <el-option-group v-if="popover.node.type === 'agent'" label="AI 智能体">
                          <el-option v-for="item in activeAgents" :key="item.id" :label="item.agentName" :value="item.agentCode" />
                        </el-option-group>
                        <el-option-group v-if="popover.node.type === 'java'" label="系统 Java 节点">
                          <el-option v-for="item in javaExecutors" :key="item.code" :label="item.name" :value="item.code" />
                        </el-option-group>
                      </el-select>
                    </el-form-item>
                    <el-form-item label="需要人工审核 (Human-in-the-Loop)">
                      <el-switch v-model="popover.node.requireApproval" @change="onPopoverChange" />
                      <span style="margin-left:8px;font-size:12px;color:#94a3b8;">
                        {{ popover.node.requireApproval ? '开启挂起' : '已关闭' }}
                      </span>
                    </el-form-item>
                    <el-form-item label="执行超时限制 (秒)">
                      <el-input-number v-model="popover.node.timeoutSeconds" :min="10" :max="1800" style="width:100%;" @change="onPopoverChange" />
                    </el-form-item>
                    <div class="popover-divider"></div>
                    <el-button type="danger" size="small" icon="Delete" style="width:100%;" @click="deletePopoverNode">删除此节点</el-button>
                  </el-form>
                  <div v-if="popover.node.type === 'agent' && popover.node.ref && getAgentByCode(popover.node.ref)" class="popover-agent-props">
                    <div class="prop-group">
                      <span class="prop-label-title"><el-icon><cpu /></el-icon> 底座大模型</span>
                      <el-tag size="small" type="primary" effect="plain" style="width:100%;text-align:center;font-family:monospace;">
                        {{ getAgentByCode(popover.node.ref).modelName }}
                      </el-tag>
                    </div>
                    <div class="prop-group">
                      <span class="prop-label-title"><el-icon><odometer /></el-icon> Temperature: {{ getAgentByCode(popover.node.ref).temperature }}</span>
                      <el-progress :percentage="getAgentByCode(popover.node.ref).temperature * 100" :show-text="false" :stroke-width="4" color="#6366f1" />
                    </div>
                    <div class="prop-group">
                      <span class="prop-label-title"><el-icon><document /></el-icon> System Prompt</span>
                      <div class="popover-prompt-preview">{{ getAgentByCode(popover.node.ref).systemPrompt }}</div>
                    </div>
                  </div>
                  <div v-else-if="popover.node.type === 'java' && popover.node.ref" class="popover-agent-props">
                    <el-tag size="small" type="success" effect="plain" style="width:100%;text-align:center;">{{ getJavaExecutorName(popover.node.ref) }}</el-tag>
                  </div>
                </div>
              </div>
            </transition>

            <!-- 边 condition 编辑浮层 (固定在画布右上角) -->
            <transition name="popover-fade">
              <div
                v-if="edgeEditor.visible"
                class="edge-editor-popover"
                @click.stop
              >
                <div class="popover-header">
                  <span class="popover-title"><el-icon><share /></el-icon> 配置路由条件</span>
                  <el-button size="small" circle icon="Close" @click="edgeEditor.visible = false" class="popover-close-btn" />
                </div>
                <div class="edge-editor-body">
                  <!-- 分类节点出边：直接从已定义分支中选择 -->
                  <template v-if="edgeEditor.sourceBranches">
                    <div class="edge-keyword-section">
                      <div class="edge-keyword-label">选择此连线对应的分支</div>
                      <el-select v-model="edgeEditor.condition" placeholder="选择分支（留空=默认兜底路径）" clearable size="small" style="width:100%;">
                        <el-option v-for="b in edgeEditor.sourceBranches" :key="b.slug" :label="b.label" :value="b.slug" />
                      </el-select>
                      <div class="edge-keyword-hint">
                        分类节点判定为该分支时，流程走这条连线。留空表示默认兜底路径（所有分支都不匹配时走它）。
                      </div>
                    </div>
                  </template>

                  <!-- 非分类节点出边：默认/条件两卡片模式 -->
                  <template v-else>
                    <!-- 路由类型选择 -->
                    <div class="edge-type-switcher">
                      <div
                        class="edge-type-option"
                        :class="{ active: !edgeEditor.isCondition }"
                        @click="edgeEditor.isCondition = false; edgeEditor.condition = ''"
                      >
                        <el-icon><right /></el-icon>
                        <span>默认路径</span>
                        <div class="edge-type-desc">没有条件匹配时走此路径</div>
                      </div>
                      <div
                        class="edge-type-option"
                        :class="{ active: edgeEditor.isCondition }"
                        @click="edgeEditor.isCondition = true"
                      >
                        <el-icon><filter /></el-icon>
                        <span>条件路径</span>
                        <div class="edge-type-desc">上游节点输出包含关键词时走此路径</div>
                      </div>
                    </div>

                    <!-- 条件关键词输入（仅条件路径时显示） -->
                    <div v-if="edgeEditor.isCondition" class="edge-keyword-section">
                      <div class="edge-keyword-label">匹配关键词</div>
                      <el-input
                        v-model="edgeEditor.condition"
                        placeholder="如：log_query、user_audit"
                        size="small"
                        clearable
                        @keyup.enter="confirmEdgeEdit"
                      />
                      <div class="edge-keyword-hint">
                        上游智能体的输出内容中包含此关键词时，流程走本路径。
                        建议在智能体 System Prompt 中明确约束输出格式。
                      </div>
                    </div>
                    <div v-else class="edge-default-hint">
                      <el-icon><info-filled /></el-icon>
                      当所有条件路径均不匹配时，自动走此默认路径。每个节点建议保留一条默认路径。
                    </div>
                  </template>

                  <div class="edge-editor-actions">
                    <el-button size="small" type="primary" style="flex:1;" @click="confirmEdgeEdit">保存</el-button>
                    <el-button size="small" type="danger" plain style="flex:1;" @click="deleteEdgeFromEditor">删除连线</el-button>
                  </div>
                </div>
              </div>
            </transition>
          </div>
        </div>
      </div>

      <!-- 工作流试运行对话框 -->
      <el-dialog v-model="testRun.visible" title="工作流试运行" width="520px" :close-on-click-modal="false" append-to-body @close="stopTestRun">
        <el-alert type="warning" :closable="false" show-icon style="margin-bottom:10px;">
          试运行会真实调用大模型，产生 token 消耗；不会写入任何聊天会话记录。
        </el-alert>
        <el-input v-model="testRun.input" type="textarea" :rows="2" placeholder="输入一句测试问题，如：帮我查一下最近的登录日志" :disabled="testRun.running || !!testRun.approvalId" />
        <div style="margin-top:10px;">
          <el-button type="primary" :loading="testRun.running" :disabled="!!testRun.approvalId" @click="startTestRun">开始测试</el-button>
          <el-button v-if="testRun.running" @click="stopTestRun">停止</el-button>
        </div>
        <div v-if="testRun.approvalId" class="test-run-approval">
          <div class="test-run-approval-title">节点 {{ getNodeDisplayName(testRun.approvalNodeId) || testRun.approvalNodeId }} 等待审批</div>
          <el-input
            v-model="testRun.approvalFeedback"
            type="textarea"
            :rows="2"
            maxlength="2000"
            show-word-limit
            placeholder="审批意见（可选）"
            :disabled="testRun.running"
          />
          <div class="test-run-approval-actions">
            <el-button type="success" icon="CircleCheck" :loading="testRun.running" @click="decideTestRun(true)">通过并继续</el-button>
            <el-button type="danger" plain icon="CircleClose" :disabled="testRun.running" @click="decideTestRun(false)">驳回并结束</el-button>
          </div>
        </div>
        <div class="test-run-logs" style="margin-top:12px;max-height:260px;overflow:auto;">
          <div v-for="(log, i) in testRun.logs" :key="i" :class="['test-log-item', 'log-' + log.type]">
            <el-icon v-if="log.type === 'route'"><share /></el-icon>
            <el-icon v-else-if="log.type === 'done'"><circle-check /></el-icon>
            <el-icon v-else-if="log.type === 'error'"><circle-close /></el-icon>
            <el-icon v-else><loading /></el-icon>
            <span>{{ log.text }}</span>
          </div>
          <div v-if="testRun.logs.length === 0" style="color:#94a3b8;font-size:13px;text-align:center;padding:16px;">
            输入问题后点击「开始测试」，这里将实时显示执行路径。
          </div>
        </div>
      </el-dialog>
    </div>
  </transition>
  </div>
</div>
</template>
<script>
import {
  addWorkflow,
  cancelWorkflowExecution,
  delWorkflow,
  getWorkflow,
  listWorkflow,
  listWorkflowExecutors,
  streamWorkflowExecution,
  streamWorkflowTestApproval,
  updateWorkflow
} from "@/api/ai/workflow";
import {listAllAgents} from "@/api/ai/agent";
import {listAvailableModel} from "@/api/ai/model";
import {Setting as SettingIcon} from '@element-plus/icons-vue'
import draggable from "vuedraggable/dist/vuedraggable.common"
import {VueFlow} from '@vue-flow/core'
import {Background} from '@vue-flow/background'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import dagre from 'dagre'

export default {
  name: "AiWorkflowManager",
  components: {
    SettingIcon,
    draggable,
    VueFlow,
    Background
  },
  data() {
    return {
      loading: true,
      total: 0,
      workflowList: [],
      activeAgents: [],
      javaExecutors: [],
      availableModels: [],
      testRun: {
        visible: false,
        input: '',
        running: false,
        logs: [],
        activeNodeId: null,
        controller: null,
        executionId: null,
        approvalId: null,
        approvalNodeId: null,
        approvalFeedback: ''
      },
      graph: {
        nodes: [],
        edges: [],
        maxIterations: 10
      },
      isDraggingActive: false,
      activeStepIndex: null,
      viewMode: 'card',
      lastListViewMode: 'card',
      mermaidError: "",
      // 画布节点/边双向绑定（直接驱动 Vue Flow）
      vfNodes: [],
      vfEdges: [],
      // 节点属性浮层
      popover: {
        visible: false,
        node: null,
        x: 0,
        y: 0
      },
      // 边编辑浮层
      edgeEditor: {
        visible: false,
        edgeId: null,
        condition: '',
        isCondition: false,
        sourceRef: null,
        sourceBranches: null,
        x: 0,
        y: 0
      },
      // Vue Flow 默认边样式
      defaultEdgeOptions: {
        type: 'smoothstep',
        style: { stroke: '#818cf8', strokeWidth: 2 },
        markerEnd: { type: 'arrowclosed', color: '#818cf8', width: 16, height: 16 }
      },
      _nodeClickHandled: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        workflowCode: null,
        workflowName: null,
        status: null
      },
      form: {},
      rules: {
        workflowCode: [{ required: true, message: "工作流唯一编码不能为空", trigger: "blur" }],
        workflowName: [{ required: true, message: "工作流名称不能为空", trigger: "blur" }]
      }
    };
  },
  computed: {
    nodeSteps: {
      get() {
        return this.graph.nodes;
      },
      set(val) {
        this.graph.nodes = val;
      }
    },
    currentNodeConditionalEdges() {
      if (this.activeStepIndex === null || !this.nodeSteps[this.activeStepIndex]) return [];
      const currentId = this.nodeSteps[this.activeStepIndex].id;
      if (!currentId) return [];
      return this.graph.edges.filter(e => e.from === currentId && e.condition != null && e.condition.trim() !== "");
    },
    currentNodeDefaultPath: {
      get() {
        if (this.activeStepIndex === null || !this.nodeSteps[this.activeStepIndex]) return "";
        const currentId = this.nodeSteps[this.activeStepIndex].id;
        if (!currentId) return "";
        const edge = this.graph.edges.find(e => e.from === currentId && (e.condition == null || e.condition.trim() === ""));
        return edge ? edge.to : "";
      },
      set(val) {
        if (this.activeStepIndex === null || !this.nodeSteps[this.activeStepIndex]) return;
        const currentId = this.nodeSteps[this.activeStepIndex].id;
        if (!currentId) return;
        let edge = this.graph.edges.find(e => e.from === currentId && (e.condition == null || e.condition.trim() === ""));
        if (edge) {
          edge.to = val;
        } else {
          this.graph.edges.push({
            from: currentId,
            to: val,
            condition: null
          });
        }
      }
    },
    previewNodes() {
      return this.graph.nodes.filter(n => n.id);
    },
    // 分类节点可选的目标节点（排除自己和 Start，追加 End）
    classifierTargets() {
      const self = this.popover.node ? this.popover.node.id : null;
      const list = this.graph.nodes
        .filter(n => n.id && n.id !== self)
        .map(n => ({
          id: n.id,
          name: n.type === 'classifier' ? (n.clfName || '意图分类')
            : n.type === 'java' ? (this.getJavaExecutorName(n.ref) || n.ref || n.id)
            : (this.getAgentShortName(n.ref) || n.ref || n.id)
        }));
      list.push({ id: '__end__', name: 'End（结束）' });
      return list;
    }
  },
  async created() {
    // 先加载智能体/模型，保证迷你拓扑图节点名可正确解析
    await Promise.all([this.loadActiveAgents(), this.loadAvailableModels(), this.loadJavaExecutors()]);
    this.getList();
  },
  beforeUnmount() {
    this.stopTestRun();
  },
  methods: {
    async getList() {
      this.loading = true;
      try {
        const res = await listWorkflow(this.queryParams);
        if (res.code === 200) {
          this.workflowList = res.data.rows || res.data || [];
          this.total = res.data.total || this.workflowList.length;
          // 预计算每个工作流的迷你拓扑图，避免模板重复解析
          this.workflowList.forEach(item => {
            item._miniGraph = this.buildMiniGraph(item);
          });
        }
      } catch (err) {
        console.error(err);
      } finally {
        this.loading = false;
      }
    },
    async loadActiveAgents() {
      try {
        const res = await listAllAgents();
        if (res.code === 200) {
          this.activeAgents = res.data || [];
        }
      } catch (err) {
        console.error(err);
      }
    },
    async loadAvailableModels() {
      try {
        const res = await listAvailableModel();
        if (res.code === 200) {
          this.availableModels = res.data || [];
        }
      } catch (err) {
        console.error(err);
      }
    },
    async loadJavaExecutors() {
      try {
        const res = await listWorkflowExecutors();
        if (res.code === 200) this.javaExecutors = res.data || [];
      } catch (err) {
        console.error(err);
      }
    },
    parseNodes(row) {
      if (row.graphJson) {
        try {
          const graph = JSON.parse(row.graphJson);
          if (graph && graph.nodes) {
            return graph.nodes.map(n => n.ref || n.id);
          }
        } catch (e) {}
      }
      try {
        return JSON.parse(row.nodes) || [];
      } catch (e) {
        return [];
      }
    },
    getAgentShortName(node) {
      const code = (node && typeof node === 'object') ? node.ref : node;
      const agent = this.activeAgents.find(a => a.agentCode === code);
      return agent ? agent.agentName : code;
    },
    getAgentByCode(node) {
      const code = (node && typeof node === 'object') ? node.ref : node;
      return this.activeAgents.find(a => a.agentCode === code);
    },
    getJavaExecutorName(code) {
      const executor = this.javaExecutors.find(item => item.code === code);
      return executor ? executor.name : code;
    },
    getNodeDisplayName(nodeId) {
      const node = this.graph.nodes.find(n => n.id === nodeId);
      if (!node) return nodeId;
      if (node.type === 'classifier') return node.clfName || '意图分类';
      if (node.type === 'java') return this.getJavaExecutorName(node.ref) || node.ref || nodeId;
      return this.getAgentShortName(node.ref) || node.ref || nodeId;
    },
    // 构建列表卡片的只读迷你拓扑图（分层布局 + SVG 坐标）
    buildMiniGraph(item) {
      let graph = null;
      if (item.graphJson) {
        try { graph = JSON.parse(item.graphJson); } catch (e) {}
      }
      if (!graph || !graph.nodes || !graph.nodes.length) {
        // 老数据兜底：nodes 数组串成单链
        let arr = [];
        try { arr = JSON.parse(item.nodes) || []; } catch (e) {}
        const gnodes = arr.map(x => {
          const ref = typeof x === 'string' ? x : (x.ref || x.id);
          const type = this.javaExecutors.some(executor => executor.code === ref) ? 'java' : 'agent';
          return { ref, id: ref, type };
        });
        const gedges = [];
        for (let i = 0; i < gnodes.length - 1; i++) {
          gedges.push({ from: gnodes[i].id, to: gnodes[i + 1].id });
        }
        if (gnodes.length) {
          gedges.unshift({ from: '__start__', to: gnodes[0].id });
          gedges.push({ from: gnodes[gnodes.length - 1].id, to: '__end__' });
        }
        graph = { nodes: gnodes, edges: gedges };
      }

      const nodes = graph.nodes || [];
      const edges = JSON.parse(JSON.stringify(graph.edges || []));

      // 自动补全首尾边以防 BFS 崩溃及开始/结束节点隐形
      if (nodes.length > 0) {
        const hasStartEdge = edges.some(e => e.from === '__start__');
        if (!hasStartEdge) {
          const firstNode = nodes[0].id;
          edges.unshift({ from: '__start__', to: firstNode });
        }
        const hasEndEdge = edges.some(e => e.to === '__end__');
        if (!hasEndEdge) {
          const lastNode = nodes[nodes.length - 1].id;
          edges.push({ from: lastNode, to: '__end__' });
        }
      }
      const allIds = ['__start__', ...nodes.map(n => n.id), '__end__'];
      const nodeMap = {};
      nodes.forEach(n => { nodeMap[n.id] = n; });

      // BFS 分层使用首次到达层级，条件循环不会把预览图高度无限抬高。
      const level = { '__start__': 0 };
      const adj = {};
      edges.forEach(e => { (adj[e.from] = adj[e.from] || []).push(e.to); });
      const queue = ['__start__'];
      let guard = 0;
      while (queue.length && guard < 1000) {
        guard++;
        const cur = queue.shift();
        (adj[cur] || []).forEach(to => {
          const nl = (level[cur] || 0) + 1;
          if (level[to] == null) {
            level[to] = nl;
            queue.push(to);
          }
        });
      }
      let maxLv = 0;
      Object.values(level).forEach(v => { if (v > maxLv) maxLv = v; });
      allIds.forEach(id => { if (level[id] == null) level[id] = maxLv + 1; });
      const endLv = Math.max.apply(null, allIds.map(id => level[id]));
      level['__end__'] = endLv;

      const byLevel = {};
      allIds.forEach(id => { (byLevel[level[id]] = byLevel[level[id]] || []).push(id); });

      const W = 300, rowH = 46, nodeW = 78, nodeH = 26;
      const layers = Object.keys(byLevel).map(Number).sort((a, b) => a - b);
      const pos = {};
      layers.forEach(lv => {
        const row = byLevel[lv];
        const gap = W / (row.length + 1);
        row.forEach((id, i) => { pos[id] = { x: gap * (i + 1), y: 16 + layers.indexOf(lv) * rowH }; });
      });

      const svgH = 32 + layers.length * rowH;
      const drawNodes = allIds.map(id => {
        const isTerm = id === '__start__' || id === '__end__';
        const n = nodeMap[id];
        const type = isTerm ? 'term' : (n ? (n.type || 'agent') : 'agent');
        const name = isTerm ? (id === '__start__' ? '开始' : '结束')
          : (type === 'classifier' ? (n.clfName || '意图分类')
            : type === 'java' ? (this.getJavaExecutorName(n.ref) || n.ref || id)
            : (this.getAgentShortName(n.ref) || n.ref || id));
        return { id, x: pos[id].x, y: pos[id].y, type, name };
      });

      let agentCount = 0;
      let classifierCount = 0;
      let javaCount = 0;
      drawNodes.forEach(n => {
        if (n.type === 'agent') agentCount++;
        else if (n.type === 'classifier') classifierCount++;
        else if (n.type === 'java') javaCount++;
      });

      const drawEdges = edges
        .filter(e => pos[e.from] && pos[e.to])
        .map(e => {
          const x1 = pos[e.from].x;
          const x2 = pos[e.to].x;
          return {
            x1, y1: pos[e.from].y + nodeH / 2,
            x2: x1 === x2 ? x2 + 0.01 : x2, y2: pos[e.to].y - nodeH / 2,
            cond: !!(e.condition && e.condition.trim())
          };
        });

      return { 
        width: W, 
        height: svgH, 
        nodeW, 
        nodeH, 
        nodes: drawNodes, 
        edges: drawEdges,
        agentCount,
        classifierCount,
        javaCount
      };
    },
    previewEdgeLabel(nodeIndex) {
      const validNodes = this.previewNodes;
      if (nodeIndex < 0 || nodeIndex >= validNodes.length) return '';
      // 查找从前一节点（或 __start__）到当前节点的边上是否有 condition
      const fromId = nodeIndex === 0 ? '__start__' : validNodes[nodeIndex - 1].id;
      const toId = validNodes[nodeIndex].id;
      const edge = this.graph.edges.find(e => e.from === fromId && e.to === toId && e.condition);
      return edge ? edge.condition : '';
    },
    toggleStatus(row) {
      row.status = row.status === "1" ? "0" : "1";
      this.handleStatusChange(row);
    },
    async handleStatusChange(row) {
      const text = row.status === "1" ? "启用" : "禁用";
      try {
        const res = await updateWorkflow(row);
        if (res.code === 200) {
          this.$message.success(text + "成功");
        } else {
          this.$message.error(res.msg || "操作失败");
          row.status = row.status === "1" ? "0" : "1";
        }
      } catch (err) {
        row.status = row.status === "1" ? "0" : "1";
      }
    },
    formatDate(dateStr) {
      if (!dateStr) return "";
      return dateStr.substring(0, 10);
    },
    cancel() {
      this.viewMode = this.lastListViewMode || 'card';
      this.activeStepIndex = null;
      this.reset();
    },
    reset() {
      this.form = {
        id: null,
        workflowCode: null,
        workflowName: null,
        description: null,
        nodes: null,
        status: "1",
        remark: null
      };
      this.graph = {
        nodes: [],
        edges: [],
        maxIterations: 10
      };
      this.activeStepIndex = null;
      if (this.$refs.form) {
        this.$refs.form.resetFields();
      }
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() {
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        workflowCode: null,
        workflowName: null,
        status: null
      };
      this.handleQuery();
    },
    generateNodeId(type = 'node') {
      let id;
      do {
        id = `${type}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
      } while (this.graph.nodes.some(node => node.id === id));
      return id;
    },
    addNodeStep(nodeType = 'agent') {
      // el-dropdown 的 command 会传字符串；直接点击（非分类场景）时兜底为 agent
      if (typeof nodeType !== 'string') nodeType = 'agent';
      const isClassifier = nodeType === 'classifier';
      const isJava = nodeType === 'java';
      const uid = Date.now() + '_' + Math.random().toString(36).substr(2, 5);
      const nodeId = this.generateNodeId(isClassifier ? 'classifier' : 'node');
      const newStep = {
        _uid: uid,
        id: nodeId,
        ref: '',
        type: nodeType,
        requireApproval: false,
        timeoutSeconds: 120,
        branchMode: false,
        branches: isClassifier ? [] : undefined,
        defaultTarget: isClassifier ? '' : undefined,
        modelConfigId: null,
        clfName: isClassifier ? '意图分类' : undefined
      };
      this.graph.nodes.push(newStep);

      // 直接加入画布，保留用户放置新节点时的位置。
      const centerX = 300 + Math.random() * 200;
      const centerY = 200 + Math.random() * 100;
      this.vfNodes.push({
        id: nodeId,
        type: 'default',
        position: { x: centerX, y: centerY },
        data: { label: isClassifier ? '意图分类' : '未配置节点' },
        class: isClassifier ? 'vf-node-classifier'
          : isJava ? 'vf-node-java vf-node-unconfigured' : 'vf-node-agent vf-node-unconfigured',
        draggable: true,
        connectable: true,
        selectable: true,
        style: { width: '110px' },
        _stepUid: uid
      });

      // 立刻把属性面板切换到新节点，方便用户直接配置
      this.popover = { visible: true, node: newStep, x: 0, y: 0 };
      this.edgeEditor.visible = false;
    },
    deleteNodeStep(index) {
      const nodeId = this.graph.nodes[index] && this.graph.nodes[index].id;
      this.graph.nodes.splice(index, 1);
      if (nodeId) {
        this.graph.edges = this.graph.edges.filter(e => e.from !== nodeId && e.to !== nodeId);
        // 清理分类节点中引用该已删除节点的 branches 分支出口
        this.graph.nodes.forEach(n => {
          if (n.type === 'classifier' && n.branches) {
            n.branches = n.branches.filter(b => b.targetId !== nodeId);
          }
        });
      }
      this.syncEdges();
      this.buildVfGraph();
    },
    addBranchRoute(stepIndex) {
      const currentId = this.nodeSteps[stepIndex].id;
      if (!currentId) return;
      this.graph.edges.push({
        from: currentId,
        to: "__end__",
        condition: "新路由"
      });
    },
    deleteBranchRoute(stepIndex, routeIndex) {
      const currentId = this.nodeSteps[stepIndex].id;
      if (!currentId) return;
      const conditionalEdges = this.graph.edges.filter(e => e.from === currentId && e.condition != null);
      if (conditionalEdges[routeIndex]) {
        const edge = conditionalEdges[routeIndex];
        const idx = this.graph.edges.indexOf(edge);
        if (idx !== -1) {
          this.graph.edges.splice(idx, 1);
        }
      }
    },
    syncEdges() {
      const activeIds = new Set(this.nodeSteps.map(n => n.id).filter(Boolean));
      activeIds.add("__end__");

      // 分类节点：其所有出边（多分支）原样保留，绝不折叠或串行覆盖
      const classifierIds = new Set(
        this.nodeSteps.filter(n => n.type === 'classifier' && n.id).map(n => n.id)
      );

      // 一旦工作流进入图模式（存在分类节点），完全关闭自动串行补边，
      // 边由用户显式建立（分类面板配置 / 手动拖线），仅清理指向已删除节点的悬空边。
      if (classifierIds.size > 0) {
        this.graph.edges = this.graph.edges.filter(
          e => (e.from === '__start__' || activeIds.has(e.from)) && activeIds.has(e.to)
        );
        return;
      }

      const classifierEdges = this.graph.edges.filter(
        e => classifierIds.has(e.from) && activeIds.has(e.to)
      );

      // 1. 保留所有合法的条件出边 (源节点开启了 branchMode，目标节点也存在；分类节点已单独处理)
      const conditionalEdges = this.graph.edges.filter(e => {
        if (e.condition == null || e.condition.trim() === "") return false;
        if (classifierIds.has(e.from)) return false;
        const fromNode = this.nodeSteps.find(n => n.id === e.from);
        return fromNode && fromNode.branchMode && activeIds.has(e.to);
      });

      const normalEdges = [];

      // start → 第一个节点
      if (this.nodeSteps.length > 0 && this.nodeSteps[0].id) {
        normalEdges.push({ from: "__start__", to: this.nodeSteps[0].id, condition: null });
      }

      this.nodeSteps.forEach((step, index) => {
        const fromNode = step.id;
        if (!fromNode) return;

        // 分类节点出边已原样保留，跳过串行/分支补边
        if (classifierIds.has(fromNode)) return;

        if (step.branchMode) {
          // 分支节点：只追加用户已明确设置的兜底路径边，不自动串下一个数组项
          const existingDefault = this.graph.edges.find(
            e => e.from === fromNode && (e.condition == null || e.condition.trim() === "")
          );
          if (existingDefault && activeIds.has(existingDefault.to) && existingDefault.to !== fromNode) {
            normalEdges.push({ from: fromNode, to: existingDefault.to, condition: null });
          }
          // 若用户还没设置兜底路径，不追加任何默认串行边（等用户在右侧面板选择）
        } else {
          // 非分支节点：串行连到下一个节点或 __end__
          const target = index < this.nodeSteps.length - 1 ? this.nodeSteps[index + 1].id : "__end__";
          if (target) {
            normalEdges.push({ from: fromNode, to: target, condition: null });
          }
        }
      });

      this.graph.edges = [...classifierEdges, ...conditionalEdges, ...normalEdges];
    },
    handleRefChange() {
      this.syncEdges();
    },
    handleBranchModeChange() {
      this.syncEdges();
    },
    handleEdgeChange() {
      this.syncEdges();
    },

    // 保存时序列化 graph 数据，过滤掉前端临时属性
    buildGraphJsonForSubmit() {
      const cleanNodes = this.graph.nodes
        .filter(n => n.id && (n.type === 'classifier' || n.ref))
        .map(n => {
          const base = {
            id: n.id,
            ref: n.ref || null,
            type: n.type || 'agent',
            requireApproval: !!n.requireApproval,
            timeoutSeconds: n.timeoutSeconds || 120
          };
          if (n.type === 'classifier') {
            base.branches = (n.branches || [])
              .filter(b => b.slug && b.label && b.label.trim())
              .map(b => ({ slug: b.slug, label: b.label.trim() }));
            base.modelConfigId = n.modelConfigId || null;
            base.clfName = n.clfName || '意图分类';
          }
          return base;
        });
      const nodeIds = new Set(cleanNodes.map(node => node.id));
      nodeIds.add('__start__');
      nodeIds.add('__end__');
      const cleanEdges = this.graph.edges.filter(edge => nodeIds.has(edge.from) && nodeIds.has(edge.to));
      return JSON.stringify({
        nodes: cleanNodes,
        edges: cleanEdges,
        maxIterations: this.graph.maxIterations || 10
      });
    },

    focusNodeByUid(uid) {
      const idx = this.nodeSteps.findIndex(n => n._uid === uid);
      if (idx !== -1) this.activeStepIndex = idx;
    },

    // ── Vue Flow 画布交互 ──────────────────────────────────────────
    buildVfGraph() {
      const nodes = this.graph.nodes.filter(n => n.id);
      const rawEdges = this.graph.edges || [];
      const NODE_W = 110;
      const NODE_H = 32;
      const allIds = ['__start__', ...nodes.map(n => n.id), '__end__'];
      const idSet = new Set(allIds);

      const seenKey = new Set();
      const edges = rawEdges.filter(e => {
        if (!idSet.has(e.from) || !idSet.has(e.to)) return false;
        const k = `${e.from}=>${e.to}=>${e.condition || ''}`;
        if (seenKey.has(k)) return false;
        seenKey.add(k);
        return true;
      });

      const g = new dagre.graphlib.Graph({ multigraph: true });
      g.setGraph({ rankdir: 'TB', nodesep: 60, ranksep: 60, marginx: 40, marginy: 30 });
      g.setDefaultEdgeLabel(() => ({}));
      allIds.forEach(id => {
        const isTerm = (id === '__start__' || id === '__end__');
        g.setNode(id, { width: isTerm ? 70 : NODE_W, height: NODE_H });
      });
      edges.forEach((e, i) => g.setEdge(e.from, e.to, {}, `e${i}`));
      dagre.layout(g);

      const newNodes = allIds.map(id => {
        const p = g.node(id);
        const isTerm = id === '__start__' || id === '__end__';
        const stepNode = this.graph.nodes.find(n => n.id === id);
        const isClassifier = stepNode && stepNode.type === 'classifier';
        const isJava = stepNode && stepNode.type === 'java';
        const isUnconfigured = stepNode && !isClassifier && !stepNode.ref;
        const label = isTerm ? (id === '__start__' ? 'Start' : 'End')
          : isClassifier ? (stepNode.clfName || '意图分类')
          : isUnconfigured ? '未配置节点'
          : isJava ? (this.getJavaExecutorName(stepNode.ref) || stepNode.ref || id)
          : (this.getAgentShortName(stepNode.ref) || stepNode.ref || id);
        const existing = this.vfNodes.find(n => n.id === id);
        return {
          id,
          type: 'default',
          position: existing ? existing.position : { x: p.x - p.width / 2, y: p.y - p.height / 2 },
          data: { label },
          class: isTerm ? 'vf-node-terminal' : isClassifier ? 'vf-node-classifier'
            : isJava ? (isUnconfigured ? 'vf-node-java vf-node-unconfigured' : 'vf-node-java')
            : isUnconfigured ? 'vf-node-agent vf-node-unconfigured' : 'vf-node-agent',
          draggable: !isTerm,
          connectable: true,
          selectable: !isTerm,
          style: isTerm ? { width: '80px' } : { width: NODE_W + 'px' }
        };
      });

      const newEdges = edges.map((e, i) => {
        let cond = (e.condition && e.condition.trim()) ? e.condition.trim() : '';
        // 分类节点出边：把 slug 显示成分支中文名（仅改显示，graph.edges 里仍存 slug）
        const srcNode = this.graph.nodes.find(n => n.id === e.from);
        if (cond && srcNode && srcNode.type === 'classifier' && srcNode.branches) {
          const br = srcNode.branches.find(b => b.slug === cond);
          if (br) cond = br.label;
        }
        return {
          id: `vfe-${e.from}-${e.to}-${i}`,
          source: e.from,
          target: e.to,
          data: { condition: e.condition || null },
          type: 'smoothstep',
          label: cond,
          labelStyle: { fontSize: '11px', fontWeight: 700, fill: cond ? '#f59e0b' : '#a5b4fc' },
          labelBgStyle: { fill: cond ? 'rgba(251,191,36,0.15)' : 'rgba(129,140,248,0.1)', stroke: cond ? 'rgba(251,191,36,0.4)' : 'rgba(129,140,248,0.3)' },
          labelBgPadding: [4, 3],
          labelBgBorderRadius: 4,
          animated: !!cond,
          style: { stroke: cond ? '#f59e0b' : '#818cf8', strokeWidth: 2 },
          markerEnd: { type: 'arrowclosed', color: cond ? '#f59e0b' : '#818cf8', width: 16, height: 16 }
        };
      });

      this.vfNodes = newNodes;
      this.vfEdges = newEdges;
    },

    // 拖拽节点后同步位置到 graph.nodes
    onNodeDragStop({ node }) {
      const step = this.graph.nodes.find(n => n.id === node.id);
      if (step) step._vfPos = { ...node.position };
    },

    // 连线创建（拖拽 handle）
    onConnect(params) {
      const { source, target } = params;
      if (!source || !target || source === target) return;

      // 防重复
      const exists = this.graph.edges.find(e => e.from === source && e.to === target);
      if (exists) return;

      const sourceNode = this.graph.nodes.find(n => n.id === source);
      const isClassifier = sourceNode && sourceNode.type === 'classifier';
      let slug = null;

      // 如果源节点是分类路由节点，联动在 sourceNode.branches 自动新增分支
      if (isClassifier) {
        if (!sourceNode.branches) sourceNode.branches = [];
        const hasBranch = sourceNode.branches.some(b => b.targetId === target);
        if (!hasBranch) {
          slug = 'br_' + Math.random().toString(36).substr(2, 6);
          const targetNode = this.graph.nodes.find(n => n.id === target);
          const targetName = targetNode ? (targetNode.clfName || this.getAgentShortName(targetNode.ref) || target) : target;
          sourceNode.branches.push({
            slug,
            label: targetName,
            targetId: target
          });
        } else {
          const br = sourceNode.branches.find(b => b.targetId === target);
          if (br) slug = br.slug;
        }
      }

      // 同步到 graph.edges
      this.graph.edges.push({ from: source, to: target, condition: slug });

      // 标记分支
      if (sourceNode) {
        const outEdges = this.graph.edges.filter(e => e.from === source);
        if (outEdges.length > 1) sourceNode.branchMode = true;
      }

      // 直接往 vfEdges push，不重建整图（防止占位节点被覆盖）
      const edgeStyle = isClassifier ? { stroke: '#f59e0b', strokeWidth: 2 } : { stroke: '#818cf8', strokeWidth: 2 };
      const condLabel = isClassifier ? (sourceNode.branches.find(b => b.targetId === target)?.label || '') : '';
      this.vfEdges.push({
        id: `vfe-${source}-${target}-${Date.now()}`,
        source,
        target,
        data: { condition: slug },
        type: 'smoothstep',
        label: condLabel,
        animated: isClassifier,
        style: edgeStyle,
        markerEnd: { type: 'arrowclosed', color: isClassifier ? '#f59e0b' : '#818cf8', width: 16, height: 16 }
      });
    },

    // 双击边打开条件编辑浮层
    onEdgeDoubleClick({ edge }) {
      const sourceRef = edge.source;
      const sourceNode = this.graph.nodes.find(n => n.id === sourceRef);
      const isClassifierSource = sourceNode && sourceNode.type === 'classifier';
      // 找到 graph.edges 里对应的真实边，读取其原始 condition（画布 label 分类节点显示的是中文名）
      const ve = this.vfEdges.find(v => v.id === edge.id);
      const ge = ve ? this.graph.edges.find(e => e.from === ve.source && e.to === ve.target
        && (e.condition || null) === (ve.data?.condition || null)) : null;
      const rawCondition = ge && ge.condition ? ge.condition : '';

      this.edgeEditor.edgeId = edge.id;
      this.edgeEditor.sourceRef = sourceRef;
      this.edgeEditor.sourceBranches = isClassifierSource ? (sourceNode.branches || []) : null;
      this.edgeEditor.condition = isClassifierSource ? rawCondition : (edge.label || '');
      this.edgeEditor.isCondition = !!(edge.label || '');
      this.edgeEditor.visible = true;
      this.popover.visible = false;
    },

    // 确认边条件编辑
    confirmEdgeEdit() {
      const ge = this.graph.edges.find(e => {
        const expectedId = this.vfEdges.find(ve => ve.id === this.edgeEditor.edgeId);
        return expectedId && e.from === expectedId.source && e.to === expectedId.target
          && (e.condition || null) === (expectedId.data?.condition || null);
      });
      if (ge) {
        if (this.edgeEditor.sourceBranches) {
          // 分类节点出边：condition 直接取选中的 slug（空=默认兜底边）
          ge.condition = this.edgeEditor.condition ? this.edgeEditor.condition : null;
        } else {
          // 非分类节点：沿用原逻辑
          ge.condition = this.edgeEditor.isCondition ? (this.edgeEditor.condition.trim() || null) : null;
          const sourceNode = this.graph.nodes.find(n => n.id === ge.from);
          if (sourceNode && ge.condition) sourceNode.branchMode = true;
        }
      }
      this.edgeEditor.visible = false;
      this.buildVfGraph();
    },

    // 从浮层删除边
    deleteEdgeFromEditor() {
      const ve = this.vfEdges.find(e => e.id === this.edgeEditor.edgeId);
      if (ve) {
        this.graph.edges = this.graph.edges.filter(e => !(e.from === ve.source && e.to === ve.target
          && (e.condition || null) === (ve.data?.condition || null)));
        // 若源节点是分类路由节点，联动清理分支定义
        const sourceNode = this.graph.nodes.find(n => n.id === ve.source);
        if (sourceNode && sourceNode.type === 'classifier' && sourceNode.branches) {
          sourceNode.branches = sourceNode.branches.filter(b => b.slug !== ve.data?.condition);
        }
      }
      this.edgeEditor.visible = false;
      this.buildVfGraph();
    },

    // 点击节点：计算浮层位置并显示
    onCanvasNodeClick({ node }) {
      if (node.id === '__start__' || node.id === '__end__') return;
      const step = this.graph.nodes.find(n => n.id === node.id);
      if (!step) return;
      this.popover = { visible: true, node: step, x: 0, y: 0 };
      this.edgeEditor.visible = false;
      // 阻止冒泡到 pane-click
      this._nodeClickHandled = true;
      setTimeout(() => { this._nodeClickHandled = false; }, 100);
    },

    // 点画布空白处关闭浮层
    onPaneClick() {
      if (this._nodeClickHandled) return;
      this.popover.visible = false;
      this.edgeEditor.visible = false;
    },

    closePopover() {
      this.popover.visible = false;
    },

    // 浮层里修改了 ref
    onPopoverRefChange() {
      if (!this.popover.node) return;
      this.syncEdges();
      this.buildVfGraph();
    },

    // 浮层里修改了其他属性
    onPopoverChange() {
      this.buildVfGraph();
    },

    // 分类节点：新增一个分支出口
    addBranch() {
      if (!this.popover.node.branches) this.popover.node.branches = [];
      this.popover.node.branches.push({
        slug: 'br_' + Math.random().toString(36).substr(2, 6),
        label: '',
        targetId: ''
      });
    },

    // 分类节点：删除分支出口，并联动同步边
    removeBranch(index) {
      this.popover.node.branches.splice(index, 1);
      this.syncClassifierBranches();
    },

    // 分类节点：选中目标节点后自动预填含义（用户未手填时）
    onBranchTargetChange(branch) {
      if (branch && branch.targetId && (!branch.label || !branch.label.trim())) {
        const t = this.classifierTargets.find(x => x.id === branch.targetId);
        if (t) branch.label = t.name;
      }
      this.syncClassifierBranches();
    },

    // 分类节点：把分支 + 默认路径同步为该节点的出边（画布只读展示）
    syncClassifierBranches() {
      const node = this.popover.node;
      if (!node || node.type !== 'classifier' || !node.id) return;
      const selfId = node.id;
      // 先移除该分类节点原有的所有出边
      this.graph.edges = this.graph.edges.filter(e => e.from !== selfId);
      // 每条有目标的分支 → 一条带 slug condition 的边
      (node.branches || []).forEach(b => {
        if (b.targetId) {
          this.graph.edges.push({ from: selfId, to: b.targetId, condition: b.slug });
        }
      });
      // 默认兜底路径 → 一条 condition 为空的边
      if (node.defaultTarget) {
        this.graph.edges.push({ from: selfId, to: node.defaultTarget, condition: null });
      }
      this.buildVfGraph();
    },

    // 分类节点：名称/模型等非分支属性变更后重建画布
    onClassifierChange() {
      this.buildVfGraph();
    },

    // ── 试运行 ────────────────────────────────────────────────
    openTestRun() {
      if (!this.form.id || !this.form.workflowCode) {
        this.$modal.confirm('试运行前需要先保存工作流，是否现在保存？').then(() => {
          this.submitForm();
        }).catch(() => {});
        return;
      }
      this.testRun.visible = true;
      this.testRun.logs = [];
      this.testRun.input = '';
      this.resetTestRunApproval();
    },

    async startTestRun() {
      if (!this.testRun.input.trim()) {
        this.$message.warning('请输入测试问题');
        return;
      }
      this.testRun.running = true;
      this.testRun.logs = [];
      this.clearNodeHighlight();

      const controller = new AbortController();
      this.testRun.controller = controller;
      this.testRun.executionId = null;
      this.resetTestRunApproval();

      try {
        await streamWorkflowExecution({
          workflowCode: this.form.workflowCode,
          message: this.testRun.input,
          testRun: true
        }, (event, envelope) => this.handleTestRunEvent(event, envelope), controller.signal);
      } catch (err) {
        if (err.name !== 'AbortError') {
          this.testRun.logs.push({ type: 'error', text: '执行失败: ' + err.message });
        }
      } finally {
        this.testRun.running = false;
        this.testRun.controller = null;
      }
    },

    handleTestRunEvent(event, envelope) {
      const payload = envelope.payload || {};
      const nodeCode = envelope.nodeId || '';
      this.testRun.executionId = envelope.executionId || this.testRun.executionId;
      if (event === 'node_start') {
        const nodeName = payload.nodeName || nodeCode;
        this.testRun.logs.push({ type: 'start', text: `执行节点：${this.getNodeDisplayName(nodeCode) || nodeName}` });
        this.highlightNode(nodeCode);
      } else if (event === 'node_route') {
        const decision = payload.route || '';
        let label = decision;
        const srcNode = this.graph.nodes.find(n => n.id === nodeCode);
        if (srcNode && srcNode.branches) {
          const br = srcNode.branches.find(b => b.slug === decision);
          if (br) label = br.label;
        }
        this.testRun.logs.push({ type: 'route', text: `分类判定 → ${label || '（无匹配，走默认路径）'}` });
        this.highlightRoute(nodeCode, decision);
      } else if (event === 'node_done') {
        this.testRun.logs.push({ type: 'done', text: `节点完成：${this.getNodeDisplayName(nodeCode) || nodeCode}` });
      } else if (event === 'status') {
        if (payload.message) {
          this.testRun.logs.push({ type: 'route', text: payload.message });
        }
      } else if (event === 'search_sources') {
        this.testRun.logs.push({
          type: 'route',
          text: `联网搜索完成：${payload.count || (payload.sources || []).length} 个来源`
        });
      } else if (event === 'workflow_done') {
        this.testRun.logs.push({ type: 'done', text: '✓ 工作流执行完成' });
        this.testRun.running = false;
        this.testRun.executionId = null;
        this.resetTestRunApproval();
      } else if (event === 'workflow_rejected') {
        this.testRun.logs.push({ type: 'error', text: '试运行已驳回并结束' });
        this.testRun.running = false;
        this.testRun.executionId = null;
        this.resetTestRunApproval();
      } else if (event === 'error') {
        this.testRun.logs.push({ type: 'error', text: payload.message || '工作流执行失败' });
        this.testRun.running = false;
      } else if (event === 'node_error') {
        this.testRun.logs.push({ type: 'error', text: `节点异常：${payload.message || nodeCode}` });
      } else if (event === 'node_interrupt') {
        this.testRun.logs.push({ type: 'route', text: `等待审批：${nodeCode}` });
        this.testRun.approvalId = payload.approvalId;
        this.testRun.approvalNodeId = nodeCode;
        this.testRun.approvalFeedback = '';
        this.testRun.running = false;
      } else if (event === 'execution_resumed') {
        this.resetTestRunApproval();
      }
    },

    async decideTestRun(approve) {
      if (!this.testRun.executionId || !this.testRun.approvalId || this.testRun.running) {
        return;
      }
      const executionId = this.testRun.executionId;
      const approvalId = this.testRun.approvalId;
      const controller = new AbortController();
      this.testRun.controller = controller;
      this.testRun.running = true;
      this.testRun.logs.push({
        type: 'route',
        text: approve ? '审批通过，继续执行' : '审批驳回，结束试运行'
      });
      try {
        await streamWorkflowTestApproval(
          executionId,
          approvalId,
          { approve, feedback: this.testRun.approvalFeedback.trim() },
          (event, envelope) => this.handleTestRunEvent(event, envelope),
          controller.signal
        );
      } catch (err) {
        if (err.name !== 'AbortError') {
          this.testRun.logs.push({ type: 'error', text: '审批失败: ' + err.message });
        }
      } finally {
        this.testRun.running = false;
        if (this.testRun.controller === controller) {
          this.testRun.controller = null;
        }
      }
    },

    resetTestRunApproval() {
      this.testRun.approvalId = null;
      this.testRun.approvalNodeId = null;
      this.testRun.approvalFeedback = '';
    },

    stopTestRun() {
      if (this.testRun.executionId) {
        cancelWorkflowExecution(this.testRun.executionId).catch(() => {});
      }
      if (this.testRun.controller) {
        this.testRun.controller.abort();
        this.testRun.controller = null;
      }
      this.testRun.running = false;
      this.testRun.executionId = null;
      this.resetTestRunApproval();
      this.clearNodeHighlight();
    },

    highlightNode(nodeId) {
      this.testRun.activeNodeId = nodeId;
      this.vfNodes = this.vfNodes.map(n => {
        const base = (n.class || '').replace(/ ?vf-node-active/g, '');
        return { ...n, class: n.id === nodeId ? base + ' vf-node-active' : base };
      });
    },

    highlightRoute(fromId, decision) {
      this.vfEdges = this.vfEdges.map(e => {
        if (e.source !== fromId) return e;
        const cond = e.data?.condition || '';
        const isActive = decision ? (cond === decision) : (!cond);
        if (!isActive) return e;
        return { ...e, animated: true, style: { ...e.style, stroke: '#22c55e', strokeWidth: 3 } };
      });
    },

    clearNodeHighlight() {
      this.testRun.activeNodeId = null;
      this.vfNodes = this.vfNodes.map(n => ({ ...n, class: (n.class || '').replace(/ ?vf-node-active/g, '') }));
      this.buildVfGraph();
    },

    // 浮层删除节点
    deletePopoverNode() {
      const step = this.popover.node;
      if (!step) return;
      const nodeId = step.id;
      const uid = step._uid;
      // 从 graph.nodes 删除
      const idx = this.graph.nodes.findIndex(n => n._uid === uid);
      if (idx !== -1) this.graph.nodes.splice(idx, 1);
      // 删除关联边
      if (nodeId) this.graph.edges = this.graph.edges.filter(e => e.from !== nodeId && e.to !== nodeId);
      // 从 vfNodes 删除占位或正式节点
      this.vfNodes = this.vfNodes.filter(n => n.id !== uid && n.id !== nodeId);
      this.vfEdges = this.vfEdges.filter(e => e.source !== nodeId && e.target !== nodeId);
      this.popover.visible = false;
      this.syncEdges();
    },

    // 自动对齐（重新 dagre 布局，忽略已有 position）
    autoLayout() {
      this.vfNodes = [];
      this.buildVfGraph();
    },

    // 适应屏幕
    fitView() {
      if (this.$refs.vueFlowRef && this.$refs.vueFlowRef.fitView) {
        this.$refs.vueFlowRef.fitView({ padding: 0.2 });
      }
    },

    handleAdd() {
      this.reset();
      this.vfNodes = [];
      this.vfEdges = [];
      this.popover.visible = false;
      this.edgeEditor.visible = false;
      this.viewMode = 'edit';
      this.$nextTick(() => {
        this.buildVfGraph();
        window.scrollTo({ top: 0, behavior: 'smooth' });
      });
    },
    async handleUpdate(row) {
      this.reset();
      this.popover.visible = false;
      this.edgeEditor.visible = false;
      try {
        const res = await getWorkflow(row.id);
        const workflowData = (res && res.code === 200) ? res.data : null;
        if (!workflowData) {
          this.$message.error(res?.msg || '获取工作流数据失败');
          return;
        }
        this.form = workflowData;
        let graphObj = null;
        if (this.form.graphJson) {
          try {
            graphObj = JSON.parse(this.form.graphJson);
          } catch (e) {
            console.error("解析 graphJson 失败", e);
          }
        }

        if (graphObj && graphObj.nodes) {
          this.graph = graphObj;
          if (!Array.isArray(this.graph.edges)) this.graph.edges = [];
          if (!this.graph.maxIterations) this.graph.maxIterations = 10;
          const edgesByFrom = {};
          if (this.graph.edges) {
            this.graph.edges.forEach(e => {
              if (!edgesByFrom[e.from]) edgesByFrom[e.from] = [];
              edgesByFrom[e.from].push(e);
            });
          }
          const usedIds = new Set();
          this.graph.nodes = this.graph.nodes.map((n, index) => {
            const stepRef = n.ref || (n.type !== 'classifier' ? n.id : '');
            let stepId = n.id || n.ref || `node_${index + 1}`;
            while (usedIds.has(stepId)) stepId = this.generateNodeId(n.type === 'classifier' ? 'classifier' : 'node');
            usedIds.add(stepId);
            const step = {
              _uid: Date.now() + '_' + Math.random().toString(36).substr(2, 5),
              id: stepId,
              ref: stepRef,
              type: n.type || (this.javaExecutors.some(executor => executor.code === stepRef) ? 'java' : 'agent'),
              requireApproval: !!n.requireApproval,
              timeoutSeconds: n.timeoutSeconds || 120,
              branchMode: false,
              routes: [],
              defaultPath: "",
              branches: n.type === 'classifier'
                ? (n.branches || []).map(branch => ({ ...branch, targetId: branch.targetId || branch.targetRef || '' }))
                : undefined,
              defaultTarget: n.type === 'classifier' ? '' : undefined,
              modelConfigId: n.modelConfigId || null,
              clfName: n.clfName || (n.type === 'classifier' ? '意图分类' : undefined)
            };
            const outEdges = edgesByFrom[stepId] || [];
            const conditionalEdges = outEdges.filter(e => e.condition != null && e.condition.trim() !== "");
            const defaultEdge = outEdges.find(e => e.condition == null || e.condition.trim() === "");
            if (n.type === 'classifier') {
              // 分类节点：从边反推每条分支的目标节点与默认兜底路径
              (step.branches || []).forEach(b => {
                const edge = conditionalEdges.find(e => e.condition === b.slug);
                b.targetId = edge ? edge.to : '';
              });
              if (defaultEdge) step.defaultTarget = defaultEdge.to;
            } else if (conditionalEdges.length > 0) {
              step.branchMode = true;
              step.routes = conditionalEdges.map(e => ({ condition: e.condition, targetId: e.to }));
              if (defaultEdge) step.defaultPath = defaultEdge.to;
            }
            return step;
          });
          // 保留后端存储的真实边关系，不走 syncEdges（syncEdges 只适合串行自动补边）
        } else {
          try {
            const oldNodes = JSON.parse(this.form.nodes) || [];
            this.nodeSteps = oldNodes.map(item => {
              const refCode = typeof item === 'string' ? item : (item.ref || item.id);
              return {
                _uid: Date.now() + '_' + Math.random().toString(36).substr(2, 5),
                id: this.generateNodeId('node'),
                ref: refCode,
                type: typeof item === 'string'
                  ? (this.javaExecutors.some(executor => executor.code === item) ? 'java' : 'agent')
                  : (item.type || (this.javaExecutors.some(executor => executor.code === refCode) ? 'java' : 'agent')),
                requireApproval: typeof item === 'string' ? false : !!item.requireApproval,
                timeoutSeconds: typeof item === 'string' ? 120 : (item.timeoutSeconds || 120),
                branchMode: false,
                routes: [],
                defaultPath: ""
              };
            });
            this.syncEdges();
          } catch (e) {
            this.nodeSteps = [];
          }
        }
        this.viewMode = 'edit';
        this.$nextTick(() => {
          this.buildVfGraph();
          window.scrollTo({ top: 0, behavior: 'smooth' });
        });
      } catch (err) {
        console.error(err);
        this.$message.error('加载工作流时发生错误，请重试');
      }
    },
    // 将节点配置编译序列化
    buildGraphJsonFromSteps() {
      // 实际上这跟 buildGraphJsonForSubmit 逻辑几乎一致，做一次桥接封装
      return this.buildGraphJsonForSubmit();
    },
    submitForm() {
      this.$refs.form.validate(async (valid) => {
        if (valid) {
          if (this.nodeSteps.length === 0) {
            this.$message.warning("请至少编排一个执行节点步骤");
            return;
          }
          for (let i = 0; i < this.nodeSteps.length; i++) {
            if (!this.nodeSteps[i] || !this.nodeSteps[i].id
              || (this.nodeSteps[i].type !== 'classifier' && !this.nodeSteps[i].ref)) {
              this.$message.warning(`步骤 ${i + 1} 未绑定任何节点，请选择`);
              return;
            }
          }
          if (this.mermaidError) {
            this.$message.error(`拓扑图配置存在异常: ${this.mermaidError}，请检查连线流向再行保存`);
            return;
          }
          
          this.form.graphJson = this.buildGraphJsonFromSteps();
          this.form.nodes = JSON.stringify(JSON.parse(this.form.graphJson).nodes);
          try {
            let res;
            if (this.form.id != null) {
              res = await updateWorkflow(this.form);
            } else {
              res = await addWorkflow(this.form);
            }
            if (res.code === 200) {
              this.$message.success("保存成功");
              this.viewMode = this.lastListViewMode || 'card';
              this.getList();
            } else {
              this.$message.error(res.msg || "保存失败");
            }
          } catch (err) {
            console.error(err);
          }
        }
      });
    },
    handleDelete(row) {
      this.$confirm('是否确认删除名称为 "' + row.workflowName + '" 的工作流编排配置？', "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(async () => {
        const res = await delWorkflow(row.id);
        if (res.code === 200) {
          this.$message.success("删除成功");
          this.getList();
        } else {
          this.$message.error(res.msg || "删除失败");
        }
      }).catch(() => {});
    }
  }
};
</script>

<style lang="scss" scoped>
@use "@/assets/styles/polaris-ai.scss";
.ai-workflow-manager {
  background-color: transparent !important;
  min-height: 100vh;
}

.filter-container {
  background: rgba(255, 255, 255, 0.02) !important;
  backdrop-filter: blur(20px) !important;
  -webkit-backdrop-filter: blur(20px) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
  border-radius: 12px;
  padding: 16px 20px 4px;
  margin-bottom: 24px;
  box-shadow: none !important;
}
.btn-gradient-success {
  background: linear-gradient(135deg, var(--el-color-success) 0%, var(--el-color-success-dark-2) 100%);
  border: none;
  box-shadow: 0 4px 10px var(--el-color-success-light-5);
  color: #fff;
}
.btn-gradient-success:hover {
  background: linear-gradient(135deg, var(--el-color-success-dark-2) 0%, var(--el-color-success-dark-2) 100%);
}

/* 清除原有的废弃卡片样式，避免干扰 Polaris 磨砂玻璃卡片 */

/* ================================================================
   三栏编排工作台样式（跟随项目主题变量）
   ================================================================ */
.workflow-workbench-container {
  animation: fadeIn 0.4s ease;
  background-color: transparent;
  min-height: 100vh;
  padding-bottom: 24px;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* SaaS Header */
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
    border-radius: 12px !important;
    font-size: 12px;
    font-weight: 700;
    background: linear-gradient(135deg, #6366f1, #818cf8) !important;
    border: none !important;
    color: #ffffff !important;
    box-shadow: 0 4px 14px rgba(99, 102, 241, 0.25);

    &:hover {
      background: linear-gradient(135deg, #4f46e5, #6366f1) !important;
      box-shadow: 0 6px 18px rgba(99, 102, 241, 0.35);
    }

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

  .cancel-action-btn {
    border-radius: 12px !important;
    font-size: 12px;
    font-weight: 700;
    border-color: rgba(0, 0, 0, 0.08) !important;
    background-color: transparent !important;
    color: #475569 !important;

    &:hover {
      background-color: rgba(0, 0, 0, 0.02) !important;
    }

    .dark &,
    .theme-dark & {
      border-color: rgba(255, 255, 255, 0.08) !important;
      color: #cbd5e1 !important;
      
      &:hover {
        background-color: rgba(255, 255, 255, 0.04) !important;
      }
    }
  }
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.back-btn {
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 5px rgba(0,0,0,0.02);
  transition: all 0.2s;

  &:hover {
    transform: scale(1.05);
    border-color: #818cf8;
    color: #818cf8;
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
.workbench-title-text {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;

  .dark &,
  .theme-dark & {
    color: #cbd5e1;
  }
}

/* 核心两栏布局 */
.workbench-body {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  width: 100%;
}

/* 左面板：320px */
.editor-left-pane {
  width: 320px;
  flex-shrink: 0;
}
.pane-card {
  border-radius: 20px;
  border: 1px solid rgba(0, 0, 0, 0.06) !important;
  background: rgba(255, 255, 255, 0.55) !important;
  box-shadow: 0 4px 15px rgba(0,0,0,0.01) !important;
  backdrop-filter: blur(20px);

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.3) !important;
    border-color: rgba(255, 255, 255, 0.05) !important;
    box-shadow: none !important;
  }
}
.pane-card :deep(.el-card__header) {
  background: transparent !important;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05) !important;
  padding: 14px 20px;
  
  .dark &,
  .theme-dark & {
    border-bottom-color: rgba(255, 255, 255, 0.05) !important;
  }
}
.pane-card :deep(.el-card__body) {
  background: transparent !important;
  padding: 20px;
}
.pane-card-header {
  font-size: 13.5px;
  font-weight: 700;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 6px;

  .el-icon {
    color: #6366f1;
    font-size: 15px;
  }

  .dark &,
  .theme-dark & {
    color: #cbd5e1;
    
    .el-icon {
      color: #38bdf8;
    }
  }
}

/* 右侧全屏画布面板 */
.editor-canvas-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.canvas-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 18px 18px 0 0;
  padding: 12px 20px;
  
  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.35);
    border-color: rgba(255, 255, 255, 0.05);
  }
}
.canvas-title-text {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 6px;
  
  .el-icon {
    color: #6366f1;
  }
  
  .dark &,
  .theme-dark & {
    color: #cbd5e1;
    
    .el-icon {
      color: #38bdf8;
    }
  }
}
.toolbar-actions {
  display: flex;
  gap: 8px;
  align-items: center;

  /* 基础按钮样式 (解决鼠标悬浮时白底白字不可见问题) */
  .el-button:not(.action-btn-primary) {
    background: #ffffff !important;
    border: 1px solid rgba(0, 0, 0, 0.08) !important;
    color: #475569 !important;
    border-radius: 10px !important;
    transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
    font-weight: 600;

    &:hover {
      background: rgba(99, 102, 241, 0.05) !important;
      border-color: rgba(99, 102, 241, 0.3) !important;
      color: #6366f1 !important;
    }
    
    &:active {
      background: rgba(99, 102, 241, 0.1) !important;
    }

    .dark &,
    .theme-dark & {
      background: rgba(0, 0, 0, 0.25) !important;
      border-color: rgba(255, 255, 255, 0.08) !important;
      color: #cbd5e1 !important;

      &:hover {
        background: rgba(56, 189, 248, 0.08) !important;
        border-color: rgba(56, 189, 248, 0.3) !important;
        color: #38bdf8 !important;
      }
      
      &:active {
        background: rgba(56, 189, 248, 0.15) !important;
      }
    }
  }

  /* 主动高亮“添加节点”按钮样式 (使其在工具栏中特别突出明显，完美适配光暗) */
  .action-btn-primary {
    border-radius: 10px !important;
    font-size: 12px;
    font-weight: 700;
    background: linear-gradient(135deg, #4f46e5, #6366f1) !important;
    border: none !important;
    color: #ffffff !important;
    box-shadow: 0 4px 14px rgba(99, 102, 241, 0.2) !important;
    transition: all 0.3s ease;

    &:hover {
      background: linear-gradient(135deg, #4338ca, #4f46e5) !important;
      box-shadow: 0 6px 18px rgba(99, 102, 241, 0.3) !important;
      transform: translateY(-1px);
    }

    .dark &,
    .theme-dark & {
      background: linear-gradient(135deg, #0284c7, #38bdf8) !important;
      color: #0f172a !important;
      box-shadow: 0 4px 14px rgba(56, 189, 248, 0.2) !important;

      &:hover {
        background: linear-gradient(135deg, #0369a1, #0284c7) !important;
        box-shadow: 0 6px 18px rgba(56, 189, 248, 0.3) !important;
        color: #0f172a !important;
        transform: translateY(-1px);
      }
    }
  }
}
.vf-canvas-wrapper {
  position: relative;
  height: calc(100vh - 200px);
  min-height: 560px;
  border-radius: 0 0 18px 18px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-top: none;
  
  .dark &,
  .theme-dark & {
    border-color: rgba(255, 255, 255, 0.05);
  }
}
.vue-flow-editor {
  width: 100%;
  height: 100%;
  background: var(--el-bg-color);
}

/* Vue Flow 节点样式 */
.vue-flow-editor :deep(.vue-flow__node) {
  font-size: 12px;
  font-weight: 600;
  border-radius: 12px !important;
  border: 1px solid rgba(0, 0, 0, 0.08) !important;
  background: #ffffff !important;
  color: #475569 !important;
  padding: 8px 16px !important;
  min-width: 100px;
  text-align: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02) !important;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  cursor: pointer;

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.8) !important;
    border-color: rgba(255, 255, 255, 0.06) !important;
    color: #cbd5e1 !important;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2) !important;
  }
}
.vue-flow-editor :deep(.vue-flow__node:hover),
.vue-flow-editor :deep(.vue-flow__node.selected) {
  border-color: #6366f1 !important;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15), 0 8px 24px rgba(99, 102, 241, 0.15) !important;
  background: #ffffff !important;
  
  .dark &,
  .theme-dark & {
    border-color: #38bdf8 !important;
    box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.25), 0 8px 24px rgba(56, 189, 248, 0.2) !important;
    background: rgba(15, 23, 42, 0.9) !important;
  }
}
.vue-flow-editor :deep(.vue-flow__node.vf-node-terminal) {
  background: linear-gradient(135deg, #4f46e5 0%, #10b981 100%) !important;
  color: #ffffff !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
  border-radius: 50px !important;
  letter-spacing: 0.5px;
  font-size: 11px;
  font-weight: 700;
  padding: 6px 16px !important;
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.2) !important;
  cursor: default;
  transition: all 0.3s ease;

  .dark &,
  .theme-dark & {
    background: linear-gradient(135deg, #6366f1 0%, #34d399 100%) !important;
    border-color: rgba(255, 255, 255, 0.1) !important;
    box-shadow: 0 4px 20px rgba(99, 102, 241, 0.3) !important;
  }

  &.selected,
  &:hover {
    border-color: #ffffff !important;
    box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.35), 0 8px 24px rgba(79, 70, 229, 0.3) !important;
    
    .dark &,
    .theme-dark & {
      border-color: #34d399 !important;
      box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.45), 0 8px 24px rgba(99, 102, 241, 0.4) !important;
    }
  }
}
.vue-flow-editor :deep(.vue-flow__node.vf-node-classifier) {
  background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%) !important;
  color: #ffffff !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
  border-radius: 12px !important;
  font-size: 11.5px;
  font-weight: 700;
  padding: 8px 16px !important;
  box-shadow: 0 4px 14px rgba(168, 85, 247, 0.25) !important;
  transition: all 0.3s ease;
  
  .dark &,
  .theme-dark & {
    background: linear-gradient(135deg, #818cf8 0%, #c084fc 100%) !important;
    border-color: rgba(255, 255, 255, 0.1) !important;
    box-shadow: 0 4px 20px rgba(168, 85, 247, 0.35) !important;
  }

  &.selected,
  &:hover {
    border-color: #ffffff !important;
    box-shadow: 0 0 0 3px rgba(168, 85, 247, 0.3), 0 8px 24px rgba(99, 102, 241, 0.3) !important;
    
    .dark &,
    .theme-dark & {
      border-color: #c084fc !important;
      box-shadow: 0 0 0 3px rgba(192, 132, 252, 0.4), 0 8px 24px rgba(168, 85, 247, 0.4) !important;
    }
  }
}
.vue-flow-editor :deep(.vue-flow__node.vf-node-java) {
  background: rgba(16, 185, 129, 0.08) !important;
  border-color: rgba(16, 185, 129, 0.55) !important;
  color: #047857 !important;

  .dark &,
  .theme-dark & {
    background: rgba(16, 185, 129, 0.14) !important;
    border-color: rgba(52, 211, 153, 0.65) !important;
    color: #6ee7b7 !important;
  }
}
.vue-flow-editor :deep(.vue-flow__node.vf-node-active) {
  animation: nodePulse 1.2s ease-in-out infinite;
}
@keyframes nodePulse {
  0%, 100% { box-shadow: 0 0 0 3px #10b981, 0 0 12px rgba(16,185,129,0.3); }
  50% { box-shadow: 0 0 0 4px #10b981, 0 0 22px rgba(16,185,129,0.6); }
}
.test-run-approval {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #f59e0b;
  border-radius: 6px;
  background: rgba(245, 158, 11, 0.06);
}
.test-run-approval-title {
  margin-bottom: 10px;
  color: #b45309;
  font-size: 13px;
  font-weight: 600;
}
.test-run-approval-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
.test-log-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  font-size: 13px;
  border-radius: 6px;
  margin-bottom: 4px;
}
.test-log-item.log-route { color: #a855f7; background: rgba(168,85,247,0.08); }
.test-log-item.log-done { color: #22c55e; }
.test-log-item.log-error { color: #ef4444; background: rgba(239,68,68,0.08); }
.test-log-item.log-start { color: #3b82f6; }
.vue-flow-editor :deep(.vue-flow__handle) {
  width: 10px !important;
  height: 10px !important;
  background: #6366f1 !important;
  border: 2px solid #ffffff !important;
  border-radius: 50% !important;
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.3) !important;
  transition: all 0.2s;
  opacity: 1 !important;
  pointer-events: all !important;

  .dark &,
  .theme-dark & {
    background: #38bdf8 !important;
    border-color: #0f172a !important;
    box-shadow: 0 2px 6px rgba(56, 189, 248, 0.4) !important;
  }
}
.vue-flow-editor :deep(.vue-flow__handle:hover) {
  background: #10b981 !important;
  border-color: #ffffff !important;
  transform: scale(1.4) !important;
  box-shadow: 0 0 10px rgba(16, 185, 129, 0.5) !important;
  cursor: crosshair;

  .dark &,
  .theme-dark & {
    background: #34d399 !important;
    border-color: #0f172a !important;
  }
}
.vue-flow-editor :deep(.vue-flow__edge-text) {
  font-size: 11px;
  font-weight: 700;
}
.vue-flow-editor :deep(.vue-flow__controls) {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.2);
}
.vue-flow-editor :deep(.vue-flow__controls button) {
  background: transparent;
  color: var(--el-text-color-secondary);
  border-color: var(--el-border-color);
}
.vue-flow-editor :deep(.vue-flow__controls button:hover) {
  background: var(--menu-hover);
  color: var(--el-color-primary);
}
.vue-flow-editor :deep(.vue-flow__minimap) {
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  overflow: hidden;
}

.vue-flow-editor :deep(.vue-flow__node.vf-node-unconfigured) {
  border-color: #fbbf24 !important;
  color: #d97706 !important;
  background: rgba(251, 191, 36, 0.03) !important;
  border-style: dashed !important;
  
  .dark &,
  .theme-dark & {
    border-color: #fbbf24 !important;
    color: #fbbf24 !important;
    background: rgba(251, 191, 36, 0.05) !important;
  }
}
.vue-flow-editor :deep(.vue-flow__node.vf-node-unconfigured:hover),
.vue-flow-editor :deep(.vue-flow__node.vf-node-unconfigured.selected) {
  border-color: #fbbf24 !important;
  box-shadow: 0 0 0 3px rgba(251, 191, 36, 0.25), 0 8px 24px rgba(251, 191, 36, 0.15) !important;
}
.node-popover {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 320px;
  background: rgba(255, 255, 255, 0.7) !important;
  backdrop-filter: blur(25px);
  border: 1px solid rgba(255, 255, 255, 0.5) !important;
  border-radius: 20px;
  box-shadow: 0 20px 40px -15px rgba(0, 0, 0, 0.08);
  z-index: 1000;
  overflow: hidden;
  transition: all 0.3s ease;

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.6) !important;
    border-color: rgba(255, 255, 255, 0.05) !important;
    box-shadow: 0 20px 50px -10px rgba(0, 0, 0, 0.3) !important;
  }
}
.edge-editor-popover {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 320px;
  background: rgba(255, 255, 255, 0.7) !important;
  backdrop-filter: blur(25px);
  border: 1px solid rgba(255, 255, 255, 0.5) !important;
  border-radius: 20px;
  box-shadow: 0 20px 40px -15px rgba(0, 0, 0, 0.08);
  z-index: 1001;
  overflow: hidden;
  transition: all 0.3s ease;

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.6) !important;
    border-color: rgba(255, 255, 255, 0.05) !important;
    box-shadow: 0 20px 50px -10px rgba(0, 0, 0, 0.3) !important;
  }
}
.edge-editor-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.edge-type-switcher {
  display: flex;
  gap: 8px;
}
.edge-type-option {
  flex: 1;
  border: 1.5px solid var(--el-border-color);
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.edge-type-option:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.edge-type-option.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.edge-type-option .el-icon {
  font-size: 14px;
  margin-bottom: 2px;
}
.edge-type-option span {
  font-size: 12px;
  font-weight: 600;
}
.edge-type-desc {
  font-size: 10.5px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
.edge-type-option.active .edge-type-desc {
  color: var(--el-color-primary-light-3);
}
.edge-keyword-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.edge-keyword-label {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}
.edge-keyword-hint {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  background: var(--el-bg-color);
  border-radius: 6px;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color-light);
}
.edge-default-hint {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  background: var(--el-bg-color);
  border-radius: 6px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-light);
  display: flex;
  gap: 6px;
  align-items: flex-start;
}
.edge-default-hint .el-icon {
  flex-shrink: 0;
  margin-top: 1px;
  color: var(--el-color-primary);
}
.edge-editor-actions {
  display: flex;
  gap: 6px;
}
.popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  background: transparent !important;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  
  .dark &,
  .theme-dark & {
    border-bottom-color: rgba(255, 255, 255, 0.05);
  }
}
.popover-title {
  font-size: 13px;
  font-weight: 800;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 6px;

  .el-icon {
    color: #6366f1;
  }

  .dark &,
  .theme-dark & {
    color: #cbd5e1;

    .el-icon {
      color: #38bdf8;
    }
  }
}
.popover-close-btn {
  background: rgba(0, 0, 0, 0.03) !important;
  border: none !important;
  color: #64748b !important;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  
  &:hover {
    background: rgba(239, 68, 68, 0.08) !important;
    color: #ef4444 !important;
  }

  .dark &,
  .theme-dark & {
    background: rgba(255, 255, 255, 0.03) !important;
    color: #cbd5e1 !important;

    &:hover {
      background: rgba(239, 68, 68, 0.15) !important;
      color: #fca5a5 !important;
    }
  }
}
.popover-body {
  padding: 18px 20px;
  max-height: 480px;
  overflow-y: auto;

  /* Form spacing */
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  :deep(.el-form-item__label) {
    color: #475569 !important;
    font-size: 12px;
    font-weight: 700;
    padding-bottom: 6px !important;

    .dark &,
    .theme-dark & {
      color: #cbd5e1 !important;
    }
  }

  /* Form elements overrides inside popovers */
  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    border-radius: 10px !important;
    background-color: #ffffff !important;
    border: 1px solid rgba(0, 0, 0, 0.08) !important;
    box-shadow: none !important;
    transition: all 0.3s ease;
    color: #0f172a !important;

    &:hover {
      border-color: rgba(99, 102, 241, 0.3) !important;
    }

    &.is-focus {
      border-color: #6366f1 !important;
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12) !important;
    }

    .dark &,
    .theme-dark & {
      background-color: rgba(0, 0, 0, 0.2) !important;
      border-color: rgba(255, 255, 255, 0.05) !important;
      color: #cbd5e1 !important;

      &:hover {
        border-color: rgba(56, 189, 248, 0.3) !important;
      }

      &.is-focus {
        border-color: #38bdf8 !important;
        box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.15) !important;
      }
    }
  }

  :deep(.el-input-number) {
    border-radius: 10px !important;
    overflow: hidden;
    border: 1px solid rgba(0, 0, 0, 0.08) !important;
    transition: all 0.3s ease;
    width: 100%;
    
    &:hover {
      border-color: rgba(99, 102, 241, 0.3) !important;
    }
    
    .el-input-number__decrease,
    .el-input-number__increase {
      background: rgba(0, 0, 0, 0.02) !important;
      border-color: rgba(0, 0, 0, 0.05) !important;
      color: #475569 !important;
      
      &:hover {
        color: #6366f1 !important;
      }
    }
    
    .dark &,
    .theme-dark & {
      border-color: rgba(255, 255, 255, 0.06) !important;
      
      &:hover {
        border-color: rgba(56, 189, 248, 0.3) !important;
      }
      
      .el-input-number__decrease,
      .el-input-number__increase {
        background: rgba(255, 255, 255, 0.02) !important;
        border-color: rgba(255, 255, 255, 0.04) !important;
        color: #cbd5e1 !important;
        
        &:hover {
          color: #38bdf8 !important;
        }
      }
    }
  }

  /* el-button type="danger" styling inside popover */
  :deep(.el-button--danger) {
    border-radius: 10px;
    font-weight: 700;
    background: rgba(239, 68, 68, 0.06) !important;
    border: 1px solid rgba(239, 68, 68, 0.12) !important;
    color: #ef4444 !important;
    transition: all 0.3s;

    &:hover {
      background: #ef4444 !important;
      color: #ffffff !important;
      border-color: transparent !important;
      box-shadow: 0 4px 10px rgba(239, 68, 68, 0.15) !important;
    }

    .dark &,
    .theme-dark & {
      background: rgba(248, 113, 113, 0.08) !important;
      border-color: rgba(248, 113, 113, 0.15) !important;
      color: #fca5a5 !important;

      &:hover {
        background: #ef4444 !important;
        color: #ffffff !important;
        border-color: transparent !important;
        box-shadow: 0 4px 12px rgba(239, 68, 68, 0.25) !important;
      }
    }
  }
}
.popover-divider {
  height: 1px;
  background: rgba(0, 0, 0, 0.05);
  margin: 14px 0;

  .dark &,
  .theme-dark & {
    background: rgba(255, 255, 255, 0.05);
  }
}
.popover-agent-props {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: rgba(255, 255, 255, 0.4) !important;
  border: 1px solid rgba(0, 0, 0, 0.05) !important;
  border-radius: 12px;
  padding: 12px;

  .dark &,
  .theme-dark & {
    background: rgba(0, 0, 0, 0.15) !important;
    border-color: rgba(255, 255, 255, 0.04) !important;
  }
}
.prop-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.prop-label-title {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 4px;

  .dark &,
  .theme-dark & {
    color: #94a3b8;
  }
}
.popover-prompt-preview {
  background: rgba(0, 0, 0, 0.02);
  border-radius: 8px;
  padding: 8px 10px;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  color: #475569;
  line-height: 1.5;
  max-height: 100px;
  overflow-y: auto;
  white-space: pre-wrap;
  border: 1px solid rgba(0, 0, 0, 0.05);

  .dark &,
  .theme-dark & {
    background: rgba(0, 0, 0, 0.2);
    border-color: rgba(255, 255, 255, 0.04);
    color: #94a3b8;
  }
}
.popover-fade-enter-active,
.popover-fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.popover-fade-enter-from,
.popover-fade-leave-to {
  opacity: 0;
  transform: scale(0.96) translateY(-4px);
}

/* 按钮发光 */
.btn-primary-glow {
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-dark-2) 100%);
  border: none;
  box-shadow: 0 4px 10px var(--el-color-primary-light-5);
}
.btn-primary-glow:hover {
  background: linear-gradient(135deg, var(--el-color-primary-dark-2) 0%, var(--el-color-primary-dark-2) 100%);
}

/* 列表位移动画 */
.list-move {
  transition: transform 0.4s cubic-bezier(0.2, 0.8, 0.2, 1);
}
.list-enter-active, .list-leave-active {
  transition: all 0.3s ease;
}
.list-enter, .list-leave-to {
  opacity: 0;
  transform: translateY(15px);
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  background: var(--el-fill-color-blank) !important;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 16px;
  border: 1px dashed var(--el-border-color) !important;
  box-shadow: var(--el-box-shadow-light);
  width: 100%;
  margin-top: 10px;
  position: relative;
  overflow: hidden;
}
.empty-state::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, var(--el-color-primary-light-8) 0%, transparent 70%);
  pointer-events: none;
}
.empty-icon {
  font-size: 56px;
  margin-bottom: 20px;
  display: inline-block;
  animation: float-icon 3s ease-in-out infinite;
}
.empty-state p {
  color: var(--el-text-color-primary) !important;
  font-size: 14.5px;
  font-weight: 500;
  margin: 0;
  line-height: 1.6;
}
@keyframes float-icon {
  0%, 100% {
    transform: translateY(0) scale(1);
    filter: drop-shadow(0 4px 6px rgba(0, 0, 0, 0.1));
  }
  50% {
    transform: translateY(-8px) scale(1.05);
    filter: drop-shadow(0 12px 16px rgba(0, 0, 0, 0.2));
  }
}

/* 🌐 Mermaid 只读路线预览面板样式 */
.mermaid-preview-panel {
  margin: 12px 16px;
  background: rgba(30, 41, 59, 0.4) !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 12px;
}
.preview-title {
  font-size: 13px;
  font-weight: 600;
  color: #a5b4fc;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.preview-error-hint {
  font-size: 12.5px;
  color: #f87171;
  background: rgba(220, 38, 38, 0.08);
  border: 1px solid rgba(220, 38, 38, 0.2);
  border-radius: 8px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.topo-graph-render {
  min-height: 120px;
}
.vf-preview-panel {
  margin: 12px 16px;
  background: rgba(30, 41, 59, 0.4) !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 12px;
}
.vf-preview-canvas {
  width: 100%;
  height: 400px;
  border-radius: 8px;
  overflow: hidden;
}
.vue-flow-preview {
  width: 100%;
  height: 100%;
  background: rgba(248, 250, 252, 0.5) !important;
  
  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.6) !important;
  }
}
.vue-flow-preview :deep(.vue-flow__node) {
  font-size: 11.5px;
  font-weight: 700;
  border-radius: 10px;
  border: 1px solid rgba(99, 102, 241, 0.25) !important;
  background: rgba(99, 102, 241, 0.05) !important;
  color: #4f46e5 !important;
  padding: 8px 14px;
  min-width: 90px;
  text-align: center;
  box-shadow: 0 4px 10px rgba(99, 102, 241, 0.03) !important;
  transition: all 0.2s ease;
  cursor: pointer;

  .dark &,
  .theme-dark & {
    border: 1.5px solid rgba(129, 140, 248, 0.5) !important;
    background: rgba(99, 102, 241, 0.12) !important;
    color: #a5b4fc !important;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3) !important;
  }
}
.vue-flow-preview :deep(.vue-flow__node:hover) {
  background: rgba(99, 102, 241, 0.1) !important;
  border-color: rgba(99, 102, 241, 0.5) !important;
  box-shadow: 0 0 14px rgba(99, 102, 241, 0.2) !important;
  transform: translateY(-1px);
  
  .dark &,
  .theme-dark & {
    background: rgba(99, 102, 241, 0.22) !important;
    border-color: rgba(129, 140, 248, 0.9) !important;
    box-shadow: 0 0 14px rgba(99, 102, 241, 0.4) !important;
  }
}
.vue-flow-preview :deep(.vue-flow__node.vf-node-terminal) {
  background: linear-gradient(135deg, #4f46e5 0%, #10b981 100%) !important;
  color: #ffffff !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
  border-radius: 50px !important;
  letter-spacing: 0.5px;
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.2) !important;
  cursor: default;

  .dark &,
  .theme-dark & {
    background: linear-gradient(135deg, #6366f1 0%, #34d399 100%) !important;
    border-color: rgba(255, 255, 255, 0.1) !important;
    box-shadow: 0 4px 20px rgba(99, 102, 241, 0.3) !important;
  }
}
.vue-flow-preview :deep(.vue-flow__node.vf-node-terminal:hover) {
  transform: none;
}
.vue-flow-preview :deep(.vue-flow__handle) {
  opacity: 0 !important;
  pointer-events: none !important;
}




.synapse-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 20px;
  width: 100%;
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
  --node-agent-grad-start: #ffffff;
  --node-agent-grad-end: #f8fafc;
  --node-agent-stroke: rgba(0, 0, 0, 0.08);
  --node-agent-text: #1e293b;


  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.3);
    border: 1px solid rgba(255, 255, 255, 0.05);
    box-shadow: 0 15px 40px -10px rgba(0, 0, 0, 0.3);
    --node-agent-grad-start: rgba(30, 41, 59, 0.8);
    --node-agent-grad-end: rgba(15, 23, 42, 0.6);
    --node-agent-stroke: rgba(255, 255, 255, 0.08);
    --node-agent-text: #e2e8f0;
  }
  
  &:hover {
    transform: translateY(-6px) scale(1.02) !important;
    
    .card-shimmer-ray {
      transform: skewX(-20deg) translateX(400px);
    }

    &.status-border-active {
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.06), 0 0 15px rgba(16, 185, 129, 0.2) !important;

      .dark &,
      .theme-dark & {
        box-shadow: 0 20px 48px rgba(0, 0, 0, 0.55), 0 0 25px rgba(16, 185, 129, 0.22) !important;
      }
    }
    &.status-border-error {
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.06), 0 0 15px rgba(239, 68, 68, 0.25) !important;

      .dark &,
      .theme-dark & {
        box-shadow: 0 20px 48px rgba(0, 0, 0, 0.55), 0 0 25px rgba(239, 68, 68, 0.25) !important;
      }
    }
  }

  &.status-border-active {
    border-color: rgba(16, 185, 129, 0.3);

    .dark &,
    .theme-dark & {
      border-color: rgba(16, 185, 129, 0.25);
      box-shadow: 0 0 20px -5px rgba(16, 185, 129, 0.08);
    }
  }
  &.status-border-error {
    border-color: rgba(239, 68, 68, 0.35);

    .dark &,
    .theme-dark & {
      border-color: rgba(239, 68, 68, 0.25);
      box-shadow: 0 0 20px -5px rgba(239, 68, 68, 0.08);
    }
  }
}


.card-code {
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
  padding: 2px 8px;
  border-radius: 6px;
  background-color: rgba(0, 0, 0, 0.03);
  color: #4f46e5;

  .dark &,
  .theme-dark & {
    background-color: rgba(255, 255, 255, 0.04);
    color: #38bdf8;
  }
}


.card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-name {
  font-size: 14.5px;
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

/* 雷达呼吸灯相关组件 */
.pulse-light-ripple {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  position: relative;
  
  &::after {
    content: '';
    position: absolute;
    inset: -4px;
    border-radius: 50%;
    border: 1.5px solid currentColor;
    opacity: 0;
    animation: radarBreath-scoped 2s infinite ease-out;
  }
}

@keyframes radarBreath-scoped {
  0% { transform: scale(0.8); opacity: 0.6; }
  100% { transform: scale(2.2); opacity: 0; }
}

.pulse-active {
  background-color: #10b981;
  color: #10b981;
  filter: drop-shadow(0 0 3px #10b981);
}

.pulse-error {
  background-color: #ef4444;
  color: #ef4444;
  filter: drop-shadow(0 0 3px #ef4444);
}

.text-active {
  color: #10b981;
}

.text-error {
  color: #ef4444;
}

.clickable-status {
  cursor: pointer;
  transition: transform 0.2s;
  &:hover {
    transform: scale(1.04);
  }
}


.card-op-edit-pill {
  font-size: 12px;
  font-weight: 700;
  color: #4f46e5 !important;
  display: flex;
  align-items: center;
  gap: 4px;
  
  &:hover {
    opacity: 0.8;
  }

  .dark &,
  .theme-dark & {
    color: #38bdf8 !important;
  }
}

.card-op-delete-pill {
  font-size: 12px;
  font-weight: 700;
  color: #ef4444 !important;
  display: flex;
  align-items: center;
  gap: 4px;

  &:hover {
    opacity: 0.8;
  }

  .dark &,
  .theme-dark & {
    color: #fca5a5 !important;
  }
}

/* 表格视图布局及美化 */
.polaris-table-card {
  border-radius: 20px;
  padding: 16px 20px;
  backdrop-filter: blur(25px);
  -webkit-backdrop-filter: blur(25px);
  background: rgba(255, 255, 255, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.45);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.02);

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.15);
    border: 1px solid rgba(255, 255, 255, 0.04);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
  }
}

.table-op-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.op-divider {
  width: 1px;
  height: 12px;
  background-color: rgba(0, 0, 0, 0.08);

  .dark &,
  .theme-dark & {
    background-color: rgba(255, 255, 255, 0.12);
  }
}

.op-btn-edit {
  font-size: 12px;
  font-weight: bold;
  padding: 0;
  color: #4f46e5 !important;

  .dark &,
  .theme-dark & {
    color: #38bdf8 !important;
  }
}

.op-btn-delete {
  font-size: 12px;
  font-weight: bold;
  padding: 0;
  color: #ef4444 !important;

  .dark &,
  .theme-dark & {
    color: #fca5a5 !important;
  }
}

/* ===== 🧠 迷你拓扑图节点上色美化 ===== */
.mini-node {
  stroke-width: 1.5;
  filter: drop-shadow(0 2px 4px rgba(0,0,0,0.04));
}

.mini-node-term {
  fill: url(#node-grad-term);
  stroke: rgba(99, 102, 241, 0.3);
  filter: drop-shadow(0 2px 6px rgba(79, 70, 229, 0.25));
}

.mini-node-agent {
  fill: url(#node-grad-agent-light);
  stroke: rgba(0, 0, 0, 0.08);

  .dark &,
  .theme-dark & {
    fill: url(#node-grad-agent-dark);
    stroke: rgba(255, 255, 255, 0.08);
  }
}

.mini-node-classifier {
  fill: url(#node-grad-classifier);
  stroke: rgba(168, 85, 247, 0.3);
  filter: drop-shadow(0 2px 6px rgba(168, 85, 247, 0.2));
}

.mini-node-java {
  fill: url(#node-grad-java);
  stroke: rgba(16, 185, 129, 0.3);
  filter: drop-shadow(0 2px 6px rgba(16, 185, 129, 0.2));
}

.mini-node-text {
  font-size: 10px;
  fill: #1e293b;
  font-weight: 700;
  pointer-events: none;
}

.mini-node-text-agent {
  fill: #1e293b;

  .dark &,
  .theme-dark & {
    fill: #cbd5e1;
  }
}

.mini-node-text-light {
  fill: #ffffff !important;
}

/* 动效导线 */
.mini-graph-edge-path {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  transition: stroke 0.3s, stroke-width 0.3s;
}

.edge-normal {
  stroke: url(#edge-gradient-normal);
  stroke-width: 2;
  stroke-dasharray: 4 4;
  animation: flowLine 25s linear infinite;
}

.edge-cond {
  stroke: url(#edge-gradient-cond);
  stroke-width: 2.5;
  stroke-dasharray: 5 4;
  animation: flowLine 15s linear infinite;
}

@keyframes flowLine {
  from {
    stroke-dashoffset: 200;
  }
  to {
    stroke-dashoffset: 0;
  }
}

.mini-graph-canvas {
  background: rgba(248, 250, 252, 0.35);
  background-image: radial-gradient(rgba(99, 102, 241, 0.08) 1px, transparent 1px);
  background-size: 10px 10px;
  border: 1px solid rgba(0, 0, 0, 0.04);
  border-radius: 16px;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  height: 160px;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.02);
  position: relative;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(99, 102, 241, 0.2);
    border-radius: 4px;
  }

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.25);
    background-image: radial-gradient(rgba(56, 189, 248, 0.1) 1px, transparent 1px);
    background-size: 10px 10px;
    border-color: rgba(255, 255, 255, 0.04);
    box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.2);
    
    &::-webkit-scrollbar-thumb {
      background: rgba(56, 189, 248, 0.25);
    }
  }
}

/* ===== 流程备注说明与左边框修饰 ===== */
.desc-box {
  background: rgba(0, 0, 0, 0.02);
  border-left: 3px solid #818cf8;
  border-radius: 4px;
  padding: 8px 12px;
  margin-top: 6px;
  margin-bottom: 12px;
  height: 46px;
  overflow-y: auto;
  box-sizing: border-box;
  display: flex;
  align-items: center;

  &::-webkit-scrollbar {
    width: 3px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(99, 102, 241, 0.15);
    border-radius: 2px;
  }

  .dark &,
  .theme-dark & {
    background: rgba(255, 255, 255, 0.02);
    border-left-color: #38bdf8;
    
    &::-webkit-scrollbar-thumb {
      background: rgba(56, 189, 248, 0.2);
    }
  }
}

.desc-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
  word-break: break-all;
  width: 100%;

  .dark &,
  .theme-dark & {
    color: #cbd5e1;
  }
  
  &.no-desc {
    color: #94a3b8;
    font-style: italic;
    
    .dark &,
    .theme-dark & {
      color: #64748b;
    }
  }
}

/* ===== 流程核心指标徽章 ===== */
.workflow-stats-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
  margin-bottom: 4px;
}

.stat-chip-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 20px;
  border: 1px solid transparent;
  transition: all 0.3s;

  .el-icon {
    font-size: 12px;
  }

  &.chip-purple {
    color: #8b5cf6;
    background: rgba(139, 92, 246, 0.06);
    border-color: rgba(139, 92, 246, 0.12);
  }
  &.chip-blue {
    color: #3b82f6;
    background: rgba(59, 130, 246, 0.06);
    border-color: rgba(59, 130, 246, 0.12);
  }
  &.chip-green {
    color: #10b981;
    background: rgba(16, 185, 129, 0.06);
    border-color: rgba(16, 185, 129, 0.12);
  }

  .dark &,
  .theme-dark & {
    &.chip-purple {
      color: #a78bfa;
      background: rgba(167, 139, 250, 0.08);
      border-color: rgba(167, 139, 250, 0.15);
    }
    &.chip-blue {
      color: #93c5fd;
      background: rgba(147, 197, 253, 0.08);
      border-color: rgba(147, 197, 253, 0.15);
    }
    &.chip-green {
      color: #6ee7b7;
      background: rgba(110, 231, 183, 0.08);
      border-color: rgba(110, 231, 183, 0.15);
    }
  }
}

/* ===== 智能体/工作流配置工作台表单元素及单选框美化 ===== */
.workflow-workbench-container {
  :deep(.el-form-item__label) {
    font-size: 12px;
    font-weight: 700;
    color: #64748b;
    padding-bottom: 6px !important;

    .dark &,
    .theme-dark & {
      color: #94a3b8;
    }
  }

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper),
  :deep(.el-textarea__inner) {
    border-radius: 12px !important;
    background-color: #ffffff !important;
    border: 1px solid rgba(0, 0, 0, 0.08) !important;
    box-shadow: none !important;
    transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
    color: #0f172a !important;

    &:hover {
      border-color: rgba(99, 102, 241, 0.3) !important;
    }

    &.is-focus,
    &:focus {
      border-color: #6366f1 !important;
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15) !important;
    }

    .dark &,
    .theme-dark & {
      background-color: rgba(0, 0, 0, 0.25) !important;
      border-color: rgba(255, 255, 255, 0.06) !important;
      color: #cbd5e1 !important;

      &:hover {
        border-color: rgba(56, 189, 248, 0.3) !important;
      }

      &.is-focus,
      &:focus {
        border-color: #38bdf8 !important;
        box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.15) !important;
      }
    }
  }

  /* 状态单选框美化 (同智能体卡片卡式展示，完美兼容光暗模式且选中醒目) */
  :deep(.el-radio) {
    border-radius: 10px;
    padding: 8px 16px;
    border: 1px solid rgba(0, 0, 0, 0.06) !important;
    background: #ffffff;
    transition: all 0.3s;
    margin-right: 12px;
    height: auto;

    &.is-checked {
      border-color: #6366f1 !important;
      background: rgba(99, 102, 241, 0.02) !important;
      
      .el-radio__label {
        color: #6366f1 !important;
        font-weight: 700 !important;
      }
      .el-radio__inner {
        border-color: #6366f1 !important;
        background: #6366f1 !important;
      }
    }

    .dark &,
    .theme-dark & {
      background: rgba(255, 255, 255, 0.02);
      border-color: rgba(255, 255, 255, 0.05) !important;

      &.is-checked {
        border-color: #38bdf8 !important;
        background: rgba(56, 189, 248, 0.02) !important;

        .el-radio__label {
          color: #38bdf8 !important;
          font-weight: 700 !important;
        }
        .el-radio__inner {
          border-color: #38bdf8 !important;
          background: #38bdf8 !important;
        }
      }
    }

    .el-radio__label {
      color: #475569;
      font-size: 13px;
      font-weight: 500;
      
      .dark &,
      .theme-dark & {
        color: #cbd5e1;
      }
    }
  }
}
</style>
