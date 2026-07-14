<template>
  <div class="app-container ai-workflow-manager">
    <!-- 1. 工作流配置列表模式 -->
    <div v-if="viewMode === 'list'">
      <!-- 顶部高效筛选栏 -->
      <div class="filter-container">
        <el-form :model="queryParams" ref="queryForm" :inline="true" class="demo-form-inline">
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
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            <el-button class="btn-primary-glow" icon="Plus" type="primary" @click="handleAdd">新增工作流</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 卡片式工作流总览列表 -->
      <div v-loading="loading" class="card-list-container">
        <div v-if="workflowList.length === 0" class="empty-state">
          <div class="empty-icon">⚙️</div>
          <p style="color: var(--el-text-color-primary) !important; font-weight: 600;">暂无工作流编排，点击上方“新增工作流”按钮开始设计您的 AI 智能体工作流吧！</p>
        </div>

        <el-row v-else :gutter="20">
          <el-col v-for="item in workflowList" :key="item.id" :lg="12" :md="12" :sm="24" :xs="24" class="card-col">
            <div :class="['workflow-card', { 'is-disabled': item.status === '0' }]">
              <!-- 卡片头：发光管线图标与状态 -->
              <div class="card-header">
                <div class="workflow-avatar">
                  <el-icon><operation /></el-icon>
                </div>
                <div class="header-info">
                  <h3 class="workflow-title-text">{{ item.workflowName }}</h3>
                  <span class="workflow-code-tag">{{ item.workflowCode }}</span>
                </div>
                <div class="status-switch">
                  <el-switch
                    v-model="item.status"
                    active-value="1"
                    inactive-value="0"
                    @change="handleStatusChange(item)"
                  />
                </div>
              </div>

              <!-- 卡片主体：描述与节点管线可视化 -->
              <div class="card-body">
                <div class="desc-box">
                  <p class="desc-text">{{ item.description || '暂无描述' }}</p>
                </div>

                <!-- 只读迷你拓扑图（真实展示分支结构） -->
                <div class="mini-graph-visualization">
                  <span class="pipeline-label">执行流向：</span>
                  <div class="mini-graph-canvas" v-if="item._miniGraph">
                    <svg :width="item._miniGraph.width" :height="item._miniGraph.height" class="mini-graph-svg">
                      <g v-for="(e, ei) in item._miniGraph.edges" :key="'e'+ei">
                        <path
                          :d="`M ${e.x1} ${e.y1} C ${e.x1} ${(e.y1+e.y2)/2}, ${e.x2} ${(e.y1+e.y2)/2}, ${e.x2} ${e.y2}`"
                          fill="none"
                          :stroke="e.cond ? '#f59e0b' : '#a5b4fc'"
                          :stroke-width="e.cond ? 2 : 1.5"
                          :stroke-dasharray="e.cond ? '4 3' : '0'"
                        />
                      </g>
                      <g v-for="(n, ni) in item._miniGraph.nodes" :key="'n'+ni">
                        <rect
                          :x="n.x - item._miniGraph.nodeW/2" :y="n.y - item._miniGraph.nodeH/2"
                          :width="item._miniGraph.nodeW" :height="item._miniGraph.nodeH"
                          :rx="n.type === 'term' ? 12 : 5"
                          :class="['mini-node', 'mini-node-' + n.type]"
                        />
                        <text :x="n.x" :y="n.y + 3" text-anchor="middle"
                              :class="['mini-node-text', (n.type === 'term' || n.type === 'classifier') ? 'mini-node-text-light' : '']">
                          {{ n.name.length > 6 ? n.name.slice(0,6) + '…' : n.name }}
                        </text>
                      </g>
                    </svg>
                  </div>
                </div>
              </div>

              <!-- 卡片页脚：时间与操作 -->
              <div class="card-footer">
                <span class="create-time-text"><el-icon><clock /></el-icon> {{ formatDate(item.createTime) }}</span>
                <div class="action-buttons">
                  <el-button class="footer-action-btn edit" icon="Edit" link size="small" @click="handleUpdate(item)">编辑编排</el-button>
                  <el-button class="footer-action-btn delete" icon="Delete" link size="small" @click="handleDelete(item)">删除</el-button>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>

    <!-- 2. 独立整屏工作流编排工作台 -->
    <div v-else class="workflow-workbench-container">
      <!-- 顶部控制条 -->
      <div class="workbench-header">
        <div class="header-left">
          <el-button icon="Back" size="small" circle @click="cancel" class="back-btn"></el-button>
          <span class="workbench-title-text">{{ form.id ? '编辑智能体工作流管线' : '创建智能体工作流管线' }}</span>
        </div>
        <div class="header-right">
          <el-button size="small" icon="Close" @click="cancel">取消返回</el-button>
          <el-button type="primary" size="small" class="btn-primary-glow" icon="CircleCheck" @click="submitForm">保存并发布</el-button>
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
                <el-button type="primary" size="small" class="btn-primary-glow" icon="Plus">
                  添加节点<el-icon class="el-icon--right"><arrow-down /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="agent"><el-icon><cpu /></el-icon> 智能体节点</el-dropdown-item>
                    <el-dropdown-item command="classifier"><el-icon><share /></el-icon> 意图分类节点</el-dropdown-item>
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
                          <el-select v-model="b.targetRef" size="small" placeholder="目标节点" style="width:120px;" @change="onBranchTargetChange(b)">
                            <el-option v-for="t in classifierTargets" :key="t.ref" :label="t.name" :value="t.ref" />
                          </el-select>
                          <el-input v-model="b.label" size="small" placeholder="分支含义" style="flex:1;" @change="syncClassifierBranches" />
                          <el-button size="small" circle icon="Close" @click="removeBranch(bi)" />
                        </div>
                        <el-button size="small" icon="Plus" style="width:100%;" @click="addBranch">添加分支</el-button>
                      </div>
                    </el-form-item>
                    <el-form-item label="默认兜底路径（都不匹配时走）">
                      <el-select v-model="popover.node.defaultTarget" size="small" placeholder="可不设" clearable style="width:100%;" @change="syncClassifierBranches">
                        <el-option v-for="t in classifierTargets" :key="t.ref" :label="t.name" :value="t.ref" />
                      </el-select>
                    </el-form-item>
                    <div class="popover-divider"></div>
                    <el-button type="danger" size="small" icon="Delete" style="width:100%;" @click="deletePopoverNode">删除此节点</el-button>
                  </el-form>
                  <!-- 智能体/Java 节点配置 -->
                  <el-form v-else label-position="top" size="small">
                    <el-form-item label="指向执行组件/智能体">
                      <el-select v-model="popover.node.ref" placeholder="请选择智能体 / 代码组件" style="width:100%;" @change="onPopoverRefChange">
                        <el-option-group label="AI 智能体">
                          <el-option v-for="item in activeAgents" :key="item.id" :label="item.agentName" :value="item.agentCode" />
                        </el-option-group>
                        <el-option-group label="系统 Java 节点">
                          <el-option label="[系统任务] (sys_task)" value="sys_task" />
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
                  <div v-if="popover.node.type !== 'classifier' && popover.node.ref && popover.node.ref !== 'sys_task' && getAgentByCode(popover.node.ref)" class="popover-agent-props">
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
                  <div v-else-if="popover.node.ref === 'sys_task'" class="popover-agent-props">
                    <el-tag size="small" type="success" effect="plain" style="width:100%;text-align:center;">本地 Java 业务处理任务</el-tag>
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
        <el-input v-model="testRun.input" type="textarea" :rows="2" placeholder="输入一句测试问题，如：帮我查一下最近的登录日志" :disabled="testRun.running" />
        <div style="margin-top:10px;">
          <el-button type="primary" :loading="testRun.running" @click="startTestRun">开始测试</el-button>
          <el-button v-if="testRun.running" @click="stopTestRun">停止</el-button>
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
  </div>
</template>
<script>
import {addWorkflow, delWorkflow, getWorkflow, listWorkflow, updateWorkflow} from "@/api/ai/workflow";
import {listAllAgents} from "@/api/ai/agent";
import {listAvailableModel} from "@/api/ai/model";
import {getToken} from '@/utils/auth';
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
      availableModels: [],
      testRun: {
        visible: false,
        input: '',
        running: false,
        logs: [],
        activeNodeId: null,
        reader: null
      },
      graph: {
        nodes: [],
        edges: [],
        maxIterations: 10
      },
      isDraggingActive: false,
      activeStepIndex: null,
      viewMode: 'list',
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
      const currentRef = this.nodeSteps[this.activeStepIndex].ref;
      if (!currentRef) return [];
      return this.graph.edges.filter(e => e.from === currentRef && e.condition != null && e.condition.trim() !== "");
    },
    currentNodeDefaultPath: {
      get() {
        if (this.activeStepIndex === null || !this.nodeSteps[this.activeStepIndex]) return "";
        const currentRef = this.nodeSteps[this.activeStepIndex].ref;
        if (!currentRef) return "";
        const edge = this.graph.edges.find(e => e.from === currentRef && (e.condition == null || e.condition.trim() === ""));
        return edge ? edge.to : "";
      },
      set(val) {
        if (this.activeStepIndex === null || !this.nodeSteps[this.activeStepIndex]) return;
        const currentRef = this.nodeSteps[this.activeStepIndex].ref;
        if (!currentRef) return;
        let edge = this.graph.edges.find(e => e.from === currentRef && (e.condition == null || e.condition.trim() === ""));
        if (edge) {
          edge.to = val;
        } else {
          this.graph.edges.push({
            from: currentRef,
            to: val,
            condition: null
          });
        }
      }
    },
    previewNodes() {
      return this.graph.nodes.filter(n => n.ref);
    },
    // 分类节点可选的目标节点（排除自己和 Start，追加 End）
    classifierTargets() {
      const self = this.popover.node ? this.popover.node.ref : null;
      const list = this.graph.nodes
        .filter(n => n.ref && n.ref !== self)
        .map(n => ({
          ref: n.ref,
          name: n.type === 'classifier' ? (n.clfName || '意图分类') : (this.getAgentShortName(n.ref) || n.ref)
        }));
      list.push({ ref: '__end__', name: 'End（结束）' });
      return list;
    }
  },
  async created() {
    // 先加载智能体/模型，保证迷你拓扑图节点名可正确解析
    await Promise.all([this.loadActiveAgents(), this.loadAvailableModels()]);
    this.getList();
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
      if (code === 'sys_task') {
        return "系统任务";
      }
      const agent = this.activeAgents.find(a => a.agentCode === code);
      return agent ? agent.agentName : code;
    },
    getAgentByCode(node) {
      const code = (node && typeof node === 'object') ? node.ref : node;
      return this.activeAgents.find(a => a.agentCode === code);
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
          return { ref, id: ref, type: ref === 'sys_task' ? 'java' : 'agent' };
        });
        const gedges = [];
        for (let i = 0; i < gnodes.length - 1; i++) {
          gedges.push({ from: gnodes[i].ref, to: gnodes[i + 1].ref });
        }
        if (gnodes.length) {
          gedges.unshift({ from: '__start__', to: gnodes[0].ref });
          gedges.push({ from: gnodes[gnodes.length - 1].ref, to: '__end__' });
        }
        graph = { nodes: gnodes, edges: gedges };
      }

      const nodes = graph.nodes || [];
      const edges = graph.edges || [];
      const allIds = ['__start__', ...nodes.map(n => n.ref || n.id), '__end__'];
      const nodeMap = {};
      nodes.forEach(n => { nodeMap[n.ref || n.id] = n; });

      // BFS 分层（取最长路径层级，保证汇合节点排在下游）
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
          if (level[to] == null || nl > level[to]) {
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
          : (type === 'classifier' ? (n.clfName || '意图分类') : (this.getAgentShortName(id) || id));
        return { id, x: pos[id].x, y: pos[id].y, type, name };
      });

      const drawEdges = edges
        .filter(e => pos[e.from] && pos[e.to])
        .map(e => ({
          x1: pos[e.from].x, y1: pos[e.from].y + nodeH / 2,
          x2: pos[e.to].x, y2: pos[e.to].y - nodeH / 2,
          cond: !!(e.condition && e.condition.trim())
        }));

      return { width: W, height: svgH, nodeW, nodeH, nodes: drawNodes, edges: drawEdges };
    },
    previewEdgeLabel(nodeIndex) {
      const validNodes = this.previewNodes;
      if (nodeIndex < 0 || nodeIndex >= validNodes.length) return '';
      // 查找从前一节点（或 __start__）到当前节点的边上是否有 condition
      const fromRef = nodeIndex === 0 ? '__start__' : validNodes[nodeIndex - 1].ref;
      const toRef = validNodes[nodeIndex].ref;
      const edge = this.graph.edges.find(e => e.from === fromRef && e.to === toRef && e.condition);
      return edge ? edge.condition : '';
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
      this.viewMode = 'list';
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
    addNodeStep(nodeType = 'agent') {
      // el-dropdown 的 command 会传字符串；直接点击（非分类场景）时兜底为 agent
      if (typeof nodeType !== 'string') nodeType = 'agent';
      const isClassifier = nodeType === 'classifier';
      const uid = Date.now() + '_' + Math.random().toString(36).substr(2, 5);
      // 分类节点创建即分配稳定 ref（clf_xx），无需选择智能体即可连线
      const clfRef = isClassifier ? ('clf_' + Math.random().toString(36).substr(2, 6)) : '';
      const newStep = {
        _uid: uid,
        id: clfRef,
        ref: clfRef,
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

      // 直接往 vfNodes push 占位节点（agent 节点 ref 为空时 buildVfGraph 会过滤掉，所以手动加）
      const centerX = 300 + Math.random() * 200;
      const centerY = 200 + Math.random() * 100;
      this.vfNodes.push({
        id: isClassifier ? clfRef : uid,
        type: 'default',
        position: { x: centerX, y: centerY },
        data: { label: isClassifier ? '意图分类' : '未配置节点' },
        class: isClassifier ? 'vf-node-classifier' : 'vf-node-agent vf-node-unconfigured',
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
      const ref = this.graph.nodes[index] && this.graph.nodes[index].ref;
      this.graph.nodes.splice(index, 1);
      if (ref) this.graph.edges = this.graph.edges.filter(e => e.from !== ref && e.to !== ref);
      this.syncEdges();
      this.buildVfGraph();
    },
    addBranchRoute(stepIndex) {
      const currentRef = this.nodeSteps[stepIndex].ref;
      if (!currentRef) return;
      this.graph.edges.push({
        from: currentRef,
        to: "__end__",
        condition: "新路由"
      });
    },
    deleteBranchRoute(stepIndex, routeIndex) {
      const currentRef = this.nodeSteps[stepIndex].ref;
      if (!currentRef) return;
      const conditionalEdges = this.graph.edges.filter(e => e.from === currentRef && e.condition != null);
      if (conditionalEdges[routeIndex]) {
        const edge = conditionalEdges[routeIndex];
        const idx = this.graph.edges.indexOf(edge);
        if (idx !== -1) {
          this.graph.edges.splice(idx, 1);
        }
      }
    },
    syncEdges() {
      // 获取当前所有合法的节点编码集合
      const activeRefs = new Set(this.nodeSteps.map(n => n.ref).filter(Boolean));
      activeRefs.add("__end__");

      // 分类节点：其所有出边（多分支）原样保留，绝不折叠或串行覆盖
      const classifierRefs = new Set(
        this.nodeSteps.filter(n => n.type === 'classifier' && n.ref).map(n => n.ref)
      );

      // 一旦工作流进入图模式（存在分类节点），完全关闭自动串行补边，
      // 边由用户显式建立（分类面板配置 / 手动拖线），仅清理指向已删除节点的悬空边。
      if (classifierRefs.size > 0) {
        this.graph.edges = this.graph.edges.filter(
          e => (e.from === '__start__' || activeRefs.has(e.from)) && activeRefs.has(e.to)
        );
        return;
      }

      const classifierEdges = this.graph.edges.filter(
        e => classifierRefs.has(e.from) && activeRefs.has(e.to)
      );

      // 1. 保留所有合法的条件出边 (源节点开启了 branchMode，目标节点也存在；分类节点已单独处理)
      const conditionalEdges = this.graph.edges.filter(e => {
        if (e.condition == null || e.condition.trim() === "") return false;
        if (classifierRefs.has(e.from)) return false;
        const fromNode = this.nodeSteps.find(n => n.ref === e.from);
        return fromNode && fromNode.branchMode && activeRefs.has(e.to);
      });

      const normalEdges = [];

      // start → 第一个节点
      if (this.nodeSteps.length > 0 && this.nodeSteps[0].ref) {
        normalEdges.push({ from: "__start__", to: this.nodeSteps[0].ref, condition: null });
      }

      this.nodeSteps.forEach((step, index) => {
        const fromNode = step.ref;
        if (!fromNode) return;

        // 分类节点出边已原样保留，跳过串行/分支补边
        if (classifierRefs.has(fromNode)) return;

        if (step.branchMode) {
          // 分支节点：只追加用户已明确设置的兜底路径边，不自动串下一个数组项
          const existingDefault = this.graph.edges.find(
            e => e.from === fromNode && (e.condition == null || e.condition.trim() === "")
          );
          if (existingDefault && activeRefs.has(existingDefault.to) && existingDefault.to !== fromNode) {
            normalEdges.push({ from: fromNode, to: existingDefault.to, condition: null });
          }
          // 若用户还没设置兜底路径，不追加任何默认串行边（等用户在右侧面板选择）
        } else {
          // 非分支节点：串行连到下一个节点或 __end__
          const target = index < this.nodeSteps.length - 1 ? this.nodeSteps[index + 1].ref : "__end__";
          if (target) {
            normalEdges.push({ from: fromNode, to: target, condition: null });
          }
        }
      });

      this.graph.edges = [...classifierEdges, ...conditionalEdges, ...normalEdges];
    },
    handleRefChange() {
      if (this.activeStepIndex !== null && this.nodeSteps[this.activeStepIndex]) {
        const step = this.nodeSteps[this.activeStepIndex];
        step.id = step.ref;
      }
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
        .filter(n => n.ref)
        .map(n => {
          const base = {
            id: n.ref,
            ref: n.ref,
            type: n.type || 'agent',
            requireApproval: !!n.requireApproval,
            timeoutSeconds: n.timeoutSeconds || 120
          };
          if (n.type === 'classifier') {
            base.branches = (n.branches || []).filter(b => b.label && b.label.trim());
            base.modelConfigId = n.modelConfigId || null;
            base.clfName = n.clfName || '意图分类';
          }
          return base;
        });
      return JSON.stringify({
        nodes: cleanNodes,
        edges: this.graph.edges,
        maxIterations: this.graph.maxIterations || 10
      });
    },

    focusNodeByUid(uid) {
      const idx = this.nodeSteps.findIndex(n => n._uid === uid);
      if (idx !== -1) this.activeStepIndex = idx;
    },

    // ── Vue Flow 画布交互 ──────────────────────────────────────────
    buildVfGraph() {
      const nodes = this.graph.nodes.filter(n => n.ref);
      const rawEdges = this.graph.edges || [];
      const NODE_W = 110;
      const NODE_H = 32;
      const allIds = ['__start__', ...nodes.map(n => n.ref), '__end__'];
      const idSet = new Set(allIds);

      const seenKey = new Set();
      const edges = rawEdges.filter(e => {
        if (!idSet.has(e.from) || !idSet.has(e.to)) return false;
        const k = `${e.from}=>${e.to}`;
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
        const stepNode = this.graph.nodes.find(n => n.ref === id);
        const isClassifier = stepNode && stepNode.type === 'classifier';
        const label = isTerm ? (id === '__start__' ? 'Start' : 'End')
          : isClassifier ? (stepNode.clfName || '意图分类')
          : (this.getAgentShortName(id) || id);
        const existing = this.vfNodes.find(n => n.id === id);
        return {
          id,
          type: 'default',
          position: existing ? existing.position : { x: p.x - p.width / 2, y: p.y - p.height / 2 },
          data: { label },
          class: isTerm ? 'vf-node-terminal' : isClassifier ? 'vf-node-classifier' : 'vf-node-agent',
          draggable: !isTerm,
          connectable: true,
          selectable: !isTerm,
          style: isTerm ? { width: '80px' } : { width: NODE_W + 'px' }
        };
      });

      const newEdges = edges.map((e, i) => {
        let cond = (e.condition && e.condition.trim()) ? e.condition.trim() : '';
        // 分类节点出边：把 slug 显示成分支中文名（仅改显示，graph.edges 里仍存 slug）
        const srcNode = this.graph.nodes.find(n => n.ref === e.from);
        if (cond && srcNode && srcNode.type === 'classifier' && srcNode.branches) {
          const br = srcNode.branches.find(b => b.slug === cond);
          if (br) cond = br.label;
        }
        return {
          id: `vfe-${e.from}-${e.to}-${i}`,
          source: e.from,
          target: e.to,
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

      // 保留当前 vfNodes 里的占位节点（未配置 ref，id = _uid），不被覆盖
      const placeholders = this.vfNodes.filter(n => n.class && n.class.includes('vf-node-unconfigured'));
      this.vfNodes = [...newNodes, ...placeholders];
      this.vfEdges = newEdges;
    },

    // 拖拽节点后同步位置到 graph.nodes
    onNodeDragStop({ node }) {
      const step = this.graph.nodes.find(n => n.ref === node.id);
      if (step) step._vfPos = { ...node.position };
    },

    // 连线创建（拖拽 handle）
    onConnect(params) {
      const { source, target } = params;
      if (!source || !target || source === target) return;

      // 防重复
      const exists = this.graph.edges.find(e => e.from === source && e.to === target);
      if (exists) return;

      // 同步到 graph.edges（用真实 ref；占位节点的 source/target 就是 _uid，暂存即可）
      this.graph.edges.push({ from: source, to: target, condition: null });

      // 标记分支
      const sourceNode = this.graph.nodes.find(n => n.ref === source || n._uid === source);
      if (sourceNode) {
        const outEdges = this.graph.edges.filter(e => e.from === source);
        if (outEdges.length > 1) sourceNode.branchMode = true;
      }

      // 直接往 vfEdges push，不重建整图（防止占位节点被覆盖）
      const edgeStyle = { stroke: '#818cf8', strokeWidth: 2 };
      this.vfEdges.push({
        id: `vfe-${source}-${target}-${Date.now()}`,
        source,
        target,
        type: 'smoothstep',
        label: '',
        animated: false,
        style: edgeStyle,
        markerEnd: { type: 'arrowclosed', color: '#818cf8', width: 16, height: 16 }
      });
    },

    // 双击边打开条件编辑浮层
    onEdgeDoubleClick({ edge }) {
      const sourceRef = edge.source;
      const sourceNode = this.graph.nodes.find(n => n.ref === sourceRef);
      const isClassifierSource = sourceNode && sourceNode.type === 'classifier';
      // 找到 graph.edges 里对应的真实边，读取其原始 condition（画布 label 分类节点显示的是中文名）
      const ve = this.vfEdges.find(v => v.id === edge.id);
      const ge = ve ? this.graph.edges.find(e => e.from === ve.source && e.to === ve.target) : null;
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
        return expectedId && e.from === expectedId.source && e.to === expectedId.target;
      });
      if (ge) {
        if (this.edgeEditor.sourceBranches) {
          // 分类节点出边：condition 直接取选中的 slug（空=默认兜底边）
          ge.condition = this.edgeEditor.condition ? this.edgeEditor.condition : null;
        } else {
          // 非分类节点：沿用原逻辑
          ge.condition = this.edgeEditor.isCondition ? (this.edgeEditor.condition.trim() || null) : null;
          const sourceNode = this.graph.nodes.find(n => n.ref === ge.from);
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
        this.graph.edges = this.graph.edges.filter(e => !(e.from === ve.source && e.to === ve.target));
      }
      this.edgeEditor.visible = false;
      this.buildVfGraph();
    },

    // 点击节点：计算浮层位置并显示
    onCanvasNodeClick({ node }) {
      if (node.id === '__start__' || node.id === '__end__') return;
      const step = this.graph.nodes.find(n => n.ref === node.id || n._uid === node.id);
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
      const step = this.popover.node;
      step.id = step.ref;
      // 移除旧的占位 vfNode（id = _uid），buildVfGraph 会用新 ref 重建
      this.vfNodes = this.vfNodes.filter(n => n.id !== step._uid && n.id !== step.ref);
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
        targetRef: ''
      });
    },

    // 分类节点：删除分支出口，并联动同步边
    removeBranch(index) {
      this.popover.node.branches.splice(index, 1);
      this.syncClassifierBranches();
    },

    // 分类节点：选中目标节点后自动预填含义（用户未手填时）
    onBranchTargetChange(branch) {
      if (branch && branch.targetRef && (!branch.label || !branch.label.trim())) {
        const t = this.classifierTargets.find(x => x.ref === branch.targetRef);
        if (t) branch.label = t.name;
      }
      this.syncClassifierBranches();
    },

    // 分类节点：把分支 + 默认路径同步为该节点的出边（画布只读展示）
    syncClassifierBranches() {
      const node = this.popover.node;
      if (!node || node.type !== 'classifier' || !node.ref) return;
      const selfRef = node.ref;
      // 先移除该分类节点原有的所有出边
      this.graph.edges = this.graph.edges.filter(e => e.from !== selfRef);
      // 每条有目标的分支 → 一条带 slug condition 的边
      (node.branches || []).forEach(b => {
        if (b.targetRef) {
          this.graph.edges.push({ from: selfRef, to: b.targetRef, condition: b.slug });
        }
      });
      // 默认兜底路径 → 一条 condition 为空的边
      if (node.defaultTarget) {
        this.graph.edges.push({ from: selfRef, to: node.defaultTarget, condition: null });
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
    },

    async startTestRun() {
      if (!this.testRun.input.trim()) {
        this.$message.warning('请输入测试问题');
        return;
      }
      this.testRun.running = true;
      this.testRun.logs = [];
      this.clearNodeHighlight();

      const baseUrl = import.meta.env.VITE_APP_BASE_API || '';
      const threadId = 'test_' + Date.now();
      // 不传 conversationId → 后端跳过存消息，不污染真实会话
      const url = `${baseUrl}/ai/workflow/stream?workflowCode=${encodeURIComponent(this.form.workflowCode)}&message=${encodeURIComponent(this.testRun.input)}&threadId=${threadId}`;
      const token = getToken();

      try {
        const response = await fetch(url, { method: 'GET', headers: { Authorization: 'Bearer ' + token } });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const reader = response.body.getReader();
        this.testRun.reader = reader;
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let sseEvent = null;

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop();
          for (const line of lines) {
            if (line.startsWith('event:')) {
              sseEvent = line.slice(6).trim();
            } else if (line.startsWith('data:')) {
              const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5);
              this.handleTestRunEvent(sseEvent || 'message', data);
            }
          }
        }
      } catch (err) {
        this.testRun.logs.push({ type: 'error', text: '执行失败: ' + err.message });
      } finally {
        this.testRun.running = false;
        this.testRun.reader = null;
      }
    },

    handleTestRunEvent(event, data) {
      const parts = data.split('|');
      const nodeCode = parts[0];
      if (event === 'node_start') {
        const nodeName = parts[1] || nodeCode;
        this.testRun.logs.push({ type: 'start', text: `执行节点：${this.getAgentShortName(nodeCode) || nodeName}` });
        this.highlightNode(nodeCode);
      } else if (event === 'node_route') {
        const decision = parts[1] || '';
        let label = decision;
        const srcNode = this.graph.nodes.find(n => n.ref === nodeCode);
        if (srcNode && srcNode.branches) {
          const br = srcNode.branches.find(b => b.slug === decision);
          if (br) label = br.label;
        }
        this.testRun.logs.push({ type: 'route', text: `分类判定 → ${label || '（无匹配，走默认路径）'}` });
        this.highlightRoute(nodeCode, decision);
      } else if (event === 'node_done') {
        this.testRun.logs.push({ type: 'done', text: `节点完成：${this.getAgentShortName(nodeCode) || nodeCode}` });
      } else if (event === 'workflow_done') {
        this.testRun.logs.push({ type: 'done', text: '✓ 工作流执行完成' });
        this.testRun.running = false;
      } else if (event === 'error') {
        this.testRun.logs.push({ type: 'error', text: data });
        this.testRun.running = false;
      } else if (event === 'node_error') {
        this.testRun.logs.push({ type: 'error', text: `节点异常：${parts[1] || nodeCode}` });
      }
    },

    stopTestRun() {
      if (this.testRun.reader) {
        try { this.testRun.reader.cancel(); } catch (e) {}
        this.testRun.reader = null;
      }
      this.testRun.running = false;
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
        const ge = this.graph.edges.find(g => g.from === e.source && g.to === e.target);
        const cond = ge && ge.condition ? ge.condition : '';
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
      const ref = step.ref;
      const uid = step._uid;
      // 从 graph.nodes 删除
      const idx = this.graph.nodes.findIndex(n => n._uid === uid);
      if (idx !== -1) this.graph.nodes.splice(idx, 1);
      // 删除关联边
      if (ref) this.graph.edges = this.graph.edges.filter(e => e.from !== ref && e.to !== ref);
      // 从 vfNodes 删除占位或正式节点
      this.vfNodes = this.vfNodes.filter(n => n.id !== uid && n.id !== ref);
      this.vfEdges = this.vfEdges.filter(e => e.source !== ref && e.target !== ref);
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
          const edgesByFrom = {};
          if (this.graph.edges) {
            this.graph.edges.forEach(e => {
              if (!edgesByFrom[e.from]) edgesByFrom[e.from] = [];
              edgesByFrom[e.from].push(e);
            });
          }
          this.graph.nodes = this.graph.nodes.map(n => {
            const stepRef = n.ref || n.id;
            const step = {
              _uid: Date.now() + '_' + Math.random().toString(36).substr(2, 5),
              ref: stepRef,
              type: n.type || ('sys_task' === stepRef ? 'java' : 'agent'),
              requireApproval: !!n.requireApproval,
              timeoutSeconds: n.timeoutSeconds || 120,
              branchMode: false,
              routes: [],
              defaultPath: "",
              branches: n.type === 'classifier' ? (n.branches || []) : undefined,
              defaultTarget: n.type === 'classifier' ? '' : undefined,
              modelConfigId: n.modelConfigId || null,
              clfName: n.clfName || (n.type === 'classifier' ? '意图分类' : undefined)
            };
            const outEdges = edgesByFrom[stepRef] || [];
            const conditionalEdges = outEdges.filter(e => e.condition != null && e.condition.trim() !== "");
            const defaultEdge = outEdges.find(e => e.condition == null || e.condition.trim() === "");
            if (n.type === 'classifier') {
              // 分类节点：从边反推每条分支的目标节点与默认兜底路径
              (step.branches || []).forEach(b => {
                const edge = conditionalEdges.find(e => e.condition === b.slug);
                b.targetRef = edge ? edge.to : '';
              });
              if (defaultEdge) step.defaultTarget = defaultEdge.to;
            } else if (conditionalEdges.length > 0) {
              step.branchMode = true;
              step.routes = conditionalEdges.map(e => ({ condition: e.condition, targetRef: e.to }));
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
                ref: refCode,
                type: typeof item === 'string' ? ('sys_task' === item ? 'java' : 'agent') : (item.type || ('sys_task' === refCode ? 'java' : 'agent')),
                requireApproval: typeof item === 'string' ? false : !!item.requireApproval,
                timeoutSeconds: typeof item === 'string' ? 120 : (item.timeoutSeconds || 120),
                branchMode: false,
                routes: [],
                defaultPath: ""
              };
            });
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
            if (!this.nodeSteps[i] || !this.nodeSteps[i].ref) {
              this.$message.warning(`步骤 ${i + 1} 未绑定任何节点，请选择`);
              return;
            }
          }
          if (this.mermaidError) {
            this.$message.error(`拓扑图配置存在异常: ${this.mermaidError}，请检查连线流向再行保存`);
            return;
          }
          
          this.form.nodes = JSON.stringify(this.nodeSteps);
          this.form.graphJson = this.buildGraphJsonFromSteps();
          try {
            let res;
            if (this.form.id != null) {
              res = await updateWorkflow(this.form);
            } else {
              res = await addWorkflow(this.form);
            }
            if (res.code === 200) {
              this.$message.success("保存成功");
              this.viewMode = 'list';
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

<style scoped>
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

/* 列表卡片样式 */
.card-list-container {
  margin-top: 10px;
}
.card-col {
  margin-bottom: 24px;
}
.workflow-card {
  background: var(--el-fill-color-blank) !important;
  border: 1px solid var(--el-border-color-light) !important;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05) !important;
  transition: all 0.3s ease;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 240px;
  color: var(--el-text-color-regular) !important;
}
.workflow-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 25px var(--el-color-primary-light-8);
  border-color: var(--el-color-primary-light-5);
}
.workflow-card.is-disabled {
  opacity: 0.5;
  background: var(--el-fill-color-light) !important;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--el-border-color-light) !important;
  background: transparent !important;
}
.workflow-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-dark-2) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 20px;
  box-shadow: 0 4px 8px var(--el-color-primary-light-5);
}
.header-info {
  flex: 1;
  overflow: hidden;
}
.workflow-title-text {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary) !important;
  margin: 0 0 4px;
}
.workflow-code-tag {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-family: Menlo, Monaco, Consolas, monospace;
}
.card-body {
  padding: 16px 20px;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}
.desc-box {
  margin-bottom: 10px;
}
.desc-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 横向链路可视化 */
.pipeline-visualization {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;
}
.mini-graph-visualization {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;
}
.mini-graph-canvas {
  display: flex;
  justify-content: center;
  overflow: auto;
  max-height: 220px;
  background: rgba(129,140,248,0.04);
  border-radius: 8px;
  padding: 4px 0;
}
.mini-node { stroke-width: 1.5; }
.mini-node-term { fill: var(--el-color-primary); stroke: none; }
.mini-node-agent { fill: var(--el-bg-color); stroke: #cbd5e1; }
.mini-node-classifier { fill: #a855f7; stroke: #c084fc; }
.mini-node-java { fill: #16a34a; stroke: #86efac; }
.mini-node-text {
  font-size: 10px;
  fill: var(--el-text-color-primary);
  font-weight: 600;
  pointer-events: none;
}
.mini-node-text-light { fill: #fff; }
.pipeline-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}
.pipeline-flow-container {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
}
.pipeline-node {
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 20px;
  padding: 4px 12px;
  font-size: 11px;
  color: var(--el-color-primary);
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  box-shadow: 0 1px 3px rgba(var(--el-color-primary-rgb), 0.05);
}
.pipeline-node.is-java {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-5);
  color: var(--el-color-success);
}
.pipeline-node el-icon {
  font-size: 11px;
}
.pipeline-arrow {
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  animation: pulseArrow 1.5s infinite ease-in-out;
}
@keyframes pulseArrow {
  0%, 100% { opacity: 0.4; transform: scale(0.9); }
  50% { opacity: 1; transform: scale(1.1); }
}
.card-footer {
  padding: 12px 20px;
  background: var(--el-fill-color-light);
  border-top: 1px solid var(--el-border-color-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.create-time-text {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}
.footer-action-btn {
  font-weight: 600;
  font-size: 12px;
  padding: 0 4px;
}
.footer-action-btn.edit {
  color: var(--el-color-primary);
}
.footer-action-btn.delete {
  color: var(--el-color-danger);
}

/* ================================================================
   三栏编排工作台样式（跟随项目主题变量）
   ================================================================ */
.workflow-workbench-container {
  animation: fadeIn 0.4s ease;
  background-color: var(--el-bg-color);
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
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  padding: 12px 20px;
  margin-bottom: 14px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.15);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.back-btn {
  border: 1px solid var(--el-border-color);
  background: transparent;
  color: var(--el-text-color-regular);
  transition: all 0.2s;
}
.back-btn:hover {
  transform: scale(1.05);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.workbench-title-text {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.workbench-header .el-button {
  color: var(--el-text-color-regular);
  border-color: var(--el-border-color);
  background: transparent;
}
.workbench-header .el-button:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

/* 核心两栏布局 */
.workbench-body {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  width: 100%;
}

/* 左面板：260px */
.editor-left-pane {
  width: 260px;
  flex-shrink: 0;
}
.pane-card {
  border-radius: 12px;
  border: 1px solid var(--el-border-color) !important;
  background: var(--el-bg-color-overlay) !important;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1) !important;
}
.pane-card :deep(.el-card__header) {
  background: var(--el-bg-color-overlay) !important;
  border-bottom: 1px solid var(--el-border-color) !important;
  padding: 12px 16px;
}
.pane-card :deep(.el-card__body) {
  background: var(--el-bg-color-overlay) !important;
  padding: 16px;
}
.pane-card :deep(.el-form-item__label) {
  color: var(--el-text-color-regular) !important;
}
.pane-card :deep(.el-input__wrapper) {
  background: var(--el-bg-color) !important;
  border-color: var(--el-border-color) !important;
  box-shadow: none !important;
}
.pane-card :deep(.el-input__inner) {
  color: var(--el-text-color-primary) !important;
}
.pane-card :deep(.el-textarea__inner) {
  background: var(--el-bg-color) !important;
  border-color: var(--el-border-color) !important;
  color: var(--el-text-color-primary) !important;
  box-shadow: none !important;
}
.pane-card :deep(.el-radio__label) {
  color: var(--el-text-color-regular) !important;
}
.pane-card-header {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
  gap: 6px;
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
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color);
  border-radius: 10px 10px 0 0;
  padding: 10px 16px;
}
.canvas-title-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}
.toolbar-actions {
  display: flex;
  gap: 8px;
}
.vf-canvas-wrapper {
  position: relative;
  height: calc(100vh - 200px);
  min-height: 560px;
  border-radius: 0 0 10px 10px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-top: none;
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
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color-overlay);
  color: var(--el-text-color-regular);
  padding: 6px 14px;
  min-width: 80px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  transition: all 0.2s ease;
  cursor: pointer;
}
.vue-flow-editor :deep(.vue-flow__node:hover),
.vue-flow-editor :deep(.vue-flow__node.selected) {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7), 0 4px 16px var(--el-color-primary-light-8);
  background: var(--menu-hover);
}
.vue-flow-editor :deep(.vue-flow__node.vf-node-terminal) {
  background: linear-gradient(135deg, var(--el-color-primary), var(--el-color-success));
  color: #fff;
  border: none;
  border-radius: 50px;
  letter-spacing: 0.5px;
  font-size: 11px;
  font-weight: 700;
  padding: 5px 8px;
  box-shadow: 0 0 10px rgba(64,158,255,0.35);
  cursor: default;
}
.vue-flow-editor :deep(.vue-flow__node.vf-node-classifier) {
  background: linear-gradient(135deg, #7c3aed, #a855f7);
  color: #fff;
  border: 2px solid #c084fc;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  padding: 5px 8px;
  box-shadow: 0 0 12px rgba(168,85,247,0.4);
}
.vue-flow-editor :deep(.vue-flow__node.vf-node-active) {
  animation: nodePulse 1s ease-in-out infinite;
}
@keyframes nodePulse {
  0%, 100% { box-shadow: 0 0 0 3px #22c55e, 0 0 12px rgba(34,197,94,0.4); }
  50% { box-shadow: 0 0 0 4px #22c55e, 0 0 22px rgba(34,197,94,0.8); }
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
  width: 12px;
  height: 12px;
  background: var(--el-color-primary);
  border: 2px solid var(--sidebar-bg);
  border-radius: 50%;
  transition: all 0.2s;
  opacity: 1 !important;
  pointer-events: all !important;
}
.vue-flow-editor :deep(.vue-flow__handle:hover) {
  background: var(--el-color-warning);
  transform: scale(1.5);
  box-shadow: 0 0 10px var(--el-color-warning-light-5);
  cursor: crosshair;
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
  border-color: var(--el-color-warning-light-3);
  color: var(--el-color-warning);
  background: var(--el-bg-color-overlay);
}
.vue-flow-editor :deep(.vue-flow__node.vf-node-unconfigured:hover) {
  border-color: var(--el-color-warning);
  box-shadow: 0 0 0 2px var(--el-color-warning-light-7), 0 4px 16px var(--el-color-warning-light-8);
}
.node-popover {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 300px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.3);
  z-index: 1000;
  overflow: hidden;
}
.edge-editor-popover {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 300px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.3);
  z-index: 1001;
  overflow: hidden;
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
  padding: 10px 14px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color);
}
.popover-title {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}
.popover-close-btn {
  background: transparent !important;
  border: none !important;
  color: var(--el-text-color-secondary) !important;
  padding: 2px !important;
}
.popover-close-btn:hover {
  color: var(--el-color-danger) !important;
}
.popover-body {
  padding: 12px 14px;
  max-height: 480px;
  overflow-y: auto;
}
.popover-body :deep(.el-form-item) {
  margin-bottom: 12px;
}
.popover-body :deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
  font-size: 11.5px;
  font-weight: 600;
}
.popover-body :deep(.el-input__wrapper),
.popover-body :deep(.el-select .el-input__wrapper) {
  background: var(--el-bg-color) !important;
  border-color: var(--el-border-color) !important;
  box-shadow: none !important;
}
.popover-body :deep(.el-input__inner),
.popover-body :deep(.el-select__placeholder) {
  color: var(--el-text-color-primary) !important;
}
.popover-divider {
  height: 1px;
  background: var(--el-border-color);
  margin: 10px 0;
}
.popover-agent-props {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
}
.prop-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.prop-label-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--el-text-color-regular);
  display: flex;
  align-items: center;
  gap: 4px;
}
.popover-prompt-preview {
  background: var(--el-bg-color);
  border-radius: 6px;
  padding: 8px 10px;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 10.5px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  max-height: 100px;
  overflow-y: auto;
  white-space: pre-wrap;
  border: 1px solid var(--el-border-color);
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
  background: rgba(15, 23, 42, 0.6);
}
.vue-flow-preview :deep(.vue-flow__node) {
  font-size: 12.5px;
  font-weight: 700;
  border-radius: 8px;
  border: 1.5px solid rgba(129, 140, 248, 0.5);
  background: rgba(99, 102, 241, 0.12);
  color: #a5b4fc;
  padding: 10px 16px;
  min-width: 100px;
  text-align: center;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
  transition: all 0.2s ease;
  cursor: pointer;
}
.vue-flow-preview :deep(.vue-flow__node:hover) {
  background: rgba(99, 102, 241, 0.22);
  border-color: rgba(129, 140, 248, 0.9);
  box-shadow: 0 0 14px rgba(99, 102, 241, 0.4);
  transform: translateY(-1px);
}
.vue-flow-preview :deep(.vue-flow__node.vf-node-terminal) {
  background: linear-gradient(135deg, #6366f1, #818cf8);
  color: #fff;
  border: none;
  border-radius: 50px;
  letter-spacing: 1px;
  box-shadow: 0 0 16px rgba(99, 102, 241, 0.5);
  cursor: default;
}
.vue-flow-preview :deep(.vue-flow__node.vf-node-terminal:hover) {
  transform: none;
}
.vue-flow-preview :deep(.vue-flow__handle) {
  opacity: 0 !important;
  pointer-events: none !important;
}
</style>
