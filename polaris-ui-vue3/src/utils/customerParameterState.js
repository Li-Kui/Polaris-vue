export function customerParameterLabel(parameter) {
  return parameter?.label || parameter?.name || '参数'
}

export function customerParameterChoices(parameter) {
  const rules = parameter.constraints || {}
  const hasConst = Object.prototype.hasOwnProperty.call(rules, 'const')
  const values = Array.isArray(rules.enum) ? rules.enum : hasConst ? [rules.const] : []
  return values.filter(value => !hasConst || value === rules.const).map(value => ({ label: String(value), value }))
}

export function customerParameterHints(parameter) {
  const rules = parameter.constraints || {}, hints = []
  if (parameter.description) hints.push(parameter.description)
  if (rules.minLength !== undefined) hints.push(`至少 ${rules.minLength} 个字符`)
  if (rules.maxLength !== undefined) hints.push(`最多 ${rules.maxLength} 个字符`)
  if (rules.minimum !== undefined) hints.push(`不小于 ${rules.minimum}`)
  if (rules.maximum !== undefined) hints.push(`不大于 ${rules.maximum}`)
  if (rules.exclusiveMinimum !== undefined) hints.push(`大于 ${rules.exclusiveMinimum}`)
  if (rules.exclusiveMaximum !== undefined) hints.push(`小于 ${rules.exclusiveMaximum}`)
  const choices = customerParameterChoices(parameter)
  if (choices.length) hints.push(`允许值：${choices.map(item => item.label).join('、')}`)
  if (rules.pattern) hints.push('须符合配置的文本格式')
  if (parameter.example !== undefined && parameter.example !== null) hints.push(`示例（不会自动填写）：${String(parameter.example)}`)
  return hints
}

// 仅作填写提示，格式等完整约束由服务端共用的工作流校验器判定。
export function customerParameterIssue(parameter, value) {
  const label = customerParameterLabel(parameter), rules = parameter.constraints || {}
  if (Array.isArray(rules.enum) && !rules.enum.includes(value)
    || Object.prototype.hasOwnProperty.call(rules, 'const') && rules.const !== value) return `${label} 须选择允许的值`
  if (typeof value === 'string') {
    const length = Array.from(value).length
    if (rules.minLength !== undefined && length < rules.minLength) return `${label} 至少需要 ${rules.minLength} 个字符`
    if (rules.maxLength !== undefined && length > rules.maxLength) return `${label} 最多允许 ${rules.maxLength} 个字符`
  }
  if (typeof value === 'number' && (rules.minimum !== undefined && value < rules.minimum
    || rules.maximum !== undefined && value > rules.maximum || rules.exclusiveMinimum !== undefined && value <= rules.exclusiveMinimum
    || rules.exclusiveMaximum !== undefined && value >= rules.exclusiveMaximum)) return `${label} 超出允许的数值范围`
  return ''
}
