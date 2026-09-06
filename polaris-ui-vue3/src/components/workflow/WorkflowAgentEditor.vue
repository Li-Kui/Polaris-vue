<template>
  <div class="agent-editor">
    <section class="agent-task-heading">
      <div>
        <span class="agent-eyebrow">当前智能体</span>
        <strong>{{ selectedAgent?.name || '尚未选择智能体' }}</strong>
        <small>{{ agentDescription }}</small>
        <span v-if="toolAccess.hasTools" class="agent-capability">
          <el-tag
            :type="toolAccess.capabilityType"
            size="small"
            effect="plain"
          >{{ toolAccess.capabilityLabel }}</el-tag>
          <el-tag
            v-if="selectedAgent.attributes.hasExternalTools"
            type="warning"
            size="small"
            effect="plain"
          >调用外部服务</el-tag>
          <small>{{ toolExecutionLabel }}</small>
        </span>
      </div>
      <el-button type="primary" plain :disabled="disabled" @click="$emit('select-agent')">
        {{ selectedAgent ? '更换智能体' : '选择智能体' }}
      </el-button>
    </section>

    <el-alert
      v-if="!selectedAgent"
      title="请先完成第 1 步，选择一个可用的智能体。"
      type="warning"
      :closable="false"
      show-icon
    />

    <section class="agent-task-card">
      <header>
        <div>
          <strong>本次任务</strong>
          <small>只补充当前工作流里的具体要求，不会修改智能体本身</small>
        </div>
        <el-tag v-if="taskValue" type="success" size="small" effect="plain">已填写</el-tag>
        <el-tag v-else type="info" size="small" effect="plain">可选</el-tag>
      </header>
      <el-input
        :model-value="taskValue"
        type="textarea"
        :rows="6"
        maxlength="4000"
        show-word-limit
        resize="vertical"
        aria-label="本次任务要求"
        :disabled="disabled"
        placeholder="例如：审核订单信息，说明是否可以通过；信息不足时明确列出需要补充的内容。"
        @update:model-value="updateTask"
      />
      <div class="task-shortcuts" aria-label="常用任务要求">
        <span>快捷补充</span>
        <button v-for="item in shortcuts" :key="item" type="button" :disabled="disabled" @click="appendShortcut(item)">
          {{ item }}
        </button>
      </div>
    </section>

    <section class="agent-input-summary">
      <span class="summary-icon"><el-icon><Connection /></el-icon></span>
      <span class="summary-copy">
        <strong>任务输入</strong>
        <small>{{ mappingCount ? `已配置 ${mappingCount} 个输入来源` : '尚未配置输入；也可以只使用上面的任务要求运行' }}</small>
      </span>
      <el-button plain :disabled="disabled" @click="$emit('configure-input')">
        {{ mappingCount ? '检查输入' : '配置输入' }}
      </el-button>
    </section>

    <section :class="['agent-readiness', `is-${configuration.tone}`]" aria-live="polite">
      <el-icon><CircleCheck v-if="!configuration.issues.length" /><WarningFilled v-else /></el-icon>
      <div>
        <strong>{{ configuration.label }}</strong>
        <small v-if="configuration.issues.length">{{ configuration.issues[0] }}</small>
        <small v-else>配置完整，可进入第 3 步检查输入并试运行。</small>
      </div>
    </section>

    <WorkflowDisclosureCard
      name="agent-runtime"
      title="运行限制"
      description="一般无需修改，节点仍受工作流总时限约束"
    >
      <el-form-item v-if="toolAccess.hasInternalReadTools" label="使用只读工具">
        <el-switch
          :model-value="allowInternalReadTools"
          :disabled="disabled || !toolAccess.internalReadEnabled"
          aria-label="使用内部只读工具"
          inline-prompt
          active-text="开"
          inactive-text="关"
          @change="updateToolAccess"
        />
        <small class="runtime-hint">
          {{ toolAccess.internalReadEnabled
            ? `只读取当前账号有权访问的内部数据；每次最多调用 ${toolAccess.callLimit} 次，单个结果最多 ${toolAccess.resultLimitChars.toLocaleString()} 个字符。`
            : '当前身份没有可用的内部只读工具，或系统尚未启用此能力。' }}
        </small>
      </el-form-item>
      <el-form-item label="最长等待时间">
        <el-select
          :model-value="waitSeconds"
          :disabled="disabled"
          aria-label="最长等待时间"
          style="width: 100%"
          @change="updateWaitSeconds"
        >
          <el-option :value="60" label="1 分钟" />
          <el-option :value="120" label="2 分钟" />
          <el-option :value="300" label="5 分钟（推荐）" />
          <el-option :value="600" label="10 分钟" />
        </el-select>
        <small class="runtime-hint">超过时间后停止等待，不会无限占用工作流运行资源。</small>
      </el-form-item>
    </WorkflowDisclosureCard>
  </div>
</template>

<script>
import {CircleCheck, Connection, WarningFilled} from '@element-plus/icons-vue'
import WorkflowDisclosureCard from './WorkflowDisclosureCard.vue'
import {agentConfigurationState, agentToolAccessView, normalizeAgentConfig} from './workflowAgent'

export default {
  name: 'WorkflowAgentEditor',
  components: {CircleCheck, Connection, WarningFilled, WorkflowDisclosureCard},
  props: {
    config: {type: Object, default: () => ({})},
    selectedAgent: {type: Object, default: null},
    inputMapping: {type: Object, default: () => ({})},
    testSucceeded: {type: Boolean, default: false},
    disabled: {type: Boolean, default: false}
  },
  emits: ['update:config', 'select-agent', 'configure-input'],
  data() {
    return {
      shortcuts: ['说明判断原因', '无法确定时明确说明', '结果保持简洁']
    }
  },
  computed: {
    normalizedConfig() {
      return normalizeAgentConfig(this.config)
    },
    taskValue() {
      return this.normalizedConfig.task
    },
    waitSeconds() {
      return this.normalizedConfig.maxWaitSeconds
    },
    allowInternalReadTools() {
      return this.normalizedConfig.allowInternalReadTools
    },
    mappingCount() {
      return Object.keys(this.inputMapping || {}).length
    },
    agentDescription() {
      if (!this.selectedAgent) return '智能体决定节点的基础角色、模型和可用能力'
      return this.selectedAgent.description || '使用智能体既有角色和能力完成当前任务'
    },
    toolAccess() {
      return agentToolAccessView(this.selectedAgent)
    },
    toolExecutionLabel() {
      if (!this.toolAccess.internalReadEnabled) return '当前节点不执行工具'
      return this.allowInternalReadTools
        ? '仅执行内部只读工具'
        : '本节点已关闭工具'
    },
    configuration() {
      return agentConfigurationState({
        selectedAgent: this.selectedAgent,
        config: this.normalizedConfig,
        inputMapping: this.inputMapping,
        testSucceeded: this.testSucceeded
      })
    }
  },
  methods: {
    updateTask(value) {
      this.$emit('update:config', {...this.normalizedConfig, task: value})
    },
    updateWaitSeconds(value) {
      this.$emit('update:config', {...this.normalizedConfig, maxWaitSeconds: Number(value)})
    },
    updateToolAccess(value) {
      this.$emit('update:config', {
        ...this.normalizedConfig,
        allowInternalReadTools: Boolean(value)
      })
    },
    appendShortcut(value) {
      const current = this.taskValue.trim()
      if (current.includes(value)) return
      this.updateTask(current ? `${current}\n${value}。` : `${value}。`)
    }
  }
}
</script>

<style scoped lang="scss">
.agent-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-task-heading,
.agent-input-summary,
.agent-readiness {
  display: flex;
  align-items: center;
  gap: 12px;
}

.agent-task-heading {
  justify-content: space-between;
  padding: 12px 14px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--workflow-muted, var(--el-fill-color-extra-light));

  > div {
    min-width: 0;
  }

  strong,
  small,
  .agent-eyebrow {
    display: block;
  }

  strong {
    margin-top: 2px;
    color: var(--workflow-text, var(--el-text-color-primary));
    font-size: 14px;
  }

  small {
    margin-top: 3px;
    color: var(--workflow-text-secondary, var(--el-text-color-secondary));
    font-size: 11px;
  }
}

.agent-eyebrow {
  color: var(--workflow-primary, var(--el-color-primary));
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.agent-capability {
  margin-top: 7px;
  display: flex;
  align-items: center;
  gap: 7px;

  small {
    margin: 0;
  }
}

.agent-task-card {
  padding: 14px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--workflow-surface, var(--el-bg-color));

  > header {
    margin-bottom: 10px;
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;

    strong,
    small {
      display: block;
    }

    strong {
      font-size: 13px;
    }

    small {
      margin-top: 3px;
      color: var(--workflow-text-secondary, var(--el-text-color-secondary));
      font-size: 11px;
    }
  }
}

.task-shortcuts {
  margin-top: 9px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;

  > span {
    color: var(--workflow-text-tertiary, var(--el-text-color-placeholder));
    font-size: 10px;
  }

  button {
    padding: 4px 8px;
    border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
    border-radius: 999px;
    color: var(--workflow-text-secondary, var(--el-text-color-secondary));
    background: var(--workflow-surface-raised, var(--el-bg-color-overlay));
    font-size: 10px;
    cursor: pointer;

    &:hover:not(:disabled),
    &:focus-visible {
      border-color: var(--workflow-primary, var(--el-color-primary));
      color: var(--workflow-primary, var(--el-color-primary));
      outline: none;
    }
  }
}

.agent-input-summary {
  padding: 11px 14px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
}

.summary-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.summary-copy {
  min-width: 0;
  flex: 1;

  strong,
  small {
    display: block;
  }

  strong {
    font-size: 12px;
  }

  small {
    margin-top: 3px;
    color: var(--workflow-text-secondary, var(--el-text-color-secondary));
    font-size: 11px;
  }
}

.agent-readiness {
  padding: 10px 12px;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 9px;
  background: var(--workflow-muted, var(--el-fill-color-extra-light));

  > .el-icon {
    flex: 0 0 auto;
  }

  strong,
  small {
    display: block;
  }

  strong {
    font-size: 12px;
  }

  small {
    margin-top: 2px;
    color: var(--workflow-text-secondary, var(--el-text-color-secondary));
    font-size: 11px;
  }

  &.is-success,
  &.is-primary {
    border-color: color-mix(in srgb, var(--el-color-success) 32%, var(--workflow-border));

    > .el-icon,
    strong {
      color: var(--el-color-success);
    }
  }

  &.is-warning > .el-icon,
  &.is-warning strong {
    color: var(--el-color-warning);
  }

  &.is-danger > .el-icon,
  &.is-danger strong {
    color: var(--el-color-danger);
  }
}

.runtime-hint {
  display: block;
  margin-top: 6px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 11px;
}

@media (max-width: 680px) {
  .agent-task-heading,
  .agent-input-summary {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
