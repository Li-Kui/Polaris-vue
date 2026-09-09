<template>
  <section class="artifact-editor">
    <header class="artifact-editor__heading">
      <div>
        <strong>文件设置</strong>
        <small>系统会自动脱敏并保存到当前平台的私有空间</small>
      </div>
      <el-tag type="success" size="small" effect="plain">私有产物</el-tag>
    </header>

    <div class="artifact-format-grid">
      <button
        v-for="option in formatOptions"
        :key="option.value"
        type="button"
        :class="['artifact-format-card', {active: normalized.format === option.value}]"
        :disabled="disabled"
        @click="change({format: option.value})"
      >
        <strong>{{ option.label }}</strong>
        <small>{{ option.description }}</small>
        <span>{{ option.suffix }}</span>
      </button>
    </div>

    <el-form-item label="文件名" required>
      <el-input
        :model-value="normalized.fileNameTemplate"
        maxlength="180"
        :disabled="disabled"
        placeholder="例如：客户分析-{{date}}"
        @input="change({fileNameTemplate: $event})"
      >
        <template #append>.{{ extension }}</template>
      </el-input>
      <small class="artifact-field-hint">
        预览：{{ fileNamePreview }}。可使用日期、执行 ID、节点运行 ID，扩展名由系统维护。
      </small>
      <div class="artifact-token-list">
        <el-button
          v-for="token in tokens"
          :key="token.value"
          size="small"
          text
          :disabled="disabled"
          @click="appendToken(token.value)"
        >+ {{ token.label }}</el-button>
      </div>
    </el-form-item>

    <el-form-item label="保留时间">
      <el-radio-group :model-value="retentionValue" :disabled="disabled" @change="retentionChanged">
        <el-radio-button value="EXECUTION">跟随工作流</el-radio-button>
        <el-radio-button value="7">7 天</el-radio-button>
        <el-radio-button value="30">30 天</el-radio-button>
        <el-radio-button value="90">90 天</el-radio-button>
      </el-radio-group>
      <small class="artifact-field-hint">
        {{ normalized.retentionMode === 'EXECUTION'
          ? '工作流运行期间不会过期，执行结束后默认保留 30 天。'
          : `工作流运行期间不会过期，执行结束后保留 ${normalized.retentionDays} 天。` }}
      </small>
    </el-form-item>

    <el-alert
      title="产物只能由有权查看本次工作流执行的用户下载；不生成公开链接。"
      type="info"
      :closable="false"
      show-icon
    />
  </section>
</template>

<script>
import {artifactExtension, artifactFileNamePreview, normalizeArtifactConfig} from './workflowArtifact'

export default {
  name: 'WorkflowArtifactEditor',
  props: {
    config: {type: Object, default: () => ({})},
    disabled: {type: Boolean, default: false}
  },
  emits: ['update:config'],
  data() {
    return {
      formatOptions: [
        {value: 'JSON', label: 'JSON', description: '保留对象和数组结构，推荐用于后续处理', suffix: '.json'},
        {value: 'TEXT', label: '纯文本', description: '便于直接阅读，非文本内容会转成易读 JSON', suffix: '.txt'}
      ],
      tokens: [
        {label: '日期', value: '{{date}}'},
        {label: '执行 ID', value: '{{executionId}}'},
        {label: '节点运行 ID', value: '{{nodeRunId}}'}
      ]
    }
  },
  computed: {
    normalized() { return normalizeArtifactConfig(this.config) },
    extension() { return artifactExtension(this.normalized.format) },
    fileNamePreview() { return artifactFileNamePreview(this.normalized) },
    retentionValue() {
      return this.normalized.retentionMode === 'EXECUTION'
        ? 'EXECUTION' : String(this.normalized.retentionDays)
    }
  },
  methods: {
    change(patch) {
      this.$emit('update:config', normalizeArtifactConfig({...this.normalized, ...patch}))
    },
    appendToken(token) {
      const separator = this.normalized.fileNameTemplate.endsWith('-') ? '' : '-'
      this.change({fileNameTemplate: `${this.normalized.fileNameTemplate}${separator}${token}`})
    },
    retentionChanged(value) {
      if (value === 'EXECUTION') {
        this.change({retentionMode: 'EXECUTION', retentionDays: 30})
      } else {
        this.change({retentionMode: 'DAYS', retentionDays: Number(value)})
      }
    }
  }
}
</script>

<style scoped>
.artifact-editor {
  display: grid;
  gap: 16px;
}

.artifact-editor__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.artifact-editor__heading > div {
  display: grid;
  gap: 3px;
}

.artifact-editor__heading small,
.artifact-field-hint {
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  line-height: 1.6;
}

.artifact-format-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.artifact-format-card {
  position: relative;
  min-height: 88px;
  padding: 14px;
  display: grid;
  gap: 6px;
  text-align: left;
  border: 1px solid var(--workflow-border, var(--el-border-color));
  border-radius: 10px;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
  cursor: pointer;
}

.artifact-format-card.active {
  border-color: var(--workflow-primary, var(--el-color-primary));
  box-shadow: 0 0 0 2px var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.artifact-format-card small {
  padding-right: 42px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  line-height: 1.5;
}

.artifact-format-card span {
  position: absolute;
  top: 12px;
  right: 12px;
  color: var(--workflow-primary, var(--el-color-primary));
  font-family: ui-monospace, monospace;
  font-size: 11px;
}

.artifact-token-list {
  width: 100%;
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

@media (max-width: 760px) {
  .artifact-format-grid { grid-template-columns: 1fr; }
}
</style>
