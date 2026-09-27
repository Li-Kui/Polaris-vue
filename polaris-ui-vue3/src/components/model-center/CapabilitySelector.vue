<template>
  <div class="capability-selector">
    <div class="selector-heading">
      <div>
        <b>核心能力</b>
        <span>已按当前服务商筛选，选择模型后会自动推荐，仍可手动调整</span>
      </div>
      <el-tag v-if="recommendation" type="primary" effect="plain" round>{{ recommendation }}</el-tag>
    </div>

    <div class="invocation-grid">
      <button
        v-for="schema in invocationSchemas"
        :key="schema.code"
        type="button"
        class="selector-card"
        :class="{ selected: enabled(schema.code) }"
        @click="$emit('toggle', schema, '', !enabled(schema.code))"
      >
        <span class="capability-mark">{{ capabilityMark(schema.code) }}</span>
        <span class="capability-copy">
          <b>{{ schema.name }}</b>
          <small>{{ capabilityDescription(schema.code) }}</small>
        </span>
        <el-switch
          :model-value="enabled(schema.code)"
          @click.stop
          @change="$emit('toggle', schema, '', $event)"
        />
      </button>
    </div>

    <div v-if="featureSchemas.length" class="feature-section">
      <div class="feature-heading"><b>增强特性</b><span>仅在对应核心能力启用时生效</span></div>
      <div v-for="schema in featureSchemas" :key="schema.code" class="feature-row">
        <div class="capability-copy">
          <b>{{ schema.name }}</b>
          <small>{{ capabilityDescription(schema.code) }}</small>
        </div>
        <div class="feature-targets">
          <el-checkbox
            v-for="target in featureTargets(schema)"
            :key="`${schema.code}@${target}`"
            :model-value="enabled(`${schema.code}@${target}`)"
            @change="$emit('toggle', schema, target, $event)"
          >{{ targetName(target) }}</el-checkbox>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed} from 'vue'

const props = defineProps({
  schemas: { type: Array, default: () => [] },
  selectedKeys: { type: Array, default: () => [] },
  recommendation: { type: String, default: '' }
})
defineEmits(['toggle'])
const invocationSchemas = computed(() => props.schemas.filter(schema => schema.kind === 'INVOCATION'))
const featureSchemas = computed(() => props.schemas.filter(schema => schema.kind === 'FEATURE'))
const invocationCodes = computed(() => new Set(invocationSchemas.value.map(schema => schema.code)))
function enabled(key) { return props.selectedKeys.includes(key) }
function targetName(code) {
  return props.schemas.find(schema => schema.code === code)?.name || code
}
function featureTargets(schema) {
  return (schema.allowedAppliesTo || []).filter(code => invocationCodes.value.has(code))
}
function capabilityMark(code) {
  return ({
    CHAT_COMPLETION: '聊', TEXT_EMBEDDING: '向', RERANK: '排',
    IMAGE_GENERATION: '图', IMAGE_EDIT: '编', IMAGE_INPAINT: '绘', IMAGE_VARIATION: '变',
    AUDIO_TTS: '声', AUDIO_STT: '听', VIDEO_GENERATION: '视'
  })[code] || '能'
}
function capabilityDescription(code) {
  return ({
    CHAT_COMPLETION: '对话、问答与文本生成',
    TEXT_EMBEDDING: '知识库检索与文本向量化',
    RERANK: '对召回结果进行相关性重排',
    IMAGE_GENERATION: '根据文本生成图片',
    IMAGE_EDIT: '根据指令编辑现有图片',
    IMAGE_INPAINT: '对图片指定区域进行重绘',
    IMAGE_VARIATION: '基于原图生成相似变体',
    AUDIO_TTS: '将文本转换为语音',
    AUDIO_STT: '将语音转换为文字',
    VIDEO_GENERATION: '根据文本或图片生成视频',
    REASONING: '为核心能力增加推理过程'
  })[code] || (props.schemas.find(schema => schema.code === code)?.kind === 'FEATURE'
    ? '附加到核心能力的可选增强' : '模型可直接执行的核心能力')
}
</script>

<style scoped>
.capability-selector { display: grid; gap: 14px; }
.selector-heading, .feature-heading { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.selector-heading > div, .feature-heading { color: var(--mc-text); }
.selector-heading span, .feature-heading span { margin-left: 10px; color: var(--mc-muted); font-size: 12px; font-weight: 400; }
.selector-heading :deep(.el-tag) { border-color: rgba(37, 99, 235, .28) !important; color: var(--mc-accent) !important; background: var(--mc-accent-soft) !important; }
.invocation-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.selector-card { appearance: none; display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; align-items: center; gap: 12px; min-width: 0; padding: 15px 16px; border: 1px solid var(--mc-border); border-radius: 13px; color: var(--mc-text); text-align: left; background: var(--mc-surface); cursor: pointer; transition: border-color .2s ease, background .2s ease, transform .2s ease, box-shadow .2s ease; }
.selector-card:hover { border-color: var(--mc-accent); transform: translateY(-1px); box-shadow: 0 8px 20px rgba(15, 23, 42, .06); }
.selector-card.selected { border-color: var(--mc-accent); background: var(--mc-accent-soft); box-shadow: inset 3px 0 0 var(--mc-accent); }
.capability-mark { display: grid; width: 36px; height: 36px; place-items: center; border-radius: 10px; color: var(--mc-accent); background: var(--mc-accent-soft); font-size: 14px; font-weight: 800; }
.selector-card.selected .capability-mark { color: #fff; background: var(--mc-accent); }
.capability-copy { display: grid; min-width: 0; gap: 4px; }.capability-copy b { color: var(--mc-text); }.capability-copy small { overflow: hidden; color: var(--mc-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.feature-section { display: grid; gap: 10px; margin-top: 2px; padding-top: 14px; border-top: 1px solid var(--mc-border); }
.feature-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 13px 15px; border: 1px solid var(--mc-border); border-radius: 12px; background: var(--mc-surface-muted); }
.feature-targets { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 4px 12px; }
.feature-row :deep(.el-checkbox__label) { color: var(--mc-text); }
@media (max-width: 900px) { .invocation-grid { grid-template-columns: 1fr; } .selector-heading { align-items: flex-start; flex-direction: column; } }
@media (max-width: 600px) { .feature-row { align-items: flex-start; flex-direction: column; } .feature-targets { justify-content: flex-start; } }
</style>
