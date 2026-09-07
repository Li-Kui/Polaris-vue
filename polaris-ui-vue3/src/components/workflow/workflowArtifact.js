const text = value => String(value ?? '').trim()

export const ARTIFACT_DEFAULT_CONFIG = Object.freeze({
  configVersion: '2.0',
  format: 'JSON',
  fileNameTemplate: '工作流产物-{{date}}',
  retentionMode: 'EXECUTION',
  retentionDays: 30
})

export function normalizeArtifactConfig(config = {}) {
  const format = ['JSON', 'TEXT'].includes(config.format) ? config.format : 'JSON'
  const retentionMode = ['EXECUTION', 'DAYS'].includes(config.retentionMode)
    ? config.retentionMode : 'EXECUTION'
  const retentionDays = [7, 30, 90].includes(Number(config.retentionDays))
    ? Number(config.retentionDays) : 30
  return {
    configVersion: '2.0',
    format,
    fileNameTemplate: text(config.fileNameTemplate)
      || ARTIFACT_DEFAULT_CONFIG.fileNameTemplate,
    retentionMode,
    retentionDays
  }
}

export function artifactExtension(format) {
  return format === 'TEXT' ? 'txt' : 'json'
}

export function artifactFileNamePreview(config = {}, date = new Date()) {
  const normalized = normalizeArtifactConfig(config)
  const day = [date.getFullYear(), date.getMonth() + 1, date.getDate()]
    .map((value, index) => index ? String(value).padStart(2, '0') : String(value))
    .join('')
  const base = normalized.fileNameTemplate
    .replaceAll('{{date}}', day)
    .replaceAll('{{executionId}}', 'execution-id')
    .replaceAll('{{nodeRunId}}', 'node-run-id')
    .replace(/[\\/:*?"<>|\u0000-\u001F]/g, '_')
  const suffix = `.${artifactExtension(normalized.format)}`
  return base.toLowerCase().endsWith(suffix) ? base : `${base}${suffix}`
}

export function artifactConfigurationState({config = {}, inputMapping = {}} = {}) {
  const normalized = normalizeArtifactConfig(config)
  const issues = []
  if (!inputMapping?.content?.expression && !Object.hasOwn(inputMapping?.content || {}, 'value')) {
    issues.push('请选择要保存的内容')
  }
  if (!text(normalized.fileNameTemplate)) issues.push('请填写文件名')
  return {
    code: issues.length ? 'CONFIGURING' : 'READY',
    label: issues.length ? '待配置' : '可预览',
    tone: issues.length ? 'warning' : 'success',
    issues,
    fileName: artifactFileNamePreview(normalized),
    formatLabel: normalized.format === 'TEXT' ? '纯文本' : 'JSON'
  }
}

export function artifactSummary(config = {}) {
  const normalized = normalizeArtifactConfig(config)
  return `${normalized.format === 'TEXT' ? '纯文本' : 'JSON'} · ${artifactFileNamePreview(normalized)}`
}
