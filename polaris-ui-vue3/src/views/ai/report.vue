<template>
  <div class="app-container no-sidebar-manage-wrap ai-report-page">
    <div class="content-inner">
      <!-- 顶部玻璃筛选卡片 (完全融入 Polaris 公共系统设计) -->
      <el-form :model="queryParams" ref="queryForm" :inline="true" v-show="showSearch" label-width="85px" class="polaris-filter-card polaris-filter-form">
      <el-form-item label="报告标题" prop="reportTitle">
        <el-input
          v-model="queryParams.reportTitle"
          placeholder="请输入报告标题关键字"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="智能体编码" prop="agentCode">
        <el-input
          v-model="queryParams.agentCode"
          placeholder="请输入智能体编码"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery" class="polaris-query-btn">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery" class="polaris-reset-btn">重置</el-button>
      </el-form-item>
    </el-form>

      <!-- 报告列表主玻璃卡片 -->
      <div class="polaris-table-card">
      <div class="polaris-action-row">
        <div class="actions-left">
          <div class="table-brand-header">
            <span class="brand-icon-wrapper">
              <el-icon><document /></el-icon>
            </span>
            <span class="table-title-text">AI 智能分析报告归档中心</span>
            <span class="count-badge" v-if="total > 0">{{ total }} 份</span>
          </div>
        </div>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </div>

      <el-table
        v-loading="loading"
        :data="reportList"
        class="polaris-el-table polaris-custom-table"
        style="width: 100%"
      >
        <el-table-column label="序号" type="index" width="70" align="center" />
        <el-table-column label="报告编号" prop="reportCode" width="190" show-overflow-tooltip>
          <template #default="scope">
            <span class="report-code-pill">{{ scope.row.reportCode || 'REP-' + scope.row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="报告标题" prop="reportTitle" min-width="120" show-overflow-tooltip>
          <template #default="scope">
            <span class="report-title-link" @click="handlePreview(scope.row)">
              {{ scope.row.reportTitle }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="关联智能体" prop="agentCode" width="170" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.agentCode" type="success" size="small" effect="plain" class="agent-chip">
              {{ scope.row.agentCode }}
            </el-tag>
            <el-tag v-else type="info" size="small" effect="plain" class="agent-chip">
              POLARIS-ANALYST
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归档时间" prop="createTime" width="180" align="center">
          <template #default="scope">
            <span>{{ scope.row.createTime || scope.row.updateTime || '刚刚' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createBy" width="120" align="center">
          <template #default="scope">
            <span class="user-name-text">{{ scope.row.createBy || 'admin' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="美化状态" prop="refineStatus" width="110" align="center">
          <template #default="scope">
            <el-tag
              size="small"
              effect="plain"
              :type="refineStatusType(scope.row.refineStatus)"
              :title="scope.row.refineError || ''"
            >
              {{ refineStatusLabel(scope.row.refineStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="240" fixed="right">
          <template #default="scope">
            <el-button 
              size="small" 
              type="primary" 
              link 
              :icon="refiningId === scope.row.id ? 'Loading' : 'View'"
              :loading="refiningId === scope.row.id"
              :disabled="refiningId !== null && refiningId !== scope.row.id"
              @click="handlePreview(scope.row)"
            >
              {{ refiningId === scope.row.id ? 'AI 美化中...' : '查看美化报告' }}
            </el-button>
            <el-button 
              size="small" 
              type="danger" 
              link 
              icon="Delete" 
              :disabled="refiningId === scope.row.id" 
              @click="handleDelete(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 全局标准分页组件 -->
      <pagination
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
      </div>
    </div>

    <!-- 报告在线预览大屏弹窗 (高质感商业分析与图表化展示) -->
    <el-dialog
      v-model="previewVisible"
      width="1100px"
      top="4vh"
      class="report-preview-dialog"
      :show-close="false"
      @close="handleCloseReportModal"
      append-to-body
    >
      <template #header>
        <div class="drawer-header-custom" style="position: relative; z-index: 10;">
          <div class="drawer-title-box">
            <span class="drawer-icon-glow">
              <el-icon><data-board /></el-icon>
            </span>
            <span class="drawer-title">{{ cleanDialogTitle }}</span>
            <el-tag size="small" type="primary" effect="plain" class="drawer-badge">高质感数据大屏</el-tag>
            <el-tag
              v-if="currentReport.refineStatus"
              size="small"
              effect="plain"
              :type="refineStatusType(currentReport.refineStatus)"
              :title="currentReport.refineError || ''"
            >
              {{ refineStatusLabel(currentReport.refineStatus) }}
            </el-tag>
          </div>
          <div class="drawer-actions" style="display: flex; align-items: center; gap: 12px; position: relative; z-index: 50;">
            <el-button type="primary" size="small" icon="Download" @click="handlePrint">
              导出 PDF 报告
            </el-button>
            <button 
              type="button"
              class="custom-close-btn"
              title="关闭弹窗"
              @click.stop.prevent="handleCloseReportModal"
              @mousedown.stop.prevent="handleCloseReportModal"
            >
              <el-icon><Close /></el-icon>
            </button>
          </div>
        </div>
      </template>

      <div id="report-drawer-print-area" class="report-drawer-body" v-if="previewVisible">
        <!-- 默认且唯一展示最新 PolarisReportEngine 极简美化渲染大屏 -->
        <PolarisReportEngine
          :report-title="cleanDialogTitle"
          :meta-info="{ author: currentReport.createBy || 'admin', date: currentReport.createTime, code: currentReport.reportCode || ('REP-' + currentReport.id) }"
          :structured-schema="refinedSchema"
          :content="reportContent"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import {delReport, getRefineStatus, getReport, listReports, refineReportById} from '@/api/ai/report'
import PolarisReportEngine from './report/PolarisReportEngine.vue'

export default {
  name: 'AiReport',
  components: {
    PolarisReportEngine
  },
  data() {
    return {
      loading: false,
      showSearch: true,
      reportList: [],
      total: 0,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        reportTitle: undefined,
        agentCode: undefined
      },
      previewVisible: false,
      currentReport: {},
      reportContent: '',
      reportTitle: '',
      refiningId: null,
      refinePollTimer: null,
      refinePollCancel: null,
      refinedSchema: null
    }
  },
  computed: {
    cleanDialogTitle() {
      const raw = (this.currentReport && this.currentReport.reportTitle) || this.reportTitle || 'AI 智能数据分析报告'
      return raw.replace(/^[#\s\*\📊\📈\🛡️\💰]+/, '').replace(/^#{1,4}\s*/, '').trim()
    }
  },
  created() {
    this.getList()
  },
  methods: {
    refineStatusLabel(status) {
      const labels = {
        NONE: '未美化',
        RUNNING: '美化中',
        SUCCESS: '已完成',
        FAILED: '失败重试'
      }
      return labels[status] || '未美化'
    },
    refineStatusType(status) {
      const types = {
        NONE: 'info',
        RUNNING: 'warning',
        SUCCESS: 'success',
        FAILED: 'danger'
      }
      return types[status] || 'info'
    },
    handleOpenDEADemo() {
      this.currentReport = {
        id: 'DEA-2026-DEMO',
        reportTitle: '2026 北辰 AI 全流程业务增长与智能体效能深度诊断报告',
        reportCode: 'DEA-ULTIMATE-001',
        createBy: 'Polaris-AI 资深专家组',
        createTime: '2026-07-24 17:30',
        agentCode: 'POLARIS-EXPERT-AGENT'
      }
      this.reportTitle = '2026 北辰 AI 全流程业务增长与智能体效能深度诊断报告'
      this.refinedSchema = {
        executiveSummary: '本季度通过部署 Polaris-AI 智能体矩阵，企业自动化研报生成效率提升 340%，客服响应沉淀周期缩短 65%。海外 AI SaaS 业务贡献了主要净新增收入。',
        compareMatrix: {
          title: "传统 Markdown 渲染 VS 【Polaris-AI 智能可视化大屏】 深度对比",
          columns: [
            {
              title: "传统 Markdown 渲染",
              style: "default",
              points: [
                "大段白底黑字长文本，缺乏视觉层次与区分度",
                "无法直接流式呈现高颜值 KPI 大屏与手势交互",
                "静态表单缺乏数字增长与图表入场动画",
                "导出 PDF 容易断行分割错位"
              ]
            },
            {
              title: "【Polaris-AI 智能可视化大屏】",
              style: "highlight",
              badge: "推荐标杆",
              points: [
                "智能语义识别：自动构建双栏对比/SWOT四象限矩阵/行动看板",
                "渐进流式加载：支持 ::: 指令卡片实时弹出与骨架屏",
                "动态可视化 DSL：ECharts / SVG / 拓扑图秒级自适应渲染",
                "无损 1:1 矢量导出，杂志级高级视觉审美质感"
              ]
            }
          ]
        },
        swot: {
          strengths: ['具备自主研报动态编排渲染引擎', '大模型响应延时降至 180ms', '深度整合 Vue3 + Element Plus 商业全家桶'],
          weaknesses: ['部分复杂多维桑基图需要持续优化算力', '移动端手势下钻适配待加强'],
          opportunities: ['企业级 AI 自动化报告市场迎来爆破期', '出海多语言定制化报告需求旺盛'],
          threats: ['开源大模型轻量化替代竞争', '数据安全与合规审计监管趋严']
        },
        actionPlan: [
          { priority: 'P1', action: '全面上线 D+E+A 渐进式智能美化引擎，全量替换传统纯文本 Markdown 渲染', owner: '前端研发组', deadline: '2026-08-01' },
          { priority: 'P1', action: '增强后端 AI 智能体指令输出稳定性，控制 Directive 识别成功率 >99.5%', owner: 'Polaris-AI 后端团队', deadline: '2026-08-05' },
          { priority: 'P2', action: '集成 Vega-Lite 极简 DSL 拓展更多复合拓扑关系图', owner: '数据可视化小分队', deadline: '2026-08-15' }
        ]
      }
      this.reportContent = `
::: executive-summary
本报告综合评估了北辰 AI 平台引入 【Polaris-AI 智能可视化大屏】 后的多项关键指标表现。数据显示，卡片式智能排版与矢量 DSL 可视化极大地提升了决策层对研报的阅读完成率。
:::

::: kpi-group
- kpi: 智能报告阅读完成率
  value: 94.8%
  change: +38.2%
  type: success
  emoji: 📈
- kpi: AI 研报排版耗时
  value: 120ms
  change: -85.0%
  type: primary
  emoji: ⚡
- kpi: 高管满意度评分
  value: 4.95 / 5.0
  change: +12.4%
  type: warning
  emoji: 🏆
- kpi: 活跃分析智能体数
  value: 28 个
  change: +6 个
  type: success
  emoji: 🤖
:::

::: chart
{
  "title": "2026 季度 AI 报告美化使用率与满意度走势",
  "categories": ["Q1 基础版", "Q2 卡片版", "Q3 终极版大屏", "Q4 预测"],
  "values": [320, 580, 940, 1280],
  "chartType": "line"
}
:::
`
      this.previewVisible = true
    },
    getList() {
      this.loading = true
      listReports(this.queryParams).then(res => {
        if (res.code === 200) {
          this.reportList = res.data ? (res.data.rows || res.data) : []
          this.total = res.data ? (res.data.total || this.reportList.length) : 0
        }
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.queryParams.reportTitle = undefined
      this.queryParams.agentCode = undefined
      this.handleQuery()
    },
    handleDelete(row) {
      if (this.refiningId === row.id) {
        this.clearRefinePollTimer()
        this.refiningId = null
      }
      this.$confirm(`确认要删除标题为"${row.reportTitle}"的分析报告吗？`, '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        return delReport(row.id)
      }).then(res => {
        if (res.code === 200 || res.data) {
          this.$message.success('删除报告成功')
          this.getList()
        }
      }).catch(() => {})
    },

    // ──────────────────────────────────────────
    // 报告详情解析与美化渲染逻辑 (深度美化结果保存至 refinedSchema)
    // ──────────────────────────────────────────
    async handlePreview(row) {
      if (this.refiningId !== null) return

      this.clearRefinePollTimer()
      this.refiningId = row.id
      this.currentReport = {...row}
      this.refinedSchema = null
      this.reportContent = ''
      this.previewVisible = true

      try {
        const detailRes = await getReport(row.id)
        if (detailRes.code !== 200 || !detailRes.data) {
          throw new Error(detailRes.msg || '报告详情加载失败')
        }

        this.currentReport = detailRes.data
        this.parseAndInitReportData(this.currentReport.reportContent)

        const cachedSchema = this.parseCachedSchema(this.currentReport)
        if (cachedSchema && this.currentReport.refineStatus === 'SUCCESS') {
          this.applyRefinedSchema(cachedSchema)
          return
        }

        this.$notify({
          title: 'AI 深度重塑美化中...',
          message: `报告原文已打开，正在后台生成《${this.currentReport.reportTitle || '报告'}》的结构化看板。`,
          type: 'info',
          duration: 3500
        })

        const taskRes = await refineReportById(this.currentReport.id)
        if (taskRes.code !== 200 || !taskRes.data) {
          throw new Error(taskRes.msg || 'AI 美化任务创建失败')
        }

        this.applyRefineStatus(taskRes.data)
        if (taskRes.data.status === 'SUCCESS' && taskRes.data.schema) {
          this.applyRefinedSchema(taskRes.data.schema)
          return
        }
        if (taskRes.data.status === 'FAILED') {
          throw new Error(taskRes.data.error || 'AI 美化失败')
        }
        await this.pollRefineStatus(this.currentReport.id)
      } catch (err) {
        console.warn('报告中心 AI 美化处理异常，保留原始报告展示:', err)
        this.currentReport.refineStatus = 'FAILED'
        this.currentReport.refineError = err.message || 'AI 美化失败'
        this.$message.warning(`${this.currentReport.refineError}，可再次点击重试`)
      } finally {
        this.clearRefinePollTimer()
        this.refiningId = null
      }
    },

    applyRefineStatus(statusData) {
      if (!statusData || typeof statusData !== 'object') return
      this.currentReport.refineStatus = statusData.status || this.currentReport.refineStatus || 'NONE'
      this.currentReport.refineError = statusData.error || ''
      this.currentReport.refineStartedAt = statusData.refineStartedAt || this.currentReport.refineStartedAt
      this.currentReport.refinedAt = statusData.refinedAt || this.currentReport.refinedAt
      this.currentReport.refineSchemaVersion = statusData.schemaVersion || this.currentReport.refineSchemaVersion
      this.currentReport.refinePromptVersion = statusData.promptVersion || this.currentReport.refinePromptVersion
    },

    async pollRefineStatus(reportId) {
      const startedAt = Date.now()
      while (this.previewVisible && this.currentReport.id === reportId && Date.now() - startedAt < 90000) {
        await new Promise(resolve => {
          this.refinePollCancel = resolve
          this.refinePollTimer = setTimeout(resolve, 1500)
        })
        this.refinePollCancel = null
        this.refinePollTimer = null
        if (!this.previewVisible || this.currentReport.id !== reportId) return

        const statusRes = await getRefineStatus(reportId)
        if (statusRes.code !== 200 || !statusRes.data) {
          throw new Error(statusRes.msg || 'AI 美化状态查询失败')
        }
        this.applyRefineStatus(statusRes.data)
        if (statusRes.data.status === 'SUCCESS') {
          const schema = statusRes.data.schema || this.parseCachedSchema({refinedSchema: statusRes.data.refinedSchema})
          if (!schema) throw new Error('AI 美化结果为空或格式无效')
          this.applyRefinedSchema(schema)
          this.$notify({
            title: '✨ AI 重塑美化完成',
            message: '已生成结构化摘要、指标和行动计划看板。',
            type: 'success',
            duration: 3500
          })
          return
        }
        if (statusRes.data.status === 'FAILED') {
          throw new Error(statusRes.data.error || 'AI 美化失败')
        }
      }
      throw new Error('AI 美化处理超时，请稍后重试')
    },

    clearRefinePollTimer() {
      if (this.refinePollTimer) {
        clearTimeout(this.refinePollTimer)
        this.refinePollTimer = null
      }
      if (this.refinePollCancel) {
        this.refinePollCancel()
        this.refinePollCancel = null
      }
    },

    applyRefinedSchema(schema) {
      if (schema && typeof schema === 'object') {
        this.refinedSchema = schema
      }
    },

    parseCachedSchema(report) {
      if (!report || !report.refinedSchema) {
        return null
      }
      try {
        const schema = typeof report.refinedSchema === 'object'
          ? report.refinedSchema
          : JSON.parse(report.refinedSchema)
        return schema && typeof schema === 'object' && !Array.isArray(schema)
          ? schema
          : null
      } catch (error) {
        console.warn('报告中心缓存的美化结果不是有效 JSON，将重新生成:', error)
        return null
      }
    },

    parseAndInitReportData(content) {
      if (!content) {
        this.reportContent = ''
        this.reportTitle = this.currentReport.reportTitle || '数据分析报告'
        return
      }

      let normalizedContent = content.replace(/\r\n/g, '\n').trim()

      // 只做原文规范化，不在报告中心重复解析 KPI 或生成图表。
      const firstHeadingIndex = normalizedContent.search(/^#{1,3}\s+/m)
      if (firstHeadingIndex > 0) {
        normalizedContent = normalizedContent.substring(firstHeadingIndex).trim()
      } else {
        normalizedContent = normalizedContent.replace(/^(好的|收到|已为您|以下是为您|好的，已|好的，我|已成功|首先)[^\n\:]*[\:\：\n]\s*/gi, '').trim()
      }

      const lines = normalizedContent.split('\n')
      if (lines.length > 0 && /^#{1,3}\s+/.test(lines[0])) {
        const extractedTitle = lines[0].replace(/^#{1,3}\s+/, '').replace(/[\📊\📈\🛡️\💰]/g, '').trim()
        if (extractedTitle) this.reportTitle = extractedTitle
        lines.shift()
        normalizedContent = lines.join('\n').trim()
      }

      this.reportContent = normalizedContent
      const titleMatch = normalizedContent.match(/^#\s*([^\n]+)$/m)
      this.reportTitle = titleMatch && titleMatch[1]
        ? titleMatch[1].replace(/^[🛡️📊📑📋📝\s]+/, '').trim()
        : (this.reportTitle || this.currentReport.reportTitle || '智能数据分析报告')
    },

    handleCloseReportModal() {
      this.previewVisible = false
      this.clearRefinePollTimer()
    },

    handleDrawerClose(done) {
      this.handleCloseReportModal()
      if (done) done()
    },

    waitForImageLoaded(img) {
      if (img.complete) return Promise.resolve()
      return new Promise(resolve => {
        img.onload = resolve
        img.onerror = resolve
      })
    },

    async waitForReportRenderReady() {
      await this.$nextTick()
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      await new Promise(resolve => setTimeout(resolve, 350))
    },

    expandPdfExportContainers(element) {
      const containers = [element]
      const dialogBody = element.closest('.el-dialog__body')
      const dialog = element.closest('.el-dialog')
      if (dialogBody) containers.push(dialogBody)
      if (dialog) containers.push(dialog)

      const snapshots = containers.map(node => ({
        node,
        style: {
          maxHeight: node.style.maxHeight,
          height: node.style.height,
          overflow: node.style.overflow,
          overflowY: node.style.overflowY
        }
      }))

      snapshots.forEach(({ node }) => {
        node.style.maxHeight = 'none'
        node.style.height = 'auto'
        node.style.overflow = 'visible'
        node.style.overflowY = 'visible'
      })

      return () => {
        snapshots.forEach(({ node, style }) => {
          node.style.maxHeight = style.maxHeight
          node.style.height = style.height
          node.style.overflow = style.overflow
          node.style.overflowY = style.overflowY
        })
      }
    },

    async createChartSnapshots(element) {
      const snapshots = []
      const chartContainers = Array.from(element.querySelectorAll('.chart-mount-container, #pretty-report-chart'))

      for (const container of chartContainers) {
        const chart = echarts.getInstanceByDom(container)
        const canvas = container.querySelector('canvas')

        if (!chart && !canvas) continue

        try {
          if (chart) {
            chart.resize()
            await new Promise(resolve => requestAnimationFrame(resolve))
          }

          const dataUrl = chart
            ? chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#ffffff' })
            : canvas.toDataURL('image/png')

          if (!dataUrl) continue

          const previousPosition = container.style.position
          const previousOverflow = container.style.overflow
          container.style.position = previousPosition || 'relative'
          container.style.overflow = 'hidden'

          const wrapper = document.createElement('div')
          wrapper.className = 'pdf-chart-snapshot-wrap'
          wrapper.style.cssText = 'position:absolute;inset:0;z-index:20;background:#fff;display:flex;align-items:center;justify-content:center;pointer-events:none;'

          const img = new Image()
          img.className = 'pdf-chart-snapshot-img'
          img.alt = 'chart snapshot'
          img.src = dataUrl
          img.style.cssText = 'display:block;width:100%;height:100%;object-fit:contain;'
          wrapper.appendChild(img)
          container.appendChild(wrapper)
          await this.waitForImageLoaded(img)

          snapshots.push({ container, wrapper, previousPosition, previousOverflow })
        } catch (err) {
          console.warn('图表快照生成失败，跳过该图表:', err)
        }
      }

      return () => {
        snapshots.forEach(({ container, wrapper, previousPosition, previousOverflow }) => {
          if (wrapper && wrapper.parentNode) wrapper.parentNode.removeChild(wrapper)
          container.style.position = previousPosition
          container.style.overflow = previousOverflow
        })
      }
    },

    getPdfMetaInfo() {
      const report = this.currentReport || {}
      return {
        code: report.reportCode || (report.id ? 'REP-' + report.id : ''),
        date: report.createTime || '',
        author: report.createBy || 'admin'
      }
    },

    collectPdfTemplateBlocks(reportNode) {
      const blocks = []
      const banner = reportNode.querySelector('.engine-report-banner')
      const blockContainer = reportNode.querySelector('.engine-blocks-container')

      if (banner) blocks.push({ type: 'banner', node: banner.cloneNode(true) })
      if (blockContainer) {
        Array.from(blockContainer.children).forEach(node => {
          blocks.push({ type: 'content', node: node.cloneNode(true) })
        })
      }

      return blocks
    },

    escapeHtml(value) {
      return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
    },

    applyScopedAttrs(sourceNode, targetNode) {
      Array.from(sourceNode.attributes || []).forEach(attr => {
        if (attr.name.startsWith('data-v-')) {
          targetNode.setAttribute(attr.name, attr.value)
        }
      })
    },

    createPdfTemplatePage({ title, metaInfo, pageNumber, widthPx, heightPx, sourceNode }) {
      const page = document.createElement('div')
      page.className = 'pdf-report-page'
      page.style.cssText = [
        'width:' + widthPx + 'px',
        'height:' + heightPx + 'px',
        'box-sizing:border-box',
        'padding:30px 40px 24px',
        'background:#ffffff',
        'color:#0f172a',
        'font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC",sans-serif',
        'display:flex',
        'flex-direction:column',
        'overflow:hidden'
      ].join(';')

      const header = document.createElement('div')
      header.className = 'pdf-report-header'
      header.style.cssText = 'height:56px;display:flex;align-items:flex-start;justify-content:space-between;border-bottom:1px solid #e2e8f0;margin-bottom:18px;flex:0 0 auto;'
      const escapedTitle = this.escapeHtml(title)
      const escapedCode = this.escapeHtml(metaInfo.code || '-')
      const escapedAuthor = this.escapeHtml(metaInfo.author || '-')
      const escapedDate = this.escapeHtml(metaInfo.date || '')
      header.innerHTML = [
        '<div style="min-width:0;">',
        '<div style="font-size:18px;font-weight:800;color:#0f172a;line-height:1.2;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:520px;">' + escapedTitle + '</div>',
        '<div style="margin-top:7px;font-size:11px;color:#64748b;">报告编号：' + escapedCode + '　分析者：' + escapedAuthor + '</div>',
        '</div>',
        '<div style="font-size:11px;color:#64748b;text-align:right;line-height:1.7;white-space:nowrap;">',
        '<div>Polaris-AI</div>',
        '<div>' + escapedDate + '</div>',
        '</div>'
      ].join('')

      const body = document.createElement('div')
      body.className = 'pdf-report-body'
      body.style.cssText = 'flex:1 1 auto;min-height:0;overflow:hidden;background:linear-gradient(135deg,#f8fafc 0%,#f1f5f9 100%);border-radius:10px;padding:16px;box-sizing:border-box;'

      const engine = document.createElement('div')
      engine.className = 'polaris-report-engine theme-glass-light pdf-template-engine'
      engine.style.cssText = 'width:100%;padding:0;background:transparent;border-radius:0;box-shadow:none;color:#1e293b;'
      this.applyScopedAttrs(sourceNode, engine)

      const blockContainer = document.createElement('div')
      blockContainer.className = 'engine-blocks-container'
      blockContainer.style.cssText = 'display:flex;flex-direction:column;gap:16px;'
      const sourceBlockContainer = sourceNode.querySelector('.engine-blocks-container')
      if (sourceBlockContainer) this.applyScopedAttrs(sourceBlockContainer, blockContainer)

      engine.appendChild(blockContainer)
      body.appendChild(engine)

      const footer = document.createElement('div')
      footer.className = 'pdf-report-footer'
      footer.style.cssText = 'height:28px;display:flex;align-items:flex-end;justify-content:space-between;border-top:1px solid #e2e8f0;margin-top:16px;font-size:10px;color:#94a3b8;flex:0 0 auto;'
      footer.innerHTML = '<span>Polaris-AI 深度可视化诊断报告</span><span class="pdf-page-number">第 ' + pageNumber + ' 页</span>'

      page.appendChild(header)
      page.appendChild(body)
      page.appendChild(footer)

      return { page, body, engine, blockContainer, footer }
    },

    appendPdfBlockToPage(pageInfo, block) {
      if (block.type === 'banner') {
        pageInfo.engine.insertBefore(block.node, pageInfo.blockContainer)
      } else {
        pageInfo.blockContainer.appendChild(block.node)
      }
    },

    removePdfBlockFromPage(pageInfo, block) {
      if (block.node && block.node.parentNode) {
        block.node.parentNode.removeChild(block.node)
      }
    },

    pageHasPdfContent(pageInfo) {
      return !!pageInfo.body.querySelector('.engine-report-banner, .engine-blocks-container > *')
    },

    buildPdfTemplate(reportNode, title) {
      const widthPx = 794
      const heightPx = 1123
      const metaInfo = this.getPdfMetaInfo()
      const blocks = this.collectPdfTemplateBlocks(reportNode)

      const root = document.createElement('div')
      root.className = 'pdf-report-template-root'
      root.style.cssText = 'position:fixed;left:-10000px;top:0;width:' + widthPx + 'px;background:#f8fafc;z-index:-1;'
      document.body.appendChild(root)

      const pages = []
      const createPage = () => {
        const pageInfo = this.createPdfTemplatePage({
          title,
          metaInfo,
          pageNumber: pages.length + 1,
          widthPx,
          heightPx,
          sourceNode: reportNode
        })
        root.appendChild(pageInfo.page)
        pages.push(pageInfo)
        return pageInfo
      }

      let currentPage = createPage()

      blocks.forEach(block => {
        this.appendPdfBlockToPage(currentPage, block)

        if (currentPage.body.scrollHeight <= currentPage.body.clientHeight) return

        this.removePdfBlockFromPage(currentPage, block)

        if (this.pageHasPdfContent(currentPage)) {
          currentPage = createPage()
          this.appendPdfBlockToPage(currentPage, block)
        } else {
          this.appendPdfBlockToPage(currentPage, block)
        }

        if (currentPage.body.scrollHeight > currentPage.body.clientHeight) {
          const blockHeight = block.node.getBoundingClientRect().height || currentPage.body.scrollHeight
          const availableHeight = currentPage.body.clientHeight - 4
          const scale = Math.max(0.72, Math.min(1, availableHeight / Math.max(blockHeight, 1)))
          if (scale < 1) {
            const wrapper = document.createElement('div')
            wrapper.className = 'pdf-scaled-oversize-block'
            wrapper.style.cssText = 'height:' + Math.ceil(blockHeight * scale) + 'px;overflow:hidden;'
            block.node.parentNode.insertBefore(wrapper, block.node)
            block.node.parentNode.removeChild(block.node)
            wrapper.appendChild(block.node)
            block.node.style.transform = 'scale(' + scale + ')'
            block.node.style.transformOrigin = 'top left'
            block.node.style.width = (100 / scale) + '%'
          }
        }
      })

      pages.forEach((pageInfo, index) => {
        const pageNumber = pageInfo.footer.querySelector('.pdf-page-number')
        if (pageNumber) pageNumber.textContent = '第 ' + (index + 1) + ' / ' + pages.length + ' 页'
      })

      return { root, pages, widthPx, heightPx }
    },

    // 导出 / 打印 PDF 报告（前端渲染截图方案，1:1 保留大屏样式与图表）
    async handlePrint() {
      const element = document.getElementById('report-drawer-print-area')
      if (!element) {
        this.$message.warning('报告预览区域未就绪，请稍后再试')
        return
      }

      this.$message({ message: '正在生成 PDF，图表较多时请耐心等候...', type: 'info', duration: 3000 })

      let restoreContainers = null
      let restoreCharts = null
      let pdfTemplate = null

      try {
        document.body.classList.add('pdf-exporting-report')
        restoreContainers = this.expandPdfExportContainers(element)

        // 等待 DOM 重排与 ECharts 图表渲染完成，再把 canvas 固化为图片，避免 PDF 空图表
        await this.waitForReportRenderReady()
        restoreCharts = await this.createChartSnapshots(element)
        await this.waitForReportRenderReady()

        const [{ default: html2canvas }, { jsPDF }] = await Promise.all([
          import('html2canvas'),
          import('jspdf')
        ])
        const title = this.cleanDialogTitle || 'AI分析报告'
        const reportNode = element.querySelector('.polaris-report-engine') || element
        pdfTemplate = this.buildPdfTemplate(reportNode, title)
        await this.waitForReportRenderReady()

        const pdf = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait' })
        const pageWidth = pdf.internal.pageSize.getWidth()
        const pageHeight = pdf.internal.pageSize.getHeight()

        for (let pageIndex = 0; pageIndex < pdfTemplate.pages.length; pageIndex++) {
          const canvas = await html2canvas(pdfTemplate.pages[pageIndex].page, {
            scale: 2,
            useCORS: true,
            logging: false,
            backgroundColor: '#ffffff',
            scrollX: 0,
            scrollY: 0,
            x: 0,
            y: 0,
            width: pdfTemplate.widthPx,
            height: pdfTemplate.heightPx,
            windowWidth: pdfTemplate.widthPx,
            windowHeight: pdfTemplate.heightPx
          })
          const imageData = canvas.toDataURL('image/jpeg', 0.98)

          if (pageIndex > 0) pdf.addPage()
          pdf.addImage(imageData, 'JPEG', 0, 0, pageWidth, pageHeight, undefined, 'FAST')
        }

        pdf.save(title.replace(/[\\/:*?"<>|]/g, '_') + '.pdf')
        this.$message.success('PDF 导出成功')
      } catch (err) {
        console.error('PDF 导出失败:', err)
        this.$message.error('PDF 导出失败: ' + err.message)
      } finally {
        if (pdfTemplate && pdfTemplate.root && pdfTemplate.root.parentNode) {
          pdfTemplate.root.parentNode.removeChild(pdfTemplate.root)
        }
        if (restoreCharts) restoreCharts()
        if (restoreContainers) restoreContainers()
        document.body.classList.remove('pdf-exporting-report')
      }
    }
  }
}
</script>

<style scoped lang="scss">
/* 覆盖全局 .app-container 的 padding: 20px */
.app-container.no-sidebar-manage-wrap {
  padding: 16px !important;
}

.content-inner {
  padding: 0 !important;
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

/* 报告列表表格行高与动效 (对齐角色管理的高质感体验) */
:deep(.polaris-el-table) {
  background: transparent !important;

  tr, th, td {
    background: transparent !important;
  }

  th {
    padding: 14px 0 !important;
    font-size: 13px;
    font-weight: 700;
    color: #475569;
  }

  td {
    padding: 16px 0 !important;
    font-size: 14px;
  }

  .el-table__row {
    transition: transform 0.25s cubic-bezier(0.25, 0.8, 0.25, 1), background-color 0.25s ease !important;
    cursor: pointer;

    &:hover {
      transform: scale(1.002) translateX(3px);
    }
  }

  .el-table__row:hover > td {
    background-color: rgba(59, 130, 246, 0.035) !important;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      background-color: rgba(56, 189, 248, 0.05) !important;
    }
  }
}

/* 顶部品牌表格标题 */
.table-brand-header {
  display: flex;
  align-items: center;
  gap: 10px;

  .brand-icon-wrapper {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(139, 92, 246, 0.15));
    display: flex;
    align-items: center;
    justify-content: center;
    color: #3b82f6;
    font-size: 16px;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      background: linear-gradient(135deg, rgba(59, 130, 246, 0.25), rgba(139, 92, 246, 0.25));
      color: #60a5fa;
    }
  }

  .table-title-text {
    font-size: 16px;
    font-weight: 700;
    color: #0f172a;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      color: #f8fafc;
    }
  }

  .count-badge {
    padding: 2px 8px;
    border-radius: 12px;
    font-size: 12px;
    font-weight: 600;
    background: rgba(59, 130, 246, 0.1);
    color: #2563eb;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      background: rgba(59, 130, 246, 0.2);
      color: #93c5fd;
    }
  }
}

.report-code-pill {
  padding: 3px 8px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 12px;
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    background: rgba(30, 41, 59, 0.7);
    color: #cbd5e1;
    border-color: rgba(255, 255, 255, 0.1);
  }
}

.report-title-link {
  color: #2563eb;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: #1d4ed8;
    text-decoration: underline;
  }

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    color: #60a5fa;

    &:hover {
      color: #93c5fd;
    }
  }
}

.text-gray-muted {
  color: #94a3b8;
}

.user-name-text {
  font-weight: 500;
  color: #334155;

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    color: #cbd5e1;
  }
}

/* ──────────────────────────────────────────
   弹窗与美化数据大屏光暗自适应
   ────────────────────────────────────────── */
:deep(.report-preview-dialog) {
  width: 1100px !important;
  max-width: calc(100vw - 48px) !important;
  margin-left: auto !important;
  margin-right: auto !important;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  background: #ffffff;

  @media (max-width: 1150px) {
    width: calc(100vw - 48px) !important;
  }

  @media (max-width: 768px) {
    width: calc(100vw - 24px) !important;
    max-width: calc(100vw - 24px) !important;
  }

  .el-dialog__header {
    padding: 14px 16px 14px 24px;
    margin-right: 0;
    border-bottom: 1px solid #f1f5f9;
    background: #ffffff;
  }

  .el-dialog__headerbtn {
    display: none !important;
    pointer-events: none !important;
    top: 18px;
    right: 20px;
    width: 32px;
    height: 32px;
    border-radius: 8px;

    .el-icon {
      font-size: 18px;
      color: #64748b;
    }

    &:hover {
      background-color: #f1f5f9;

      .el-icon {
        color: #0f172a;
      }
    }
  }

  .el-dialog__body {
    display: flex;
    flex-direction: column;
    min-height: 0;
    padding: 12px 16px 20px;
    background: #f8fafc;
    box-sizing: border-box;
  }
}

.drawer-header-custom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.custom-close-btn {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  width: 30px !important;
  height: 30px !important;
  border-radius: 50% !important;
  background-color: #f87171 !important;
  color: #ffffff !important;
  border: none !important;
  cursor: pointer !important;
  font-size: 15px !important;
  line-height: 1 !important;
  padding: 0 !important;
  margin-left: 10px !important;
  margin-right: -4px !important;
  transition: all 0.2s ease !important;
  z-index: 99 !important;
  position: relative !important;

  &:hover {
    background-color: #ef4444 !important;
    transform: scale(1.1) !important;
  }
  &:active {
    transform: scale(0.92) !important;
  }
}

.drawer-title-box {
  display: flex;
  align-items: center;
  gap: 10px;

  .drawer-icon-glow {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    background: linear-gradient(135deg, #3b82f6, #8b5cf6);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #ffffff;
    font-size: 18px;
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
  }

  .drawer-title {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      color: #f8fafc;
    }
  }

  .drawer-badge {
    font-size: 11px;
    font-weight: 600;
    border-radius: 6px;
    padding: 3px 10px;
    background: rgba(59, 130, 246, 0.1) !important;
    color: #2563eb !important;
    border: 1px solid rgba(59, 130, 246, 0.25) !important;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      background: rgba(59, 130, 246, 0.2) !important;
      color: #93c5fd !important;
      border-color: rgba(59, 130, 246, 0.4) !important;
    }
  }
}

.drawer-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  //margin-right: 56px;

  .refine-btn {
    background: linear-gradient(135deg, #f59e0b, #d97706) !important;
    border: 1px solid rgba(217, 119, 6, 0.3) !important;
    color: #ffffff !important;
    font-weight: 600;
    box-shadow: 0 3px 10px rgba(245, 158, 11, 0.25);
    transition: all 0.25s ease;

    &:hover {
      background: linear-gradient(135deg, #d97706, #b45309) !important;
      box-shadow: 0 5px 15px rgba(245, 158, 11, 0.4);
      transform: translateY(-1px);
    }

    &:active {
      transform: translateY(0);
    }
  }

  .el-button--primary {
    background: linear-gradient(135deg, #2563eb, #3b82f6) !important;
    border: 1px solid rgba(37, 99, 235, 0.3) !important;
    color: #ffffff !important;
    font-weight: 600;
    box-shadow: 0 3px 10px rgba(37, 99, 235, 0.25);
    transition: all 0.25s ease;

    &:hover {
      background: linear-gradient(135deg, #1d4ed8, #2563eb) !important;
      box-shadow: 0 5px 15px rgba(37, 99, 235, 0.4);
      transform: translateY(-1px);
    }
  }

  /* 适配暗黑模式 */
  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    .refine-btn {
      background: linear-gradient(135deg, #d97706, #b45309) !important;
      border-color: rgba(245, 158, 11, 0.4) !important;
      box-shadow: 0 4px 14px rgba(245, 158, 11, 0.35);

      &:hover {
        background: linear-gradient(135deg, #f59e0b, #d97706) !important;
        box-shadow: 0 6px 18px rgba(245, 158, 11, 0.5);
      }
    }

    .el-button--primary {
      background: linear-gradient(135deg, #1d4ed8, #2563eb) !important;
      border-color: rgba(96, 165, 250, 0.4) !important;
      box-shadow: 0 4px 14px rgba(37, 99, 235, 0.35);

      &:hover {
        background: linear-gradient(135deg, #2563eb, #3b82f6) !important;
        box-shadow: 0 6px 18px rgba(37, 99, 235, 0.5);
      }
    }
  }
}

.report-drawer-body {
  padding: 8px 6px 16px;
  height: calc(88vh - 75px);
  max-height: calc(88vh - 75px);
  min-height: 0;
  box-sizing: border-box;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.paper-preview-box {
  background: #ffffff;
  padding: 28px 36px;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
  border: 1px solid #e2e8f0;
  color: #1e293b;
  transition: all 0.3s;

  @media (max-width: 768px) {
    padding: 18px 20px;
  }

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    background: #0f172a !important;
    border-color: rgba(255, 255, 255, 0.08) !important;
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4) !important;
    color: #e2e8f0 !important;
  }
}

.paper-header-decoration {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .confidential-badge {
    font-size: 12px;
    font-weight: 600;
    color: #3b82f6;
    background: rgba(59, 130, 246, 0.08);
    padding: 4px 10px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    gap: 6px;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      background: rgba(59, 130, 246, 0.2);
      color: #93c5fd;
    }
  }

  .serial-code {
    font-size: 13px;
    font-family: monospace;
    color: #94a3b8;
  }
}

.paper-title {
  font-size: 26px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 12px 0;
  line-height: 1.3;

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    color: #f8fafc;
  }
}

.paper-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  color: #64748b;
  font-size: 13px;

  span {
    display: flex;
    align-items: center;
    gap: 5px;
  }

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    color: #94a3b8;
  }
}

.paper-divider {
  margin: 20px 0;
}

/* ────────── KPI 数据统计卡片 ────────── */
.report-stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
  padding: 16px 20px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  transition: all 0.25s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06);
  }

  .stat-icon-wrapper {
    width: 44px;
    height: 44px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
  }

  .stat-value {
    font-size: 22px;
    font-weight: 800;
    line-height: 1.2;
  }

  .stat-label {
    font-size: 12px;
    color: #64748b;
    margin-top: 2px;
  }

  /* 4 种高端主题配色 */
  &.stat-card-indigo {
    background: linear-gradient(135deg, rgba(99, 102, 241, 0.06), rgba(99, 102, 241, 0.12));
    border-color: rgba(99, 102, 241, 0.2);
    .stat-icon-wrapper { background: rgba(99, 102, 241, 0.15); }
    .stat-value { color: #4f46e5; }
  }

  &.stat-card-blue {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.06), rgba(59, 130, 246, 0.12));
    border-color: rgba(59, 130, 246, 0.2);
    .stat-icon-wrapper { background: rgba(59, 130, 246, 0.15); }
    .stat-value { color: #2563eb; }
  }

  &.stat-card-emerald {
    background: linear-gradient(135deg, rgba(16, 185, 129, 0.06), rgba(16, 185, 129, 0.12));
    border-color: rgba(16, 185, 129, 0.2);
    .stat-icon-wrapper { background: rgba(16, 185, 129, 0.15); }
    .stat-value { color: #059669; }
  }

  &.stat-card-amber {
    background: linear-gradient(135deg, rgba(245, 158, 11, 0.06), rgba(245, 158, 11, 0.12));
    border-color: rgba(245, 158, 11, 0.2);
    .stat-icon-wrapper { background: rgba(245, 158, 11, 0.15); }
    .stat-value { color: #d97706; }
  }

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    background: rgba(30, 41, 59, 0.6) !important;
    border-color: rgba(255, 255, 255, 0.08) !important;

    .stat-label { color: #94a3b8; }

    &.stat-card-indigo .stat-value { color: #818cf8; }
    &.stat-card-blue .stat-value { color: #60a5fa; }
    &.stat-card-emerald .stat-value { color: #34d399; }
    &.stat-card-amber .stat-value { color: #fbbf24; }
  }
}

/* ────────── ECharts 图表卡片 ────────── */
.report-chart-section {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    background: #1e293b !important;
    border-color: rgba(255, 255, 255, 0.08) !important;
  }
}

.chart-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .chart-title-group {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;

    .chart-header-icon {
      color: #3b82f6;
      font-size: 18px;
    }

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      color: #f8fafc;
    }
  }

  .chart-toggle-group {
    :deep(.el-radio-button__inner) {
      background: #ffffff;
      border-color: #cbd5e1;
      color: #475569;
      font-weight: 600;
      font-size: 12px;
      padding: 6px 14px;
      transition: all 0.25s ease;
    }

    :deep(.el-radio-button:first-child .el-radio-button__inner) {
      border-radius: 6px 0 0 6px;
    }

    :deep(.el-radio-button:last-child .el-radio-button__inner) {
      border-radius: 0 6px 6px 0;
    }

    :deep(.el-radio-button.is-active .el-radio-button__inner) {
      background: linear-gradient(135deg, #2563eb, #3b82f6) !important;
      border-color: #2563eb !important;
      color: #ffffff !important;
      box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3) !important;
    }

    :deep(.el-radio-button__inner:hover) {
      color: #2563eb;
    }

    /* 暗黑模式自适应 */
    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      :deep(.el-radio-button__inner) {
        background: #0f172a !important;
        border-color: rgba(255, 255, 255, 0.15) !important;
        color: #94a3b8 !important;
      }

      :deep(.el-radio-button.is-active .el-radio-button__inner) {
        background: linear-gradient(135deg, #3b82f6, #60a5fa) !important;
        border-color: #3b82f6 !important;
        color: #ffffff !important;
        box-shadow: 0 2px 10px rgba(59, 130, 246, 0.4) !important;
      }

      :deep(.el-radio-button__inner:hover) {
        color: #60a5fa !important;
      }
    }
  }
}

.pretty-chart-box {
  width: 100%;
  height: 320px;
}

/* ────────── 高管摘要 Banner ────────── */
.executive-summary-banner {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-left: 4px solid #0284c7;
  padding: 18px 22px;
  border-radius: 10px;
  margin-bottom: 24px;

  .summary-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 700;
    color: #0369a1;
    font-size: 15px;
    margin-bottom: 8px;
  }

  .summary-body {
    color: #334155;
    font-size: 14px;
    line-height: 1.65;
  }

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    background: linear-gradient(135deg, rgba(3, 105, 161, 0.25) 0%, rgba(14, 165, 233, 0.18) 100%) !important;
    border-left-color: #38bdf8 !important;

    .summary-header { color: #7dd3fc; }
    .summary-body { color: #e2e8f0; }
  }
}

/* ────────── 核心发现 Key Findings ────────── */
.key-findings-section {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  padding: 20px;
  border-radius: 10px;
  margin-bottom: 24px;

  .findings-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 700;
    color: #1e293b;
    margin-bottom: 12px;
  }

  .findings-list {
    list-style: none;
    padding: 0;
    margin: 0;

    li {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      margin-bottom: 10px;

      .finding-num {
        width: 20px;
        height: 20px;
        border-radius: 50%;
        background: #3b82f6;
        color: #fff;
        font-size: 11px;
        font-weight: 700;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        margin-top: 2px;
      }

      .finding-text {
        font-size: 14px;
        color: #334155;
        line-height: 1.6;
      }
    }
  }

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    background: #1e293b !important;
    border-color: rgba(255, 255, 255, 0.08) !important;

    .findings-header { color: #f1f5f9; }
    .findings-list li .finding-text { color: #cbd5e1; }
  }
}

/* ────────── 分章节正文卡片与 Markdown 样式 ────────── */
.paper-content-v2-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.report-content-card {
  background: transparent !important;
  padding: 10px 0;
}

.report-markdown-content {
  line-height: 1.8;
  font-size: 14.5px;
  color: #334155;

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    color: #cbd5e1;
  }

  :deep(.report-h1) {
    font-size: 22px;
    font-weight: 800;
    color: #0f172a;
    border-bottom: 2px solid #e2e8f0;
    padding-bottom: 10px;
    margin: 24px 0 16px 0;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      color: #f8fafc !important;
      border-color: rgba(255, 255, 255, 0.1) !important;
    }
  }

  :deep(.report-h2) {
    font-size: 18px;
    font-weight: 700;
    color: #1e293b;
    margin: 20px 0 12px 0;
    display: flex;
    align-items: center;

    &::before {
      content: '';
      display: inline-block;
      width: 4px;
      height: 16px;
      background: #3b82f6;
      border-radius: 2px;
      margin-right: 8px;
    }

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      color: #f1f5f9 !important;
    }
  }

  :deep(.report-h3) {
    font-size: 16px;
    font-weight: 600;
    color: #334155;
    margin: 16px 0 10px 0;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      color: #cbd5e1 !important;
    }
  }

  :deep(.report-li) {
    margin-left: 20px;
    margin-bottom: 6px;
  }

  :deep(.code-block-box) {
    background: #0f172a;
    color: #f8fafc;
    padding: 14px 18px;
    border-radius: 8px;
    overflow-x: auto;
    font-family: monospace;
    margin: 12px 0;
  }

  :deep(.inline-code-box) {
    background: rgba(59, 130, 246, 0.08);
    color: #2563eb;
    padding: 2px 6px;
    border-radius: 4px;
    font-family: monospace;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      background: rgba(59, 130, 246, 0.2) !important;
      color: #93c5fd !important;
    }
  }

  /* 彻底解决表格在暗黑模式下的颜色与背景不兼容 Bug */
  :deep(.report-table-wrapper) {
    overflow-x: auto;
    margin: 16px 0;
    border-radius: 10px;
    border: 1px solid #e2e8f0;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      border-color: rgba(255, 255, 255, 0.1) !important;
    }
  }

  :deep(.report-table) {
    width: 100%;
    border-collapse: collapse;
    font-size: 13.5px;

    thead tr {
      background: linear-gradient(135deg, #2563eb, #3b82f6);
      color: #ffffff;
    }

    th {
      padding: 12px 16px;
      font-weight: 700;
      text-align: left;
      border: none;
    }

    td {
      padding: 12px 16px;
      border-bottom: 1px solid #f1f5f9;
      color: #334155;
      background: transparent;
    }

    tbody tr:nth-child(even) {
      background: #f8fafc;
    }

    tbody tr:nth-child(odd) {
      background: #ffffff;
    }

    tbody tr:hover {
      background: rgba(59, 130, 246, 0.04);
    }
  }
}

/* 全局与局部穿透暗黑表格控制 */
:deep(html.dark) .report-table,
:deep(.dark) .report-table,
html.dark .report-table,
.dark .report-table {
  thead tr {
    background: linear-gradient(135deg, #1d4ed8, #2563eb) !important;
    color: #ffffff !important;
  }

  td {
    border-bottom-color: rgba(255, 255, 255, 0.08) !important;
    color: #cbd5e1 !important;
    background: transparent !important;
  }

  tbody tr:nth-child(even) {
    background: rgba(30, 41, 59, 0.7) !important;
  }

  tbody tr:nth-child(odd) {
    background: rgba(15, 23, 42, 0.7) !important;
  }

  tbody tr:hover {
    background: rgba(59, 130, 246, 0.2) !important;
  }
}

/* 状态 Badge 自适应 */
.report-markdown-content :deep(.report-badge),
:deep(.report-badge) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;

  &.badge-success {
    background: rgba(16, 185, 129, 0.15);
    color: #059669;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      background: rgba(16, 185, 129, 0.25) !important;
      color: #34d399 !important;
    }
  }
  &.badge-danger {
    background: rgba(239, 68, 68, 0.15);
    color: #dc2626;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      background: rgba(239, 68, 68, 0.25) !important;
      color: #f87171 !important;
    }
  }
  &.badge-warning {
    background: rgba(245, 158, 11, 0.15);
    color: #d97706;

    :deep(html.dark) &,
    :deep(.dark) &,
    .dark & {
      background: rgba(245, 158, 11, 0.25) !important;
      color: #fbbf24 !important;
    }
  }
}

/* ────────── 行动计划 Action Plan ────────── */
.report-action-plan-section {
  margin-top: 25px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  padding: 20px;
  border-radius: 10px;

  .action-plan-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 700;
    color: #1e293b;
    margin-bottom: 14px;
  }

  :deep(html.dark) &,
  :deep(.dark) &,
  .dark & {
    background: #1e293b !important;
    border-color: rgba(255, 255, 255, 0.08) !important;
    .action-plan-header { color: #f1f5f9; }
  }
}

/* 页脚 */
.paper-footer {
  margin-top: 36px;
  text-align: center;

  .footer-gradient-line {
    height: 2px;
    background: linear-gradient(90deg, transparent, #3b82f6, transparent);
    margin-bottom: 16px;
  }

  .footer-disclaimer {
    font-size: 12px;
    color: #94a3b8;
    margin-bottom: 4px;
  }

  .footer-brand {
    font-size: 13px;
    color: #64748b;
  }
}

/* ──────────────────────────────────────────
   打印专属 PDF 样式支持 (@media print)
   ────────────────────────────────────────── */
@media print {
  body * {
    visibility: hidden;
  }
  #report-drawer-print-area, #report-drawer-print-area * {
    visibility: visible;
  }
  #report-drawer-print-area {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    padding: 0;
  }
  .paper-preview-box {
    box-shadow: none !important;
    border: none !important;
    padding: 0 !important;
    background: #ffffff !important;
    color: #1e293b !important;
  }
  .drawer-actions, .refine-btn {
    display: none !important;
  }

  // 防止表格在分页时行被从中间截断
  table {
    page-break-inside: auto;
  }
  tr {
    page-break-inside: avoid;
    page-break-after: auto;
  }
  thead {
    display: table-header-group;
  }
}
</style>

<!-- ──────────────────────────────────────────
   暗黑模式 (Dark Mode) 全局深度穿透兼容防护
   ────────────────────────────────────────── -->
<style lang="scss">
.report-preview-dialog {
  .el-dialog__headerbtn {
    display: none !important;
    pointer-events: none !important;
    width: 0 !important;
    height: 0 !important;
    overflow: hidden !important;
  }
}

html.dark,
.dark {
  .report-preview-dialog,
  .report-preview-drawer {
    background: #0f172a !important;
    border: 1px solid rgba(255, 255, 255, 0.1) !important;

    .el-dialog__header,
    .el-drawer__header {
      background: #0f172a !important;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08) !important;
      margin-bottom: 0 !important;
    }
    .el-dialog__body,
    .el-drawer__body {
      background: #090d16 !important;
    }
    .el-dialog__headerbtn,
    .el-drawer__close-btn {
      color: #94a3b8 !important;
      width: 32px;
      height: 32px;
      border-radius: 8px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: all 0.25s ease;

      .el-dialog__close, .el-icon, i, svg {
        color: #94a3b8 !important;
        font-size: 18px;
        transition: color 0.25s ease;
      }

      &:hover {
        background-color: rgba(255, 255, 255, 0.12) !important;

        .el-dialog__close, .el-icon, i, svg {
          color: #ffffff !important;
        }
      }
    }
  }

  .paper-preview-box {
    background: #0f172a !important;
    border-color: rgba(255, 255, 255, 0.1) !important;
    color: #e2e8f0 !important;
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5) !important;
  }

  .paper-title {
    color: #f8fafc !important;
  }

  .paper-meta {
    color: #94a3b8 !important;
    span { color: #94a3b8 !important; }
  }

  .paper-divider {
    border-color: rgba(255, 255, 255, 0.1) !important;
  }

  /* 核心：Markdown 正文与标题颜色在暗黑模式下的高亮自适应 */
  .report-markdown-content {
    color: #e2e8f0 !important;

    h1, h2, h3, h4, h5, h6,
    .report-h1, .report-h2, .report-h3, .report-h4 {
      color: #f8fafc !important;
    }

    .report-h1 {
      border-bottom-color: rgba(255, 255, 255, 0.12) !important;
    }

    .report-h2::before {
      background: #60a5fa !important;
    }

    p, li, span, div, strong, em {
      color: #e2e8f0 !important;
    }

    hr {
      border-color: rgba(255, 255, 255, 0.1) !important;
    }

    .inline-code-box, code {
      background: rgba(96, 165, 250, 0.18) !important;
      color: #93c5fd !important;
      border: 1px solid rgba(96, 165, 250, 0.25) !important;
    }

    .code-block-box, pre {
      background: #020617 !important;
      color: #f8fafc !important;
      border: 1px solid rgba(255, 255, 255, 0.1) !important;
    }
  }

  /* 核心：Markdown 嵌套表格在暗黑模式下的交替背景与文字色自适应 */
  .report-table-wrapper {
    border-color: rgba(255, 255, 255, 0.12) !important;
    background: transparent !important;
  }

  .report-table {
    border-color: rgba(255, 255, 255, 0.1) !important;
    background: transparent !important;

    thead tr {
      background: linear-gradient(135deg, #1d4ed8, #2563eb) !important;

      th {
        color: #ffffff !important;
        background: transparent !important;
      }
    }

    td {
      border-bottom-color: rgba(255, 255, 255, 0.08) !important;
      color: #e2e8f0 !important;
      background: transparent !important;
    }

    tbody tr:nth-child(even) {
      background: rgba(30, 41, 59, 0.75) !important;
    }

    tbody tr:nth-child(odd) {
      background: rgba(15, 23, 42, 0.75) !important;
    }

    tbody tr:hover {
      background: rgba(59, 130, 246, 0.25) !important;

      td {
        background: transparent !important;
        color: #ffffff !important;
      }
    }
  }

  /* 建议改进与行动计划看板 Element Table 暗黑适配 */
  .report-action-plan-section {
    .action-plan-header {
      color: #f8fafc !important;
    }

    .el-table {
      background: transparent !important;
      --el-table-bg-color: transparent !important;
      --el-table-tr-bg-color: transparent !important;

      th, tr, td {
        background: transparent !important;
        border-color: rgba(255, 255, 255, 0.08) !important;
        color: #e2e8f0 !important;
      }

      .el-table__row--striped td {
        background: rgba(30, 41, 59, 0.5) !important;
      }

      .el-table__row:hover > td {
        background: rgba(59, 130, 246, 0.2) !important;
      }
    }
  }

  /* 摘要与 Key Findings 模块暗色模式适配 */
  .executive-summary-banner {
    background: rgba(59, 130, 246, 0.12) !important;
    border-color: rgba(59, 130, 246, 0.3) !important;

    .summary-header {
      color: #60a5fa !important;
    }

    .summary-body {
      color: #e2e8f0 !important;
    }
  }

  .key-findings-section {
    background: rgba(30, 41, 59, 0.6) !important;
    border-color: rgba(255, 255, 255, 0.1) !important;

    .findings-header {
      color: #f1f5f9 !important;
    }

    .finding-text {
      color: #cbd5e1 !important;
    }
  }
}

/* ============================================
   PolarisReportEngine 组件暗黑模式深度适配
   (由于 append-to-body 弹窗，需在 report.vue 全局覆盖)
   ============================================ */
html.dark,
.dark {
  /* 引擎整体容器 */
  .polaris-report-engine.theme-glass-light {
    background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%) !important;

    /* Banner 头部 */
    .engine-report-banner {
      background: linear-gradient(135deg, #020617 0%, #0f172a 100%) !important;
      border-color: rgba(99, 102, 241, 0.3) !important;

      .banner-badge {
        background: rgba(99, 102, 241, 0.3) !important;
        color: #a5b4fc !important;
        border-color: rgba(165, 180, 252, 0.4) !important;
      }

      .banner-chip {
        background: rgba(255, 255, 255, 0.1) !important;
        color: #94a3b8 !important;
      }

      .banner-title {
        color: #f8fafc !important;
      }

      .banner-meta {
        color: #64748b !important;
      }
    }

    /* 摘要卡片 */
    .summary-glass-card {
      background: rgba(30, 41, 59, 0.8) !important;
      border-color: rgba(245, 158, 11, 0.4) !important;
      border-left-color: #f59e0b !important;

      .card-header {
        color: #fbbf24 !important;
      }

      .summary-content {
        color: #e2e8f0 !important;
      }
    }

    /* KPI 统计卡片网格 */
    .kpi-grid-container {
      .kpi-stat-card {
        background: rgba(30, 41, 59, 0.8) !important;
        border-color: rgba(255, 255, 255, 0.1) !important;

        .kpi-stat-icon {
          background: rgba(59, 130, 246, 0.2) !important;
          color: #60a5fa !important;
        }

        .kpi-label {
          color: #94a3b8 !important;
        }

        .kpi-value {
          color: #f8fafc !important;
        }
      }
    }

    /* 对比矩阵卡片 */
    .compare-matrix-card {
      background: rgba(30, 41, 59, 0.8) !important;
      border-color: rgba(255, 255, 255, 0.1) !important;

      .card-header {
        color: #f8fafc !important;
      }

      .compare-column {
        background: rgba(15, 23, 42, 0.6) !important;
        border-color: rgba(255, 255, 255, 0.1) !important;

        .column-title {
          color: #e2e8f0 !important;
        }

        .column-list li {
          color: #cbd5e1 !important;
        }

        &.compare-type-highlight {
          background: rgba(59, 130, 246, 0.15) !important;
          border-color: rgba(96, 165, 250, 0.4) !important;
        }
      }
    }

    /* 图表卡片 */
    .chart-block-card {
      background: rgba(30, 41, 59, 0.8) !important;
      border-color: rgba(255, 255, 255, 0.1) !important;

      .card-header {
        color: #f8fafc !important;
      }
    }

    /* SWOT 矩阵卡片 */
    .swot-matrix-card {
      background: rgba(30, 41, 59, 0.8) !important;
      border-color: rgba(255, 255, 255, 0.1) !important;

      .card-header {
        color: #f8fafc !important;
      }

      .swot-box {
        border-color: rgba(255, 255, 255, 0.1) !important;

        .swot-tag {
          color: #e2e8f0 !important;
        }

        ul li {
          color: #cbd5e1 !important;
        }
      }

      .swot-s {
        background: rgba(16, 185, 129, 0.12) !important;
        .swot-tag { background: rgba(16, 185, 129, 0.25) !important; }
        li::before { background: #34d399 !important; }
      }

      .swot-w {
        background: rgba(239, 68, 68, 0.12) !important;
        .swot-tag { background: rgba(239, 68, 68, 0.25) !important; }
        li::before { background: #f87171 !important; }
      }

      .swot-o {
        background: rgba(59, 130, 246, 0.12) !important;
        .swot-tag { background: rgba(59, 130, 246, 0.25) !important; }
        li::before { background: #60a5fa !important; }
      }

      .swot-t {
        background: rgba(245, 158, 11, 0.12) !important;
        .swot-tag { background: rgba(245, 158, 11, 0.25) !important; }
        li::before { background: #fbbf24 !important; }
      }
    }

    /* 智能章节卡片 - 左侧蓝色色条 */
    .smart-section-card {
      background: rgba(30, 41, 59, 0.8) !important;
      border: 1px solid rgba(255, 255, 255, 0.1) !important;
      border-left: 4px solid #6366f1 !important;

      .section-card-header {
        border-bottom-color: rgba(255, 255, 255, 0.1) !important;

        .section-badge-dot {
          background: #818cf8 !important;
        }

        .section-title {
          color: #f8fafc !important;
        }
      }

      /* 内嵌表格 - 深度穿透 Element Plus 样式 */
      .el-table {
        --el-table-bg-color: transparent !important;
        --el-table-tr-bg-color: transparent !important;
        --el-table-header-bg-color: rgba(15, 23, 42, 0.9) !important;
        --el-table-row-hover-bg-color: rgba(99, 102, 241, 0.12) !important;
        --el-table-border-color: rgba(255, 255, 255, 0.1) !important;

        background: transparent !important;

        th.el-table__cell {
          background: rgba(15, 23, 42, 0.9) !important;
          color: #e2e8f0 !important;
          border-bottom-color: rgba(255, 255, 255, 0.1) !important;
        }

        td.el-table__cell {
          color: #e2e8f0 !important;
          border-bottom-color: rgba(255, 255, 255, 0.08) !important;
          background: transparent !important;
        }

        .el-table__row {
          background: transparent !important;

          &:hover > td {
            background: rgba(99, 102, 241, 0.12) !important;
          }
        }

        .el-table__row--striped td.el-table__cell {
          background: rgba(30, 41, 59, 0.5) !important;
        }

        .el-table--border .el-table__cell {
          border-right-color: rgba(255, 255, 255, 0.08) !important;
        }
      }

      .table-inline-code {
        background: rgba(99, 102, 241, 0.2) !important;
        color: #a5b4fc !important;
      }
    }

    /* Markdown 正文 */
    .markdown-body {
      color: #e2e8f0 !important;

      .highlight-strong {
        color: #f8fafc !important;
      }

      .pill-code {
        background: rgba(99, 102, 241, 0.2) !important;
        color: #a5b4fc !important;
      }

      .custom-li .li-dot {
        background: #818cf8 !important;
      }

      .custom-num-item .num-badge {
        background: #6366f1 !important;
      }
    }

    /* 行动计划看板 - 深色表头 + 左侧绿色色条 */
    .action-plan-card {
      background: rgba(30, 41, 59, 0.8) !important;
      border: 1px solid rgba(255, 255, 255, 0.1) !important;
      border-left: 4px solid #10b981 !important;

      .card-header {
        color: #10b981 !important;

        .header-icon.glow-emerald {
          color: #10b981 !important;
        }
      }

      .el-table {
        --el-table-bg-color: transparent !important;
        --el-table-tr-bg-color: transparent !important;
        --el-table-header-bg-color: transparent !important;
        --el-table-row-hover-bg-color: rgba(16, 185, 129, 0.12) !important;
        --el-table-border-color: rgba(255, 255, 255, 0.1) !important;

        background: transparent !important;

        th.el-table__cell {
          background: rgba(15, 23, 42, 0.9) !important;
          color: #e2e8f0 !important;
          border-bottom-color: rgba(255, 255, 255, 0.1) !important;
        }

        td.el-table__cell {
          color: #e2e8f0 !important;
          border-bottom-color: rgba(255, 255, 255, 0.08) !important;
          background: transparent !important;
        }

        .el-table__row {
          background: transparent !important;

          &:hover > td {
            background: rgba(16, 185, 129, 0.12) !important;
          }
        }

        .el-table__row--striped td.el-table__cell {
          background: rgba(16, 185, 129, 0.08) !important;
        }
      }
    }
  }
}
</style>
