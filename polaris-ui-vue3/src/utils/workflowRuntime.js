import {resolveRuntimeImageInput} from './workflowInputMapping.js'

// 公开模板共用的结果适配；仅解开明确的输出包装，避免把业务对象当作节点元数据。
export function parseRuntimeValue(value) {
  if (typeof value !== 'string') return value
  try { return JSON.parse(value) } catch { return value }
}

export function unwrapRuntimeOutput(value, depth = 0) {
  const output = parseRuntimeValue(value)
  if (depth > 12 || !output || typeof output !== 'object' || Array.isArray(output)) return output
  const keys = Object.keys(output)
  if (keys.length === 1) {
    const child = output[keys[0]]
    if (['output', 'result', 'data'].includes(keys[0])) return unwrapRuntimeOutput(child, depth + 1)
    if (child && typeof child === 'object' && Object.hasOwn(child, 'output')) {
      return unwrapRuntimeOutput(child.output, depth + 1)
    }
  }
  return output
}

export function extractRuntimeText(value, depth = 0) {
  const output = unwrapRuntimeOutput(value)
  if (depth > 12 || output == null) return ''
  if (typeof output === 'string') return output
  if (typeof output !== 'object') return String(output)
  for (const key of ['text', 'content', 'markdown', 'answer', 'message', 'result', 'output']) {
    if (output[key] != null) {
      const text = extractRuntimeText(output[key], depth + 1)
      if (text) return text
    }
  }
  const nodes = Object.values(output).filter(item => item && typeof item === 'object' && Object.hasOwn(item, 'output'))
  return nodes.map(item => extractRuntimeText(item.output, depth + 1)).filter(Boolean).join('\n\n')
}

export function isRuntimeImageUrl(value, explicit = false) {
  if (typeof value !== 'string') return false
  const safe = /^https?:\/\//i.test(value) || value.startsWith('/profile/')
    || /^data:image\/(png|jpeg|gif|webp|bmp);base64,/i.test(value)
  return safe && (explicit || /\.(png|jpe?g|gif|webp|bmp)([?#].*)?$/i.test(value)
    || value.startsWith('data:image/'))
}

export function runtimeImageUrls(value) {
  const urls = []
  const visit = (item, explicit = false, depth = 0) => {
    if (depth > 16) return
    if (isRuntimeImageUrl(item, explicit)) urls.push(item)
    else if (Array.isArray(item)) item.forEach(child => visit(child, explicit, depth + 1))
    else if (item && typeof item === 'object') {
      Object.entries(item).forEach(([key, child]) => visit(child,
        explicit || ['image', 'images', 'imageUrl', 'imageUrls', 'resultImage', 'resultImages', 'sourceImage'].includes(key), depth + 1))
    }
  }
  visit(parseRuntimeValue(value))
  return [...new Set(urls)]
}

// 显式路径从执行结果根对象读取，不自动换路径或回退到其他节点。
export function runtimeMappedValue(value, path) {
  if (typeof path !== 'string' || !path.trim()) return undefined
  const parts = path.startsWith('/') ? path.slice(1).split('/').map(part => part.replace(/~1/g, '/').replace(/~0/g, '~')) : path.split('.')
  if (parts.length > 16 || parts.some(part => !part || ['__proto__', 'constructor', 'prototype'].includes(part))) return undefined
  let current = parseRuntimeValue(value)
  for (const part of parts) {
    current = parseRuntimeValue(current)
    if (!current || typeof current !== 'object' || !Object.hasOwn(current, part)) return undefined
    current = current[part]
  }
  return parseRuntimeValue(current)
}

export function runtimeMappedImageUrls(value, pageConfig, key = 'images') {
  const mapping = pageConfig?.outputMapping
  if (!mapping || !Object.hasOwn(mapping, key)) return runtimeImageUrls(value)
  // 映射字段已明确为图片，支持无扩展名的安全 HTTP(S) 图片地址。
  const output = runtimeMappedValue(value, mapping[key])
  const values = Array.isArray(output) ? output : [output]
  return [...new Set(values.filter(item => isRuntimeImageUrl(item, true)))]
}

export function runtimeImageInputField(schema, pageConfig) {
  return resolveRuntimeImageInput(schema, pageConfig)
}

export function runtimeRows(value) {
  const output = unwrapRuntimeOutput(value)
  if (Array.isArray(output)) return output
  for (const key of ['rows', 'data', 'records', 'items']) {
    if (Array.isArray(output?.[key])) return output[key]
  }
  return null
}

const escapeHtml = value => String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;')
  .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;')

// 不接受原始 HTML 或外部图片，支持公开回复常用的标题、列表、代码与链接。
export function renderRuntimeMarkdown(value) {
  const inline = line => escapeHtml(line)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
  const lines = String(value || '').split('\n')
  const html = []
  let code = null
  let list = false
  const closeList = () => { if (list) { html.push('</ul>'); list = false } }
  for (const line of lines) {
    if (/^\s*```/.test(line)) {
      closeList()
      if (code) { html.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`); code = null }
      else code = []
    } else if (code) code.push(line)
    else if (/^\s*[-*] /.test(line)) {
      if (!list) { html.push('<ul>'); list = true }
      html.push(`<li>${inline(line.replace(/^\s*[-*] /, ''))}</li>`)
    } else {
      closeList()
      const heading = line.match(/^(#{1,6})\s+(.+)$/)
      if (heading || line.trim()) html.push(heading ? `<h${heading[1].length}>${inline(heading[2])}</h${heading[1].length}>`
        : `<p>${inline(line)}</p>`)
    }
  }
  closeList()
  if (code) html.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`)
  return html.join('')
}

export async function copyRuntimeText(value) {
  if (!navigator.clipboard) throw new Error('复制不可用，请使用 HTTPS 或 localhost 访问')
  await navigator.clipboard.writeText(String(value))
}

export function downloadRuntimeFile(value, filename, type = 'text/plain;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([value], { type }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function runtimeCsv(rows) {
  const columns = [...new Set(rows.flatMap(row => Object.keys(row || {})))]
  const cell = value => {
    let text = value == null ? '' : typeof value === 'object' ? JSON.stringify(value) : String(value)
    if (typeof value === 'string' && /^[=+\-@\t\r]/.test(text)) text = `'${text}`
    return `"${text.replace(/"/g, '""')}"`
  }
  return '\ufeff' + [columns.map(cell).join(','), ...rows.map(row => columns.map(key => cell(row[key])).join(','))].join('\r\n')
}

// 任务页展示业务结果，不将等待节点的调度信息作为结果内容。
export function runtimeTaskResult(value) {
  const output = parseRuntimeValue(value)
  const isWaitOutput = item => item && typeof item === 'object' && Object.hasOwn(item, 'waitedMs')
    && Object.hasOwn(item, 'targetAt') && ['RESUMED', 'SKIPPED', 'WAITING'].includes(item.status)
  if (!output || typeof output !== 'object' || Array.isArray(output)) return output
  const nodes = Object.values(output)
  if (!nodes.length || !nodes.every(item => item && typeof item === 'object' && Object.hasOwn(item, 'output'))) {
    const result = unwrapRuntimeOutput(output)
    return isWaitOutput(result) ? null : result
  }
  const business = Object.entries(output).filter(([, item]) => !isWaitOutput(item.output))
  if (!business.length) return null
  if (business.length === 1) return unwrapRuntimeOutput(business[0][1].output)
  return Object.fromEntries(business.map(([key, item]) => [key, unwrapRuntimeOutput(item.output)]))
}
