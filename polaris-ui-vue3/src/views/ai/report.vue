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
        <el-table-column label="报告标题" prop="reportTitle" min-width="240" show-overflow-tooltip>
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
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link icon="View" @click="handlePreview(scope.row)">
              查看美化报告
            </el-button>
            <el-button size="small" type="danger" link icon="Delete" @click="handleDelete(scope.row)">
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

    <!-- 报告在线预览大屏抽屉 (高质感商业分析与图表化展示) -->
    <el-drawer
      v-model="previewVisible"
      size="80%"
      class="report-preview-drawer"
      :destroy-on-close="true"
      :before-close="handleDrawerClose"
    >
      <template #header>
        <div class="drawer-header-custom">
          <div class="drawer-title-box">
            <span class="drawer-icon-glow">
              <el-icon><data-board /></el-icon>
            </span>
            <span class="drawer-title">{{ reportTitle || currentReport.reportTitle }}</span>
            <el-tag size="small" type="primary" effect="dark" class="drawer-badge">高质感数据大屏</el-tag>
          </div>
          <div class="drawer-actions">
            <el-button 
              type="warning" 
              size="small" 
              icon="MagicStick" 
              :loading="refining"
              @click="handleAiRefine"
              class="refine-btn"
            >
              {{ refining ? 'AI 正在深度重塑...' : 'AI 智能重塑美化' }}
            </el-button>
            <el-button type="primary" size="small" icon="Download" @click="handlePrint">
              导出 PDF 报告
            </el-button>
          </div>
        </div>
      </template>

      <div id="report-drawer-print-area" class="report-drawer-body">
        <div class="paper-preview-box">
          <!-- 头部装饰条 -->
          <div class="paper-header-decoration">
            <span class="confidential-badge">
              <el-icon><lock /></el-icon> 内部报告 · Polaris-AI 智能可视化分析引擎
            </span>
            <span class="serial-code">编号：{{ currentReport.reportCode || 'REP-' + currentReport.id }}</span>
          </div>

          <h1 class="paper-title">{{ reportTitle || currentReport.reportTitle }}</h1>

          <div class="paper-meta">
            <span><el-icon><user /></el-icon> 创建人：{{ currentReport.createBy || 'admin' }}</span>
            <span><el-icon><calendar /></el-icon> 归档时间：{{ currentReport.createTime }}</span>
            <span v-if="currentReport.agentCode"><el-icon><cpu /></el-icon> 智能体：{{ currentReport.agentCode }}</span>
          </div>

          <el-divider class="paper-divider" />

          <!-- AI 智能提炼的高管极简摘要 Banner -->
          <div v-if="refinedSchema && refinedSchema.executiveSummary" class="executive-summary-banner">
            <div class="summary-header">
              <el-icon><opportunity /></el-icon>
              <span>高管极简摘要与决策建议 (Executive Summary)</span>
            </div>
            <div class="summary-body">
              {{ refinedSchema.executiveSummary }}
            </div>
          </div>

          <!-- KPI 核心指标概览卡片 (Stat Cards) -->
          <div v-if="reportStats" class="report-stats-row">
            <div 
              v-for="(stat, idx) in reportStats" 
              :key="idx" 
              class="stat-card" 
              :class="'stat-card-' + stat.color"
            >
              <div class="stat-icon-wrapper">
                <span class="stat-emoji">{{ stat.emoji }}</span>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stat.value }}</div>
                <div class="stat-label">{{ stat.label }}</div>
              </div>
            </div>
          </div>

          <!-- AI 智能提取的核心发现 Key Findings -->
          <div v-if="refinedSchema && refinedSchema.keyFindings && refinedSchema.keyFindings.length > 0" class="key-findings-section">
            <div class="findings-header">
              <el-icon><star /></el-icon>
              <span>核心关键发现与研判 (Key Findings)</span>
            </div>
            <ul class="findings-list">
              <li v-for="(finding, idx) in refinedSchema.keyFindings" :key="idx">
                <span class="finding-num">{{ idx + 1 }}</span>
                <span class="finding-text">{{ finding }}</span>
              </li>
            </ul>
          </div>

          <!-- 动态 ECharts 数据可视化图表卡片 -->
          <div v-if="hasChartData" class="report-chart-section">
            <div class="chart-section-header">
              <div class="chart-title-group">
                <el-icon class="chart-header-icon"><histogram /></el-icon>
                <span>数据维度与对比可视化图表</span>
              </div>
              <div class="chart-toggle-group">
                <el-radio-group v-model="activeChartType" size="small" @change="switchChartType">
                  <el-radio-button label="bar">柱状图</el-radio-button>
                  <el-radio-button label="line">趋势图</el-radio-button>
                </el-radio-group>
              </div>
            </div>
            <div id="pretty-report-chart" class="pretty-chart-box"></div>
          </div>

          <!-- 正文卡片分段展示 (渲染 Markdown 与高质感渐变表头 Vue 表格) -->
          <div class="paper-content-v2-container">
            <div v-for="(section, idx) in reportSections" :key="idx" class="report-content-card">
              <div class="markdown-body report-markdown-content" v-html="renderMarkdown(section)"></div>
            </div>
          </div>

          <!-- AI 行动计划看板 (Action Plan) -->
          <div v-if="refinedSchema && refinedSchema.actionPlan && refinedSchema.actionPlan.length > 0" class="report-action-plan-section">
            <div class="action-plan-header">
              <el-icon style="color: #10b981;"><checked /></el-icon>
              <span>建议改进与行动计划看板 (Action Plan)</span>
            </div>
            <el-table :data="refinedSchema.actionPlan" border stripe style="width: 100%;">
              <el-table-column label="优先级" prop="priority" width="100" align="center">
                <template #default="scope">
                  <el-tag :type="scope.row.priority === 'P1' ? 'danger' : (scope.row.priority === 'P2' ? 'warning' : 'info')" effect="dark" size="small">
                    {{ scope.row.priority }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="改进措施 / 行动建议" prop="action" min-width="240" />
              <el-table-column label="建议责任部门/人" prop="owner" width="160" align="center" />
            </el-table>
          </div>

          <!-- 页脚声明 -->
          <div class="paper-footer">
            <div class="footer-gradient-line"></div>
            <p class="footer-disclaimer">本报告由 Polaris-AI 数据分析引擎结构化渲染，数据真实有效，仅供决策参考</p>
            <p class="footer-brand">Powered by <strong>Polaris-Vue</strong> · AI Report Visualization Engine</p>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import {delReport, listReports, refineReport} from '@/api/ai/report'

export default {
  name: 'AiReport',
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
      reportSections: [],
      reportStats: null,
      hasChartData: false,
      chartConfig: null,
      activeChartType: 'bar',
      reportChartInstance: null,
      refining: false,
      refinedSchema: null
    }
  },
  created() {
    this.getList()
  },
  unmounted() {
    this.destroyChart()
  },
  methods: {
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
    // 报告详情解析与美化渲染逻辑
    // ──────────────────────────────────────────
    handlePreview(row) {
      this.currentReport = row
      this.refinedSchema = null
      this.parseAndInitReportData(row.reportContent)
      this.previewVisible = true

      if (this.hasChartData) {
        this.$nextTick(() => {
          this.initReportChart()
        })
      }
    },

    parseAndInitReportData(content) {
      if (!content) {
        this.reportContent = ''
        this.reportTitle = this.currentReport.reportTitle || '数据分析报告'
        this.reportSections = []
        this.reportStats = null
        this.hasChartData = false
        this.chartConfig = null
        return
      }

      let normalizedContent = content.replace(/\r\n/g, '\n').trim()

      // 自动过滤报告最开头的 AI 客套话与闲聊前缀
      const firstHeadingIndex = normalizedContent.search(/^#{1,3}\s+/m)
      if (firstHeadingIndex > 0) {
        normalizedContent = normalizedContent.substring(firstHeadingIndex).trim()
      } else {
        normalizedContent = normalizedContent.replace(/^(好的|收到|已为您|以下是为您|好的，已|好的，我|已成功|首先)[^\n\:]*[\:\：\n]\s*/gi, '').trim()
      }

      this.reportContent = normalizedContent

      // 强力提取报告大标题
      const titleMatch = normalizedContent.match(/^#\s*([^\n]+)$/m)
      if (titleMatch && titleMatch[1]) {
        this.reportTitle = titleMatch[1].replace(/^[🛡️📊📑📋📝\s]+/, '').trim()
      } else {
        this.reportTitle = this.currentReport.reportTitle || '智能数据分析报告'
      }

      // 按二级/三级标题切割报告分段卡片
      let cleanContent = normalizedContent.replace(/^#\s*[^\n]+\n?/, '').trim()

      // 清除头部重复的报告元数据段落
      for (let i = 0; i < 6; i++) {
        cleanContent = cleanContent.replace(/^[\s\*\-\>]*[\*\_]*(报告生成时间|评估人|评估范围|生成时间|报告时间|报告编号|编制部门|报告作者|创建人|评估对象|评估周期)[\*\_]*\s*[\：\:][^\n]*\n?/gi, '').trim()
      }

      const rawSections = cleanContent.split(/(?=^#{2,3}\s*[^\n]+)/gm)
      let parsedSections = rawSections
        .map(s => s.trim())
        .filter(s => {
          if (!s) return false
          if (!s.startsWith('#')) {
            const textOnly = s.replace(/[\s\*\-\>\:\：\.\,\n]/g, '')
            if (textOnly.length < 3) return false
          }
          return true
        })

      if (parsedSections.length === 0 && cleanContent.length > 0) {
        parsedSections = [cleanContent]
      }

      this.reportSections = parsedSections

      // 提取 KPI 数据卡片
      this.reportStats = this.extractReportStats(normalizedContent)

      // 解析 Markdown 表格数据生成 ECharts 可视化
      const parsedChart = this.parseTablesForCharts(normalizedContent)
      if (parsedChart) {
        this.hasChartData = true
        this.chartConfig = parsedChart
        this.activeChartType = 'bar'
      } else {
        this.hasChartData = false
        this.chartConfig = null
      }
    },

    // 精准生成概览统计卡片数据
    extractReportStats(content) {
      if (!content) return null

      const tables = this.extractStructuredTables(content)
      const tableCount = tables.length

      let totalRows = 0
      tables.forEach(t => {
        totalRows += t.length
      })

      const sectionMatches = content.match(/^#{1,3}\s*[^\n]+/gm)
      const sectionCount = sectionMatches ? sectionMatches.length : (this.reportSections.length || 1)
      const wordCount = content.replace(/\s+/g, '').length

      return [
        { label: '分析章节', value: sectionCount, emoji: '📚', color: 'indigo' },
        { label: '分析字数', value: wordCount, emoji: '✍️', color: 'blue' },
        { label: '数据表格', value: tableCount, emoji: '📊', color: 'emerald' },
        { label: '数据行数', value: totalRows, emoji: '⚡', color: 'amber' }
      ]
    },

    extractStructuredTables(content) {
      const tables = this.extractStructuredTablesWithHeaders(content)
      return tables.map(t => t.rows)
    },

    extractStructuredTablesWithHeaders(content) {
      if (!content) return []

      const lines = content.split('\n')
      const tables = []
      let i = 0

      while (i < lines.length) {
        let line = lines[i].trim()
        if (line.startsWith('|') && i + 1 < lines.length) {
          let nextLine = lines[i + 1].trim()
          if (nextLine.startsWith('|') && /^[|\s-:]+$/.test(nextLine)) {
            const headers = line.split('|').slice(1, -1).map(c => c.trim())
            const tableRows = []
            i += 2

            while (i < lines.length) {
              let dataLine = lines[i].trim()
              if (dataLine.startsWith('|') && dataLine.endsWith('|')) {
                const cells = dataLine.split('|').slice(1, -1).map(c => c.trim())
                tableRows.push(cells)
                i++
              } else {
                break
              }
            }
            if (headers.length > 0 && tableRows.length > 0) {
              tables.push({ headers, rows: tableRows })
            }
            continue
          }
        }
        i++
      }
      return tables
    },

    // 解析数值对比表格给 ECharts
    parseTablesForCharts(content) {
      if (!content) return null

      const tables = this.extractStructuredTablesWithHeaders(content)
      if (!tables || tables.length === 0) return null

      const validTables = tables.filter(t => {
        if (t.headers.length < 2 || t.rows.length < 1) return false
        let numericCellCount = 0
        t.rows.forEach(row => {
          row.slice(1).forEach(cell => {
            const clean = cell.replace(/[^\d.-]/g, '')
            if (clean !== '' && !isNaN(parseFloat(clean))) {
              numericCellCount++
            }
          })
        })
        const totalNumCells = t.rows.length * (t.headers.length - 1)
        const isHeaderLikeId = t.headers.some(h => /ID|序号|账号|用户名|IP|时间|日期/i.test(h))
        return (numericCellCount / totalNumCells >= 0.35) && !isHeaderLikeId
      })

      if (validTables.length === 0) return null
      const target = validTables[0]

      const xAxisData = target.rows.map(row => row[0])
      const seriesNames = target.headers.slice(1)

      const seriesDataList = seriesNames.map((name, sIdx) => {
        const data = target.rows.map(row => {
          const cellVal = row[sIdx + 1] || '0'
          const num = parseFloat(cellVal.replace(/[^\d.-]/g, ''))
          return isNaN(num) ? 0 : num
        })
        return {
          name: name,
          type: 'bar',
          data: data,
          barMaxWidth: 30,
          itemStyle: {
            borderRadius: [4, 4, 0, 0]
          }
        }
      })

      return {
        xAxisData,
        series: seriesDataList,
        legendData: seriesNames
      }
    },

    initReportChart() {
      if (!this.chartConfig) return
      this.$nextTick(() => {
        const chartDom = document.getElementById('pretty-report-chart')
        if (!chartDom) return

        if (this.reportChartInstance) {
          this.reportChartInstance.dispose()
        }

        const isDark = document.documentElement.classList.contains('dark') ||
                       document.body.classList.contains('dark') ||
                       !!document.querySelector('.dark')

        const textColor = isDark ? '#94a3b8' : '#64748b'
        const splitLineColor = isDark ? 'rgba(255, 255, 255, 0.08)' : '#f1f5f9'
        const axisLineColor = isDark ? 'rgba(255, 255, 255, 0.15)' : '#e2e8f0'

        this.reportChartInstance = echarts.init(chartDom, isDark ? 'dark' : null)

        const option = {
          backgroundColor: 'transparent',
          tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'shadow' }
          },
          legend: {
            data: this.chartConfig.legendData,
            bottom: 0,
            icon: 'roundRect',
            textStyle: { color: textColor }
          },
          grid: {
            left: '3%',
            right: '4%',
            bottom: '14%',
            top: '8%',
            containLabel: true
          },
          xAxis: {
            type: 'category',
            data: this.chartConfig.xAxisData,
            axisLabel: { interval: 0, rotate: 15, color: textColor },
            axisLine: { lineStyle: { color: axisLineColor } }
          },
          yAxis: {
            type: 'value',
            axisLabel: { color: textColor },
            splitLine: { lineStyle: { type: 'dashed', color: splitLineColor } }
          },
          color: ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'],
          series: this.chartConfig.series.map(s => ({
            ...s,
            type: this.activeChartType
          }))
        }

        this.reportChartInstance.setOption(option)
        window.addEventListener('resize', this.resizeReportChart)
      })
    },

    switchChartType() {
      this.initReportChart()
    },

    resizeReportChart() {
      if (this.reportChartInstance) {
        this.reportChartInstance.resize()
      }
    },

    destroyChart() {
      window.removeEventListener('resize', this.resizeReportChart)
      if (this.reportChartInstance) {
        this.reportChartInstance.dispose()
        this.reportChartInstance = null
      }
    },

    handleDrawerClose(done) {
      this.destroyChart()
      this.previewVisible = false
      if (done) done()
    },

    // 智能 Markdown 表格美化与状态 Badge 转化
    parseAndFormatTables(text) {
      if (!text) return text

      const lines = text.split('\n')
      const newLines = []
      let i = 0

      while (i < lines.length) {
        let line = lines[i].trim()

        if (line.startsWith('|') && i + 1 < lines.length) {
          let nextLine = lines[i + 1].trim()

          if (nextLine.startsWith('|') && /^[|\s-:]+$/.test(nextLine)) {
            const rawHeaders = line.split('|').slice(1, -1).map(c => c.trim())
            let tableHtml = '<div class="report-table-wrapper"><table class="report-table"><thead><tr>'
            rawHeaders.forEach(th => {
              tableHtml += `<th>${th}</th>`
            })
            tableHtml += '</tr></thead><tbody>'

            i += 2

            while (i < lines.length) {
              let dataLine = lines[i].trim()
              if (dataLine.startsWith('|') && dataLine.endsWith('|')) {
                const cells = dataLine.split('|').slice(1, -1).map(c => {
                  let cellVal = c.trim()
                  if (cellVal === '是' || cellVal === '正常' || cellVal === '通过' || cellVal === '成功' || cellVal === '优' || cellVal === '完成') {
                    return `<span class="report-badge badge-success">✅ ${cellVal}</span>`
                  } else if (cellVal === '否' || cellVal === '异常' || cellVal === '失败' || cellVal === '高危' || cellVal === '危险') {
                    return `<span class="report-badge badge-danger">❌ ${cellVal}</span>`
                  } else if (cellVal === '未知' || cellVal === '挂起' || cellVal === '待定' || cellVal === '警告' || cellVal === '中危') {
                    return `<span class="report-badge badge-warning">⚠️ ${cellVal}</span>`
                  }
                  return cellVal
                })

                tableHtml += '<tr>'
                cells.forEach(td => {
                  tableHtml += `<td>${td}</td>`
                })
                tableHtml += '</tr>'
                i++
              } else {
                break
              }
            }

            tableHtml += '</tbody></table></div>'
            newLines.push(tableHtml)
            continue
          }
        }

        newLines.push(lines[i])
        i++
      }

      return newLines.join('\n')
    },

    // Markdown 自定义轻量渲染器
    renderMarkdown(text) {
      if (!text) return ''

      let html = this.parseAndFormatTables(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')

      // 转义恢复我们的 report-table HTML
      html = html
        .replace(/&lt;div class=&quot;report-table-wrapper&quot;&gt;/g, '<div class="report-table-wrapper">')
        .replace(/&lt;\/div&gt;/g, '</div>')
        .replace(/&lt;table class=&quot;report-table&quot;&gt;/g, '<table class="report-table">')
        .replace(/&lt;\/table&gt;/g, '</table>')
        .replace(/&lt;thead&gt;/g, '<thead>')
        .replace(/&lt;\/thead&gt;/g, '</thead>')
        .replace(/&lt;tbody&gt;/g, '<tbody>')
        .replace(/&lt;\/tbody&gt;/g, '</tbody>')
        .replace(/&lt;tr&gt;/g, '<tr>')
        .replace(/&lt;\/tr&gt;/g, '</tr>')
        .replace(/&lt;th&gt;/g, '<th>')
        .replace(/&lt;\/th&gt;/g, '</th>')
        .replace(/&lt;td&gt;/g, '<td>')
        .replace(/&lt;\/td&gt;/g, '</td>')
        .replace(/&lt;span class=&quot;report-badge badge-([a-z]+)&quot;&gt;/g, '<span class="report-badge badge-$1">')
        .replace(/&lt;\/span&gt;/g, '</span>')

      // 代码块与行内代码
      html = html.replace(/```[\w]*\n?([\s\S]*?)```/g, '<pre class="code-block-box"><code>$1</code></pre>')
      html = html.replace(/`([^`\n]+)`/g, '<code class="inline-code-box">$1</code>')

      // 标题
      html = html.replace(/^#\s+(.+)/gm, '<h1 class="report-h1">$1</h1>')
      html = html.replace(/^##\s+(.+)/gm, '<h2 class="report-h2">$1</h2>')
      html = html.replace(/^###\s+(.+)/gm, '<h3 class="report-h3">$1</h3>')

      // 粗体
      html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')

      // 分割线 (--- / *** / ___) 过滤清除，避免在页面上多余展示 --- 裸文本
      html = html.replace(/^(?:---|[*]{3,}|_{3,})\s*$/gm, '')

      // 列表
      html = html.replace(/^\s*[-*] (.+)$/gm, '<li class="report-li">$1</li>')

      // 换行处理
      html = html.replace(/\n/g, '<br>')
      html = html.replace(/<br>\s*(<\/?(div|table|tr|thead|tbody|th|td|ul|ol|li|h1|h2|h3|pre|blockquote))/gi, '$1')
      html = html.replace(/(<\/(div|table|tr|thead|tbody|th|td|ul|ol|li|h1|h2|h3|pre|blockquote)>)\s*<br>/gi, '$1')

      return html
    },

    // AI 智能重塑报告 (完全对齐 AI 对话的高稳定后端处理)
    async handleAiRefine() {
      if (!this.reportContent) {
        this.$message.warning('报告内容为空，无法进行 AI 重塑')
        return
      }

      this.refining = true
      this.$notify({
        title: 'AI 智能重塑中...',
        message: 'Polaris-AI 大模型正在深度提炼报告内容，生成高管摘要、数据图表与行动计划看板',
        type: 'info',
        duration: 4500
      })

      try {
        const res = await refineReport(this.reportContent)
        if (res.code === 200 && res.data) {
          let schema = null
          if (typeof res.data === 'object') {
            schema = res.data
          } else {
            let cleanText = String(res.data)
            const firstBrace = cleanText.indexOf('{')
            const lastBrace = cleanText.lastIndexOf('}')
            if (firstBrace !== -1 && lastBrace !== -1 && lastBrace > firstBrace) {
              cleanText = cleanText.substring(firstBrace, lastBrace + 1)
            }
            schema = JSON.parse(cleanText)
          }

          this.refinedSchema = schema

          // 1. 更新 KPI 统计卡片
          if (schema.kpiCards && Array.isArray(schema.kpiCards) && schema.kpiCards.length > 0) {
            this.reportStats = schema.kpiCards.map(k => ({
              label: k.label || k.title || '核心指标',
              value: k.value || '0',
              emoji: k.status === 'danger' ? '🚨' : (k.status === 'warning' ? '⚠️' : '📊'),
              color: k.status === 'danger' ? 'rose' : (k.status === 'warning' ? 'amber' : 'indigo')
            }))
          }

          // 2. 更新 ECharts 动态数据图表
          if (schema.visualizations && Array.isArray(schema.visualizations) && schema.visualizations.length > 0) {
            const viz = schema.visualizations[0]
            if (viz.chartData && viz.chartData.categories && viz.chartData.series) {
              this.hasChartData = true
              this.chartConfig = {
                xAxisData: viz.chartData.categories,
                series: viz.chartData.series.map(s => ({
                  name: s.name,
                  type: viz.chartType === 'line' ? 'line' : 'bar',
                  data: s.data,
                  barMaxWidth: 30
                })),
                legendData: viz.chartData.series.map(s => s.name)
              }
              this.$nextTick(() => {
                this.initReportChart()
              })
            }
          }

          this.refining = false
          this.$forceUpdate()

          this.$notify({
            title: '✨ AI 重塑美化完成',
            message: '已成功生成高管决策摘要、核心研判与建议改进行动计划看板！',
            type: 'success',
            duration: 5000
          })

          // 平滑聚焦滚动到新重塑生成的关键区块
          this.$nextTick(() => {
            const target = document.querySelector('.executive-summary-banner') || document.querySelector('.report-action-plan-section')
            if (target) {
              target.scrollIntoView({ behavior: 'smooth', block: 'center' })
            }
          })

        } else {
          this.refining = false
          this.$message.error('AI 重塑异常: ' + (res.msg || '数据处理未达成'))
        }
      } catch (err) {
        this.refining = false
        console.error('AI 重塑失败', err)
        this.$message.error('AI 重塑失败: ' + (err.message || '网络连接超时'))
      }
    },

    // 导出 / 打印 PDF 报告
    handlePrint() {
      window.print()
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
   抽屉与美化数据大屏光暗自适应
   ────────────────────────────────────────── */
.drawer-header-custom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
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
    border-radius: 4px;
  }
}

.drawer-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-right: 20px;

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
  padding: 10px 20px 30px;
}

.paper-preview-box {
  background: #ffffff;
  padding: 36px 44px;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
  border: 1px solid #e2e8f0;
  color: #1e293b;
  transition: all 0.3s;

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
}
</style>

<!-- ──────────────────────────────────────────
   暗黑模式 (Dark Mode) 全局深度穿透兼容防护
   ────────────────────────────────────────── -->
<style lang="scss">
html.dark,
.dark {
  .report-preview-drawer {
    .el-drawer__header {
      background: #0f172a !important;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08) !important;
      margin-bottom: 0 !important;
    }
    .el-drawer__body {
      background: #090d16 !important;
    }
    .el-drawer__close-btn {
      color: #94a3b8 !important;
      margin-left: 16px !important;
      width: 32px;
      height: 32px;
      border-radius: 8px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: all 0.25s ease;

      .el-icon, i, svg {
        color: #94a3b8 !important;
        font-size: 18px;
        transition: color 0.25s ease;
      }

      &:hover {
        background-color: rgba(255, 255, 255, 0.12) !important;

        .el-icon, i, svg {
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
</style>
