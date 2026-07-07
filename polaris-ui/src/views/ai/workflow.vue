<template>
  <div class="app-container ai-workflow-manager">
    <!-- 1. 工作流配置列表模式 -->
    <div v-if="viewMode === 'list'">
      <!-- 顶部高效筛选栏 -->
      <div class="filter-container">
        <el-form :model="queryParams" ref="queryForm" :inline="true" size="small" class="demo-form-inline">
          <el-form-item label="工作流编码">
            <el-input v-model="queryParams.workflowCode" placeholder="请输入编码" clearable @keyup.enter.native="handleQuery" style="width: 180px;"/>
          </el-form-item>
          <el-form-item label="工作流名称">
            <el-input v-model="queryParams.workflowName" placeholder="请输入名称" clearable @keyup.enter.native="handleQuery" style="width: 180px;"/>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px;">
              <el-option label="正常" value="1"/>
              <el-option label="禁用" value="0"/>
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
            <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
            <el-button class="btn-gradient-success" icon="el-icon-plus" type="success" @click="handleAdd">新增工作流</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 卡片式工作流总览列表 -->
      <div v-loading="loading" class="card-list-container">
        <div v-if="workflowList.length === 0" class="empty-state">
          <div class="empty-icon">⚙️</div>
          <p>暂无工作流编排，点击上方“新增工作流”按钮开始设计您的 AI 智能体工作流吧！</p>
        </div>

        <el-row v-else :gutter="20">
          <el-col v-for="item in workflowList" :key="item.id" :lg="12" :md="12" :sm="24" :xs="24" class="card-col">
            <div :class="['workflow-card', { 'is-disabled': item.status === '0' }]">
              <!-- 卡片头：发光管线图标与状态 -->
              <div class="card-header">
                <div class="workflow-avatar">
                  <i class="el-icon-s-operation"></i>
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

                <!-- 精美的横向执行链路可视化 -->
                <div class="pipeline-visualization">
                  <span class="pipeline-label">执行流向：</span>
                  <div class="pipeline-flow-container">
                    <template v-for="(node, idx) in parseNodes(item.nodes)">
                      <div :key="'node-'+idx" :class="['pipeline-node', { 'is-java': node === 'sys_task' }]">
                        <i :class="node === 'sys_task' ? 'el-icon-setting' : 'el-icon-cpu'"></i>
                        <span class="node-name-text">{{ getAgentShortName(node) }}</span>
                      </div>
                      <div v-if="idx < parseNodes(item.nodes).length - 1" :key="'arrow-'+idx" class="pipeline-arrow">
                        <i class="el-icon-right"></i>
                      </div>
                    </template>
                  </div>
                </div>
              </div>

              <!-- 卡片页脚：时间与操作 -->
              <div class="card-footer">
                <span class="create-time-text"><i class="el-icon-time"></i> {{ formatDate(item.createTime) }}</span>
                <div class="action-buttons">
                  <el-button class="footer-action-btn edit" icon="el-icon-edit" size="mini" type="text" @click="handleUpdate(item)">编辑编排</el-button>
                  <el-button class="footer-action-btn delete" icon="el-icon-delete" size="mini" type="text" @click="handleDelete(item)">删除</el-button>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>

    <!-- 2. 独立整屏工作流编排工作台 (升级三栏：配置、极简画布、右侧属性控制抽屉) -->
    <div v-else class="workflow-workbench-container">
      <!-- 顶部控制条 (SaaS Header，提供保存和取消) -->
      <div class="workbench-header">
        <div class="header-left">
          <el-button icon="el-icon-back" size="small" circle @click="cancel" class="back-btn"></el-button>
          <span class="workbench-title-text">{{ form.id ? '编辑智能体工作流管线' : '创建智能体工作流管线' }}</span>
        </div>
        <div class="header-right">
          <el-button size="small" icon="el-icon-close" @click="cancel">取消返回</el-button>
          <el-button type="primary" size="small" class="btn-primary-glow" icon="el-icon-circle-check" @click="submitForm">保存并发布</el-button>
        </div>
      </div>

      <!-- 工作台三栏布局 (左配置、中画布、右属性抽屉) -->
      <div class="workbench-body">
        <!-- 左侧面板：元数据基础配置 -->
        <div class="editor-left-pane">
          <el-card class="pane-card" shadow="never">
            <div slot="header" class="pane-card-header">
              <span><i class="el-icon-info"></i> 工作流基础配置</span>
            </div>
            <el-form ref="form" :model="form" :rules="rules" label-position="top" size="small">
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

        <!-- 中间面板：极简拓扑画布 (拉大卡片间距，突出连线和拖拽) -->
        <div class="editor-middle-pane">
          <div class="canvas-panel">
            <div class="canvas-header">
              <span class="canvas-title-text"><i class="el-icon-connection"></i> 执行节点画布 (长按拖拽步骤，点击配置属性)</span>
              <el-button type="primary" icon="el-icon-plus" size="mini" class="btn-primary-glow" @click="addNodeStep">添加步骤</el-button>
            </div>

            <div class="steps-canvas-wrapper">
              <div v-if="nodeSteps.length === 0" class="no-steps-empty">
                <i class="el-icon-copy-document" style="font-size: 44px; color: #cbd5e1; margin-bottom: 12px;"></i>
                <div class="empty-title">画布空空如也</div>
                <div class="empty-subtitle">请点击右上角“添加步骤”按钮设计您的智能体执行节点</div>
              </div>

              <div v-else class="node-steps-editor-wrapper">
                <transition-group name="list" tag="div" class="node-steps-editor">
                  <div
                    v-for="(node, index) in nodeSteps"
                    :key="'editor-step-' + index"
                    :class="['editor-step-row', { 'is-active': activeStepIndex === index }]"
                    draggable="true"
                    @dragstart="handleDragStart($event, index)"
                    @dragover.prevent
                    @dragenter="handleDragEnter($event, index)"
                    @dragend="handleDragEnd"
                    @click="activeStepIndex = index"
                  >
                    <!-- 步骤序号圆标 -->
                    <div class="step-num-badge">{{ index + 1 }}</div>

                    <!-- 卡片主体：仅展示节点名称与编码，保持画布极其清爽 -->
                    <div class="step-node-mini-card">
                      <h4 class="mini-node-title">{{ getAgentShortName(nodeSteps[index]) || '未选择执行节点' }}</h4>
                      <span class="mini-node-code" v-if="nodeSteps[index]">({{ nodeSteps[index] }})</span>
                    </div>

                    <!-- 删除操作 -->
                    <div class="step-action-area" @click.stop>
                      <el-button
                        type="danger"
                        icon="el-icon-delete"
                        size="mini"
                        circle
                        @click="deleteNodeStep(index)"
                      />
                    </div>
                  </div>
                </transition-group>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧面板：节点属性控制抽屉面板 (通过点击节点触发渲染) -->
        <div class="editor-right-pane">
          <el-card class="pane-card config-panel-card" shadow="never">
            <div slot="header" class="pane-card-header">
              <span><i class="el-icon-setting"></i> 步骤节点属性面板</span>
            </div>

            <!-- 空态提示：未选择任何步骤 -->
            <div v-if="activeStepIndex === null" class="no-active-step-hint">
              <i class="el-icon-mouse" style="font-size: 32px; color: #cbd5e1; margin-bottom: 10px;"></i>
              <p>请点击中间画布上的某个步骤节点卡片以审查和配置其详细参数</p>
            </div>

            <!-- 选择步骤后的详细设置区 -->
            <el-form v-else label-position="top" size="small" class="node-config-form">
              <div class="selected-step-indicator">
                <span class="indicator-badge">步骤 {{ activeStepIndex + 1 }}</span>
                <span class="indicator-text">当前节点属性配置</span>
              </div>

              <!-- 执行节点选择 -->
              <el-form-item label="指向执行组件/智能体">
                <el-select v-model="nodeSteps[activeStepIndex]" placeholder="请选择智能体 / 代码组件" style="width: 100%;" size="medium">
                  <el-option-group label="AI 智能体">
                    <el-option
                      v-for="item in activeAgents"
                      :key="item.id"
                      :label="item.agentName"
                      :value="item.agentCode"
                    />
                  </el-option-group>
                  <el-option-group label="系统 Java 节点 (代码任务)">
                    <el-option label="[系统任务] (sys_task)" value="sys_task" />
                  </el-option-group>
                </el-select>
              </el-form-item>

              <!-- 智能体实时属性透视 (只读面板) -->
              <div v-if="nodeSteps[activeStepIndex] && nodeSteps[activeStepIndex] !== 'sys_task' && getAgentByCode(nodeSteps[activeStepIndex])" class="agent-live-properties">

                <!-- 大模型底座 -->
                <div class="prop-group">
                  <span class="prop-label-title"><i class="el-icon-cpu"></i> 底座大模型</span>
                  <el-tag size="small" type="primary" effect="plain" class="full-width-tag">
                    {{ getAgentByCode(nodeSteps[activeStepIndex]).modelName }}
                  </el-tag>
                </div>

                <!-- 温度参数 -->
                <div class="prop-group">
                  <span class="prop-label-title"><i class="el-icon-odometer"></i> 随机温度 (Temperature): {{ getAgentByCode(nodeSteps[activeStepIndex]).temperature }}</span>
                  <el-progress :percentage="getAgentByCode(nodeSteps[activeStepIndex]).temperature * 100" :show-text="false" :stroke-width="5" color="#6366f1" />
                </div>

                <!-- 绑定系统工具 -->
                <div class="prop-group">
                  <span class="prop-label-title"><i class="el-icon-folder-opened"></i> 绑定系统工具</span>
                  <div class="live-tools-badges">
                    <span v-if="!getAgentByCode(nodeSteps[activeStepIndex]).tools" class="no-tools-info">未绑定任何工具</span>
                    <el-tag
                      v-else
                      v-for="(t, tIdx) in getAgentByCode(nodeSteps[activeStepIndex]).tools.split(',')"
                      :key="tIdx"
                      size="mini"
                      type="success"
                      class="tool-tag-pill"
                    >
                      {{ t }}
                    </el-tag>
                  </div>
                </div>

                <!-- 核心系统提示词 -->
                <div class="prop-group">
                  <span class="prop-label-title"><i class="el-icon-document"></i> 大脑核心指令 (System Prompt)</span>
                  <div class="live-prompt-editor-preview">
                    {{ getAgentByCode(nodeSteps[activeStepIndex]).systemPrompt }}
                  </div>
                </div>
              </div>

              <!-- Java 节点实时参数透视 -->
              <div v-else-if="nodeSteps[activeStepIndex] === 'sys_task'" class="agent-live-properties">
                <div class="prop-group">
                  <span class="prop-label-title"><i class="el-icon-setting"></i> 执行类型</span>
                  <el-tag size="small" type="success" effect="plain" class="full-width-tag">本地 Java 业务处理任务</el-tag>
                </div>
                <div class="prop-group">
                  <span class="prop-label-title"><i class="el-icon-warning-outline"></i> 说明</span>
                  <p class="java-node-hint-text">本步骤将调用非大模型的后台 Java Bean (实现 WorkflowNodeExecutor 接口的类组件) 串行处理业务，直接向下传递上下文。</p>
                </div>
              </div>
            </el-form>
          </el-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import {addWorkflow, delWorkflow, getWorkflow, listWorkflow, updateWorkflow} from "@/api/ai/workflow";
import {listAllAgents} from "@/api/ai/agent";

export default {
  name: "AiWorkflowManager",
  data() {
    return {
      loading: true,
      total: 0,
      workflowList: [],
      activeAgents: [],
      nodeSteps: [],
      draggedIndex: null,
      activeStepIndex: null, // 当前选中的步骤下标
      viewMode: 'list', // list: 列表模式, edit: 大屏编排工作台
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
  created() {
    this.getList();
    this.loadActiveAgents();
  },
  methods: {
    async getList() {
      this.loading = true;
      try {
        const res = await listWorkflow(this.queryParams);
        if (res.code === 200) {
          this.workflowList = res.data.rows || res.data || [];
          this.total = res.data.total || this.workflowList.length;
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
    parseNodes(nodesJson) {
      try {
        return JSON.parse(nodesJson) || [];
      } catch (e) {
        return [];
      }
    },
    getAgentShortName(nodeCode) {
      if (nodeCode === 'sys_task') {
        return "系统任务";
      }
      const agent = this.activeAgents.find(a => a.agentCode === nodeCode);
      return agent ? agent.agentName : nodeCode;
    },
    getAgentByCode(nodeCode) {
      return this.activeAgents.find(a => a.agentCode === nodeCode);
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
      this.nodeSteps = [];
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
    addNodeStep() {
      this.nodeSteps.push("");
      // 自动高亮选中新节点，方便在右边控制台直接配置它
      this.activeStepIndex = this.nodeSteps.length - 1;
    },
    deleteNodeStep(index) {
      this.nodeSteps.splice(index, 1);
      this.activeStepIndex = null;
    },

    // H5 原生拖拽逻辑
    handleDragStart(e, index) {
      this.draggedIndex = index;
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', index);
      e.target.classList.add('is-dragging');
      this.activeStepIndex = null; // 拖拽中先虚焦
    },
    handleDragEnter(e, index) {
      if (this.draggedIndex !== null && this.draggedIndex !== index) {
        const steps = [...this.nodeSteps];
        const draggedItem = steps[this.draggedIndex];
        steps.splice(this.draggedIndex, 1);
        steps.splice(index, 0, draggedItem);
        this.draggedIndex = index;
        this.nodeSteps = steps;
      }
    },
    handleDragEnd(e) {
      this.activeStepIndex = this.draggedIndex; // 拖完后重新聚焦到落下的卡片
      this.draggedIndex = null;
      if (e.target && e.target.classList) {
        e.target.classList.remove('is-dragging');
      }
    },

    handleAdd() {
      this.reset();
      this.viewMode = 'edit';
      this.$nextTick(() => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      });
    },
    async handleUpdate(row) {
      this.reset();
      try {
        const res = await getWorkflow(row.id);
        if (res.code === 200) {
          this.form = res.data;
          try {
            this.nodeSteps = JSON.parse(this.form.nodes) || [];
          } catch (e) {
            this.nodeSteps = [];
          }
          this.viewMode = 'edit';
          this.$nextTick(() => {
            window.scrollTo({ top: 0, behavior: 'smooth' });
          });
        }
      } catch (err) {
        console.error(err);
      }
    },
    submitForm() {
      this.$refs.form.validate(async (valid) => {
        if (valid) {
          if (this.nodeSteps.length === 0) {
            this.$message.warning("请至少编排一个执行节点步骤");
            return;
          }
          for (let i = 0; i < this.nodeSteps.length; i++) {
            if (!this.nodeSteps[i]) {
              this.$message.warning(`步骤 ${i + 1} 未绑定任何节点，请选择`);
              return;
            }
          }
          this.form.nodes = JSON.stringify(this.nodeSteps);
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
  background-color: #fafbfe;
  min-height: 100vh;
}
.filter-container {
  background: #ffffff;
  border-radius: 12px;
  padding: 16px 20px 4px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02);
  border: 1px solid #edf2f7;
  margin-bottom: 24px;
}
.btn-gradient-success {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border: none;
  box-shadow: 0 4px 10px rgba(5, 150, 105, 0.15);
  color: #fff;
}
.btn-gradient-success:hover {
  background: linear-gradient(135deg, #059669 0%, #047857 100%);
}

/* 列表卡片样式 */
.card-list-container {
  margin-top: 10px;
}
.card-col {
  margin-bottom: 24px;
}
.workflow-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03);
  transition: all 0.3s ease;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 240px;
}
.workflow-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 25px rgba(109, 40, 217, 0.07);
  border-color: rgba(109, 40, 217, 0.3);
}
.workflow-card.is-disabled {
  opacity: 0.65;
  background: #f8fafc;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid #f1f5f9;
  background: linear-gradient(135deg, #ffffff 0%, #fafafd 100%);
}
.workflow-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 20px;
  box-shadow: 0 4px 8px rgba(99, 102, 241, 0.2);
}
.header-info {
  flex: 1;
  overflow: hidden;
}
.workflow-title-text {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 4px;
}
.workflow-code-tag {
  font-size: 11px;
  color: #94a3b8;
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
  color: #64748b;
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
.pipeline-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
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
  background: #f3f0ff;
  border: 1px solid #d8b4fe;
  border-radius: 20px;
  padding: 4px 12px;
  font-size: 11px;
  color: #6b21a8;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  box-shadow: 0 1px 3px rgba(107, 33, 168, 0.05);
}
.pipeline-node.is-java {
  background: #ecfdf5;
  border-color: #a7f3d0;
  color: #065f46;
}
.pipeline-node i {
  font-size: 11px;
}
.pipeline-arrow {
  color: #94a3b8;
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
  background: #fafbfe;
  border-top: 1px solid #f1f5f9;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.create-time-text {
  font-size: 11px;
  color: #94a3b8;
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
  color: #4f46e5;
}
.footer-action-btn.delete {
  color: #ef4444;
}

/* ================================================================
   三栏编排工作台样式
   ================================================================ */
.workflow-workbench-container {
  animation: fadeIn 0.4s ease;
  background-color: #fcfcfd;
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
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 24px;
  margin-bottom: 24px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.02);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.back-btn {
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 5px rgba(0,0,0,0.03);
  transition: all 0.2s;
}
.back-btn:hover {
  transform: scale(1.05);
  border-color: #6366f1;
  color: #6366f1;
}
.workbench-title-text {
  font-size: 17px;
  font-weight: 800;
  color: #0f172a;
}

/* 核心三栏布局 */
.workbench-body {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  width: 100%;
}

/* 左面板：300px */
.editor-left-pane {
  width: 300px;
  flex-shrink: 0;
}
.pane-card {
  border-radius: 12px;
  border: 1px solid #edf2f7;
  box-shadow: 0 4px 15px rgba(0,0,0,0.01) !important;
}
.pane-card-header {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 6px;
}
.pane-card-header i {
  color: #6366f1;
  font-size: 15px;
}

/* 中面板：自适应宽度画布 */
.editor-middle-pane {
  flex: 1;
}
.canvas-panel {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02);
  background: #ffffff;
}
.canvas-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  padding: 12px 20px;
  border-bottom: 1px solid #edf2f7;
}
.canvas-title-text {
  font-size: 12.5px;
  font-weight: 700;
  color: #475569;
}
.canvas-title-text i {
  color: #4f46e5;
}

/* 超大波点画布 */
.steps-canvas-wrapper {
  background-image: radial-gradient(#cbd5e1 1.2px, transparent 1.2px);
  background-size: 16px 16px;
  background-color: #fafbfe;
  padding: 30px;
  min-height: 540px; /* 超高纵深 */
}
.no-steps-empty {
  text-align: center;
  color: #94a3b8;
  padding: 120px 0;
}
.empty-title {
  font-size: 15px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 6px;
}
.empty-subtitle {
  font-size: 12px;
  color: #94a3b8;
}

.node-steps-editor {
  display: flex;
  flex-direction: column;
  gap: 54px; /* 卡片之间的呼吸间距 */
  position: relative;
}

/* 步骤芯片卡片 (极简设计) */
.editor-step-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  transition: all 0.25s ease;
  position: relative;
  cursor: grab;
}
.editor-step-row:hover {
  border-color: #a5b4fc;
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.06);
  transform: translateY(-1px);
}
.editor-step-row.is-active {
  border-color: #6366f1;
  box-shadow: 0 0 12px rgba(99, 102, 241, 0.22);
  background: linear-gradient(135deg, #ffffff 0%, #fafafd 100%);
}
.editor-step-row:active {
  cursor: grabbing;
}
.editor-step-row.is-dragging {
  opacity: 0.3;
  border: 1px dashed #6366f1;
  background: #f5f3ff;
}

/* 垂直能量管道连线 */
.editor-step-row:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 31px;
  top: 56px;
  bottom: -58px;
  width: 3px;
  background: linear-gradient(to bottom, #6366f1, rgba(99, 102, 241, 0.15), #6366f1);
  background-size: 100% 200%;
  animation: pipelineFlow 1.5s infinite linear;
  z-index: 0;
}
@keyframes pipelineFlow {
  0% { background-position: 0% 0%; }
  100% { background-position: 0% 200%; }
}

/* 序号徽章 */
.step-num-badge {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #94a3b8 0%, #64748b 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 800;
  box-shadow: 0 2px 4px rgba(100, 116, 139, 0.15);
  z-index: 2;
  flex-shrink: 0;
}
.editor-step-row.is-active .step-num-badge {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  box-shadow: 0 3px 6px rgba(79, 70, 229, 0.25);
}

/* 极简内容区 */
.step-node-mini-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  z-index: 1;
}
.mini-node-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}
.editor-step-row.is-active .mini-node-title {
  color: #4f46e5;
}
.mini-node-code {
  font-size: 11.5px;
  color: #94a3b8;
  font-family: Menlo, Monaco, Consolas, monospace;
}

.step-action-area {
  z-index: 1;
}

/* 右侧面板：320px 属性抽屉 */
.editor-right-pane {
  width: 320px;
  flex-shrink: 0;
}
.config-panel-card {
  min-height: 580px; /* 大幅度长面板 */
  background: #ffffff;
}

/* 属性空态 */
.no-active-step-hint {
  text-align: center;
  color: #94a3b8;
  padding: 160px 20px;
  font-size: 12.5px;
  line-height: 1.6;
}

/* 属性控制区 */
.node-config-form {
  animation: slideIn 0.3s ease;
}
@keyframes slideIn {
  from { opacity: 0; transform: translateX(10px); }
  to { opacity: 1; transform: translateX(0); }
}
.selected-step-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
}
.indicator-badge {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 4px;
}
.indicator-text {
  font-size: 12px;
  font-weight: 700;
  color: #475569;
}

/* 智能体参数展示细节 */
.agent-live-properties {
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: #fafbfe;
  border: 1px solid #eef2f6;
  border-radius: 8px;
  padding: 16px;
  margin-top: 10px;
}
.prop-group {
  display: flex;
  flex-direction: column;
}
.prop-label-title {
  font-size: 11.5px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.prop-label-title i {
  color: #6366f1;
}
.full-width-tag {
  width: 100%;
  text-align: center;
  font-weight: 600;
  font-family: Menlo, Monaco, Consolas, monospace;
}
.live-tools-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.tool-tag-pill {
  font-weight: 600;
}
.no-tools-info {
  font-size: 11.5px;
  color: #94a3b8;
  font-style: italic;
}

/* 代码段提示词预览面板 */
.live-prompt-editor-preview {
  background: #1e1e2e; /* 极客深色底 */
  border-radius: 6px;
  padding: 10px 12px;
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
  font-size: 11px;
  color: #cdd6f4;
  line-height: 1.6;
  max-height: 180px;
  overflow-y: auto;
  white-space: pre-wrap;
  border: 1px solid #313244;
}
.java-node-hint-text {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  margin: 4px 0 0;
}

/* 按钮发光 */
.btn-primary-glow {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  border: none;
  box-shadow: 0 4px 10px rgba(79, 70, 229, 0.2);
}
.btn-primary-glow:hover {
  background: linear-gradient(135deg, #4f46e5 0%, #4338ca 100%);
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
  background: #fff;
  border-radius: 16px;
  border: 1px dashed #cbd5e1;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}
.empty-state p {
  color: #64748b;
  font-size: 14px;
}
</style>
