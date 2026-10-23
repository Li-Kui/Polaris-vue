<template>
  <section class="runtime-app-intro" aria-labelledby="runtime-app-title">
    <span class="app-icon" aria-hidden="true"><el-icon><component :is="presentation.icon" /></el-icon></span>
    <div class="app-heading">
      <h1 id="runtime-app-title">{{ title || presentation.title }}</h1>
      <p>{{ description || presentation.description }}</p>
    </div>
  </section>
</template>

<script setup>
import {computed} from 'vue'
import {ChatDotRound, DataAnalysis, EditPen, Files, List, Picture, Search, Switch} from '@element-plus/icons-vue'

const props = defineProps({ pageType: { type: String, default: 'form' }, title: String, description: String })
const types = {
  form: { icon: EditPen, title: '表单应用', description: '填写需求，开始运行你的智能应用' },
  chat: { icon: ChatDotRound, title: '对话助手', description: '上传资料，直接提问' },
  image: { icon: Picture, title: 'AI 图片工作台', description: '描述想法，生成你的画面' },
  compare: { icon: Switch, title: '图片优化', description: '上传原图，查看处理前后的差异' },
  gallery: { icon: Files, title: '作品画廊', description: '一次生成，多张灵感' },
  report: { icon: DataAnalysis, title: '分析报告', description: '填写报告参数，获取清晰的分析结果' },
  task: { icon: List, title: '批量任务', description: '提交任务，稍后查看处理结果' },
  query: { icon: Search, title: '数据查询', description: '输入条件，快速查看结果' }
}
const presentation = computed(() => types[props.pageType] || types.form)
</script>

<style scoped>
.runtime-app-intro { display: flex; align-items: flex-start; gap: 14px; margin: 0 22px; padding: 26px 0 24px; border-bottom: 1px solid var(--runtime-border); }
.app-icon { display: grid; place-items: center; flex: 0 0 44px; height: 44px; border-radius: 14px; background: var(--runtime-primary-soft); color: var(--workflow-primary); font-size: 25px; }
.app-heading { min-width: 0; }
h1 { margin: 0; color: var(--runtime-text); font-size: 22px; font-weight: 650; letter-spacing: -.5px; line-height: 1.4; overflow-wrap: anywhere; }
p { margin: 6px 0 0; color: var(--runtime-secondary); font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
</style>
