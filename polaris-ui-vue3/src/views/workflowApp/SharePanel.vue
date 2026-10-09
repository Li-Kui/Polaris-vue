<template>
  <el-dialog
    v-model="dialogVisible"
    title="工作流分享"
    width="min(920px, 94vw)"
    class="workflow-share-dialog polaris-glass-dialog"
    @close="handleClose"
    append-to-body
  >
    <div class="share-context">当前工作流：{{ workflowName }}</div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="分享列表" name="list">
        <div class="share-toolbar">
          <div>
            <div class="share-toolbar__title">公开访问与嵌入</div>
            <div class="share-toolbar__description">为当前工作流生成独立页面或受限 iframe。</div>
          </div>
          <div class="share-actions">
            <el-button @click="handleDefaults" :disabled="definitionLoading || definitionError">默认配置</el-button>
            <el-button type="primary" icon="Plus" @click="handleCreate">新建分享</el-button>
          </div>
        </div>

        <el-table :data="shareList" v-loading="loading" class="share-table" style="width: 100%">
          <el-table-column prop="shareName" label="分享名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="pageType" label="页面类型" width="110">
            <template #default="{ row }">
              {{ pageTypeLabel(row.pageType) }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status !== '0' ? 'info' : isExpired(row) ? 'warning' : 'success'" effect="light" round>
                {{ row.status !== '0' ? '已停用' : isExpired(row) ? '已过期' : '已启用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="expireTime" label="有效期至" width="170">
            <template #default="{ row }">
              {{ row.expireTime || '永久有效' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <div class="share-actions">
                <el-button link class="share-action-button" @click="handleCopyLink(row)">复制链接</el-button>
                <el-button link class="share-action-button" @click="handleCopyIframe(row)">复制嵌入</el-button>
                <el-button tag="a" :href="`/app/${encodeURIComponent(row.shareCode)}?preview=1`" target="_blank" rel="noopener noreferrer" link class="share-action-button">预览</el-button>
                <el-dropdown trigger="click" @command="command => handleRowCommand(command, row)">
                  <el-button link class="share-action-button">更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="toggle">
                        {{ row.status === '0' ? '停用分享' : '启用分享' }}
                      </el-dropdown-item>
                      <el-dropdown-item command="edit">编辑分享</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除分享</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无分享，创建后即可获得公开链接" :image-size="72" />
          </template>
        </el-table>
        <div class="share-mobile-list" v-loading="loading">
          <el-empty v-if="!shareList.length" description="暂无分享，创建后即可获得公开链接" :image-size="72" />
          <article v-for="row in shareList" :key="row.id" class="share-mobile-card">
            <div class="share-mobile-card__heading">
              <strong>{{ row.shareName }}</strong>
              <el-tag :type="row.status !== '0' ? 'info' : isExpired(row) ? 'warning' : 'success'" round>
                {{ row.status !== '0' ? '已停用' : isExpired(row) ? '已过期' : '已启用' }}
              </el-tag>
            </div>
            <div class="share-toolbar__description">{{ pageTypeLabel(row.pageType) }} · {{ row.expireTime || '永久有效' }}</div>
            <div class="share-actions">
              <el-button link class="share-action-button" @click="handleCopyLink(row)">复制链接</el-button>
              <el-button link class="share-action-button" @click="handleCopyIframe(row)">复制嵌入</el-button>
              <el-button tag="a" :href="`/app/${encodeURIComponent(row.shareCode)}?preview=1`" target="_blank" rel="noopener noreferrer" link class="share-action-button">预览</el-button>
              <el-button link class="share-action-button" @click="handleEdit(row)">编辑</el-button>
              <el-dropdown trigger="click" @command="command => handleRowCommand(command, row)">
                <el-button link class="share-action-button">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="toggle">{{ row.status === '0' ? '停用分享' : '启用分享' }}</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除分享</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </article>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="editingDefaults ? '工作流默认配置' : form.id != null ? '编辑分享' : '新建分享'" name="create" :disabled="!isCreating">
        <div class="share-form-summary">
          <template v-if="editingDefaults">保存后立即应用于所有继承默认设置的分享，已有单独覆盖不变。不会修改工作流草稿和发布版本。</template>
          <template v-else>{{ form.id != null ? '保存后原分享链接不变。' : '使用默认设置即可创建分享。' }} 标题、说明、主题和对话引导留空时继承工作流配置。</template>
        </div>
        <div v-if="definitionError" class="share-toolbar__description">
          默认配置加载失败，请重试后保存。
          <el-button link class="share-action-button" @click="getDefinition">重新加载</el-button>
        </div>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-form-item v-if="!editingDefaults" label="分享名称" prop="shareName">
            <el-input v-model="form.shareName" placeholder="请输入分享名称" />
          </el-form-item>
          <el-form-item label="页面标题">
            <el-input v-model="form.title" :placeholder="defaultConfig.title || workflowName || '继承默认标题'" />
          </el-form-item>
          <el-form-item label="页面说明">
            <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="defaultConfig.description || '继承默认说明'" />
          </el-form-item>
          <el-form-item label="页面主题">
            <el-select v-model="form.theme" :empty-values="[null, undefined]" style="width: 100%">
              <el-option :label="editingDefaults ? '使用默认主题（跟随系统）' : '继承工作流主题'" value="" />
              <el-option label="跟随系统" value="auto" />
              <el-option label="亮色模式" value="light" />
              <el-option label="暗色模式" value="dark" />
            </el-select>
          </el-form-item>
          <el-form-item label="页面类型" prop="pageType">
            <el-select v-model="form.pageType" :empty-values="[null, undefined]" placeholder="请选择页面类型" style="width: 100%">
              <el-option :label="editingDefaults ? '根据已发布工作流自动推荐' : '继承工作流默认（未配置时自动推荐）'" value="" />
              <el-option label="表单应用" value="form" />
              <el-option label="对话应用" value="chat" />
              <el-option label="图片工作台" value="image" />
              <el-option label="前后对比" value="compare" />
              <el-option label="画廊" value="gallery" />
              <el-option label="报告" value="report" />
              <el-option label="批量任务" value="task" />
              <el-option label="数据查询" value="query" />
            </el-select>
            <div v-if="definition" class="share-toolbar__description">
              当前使用：{{ pageTypeLabel(resolvedPageType) }}{{ editingDefaults ? '（工作流默认配置）' : !form.pageType ? (definition.defaultPageType ? '（工作流默认）' : '（根据已发布工作流自动推荐）') : '（当前分享覆盖）' }}
            </div>
          </el-form-item>
          <template v-if="resolvedPageType === 'chat'">
            <el-form-item label="欢迎语">
              <el-input v-model="form.welcomeMessage" type="textarea" :rows="2" :placeholder="defaultConfig.welcomeMessage || '继承默认欢迎语'" />
            </el-form-item>
            <el-form-item label="推荐问题">
              <el-input v-model="form.suggestedQuestionsText" type="textarea" :rows="3" placeholder="每行一个问题，留空继承工作流配置" />
            </el-form-item>
          </template>
          <template v-if="['image', 'compare', 'gallery'].includes(resolvedPageType)">
            <el-form-item v-if="resolvedPageType !== 'gallery'" label="输入图片">
              <el-select :model-value="getImageMapping('inputMapping', 'image')" @update:model-value="value => setImageMapping('inputMapping', 'image', value)" :empty-values="[null, undefined]" style="width: 100%">
                <el-option label="继承配置或自动识别（多个字段时须明确选择）" value="" />
                <el-option v-for="field in imageInputOptions" :key="field.name" :label="field.label" :value="field.name" />
              </el-select>
              <div class="share-toolbar__description">支持已发布定义中的顶层和嵌套对象图片字段，其他参数仍保留在表单中；数组不支持单图上传映射，可改用表单模板的高级 JSON 提交。</div>
            </el-form-item>
            <el-form-item label="图片输出">
              <el-input :model-value="getImageMapping('outputMapping', resolvedPageType === 'compare' ? 'resultImage' : 'images')" @update:model-value="value => setImageMapping('outputMapping', resolvedPageType === 'compare' ? 'resultImage' : 'images', value)" placeholder="例如 /transform_1/output/images，留空自动识别" />
              <div class="share-toolbar__description">从完整执行结果根对象读取。支持点路径和 JSON Pointer；指定后只展示该字段，路径错误不会显示其他节点的图片。</div>
            </el-form-item>
          </template>
          <el-collapse v-model="advancedSections" class="share-advanced">
            <el-collapse-item title="模板高级配置" name="template">
              <div class="share-toolbar__description">JSON 对象，只保存配置值；{} 表示不设置高级配置。可配置 enabledModes、sizeOptions、maxGenerateCount、showDownload、inputMapping、outputMapping。编辑时保留已有自定义字段。</div>
              <el-input v-model="form.extraConfigJson" type="textarea" :rows="5" aria-label="模板高级配置 JSON" />
            </el-collapse-item>
            <el-collapse-item v-if="!editingDefaults" title="访问设置（限流、嵌入与有效期）" name="access">
              <el-form-item label="每分钟运行次数" prop="rateLimit">
                <el-input-number v-model="form.rateLimit" :min="1" :max="10000" />
                <div class="form-hint">仅限制启动请求（失败或重试也计数）；加载、查询、SSE 和上传采用独立防刷限制。</div>
              </el-form-item>
              <el-form-item label="iframe 来源">
                <el-input
                  v-model="form.allowedOriginsText"
                  type="textarea"
                  :rows="2"
                  placeholder="每行一个 Origin，例如 https://example.com"
                />
                <div class="share-toolbar__description">留空仅允许同源嵌入；只填写协议、域名和端口，不含路径。</div>
              </el-form-item>
              <el-form-item label="过期时间" prop="expireTime">
                <el-date-picker
                  v-model="form.expireTime"
                  type="datetime"
                  placeholder="选择过期时间"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  style="width: 100%"
                />
                <div class="share-toolbar__description">不填表示永久有效；编辑时清空可恢复永久有效。</div>
              </el-form-item>
            </el-collapse-item>
          </el-collapse>
          <el-form-item>
            <el-button type="primary" class="share-submit-button" @click="submitCreate" :loading="submitLoading" :disabled="definitionLoading || definitionError">{{ editingDefaults ? '保存默认配置' : form.id != null ? '保存修改' : '创建分享' }}</el-button>
            <el-button class="share-cancel-button" @click="cancelCreate" :disabled="submitLoading">取消</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script>
import {
  createWorkflowShare,
  deleteWorkflowShare,
  getWorkflowShareDefinition,
  listWorkflowShares,
  updateWorkflowShare,
  updateWorkflowShareDefaults
} from '@/api/workflowApp/share'
import {
  buildShareDefaultsRequest,
  buildShareRequest,
  createShareDefaultsForm,
  createShareForm,
  parseSharePageConfig
} from '@/utils/workflowShareConfig'
import {ElMessage, ElMessageBox} from 'element-plus'
import {runtimeImageInputOptions} from '@/utils/workflowInputMapping'

export default {
  name: 'SharePanel',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    workflowDefinitionId: {
      type: Number,
      required: true
    },
    workflowCode: {
      type: String,
      default: ''
    },
    workflowName: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      activeTab: 'list',
      isCreating: false,
      editingDefaults: false,
      loading: false,
      submitLoading: false,
      currentTime: Date.now(),
      expiryTimer: null,
      shareList: [],
      definition: null,
      definitionLoading: false,
      definitionError: false,
      advancedSections: [],
      form: createShareForm(''),
      rules: {
        shareName: [{ required: true, message: '请输入分享名称', trigger: 'blur' }],
        pageType: []
      }
    }
  },
  computed: {
    defaultConfig() {
      try { return parseSharePageConfig(this.definition?.sharePageConfigJson) }
      catch { return {} }
    },
    resolvedPageType() {
      return this.form.pageType || (!this.editingDefaults && this.definition?.defaultPageType) || this.definition?.recommendedPageType || 'form'
    },
    imageInputOptions() {
      return runtimeImageInputOptions(this.definition?.inputSchema)
    },
    dialogVisible: {
      get() {
        return this.visible
      },
      set(val) {
        this.$emit('update:visible', val)
      }
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(val) {
        this.stopExpiryTimer()
        if (val) {
          this.currentTime = Date.now()
          this.expiryTimer = setInterval(() => {
            this.currentTime = Date.now()
          }, 1000)
          this.activeTab = 'list'
          this.isCreating = false
          this.editingDefaults = false
          this.getList()
          this.getDefinition()
        }
      }
    }
  },
  beforeUnmount() {
    this.stopExpiryTimer()
  },
  methods: {
    getImageMapping(group, key) {
      try { return parseSharePageConfig(this.form.extraConfigJson)[group]?.[key] || '' }
      catch { return '' }
    },
    setImageMapping(group, key, value) {
      try {
        const config = parseSharePageConfig(this.form.extraConfigJson)
        const mapping = { ...parseSharePageConfig(config[group]) }
        if (value) mapping[key] = value
        else delete mapping[key]
        if (Object.keys(mapping).length) config[group] = mapping
        else delete config[group]
        this.form.extraConfigJson = JSON.stringify(config, null, 2)
      } catch (error) { ElMessage.error(error.message) }
    },
    stopExpiryTimer() {
      if (this.expiryTimer) {
        clearInterval(this.expiryTimer)
        this.expiryTimer = null
      }
    },
    isExpired(row) {
      if (!row.expireTime) return false
      const value = typeof row.expireTime === 'string' ? row.expireTime.replace(' ', 'T') : row.expireTime
      return new Date(value).getTime() < this.currentTime
    },
    pageTypeLabel(value) {
      const labels = {
        form: '表单应用', chat: '对话应用', image: '图片工作台', compare: '前后对比',
        gallery: '画廊', report: '报告', task: '批量任务', query: '数据查询'
      }
      return labels[value] || value || '继承默认'
    },
    handleClose() {
      this.dialogVisible = false
    },
    async getList() {
      this.loading = true
      try {
        const res = await listWorkflowShares(this.workflowDefinitionId)
        this.shareList = res.rows || res.data || []
      } catch (error) {
        ElMessage.error(error.message || '获取列表失败')
      } finally {
        this.loading = false
      }
    },
    async getDefinition() {
      this.definitionLoading = true
      this.definitionError = false
      this.definition = null
      try {
        const res = await getWorkflowShareDefinition(this.workflowDefinitionId)
        this.definition = res.data || res
        parseSharePageConfig(this.definition.sharePageConfigJson)
      } catch (error) {
        this.definitionError = true
      } finally {
        this.definitionLoading = false
      }
    },
    handleCreate() {
      this.editingDefaults = false
      this.form = createShareForm(this.workflowName)
      this.advancedSections = []
      this.isCreating = true
      this.activeTab = 'create'
      this.$nextTick(() => this.$refs.formRef?.clearValidate())
    },
    handleEdit(row) {
      this.editingDefaults = false
      try {
        this.form = createShareForm(this.workflowName, row)
      } catch (error) {
        ElMessage.error(error.message)
        return
      }
      this.advancedSections = []
      this.isCreating = true
      this.activeTab = 'create'
      this.$nextTick(() => this.$refs.formRef?.clearValidate())
    },
    cancelCreate() {
      this.isCreating = false
      this.editingDefaults = false
      this.activeTab = 'list'
      this.form = createShareForm(this.workflowName)
    },
    async handleDefaults() {
      await this.getDefinition()
      if (this.definitionError) return
      this.form = createShareDefaultsForm(this.workflowName, this.definition)
      this.editingDefaults = true
      this.advancedSections = []
      this.isCreating = true
      this.activeTab = 'create'
      this.$nextTick(() => this.$refs.formRef?.clearValidate())
    },
    submitCreate() {
      if (this.submitLoading || this.definitionLoading || this.definitionError) return
      this.$refs.formRef.validate(async valid => {
        if (!valid) return
        let data
        try { data = this.editingDefaults ? buildShareDefaultsRequest(this.form, this.definition) : buildShareRequest(this.form, this.workflowDefinitionId) }
        catch (error) { ElMessage.error(error.message); return }
        this.submitLoading = true
        try {
          if (this.editingDefaults) {
            await ElMessageBox.confirm('默认配置会立即应用于所有继承设置的分享，单独覆盖的设置不变。确定保存吗？', '保存工作流默认配置', { type: 'warning' })
            const res = await updateWorkflowShareDefaults(this.workflowDefinitionId, data)
            this.definition = res.data
            ElMessage.success('默认配置已保存')
            this.cancelCreate()
            this.getList()
            return
          }
          const editing = data.id != null
          const res = await (editing ? updateWorkflowShare(data) : createWorkflowShare(data))
          if (editing && res.data !== 1) throw new Error('分享不存在或无权修改，请刷新列表')
          ElMessage.success(editing ? '保存成功，原分享链接不变' : '创建成功')
          this.cancelCreate()
          this.getList()
        } catch (error) {
          if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '保存失败')
        } finally {
          this.submitLoading = false
        }
      })
    },
    handleCopyLink(row) {
      if (!navigator.clipboard) {
        ElMessage.error('当前浏览器不支持剪贴板，请使用 HTTPS 或 localhost 访问')
        return
      }
      const url = `${window.location.origin}/app/${row.shareCode}`
      navigator.clipboard.writeText(url).then(() => {
        ElMessage.success('链接已复制到剪贴板')
      }).catch(() => ElMessage.error('复制失败，请检查浏览器剪贴板权限'))
    },
    handleCopyIframe(row) {
      if (!navigator.clipboard) {
        ElMessage.error('当前浏览器不支持剪贴板，请使用 HTTPS 或 localhost 访问')
        return
      }
      const baseUrl = (import.meta.env.VITE_APP_BASE_API || '').replace(/\/$/, '')
      const path = `${baseUrl}/platform/runtime/shares/${encodeURIComponent(row.shareCode)}/embed`
      const url = new URL(path, window.location.origin).toString()
      const iframe = `<iframe src="${url}" style="width:100%;height:600px;border:0" loading="lazy"></iframe>`
      navigator.clipboard.writeText(iframe).then(() => {
        ElMessage.success('iframe 代码已复制到剪贴板')
      }).catch(() => ElMessage.error('复制失败，请检查浏览器剪贴板权限'))
    },
    handleRowCommand(command, row) {
      if (command === 'toggle') {
        this.handleToggleStatus(row)
      } else if (command === 'delete') {
        this.handleDelete(row)
      } else if (command === 'edit') {
        this.handleEdit(row)
      }
    },
    async handleToggleStatus(row) {
      const newStatus = row.status === '0' ? '1' : '0'
      try {
        await updateWorkflowShare({ id: row.id, status: newStatus })
        ElMessage.success('更新成功')
        this.getList()
      } catch (error) {
        ElMessage.error('更新失败')
      }
    },
    handleDelete(row) {
      ElMessageBox.confirm('确定要删除该分享吗？', '提示', { type: 'warning' }).then(async () => {
        try {
          await deleteWorkflowShare(row.id)
          ElMessage.success('删除成功')
          this.getList()
        } catch (error) {
          ElMessage.error('删除失败')
        }
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.form-hint {
  width: 100%;
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.7;
}
.share-context, .share-form-summary {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.7;
  margin-bottom: 14px;
}
.share-mobile-list { display: none; }
.share-mobile-card { padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 12px; background: var(--el-bg-color); }
.share-mobile-card__heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; color: var(--el-text-color-primary); }
.share-mobile-card__heading strong { overflow-wrap: anywhere; font-size: 14px; line-height: 1.7; }
.share-mobile-card__heading .el-tag { flex-shrink: 0; }
.share-mobile-card .share-actions { flex-wrap: wrap; margin-top: 12px; }
.share-advanced { margin-bottom: 20px; }
.share-advanced :deep(.el-collapse-item__header) { color: var(--el-text-color-primary); background: transparent; }
.share-advanced :deep(.el-collapse-item__wrap) { background: transparent; }
.share-advanced .share-toolbar__description { margin-bottom: 10px; }
.share-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 2px 0 18px;
}

.share-toolbar__title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 700;
}

.share-toolbar__description {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.share-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.share-actions :deep(.el-button) {
  height: 30px !important;
  margin: 0 !important;
  padding: 5px 8px !important;
  border-color: transparent !important;
  background: transparent !important;
  box-shadow: none !important;
}

:global(.workflow-share-dialog .el-dialog__body) {
  padding-top: 14px;
}

:global(html:not(.dark) .workflow-share-dialog .el-dialog__title) {
  color: #0f172a !important;
}

:global(.workflow-share-dialog .el-tabs__header) {
  margin-bottom: 18px;
}

:global(.workflow-share-dialog .el-tabs__item) {
  height: 42px;
  color: #64748b !important;
  font-weight: 650;
}

:global(.workflow-share-dialog .el-tabs__item:hover) {
  color: #4f46e5 !important;
}

:global(.workflow-share-dialog .el-tabs__item.is-active) {
  color: #4f46e5 !important;
}

:global(html:not(.dark) .workflow-share-dialog .share-actions .share-action-button.el-button.el-button--default.is-link:not(.el-button--primary)) {
  border-color: transparent !important;
  background: transparent !important;
  color: #4f46e5 !important;
}

:global(html:not(.dark) .workflow-share-dialog .share-actions .share-action-button.el-button.el-button--default.is-link:not(.el-button--primary):hover),
:global(html:not(.dark) .workflow-share-dialog .share-actions .share-action-button.el-button.el-button--default.is-link:not(.el-button--primary):focus-visible) {
  border-color: transparent !important;
  background: rgba(79, 70, 229, 0.08) !important;
  color: #4338ca !important;
}

:global(html:not(.dark) .workflow-share-dialog .share-submit-button.el-button.el-button--primary) {
  border-color: transparent !important;
  background: #4f46e5 !important;
  color: #ffffff !important;
}

:global(html:not(.dark) .workflow-share-dialog .share-cancel-button.el-button.el-button--default) {
  border-color: #cbd5e1 !important;
  background: transparent !important;
  color: #475569 !important;
}

:global(html.dark .workflow-share-dialog .el-tabs__item),
:global(html.theme-dark .workflow-share-dialog .el-tabs__item) {
  color: #94a3b8 !important;
}

:global(html.dark .workflow-share-dialog .el-dialog__title),
:global(html.theme-dark .workflow-share-dialog .el-dialog__title) {
  color: #f8fafc !important;
}

:global(html.dark .workflow-share-dialog .el-tabs__item:hover),
:global(html.dark .workflow-share-dialog .el-tabs__item.is-active),
:global(html.theme-dark .workflow-share-dialog .el-tabs__item:hover),
:global(html.theme-dark .workflow-share-dialog .el-tabs__item.is-active) {
  color: #38bdf8 !important;
}

:global(html.dark .workflow-share-dialog .share-actions .share-action-button.el-button.el-button--default.is-link:not(.el-button--primary)),
:global(html.theme-dark .workflow-share-dialog .share-actions .share-action-button.el-button.el-button--default.is-link:not(.el-button--primary)) {
  border-color: transparent !important;
  background: transparent !important;
  color: #38bdf8 !important;
}

:global(html.dark .workflow-share-dialog .share-actions .share-action-button.el-button.el-button--default.is-link:not(.el-button--primary):hover),
:global(html.dark .workflow-share-dialog .share-actions .share-action-button.el-button.el-button--default.is-link:not(.el-button--primary):focus-visible),
:global(html.theme-dark .workflow-share-dialog .share-actions .share-action-button.el-button.el-button--default.is-link:not(.el-button--primary):hover),
:global(html.theme-dark .workflow-share-dialog .share-actions .share-action-button.el-button.el-button--default.is-link:not(.el-button--primary):focus-visible) {
  border-color: transparent !important;
  background: rgba(56, 189, 248, 0.12) !important;
  color: #7dd3fc !important;
}

:global(html.dark .workflow-share-dialog .share-submit-button.el-button.el-button--primary),
:global(html.theme-dark .workflow-share-dialog .share-submit-button.el-button.el-button--primary) {
  border-color: transparent !important;
  background: #38bdf8 !important;
  color: #0f172a !important;
}

:global(html.dark .workflow-share-dialog .share-cancel-button.el-button.el-button--default:not(.el-button--primary):not(.el-button--success):not(.el-button--warning):not(.el-button--danger)),
:global(html.theme-dark .workflow-share-dialog .share-cancel-button.el-button.el-button--default:not(.el-button--primary):not(.el-button--success):not(.el-button--warning):not(.el-button--danger)) {
  border-color: #475569 !important;
  background: transparent !important;
  color: #cbd5e1 !important;
}

:global(html.dark .workflow-share-dialog .share-cancel-button.el-button.el-button--default:not(.el-button--primary):not(.el-button--success):not(.el-button--warning):not(.el-button--danger):hover),
:global(html.theme-dark .workflow-share-dialog .share-cancel-button.el-button.el-button--default:not(.el-button--primary):not(.el-button--success):not(.el-button--warning):not(.el-button--danger):hover) {
  border-color: #64748b !important;
  background: rgba(255, 255, 255, 0.06) !important;
  color: #f8fafc !important;
}

:global(.workflow-share-dialog .el-input-number__decrease),
:global(.workflow-share-dialog .el-input-number__increase) {
  color: #475569 !important;
}

:global(html.dark .workflow-share-dialog .el-input-number__decrease),
:global(html.dark .workflow-share-dialog .el-input-number__increase),
:global(html.theme-dark .workflow-share-dialog .el-input-number__decrease),
:global(html.theme-dark .workflow-share-dialog .el-input-number__increase) {
  border-color: #334155 !important;
  background: rgba(255, 255, 255, 0.06) !important;
  color: #cbd5e1 !important;
}

:global(.workflow-share-dialog .share-table) {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

:global(.workflow-share-dialog .share-table th.el-table__cell) {
  height: 44px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 12px;
}

:global(.workflow-share-dialog .share-table td.el-table__cell) {
  height: 58px;
}

:global(.workflow-share-dialog .el-tag--success) {
  --el-tag-text-color: #15803d;
  --el-tag-bg-color: #f0fdf4;
  --el-tag-border-color: #bbf7d0;
}

:global(.workflow-share-dialog .el-tag--warning) {
  --el-tag-text-color: #92400e;
  --el-tag-bg-color: #fffbeb;
  --el-tag-border-color: #fde68a;
}

:global(.workflow-share-dialog .el-tag--info) {
  --el-tag-text-color: #475569;
  --el-tag-bg-color: #f1f5f9;
  --el-tag-border-color: #cbd5e1;
}

:global(html.dark .workflow-share-dialog .el-tag--success),
:global(html.theme-dark .workflow-share-dialog .el-tag--success) {
  --el-tag-text-color: #86efac;
  --el-tag-bg-color: #14532d;
  --el-tag-border-color: #166534;
}

:global(html.dark .workflow-share-dialog .el-tag--warning),
:global(html.theme-dark .workflow-share-dialog .el-tag--warning) {
  --el-tag-text-color: #fde68a;
  --el-tag-bg-color: #78350f;
  --el-tag-border-color: #92400e;
}

:global(html.dark .workflow-share-dialog .el-tag--info),
:global(html.theme-dark .workflow-share-dialog .el-tag--info) {
  --el-tag-text-color: #e2e8f0;
  --el-tag-bg-color: #334155;
  --el-tag-border-color: #475569;
}

@media (max-width: 720px) {
  :global(.workflow-share-dialog .share-table) { display: none; }
  .share-mobile-list { display: grid; gap: 12px; }
  .share-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  :global(.workflow-share-dialog .el-dialog__body) {
    padding: 14px 16px 20px;
  }

  :global(.workflow-share-dialog .share-table .el-table__cell.el-table-fixed-column--right) {
    position: static !important;
    right: auto !important;
  }
}
</style>
