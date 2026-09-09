<template>
  <section class="approval-editor">
      <header class="approval-heading">
        <div><strong>人工审批</strong><small>按“审批什么、谁来审批、审批后做什么”依次设置</small></div>
        <span class="approval-heading-actions">
          <el-button plain @click="previewOpen = true">预览审批单</el-button>
          <el-tag :type="issues.length ? 'warning' : 'success'" effect="plain">
            {{ issues.length ? `${issues.length} 项待完成` : '配置完成' }}
          </el-tag>
        </span>
      </header>

      <section class="approval-section">
        <div class="section-title"><b>1. 审批什么</b><small>审批人打开任务时先看到标题和说明</small></div>
        <div class="content-grid">
          <label><span>审批标题</span><el-input :model-value="content.titleTemplate" :disabled="disabled" maxlength="200" placeholder="例如：订单审批" @input="updateContent({titleTemplate: $event})" /></label>
          <label class="wide"><span>审批说明</span><el-input :model-value="content.descriptionTemplate" type="textarea" :rows="3" :disabled="disabled" maxlength="2000" placeholder="说明需要核对的内容和审批标准" @input="updateContent({descriptionTemplate: $event})" /></label>
        </div>
        <div class="field-heading">
          <span><b>审批单字段（可选）</b><small>选择需要展示给审批人的上游数据</small></span>
          <span class="inline-actions"><el-button plain :disabled="disabled || fields.length >= 50 || !recommendedFields.length" @click="addRecommendedFields">自动添加常用字段</el-button><el-button plain :disabled="disabled || fields.length >= 50" @click="addField">＋ 添加字段</el-button></span>
        </div>
        <div v-if="!fields.length" class="compact-empty">未添加字段，审批单仍会显示标题和说明。</div>
        <div v-for="(field, index) in fields" :key="field.key" class="approval-field-row">
          <el-input :model-value="field.label" :disabled="disabled" placeholder="显示名称" @input="updateField(index, {label: $event})" />
          <el-select :model-value="fieldSourceMode(field)" :disabled="disabled" @change="fieldSourceModeChanged(index, $event)">
            <el-option label="上游字段" value="EXPRESSION" /><el-option label="固定值" value="VALUE" />
          </el-select>
          <el-select v-if="fieldSourceMode(field) === 'EXPRESSION'" :model-value="field.source?.expression || ''" filterable clearable :disabled="disabled" placeholder="选择上游字段" @change="updateField(index, {source: {expression: $event}})">
            <el-option-group v-for="group in sourceGroups" :key="group.id" :label="group.label">
              <el-option v-for="option in group.options" :key="option.expression" :label="option.label" :value="option.expression" />
            </el-option-group>
          </el-select>
          <el-input v-else :model-value="field.source?.value ?? ''" :disabled="disabled" placeholder="输入固定展示内容" @input="updateField(index, {source: {value: $event}})" />
          <el-select :model-value="field.displayType || 'TEXT'" :disabled="disabled" @change="updateField(index, {displayType: $event})">
            <el-option v-for="item in displayTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select :model-value="field.mask || 'NONE'" :disabled="disabled" title="敏感信息展示方式" @change="updateField(index, {mask: $event})">
            <el-option v-for="item in maskTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input v-if="field.mask === 'CUSTOM'" :model-value="field.maskPattern || ''" :disabled="disabled" maxlength="256" placeholder="# 保留，* 隐藏" @input="updateField(index, {maskPattern: String($event || '').replace(/[^#*]/g, '')})" />
          <span v-else class="field-order"><el-button text :disabled="disabled || index === 0" title="上移" @click="moveField(index, -1)">↑</el-button><el-button text :disabled="disabled || index === fields.length - 1" title="下移" @click="moveField(index, 1)">↓</el-button></span>
          <el-button text type="danger" :disabled="disabled" title="删除字段" @click="removeField(index)"><el-icon><Delete /></el-icon></el-button>
        </div>
      </section>

      <section class="approval-section">
        <div class="section-title stage-title"><span><b>2. 谁来审批</b><small>级别按顺序执行；同一级可由多人共同决定</small></span><el-button type="primary" plain :disabled="disabled || stages.length >= 20" @click="addStage">＋ 添加审批级别</el-button></div>
        <article v-for="(stage, index) in stages" :key="stage.id" class="approval-stage-card">
          <div class="stage-number">{{ index + 1 }}</div>
          <div class="stage-main">
            <div class="stage-row">
              <label><span>级别名称</span><el-input :model-value="stage.name" :disabled="disabled" placeholder="例如：财务审批" @input="updateStage(index, {name: $event})" /></label>
              <label class="stage-people"><span>审批人</span>
                <el-select :model-value="targetKeys(stage)" multiple filterable remote :remote-method="searchDirectory" collapse-tags :max-collapse-tags="3" :loading="directoryLoading" :disabled="disabled" placeholder="搜索并选择成员、角色或部门" @change="targetsChanged(index, $event)">
                  <el-option-group v-for="group in directoryGroups" :key="group.type" :label="group.label">
                    <el-option v-for="entry in group.items" :key="`${entry.type}:${entry.id}`" :label="entry.name" :value="`${entry.type}:${entry.id}`" :disabled="!entry.available">
                      <span>{{ entry.name }}</span><small class="directory-description">{{ entry.description }}</small>
                    </el-option>
                  </el-option-group>
                </el-select>
              </label>
              <el-button text type="danger" :disabled="disabled || stages.length <= 1" title="删除本级" @click="removeStage(index)"><el-icon><Delete /></el-icon></el-button>
            </div>
            <div class="policy-row">
              <span>多人审批方式</span>
              <el-radio-group :model-value="stage.decisionPolicy?.mode || 'ANY'" :disabled="disabled" @change="policyModeChanged(index, $event)">
                <el-radio-button value="ANY">任一人通过</el-radio-button>
                <el-radio-button value="ALL">所有人通过</el-radio-button>
                <el-radio-button value="N_OF_M">达到人数</el-radio-button>
              </el-radio-group>
              <el-input-number v-if="stage.decisionPolicy?.mode === 'N_OF_M'" :model-value="stage.decisionPolicy.requiredApprovals" :min="1" :max="500" :disabled="disabled" @change="updatePolicy(index, {requiredApprovals: $event})" />
              <el-checkbox :model-value="!!stage.decisionPolicy?.rejectOnAny" :disabled="disabled" @change="updatePolicy(index, {rejectOnAny: $event})">任一人拒绝即结束</el-checkbox>
            </div>
            <p class="policy-summary">{{ policySummary(stage) }}</p>
            <details class="stage-advanced">
              <summary>本级高级设置</summary>
              <div class="stage-advanced-grid">
                <label v-if="hasDepartment(stage)"><span>部门范围</span><el-checkbox :model-value="stageIncludesChildren(stage)" :disabled="disabled" @change="setStageIncludeChildren(index, $event)">包含子部门成员</el-checkbox></label>
                <label><span>本级最长等待</span><el-checkbox :model-value="!!stage.deadline" :disabled="disabled" @change="toggleStageDeadline(index, $event)">单独设置</el-checkbox></label>
                <div v-if="stage.deadline" class="duration-row"><el-input-number :model-value="stage.deadline.duration" :min="1" :max="durationMaximum(stage.deadline.unit)" :disabled="disabled" @change="updateStageDeadline(index, {duration: $event})" /><el-select :model-value="stage.deadline.unit" :disabled="disabled" @change="updateStageDeadlineUnit(index, $event)"><el-option label="分钟" value="MINUTE" /><el-option label="小时" value="HOUR" /><el-option label="天" value="DAY" /></el-select></div>
                <label class="wide-stage"><span>备用审批人</span><el-select :model-value="fallbackTargetKeys(stage)" multiple filterable remote :remote-method="searchDirectory" collapse-tags :max-collapse-tags="3" :disabled="disabled" placeholder="主要审批人不可用时启用（可选）" @change="fallbackTargetsChanged(index, $event)"><el-option-group v-for="group in directoryGroups" :key="`fallback-${group.type}`" :label="group.label"><el-option v-for="entry in group.items" :key="`fallback-${entry.type}:${entry.id}`" :label="entry.name" :value="`${entry.type}:${entry.id}`" :disabled="!entry.available" /></el-option-group></el-select></label>
                <label v-if="hasFallbackDepartment(stage)"><span>备用部门范围</span><el-checkbox :model-value="fallbackIncludesChildren(stage)" :disabled="disabled" @change="setFallbackIncludeChildren(index, $event)">包含子部门成员</el-checkbox></label>
              </div>
            </details>
          </div>
          <div class="stage-order">
            <el-button text :disabled="disabled || stages.length >= 20" title="复制本级" @click="copyStage(index)">复制</el-button>
            <el-button text :disabled="disabled || index === 0" title="上移" @click="moveStage(index, -1)">↑</el-button>
            <el-button text :disabled="disabled || index === stages.length - 1" title="下移" @click="moveStage(index, 1)">↓</el-button>
          </div>
        </article>
        <p v-if="directoryError" class="approval-error">{{ directoryError }}</p>
      </section>

      <section class="approval-section">
        <div class="section-title"><b>3. 审批后做什么</b><small>简单流程直接结束；需要分别处理时使用结果分支</small></div>
        <el-radio-group :model-value="resultMode" :disabled="disabled" @change="resultModeChanged">
          <el-radio-button value="SIMPLE">通过后继续，拒绝或超时结束</el-radio-button>
          <el-radio-button value="BRANCH">按通过、拒绝、超时分别处理</el-radio-button>
        </el-radio-group>
        <div v-if="resultMode === 'BRANCH'" class="result-branches">
          <label v-for="branch in resultBranches" :key="branch.port"><span>{{ branch.label }}</span>
            <el-select :model-value="branchTargets[branch.port] || ''" filterable clearable :disabled="disabled" placeholder="选择下一节点" @change="$emit('update:branch-target', {port: branch.port, target: $event || ''})">
              <el-option v-for="node in targetOptions" :key="node.id" :label="node.name" :value="node.id" />
            </el-select>
          </label>
        </div>
      </section>

      <el-alert v-if="issues.length" type="warning" :closable="false" show-icon :title="issues[0]" :description="issues.length > 1 ? `另外还有 ${issues.length - 1} 项未完成` : ''" />

      <el-collapse class="approval-advanced">
        <el-collapse-item name="advanced">
          <template #title><span><strong>高级选项</strong><small>通常保持默认即可</small></span></template>
          <div class="advanced-grid">
            <label><span>最长等待</span><div class="duration-row"><el-input-number :model-value="deadline.duration" :min="1" :max="deadlineMaximum" :disabled="disabled" @change="updateDeadline({duration: $event})" /><el-select :model-value="deadline.unit" :disabled="disabled" @change="deadlineUnitChanged"><el-option label="分钟" value="MINUTE" /><el-option label="小时" value="HOUR" /><el-option label="天" value="DAY" /></el-select></div></label>
            <label><span>期限计算</span><el-select :model-value="deadline.calendar || 'CALENDAR_DAY'" :disabled="disabled" @change="updateDeadline({calendar: $event})"><el-option label="按自然日" value="CALENDAR_DAY" /><el-option label="按工作日（跳过周末）" value="BUSINESS_DAY" /></el-select></label>
            <label><span>时区</span><el-input :model-value="deadline.timezone || 'TENANT'" :disabled="disabled" placeholder="TENANT 或 Asia/Shanghai" @input="updateDeadline({timezone: $event})" /></label>
            <label><span>到期前提醒</span><el-switch :model-value="!!reminder.enabled" :disabled="disabled" @change="updateReminder({enabled: $event})" /></label>
            <label v-if="reminder.enabled"><span>提前多久提醒</span><div class="duration-row"><el-input-number :model-value="reminder.beforeDuration" :min="1" :max="durationMaximum(reminder.beforeUnit)" :disabled="disabled" @change="updateReminder({beforeDuration: $event})" /><el-select :model-value="reminder.beforeUnit" :disabled="disabled" @change="updateReminder({beforeUnit: $event})"><el-option label="分钟" value="MINUTE" /><el-option label="小时" value="HOUR" /><el-option label="天" value="DAY" /></el-select></div></label>
            <label><span>审批超时后</span><el-select :model-value="expirationPolicy.action" :disabled="disabled" @change="updateExpiration({action: $event})"><el-option label="按超时结果处理" value="EXPIRE" /><el-option label="自动转交" value="REASSIGN" /></el-select></label>
            <label v-if="expirationPolicy.action === 'REASSIGN'" class="wide"><span>转交给</span><el-select :model-value="expirationTargetKeys" multiple filterable remote :remote-method="searchDirectory" collapse-tags :max-collapse-tags="3" :disabled="disabled" placeholder="选择转交成员、角色或部门" @change="expirationTargetsChanged"><el-option-group v-for="group in directoryGroups" :key="`expire-${group.type}`" :label="group.label"><el-option v-for="entry in group.items" :key="`expire-${entry.type}:${entry.id}`" :label="entry.name" :value="`${entry.type}:${entry.id}`" :disabled="!entry.available" /></el-option-group></el-select></label>
            <label v-if="expirationPolicy.action === 'REASSIGN' && expirationHasDepartment"><span>转交部门范围</span><el-checkbox :model-value="expirationIncludesChildren" :disabled="disabled" @change="setExpirationIncludeChildren">包含子部门成员</el-checkbox></label>
            <label v-if="expirationPolicy.action === 'REASSIGN'"><span>最多转交次数</span><el-input-number :model-value="expirationPolicy.maxEscalations" :min="1" :max="10" :disabled="disabled" @change="updateExpiration({maxEscalations: $event})" /></label>
            <label><span>审批人可以是发起人</span><el-switch :model-value="!!options.allowSelfApproval" :disabled="disabled" @change="updateOptions({allowSelfApproval: $event})" /></label>
            <label><span>通过时必须填写意见</span><el-switch :model-value="!!options.requireApproveComment" :disabled="disabled" @change="updateOptions({requireApproveComment: $event})" /></label>
            <label><span>拒绝时必须填写意见</span><el-switch :model-value="options.requireRejectComment !== false" :disabled="disabled" @change="updateOptions({requireRejectComment: $event})" /></label>
          </div>
        </el-collapse-item>
      </el-collapse>
    <el-dialog v-model="previewOpen" title="审批单预览" width="620px" append-to-body>
      <div class="approval-preview">
        <small>实际运行时，标题和说明中的变量会替换为当次工作流数据。</small>
        <h3>{{ content.titleTemplate || '请审批当前工作流任务' }}</h3>
        <p v-if="content.descriptionTemplate">{{ content.descriptionTemplate }}</p>
        <dl v-if="fields.length">
          <template v-for="field in fields" :key="field.key">
            <dt>{{ field.label || '未命名字段' }}</dt><dd>{{ previewValue(field) }}</dd>
          </template>
        </dl>
        <div v-else class="compact-empty">未配置展示字段</div>
      </div>
      <template #footer><el-button type="primary" @click="previewOpen = false">知道了</el-button></template>
    </el-dialog>
  </section>
</template>

<script>
import {Delete} from '@element-plus/icons-vue'
import {listWorkflowApprovalDirectory} from '@/api/ai/workflow'

const clone = value => JSON.parse(JSON.stringify(value))
const stageId = () => `stage_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`

export default {
  name: 'WorkflowApprovalEditor',
  components: {Delete},
  props: {
    config: {type: Object, default: () => ({})},
    sourceGroups: {type: Array, default: () => []},
    targetOptions: {type: Array, default: () => []},
    branchTargets: {type: Object, default: () => ({})},
    disabled: {type: Boolean, default: false}
  },
  emits: ['update:config', 'update:branch-target'],
  data() {
    return {directory: [], directoryLoading: false, directoryError: '', directorySearchTimer: null, previewOpen: false,
      displayTypes: [
        {label: '自动识别', value: 'AUTO'}, {label: '文本', value: 'TEXT'}, {label: '数字', value: 'NUMBER'},
        {label: '金额', value: 'MONEY'}, {label: '日期', value: 'DATE'}, {label: '日期时间', value: 'DATETIME'},
        {label: '是/否', value: 'BOOLEAN'}, {label: '对象', value: 'OBJECT'}, {label: '数组', value: 'ARRAY'},
        {label: '链接', value: 'LINK'}, {label: '附件', value: 'ATTACHMENT'}, {label: 'JSON', value: 'JSON'}
      ],
      maskTypes: [
        {label: '完整显示', value: 'NONE'}, {label: '部分隐藏', value: 'PARTIAL'}, {label: '手机号', value: 'PHONE'},
        {label: '邮箱', value: 'EMAIL'}, {label: '证件号', value: 'ID_CARD'}, {label: '自定义', value: 'CUSTOM'},
        {label: '完全隐藏', value: 'HIDDEN'}
      ], resultBranches: [
      {port: 'approved', label: '通过后进入'}, {port: 'rejected', label: '拒绝后进入'}, {port: 'expired', label: '超时后进入'}
    ]}
  },
  computed: {
    content() { return this.config.content || {titleTemplate: '', descriptionTemplate: '', fields: []} },
    fields() { return Array.isArray(this.content.fields) ? this.content.fields : [] },
    stages() { return Array.isArray(this.config.stages) ? this.config.stages : [] },
    resultMode() { return this.config.resultPolicy?.mode || 'SIMPLE' },
    deadline() { return this.config.deadline || {duration: 24, unit: 'HOUR', calendar: 'CALENDAR_DAY', timezone: 'TENANT'} },
    reminder() { return this.config.reminder || {enabled: false, beforeDuration: 1, beforeUnit: 'HOUR'} },
    expirationPolicy() { return this.config.expirationPolicy || {action: 'EXPIRE', targets: [], maxEscalations: 1} },
    expirationTargetKeys() { return this.keysFromTargets(this.expirationPolicy.targets || []) },
    expirationHasDepartment() { return (this.expirationPolicy.targets || []).some(target => target.type === 'DEPARTMENT') },
    expirationIncludesChildren() { return (this.expirationPolicy.targets || []).some(target => target.type === 'DEPARTMENT' && target.includeChildren) },
    options() { return this.config.options || {} },
    deadlineMaximum() { return this.deadline.unit === 'MINUTE' ? 525600 : this.deadline.unit === 'HOUR' ? 8760 : 365 },
    recommendedFields() {
      const existing = new Set(this.fields.map(field => field.source?.expression).filter(Boolean))
      return this.sourceGroups.flatMap(group => group.options || [])
        .filter(option => option.expression && !existing.has(option.expression))
        .slice(0, Math.max(0, 50 - this.fields.length))
    },
    directoryGroups() {
      const items = [...this.directory]
      const known = new Set(items.map(item => `${item.type}:${item.id}`))
      const selected = this.stages.flatMap(stage => [
        ...this.targetKeys(stage), ...this.fallbackTargetKeys(stage)
      ]).concat(this.expirationTargetKeys)
      selected.forEach(key => {
        if (!known.has(key)) {
          const split = key.indexOf(':')
          items.push({type: key.slice(0, split), id: key.slice(split + 1), name: `已不可用（${key.slice(split + 1)}）`, description: '请重新选择', available: false})
          known.add(key)
        }
      })
      return [
        {type: 'USER', label: '用户', items: items.filter(item => item.type === 'USER')},
        {type: 'ROLE', label: '角色', items: items.filter(item => item.type === 'ROLE')},
        {type: 'DEPARTMENT', label: '部门', items: items.filter(item => item.type === 'DEPARTMENT')}
      ].filter(group => group.items.length)
    },
    issues() {
      const result = []
      if (!String(this.content.titleTemplate || '').trim()) result.push('请填写审批标题')
      if (!this.stages.length) result.push('请添加至少一个审批级别')
      this.stages.forEach((stage, index) => {
        if (!String(stage.name || '').trim()) result.push(`第 ${index + 1} 级缺少名称`)
        if (!this.targetKeys(stage).length) result.push(`第 ${index + 1} 级还没有选择审批人`)
        if (stage.decisionPolicy?.mode === 'N_OF_M' && Number(stage.decisionPolicy.requiredApprovals || 0) < 1) result.push(`第 ${index + 1} 级的通过人数无效`)
        if (stage.deadline && Number(stage.deadline.duration || 0) < 1) result.push(`第 ${index + 1} 级的等待期限无效`)
      })
      this.fields.forEach((field, index) => {
        if (!String(field.label || '').trim()) result.push(`审批单第 ${index + 1} 个字段缺少显示名称`)
        if (this.fieldSourceMode(field) === 'EXPRESSION' && !field.source?.expression) result.push(`审批单第 ${index + 1} 个字段还没有选择来源`)
        if (field.mask === 'CUSTOM' && !String(field.maskPattern || '').trim()) result.push(`审批单第 ${index + 1} 个字段缺少自定义脱敏规则`)
        else if (field.mask === 'CUSTOM' && !/^[#*]{1,256}$/.test(String(field.maskPattern || ''))) result.push(`审批单第 ${index + 1} 个字段的脱敏规则只能使用 # 和 *`)
      })
      if (this.expirationPolicy.action === 'REASSIGN' && !this.expirationTargetKeys.length) result.push('请选择审批超时后的转交对象')
      if (this.resultMode === 'BRANCH') this.resultBranches.forEach(branch => {
        if (!this.branchTargets[branch.port]) result.push(`${branch.label}尚未选择下一节点`)
      })
      return result
    }
  },
  created() { this.loadDirectory() },
  beforeUnmount() { clearTimeout(this.directorySearchTimer) },
  methods: {
    emit(config) { this.$emit('update:config', clone(config)) },
    async loadDirectory(keyword = '') {
      this.directoryLoading = true
      this.directoryError = ''
      try { const response = await listWorkflowApprovalDirectory(keyword); this.directory = response.data || [] }
      catch (error) { this.directoryError = error?.message || '审批人员目录加载失败' }
      finally { this.directoryLoading = false }
    },
    searchDirectory(keyword) {
      clearTimeout(this.directorySearchTimer)
      this.directorySearchTimer = setTimeout(() => this.loadDirectory(keyword || ''), 250)
    },
    newStage(number, targets = [], mode = 'ANY') { return {id: stageId(), name: `第 ${number} 级审批`, targets, decisionPolicy: {mode: ['ANY', 'ALL', 'N_OF_M'].includes(mode) ? mode : 'ANY', requiredApprovals: 1, rejectOnAny: false}, deadline: null} },
    updateContent(patch) { this.emit({...this.config, content: {...this.content, ...patch, fields: this.fields}}) },
    addField() { const used = new Set(this.fields.map(field => field.key)); let number = this.fields.length + 1; while (used.has(`field_${number}`)) number += 1; this.updateContent({fields: [...this.fields, {key: `field_${number}`, label: '', source: {expression: ''}, displayType: 'TEXT', mask: 'NONE'}]}) },
    addRecommendedFields() {
      const used = new Set(this.fields.map(field => field.key))
      const added = this.recommendedFields.slice(0, 10).map((option, index) => {
        const raw = String(option.key || option.label || `field_${this.fields.length + index + 1}`).replace(/[^A-Za-z0-9_.-]/g, '_')
        let key = /^[A-Za-z]/.test(raw) ? raw : `field_${raw}`
        let suffix = 2
        while (used.has(key)) key = `${raw}_${suffix++}`
        used.add(key)
        const type = String(option.type || '').toLowerCase()
        const displayType = type === 'number' || type === 'integer' ? 'NUMBER' : type === 'boolean' ? 'BOOLEAN' : type === 'array' ? 'ARRAY' : type === 'object' ? 'OBJECT' : 'AUTO'
        return {key, label: option.label || key, source: {expression: option.expression}, displayType, mask: 'NONE'}
      })
      this.updateContent({fields: [...this.fields, ...added]})
    },
    fieldSourceMode(field) { return Object.prototype.hasOwnProperty.call(field.source || {}, 'value') ? 'VALUE' : 'EXPRESSION' },
    fieldSourceModeChanged(index, mode) { this.updateField(index, {source: mode === 'VALUE' ? {value: ''} : {expression: ''}}) },
    previewValue(field) {
      if (this.fieldSourceMode(field) === 'VALUE') return String(field.source?.value ?? '') || '—'
      const expression = field.source?.expression || ''
      const option = this.sourceGroups.flatMap(group => group.options || []).find(item => item.expression === expression)
      return option ? `运行时显示：${option.label}` : expression ? `运行时读取：${expression}` : '尚未选择数据来源'
    },
    updateField(index, patch) { const fields = clone(this.fields); fields[index] = {...fields[index], ...patch}; fields[index].key = fields[index].key || `field_${index + 1}`; this.updateContent({fields}) },
    removeField(index) { this.updateContent({fields: this.fields.filter((_, itemIndex) => itemIndex !== index)}) },
    moveField(index, offset) { const fields = clone(this.fields); const [field] = fields.splice(index, 1); fields.splice(index + offset, 0, field); this.updateContent({fields}) },
    addStage() { this.emit({...this.config, stages: [...this.stages, this.newStage(this.stages.length + 1)]}) },
    copyStage(index) { const stage = clone(this.stages[index]); stage.id = stageId(); stage.name = `${stage.name || `第 ${index + 1} 级审批`}（副本）`; const stages = clone(this.stages); stages.splice(index + 1, 0, stage); this.emit({...this.config, stages}) },
    updateStage(index, patch) { const stages = clone(this.stages); stages[index] = {...stages[index], ...patch}; this.emit({...this.config, stages}) },
    removeStage(index) { this.emit({...this.config, stages: this.stages.filter((_, itemIndex) => itemIndex !== index)}) },
    moveStage(index, offset) { const stages = clone(this.stages); const [stage] = stages.splice(index, 1); stages.splice(index + offset, 0, stage); this.emit({...this.config, stages}) },
    keysFromTargets(targets) { return (targets || []).flatMap(target => (target.ids || []).map(id => `${target.type}:${id}`)) },
    targetKeys(stage) { return this.keysFromTargets(stage.targets) },
    fallbackTargetKeys(stage) { return this.keysFromTargets(stage.fallbackTargets) },
    groupedTargets(keys, includeChildren = false) {
      const grouped = keys.reduce((result, key) => { const split = key.indexOf(':'); const type = key.slice(0, split); const id = key.slice(split + 1); (result[type] ||= []).push(id); return result }, {})
      return Object.entries(grouped).map(([type, ids]) => ({type, ids, includeChildren: type === 'DEPARTMENT' && includeChildren}))
    },
    targetsChanged(index, keys) {
      this.updateStage(index, {targets: this.groupedTargets(keys, this.stageIncludesChildren(this.stages[index]))})
    },
    fallbackTargetsChanged(index, keys) { this.updateStage(index, {fallbackTargets: this.groupedTargets(keys, this.fallbackIncludesChildren(this.stages[index]))}) },
    hasDepartment(stage) { return (stage.targets || []).some(target => target.type === 'DEPARTMENT') },
    hasFallbackDepartment(stage) { return (stage.fallbackTargets || []).some(target => target.type === 'DEPARTMENT') },
    stageIncludesChildren(stage) { return (stage.targets || []).some(target => target.type === 'DEPARTMENT' && target.includeChildren) },
    fallbackIncludesChildren(stage) { return (stage.fallbackTargets || []).some(target => target.type === 'DEPARTMENT' && target.includeChildren) },
    setStageIncludeChildren(index, value) { this.updateStage(index, {targets: (this.stages[index].targets || []).map(target => target.type === 'DEPARTMENT' ? {...target, includeChildren: !!value} : target)}) },
    setFallbackIncludeChildren(index, value) { this.updateStage(index, {fallbackTargets: (this.stages[index].fallbackTargets || []).map(target => target.type === 'DEPARTMENT' ? {...target, includeChildren: !!value} : target)}) },
    toggleStageDeadline(index, enabled) { this.updateStage(index, {deadline: enabled ? {duration: 8, unit: 'HOUR', calendar: 'CALENDAR_DAY', timezone: 'TENANT'} : null}) },
    updateStageDeadline(index, patch) { this.updateStage(index, {deadline: {...this.stages[index].deadline, ...patch}}) },
    updateStageDeadlineUnit(index, unit) { const deadline = this.stages[index].deadline || {}; this.updateStageDeadline(index, {unit, duration: Math.min(Number(deadline.duration || 1), this.durationMaximum(unit))}) },
    durationMaximum(unit) { return unit === 'MINUTE' ? 525600 : unit === 'HOUR' ? 8760 : 365 },
    policyModeChanged(index, mode) { this.updatePolicy(index, {mode, requiredApprovals: mode === 'N_OF_M' ? Math.max(1, Number(this.stages[index].decisionPolicy?.requiredApprovals || 1)) : 1}) },
    updatePolicy(index, patch) { this.updateStage(index, {decisionPolicy: {...this.stages[index].decisionPolicy, ...patch}}) },
    policySummary(stage) { const count = this.targetKeys(stage).length; const policy = stage.decisionPolicy || {}; const rule = policy.mode === 'ALL' ? '所有实际审批人都通过' : policy.mode === 'N_OF_M' ? `至少 ${policy.requiredApprovals || 1} 人通过` : '任一实际审批人通过'; return `${count ? `已选择 ${count} 个用户/角色/部门范围；` : ''}${rule}${policy.rejectOnAny ? '；任一人拒绝即结束' : ''}` },
    async resultModeChanged(mode) {
      if (this.resultMode === 'BRANCH' && mode === 'SIMPLE' && Object.values(this.branchTargets || {}).some(Boolean)) {
        try { await this.$confirm('切换后将移除已连接的拒绝和超时分支，是否继续？', '确认切换', {type: 'warning', confirmButtonText: '继续切换', cancelButtonText: '保留分支'}) }
        catch (_) { return }
      }
      this.emit({...this.config, resultPolicy: {...this.config.resultPolicy, mode, rejectAction: mode === 'BRANCH' ? 'BRANCH' : 'END', expireAction: mode === 'BRANCH' ? 'BRANCH' : 'END'}})
    },
    updateDeadline(patch) { this.emit({...this.config, deadline: {...this.deadline, ...patch}}) },
    deadlineUnitChanged(unit) { const maximum = unit === 'MINUTE' ? 525600 : unit === 'HOUR' ? 8760 : 365; this.updateDeadline({unit, duration: Math.min(Number(this.deadline.duration || 1), maximum)}) },
    updateReminder(patch) { this.emit({...this.config, reminder: {...this.reminder, ...patch}}) },
    updateExpiration(patch) { this.emit({...this.config, expirationPolicy: {...this.expirationPolicy, ...patch}}) },
    expirationTargetsChanged(keys) { this.updateExpiration({targets: this.groupedTargets(keys, this.expirationIncludesChildren)}) },
    setExpirationIncludeChildren(value) { this.updateExpiration({targets: (this.expirationPolicy.targets || []).map(target => target.type === 'DEPARTMENT' ? {...target, includeChildren: !!value} : target)}) },
    updateOptions(patch) { this.emit({...this.config, options: {...this.options, ...patch}}) }
  }
}
</script>

<style scoped>
.approval-editor{display:grid;gap:16px;color:var(--el-text-color-primary)}.approval-heading,.section-title,.field-heading,.stage-title{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.approval-heading div,.section-title,.field-heading span{display:flex;flex-direction:column;gap:4px}.field-heading .inline-actions{flex-direction:row}.approval-heading-actions,.inline-actions,.field-order{display:flex;align-items:center;gap:8px}.approval-heading small,.section-title small,.field-heading small,.approval-advanced small{color:var(--el-text-color-secondary);font-weight:400}.approval-section{padding:16px;border:1px solid var(--el-border-color-light);border-radius:14px;background:var(--el-bg-color)}.content-grid{display:grid;grid-template-columns:1fr;gap:12px;margin-top:14px}.content-grid label,.stage-row label,.result-branches label,.advanced-grid label,.stage-advanced-grid label{display:grid;gap:6px}.content-grid label>span,.stage-row label>span,.result-branches label>span,.advanced-grid label>span,.stage-advanced-grid label>span{font-size:13px;font-weight:600}.field-heading{margin:16px 0 8px}.compact-empty{padding:12px;border:1px dashed var(--el-border-color);border-radius:10px;color:var(--el-text-color-secondary);text-align:center}.approval-field-row{display:grid;grid-template-columns:minmax(110px,.65fr) 110px minmax(210px,1.35fr) 105px 105px minmax(70px,.5fr) 36px;gap:8px;margin-top:8px}.approval-stage-card{display:flex;gap:12px;padding:14px;margin-top:12px;border:1px solid var(--el-border-color);border-radius:12px;background:var(--el-fill-color-blank)}.stage-number{display:grid;place-items:center;width:30px;height:30px;border-radius:10px;background:var(--el-color-primary-light-9);color:var(--el-color-primary);font-weight:700}.stage-main{flex:1;min-width:0}.stage-row{display:grid;grid-template-columns:minmax(160px,.7fr) minmax(280px,1.6fr) 36px;gap:10px}.policy-row{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-top:14px}.policy-row>span{font-size:13px;font-weight:600}.policy-summary{margin:9px 0 0;color:var(--el-text-color-secondary);font-size:12px}.stage-order{display:flex;flex-direction:column;align-items:center}.directory-description{float:right;margin-left:18px;color:var(--el-text-color-secondary)}.stage-advanced{margin-top:10px;border-top:1px solid var(--el-border-color-lighter);padding-top:10px}.stage-advanced summary{cursor:pointer;color:var(--el-color-primary);font-size:13px}.stage-advanced-grid{display:grid;grid-template-columns:repeat(2,minmax(220px,1fr));gap:12px;margin-top:12px}.wide-stage{grid-column:1/-1}.result-branches{display:grid;grid-template-columns:repeat(3,minmax(180px,1fr));gap:12px;margin-top:14px}.approval-advanced :deep(.el-collapse-item__header){padding:0 14px;border:1px solid var(--el-border-color-light);border-radius:10px;background:var(--el-fill-color-extra-light)}.approval-advanced :deep(.el-collapse-item__header span){display:flex;flex-direction:column;align-items:flex-start;line-height:1.4}.advanced-grid{display:grid;grid-template-columns:repeat(2,minmax(220px,1fr));gap:14px;padding:14px}.advanced-grid .wide{grid-column:1/-1}.duration-row{display:grid;grid-template-columns:1fr 100px;gap:8px}.approval-error{color:var(--el-color-danger)}.approval-preview>small{color:var(--el-text-color-secondary)}.approval-preview h3{margin:12px 0 6px}.approval-preview>p{white-space:pre-wrap}.approval-preview dl{display:grid;grid-template-columns:130px 1fr;margin:16px 0 0;border:1px solid var(--el-border-color-light);border-radius:10px;overflow:hidden}.approval-preview dt,.approval-preview dd{margin:0;padding:10px 12px;border-bottom:1px solid var(--el-border-color-lighter);overflow-wrap:anywhere}.approval-preview dt{background:var(--el-fill-color-light);font-weight:600}
@media(max-width:900px){.approval-field-row,.stage-row,.result-branches,.advanced-grid{grid-template-columns:1fr}.stage-order{flex-direction:row}}
</style>
