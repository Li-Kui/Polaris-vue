<template>
  <div class="app-container ai-agent-manager">
    <!-- 1. 智能体列表模式 -->
    <div v-if="viewMode === 'list'">
      <!-- 顶部高效筛选与操作栏 -->
      <div class="filter-container">
        <el-form :model="queryParams" ref="queryForm" :inline="true" size="small" class="demo-form-inline">
          <el-form-item label="智能体编码">
            <el-input v-model="queryParams.agentCode" placeholder="请输入编码" clearable @keyup.enter.native="handleQuery" style="width: 180px;"/>
          </el-form-item>
          <el-form-item label="智能体名称">
            <el-input v-model="queryParams.agentName" placeholder="请输入名称" clearable @keyup.enter.native="handleQuery" style="width: 180px;"/>
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
            <el-button class="btn-gradient-success" icon="el-icon-plus" type="success" @click="handleAdd">新增智能体</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 渐变网格化智能体卡片列表 -->
      <div v-loading="loading" class="card-list-container">
        <div v-if="agentList.length === 0" class="empty-state">
          <div class="empty-icon">🧠</div>
          <p>暂无智能体配置，点击上方“新增智能体”按钮开始配置您的 AI 智能体吧！</p>
        </div>

        <el-row v-else :gutter="20">
          <el-col v-for="item in agentList" :key="item.id" :lg="8" :md="8" :sm="12" :xs="24" class="card-col">
            <div :class="['agent-card', { 'is-disabled': item.status === '0' }]">
              <!-- 卡片头部：发光芯片图标与状态开关 -->
              <div class="card-header">
                <div class="agent-avatar">
                  <i class="el-icon-cpu"></i>
                </div>
                <div class="header-info">
                  <h3 class="agent-title-text">{{ item.agentName }}</h3>
                  <span class="agent-code-tag">{{ item.agentCode }}</span>
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

              <!-- 卡片主体：模型、温度、工具与提示词 -->
              <div class="card-body">
                <div class="param-row" style="margin-bottom: 12px;">
                  <span class="param-label"><i class="el-icon-cpu"></i> 选用底座</span>
                  <el-tag size="mini" effect="plain" type="primary">{{ item.modelName }}</el-tag>
                </div>

                <div class="param-row" style="margin-bottom: 12px;">
                  <span class="param-label"><i class="el-icon-odometer"></i> 随机温度</span>
                  <div class="temp-indicator" style="flex: 1; display: flex; align-items: center; gap: 8px; margin-left: 10px;">
                    <el-progress :percentage="item.temperature * 100" :show-text="false" :stroke-width="6" style="flex: 1; margin: 0 !important;" color="#8b5cf6" />
                    <span class="temp-text" style="font-size: 12px; font-weight: bold; color: #6d28d9;">{{ item.temperature }}</span>
                  </div>
                </div>

                <!-- 绑定系统工具 pill tags -->
                <div class="tools-section" style="margin-bottom: 14px;">
                  <span class="param-label" style="margin-bottom: 6px;"><i class="el-icon-folder-opened"></i> 绑定系统工具</span>
                  <div v-if="!item.tools" class="no-tools-text">未绑定任何工具</div>
                  <div v-else class="tool-pills-list">
                    <span
                      v-for="(tool, tIdx) in item.tools.split(',')"
                      :key="tIdx"
                      class="tool-pill-badge"
                    >
                      <i class="el-icon-link"></i> {{ tool }}
                    </span>
                  </div>
                </div>

                <!-- 系统提示词预览 -->
                <div v-if="item.systemPrompt" class="prompt-preview-card">
                  <span class="prompt-label-title">系统提示词 (System Prompt):</span>
                  <p :title="item.systemPrompt" class="prompt-content-text">{{ item.systemPrompt }}</p>
                </div>
              </div>

              <!-- 卡片尾部：日期与操作按钮 -->
              <div class="card-footer">
                <span class="create-time-text"><i class="el-icon-time"></i> {{ formatDate(item.createTime) }}</span>
                <div class="action-buttons">
                  <el-button class="footer-action-btn edit" icon="el-icon-edit" size="mini" type="text" @click="handleUpdate(item)">编辑</el-button>
                  <el-button class="footer-action-btn delete" icon="el-icon-delete" size="mini" type="text" @click="handleDelete(item)">删除</el-button>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>

    <!-- 2. 独立整屏智能体配置工作台 -->
    <div v-else class="agent-workbench-container">
      <!-- 顶部控制 Header -->
      <div class="workbench-header">
        <div class="header-left">
          <el-button icon="el-icon-back" size="small" circle @click="cancel" class="back-btn"></el-button>
          <span class="workbench-title-text">{{ form.id ? '配置智能体核心参数' : '创建全新 AI 智能体' }}</span>
        </div>
        <div class="header-right">
          <el-button size="small" icon="el-icon-close" @click="cancel">取消返回</el-button>
          <el-button type="primary" size="small" class="btn-primary-glow" icon="el-icon-circle-check" @click="submitForm">保存智能体</el-button>
        </div>
      </div>

      <!-- 双栏配置工作区 (左参数配置、右 Prompt 与工具链配置) -->
      <div class="workbench-body">
        <!-- 左侧面板：基础参数 -->
        <div class="editor-left-pane">
          <el-card class="pane-card" shadow="never">
            <div slot="header" class="pane-card-header">
              <span><i class="el-icon-info"></i> 智能体基础属性</span>
            </div>

            <el-form ref="form" :model="form" :rules="rules" label-position="top" size="medium">
              <el-form-item label="智能体名称" prop="agentName">
                <el-input v-model="form.agentName" placeholder="如: 系统安全审计员" />
              </el-form-item>

              <el-form-item label="智能体唯一编码" prop="agentCode">
                <el-input :disabled="!!form.id" v-model="form.agentCode" placeholder="如: sys_user_analyst" />
              </el-form-item>

              <el-form-item label="选用底座大模型" prop="modelName">
                <el-select v-model="form.modelName" placeholder="请选择绑定的底座大模型" style="width: 100%;">
                  <el-option
                    v-for="item in models"
                    :key="item.id"
                    :label="item.name"
                    :value="item.modelName"
                  />
                </el-select>
              </el-form-item>

              <!-- 随机温度滑块，大屏完全延展舒展 -->
              <el-form-item label="随机温度 (Temperature)">
                <div class="temp-slider-box">
                  <el-slider
                    v-model="form.temperature"
                    :max="1"
                    :step="0.05"
                    show-input
                    input-size="mini"
                  />
                  <div class="temp-slider-tips">温度越低，大模型输出越确定、越精准；温度越高则越有创造性。</div>
                </div>
              </el-form-item>

              <el-form-item label="开启状态">
                <el-radio-group v-model="form.status">
                  <el-radio label="1">正常启用</el-radio>
                  <el-radio label="0">停用禁用</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item label="用途备注说明" prop="remark">
                <el-input v-model="form.remark" placeholder="备注该智能体用途说明..." />
              </el-form-item>
            </el-form>
          </el-card>
        </div>

        <!-- 右侧面板：系统核心提示词 Prompt 与系统工具绑定 -->
        <div class="editor-right-pane">
          <!-- 核心指令 -->
          <el-card class="pane-card" shadow="never" style="margin-bottom: 20px;">
            <div slot="header" class="pane-card-header">
              <span><i class="el-icon-document"></i> 大脑核心系统指令 (System Role Prompt)</span>
            </div>
            <el-form ref="formPrompt" :model="form" label-position="top">
              <el-form-item prop="systemPrompt" label-width="0">
                <el-input
                  v-model="form.systemPrompt"
                  type="textarea"
                  :rows="12"
                  placeholder="在此输入指派给大语言模型的 System Instruction/Prompt。定义它扮演的角色、任务边界与工作风格..."
                  class="textarea-code-style"
                />
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 工具装备板 -->
          <el-card class="pane-card" shadow="never">
            <div slot="header" class="pane-card-header">
              <span><i class="el-icon-folder-opened"></i> 绑定系统级扩展工具 (Dynamic Tools)</span>
            </div>

            <div v-if="systemTools.length === 0" class="no-tools-text" style="padding: 10px 0;">
              系统中暂未注册任何实现 AiTool 接口的工具组件
            </div>
            <div v-else class="tool-checkbox-large-grid">
              <el-checkbox-group v-model="selectedTools">
                <el-row :gutter="16">
                  <el-col v-for="tool in systemTools" :key="tool.name" :span="12" class="tool-check-col">
                    <div :class="['tool-checkbox-card', { 'is-checked': selectedTools.includes(tool.name) }]">
                      <el-checkbox :label="tool.name">
                        <span class="tool-card-content">
                          <i class="el-icon-link"></i>
                          <span class="tool-label-text">{{ tool.label }}</span>
                        </span>
                      </el-checkbox>
                    </div>
                  </el-col>
                </el-row>
              </el-checkbox-group>
            </div>
          </el-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import {addAgent, delAgent, getAgent, getAvailableTools, listAgent, updateAgent} from "@/api/ai/agent";
import {listAvailableModel} from "@/api/ai/model";

export default {
  name: "AiAgentManager",
  data() {
    return {
      loading: true,
      total: 0,
      agentList: [],
      models: [],
      selectedTools: [],
      systemTools: [], // 动态扫描加载的系统工具列表
      viewMode: 'list', // list: 卡片列表模式, edit: 大屏参数配置工作台
      title: "",
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        agentCode: null,
        agentName: null,
        status: null
      },
      form: {},
      rules: {
        agentCode: [{ required: true, message: "智能体唯一编码不能为空", trigger: "blur" }],
        agentName: [{ required: true, message: "智能体名称不能为空", trigger: "blur" }],
        modelName: [{ required: true, message: "请选择底座大模型", trigger: "change" }],
        systemPrompt: [{ required: true, message: "智能体 System Prompt 不能为空", trigger: "blur" }]
      }
    };
  },
  created() {
    this.getList();
    this.loadModels();
    this.loadSystemTools();
  },
  methods: {
    async getList() {
      this.loading = true;
      try {
        const res = await listAgent(this.queryParams);
        if (res.code === 200) {
          this.agentList = res.data.rows || res.data || [];
          this.total = res.data.total || this.agentList.length;
        }
      } catch (err) {
        console.error(err);
      } finally {
        this.loading = false;
      }
    },
    async loadModels() {
      try {
        const res = await listAvailableModel();
        if (res.code === 200) {
          this.models = (res.data.rows || res.data || []).filter(m => m.isDefaultEmbedding !== '1' && !m.modelName.toLowerCase().includes('embed'));
        }
      } catch (err) {
        console.error(err);
      }
    },
    async loadSystemTools() {
      try {
        const res = await getAvailableTools();
        if (res.code === 200) {
          this.systemTools = res.data || [];
        }
      } catch (err) {
        console.error(err);
      }
    },
    async handleStatusChange(row) {
      const text = row.status === "1" ? "启用" : "禁用";
      try {
        const res = await updateAgent(row);
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
      this.reset();
    },
    reset() {
      this.form = {
        id: null,
        agentCode: null,
        agentName: null,
        modelName: null,
        systemPrompt: null,
        temperature: 0.2,
        tools: null,
        status: "1",
        remark: null
      };
      this.selectedTools = [];
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
        agentCode: null,
        agentName: null,
        status: null
      };
      this.handleQuery();
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
        const res = await getAgent(row.id);
        if (res.code === 200) {
          this.form = res.data;
          if (this.form.tools) {
            this.selectedTools = this.form.tools.split(",");
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
          this.form.tools = this.selectedTools.join(",");
          try {
            let res;
            if (this.form.id != null) {
              res = await updateAgent(this.form);
            } else {
              res = await addAgent(this.form);
            }
            if (res.code === 200) {
              this.$message.success("保存智能体成功");
              this.viewMode = 'list';
              this.getList();
            } else {
              this.$message.error(res.msg || "操作失败");
            }
          } catch (err) {
            console.error(err);
          }
        }
      });
    },
    handleDelete(row) {
      this.$confirm('是否确认删除名称为 "' + row.agentName + '" 的智能体配置？', "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }).then(async () => {
        const res = await delAgent(row.id);
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
.ai-agent-manager {
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
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border: none;
  box-shadow: 0 4px 10px rgba(5, 150, 105, 0.15);
  color: #fff;
}
.btn-gradient-success:hover {
  background: linear-gradient(135deg, #059669 0%, #047857 100%);
}

/* 卡片布局 */
.card-list-container {
  margin-top: 10px;
}
.card-col {
  margin-bottom: 24px;
}
.agent-card {
  background: rgba(255, 255, 255, 0.03) !important;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15) !important;
  transition: all 0.3s ease;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.85) !important;
}
.agent-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 25px rgba(139, 92, 246, 0.08);
  border-color: rgba(139, 92, 246, 0.3);
}
.agent-card.is-disabled {
  opacity: 0.5;
  background: rgba(255, 255, 255, 0.01) !important;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06) !important;
  background: transparent !important;
}
.agent-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 20px;
  box-shadow: 0 4px 8px rgba(109, 40, 217, 0.2);
}
.header-info {
  flex: 1;
  overflow: hidden;
}
.agent-title-text {
  font-size: 15px;
  font-weight: 700;
  color: #ffffff !important;
  margin: 0 0 4px;
}
.agent-code-tag {
  font-size: 11px;
  color: #94a3b8;
  font-family: Menlo, Monaco, Consolas, monospace;
}
.card-body {
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.param-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.param-label {
  color: #64748b;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}
.param-label i {
  color: #8b5cf6;
  font-size: 14px;
}
.tools-section {
  display: flex;
  flex-direction: column;
  margin-top: 2px;
}
.no-tools-text {
  font-size: 12px;
  color: #94a3b8;
  font-style: italic;
}
.tool-pills-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}
.tool-pill-badge {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 20px;
  padding: 3px 10px;
  font-size: 11px;
  color: #1e40af;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.tool-pill-badge i {
  font-size: 10px;
  color: #3b82f6;
}
.prompt-preview-card {
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
  border-radius: 8px;
  padding: 10px;
  margin-top: 4px;
}
.prompt-label-title {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
}
.prompt-content-text {
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
  margin: 4px 0 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
   智能体整屏大设计工作台
   ================================================================ */
.agent-workbench-container {
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
  border-color: #8b5cf6;
  color: #8b5cf6;
}
.workbench-title-text {
  font-size: 17px;
  font-weight: 800;
  color: #0f172a;
}

/* 双栏工作区 */
.workbench-body {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

/* 左侧：360px 宽度基础属性卡片 */
.editor-left-pane {
  width: 360px;
  flex-shrink: 0;
}
.pane-card {
  border-radius: 12px;
  border: 1px solid #edf2f7;
  box-shadow: 0 4px 15px rgba(0,0,0,0.01) !important;
}
.pane-card-header {
  font-size: 13.5px;
  font-weight: 700;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 6px;
}
.pane-card-header i {
  color: #8b5cf6;
  font-size: 15px;
}

/* 随机温度滑块美化 */
.temp-slider-box {
  padding: 4px 10px;
  background: #fafbfe;
  border: 1px solid #f1f5f9;
  border-radius: 8px;
}
.temp-slider-tips {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.4;
  margin-top: 6px;
}

/* 右侧：超大 Prompt 核心指令 + 装备工具画布 */
.editor-right-pane {
  flex: 1;
}

/* 提示词输入框重构为程序员代码极客样式 */
.textarea-code-style >>> .el-textarea__inner {
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
  font-size: 12.5px;
  background-color: #1e1e2e; /* 极客深色底 */
  color: #cdd6f4;
  border: 1px solid #313244;
  border-radius: 8px;
  padding: 14px 16px;
  line-height: 1.6;
}
.textarea-code-style >>> .el-textarea__inner:focus {
  border-color: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.15);
}

/* 大 Checkbox 选中卡片样式 (超级高级好看) */
.tool-checkbox-large-grid {
  padding: 4px 0;
}
.tool-check-col {
  margin-bottom: 16px;
}
.tool-checkbox-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 18px;
  background: #ffffff;
  transition: all 0.3s;
  cursor: pointer;
  display: flex;
  align-items: center;
}
.tool-checkbox-card:hover {
  border-color: #ddd6fe;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.05);
}
.tool-checkbox-card.is-checked {
  border-color: #8b5cf6;
  background: #fdfbfe;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.06);
}
.tool-card-content {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: 6px;
}
.tool-card-content i {
  font-size: 15px;
  color: #94a3b8;
  transition: color 0.3s;
}
.tool-checkbox-card.is-checked .tool-card-content i {
  color: #8b5cf6;
}
.tool-label-text {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}
.tool-checkbox-card.is-checked .tool-label-text {
  color: #6d28d9;
}

/* 按钮发光特效 */
.btn-primary-glow {
  background: linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%);
  border: none;
  box-shadow: 0 4px 10px rgba(109, 40, 217, 0.2);
}
.btn-primary-glow:hover {
  background: linear-gradient(135deg, #6d28d9 0%, #5b21b6 100%);
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  background: rgba(30, 41, 59, 0.45) !important;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.3);
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
  background: radial-gradient(circle, rgba(139, 92, 246, 0.06) 0%, transparent 70%);
  pointer-events: none;
}
.empty-icon {
  font-size: 56px;
  margin-bottom: 20px;
  display: inline-block;
  animation: float-icon 3s ease-in-out infinite;
}
.empty-state p {
  color: rgba(255, 255, 255, 0.6) !important;
  font-size: 14.5px;
  margin: 0;
  line-height: 1.6;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
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
</style>
