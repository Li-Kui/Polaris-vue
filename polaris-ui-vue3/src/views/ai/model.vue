<template>
  <div class="app-container ai-model-manager">
    <!-- 顶部操作工具栏 -->
    <div class="filter-container">
      <el-form :inline="true" class="demo-form-inline">
        <el-form-item label="模型名称">
          <el-input v-model="queryParams.name" clearable placeholder="请输入配置名称" @keyup.enter="handleQuery"/>
        </el-form-item>
        <el-form-item label="提供商">
          <el-select v-model="queryParams.provider" clearable placeholder="请选择提供商">
            <el-option label="DeepSeek" value="deepseek"/>
            <el-option label="阿里云通义" value="dashscope"/>
            <el-option label="OpenAI" value="openai"/>
            <el-option label="Ollama (本地)" value="ollama"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button icon="Search" type="primary" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button class="btn-gradient-primary" icon="Plus" type="primary" @click="handleAdd">新增模型</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 卡片式模型列表 -->
    <div v-loading="loading" class="card-list-container">
      <div v-if="modelList.length === 0" class="empty-state">
        <div class="empty-icon">🤖</div>
        <p style="color: var(--el-text-color-primary) !important; font-weight: 600;">暂无模型配置，点击上方“新增模型”按钮开始配置您的 AI 模型吧！</p>
      </div>

      <el-row v-else :gutter="20">
        <el-col v-for="item in modelList" :key="item.id" :lg="8" :md="8" :sm="12" :xs="24" class="card-col">
          <div :class="{ 'is-active-chat': item.isDefault === '1', 'is-active-embedding': item.isDefaultEmbedding === '1' }" class="model-card">

            <!-- 卡片头部：提供商与基本名称 -->
            <div class="card-header">
              <div :class="'provider-' + item.provider" class="provider-avatar">
                <span v-if="item.provider === 'deepseek'">🌀</span>
                <span v-else-if="item.provider === 'dashscope'">✦</span>
                <span v-else-if="item.provider === 'openai'">⚛</span>
                <span v-else-if="item.provider === 'ollama'">🦙</span>
                <span v-else>🤖</span>
              </div>
              <div class="header-info">
                <h3 class="model-alias">{{ item.name }}</h3>
                <span :class="'provider-' + item.provider" class="provider-badge">{{ getProviderLabel(item.provider) }}</span>
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

            <!-- 卡片主体：主要配置参数 -->
            <div class="card-body">
              <div class="param-row">
                <span class="param-label"><el-icon><cpu /></el-icon> 模型名称</span>
                <span class="param-val code-text">{{ item.modelName }}</span>
              </div>
              <div class="param-row">
                <span class="param-label"><el-icon><key /></el-icon> API Key</span>
                <span class="param-val code-text mask-text">{{ item.apiKey ? '••••••••••••••••' : '未设置' }}</span>
              </div>
              <div v-if="item.baseUrl" class="param-row">
                <span class="param-label"><el-icon><link /></el-icon> API 地址</span>
                <span :title="item.baseUrl" class="param-val url-text">{{ item.baseUrl }}</span>
              </div>
              <div class="param-grid">
                <div class="grid-item">
                  <span class="grid-label">Max Tokens</span>
                  <span class="grid-val">{{ item.maxTokens || 2048 }}</span>
                </div>
                <div class="grid-item">
                  <span class="grid-label">温度</span>
                  <span class="grid-val">{{ item.temperature || 0.7 }}</span>
                </div>
                <div class="grid-item">
                  <span class="grid-label">历史消息数</span>
                  <span class="grid-val">{{ item.maxHistoryMessages || 20 }}</span>
                </div>
              </div>
              <div class="param-row">
                <span class="param-label"><el-icon><chat-line-round /></el-icon> 思考模式</span>
                <span class="param-val">
                  <el-tag v-if="item.enableThinking === '1'" effect="plain" size="small" type="warning">
                    开启 ({{ getReasoningEffortLabel(item.reasoningEffort) }})
                  </el-tag>
                  <el-tag v-else effect="plain" size="small" type="info">关闭</el-tag>
                </span>
              </div>
              <div class="param-row">
                <span class="param-label"><el-icon><connection /></el-icon> 联网搜索</span>
                <span class="param-val">
                  <el-tag v-if="item.enableSearch === '1'" effect="plain" size="small" type="success">开启</el-tag>
                  <el-tag v-else effect="plain" size="small" type="info">关闭</el-tag>
                </span>
              </div>
              <div class="param-row">
                <span class="param-label"><el-icon><office-building /></el-icon> 归属部门</span>
                <span class="param-val">
                  <el-tag v-if="item.deptId" effect="plain" size="small" type="primary">
                    {{ getDeptName(item.deptId) }}
                  </el-tag>
                  <el-tag v-else effect="plain" size="small" type="success">全局共享</el-tag>
                </span>
              </div>
              <div v-if="item.systemPrompt" class="prompt-preview">
                <span class="prompt-title">系统提示词:</span>
                <p :title="item.systemPrompt" class="prompt-content">{{ item.systemPrompt }}</p>
              </div>
            </div>

            <!-- 卡片页脚：默认控制与操作 -->
            <div class="card-footer">
              <div class="default-tags">
                <template v-if="!isEmbeddingModel(item.modelName)">
                  <el-tag v-if="item.isDefault === '1'" class="active-tag-chat" effect="dark" size="small" style="display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; vertical-align: middle;" type="success">
                    <el-icon style="margin: 0; display: inline-flex; align-items: center; justify-content: center; vertical-align: middle;"><chat-dot-round /></el-icon>
                    <span style="vertical-align: middle; line-height: 1;">默认聊天</span>
                  </el-tag>
                  <el-button v-else class="footer-action-btn" link size="small" @click="handleSetDefaultChat(item)">
                    设为默认聊天
                  </el-button>
                </template>

                <template v-if="isEmbeddingModel(item.modelName)">
                  <el-tag v-if="item.isDefaultEmbedding === '1'" class="active-tag-embed" effect="dark" size="small" style="display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; vertical-align: middle;" type="primary">
                    <el-icon style="margin: 0; display: inline-flex; align-items: center; justify-content: center; vertical-align: middle;"><collection /></el-icon>
                    <span style="vertical-align: middle; line-height: 1;">默认向量</span>
                  </el-tag>
                  <el-button v-else class="footer-action-btn color-primary" link size="small" @click="handleSetDefaultEmbedding(item)">
                    设为默认向量
                  </el-button>
                </template>
              </div>

              <div class="action-buttons">
                <el-button class="footer-icon-btn edit-btn" icon="Edit" link size="small" @click="handleUpdate(item)">编辑</el-button>
                <el-button class="footer-icon-btn delete-btn" icon="Delete" link size="small" @click="handleDelete(item)">删除</el-button>
              </div>
            </div>

          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 添加或修改模型配置对话框 -->
    <el-dialog :title="title" v-model="open" append-to-body class="ai-model-dialog" width="640px">
      <el-form ref="form" :model="form" :rules="rules" label-width="110px">
        <el-row>
          <el-col :span="24">
            <el-form-item label="配置名称" prop="name">
              <el-input v-model="form.name" placeholder="例如：DeepSeek官方对话、阿里云通用向量"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="提供商" prop="provider">
              <el-select v-model="form.provider" placeholder="请选择提供商" style="width: 100%;">
                <el-option label="DeepSeek" value="deepseek"/>
                <el-option label="阿里云通义" value="dashscope"/>
                <el-option label="OpenAI" value="openai"/>
                <el-option label="Ollama (本地部署)" value="ollama"/>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型名称" prop="modelName">
              <el-input v-model="form.modelName" placeholder="例如：deepseek-chat、text-embedding-v3"/>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="API Key" prop="apiKey">
              <el-input v-model="form.apiKey" placeholder="输入大模型 API Key（脱敏存储）" show-password/>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="API Base URL">
              <el-input v-model="form.baseUrl" placeholder="不填使用官方默认。Ollama 必须填写：http://localhost:11434"/>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="启用联网搜索" prop="enableSearch">
              <el-radio-group v-model="form.enableSearch">
                <el-radio label="1">是</el-radio>
                <el-radio label="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-slot:default v-if="form.enableSearch === '1'" :span="24">
            <el-form-item label="联网搜索 Key" prop="searchKey">
              <el-input v-model="form.searchKey" placeholder="输入 Tavily 等联网搜索的 API Key（非必填，填入以实现模型端专属联网搜索）" show-password/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Max Tokens" prop="maxTokens">
              <el-input-number v-model="form.maxTokens" :max="32768" :min="256" controls-position="right" style="width: 100%;"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="随机温度" prop="temperature">
              <el-input-number v-model="form.temperature" :max="2" :min="0" :step="0.1" controls-position="right" style="width: 100%;"/>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="最大历史数" prop="maxHistoryMessages">
              <el-input-number v-model="form.maxHistoryMessages" :max="100" :min="1" controls-position="right" style="width: 100%;"/>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="系统提示词">
              <el-input v-model="form.systemPrompt" :rows="3" placeholder="该模型专属系统提示词，设置后会覆盖全局兜底设置。" type="textarea"/>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="归属部门">
              <el-tree-select
                v-model="form.deptId"
                :data="deptOptions"
                :props="{ value: 'id', label: 'label', children: 'children' }"
                value-key="id"
                placeholder="请选择所属部门（留空表示全局共享模型）"
                clearable
                check-strictly
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="思考模式">
              <el-radio-group v-model="form.enableThinking">
                <el-radio label="1">开启</el-radio>
                <el-radio label="0">关闭</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-slot:default v-if="form.enableThinking === '1'" :span="12">
            <el-form-item label="思考强度">
              <el-select v-model="form.reasoningEffort" clearable placeholder="请选择思考强度" style="width: 100%;">
                <el-option label="默认" value=""/>
                <el-option label="低强度 (low)" value="low"/>
                <el-option label="中强度 (medium)" value="medium"/>
                <el-option label="高强度 (high)" value="high"/>
                <el-option label="极大强度 (max)" value="max"/>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认聊天">
              <el-radio-group v-model="form.isDefault">
                <el-radio label="1">是</el-radio>
                <el-radio label="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认向量">
              <el-radio-group v-model="form.isDefaultEmbedding">
                <el-radio label="1">是</el-radio>
                <el-radio label="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button class="btn-gradient-primary" type="primary" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {addModel, delModel, getModel, listModel, setDefaultChat, setDefaultEmbedding, updateModel} from '@/api/ai/model'
import {deptTreeSelect} from '@/api/system/user'

export default {
  name: 'AiModelConfig',
  data() {
    return {
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
      // 查询参数
      queryParams: {
        name: undefined,
        provider: undefined
      },
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
          { required: true, message: '模型名称不能为空', trigger: 'blur' }
        ]
      }
    }
  },
  created() {
    this.getList()
    this.getDeptTree()
  },
  methods: {
    isEmbeddingModel(modelName) {
      if (!modelName) return false
      return modelName.toLowerCase().includes('embed')
    },
    /** 查询模型配置列表 */
    getList() {
      this.loading = true
      listModel(this.queryParams).then(response => {
        this.modelList = response.data.rows
        this.loading = false
      })
    },
    // 提供商翻译成可读Label
    getProviderLabel(provider) {
      const map = {
        deepseek: 'DeepSeek',
        dashscope: '阿里云通义',
        openai: 'OpenAI',
        ollama: 'Ollama'
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
        this.deptOptions = response.data
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
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        id: undefined,
        name: undefined,
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
        isDefault: '0',
        isDefaultEmbedding: '0',
        status: '1'
      }
      this.resetForm('form')
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.queryParams = {
        name: undefined,
        provider: undefined
      }
      this.handleQuery()
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '添加 AI 模型配置'
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      getModel(row.id).then(response => {
        this.form = response.data
        this.open = true
        this.title = '修改 AI 模型配置'
      })
    },
    /** 提交表单 */
    submitForm() {
      this.$refs['form'].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateModel(this.form).then(() => {
              this.$modal.msgSuccess('修改成功')
              this.open = false
              this.getList()
            })
          } else {
            addModel(this.form).then(() => {
              this.$modal.msgSuccess('新增成功')
              this.open = false
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
    }
  }
}
</script>

<style scoped>
.ai-model-manager {
  background: transparent !important;
  min-height: calc(100vh - 84px);
  padding: 24px;
}

/* 顶部搜索栏 */
.filter-container {
  background: rgba(255, 255, 255, 0.02) !important;
  backdrop-filter: blur(20px) !important;
  -webkit-backdrop-filter: blur(20px) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
  border-radius: 12px;
  padding: 18px 24px 4px 24px;
  margin-bottom: 20px;
  box-shadow: none !important;
}

.btn-gradient-success {
  background: linear-gradient(135deg, var(--el-color-success) 0%, var(--el-color-success-dark-2) 100%);
  border: none;
  font-weight: 500;
  transition: all 0.3s;
}
.btn-gradient-success:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

/* 空状态 (科技感暗色毛玻璃风格) */
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

/* 卡片布局 */
.card-list-container {
  margin-top: 10px;
}
.card-col {
  margin-bottom: 24px;
}

.model-card {
  background: rgba(255, 255, 255, 0.03) !important;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  box-shadow: 0 4px 25px rgba(0, 0, 0, 0.15) !important;
  display: flex;
  flex-direction: column;
  height: 430px;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  overflow: hidden;
  position: relative;
  color: rgba(255, 255, 255, 0.85) !important;
}
.model-card:hover {
  transform: translateY(-4px);
  border-color: rgba(99, 102, 241, 0.35) !important;
  box-shadow: 0 12px 30px rgba(99, 102, 241, 0.1) !important;
}

/* 选中模型亮色边框 */
.model-card.is-active-chat {
  border: 2px solid var(--el-color-success);
}
.model-card.is-active-embedding {
  border: 2px solid var(--el-color-primary);
}
.model-card.is-active-chat.is-active-embedding {
  border: 2px solid;
  border-image: linear-gradient(135deg, var(--el-color-success) 0%, var(--el-color-primary) 100%) 1;
}

/* 卡片头部 */
.card-header {
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06) !important;
  display: flex;
  align-items: center;
  position: relative;
  background: transparent !important;
}

.provider-avatar {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  margin-right: 12px;
}
.provider-avatar.provider-deepseek {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}
.provider-avatar.provider-dashscope {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}
.provider-avatar.provider-openai {
  background: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
}
.provider-avatar.provider-ollama {
  background: rgba(74, 85, 104, 0.1);
  color: #4a5568;
}

.header-info {
  flex-grow: 1;
  overflow: hidden;
}
.model-alias {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary) !important;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}

.provider-badge {
  display: inline-block;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 6px;
  font-weight: 600;
}
.provider-badge.provider-deepseek {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}
.provider-badge.provider-dashscope {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}
.provider-badge.provider-openai {
  background: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
}
.provider-badge.provider-ollama {
  background: rgba(74, 85, 104, 0.1);
  color: #4a5568;
}

/* 卡片主体 */
.card-body {
  padding: 16px 20px;
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}

.param-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.param-label {
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}
.param-val {
  color: var(--el-text-color-regular);
  font-weight: 500;
}
.code-text {
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  border: 1px solid var(--el-border-color-light);
  white-space: nowrap;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.url-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
}

/* 参数网格 */
.param-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 8px 12px;
  gap: 8px;
  border: 1px solid var(--el-border-color-light);
  text-align: center;
}
.grid-item {
  display: flex;
  flex-direction: column;
}
.grid-label {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  margin-bottom: 2px;
}
.grid-val {
  font-size: 12px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

/* 提示词预览 */
.prompt-preview {
  background: #fffaf0;
  border: 1px solid #feebc8;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12px;
}
.prompt-title {
  color: #dd6b20;
  font-weight: 600;
  margin-bottom: 2px;
  display: block;
}
.prompt-content {
  color: #7b341e;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

/* 卡片脚部 */
.card-footer {
  padding: 12px 20px;
  background: #fafbfe;
  border-top: 1px solid #edf2f7;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.default-tags {
  display: flex;
  gap: 6px;
  align-items: center;
}
.footer-action-btn {
  font-size: 11px;
  font-weight: 600;
  color: #10b981;
  padding: 0;
}
.footer-action-btn:hover {
  text-decoration: underline;
}
.footer-action-btn.color-primary {
  color: var(--el-color-primary);
}

.active-tag-chat {
  font-weight: 600;
}
.active-tag-embed {
  font-weight: 600;
}

.action-buttons {
  display: flex;
  gap: 8px;
}
.footer-icon-btn {
  font-size: 12px;
  font-weight: 600;
}
.footer-icon-btn.edit-btn {
  color: #4a5568;
}
.footer-icon-btn.edit-btn:hover {
  color: #1a202c;
}
.footer-icon-btn.delete-btn {
  color: #e53e3e;
}
.footer-icon-btn.delete-btn:hover {
  color: #c53030;
}

/* 渐变确定按钮 */
.btn-gradient-primary {
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-dark-2) 100%);
  border: none;
  color: white;
}
.btn-gradient-primary:hover {
  opacity: 0.9;
}
</style>
