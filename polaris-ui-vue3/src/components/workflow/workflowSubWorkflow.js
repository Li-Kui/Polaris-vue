export function subWorkflowPorts(mode = 'STOP') {
  return [{port: 'completed', label: '完成'}, ...(mode === 'DETAILED'
    ? [{port: 'rejected', label: '未通过'}, {port: 'failed', label: '执行失败'}]
    : mode === 'BRANCH' ? [{port: 'incomplete', label: '未完成'}] : [])]
}

export function isInsideSubWorkflowLoop(definition, nodeId) {
  return (definition.nodes || []).filter(n => n.type === 'loop').some(loop => {
    const edges=definition.edges || []
    const start=edges.find(e=>e.source===loop.id && e.sourcePort==='body')?.target
    const done=edges.find(e=>e.source===loop.id && e.sourcePort==='done')?.target
    const pending=[start], seen=new Set()
    while(pending.length){
      const current=pending.shift()
      if(!current || current===loop.id || current===done || seen.has(current))continue
      if(current===nodeId)return true
      seen.add(current)
      edges.filter(e=>e.source===current && e.targetPort!=='loop-return').forEach(e=>pending.push(e.target))
    }
    return false
  })
}

export function schemaType(schema = {}) {
  return (Array.isArray(schema.type) ? schema.type.find(t => t !== 'null') : schema.type)
    || (schema.properties ? 'object' : schema.items ? 'array' : '')
}

export function initialInput(schema = {}, depth = 0) {
  if (depth > 20) return {mode: 'DEFAULT'}
  if (schemaType(schema) === 'object' || (depth === 0 && !schemaType(schema))) return {mode: 'OBJECT', fields: Object.fromEntries(
    Object.entries(schema.properties || {}).map(([key, value]) => [key,
      (schema.required || []).includes(key) && !Object.hasOwn(value, 'default') ? initialInput(value, depth + 1) : {mode: 'DEFAULT'}]))}
  return {mode: 'DEFAULT'}
}

export function compatibleType(target, source) {
  const type = schemaType(target)
  return !type || source === 'any' || !source || type === source || (type === 'number' && source === 'integer')
}

export function inputIssues(tree, schema = {}, path = '输入', required = true) {
  if (!tree || tree.mode === 'DEFAULT') return required && !Object.hasOwn(schema, 'default') ? [`${path}：请填写或选择来源`] : []
  if (tree.mode === 'SOURCE') return tree.expression ? [] : [`${path}：请选择来源`]
  if (tree.mode === 'JSON') { try { JSON.parse(tree.json); return [] } catch { return [`${path}：JSON 格式不正确`] } }
  if (tree.mode === 'VALUE') return Object.hasOwn(tree, 'value') ? [] : [`${path}：请填写固定值`]
  if (tree.mode === 'OBJECT') return Object.entries(schema.properties || {}).flatMap(([key, child]) =>
    inputIssues(tree.fields?.[key], child, `${path}.${key}`, (schema.required || []).includes(key)))
  if (tree.mode === 'ARRAY') return (tree.items || []).flatMap((item, i) => inputIssues(item, schema.items || {}, `${path}[${i}]`))
  return []
}

export function autoMatchInputs(schema, fields) {
  const tree = initialInput(schema)
  let matched = 0
  Object.entries(schema.properties || {}).forEach(([key, target]) => {
    // 对象和数组的同名不代表结构兼容，保留用户明确选择。
    if (['object', 'array'].includes(schemaType(target))) return
    const candidates = fields.filter(f => !f.expression.includes('[]') && f.expression.split('.').at(-1) === key && compatibleType(target, f.type))
    if (candidates.length === 1) { tree.fields[key] = {mode: 'SOURCE', expression: candidates[0].expression}; matched++ }
  })
  return {tree, matched}
}
