// 只导出报告内容。使用本地依赖和独立亮色副本，不改动正在查看的报告。
export function runtimeReportPdfFilename(title) {
  const name = String(title || '报告').replace(/[\\/:*?"<>|\x00-\x1f]/g, '_').trim().replace(/[. ]+$/, '').slice(0, 80)
  return `${name || '报告'}.pdf`
}

function reportTable(headers, rows, widths = []) {
  const table = document.createElement('table')
  const totalWidth = widths.reduce((total, width) => total + width, 0)
  if (totalWidth) {
    const columns = document.createElement('colgroup')
    widths.forEach(width => { const column = document.createElement('col'); column.style.width = `${width / totalWidth * 100}%`; columns.appendChild(column) })
    table.appendChild(columns)
  }
  const head = document.createElement('thead')
  const header = document.createElement('tr')
  headers.forEach(label => { const cell = document.createElement('th'); cell.textContent = label; header.appendChild(cell) })
  head.appendChild(header)
  table.appendChild(head)
  const body = document.createElement('tbody')
  rows.forEach(values => {
    const row = document.createElement('tr')
    values.forEach(value => { const cell = document.createElement('td'); cell.textContent = value; row.appendChild(cell) })
    body.appendChild(row)
  })
  table.appendChild(body)
  return table
}

// 按 A4 可用高度分表，重复表头，并把首行与表头保留在同一页。
function paginateReportTables(report) {
  const pageHeight = report.getBoundingClientRect().width * 269 / 186
  report.querySelectorAll('table').forEach(table => {
    const rows = [...table.querySelectorAll('tbody tr')]
    if (!rows.length) return
    const headerHeight = table.querySelector('thead').getBoundingClientRect().height
    const sectionHeading = table.previousElementSibling?.classList.contains('card-header') ? table.previousElementSibling : null
    const sectionHeight = sectionHeading ? table.getBoundingClientRect().top - sectionHeading.getBoundingClientRect().top : 0
    const top = table.getBoundingClientRect().top - report.getBoundingClientRect().top
    const heights = rows.map(row => row.getBoundingClientRect().height)
    if (heights.some(height => height + headerHeight > pageHeight - 24)) throw new Error('报告表格单行过长，请导出原始数据或缩短内容后重试')
    let remaining = pageHeight - top % pageHeight - headerHeight - 24
    let pageBreak = heights[0] > remaining
    if (pageBreak) remaining = pageHeight - headerHeight - sectionHeight - 24
    const fragment = document.createDocumentFragment()
    let current, body
    rows.forEach((row, index) => {
      if (!current || heights[index] > remaining) {
        if (current) { pageBreak = true; remaining = pageHeight - headerHeight - 24 }
        current = table.cloneNode(false)
        current.style.breakInside = 'avoid'
        if (pageBreak && (index || !sectionHeading)) current.style.breakBefore = 'always'
        table.querySelector('colgroup') && current.appendChild(table.querySelector('colgroup').cloneNode(true))
        current.appendChild(table.querySelector('thead').cloneNode(true))
        body = document.createElement('tbody')
        current.appendChild(body)
        if (!index && sectionHeading) {
          const section = document.createElement('div')
          section.style.breakInside = 'avoid'
          if (pageBreak) section.style.breakBefore = 'always'
          section.appendChild(sectionHeading)
          section.appendChild(current)
          fragment.appendChild(section)
        } else fragment.appendChild(current)
      }
      body.appendChild(row.cloneNode(true))
      remaining -= heights[index]
    })
    table.replaceWith(fragment)
  })
}

export async function exportRuntimeReportPdf(element, { title, rows } = {}) {
  if (!element) throw new Error('报告尚未就绪，请稍后重试')
  const { default: html2pdf } = await import('html2pdf.js')
  await document.fonts?.ready
  const holder = document.createElement('div')
  holder.setAttribute('aria-hidden', 'true')
  holder.style.cssText = 'position:fixed;left:-10000px;top:0;width:186mm;pointer-events:none;'
  const report = element.cloneNode(true)
  report.classList.add('runtime-report-pdf')
  report.querySelector('.report-toolbar')?.remove()
  report.querySelectorAll('.dark').forEach(node => node.classList.remove('dark'))
  report.querySelectorAll('[id]').forEach(node => node.removeAttribute('id'))
  const heading = document.createElement('h1')
  heading.className = 'pdf-report-title'
  heading.textContent = title || '报告'
  if (!report.querySelector('.banner-title, h1')) report.prepend(heading)

  // Canvas 本身不会随 cloneNode 复制像素；转换图表快照，失败时明确中止。
  const canvases = element.querySelectorAll('canvas')
  report.querySelectorAll('canvas').forEach((canvas, index) => {
    const image = document.createElement('img')
    image.src = canvases[index].toDataURL('image/png')
    image.style.cssText = 'display:block;width:100%;height:auto;'
    canvas.replaceWith(image)
  })
  // Element 表格有分离表头和固定宽度，转换为完整可换行的原生表格。
  report.querySelectorAll('.el-table').forEach(table => {
    const headers = [...table.querySelectorAll('.el-table__header-wrapper th .cell')].map(cell => cell.textContent.trim())
    const values = [...table.querySelectorAll('.el-table__body-wrapper tbody tr')].map(row => [...row.querySelectorAll('td .cell')].map(cell => cell.textContent.trim()))
    const widths = [...table.querySelectorAll('.el-table__header-wrapper col')].map(column => Number(column.getAttribute('width')) || 0)
    if (headers.length) table.replaceWith(reportTable(headers, values, widths))
  })
  if (Array.isArray(rows) && rows.length) {
    const headers = [...new Set(rows.flatMap(row => Object.keys(row || {})))]
    report.querySelector('.result-table')?.replaceWith(reportTable(headers, rows.map(row => headers.map(key => {
      const value = row?.[key]
      return value == null ? '' : typeof value === 'object' ? JSON.stringify(value) : String(value)
    }))))
  }
  report.querySelectorAll('.el-pagination').forEach(node => node.remove())
  const style = document.createElement('style')
  style.textContent = `
    .runtime-report-pdf { width:100%; background:#fff !important; color:#1e293b !important; padding:0; margin:0; font-size:14px; line-height:1.7;
      --runtime-text:#1e293b; --runtime-secondary:#64748b; --runtime-surface:#fff; --runtime-muted:#f1f5f9; --runtime-border:#dbe2ea; --workflow-primary:#4f46e5; color-scheme:light; }
    .runtime-report-pdf *, .runtime-report-pdf *::before, .runtime-report-pdf *::after { animation:none !important; transition:none !important; box-shadow:none !important; }
    .runtime-report-pdf .pdf-report-title { font-size:24px; line-height:1.4; margin:0 0 20px; }
    .runtime-report-pdf .polaris-report-engine.theme-glass-light { background:#fff !important; padding:0 !important; }
    .runtime-report-pdf .engine-report-banner { background:#f1f5f9 !important; color:#1e293b !important; }
    .runtime-report-pdf .banner-title, .runtime-report-pdf .banner-meta { color:#1e293b !important; }
    .runtime-report-pdf .banner-badge { background:#fff !important; color:#4f46e5 !important; }
    .runtime-report-pdf .action-plan-card { background:transparent !important; border:0 !important; padding:0 !important; }
    .runtime-report-pdf .kpi-grid-container, .runtime-report-pdf .compare-columns-grid, .runtime-report-pdf .swot-grid { grid-template-columns:repeat(2,minmax(0,1fr)) !important; }
    .runtime-report-pdf .chart-mount-container { height:auto !important; min-height:0 !important; }
    .runtime-report-pdf .chart-block-card, .runtime-report-pdf .kpi-stat-card { break-inside:avoid; }
    .runtime-report-pdf h1, .runtime-report-pdf h2, .runtime-report-pdf h3, .runtime-report-pdf .card-header { break-after:avoid; }
    .runtime-report-pdf p, .runtime-report-pdf li, .runtime-report-pdf tr { break-inside:avoid; }
    .runtime-report-pdf pre { white-space:pre-wrap !important; overflow:visible !important; overflow-wrap:anywhere; }
    .runtime-report-pdf table { width:100%; border-collapse:collapse; table-layout:fixed; margin:12px 0; font-size:12px; }
    .runtime-report-pdf th, .runtime-report-pdf td { border:1px solid #dbe2ea; padding:8px; text-align:left; overflow-wrap:anywhere; }
    .runtime-report-pdf th { background:#f1f5f9; }
    .runtime-report-pdf img { max-width:100%; }
  `
  report.prepend(style)
  holder.appendChild(report)
  document.body.appendChild(holder)
  try {
    await Promise.all([...report.querySelectorAll('img')].map(image => image.decode()))
    if (report.scrollHeight > 16000) throw new Error('报告过长，无法安全生成 PDF；请导出原始数据或缩短报告后重试')
    paginateReportTables(report)
    const worker = html2pdf().set({
      margin: [12, 12, 16, 12], filename: runtimeReportPdfFilename(title),
      image: { type: 'jpeg', quality: 0.98 }, enableLinks: false,
      html2canvas: { scale: 1.5, backgroundColor: '#ffffff', useCORS: false, scrollX: 0, scrollY: 0, windowWidth: 1024 },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }, pagebreak: { mode: ['css', 'legacy'] }
    }).from(report).toPdf()
    const pdf = await worker.get('pdf')
    pdf.setProperties({ title: title || '报告', subject: '工作流分享报告' })
    const pages = pdf.internal.getNumberOfPages()
    for (let page = 1; page <= pages; page++) {
      pdf.setPage(page)
      pdf.setFontSize(9)
      pdf.setTextColor(100, 116, 139)
      pdf.text(`${page} / ${pages}`, 198, 289, { align: 'right' })
    }
    await worker.save()
  } finally {
    holder.remove()
  }
}
