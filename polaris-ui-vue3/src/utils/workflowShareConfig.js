// 分享配置只保存主动覆盖的值；编辑时保留未在表单中展示的模板配置。
const formConfigKeys = ['title', 'description', 'theme', 'welcomeMessage', 'suggestedQuestions']

export function parseSharePageConfig(value) {
  if (value == null || value === '') return {}
  let config
  try { config = typeof value === 'string' ? JSON.parse(value) : value }
  catch { throw new Error('页面配置必须是有效的 JSON 对象') }
  if (!config || typeof config !== 'object' || Array.isArray(config)) throw new Error('页面配置必须是 JSON 对象')
  return config
}

export function createShareForm(workflowName, row) {
  const config = parseSharePageConfig(row?.pageConfigJson)
  const extra = Object.fromEntries(Object.entries(config).filter(([key]) => !formConfigKeys.includes(key)))
  let origins = []
  if (row?.allowedOrigins) {
    try { origins = JSON.parse(row.allowedOrigins) }
    catch { throw new Error('已有 iframe 来源配置无效，请先检查配置') }
    if (!Array.isArray(origins) || origins.some(value => typeof value !== 'string')) throw new Error('已有 iframe 来源配置无效')
  }
  return {
    id: row?.id,
    shareName: row?.shareName || `${workflowName} 的分享`,
    pageType: row?.pageType || '',
    rateLimit: row?.rateLimit ?? 60,
    allowedOriginsText: origins.join('\n'),
    title: config.title ?? '',
    description: config.description ?? '',
    theme: config.theme || '',
    welcomeMessage: config.welcomeMessage ?? '',
    suggestedQuestionsText: Array.isArray(config.suggestedQuestions) ? config.suggestedQuestions.join('\n') : '',
    extraConfigJson: JSON.stringify(extra, null, 2),
    expireTime: row?.expireTime || null
  }
}

export function buildShareRequest(form, workflowDefinitionId) {
  const config = { ...parseSharePageConfig(form.extraConfigJson) }
  if (formConfigKeys.some(key => Object.hasOwn(config, key))) throw new Error('标题、说明、主题和对话引导请使用上方表单设置')
  for (const key of ['title', 'description', 'welcomeMessage']) {
    if (form[key]?.trim()) config[key] = form[key].trim()
  }
  if (form.theme) config.theme = form.theme
  const questions = form.suggestedQuestionsText?.split('\n').map(value => value.trim()).filter(Boolean) || []
  if (questions.length) config.suggestedQuestions = questions
  for (const key of ['enabledModes', 'sizeOptions']) {
    if (config[key] != null && (!Array.isArray(config[key]) || config[key].some(value => typeof value !== 'string' || !value.trim()))) {
      throw new Error(`${key} 必须是非空字符串组成的数组`)
    }
  }
  for (const key of ['inputMapping', 'outputMapping']) {
    if (config[key] != null) {
      if (typeof config[key] !== 'object' || Array.isArray(config[key])) throw new Error(`${key} 必须是 JSON 对象`)
      const mapping = parseSharePageConfig(config[key])
      if (Object.values(mapping).some(value => typeof value !== 'string' || !value.trim())) throw new Error(`${key} 的字段映射必须是非空字符串`)
    }
  }
  if (config.maxGenerateCount != null && (!Number.isInteger(config.maxGenerateCount) || config.maxGenerateCount < 1 || config.maxGenerateCount > 100)) {
    throw new Error('maxGenerateCount 必须是 1 到 100 之间的整数')
  }
  if (config.showDownload != null && typeof config.showDownload !== 'boolean') throw new Error('showDownload 必须是布尔值')
  return {
    ...(form.id != null ? { id: form.id } : { workflowDefinitionId }),
    shareName: form.shareName.trim(),
    pageType: form.pageType || null,
    rateLimit: form.rateLimit,
    pageConfigJson: JSON.stringify(config),
    allowedOrigins: JSON.stringify(form.allowedOriginsText.split('\n').map(value => value.trim()).filter(Boolean)),
    expireTime: form.expireTime || null
  }
}

export function createShareDefaultsForm(workflowName, definition) {
  return createShareForm(workflowName, {
    pageType: definition?.defaultPageType,
    pageConfigJson: definition?.sharePageConfigJson
  })
}

export function buildShareDefaultsRequest(form, definition) {
  if (!Number.isInteger(definition?.lockVersion)) throw new Error('默认配置版本号无效，请重新加载')
  const request = buildShareRequest(form, definition.definitionId)
  return {
    defaultPageType: request.pageType,
    sharePageConfigJson: request.pageConfigJson,
    expectedLockVersion: definition.lockVersion
  }
}
