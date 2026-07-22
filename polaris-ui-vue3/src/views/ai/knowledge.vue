<template>
  <div class="knowledge-wrapper">
    <!-- 左侧知识库卡片栏 -->
    <aside class="kb-sidebar">
      <div class="kb-sidebar-header">
        <h3 class="kb-title">知识库列表</h3>
        <el-button
          id="btn-add-kb"
          class="action-btn-primary"
          icon="Plus"
          type="primary"
          @click="handleCreateKb"
        >
          新建知识库
        </el-button>
      </div>

      <div class="kb-search-box">
        <el-input
          v-model="queryParams.name"
          clearable
          placeholder="搜索知识库名称..."
          prefix-icon="Search"
          @input="handleQuery"
        />
      </div>

      <!-- 知识库列表容器 -->
      <div v-loading="loadingKb" class="kb-list-container">
        <div v-if="kbList.length === 0" class="empty-state">
          <el-icon class="empty-icon"><folder-opened /></el-icon>
          <p style="color: var(--el-text-color-primary) !important; font-weight: 600;">暂无知识库</p>
        </div>

        <transition-group name="fade-list" tag="div">
          <div
            v-for="kb in kbList"
            :key="kb.id"
            :class="{ active: currentKbId === kb.id }"
            class="kb-card"
            @click="handleSelectKb(kb)"
          >
            <div class="kb-card-info">
              <span class="kb-card-name">{{ kb.name }}</span>
              <p class="kb-card-desc">{{ kb.description || '暂无描述' }}</p>
            </div>
            <div class="kb-card-actions" @click.stop>
              <el-button
                link
                icon="Edit"
                @click="handleUpdateKb(kb)"
              />
              <el-button
                link
                type="danger"
                icon="Delete"
                @click="handleDeleteKb(kb)"
              />
            </div>
          </div>
        </transition-group>
      </div>
    </aside>

    <!-- 右侧文档展示与解析区 -->
    <main class="kb-main">
      <div v-if="!currentKbId" class="select-prompt">
        <div class="glow-sphere"></div>
        <el-icon class="select-prompt-icon"><collection /></el-icon>
        <h2>探索与配置您的专属 AI 知识库</h2>
        <p>从左侧列表中选择一个知识库，或者创建一个新的知识库来导入和向量化您的业务文件。</p>
      </div>

      <div class="kb-content" v-else>
        <!-- 头部知识库描述 -->
        <header class="kb-content-header">
          <div class="kb-detail-title">
            <h2>{{ currentKb.name }}</h2>
            <el-tag class="kb-tag" type="info">向量知识库</el-tag>
          </div>
          <p class="kb-detail-desc">{{ currentKb.description || '这个知识库还没有填写描述。' }}</p>
        </header>

        <!-- 拖拽上传区 -->
        <section class="kb-upload-section">
          <el-upload
            :action="uploadUrl"
            :before-upload="beforeUpload"
            :data="uploadData"
            :headers="uploadHeaders"
            :on-error="handleUploadError"
            :on-success="handleUploadSuccess"
            :show-file-list="false"
            class="kb-uploader"
            drag
            multiple
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              将业务文件拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 PDF、Word (.docx)、Excel (.xlsx/.xls)、TXT、MD 等常用纯文本格式，文件大小不超过 20MB。
              </div>
            </template>
          </el-upload>
        </section>

        <!-- 文档列表区 -->
        <section class="kb-docs-section polaris-table-card">
          <div class="docs-header">
            <h4>包含文档 ({{ docList.length }})</h4>
            <el-button
              class="btn-refresh"
              icon="Refresh"
              link
              @click="getDocList"
            >
              刷新状态
            </el-button>
          </div>

          <el-table
            v-loading="loadingDoc"
            :data="docList"
            class="docs-table polaris-el-table"
            empty-text="此知识库下暂无文档，请在上方上传文件"
            style="width: 100%"
          >
            <el-table-column label="文档名称" min-width="220" prop="name" show-overflow-tooltip>
              <template #default="scope">
                <div class="doc-name-cell">
                  <el-icon class="doc-type-icon">
                    <component :is="getFileIcon(scope.row.name)" />
                  </el-icon>
                  <span>{{ scope.row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="字数" prop="wordCount" width="120">
              <template #default="scope">
                {{ formatWordCount(scope.row.wordCount) }}
              </template>
            </el-table-column>
            <el-table-column label="上传时间" prop="createTime" width="160" />
            <el-table-column label="解析状态" prop="status" width="140">
              <template #default="scope">
                <el-tag :type="getStatusTag(scope.row.status)" class="status-tag">
                  <el-icon v-if="scope.row.status === '1'" class="is-loading"><loading /></el-icon>
                  {{ getStatusText(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column align="center" label="操作" width="180">
              <template #default="scope">
                <el-button
                  link
                  class="op-btn-edit"
                  icon="RefreshLeft"
                  @click="handleRebuildDoc(scope.row)"
                >重新生成</el-button>
                <el-button
                  link
                  class="op-btn-delete"
                  icon="Delete"
                  @click="handleDeleteDoc(scope.row)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </main>

    <!-- 知识库表单弹窗 -->
    <el-dialog
      :title="dialogTitle"
      v-model="openDialog"
      append-to-body
      class="polaris-glass-dialog"
      width="500px"
    >
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item prop="name">
          <template #label>
            <span class="form-label-item">
              <el-icon><folder /></el-icon>
              <span>库名称</span>
            </span>
          </template>
          <el-input v-model="form.name" maxlength="50" placeholder="例如：北辰大模型业务文档库" show-word-limit />
        </el-form-item>
        <el-form-item prop="description">
          <template #label>
            <span class="form-label-item">
              <el-icon><document /></el-icon>
              <span>库描述</span>
            </span>
          </template>
          <el-input
            v-model="form.description"
            maxlength="200"
            placeholder="请输入知识库的业务背景或用途，便于后续大模型做精准语义检索匹配..."
            rows="4"
            show-word-limit
            type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="action-btn-secondary" @click="openDialog = false">取 消</el-button>
          <el-button class="action-btn-primary" type="primary" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {
  addKnowledge,
  delDocument,
  delKnowledge,
  listDocuments,
  listKnowledge,
  rebuildDocument,
  updateKnowledge
} from "@/api/ai/knowledge";
import {getToken} from "@/utils/auth";

export default {
  name: "AiKnowledge",
  data() {
    return {
      // 知识库加载状态
      loadingKb: false,
      // 文档加载状态
      loadingDoc: false,
      // 知识库数据列表
      kbList: [],
      // 当前选中的知识库ID
      currentKbId: null,
      // 当前选中的知识库详情
      currentKb: {},
      // 当前知识库的文档列表
      docList: [],
      // 列表查询参数
      queryParams: {
        name: undefined
      },
      // 弹窗属性
      openDialog: false,
      dialogTitle: "",
      form: {
        id: undefined,
        name: "",
        description: ""
      },
      // 表单规则
      rules: {
        name: [
          { required: true, message: "知识库名称不能为空", trigger: "blur" },
          { min: 2, max: 40, message: "长度在 2 到 40 个字符之间", trigger: "blur" }
        ]
      },
      // 上传配置
      uploadUrl: import.meta.env.VITE_APP_BASE_API + "/ai/knowledge/document/upload",
      uploadHeaders: {
        Authorization: "Bearer " + getToken()
      },
      // 自动刷新的定时器
      timer: null
    };
  },
  computed: {
    uploadData() {
      return {
        knowledgeBaseId: this.currentKbId
      };
    }
  },
  created() {
    this.getKbList();
  },
  beforeUnmount() {
    this.stopStatusPolling();
  },
  methods: {
    // 获取知识库列表
    getKbList() {
      this.loadingKb = true;
      listKnowledge(this.queryParams).then(response => {
        this.kbList = response.data.rows || [];
        this.loadingKb = false;
        // 默认选中第一个知识库
        if (this.kbList.length > 0 && !this.currentKbId) {
          this.handleSelectKb(this.kbList[0]);
        }
      }).catch(() => {
        this.loadingKb = false;
      });
    },
    // 过滤知识库
    handleQuery() {
      this.getKbList();
    },
    // 选中知识库事件
    handleSelectKb(kb) {
      this.currentKbId = kb.id;
      this.currentKb = kb;
      this.getDocList();
    },
    // 获取文档明细列表
    getDocList() {
      if (!this.currentKbId) return;
      this.loadingDoc = true;
      listDocuments({ knowledgeBaseId: this.currentKbId }).then(response => {
        this.docList = response.data.rows || [];
        this.loadingDoc = false;
        // 如果列表中包含处于"解析中(1)"状态的文档，则启动定时轮询刷新状态
        const hasParsing = this.docList.some(doc => doc.status === "1" || doc.status === "0");
        if (hasParsing) {
          this.startStatusPolling();
        } else {
          this.stopStatusPolling();
        }
      }).catch(() => {
        this.loadingDoc = false;
      });
    },
    // 状态轮询逻辑
    startStatusPolling() {
      if (this.timer) return;
      this.timer = setInterval(() => {
        if (!this.currentKbId) return;
        listDocuments({ knowledgeBaseId: this.currentKbId }).then(response => {
          this.docList = response.data.rows || [];
          const stillParsing = this.docList.some(doc => doc.status === "1" || doc.status === "0");
          if (!stillParsing) {
            this.stopStatusPolling();
          }
        });
      }, 5000); // 每5秒自动同步一次状态
    },
    stopStatusPolling() {
      if (this.timer) {
        clearInterval(this.timer);
        this.timer = null;
      }
    },
    // 新建知识库
    handleCreateKb() {
      this.resetForm();
      this.dialogTitle = "新建知识库";
      this.openDialog = true;
    },
    // 编辑知识库
    handleUpdateKb(kb) {
      this.resetForm();
      this.form = {
        id: kb.id,
        name: kb.name,
        description: kb.description
      };
      this.dialogTitle = "修改知识库";
      this.openDialog = true;
    },
    // 提交表单
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id !== undefined) {
            updateKnowledge(this.form).then(() => {
              this.$modal.msgSuccess("修改成功");
              this.openDialog = false;
              this.getKbList();
            });
          } else {
            addKnowledge(this.form).then(() => {
              this.$modal.msgSuccess("创建成功");
              this.openDialog = false;
              this.getKbList();
            });
          }
        }
      });
    },
    // 删除知识库
    handleDeleteKb(kb) {
      this.$modal.confirm(`确认要删除知识库 "${kb.name}" 吗？这会级联清除此知识库下所有的文档及向量数据！`).then(() => {
        return delKnowledge(kb.id);
      }).then(() => {
        this.$modal.msgSuccess("删除成功");
        if (this.currentKbId === kb.id) {
          this.currentKbId = null;
          this.currentKb = {};
          this.docList = [];
        }
        this.getKbList();
      }).catch(() => {});
    },
    // 文件上传前校验
    beforeUpload(file) {
      const isLt20M = file.size / 1024 / 1024 < 20;
      if (!isLt20M) {
        this.$modal.msgError("上传文件大小不能超过 20MB!");
        return false;
      }
      this.$modal.loading("文件正在上传并解析中，请稍候...");
      return true;
    },
    // 文件上传成功
    handleUploadSuccess(response) {
      this.$modal.closeLoading();
      if (response.code === 200) {
        this.$modal.msgSuccess("文件上传成功，已加入后台切片向量化任务");
        this.getDocList();
      } else {
        this.$modal.msgError(response.msg || "文件解析服务异常");
      }
    },
    // 文件上传失败
    handleUploadError() {
      this.$modal.closeLoading();
      this.$modal.msgError("网络连接错误，文件上传失败");
    },
    // 删除单个文档
    handleDeleteDoc(doc) {
      this.$modal.confirm(`确认删除文档 "${doc.name}" 吗？这会从向量数据库中永久移除该文档对应的向量切片。`).then(() => {
        return delDocument(doc.id);
      }).then(() => {
        this.$modal.msgSuccess("删除成功");
        this.getDocList();
      }).catch(() => {});
    },
    // 重构单个文档向量索引
    handleRebuildDoc(doc) {
      rebuildDocument(doc.id).then(() => {
        this.$modal.msgSuccess("已触发重新解析，请耐心等待状态同步");
        this.getDocList();
      });
    },
    // 重置表单
    resetForm() {
      this.form = {
        id: undefined,
        name: "",
        description: ""
      };
      if (this.$refs["form"]) {
        this.$refs["form"].resetFields();
      }
    },
    // 辅助工具方法
    getFileIcon(filename) {
      if (!filename) return "Document";
      const ext = filename.split(".").pop().toLowerCase();
      switch (ext) {
        case "pdf": return "DocumentChecked";
        case "xlsx":
        case "xls": return "DataAnalysis";
        case "docx":
        case "doc": return "DocumentCopy";
        case "txt":
        case "md": return "Document";
        default: return "Document";
      }
    },
    formatWordCount(count) {
      if (!count) return "0 字";
      return count.toLocaleString() + " 字";
    },
    getStatusTag(status) {
      switch (status) {
        case "0": return "info";
        case "1": return "warning";
        case "2": return "success";
        case "3": return "danger";
        default: return "info";
      }
    },
    getStatusText(status) {
      switch (status) {
        case "0": return "待解析";
        case "1": return "解析向量化中";
        case "2": return "就绪 (已向量化)";
        case "3": return "解析失败";
        default: return "未知";
      }
    }
  }
};
</script>

<style lang="scss" scoped>
/* 骨架与现代感布局 */
.knowledge-wrapper {
  display: flex;
  height: calc(100vh - 84px); /* 减去北辰顶部 navbar 与 tagsView 高度 */
  background: var(--polaris-bg, #f1f5f9);
  font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  transition: background-color 0.5s ease;
}

/* 左侧知识库栏 */
.kb-sidebar {
  width: 320px;
  background: rgba(255, 255, 255, 0.45);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.01);
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);

  .dark & {
    background: rgba(10, 15, 30, 0.45);
    border-right-color: rgba(255, 255, 255, 0.04);
    box-shadow: 2px 0 15px rgba(0, 0, 0, 0.15);
  }
}

.kb-sidebar-header {
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);

  .dark & {
    border-bottom-color: rgba(255, 255, 255, 0.04);
  }
}

.kb-title {
  margin: 0;
  font-size: 15px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  letter-spacing: 0.05em;
}

.kb-search-box {
  padding: 12px 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);

  .dark & {
    border-bottom-color: rgba(255, 255, 255, 0.04);
  }

  :deep(.el-input__wrapper) {
    border-radius: 10px !important;
    background-color: #ffffff !important;
    border: 1px solid rgba(0, 0, 0, 0.08) !important;
    box-shadow: none !important;
    transition: all 0.3s;

    &:hover,
    &.is-focus {
      border-color: rgba(79, 70, 229, 0.4) !important;
    }

    .dark & {
      background-color: rgba(0, 0, 0, 0.35) !important;
      border-color: rgba(255, 255, 255, 0.08) !important;

      &:hover,
      &.is-focus {
        border-color: rgba(56, 189, 248, 0.4) !important;
      }
    }
  }
}

.kb-list-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;

  /* 微型精致滚动条 */
  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.08);
    border-radius: 4px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }

  .dark & {
    &::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.08);
    }
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 60px;
  color: var(--el-text-color-secondary);
}

.empty-icon {
  font-size: 36px;
  margin-bottom: 10px;
  color: var(--el-text-color-placeholder);
}

/* 知识库卡片微动效与高光 */
.kb-card {
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 14px;
  padding: 14px 16px;
  margin-bottom: 12px;
  cursor: pointer;
  position: relative;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;

  .dark & {
    background: rgba(15, 23, 42, 0.2);
    border-color: rgba(255, 255, 255, 0.04);
  }

  &:hover {
    transform: translateY(-2px);
    background: #ffffff;
    border-color: #4f46e5;
    box-shadow: 0 8px 20px -4px rgba(79, 70, 229, 0.1);

    .dark & {
      background: rgba(15, 23, 42, 0.4);
      border-color: #38bdf8;
      box-shadow: 0 8px 24px -4px rgba(56, 189, 248, 0.15);
    }
  }

  &.active {
    background: rgba(79, 70, 229, 0.05);
    border-color: #4f46e5;
    box-shadow: 0 4px 16px -2px rgba(79, 70, 229, 0.12);

    .dark & {
      background: rgba(56, 189, 248, 0.06);
      border-color: #38bdf8;
      box-shadow: 0 4px 20px -2px rgba(56, 189, 248, 0.2);
    }
  }
}

.kb-card-info {
  flex: 1;
  min-width: 0;
}

.kb-card-name {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  display: block;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-card-desc {
  margin: 0;
  font-size: 11.5px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 卡片操作悬浮 */
.kb-card-actions {
  display: flex;
  margin-left: 10px;
  opacity: 0;
  transition: opacity 0.2s ease;

  :deep(.el-button) {
    padding: 4px !important;
    font-size: 14px;
    height: auto;
    color: var(--el-text-color-secondary);
    
    &:hover {
      color: #4f46e5;
      
      .dark & {
        color: #38bdf8;
      }
    }
    
    &.el-button--danger:hover {
      color: #ef4444;
    }
  }
}

.kb-card:hover .kb-card-actions,
.kb-card.active .kb-card-actions {
  opacity: 1;
}

/* 右侧内容主区 */
.kb-main {
  flex: 1;
  background: transparent;
  overflow-y: auto;
  position: relative;
  display: flex;
  flex-direction: column;
}

/* 提示选择知识库的精美界面 */
.select-prompt {
  margin: auto;
  text-align: center;
  max-width: 480px;
  padding: 40px;
  position: relative;
  z-index: 1;
}

.select-prompt-icon {
  font-size: 64px;
  background: linear-gradient(135deg, #4f46e5 0%, #818cf8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: 24px;
  filter: drop-shadow(0 4px 12px rgba(79, 70, 229, 0.15));

  .dark & {
    background: linear-gradient(135deg, #38bdf8 0%, #818cf8 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    filter: drop-shadow(0 0 15px rgba(56, 189, 248, 0.2));
  }
}

.select-prompt h2 {
  font-size: 20px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  margin: 0 0 12px 0;
  letter-spacing: 0.05em;
}

.select-prompt p {
  font-size: 13.5px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.glow-sphere {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(79, 70, 229, 0.06) 0%, rgba(255, 255, 255, 0) 70%);
  z-index: -1;
  filter: blur(25px);

  .dark & {
    background: radial-gradient(circle, rgba(56, 289, 248, 0.06) 0%, rgba(255, 255, 255, 0) 70%);
  }
}

/* 知识库文档展示主区 */
.kb-content {
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.kb-content-header {
  margin-bottom: 4px;
}

.kb-detail-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.kb-detail-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.kb-tag {
  background-color: rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.05);
  color: var(--el-text-color-regular);
  border-radius: 6px;
  font-weight: bold;
  height: 24px;
  padding: 0 8px;

  .dark & {
    background-color: rgba(255, 255, 255, 0.04);
    border-color: rgba(255, 255, 255, 0.06);
  }
}

.kb-detail-desc {
  margin: 0;
  font-size: 13.5px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

/* 上传板块设计 */
.kb-upload-section {
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 22px;
  padding: 24px;
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.5);
  transition: all 0.3s;

  .dark & {
    background: rgba(15, 23, 42, 0.45);
    border-color: rgba(255, 255, 255, 0.06);
    box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  }
}

.kb-uploader {
  width: 100%;
}

:deep(.el-upload-dragger) {
  width: 100% !important;
  height: 140px;
  background: rgba(0, 0, 0, 0.01);
  border: 1.5px dashed rgba(0, 0, 0, 0.1) !important;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  padding: 0 20px;

  .dark & {
    background: rgba(255, 255, 255, 0.01);
    border-color: rgba(255, 255, 255, 0.08) !important;
  }

  &:hover {
    border-color: #4f46e5 !important;
    background: rgba(79, 70, 229, 0.02) !important;

    .dark & {
      border-color: #38bdf8 !important;
      background: rgba(56, 189, 248, 0.03) !important;
    }
  }

  .el-icon--upload {
    font-size: 40px;
    color: var(--el-text-color-placeholder);
    margin-bottom: 8px;
    transition: color 0.3s;
  }

  &:hover .el-icon--upload {
    color: #4f46e5;
    .dark & { color: #38bdf8; }
  }

  .el-upload__text {
    font-size: 13px;
    color: var(--el-text-color-regular);
    font-weight: 600;

    em {
      color: #4f46e5;
      font-style: normal;
      font-weight: 700;
      .dark & { color: #38bdf8; }
    }
  }
}

:deep(.el-upload__tip) {
  font-size: 11.5px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
  text-align: center;
}

/* 文档列表区 */
.kb-docs-section {
  /* 基础继承自 .polaris-table-card */
  transition: all 0.3s;
}

.docs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  width: 100%;
}

.docs-header h4 {
  margin: 0;
  font-size: 14.5px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.btn-refresh {
  font-size: 12px;
  font-weight: 700;
  color: #4f46e5 !important;
  transition: all 0.2s;

  &:hover {
    color: #4338ca !important;
    transform: scale(1.02);
  }

  .dark & {
    color: #38bdf8 !important;
    &:hover { color: #7dd3fc !important; }
  }
}

.doc-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doc-type-icon {
  font-size: 18px;
  color: var(--el-text-color-secondary);
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  font-size: 11px;
  border-radius: 6px;
  padding: 2px 8px;
  border: none;
}

/* 列表进入动画 */
.fade-list-enter-active, .fade-list-leave-active {
  transition: all 0.3s ease;
}
.fade-list-enter, .fade-list-leave-to {
  opacity: 0;
  transform: translateX(-15px);
}

/* 新建/编辑表单内 Label 精致排版与图标 */
.form-label-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;

  .el-icon {
    font-size: 14px;
    color: #4f46e5;
    
    .dark &,
    .theme-dark & {
      color: #38bdf8;
    }
  }
}

/* 弹窗内表单输入域聚焦发光增强 */
:deep(.polaris-glass-dialog) {
  .el-input__wrapper,
  .el-textarea__inner {
    border-radius: 10px !important;
    background-color: var(--el-fill-color-blank) !important;
    border: 1px solid var(--el-border-color-light) !important;
    box-shadow: none !important;
    transition: border-color 0.3s, box-shadow 0.3s !important;

    &:focus,
    &:focus-within {
      border-color: #4f46e5 !important;
      box-shadow: 0 0 8px rgba(79, 70, 229, 0.25) !important;

      .dark &,
      .theme-dark & {
        border-color: #38bdf8 !important;
        box-shadow: 0 0 10px rgba(56, 189, 248, 0.3) !important;
      }
    }
  }
  
  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
}

/* 表格内操作按钮样式补充，确保光暗兼容 */
.op-btn-edit {
  font-size: 12px;
  font-weight: bold;
  padding: 0;
  color: #4f46e5 !important;
  display: inline-flex;
  align-items: center;
  gap: 4px;

  &:hover {
    color: #4338ca !important;
  }

  .dark &,
  .theme-dark & {
    color: #38bdf8 !important;
    &:hover {
      color: #7dd3fc !important;
    }
  }
}

.op-btn-delete {
  font-size: 12px;
  font-weight: bold;
  padding: 0;
  color: #ef4444 !important;
  display: inline-flex;
  align-items: center;
  gap: 4px;

  &:hover {
    color: #dc2626 !important;
  }

  .dark &,
  .theme-dark & {
    color: #fca5a5 !important;
    &:hover {
      color: #f87171 !important;
    }
  }
}
</style>
