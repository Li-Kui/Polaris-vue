const schemaType = schema => {
  const declared = Array.isArray(schema?.type)
    ? schema.type.find(type => type !== 'null')
    : schema?.type
  return declared || (schema?.properties ? 'object' : schema?.items ? 'array' : 'object')
}

/** 返回当前节点的全部上游节点，供下游选择和连线防环复用。 */
export function workflowUpstreamNodeIds(definition, nodeId) {
  const result = new Set()
  const pending = [nodeId]
  while (pending.length) {
    const current = pending.shift()
    ;(definition?.edges || []).filter(edge => edge.target === current
      && edge.targetPort !== 'loop-return').forEach(edge => {
      if (edge.source === '__start__' || edge.source === nodeId || result.has(edge.source)) return
      result.add(edge.source)
      pending.push(edge.source)
    })
  }
  return result
}

/** 语义分类分支只能指向真正的下游节点或流程结束，避免直接拖线制造环。 */
export function isClassifierTargetAllowed(definition, nodeId, targetId) {
  if (!nodeId || !targetId || targetId === '__start__' || targetId === nodeId) return false
  if (targetId === '__end__') return true
  const nodeExists = (definition?.nodes || []).some(node => node.id === targetId)
  return nodeExists && !workflowUpstreamNodeIds(definition, nodeId).has(targetId)
}

export function classifierTargetOptions(definition, nodeId) {
  return [
    ...(definition?.nodes || [])
      .filter(node => isClassifierTargetAllowed(definition, nodeId, node.id))
      .map(node => ({id: node.id, name: node.name})),
    {id: '__end__', name: '结束流程'}
  ]
}

/**
 * 将 JSON Schema 展开成分类内容可选项。
 * 数组元素使用 [] 路径语义，例如 orders[].items[].name；运行时会逐层投影。
 */
export function classifierSchemaOptions(schema, baseExpression, rootLabel, limits = {}) {
  const maxDepth = limits.maxDepth ?? 24
  const maxOptions = limits.maxOptions ?? 1000
  const result = []
  const expressions = new Set()
  const labels = {
    object: '对象', array: '数组', string: '文本', number: '数字', integer: '整数',
    boolean: '是/否', null: '空值'
  }

  const resolveReference = current => {
    const reference = current?.$ref
    if (typeof reference !== 'string' || !reference.startsWith('#/')) return current
    return reference.slice(2).split('/').reduce((value, segment) => value?.[
      segment.replaceAll('~1', '/').replaceAll('~0', '~')
    ], schema) || current
  }

  const visit = (candidate, expression, path, depth, addCurrent = true) => {
    const current = resolveReference(candidate)
    if (!current || depth > maxDepth || result.length >= maxOptions) return
    const type = schemaType(current)
    if (addCurrent && !expressions.has(expression)) {
      expressions.add(expression)
      result.push({
        expression,
        label: path || rootLabel,
        type,
        typeLabel: labels[type] || '数据'
      })
    }
    const itemSchemas = [
      ...(Array.isArray(current.prefixItems) ? current.prefixItems : []),
      ...(Array.isArray(current.items) ? current.items : current.items ? [current.items] : [])
    ]
    if ((type === 'array' || itemSchemas.length) && itemSchemas.length) {
      const itemPath = `${path || rootLabel}[]`
      itemSchemas.forEach(item => visit(item, `${expression}[]`, itemPath, depth + 1, true))
    }
    Object.entries(current.properties || {}).forEach(([key, property]) => {
      visit(property, `${expression}.${key}`, path ? `${path}.${key}` : key, depth + 1, true)
    })
    ;['allOf', 'oneOf', 'anyOf'].forEach(keyword => {
      ;(current[keyword] || []).forEach(option => {
        visit(option, expression, path, depth + 1, false)
      })
    })
  }

  visit(schema || {type: 'object'}, baseExpression, '', 0)
  return result
}
