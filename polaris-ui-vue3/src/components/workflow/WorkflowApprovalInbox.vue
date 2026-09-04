<template>
  <div class="approval-inbox">
    <header class="approval-header">
      <div>
        <el-button link icon="Back" @click="$emit('back')">返回工作流</el-button>
        <h2>工作流审批箱</h2>
        <p>审批决定持久化后，执行才会从安全检查点继续。</p>
      </div>
      <div class="approval-filters">
        <el-button icon="Refresh" :loading="loading" @click="loadTasks">刷新</el-button>
      </div>
    </header>

    <nav class="approval-buckets" aria-label="审批任务分组">
      <button
        v-for="item in bucketOptions"
        :key="item.value"
        type="button"
        :class="{active: bucket === item.value}"
        @click="bucket = item.value"
      >
        <span>{{ item.label }}</span><strong>{{ bucketCount(item.value) }}</strong>
      </button>
    </nav>

    <el-table v-loading="loading" :data="filteredTasks" class="polaris-el-table" :empty-text="emptyText">
      <el-table-column label="审批事项" min-width="300">
        <template #default="{row}">
          <div class="approval-title">{{ row.content.title || '工作流人工审批' }}</div>
          <div class="approval-subtitle">{{ row.content.workflowCode || '工作流' }} · {{ row.currentStageName || '当前审批' }} · {{ row.content.initiatorName || '工作流发起人' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="审批规则" min-width="210">
        <template #default="{row}">{{ policyText(row) }}</template>
      </el-table-column>
      <el-table-column label="进度" width="130">
        <template #default="{row}">{{ row.currentStageSequence || 1 }} / {{ row.totalStageCount || 1 }} 级</template>
      </el-table-column>
      <el-table-column label="截止时间" width="180">
        <template #default="{row}"><div>{{ formatTime(row.deadline) }}</div><small class="deadline-hint">{{ remainingText(row.deadline, row.status) }}</small></template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{row}">
          <el-tag :type="statusType(row.status)">{{ taskStatusText(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{row}">
          <template v-if="row.summary.canHandle">
            <el-button link @click="openDetail(row)">查看</el-button>
            <el-button link type="success" @click="openDecision(row, 'APPROVE')">通过</el-button>
            <el-button link type="danger" @click="openDecision(row, 'REJECT')">拒绝</el-button>
          </template>
          <template v-else>
            <el-button link @click="openDetail(row)">查看详情</el-button>
            <el-button v-if="row.summary.canRepair" link type="primary" @click="openRepair(row)">修复</el-button>
            <span v-if="row.status === 'PENDING'" class="waiting-label">等待其他人</span>
          </template>
          <el-button v-if="row.status === 'PENDING' && row.summary.canRemind" link type="primary" @click="remind(row)">催办</el-button>
          <el-dropdown v-if="row.status === 'PENDING' && row.summary.canManage" @command="openManagement(row, $event)"><el-button link type="primary">管理⌄</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="REASSIGN">重新指派待处理人</el-dropdown-item><el-dropdown-item command="RESTART">作废并重新发起本级</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogOpen" class="workflow-dialog" :title="decision === 'APPROVE' ? '通过审批' : '拒绝审批'" width="520px">
      <section v-if="selectedTask" class="approval-detail">
        <h3>{{ selectedTask.content.title || '工作流人工审批' }}</h3>
        <p v-if="selectedTask.content.description">{{ selectedTask.content.description }}</p>
        <dl v-if="selectedTask.content.fields?.length">
          <template v-for="field in selectedTask.content.fields" :key="field.key">
            <dt>{{ field.label }}</dt><dd :class="{unavailable: field.available === false}">
              <template v-if="field.available !== false && field.displayType === 'LINK' && safeLink(field.value)"><el-link :href="safeLink(field.value)" target="_blank" type="primary">{{ String(field.value) }}</el-link></template>
              <template v-else-if="field.available !== false && field.displayType === 'ATTACHMENT' && attachmentItems(field.value).length"><el-link v-for="attachment in attachmentItems(field.value)" :key="attachment.url" :href="attachment.url" target="_blank" type="primary" class="attachment-link">{{ attachment.name }}</el-link></template>
              <pre v-else-if="field.available !== false && ['OBJECT', 'ARRAY', 'JSON'].includes(field.displayType)" class="structured-value">{{ displayValue(field.value, field) }}</pre>
              <template v-else>{{ displayValue(field.value, field) }}</template>
            </dd>
          </template>
        </dl>
        <el-button v-if="selectedTask.content.artifactId" class="artifact-button" plain type="primary" @click="downloadSnapshot(selectedTask)">下载完整审批内容（{{ formatBytes(selectedTask.content.artifactSizeBytes) }}）</el-button>
      </section>
      <el-alert
        :type="decision === 'APPROVE' ? 'success' : 'warning'"
        :closable="false"
        show-icon
        title="提交后不可撤销；多人审批未达到阈值时，任务会继续等待。"
      />
      <el-input v-model="comment" type="textarea" :rows="5" maxlength="500" show-word-limit :placeholder="commentRequired ? '请填写审批意见（必填）' : '审批意见（可选）'" />
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button :type="decision === 'APPROVE' ? 'success' : 'danger'" :loading="deciding" @click="submitDecision">
          确认{{ decision === 'APPROVE' ? '通过' : '拒绝' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="repairOpen" class="workflow-dialog" title="修复审批人配置" width="560px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="补充可用审批人后，本级审批会重新进入等待状态，等待期限从恢复时重新计算。"
      />
      <label class="repair-field">
        <span>补充审批对象</span>
        <el-select v-model="repairTargets" multiple filterable collapse-tags :max-collapse-tags="3" placeholder="选择成员、角色或部门">
          <el-option
            v-for="item in repairDirectory"
            :key="`${item.type}:${item.id}`"
            :label="`${directoryTypeText(item.type)} · ${item.name}${item.description ? `（${item.description}）` : ''}`"
            :value="`${item.type}:${item.id}`"
            :disabled="!item.available"
          />
        </el-select>
      </label>
      <label class="repair-field">
        <span>修复原因</span>
        <el-input v-model="repairReason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明为什么调整审批人（必填，会记录到审计日志）" />
      </label>
      <el-checkbox v-if="repairTargets.some(item => item.startsWith('DEPARTMENT:'))" v-model="repairIncludeChildren">部门目标包含子部门成员</el-checkbox>
      <template #footer>
        <el-button @click="repairOpen = false">取消</el-button>
        <el-button type="primary" :loading="repairing" @click="submitRepair">恢复审批</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailOpen" class="workflow-dialog" title="审批详情" width="620px">
      <section v-if="selectedTask" class="approval-detail">
        <h3>{{ selectedTask.content.title || '工作流人工审批' }}</h3>
        <p v-if="selectedTask.content.description">{{ selectedTask.content.description }}</p>
        <dl v-if="selectedTask.content.fields?.length">
          <template v-for="field in selectedTask.content.fields" :key="field.key">
            <dt>{{ field.label }}</dt><dd :class="{unavailable: field.available === false}">
              <template v-if="field.available !== false && field.displayType === 'LINK' && safeLink(field.value)"><el-link :href="safeLink(field.value)" target="_blank" type="primary">{{ String(field.value) }}</el-link></template>
              <template v-else-if="field.available !== false && field.displayType === 'ATTACHMENT' && attachmentItems(field.value).length"><el-link v-for="attachment in attachmentItems(field.value)" :key="attachment.url" :href="attachment.url" target="_blank" type="primary" class="attachment-link">{{ attachment.name }}</el-link></template>
              <pre v-else-if="field.available !== false && ['OBJECT', 'ARRAY', 'JSON'].includes(field.displayType)" class="structured-value">{{ displayValue(field.value, field) }}</pre>
              <template v-else>{{ displayValue(field.value, field) }}</template>
            </dd>
          </template>
        </dl>
        <el-button v-if="selectedTask.content.artifactId" class="artifact-button" plain type="primary" @click="downloadSnapshot(selectedTask)">下载完整审批内容（{{ formatBytes(selectedTask.content.artifactSizeBytes) }}）</el-button>
        <div class="detail-meta">当前进度：{{ selectedTask.currentStageSequence || 1 }} / {{ selectedTask.totalStageCount || 1 }} 级 · {{ taskStatusText(selectedTask) }}</div>
        <section class="decision-history">
          <h4>处理记录</h4>
          <el-empty v-if="!selectedTask.summary.history?.length" description="暂无处理记录" :image-size="56" />
          <ol v-else>
            <li v-for="(item, index) in selectedTask.summary.history" :key="`${item.decidedAt || index}_${index}`">
              <span :class="['decision-dot', item.decision === 'APPROVE' ? 'approved' : 'rejected']" />
              <div>
                <strong>{{ item.actorName || '审批人' }}</strong>
                <span>{{ item.decision === 'APPROVE' ? '已通过' : '已拒绝' }}</span>
                <small>{{ item.stageName || '审批' }} · {{ formatTime(item.decidedAt) }}</small>
                <p v-if="item.comment">{{ item.comment }}</p>
              </div>
            </li>
          </ol>
        </section>
      </section>
    </el-dialog>

    <el-dialog v-model="managementOpen" class="workflow-dialog" :title="managementMode === 'RESTART' ? '作废并重新发起本级' : '重新指派审批人'" width="580px">
      <el-alert :type="managementMode === 'RESTART' ? 'warning' : 'info'" :closable="false" show-icon :title="managementMode === 'RESTART' ? '原审批记录会完整保留，本级将从零重新审批。' : '仅替换尚未处理的审批人，已提交的决定保持不变。'" />
      <label class="repair-field"><span>新的审批对象</span><el-select v-model="managementTargets" multiple filterable remote :remote-method="searchManagementDirectory" :loading="managementDirectoryLoading" collapse-tags :max-collapse-tags="3" placeholder="搜索成员、角色或部门"><el-option v-for="item in managementDirectory" :key="`manage-${item.type}:${item.id}`" :label="`${directoryTypeText(item.type)} · ${item.name}${item.description ? `（${item.description}）` : ''}`" :value="`${item.type}:${item.id}`" :disabled="!item.available" /></el-select></label>
      <label class="repair-field"><span>操作原因</span><el-input v-model="managementReason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="必填，将记录到审计日志" /></label>
      <el-checkbox v-if="managementTargets.some(item => item.startsWith('DEPARTMENT:'))" v-model="managementIncludeChildren">部门目标包含子部门成员</el-checkbox>
      <template #footer><el-button @click="managementOpen = false">取消</el-button><el-button type="primary" :loading="managing" @click="submitManagement">确认{{ managementMode === 'RESTART' ? '重新发起' : '指派' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script>
import {
  decideWorkflowApproval,
  downloadWorkflowArtifact,
  getWorkflowApproval,
  listWorkflowApprovalDirectory,
  listWorkflowApprovals,
  reassignWorkflowApproval,
  remindWorkflowApproval,
  repairWorkflowApproval,
  restartWorkflowApprovalStage
} from '@/api/ai/workflow'

export default {
  name: 'WorkflowApprovalInbox',
  emits: ['back'],
  data() {
    return {
      loading: false,
      deciding: false,
      bucket: 'TODO',
      bucketOptions: [
        {label: '待我处理', value: 'TODO'},
        {label: '我已处理', value: 'PROCESSED'},
        {label: '等待其他人', value: 'WAITING'},
        {label: '已结束', value: 'FINISHED'},
        {label: '异常', value: 'ERROR'}
      ],
      tasks: [],
      selectedTask: null,
      decision: 'APPROVE',
      comment: '',
      dialogOpen: false,
      detailOpen: false,
      repairOpen: false,
      repairing: false,
      repairTargets: [],
      repairReason: '',
      repairIncludeChildren: false,
      repairDirectory: [],
      managementOpen: false,
      managementMode: 'REASSIGN',
      managementTargets: [],
      managementReason: '',
      managementIncludeChildren: false,
      managementDirectory: [],
      managementDirectoryLoading: false,
      managementSearchTimer: null,
      managing: false
    }
  },
  computed: {
    displayTasks() {
      return this.tasks.map(this.normalizeTask)
    },
    filteredTasks() {
      return this.displayTasks.filter(task => this.taskBucket(task) === this.bucket)
    },
    emptyText() {
      return {
        TODO: '暂无需要你处理的审批',
        PROCESSED: '暂无你已处理、仍在流转的审批',
        WAITING: '暂无等待其他人处理的审批',
        FINISHED: '暂无已结束的审批',
        ERROR: '暂无异常审批'
      }[this.bucket]
    },
    commentRequired() {
      if (!this.selectedTask) return false
      return this.decision === 'APPROVE' ? !!this.selectedTask.summary?.requireApproveComment : this.selectedTask.summary?.requireRejectComment !== false
    }
  },
  created() {
    this.loadTasks()
  },
  beforeUnmount() { clearTimeout(this.managementSearchTimer) },
  methods: {
    async loadTasks() {
      this.loading = true
      try {
        const response = await listWorkflowApprovals()
        this.tasks = response.data || []
      } finally {
        this.loading = false
      }
    },
    async openDecision(task, decision) {
      const response = await getWorkflowApproval(task.approvalInstanceId)
      this.selectedTask = this.normalizeTask(response.data || task)
      this.decision = decision
      this.comment = ''
      this.dialogOpen = true
    },
    async openDetail(task) {
      const response = await getWorkflowApproval(task.approvalInstanceId)
      this.selectedTask = this.normalizeTask(response.data || task)
      this.detailOpen = true
    },
    async openRepair(task) {
      this.selectedTask = task
      this.repairTargets = []
      this.repairReason = ''
      this.repairIncludeChildren = false
      this.repairOpen = true
      try {
        const response = await listWorkflowApprovalDirectory('')
        this.repairDirectory = response.data || []
      } catch (_) {
        this.repairOpen = false
      }
    },
    async submitRepair() {
      if (!this.repairTargets.length) {
        this.$message.warning('请选择至少一个审批对象')
        return
      }
      if (!this.repairReason.trim()) {
        this.$message.warning('请填写修复原因')
        return
      }
      const grouped = new Map()
      this.repairTargets.forEach(value => {
        const separator = value.indexOf(':')
        const type = value.slice(0, separator)
        const id = value.slice(separator + 1)
        if (!grouped.has(type)) grouped.set(type, [])
        grouped.get(type).push(id)
      })
      this.repairing = true
      try {
        await repairWorkflowApproval(this.selectedTask.approvalInstanceId, {
          targets: [...grouped].map(([type, ids]) => ({
            type,
            ids,
            includeChildren: type === 'DEPARTMENT' && this.repairIncludeChildren
          })),
          reason: this.repairReason.trim(),
          expectedLockVersion: this.selectedTask.lockVersion
        })
        this.$message.success('审批任务已恢复，新的审批人可以开始处理')
        this.repairOpen = false
        this.bucket = 'TODO'
        await this.loadTasks()
      } finally {
        this.repairing = false
      }
    },
    async remind(task) {
      try {
        await remindWorkflowApproval(task.approvalInstanceId, {
          message: '请及时处理当前审批任务',
          expectedLockVersion: task.lockVersion,
          requestId: globalThis.crypto?.randomUUID?.() || `remind_${Date.now()}`
        })
        this.$message.success('催办已发送并记录')
      } catch (_) {}
    },
    async openManagement(task, mode) {
      this.selectedTask = task
      this.managementMode = mode
      this.managementTargets = []
      this.managementReason = ''
      this.managementIncludeChildren = false
      this.managementOpen = true
      await this.loadManagementDirectory('')
    },
    searchManagementDirectory(keyword) {
      clearTimeout(this.managementSearchTimer)
      this.managementSearchTimer = setTimeout(() => this.loadManagementDirectory(keyword || ''), 250)
    },
    async loadManagementDirectory(keyword) {
      this.managementDirectoryLoading = true
      try { const response = await listWorkflowApprovalDirectory(keyword); this.managementDirectory = response.data || [] }
      finally { this.managementDirectoryLoading = false }
    },
    groupedTargets(keys) {
      const grouped = new Map()
      keys.forEach(value => { const separator = value.indexOf(':'); const type = value.slice(0, separator); const id = value.slice(separator + 1); if (!grouped.has(type)) grouped.set(type, []); grouped.get(type).push(id) })
      return [...grouped].map(([type, ids]) => ({
        type,
        ids,
        includeChildren: type === 'DEPARTMENT' && this.managementIncludeChildren
      }))
    },
    async submitManagement() {
      if (!this.managementTargets.length) return this.$message.warning('请选择至少一个审批对象')
      if (!this.managementReason.trim()) return this.$message.warning('请填写操作原因')
      this.managing = true
      try {
        const data = {targets: this.groupedTargets(this.managementTargets), reason: this.managementReason.trim(), expectedLockVersion: this.selectedTask.lockVersion}
        if (this.managementMode === 'RESTART') await restartWorkflowApprovalStage(this.selectedTask.approvalInstanceId, data)
        else await reassignWorkflowApproval(this.selectedTask.approvalInstanceId, data)
        this.$message.success(this.managementMode === 'RESTART' ? '本级审批已重新发起' : '审批人已重新指派')
        this.managementOpen = false
        await this.loadTasks()
      } finally { this.managing = false }
    },
    async submitDecision() {
      if (this.commentRequired && !this.comment.trim()) {
        this.$message.warning('请填写审批意见')
        return
      }
      this.deciding = true
      try {
        const response = await decideWorkflowApproval(this.selectedTask.approvalInstanceId, {
          decision: this.decision,
          comment: this.comment || null,
          expectedLockVersion: this.selectedTask.lockVersion,
          requestId: globalThis.crypto?.randomUUID?.() || `approval_${Date.now()}_${Math.random().toString(36).slice(2)}`
        })
        if (response.data?.status === 'EXPIRED') {
          this.$message.warning('审批任务已超时，工作流已终止')
        } else {
          this.$message.success('审批决定已保存')
        }
        this.dialogOpen = false
        await this.loadTasks()
      } finally {
        this.deciding = false
      }
    },
    statusType(status) {
      return {
        PENDING: 'warning',
        APPROVED: 'success',
        REJECTED: 'danger',
        EXPIRED: 'info',
        CANCELLED: 'info'
      }[status] || 'info'
    },
    statusText(status) {
      return {PENDING: '待处理', APPROVED: '已通过', REJECTED: '已拒绝', EXPIRED: '已超时', CANCELLED: '已取消', CONFIG_ERROR: '配置异常'}[status] || status
    },
    taskBucket(task) {
      if (task.status === 'CONFIG_ERROR') return 'ERROR'
      if (task.status !== 'PENDING') return 'FINISHED'
      if (task.summary?.canHandle) return 'TODO'
      return task.summary?.currentUserDecision ? 'PROCESSED' : 'WAITING'
    },
    bucketCount(bucket) {
      return this.displayTasks.filter(task => this.taskBucket(task) === bucket).length
    },
    directoryTypeText(type) {
      return {USER: '成员', ROLE: '角色', DEPARTMENT: '部门'}[type] || '审批对象'
    },
    taskStatusText(task) {
      if (task.status === 'PENDING' && task.summary?.currentUserDecision) {
        return task.summary.currentUserDecision === 'APPROVE' ? '我已通过' : '我已拒绝'
      }
      return this.statusText(task.status)
    },
    policyText(row) {
      if (row.approvalMode === 'ALL') return '所有审批人通过'
      if (row.approvalMode === 'N_OF_M') return `至少 ${row.requiredApprovals || 1} 人通过`
      return '任一审批人通过'
    },
    parseJson(value) {
      if (!value) return {}
      try { return typeof value === 'string' ? JSON.parse(value) : value }
      catch (_) { return {} }
    },
    normalizeTask(task) {
      return {...task, content: this.parseJson(task.contentSnapshot), summary: this.parseJson(task.decisionSummary)}
    },
    formatTime(value) { return value ? new Date(value).toLocaleString() : '不限制' },
    remainingText(value, status) {
      if (!value || status !== 'PENDING') return ''
      const milliseconds = new Date(value).getTime() - Date.now()
      if (milliseconds <= 0) return '已到截止时间'
      const minutes = Math.ceil(milliseconds / 60000)
      if (minutes < 60) return `剩余 ${minutes} 分钟`
      const hours = Math.ceil(minutes / 60)
      if (hours < 48) return `剩余约 ${hours} 小时`
      return `剩余约 ${Math.ceil(hours / 24)} 天`
    },
    displayValue(value, field = {}) {
      if (field.available === false) return field.unavailableReason || '字段不可用'
      if (value === null || value === undefined || value === '') return '—'
      if (field.displayType === 'BOOLEAN') return value === true || value === 1 || value === 'true' || value === '1' ? '是' : '否'
      if (field.displayType === 'MONEY' && Number.isFinite(Number(value))) return new Intl.NumberFormat('zh-CN', {style: 'currency', currency: 'CNY'}).format(Number(value))
      if (field.displayType === 'NUMBER' && Number.isFinite(Number(value))) return new Intl.NumberFormat('zh-CN').format(Number(value))
      if (['DATE', 'DATETIME'].includes(field.displayType)) {
        const date = new Date(value)
        if (!Number.isNaN(date.getTime())) return field.displayType === 'DATE' ? date.toLocaleDateString() : date.toLocaleString()
      }
      return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
    },
    safeLink(value) {
      try { const url = new URL(String(value)); return ['http:', 'https:'].includes(url.protocol) ? url.href : '' }
      catch (_) { return '' }
    },
    attachmentItems(value) {
      const items = Array.isArray(value) ? value : value ? [value] : []
      return items.map((item, index) => {
        const source = typeof item === 'string' ? {url: item} : item || {}
        const url = this.safeLink(source.url || source.href)
        return url ? {url, name: source.name || source.fileName || `附件 ${index + 1}`} : null
      }).filter(Boolean)
    },
    formatBytes(value) {
      const size = Number(value || 0)
      if (!size) return '完整文件'
      if (size < 1024) return `${size} B`
      if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
      return `${(size / 1024 / 1024).toFixed(1)} MB`
    },
    async downloadSnapshot(task) {
      const response = await downloadWorkflowArtifact(
        task.executionId, task.content.artifactId)
      const url = URL.createObjectURL(response.data)
      const link = document.createElement('a')
      link.href = url
      link.download = task.content.artifactFileName || '审批内容.json'
      link.click()
      URL.revokeObjectURL(url)
    }
  }
}
</script>

<style scoped>
.approval-inbox {
  padding: 24px;
  border: 1px solid var(--workflow-border, var(--el-border-color-light));
  border-radius: var(--workflow-radius, 18px);
  background: var(--workflow-surface, var(--el-bg-color));
  box-shadow: var(--workflow-shadow, none);
}

.approval-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.approval-header h2 {
  margin: 10px 0 6px;
}

.approval-header p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.approval-filters {
  display: flex;
  gap: 8px;
}

.approval-buckets {
  display: grid;
  grid-template-columns: repeat(5, minmax(110px, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.approval-buckets button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 46px;
  padding: 0 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  color: var(--el-text-color-regular);
  background: var(--el-bg-color);
  cursor: pointer;
}

.approval-buckets button:hover,
.approval-buckets button.active {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.approval-buckets strong {
  min-width: 24px;
  padding: 2px 7px;
  border-radius: 999px;
  color: inherit;
  background: var(--el-fill-color-light);
  text-align: center;
}

.el-alert {
  margin-bottom: 16px;
}

.approval-title {font-weight: 700;color: var(--el-text-color-primary)}
.approval-subtitle,.detail-meta {margin-top: 4px;color: var(--el-text-color-secondary);font-size: 13px}
.waiting-label {margin-left:8px;color:var(--el-text-color-secondary);font-size:12px}
.deadline-hint {color:var(--el-text-color-secondary)}
.approval-detail {padding: 2px 2px 14px}
.approval-detail h3 {margin: 0 0 8px;font-size: 18px}
.approval-detail>p {white-space: pre-wrap;color: var(--el-text-color-regular)}
.approval-detail dl {display:grid;grid-template-columns:120px minmax(0,1fr);margin:16px 0 0;border:1px solid var(--el-border-color-light);border-radius:10px;overflow:hidden}
.approval-detail dt,.approval-detail dd {margin:0;padding:10px 12px;border-bottom:1px solid var(--el-border-color-lighter);white-space:pre-wrap;overflow-wrap:anywhere}
.approval-detail dt {background:var(--el-fill-color-light);font-weight:600}
.approval-detail dd.unavailable {color:var(--el-color-warning)}
.artifact-button {margin-top:14px}
.attachment-link {display:block;margin-bottom:4px}.structured-value{max-height:240px;margin:0;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;font:12px/1.55 ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace}
.approval-detail dt:last-of-type,.approval-detail dd:last-of-type {border-bottom:0}
.decision-history {margin-top:20px;padding-top:16px;border-top:1px solid var(--el-border-color-lighter)}
.decision-history h4 {margin:0 0 14px}
.decision-history ol {margin:0;padding:0;list-style:none}
.decision-history li {display:grid;grid-template-columns:14px minmax(0,1fr);gap:10px;padding-bottom:16px}
.decision-history li>div {display:grid;grid-template-columns:auto 1fr;gap:2px 10px;align-items:baseline}
.decision-history li span {color:var(--el-text-color-regular)}
.decision-history li small {grid-column:1/-1;color:var(--el-text-color-secondary)}
.decision-history li p {grid-column:1/-1;margin:5px 0 0;padding:8px 10px;border-radius:8px;background:var(--el-fill-color-light);white-space:pre-wrap}
.decision-dot {width:9px;height:9px;margin-top:6px;border-radius:50%;background:var(--el-color-info)}
.decision-dot.approved {background:var(--el-color-success)}
.decision-dot.rejected {background:var(--el-color-danger)}
.repair-field {display:grid;gap:7px;margin-top:16px}
.repair-field>span {font-size:13px;font-weight:600;color:var(--el-text-color-primary)}

@media (max-width: 760px) {
  .approval-header {align-items:flex-start;flex-direction:column}
  .approval-buckets {grid-template-columns:repeat(2,minmax(120px,1fr))}
}
</style>
