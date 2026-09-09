<template>
  <el-collapse v-model="activeNames" class="workflow-disclosure" @change="$emit('change', $event)">
    <el-collapse-item :name="name">
      <template #title>
        <div class="workflow-disclosure-title">
          <span class="workflow-disclosure-icon">
            <el-icon><component :is="iconComponent" /></el-icon>
          </span>
          <span class="workflow-disclosure-copy">
            <strong>{{ title }}</strong>
            <small v-if="description">{{ description }}</small>
          </span>
          <el-tag v-if="badge" size="small" :type="badgeType" effect="plain">{{ badge }}</el-tag>
        </div>
      </template>
      <div class="workflow-disclosure-content">
        <slot />
      </div>
    </el-collapse-item>
  </el-collapse>
</template>

<script>
import {DataAnalysis, Setting} from '@element-plus/icons-vue'

export default {
  name: 'WorkflowDisclosureCard',
  components: {DataAnalysis, Setting},
  props: {
    title: {type: String, required: true},
    description: {type: String, default: ''},
    badge: {type: String, default: ''},
    badgeType: {type: String, default: 'info'},
    icon: {type: String, default: 'setting'},
    name: {type: String, default: 'panel'},
    defaultOpen: {type: Boolean, default: false}
  },
  emits: ['change'],
  data() {
    return {activeNames: this.defaultOpen ? [this.name] : []}
  },
  computed: {
    iconComponent() {
      return this.icon === 'preview' ? 'DataAnalysis' : 'Setting'
    }
  }
}
</script>

<style scoped lang="scss">
.workflow-disclosure {
  overflow: hidden;
  border: 1px solid var(--workflow-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--workflow-surface, var(--el-bg-color));
}

.workflow-disclosure :deep(.el-collapse-item__header) {
  min-height: 64px;
  height: auto;
  padding: 10px 12px;
  border-bottom: 0;
  color: var(--workflow-text, var(--el-text-color-primary));
  background: var(--workflow-muted, var(--el-fill-color-extra-light));
  line-height: 1.4;
}

.workflow-disclosure :deep(.el-collapse-item__header.is-active) {
  border-bottom: 1px solid var(--workflow-border, var(--el-border-color-lighter));
}

.workflow-disclosure :deep(.el-collapse-item__wrap) {
  border-bottom: 0;
  background: var(--workflow-surface, var(--el-bg-color));
}

.workflow-disclosure :deep(.el-collapse-item__content) {
  padding: 0;
}

.workflow-disclosure :deep(.el-collapse-item__arrow) {
  flex: 0 0 auto;
  margin-left: 10px;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
}

.workflow-disclosure-title {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 9px;
  margin-right: 2px;
  text-align: left;
}

.workflow-disclosure-icon {
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: var(--workflow-primary, var(--el-color-primary));
  background: var(--workflow-primary-soft, var(--el-color-primary-light-9));
}

.workflow-disclosure-copy {
  min-width: 0;
  flex: 1;
}

.workflow-disclosure-copy strong,
.workflow-disclosure-copy small {
  display: block;
}

.workflow-disclosure-copy strong {
  color: var(--workflow-text, var(--el-text-color-primary));
  font-size: 12px;
  font-weight: 650;
}

.workflow-disclosure-copy small {
  margin-top: 2px;
  overflow: hidden;
  color: var(--workflow-text-secondary, var(--el-text-color-secondary));
  font-size: 10px;
  font-weight: 400;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-disclosure-content {
  padding: 12px;
}
</style>
