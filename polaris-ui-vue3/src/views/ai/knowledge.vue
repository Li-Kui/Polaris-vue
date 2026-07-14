<template>
  <div class="knowledge-wrapper">
    <!-- 左侧知识库卡片栏 -->
    <aside class="kb-sidebar">
      <div class="kb-sidebar-header">
        <h3 class="kb-title">知识库列表</h3>
        <el-button
          id="btn-add-kb"
          class="btn-gradient"
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
        <section class="kb-docs-section">
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
            class="docs-table"
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
                <el-tooltip :open-delay="400" content="重新生成向量索引" placement="top">
                  <el-button
                    link
                    icon="RefreshLeft"
                    @click="handleRebuildDoc(scope.row)"
                  />
                </el-tooltip>
                <el-tooltip :open-delay="400" content="删除文档" placement="top">
                  <el-button
                    link
                    type="danger"
                    icon="Delete"
                    @click="handleDeleteDoc(scope.row)"
                  />
                </el-tooltip>
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
      class="kb-dialog"
      width="500px"
    >
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="请输入知识库名称" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            maxlength="200"
            placeholder="请输入知识库的业务背景或描述，便于维护..."
            rows="4"
            show-word-limit
            type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="openDialog = false">取 消</el-button>
          <el-button class="btn-gradient" type="primary" @click="submitForm">确 定</el-button>
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

<style scoped>
/* 骨架与现代感布局 */
.knowledge-wrapper {
  display: flex;
  height: calc(100vh - 84px); /* 减去北辰顶部 navbar 与 tagsView 高度 */
  background: #f7f9fc;
  font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

/* 左侧知识库栏 */
.kb-sidebar {
  width: 320px;
  background: #ffffff;
  border-right: 1px solid #eef2f7;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 8px rgba(165, 175, 186, 0.06);
}

.kb-sidebar-header {
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f2f5f8;
}

.kb-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #1a202c;
}

.btn-gradient {
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-dark-2) 100%) !important;
  border: none !important;
  color: white !important;
  transition: all 0.3s ease;
}

.btn-gradient:hover {
  opacity: 0.95;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px var(--el-color-primary-light-5);
}

.kb-search-box {
  padding: 12px 20px;
  border-bottom: 1px solid #f2f5f8;
}

.kb-list-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 60px;
  color: var(--el-text-color-primary);
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 10px;
}

/* 知识库卡片微动效与高光 */
.kb-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 12px;
  cursor: pointer;
  position: relative;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.kb-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.08);
  transform: translateY(-2px);
}

.kb-card.active {
  background: #eff6ff;
  border-color: #3b82f6;
  box-shadow: 0 2px 10px rgba(59, 130, 246, 0.1);
}

.kb-card-info {
  flex: 1;
  min-width: 0;
}

.kb-card-name {
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
  display: block;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-card-desc {
  margin: 0;
  font-size: 12px;
  color: #718096;
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
}

.kb-card:hover .kb-card-actions,
.kb-card.active .kb-card-actions {
  opacity: 1;
}

.kb-action-btn {
  padding: 4px;
  font-size: 14px;
  color: #718096;
}

.kb-action-btn:hover {
  color: #3b82f6;
}

.kb-action-btn.danger:hover {
  color: #ef4444;
}

/* 右侧内容主区 */
.kb-main {
  flex: 1;
  background: #f8fafc;
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
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-dark-2) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: 24px;
}

.select-prompt h2 {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 12px 0;
}

.select-prompt p {
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
}

.glow-sphere {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 250px;
  height: 250px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.1) 0%, rgba(255, 255, 255, 0) 70%);
  z-index: -1;
  filter: blur(20px);
}

/* 知识库文档展示主区 */
.kb-content {
  padding: 30px;
}

.kb-content-header {
  margin-bottom: 24px;
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
  font-weight: 700;
  color: #0f172a;
}

.kb-tag {
  background-color: #f1f5f9;
  border: none;
  color: #475569;
}

.kb-detail-desc {
  margin: 0;
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
}

/* 上传板块设计 */
.kb-upload-section {
  background: #ffffff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  margin-bottom: 24px;
  border: 1px solid #e2e8f0;
}

.kb-uploader {
  width: 100%;
}

:deep(.el-upload-dragger) {
  width: 100% !important;
  height: 180px;
  background: #f8fafc;
  border: 1.5px dashed #cbd5e1;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  transition: all 0.3s ease;
}

:deep(.el-upload-dragger:hover) {
  border-color: #3b82f6;
  background: #f0f7ff;
}

.kb-upload-icon {
  font-size: 44px;
  color: #94a3b8;
  margin-bottom: 12px;
  transition: color 0.3s ease;
}

:deep(.el-upload-dragger:hover) .kb-upload-icon {
  color: #3b82f6;
}

/* 表格与文档列表 */
.kb-docs-section {
  background: #ffffff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  border: 1px solid #e2e8f0;
}

.docs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.docs-header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
}

.btn-refresh {
  color: #3b82f6;
  font-weight: 600;
}

.doc-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doc-type-icon {
  font-size: 18px;
  color: #64748b;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  border: none;
}

.status-spin {
  font-size: 12px;
}

.action-btn {
  font-size: 16px;
  padding: 4px;
  color: #64748b;
  margin: 0 6px;
}

.action-btn:hover {
  color: #3b82f6;
}

.action-btn.danger:hover {
  color: #ef4444;
}

/* 列表进入动画 */
.fade-list-enter-active, .fade-list-leave-active {
  transition: all 0.3s ease;
}
.fade-list-enter, .fade-list-leave-to {
  opacity: 0;
  transform: translateX(-15px);
}

/* 弹窗设计 */
:deep(.kb-dialog) {
  border-radius: 12px;
  overflow: hidden;
}
:deep(.kb-dialog .el-dialog__header) {
  background: #f8fafc;
  padding: 20px;
  border-bottom: 1px solid #e2e8f0;
}
:deep(.kb-dialog .el-dialog__title) {
  font-weight: 700;
  color: #1e293b;
}
:deep(.kb-dialog .el-dialog__body) {
  padding: 24px 30px;
}
</style>
