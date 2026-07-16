<template>
  <div class="app-container no-sidebar-manage-wrap">
    <div class="content-inner">
      <!-- 顶部高效筛选与操作栏 (完全恢复系统公共原装样式，绝不挨着) -->
      <div v-if="viewMode !== 'edit'" class="polaris-filter-card">
        <el-form :model="queryParams" ref="queryForm" :inline="true" class="polaris-filter-form">
          <el-form-item label="智能体编码">
            <el-input v-model="queryParams.agentCode" placeholder="请输入编码" clearable @keyup.enter="handleQuery" style="width: 180px;"/>
          </el-form-item>
          <el-form-item label="智能体名称">
            <el-input v-model="queryParams.agentName" placeholder="请输入名称" clearable @keyup.enter="handleQuery" style="width: 180px;"/>
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
          <!-- 中间共享操作行：新增智能体按钮与视图切换器 (独立于检索卡片，错落有致) -->
          <div class="matrix-actions-bar" style="margin-top: 0; width: 100%;">
            <div class="actions-left">
              <el-button type="primary" class="action-btn-primary" icon="Plus" @click="handleAdd">
                新增智能体
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
          <!-- 全局 SVG 渐变定义 -->
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

          <div v-loading="loading" class="synapse-card-grid-container">
            <div v-if="agentList.length === 0" class="empty-state">
              <div class="empty-icon">🧠</div>
              <p>暂无智能体配置，点击上方“新增智能体”按钮开始配置您的 AI 智能体吧！</p>
            </div>

            <div class="synapse-card-grid" v-else>
              <div
                v-for="item in agentList"
                :key="item.id"
                :class="['synapse-glass-card', item.status === '1' ? 'status-border-active' : 'status-border-error']"
              >
                <!-- 卡片高亮流光线 -->
                <div class="card-shimmer-ray"></div>
                
                <div class="card-header-row">
                  <span :class="['card-code', getAgentTagType(item.agentCode)]">{{ item.agentCode }}</span>
                  <!-- 雷达多层呼吸灯 -->
                  <div class="status-cell clickable-status" @click="toggleStatus(item)">
                    <span :class="['pulse-light-ripple', item.status === '1' ? 'pulse-active' : 'pulse-error']"></span>
                    <span class="status-badge-text" :class="item.status === '1' ? 'text-active' : 'text-error'">
                      {{ item.status === '1' ? '正常' : '禁用' }}
                    </span>
                  </div>
                </div>

                <div class="card-body">
                  <h4 class="card-name" :title="item.agentName">{{ item.agentName }}</h4>
                  <div class="card-model-chip-badge">
                    <el-icon class="model-cpu-icon"><cpu /></el-icon>
                    <span>{{ getModelLabel(item.modelConfigId, item.modelName) }}</span>
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
                            :stroke-dasharray="`${(item.temperature || 0) * 100}, 100`"
                            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          />
                        </svg>
                        <div class="radial-gauge-text">{{ item.temperature }}</div>
                      </div>
                      <span class="gauge-label">随机温度</span>
                    </div>
                    
                    <!-- 工具载荷 -->
                    <div class="radial-gauge-item">
                      <div class="radial-circle-box">
                        <svg viewBox="0 0 36 36">
                          <path
                            class="circle-bg"
                            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          />
                          <path
                            class="circle-fill"
                            :stroke="item.status === '1' ? 'url(#tool-gradient-active)' : 'url(#tool-gradient-error)'"
                            :stroke-dasharray="`${getToolLoad(item.tools)}, 100`"
                            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          />
                        </svg>
                        <div class="radial-gauge-text">{{ getToolCount(item.tools) }}个</div>
                      </div>
                      <span class="gauge-label">已装载工具</span>
                    </div>
                  </div>

                  <!-- 系统提示词预览 (Mac 极客代码终端风格) -->
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

                <!-- 卡片底部操作按钮 -->
                <div class="card-footer-actions">
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

          <!-- 三维星图视图下的分页组件 -->
          <pagination
            v-show="total > 0"
            :total="total"
            v-model:page="queryParams.pageNum"
            v-model:limit="queryParams.pageSize"
            @pagination="getList"
          />
        </div>

        <!-- 📊 经典数据表格视图 -->
        <div v-else-if="viewMode === 'table'" key="table-view" class="polaris-table-card">
          <!-- 中间共享操作行：新增智能体按钮与视图切换器 (独立于检索卡片，错落有致) -->
          <div class="matrix-actions-bar" style="margin-top: 0; width: 100%;">
            <div class="actions-left">
              <el-button type="primary" class="action-btn-primary" icon="Plus" @click="handleAdd">
                新增智能体
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
          <el-table v-loading="loading" :data="agentList" class="polaris-el-table">
            <el-table-column label="智能体编码" prop="agentCode" width="150" />
            <el-table-column label="智能体名称" prop="agentName" min-width="150" :show-overflow-tooltip="true" />
            <el-table-column label="搭载大模型" prop="modelName" width="180">
              <template #default="{ row }">
                <span class="model-badge">{{ getModelLabel(row.modelConfigId, row.modelName) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="随机温度" width="150">
              <template #default="{ row }">
                <div class="load-progress-wrapper">
                  <div class="load-progress-bar">
                    <div class="load-progress-fill color-mem" :style="{ width: ((row.temperature || 0) * 100) + '%' }"></div>
                  </div>
                  <span class="load-text">{{ row.temperature }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="已装载工具" width="150">
              <template #default="{ row }">
                <el-tooltip v-if="row.tools" :content="row.tools" placement="top">
                  <span class="tool-count-badge">{{ getToolCount(row.tools) }} 个工具</span>
                </el-tooltip>
                <span v-else class="no-tools-text">未绑定</span>
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
            <el-table-column label="最近修改" width="140">
              <template #default="{ row }">
                <span>{{ formatDate(row.updateTime || row.createTime) }}</span>
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

          <pagination
            v-show="total > 0"
            :total="total"
            v-model:page="queryParams.pageNum"
            v-model:limit="queryParams.pageSize"
            @pagination="getList"
          />
        </div>

        <!-- 2. 独立整屏智能体配置工作台 -->
        <div v-else-if="viewMode === 'edit'" key="edit-view" class="agent-workbench-container">
          <!-- 顶部控制 Header -->
          <div class="workbench-header">
            <div class="header-left">
              <el-button icon="Back" size="small" circle @click="cancel" class="back-btn"></el-button>
              <span class="workbench-title-text">{{ form.id ? '配置智能体核心参数' : '创建全新 AI 智能体' }}</span>
            </div>
            <div class="header-right">
              <el-button size="small" icon="Close" @click="cancel" class="cancel-action-btn">取消返回</el-button>
              <el-button type="primary" size="small" class="action-btn-primary" icon="CircleCheck" @click="submitForm">保存智能体</el-button>
            </div>
          </div>

          <!-- 双栏配置工作区 -->
          <div class="workbench-body">
            <!-- 左侧面板：基础参数 -->
            <div class="editor-left-pane">
              <el-card class="pane-card" shadow="never">
                <template #header>
                  <div class="pane-card-header">
                    <span><el-icon><info-filled /></el-icon> 智能体基础属性</span>
                  </div>
                </template>

                <el-form ref="form" :model="form" :rules="rules" label-position="top">
                  <el-form-item label="智能体名称" prop="agentName">
                    <el-input v-model="form.agentName" placeholder="如: 系统安全审计员" />
                  </el-form-item>

                  <el-form-item label="智能体唯一编码" prop="agentCode">
                    <el-input :disabled="!!form.id" v-model="form.agentCode" placeholder="如: sys_user_analyst" />
                  </el-form-item>

                  <el-form-item label="选用底座大模型" prop="modelConfigId">
                    <el-select v-model="form.modelConfigId" placeholder="请选择绑定的底座大模型" style="width: 100%;">
                      <el-option
                        v-for="item in models"
                        :key="item.id"
                        :label="item.name"
                        :value="item.id"
                      />
                    </el-select>
                  </el-form-item>

                  <!-- 随机温度滑块 -->
                  <el-form-item label="随机温度 (Temperature)">
                    <div class="temp-slider-box">
                      <el-slider
                        v-model="form.temperature"
                        :max="1"
                        :step="0.05"
                        show-input
                        input-size="small"
                      />
                      <div class="temp-slider-tips">温度越低，大模型输出越确定、越精准；温度越高则越有创造性。</div>
                    </div>
                  </el-form-item>

                  <el-form-item label="开启状态">
                    <el-radio-group v-model="form.status">
                      <el-radio value="1" label="1">正常启用</el-radio>
                      <el-radio value="0" label="0">停用禁用</el-radio>
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
                <template #header>
                  <div class="pane-card-header">
                    <span><el-icon><document /></el-icon> 大脑核心系统指令 (System Role Prompt)</span>
                  </div>
                </template>
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
                <template #header>
                  <div class="pane-card-header">
                    <span><el-icon><folder-opened /></el-icon> 绑定系统级扩展工具 (Dynamic Tools)</span>
                  </div>
                </template>

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
                              <el-icon><link-icon /></el-icon>
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
      </transition>
    </div>
  </div>
</template>

<script>
import {addAgent, delAgent, getAgent, getAvailableTools, listAgent, updateAgent} from "@/api/ai/agent";
import {listAvailableModel} from "@/api/ai/model";
import {Cpu, Delete, DocumentCopy, Edit, Link as LinkIcon} from '@element-plus/icons-vue'

export default {
  name: "AiAgentManager",
  components: {
    LinkIcon,
    Cpu,
    Edit,
    Delete,
    DocumentCopy
  },
  data() {
    return {
      loading: true,
      total: 0,
      agentList: [],
      models: [],
      selectedTools: [],
      systemTools: [], // 动态扫描加载的系统工具列表
      viewMode: 'card', // card: 三维星图视图, table: 经典表格视图, edit: 大屏参数配置工作台
      lastListViewMode: 'card', // 记录返回时的视图模式
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
        modelConfigId: [{ required: true, message: "请选择绑定的底座大模型", trigger: "change" }],
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
    toggleStatus(row) {
      row.status = row.status === "1" ? "0" : "1";
      this.handleStatusChange(row);
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
      this.viewMode = this.lastListViewMode || 'card';
      this.reset();
    },
    reset() {
      this.form = {
        id: null,
        agentCode: null,
        agentName: null,
        modelName: null,
        modelConfigId: null,
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
      this.lastListViewMode = this.viewMode;
      this.viewMode = 'edit';
      this.$nextTick(() => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      });
    },
    async handleUpdate(row) {
      this.reset();
      try {
        const res = await getAgent(row.id);
        if (res.code === 200 && res.data) {
          this.form = res.data;
          if (this.form.tools) {
            this.selectedTools = this.form.tools.split(",");
          } else {
            this.selectedTools = [];
          }
          this.lastListViewMode = this.viewMode;
          this.viewMode = 'edit';
          this.$nextTick(() => {
            window.scrollTo({ top: 0, behavior: 'smooth' });
          });
        } else {
          this.$message.error("未获取到智能体配置数据");
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
              this.viewMode = this.lastListViewMode || 'card';
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
    },
    getModelLabel(modelConfigId, fallbackName) {
      if (!modelConfigId) return fallbackName || '未配置';
      const found = this.models.find(m => m.id === modelConfigId);
      return found ? found.name : (fallbackName || '未配置');
    },
    getToolCount(tools) {
      if (!tools) return 0;
      return tools.split(',').filter(Boolean).length;
    },
    getToolLoad(tools) {
      const count = this.getToolCount(tools);
      return Math.min(count * 25, 100); // 4个工具即100%装载
    },
    getAgentTagType(code) {
      if (!code) return 'tag-slate';
      const c = code.toLowerCase();
      if (c.includes('file')) return 'tag-purple';
      if (c.includes('log')) return 'tag-amber';
      if (c.includes('net') || c.includes('url') || c.includes('api')) return 'tag-teal';
      if (c.includes('sys') || c.includes('user') || c.includes('admin')) return 'tag-rose';
      if (c.includes('intent') || c.includes('router') || c.includes('dist')) return 'tag-indigo';
      return 'tag-slate';
    },
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
};
</script>

<style lang="scss" scoped>
/* 过滤表单样式微调，清除多余 margin */
.polaris-filter-form {
  margin-bottom: 0 !important;
}

/* 聚合操作栏 (检索卡片正下方) */
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

.view-mode-fade-enter-active,
.view-mode-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.view-mode-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.view-mode-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* ===== 🧠 三维北辰星图卡片视图布局 ===== */
.synapse-card-grid-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.synapse-card-grid-container {
  width: 100%;
}

.synapse-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(290px, 1fr));
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

.card-shimmer-ray {
  position: absolute;
  top: 0;
  bottom: 0;
  left: -80px;
  width: 50px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.15), transparent);
  transform: skewX(-20deg) translateX(-100px);
  transition: transform 0.6s ease;
  pointer-events: none;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.status-badge-text {
  font-size: 11px;
  font-weight: bold;
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

.card-model-badge {
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: bold;
  color: #64748b;

  .dark &,
  .theme-dark & {
    color: #94a3b8;
  }
}

/* 环形仪表 */
.card-stats-gauges {
  display: flex;
  justify-content: space-around;
  margin: 8px 0;
}

.radial-gauge-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  transition: transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  
  .gauge-label {
    font-size: 10px;
    font-weight: bold;
    color: #64748b;

    .dark &,
    .theme-dark & {
      color: #94a3b8;
    }
  }
}

.radial-circle-box {
  width: 56px;
  height: 56px;
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
    transition: stroke-dasharray 0.3s ease;
    
    &.fill-active {
      stroke: #10b981;

      .dark &,
      .theme-dark & {
        stroke: #10b981;
        filter: drop-shadow(0 0 4px #10b981);
      }
    }
    
    &.fill-error {
      stroke: #ef4444;

      .dark &,
      .theme-dark & {
        stroke: #ef4444;
        filter: drop-shadow(0 0 4px #ef4444);
      }
    }

    &.fill-mem-purple {
      stroke: #8b5cf6;

      .dark &,
      .theme-dark & {
        stroke: #818cf8;
        filter: drop-shadow(0 0 4px #818cf8);
      }
    }
  }
}

.radial-gauge-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: monospace;
  font-size: 11px;
  font-weight: 800;
  color: #0f172a;

  .dark &,
  .theme-dark & {
    color: #cbd5e1;
  }
}

.prompt-preview-card {
  background: var(--el-fill-color-light);
  border: 1px dashed var(--el-border-color-light);
  border-radius: 8px;
  padding: 10px;
  max-height: 90px;
  overflow-y: auto;

  /* 微型精致滚动条 */
  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.1);
    border-radius: 4px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }

  .dark &,
  .theme-dark & {
    background: rgba(0, 0, 0, 0.15);
    border-color: rgba(255, 255, 255, 0.06);

    &::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.1);
    }
  }
}

.prompt-label-title {
  font-size: 10px;
  font-weight: 700;
  color: #64748b;

  .dark &,
  .theme-dark & {
    color: #94a3b8;
  }
}

.prompt-content-text {
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
  margin: 4px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.card-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  border-top: 1px dashed rgba(0, 0, 0, 0.05);
  padding-top: 12px;

  .dark &,
  .theme-dark & {
    border-top-color: rgba(255, 255, 255, 0.05);
  }
}

.card-op-edit {
  font-size: 11px;
  font-weight: bold;
  color: #4f46e5 !important;

  .dark &,
  .theme-dark & {
    color: #38bdf8 !important;
  }
}

.card-op-delete {
  font-size: 11px;
  font-weight: bold;
  color: #ef4444 !important;
}

/* ===== 经典数据表格内的精细元素 ===== */
.model-badge {
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: bold;
  padding: 2px 8px;
  border-radius: 6px;
  background-color: rgba(0, 0, 0, 0.03);
  color: #475569;

  .dark &,
  .theme-dark & {
    background-color: rgba(255, 255, 255, 0.04);
    color: #cbd5e1;
  }
}

.tool-count-badge {
  font-weight: bold;
  font-size: 11px;
  color: #8b5cf6;
  background: rgba(139, 92, 246, 0.06);
  border: 1px solid rgba(139, 92, 246, 0.15);
  border-radius: 20px;
  padding: 2px 8px;
}

.load-progress-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
}

.load-progress-bar {
  flex: 1;
  height: 6px;
  border-radius: 99px;
  overflow: hidden;
  position: relative;
  background-color: rgba(0, 0, 0, 0.04);

  .dark &,
  .theme-dark & {
    background-color: rgba(255, 255, 255, 0.04);
  }
}

.load-progress-fill {
  height: 100%;
  border-radius: 99px;
  background-size: 200% auto;
  animation: progressShimmerFlow 3s linear infinite;
  
  &.color-mem {
    background-image: linear-gradient(90deg, #60a5fa 0%, #3b82f6 50%, #60a5fa 100%);
    
    .dark &,
    .theme-dark & {
      background-image: linear-gradient(90deg, #8b5cf6 0%, #818cf8 50%, #8b5cf6 100%);
    }
  }
}

@keyframes progressShimmerFlow {
  0% { background-position: 0% 50%; }
  100% { background-position: 200% 50%; }
}

.load-text {
  font-family: monospace;
  font-size: 11px;
  font-weight: bold;
  width: 32px;
  text-align: right;
  color: #64748b;

  .dark &,
  .theme-dark & {
    color: #94a3b8;
  }
}

.no-tools-text {
  font-size: 12px;
  color: #94a3b8;
  font-style: italic;
}

.table-op-actions {
  display: flex;
  gap: 12px;
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
}

/* ===== 智能体配置整屏大屏工作台 ===== */
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

.back-btn {
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 5px rgba(0,0,0,0.02);
  transition: all 0.2s;

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

.workbench-body {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.editor-left-pane {
  width: 360px;
  flex-shrink: 0;
}

.pane-card {
  border-radius: 20px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(255, 255, 255, 0.55);
  box-shadow: 0 4px 15px rgba(0,0,0,0.01) !important;
  backdrop-filter: blur(20px);

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.3);
    border-color: rgba(255, 255, 255, 0.05);
  }
}

.pane-card-header {
  font-size: 13.5px;
  font-weight: 700;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 6px;

  el-icon {
    color: #8b5cf6;
    font-size: 15px;
  }

  .dark &,
  .theme-dark & {
    color: #cbd5e1;
  }
}

.temp-slider-box {
  padding: 10px 14px;
  background: rgba(0, 0, 0, 0.01);
  border: 1px solid rgba(0, 0, 0, 0.04);
  border-radius: 12px;

  .dark &,
  .theme-dark & {
    background: rgba(255, 255, 255, 0.01);
    border-color: rgba(255, 255, 255, 0.02);
  }
}

.temp-slider-tips {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.4;
  margin-top: 6px;
}

.editor-right-pane {
  flex: 1;
}

/* 极客指令框 */
.textarea-code-style :deep(.el-textarea__inner) {
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

.textarea-code-style :deep(.el-textarea__inner:focus) {
  border-color: #4f46e5 !important;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.15) !important;

  .dark &,
  .theme-dark & {
    border-color: #38bdf8 !important;
    box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.15) !important;
  }
}

/* 工具装备格 */
.tool-checkbox-large-grid {
  padding: 4px 0;
}

.tool-check-col {
  margin-bottom: 16px;
}

.tool-checkbox-card {
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  padding: 12px 16px;
  background: #ffffff;
  transition: all 0.3s;
  cursor: pointer;
  display: flex;
  align-items: center;

  .dark &,
  .theme-dark & {
    background: rgba(15, 23, 42, 0.15);
    border-color: rgba(255, 255, 255, 0.05);
  }

  &:hover {
    border-color: rgba(139, 92, 246, 0.3);
    box-shadow: 0 4px 12px rgba(139, 92, 246, 0.04);
  }

  &.is-checked {
    border-color: #8b5cf6;
    background: rgba(139, 92, 246, 0.02);
    box-shadow: 0 4px 12px rgba(139, 92, 246, 0.05);

    .dark &,
    .theme-dark & {
      border-color: #38bdf8;
      background: rgba(56, 189, 248, 0.02);
      box-shadow: 0 4px 12px rgba(56, 189, 248, 0.05);
    }
  }

  :deep(.el-checkbox) {
    margin-right: 0;
    width: 100%;
  }
}

.tool-card-content {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: 6px;
  
  el-icon {
    font-size: 15px;
    color: #94a3b8;
    transition: color 0.3s;
  }
}

.tool-checkbox-card.is-checked .tool-card-content el-icon {
  color: #8b5cf6;

  .dark &,
  .theme-dark & {
    color: #38bdf8;
  }
}

.tool-label-text {
  font-size: 13px;
  font-weight: 600;
  color: #475569;

  .dark &,
  .theme-dark & {
    color: #cbd5e1;
  }
}

.tool-checkbox-card.is-checked .tool-label-text {
  color: #8b5cf6;

  .dark &,
  .theme-dark & {
    color: #38bdf8;
  }
}

/* ===== 极简空状态 ===== */
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

/* ==========================================================================
   智能体管理卡片视觉与动效微调 (Modern Dashboard Enhancement)
   ========================================================================== */

/* 芯片化大模型徽章 */
.card-model-chip-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  background: rgba(0, 0, 0, 0.03);
  padding: 3px 8px;
  border-radius: 6px;
  border: 1px solid rgba(0, 0, 0, 0.04);
  transition: all 0.3s;

  .model-cpu-icon {
    font-size: 12px;
    color: #64748b;
  }

  .dark &,
  .theme-dark & {
    color: #cbd5e1;
    background: rgba(255, 255, 255, 0.03);
    border-color: rgba(255, 255, 255, 0.05);
    
    .model-cpu-icon {
      color: #94a3b8;
    }
  }
}

.synapse-glass-card:hover .card-model-chip-badge {
  background: rgba(139, 92, 246, 0.05);
  border-color: rgba(139, 92, 246, 0.15);
  color: #8b5cf6;

  .model-cpu-icon {
    color: #8b5cf6;
  }

  .dark &,
  .theme-dark & {
    background: rgba(56, 189, 248, 0.05);
    border-color: rgba(56, 189, 248, 0.15);
    color: #38bdf8;

    .model-cpu-icon {
      color: #38bdf8;
    }
  }
}

/* 动态智能体药丸型标签颜色映射 */
.card-code {
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

/* Mac 极客终端风格系统提示词展示区 */
.prompt-preview-terminal {
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 100px;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  
  /* 默认：亮色模式 */
  background: #f8fafc;
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.02), 0 4px 10px rgba(0, 0, 0, 0.02);

  &:hover {
    border-color: rgba(139, 92, 246, 0.3);
    box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.02), 0 6px 15px rgba(139, 92, 246, 0.08);
  }

  /* 深色模式覆盖 */
  .dark &,
  .theme-dark & {
    background: #0b0f19;
    border-color: rgba(255, 255, 255, 0.03);
    box-shadow: inset 0 2px 5px rgba(0, 0, 0, 0.4);

    &:hover {
      border-color: rgba(56, 189, 248, 0.22);
      box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.4), 0 6px 15px rgba(56, 189, 248, 0.08);
    }
  }
}

.terminal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 24px;
  padding: 0 10px;
  
  /* 默认：亮色模式 */
  background: rgba(0, 0, 0, 0.02);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);

  /* 深色模式覆盖 */
  .dark &,
  .theme-dark & {
    background: rgba(255, 255, 255, 0.03);
    border-bottom: 1px solid rgba(255, 255, 255, 0.02);
  }
}

.terminal-dots {
  display: flex;
  gap: 5px;
  align-items: center;

  span {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    display: inline-block;
  }

  .dot-red { background-color: #ff5f56; }
  .dot-yellow { background-color: #ffbd2e; }
  .dot-green { background-color: #27c93f; }
}

.terminal-title {
  font-family: monospace;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  
  /* 默认：亮色模式 */
  color: rgba(0, 0, 0, 0.35);

  /* 深色模式覆盖 */
  .dark &,
  .theme-dark & {
    color: rgba(255, 255, 255, 0.35);
  }
}

.terminal-copy-btn {
  background: transparent;
  border: none;
  padding: 0;
  margin: 0;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  transition: all 0.2s;
  
  /* 默认：亮色模式 */
  color: rgba(0, 0, 0, 0.4);

  &:hover {
    color: #8b5cf6;
    transform: scale(1.1);
  }

  /* 深色模式覆盖 */
  .dark &,
  .theme-dark & {
    color: rgba(255, 255, 255, 0.4);

    &:hover {
      color: #38bdf8;
      transform: scale(1.1);
    }
  }
}

.terminal-body {
  padding: 8px 12px;
  flex: 1;
  overflow-y: auto;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.1);
    border-radius: 4px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }

  .dark &,
  .theme-dark & {
    &::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.1);
    }
  }
}

.terminal-body .prompt-content-text {
  font-family: Menlo, Monaco, Consolas, "Fira Code", monospace;
  font-size: 11px;
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  
  /* 默认：亮色模式 */
  color: #334155;

  /* 深色模式覆盖 */
  .dark &,
  .theme-dark & {
    color: #cbd5e1;
  }
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

/* 悬浮时仪表盘微动效 */
.synapse-glass-card:hover {
  .radial-circle-box svg {
    filter: drop-shadow(0 0 3px rgba(139, 92, 246, 0.15));
  }
}

/* 页面内部容器 Flex 布局，使搜索栏与下方内容间距为 16px */
.content-inner {
  padding: 0 !important;
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

/* ===== 智能体配置工作台表单元素及滑块、单选框高新科技化美化 ===== */
.agent-workbench-container {
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
  :deep(.el-select__wrapper) {
    border-radius: 12px !important;
    background-color: #ffffff !important;
    border: 1px solid rgba(0, 0, 0, 0.08) !important;
    box-shadow: none !important;
    transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);

    &:hover {
      border-color: rgba(139, 92, 246, 0.3) !important;
    }

    &.is-focus {
      border-color: #8b5cf6 !important;
      box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.15) !important;
    }

    .dark &,
    .theme-dark & {
      background-color: rgba(0, 0, 0, 0.25) !important;
      border-color: rgba(255, 255, 255, 0.06) !important;

      &:hover {
        border-color: rgba(56, 189, 248, 0.3) !important;
      }

      &.is-focus {
        border-color: #38bdf8 !important;
        box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.15) !important;
      }
    }
  }

  :deep(.el-input__inner) {
    font-size: 12.5px;
    color: #0f172a;

    .dark &,
    .theme-dark & {
      color: #cbd5e1;
    }
  }

  /* 状态单选框美化 */
  :deep(.el-radio) {
    border-radius: 10px;
    padding: 8px 16px;
    border: 1px solid rgba(0, 0, 0, 0.06) !important;
    background: #ffffff;
    transition: all 0.3s;
    margin-right: 12px;
    height: auto;

    &.is-checked {
      border-color: #8b5cf6 !important;
      background: rgba(139, 92, 246, 0.02) !important;
      
      .el-radio__label {
        color: #8b5cf6 !important;
      }
      .el-radio__inner {
        border-color: #8b5cf6 !important;
        background: #8b5cf6 !important;
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
        }
        .el-radio__inner {
          border-color: #38bdf8 !important;
          background: #38bdf8 !important;
        }
      }
    }
  }

  /* 随机温度滑块美化 */
  :deep(.el-slider) {
    .el-slider__runway {
      background-color: rgba(0, 0, 0, 0.04) !important;
      height: 5px;

      .dark &,
      .theme-dark & {
        background-color: rgba(255, 255, 255, 0.04) !important;
      }
    }
    .el-slider__bar {
      height: 5px;
      background-color: #8b5cf6 !important;

      .dark &,
      .theme-dark & {
        background-color: #38bdf8 !important;
      }
    }
    .el-slider__button {
      border: 2px solid #8b5cf6 !important;
      background-color: #ffffff !important;
      width: 14px;
      height: 14px;
      box-shadow: 0 2px 6px rgba(139, 92, 246, 0.2);

      .dark &,
      .theme-dark & {
        border-color: #38bdf8 !important;
        background-color: #0f172a !important;
        box-shadow: 0 2px 6px rgba(56, 189, 248, 0.3);
      }
    }
    .el-input-number--small {
      width: 90px;
    }
  }
}
</style>
