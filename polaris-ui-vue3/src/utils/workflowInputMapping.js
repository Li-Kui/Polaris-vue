const unsafeNames = ['__proto__', 'constructor', 'prototype']
const isObjectSchema = schema => schema && (!schema.type || schema.type === 'object')
  && schema.properties && typeof schema.properties === 'object' && !Array.isArray(schema.properties)
const isImageSchema = schema => schema && (!schema.type || schema.type === 'string')
  && (schema.format === 'uri' || schema.format === 'binary' || String(schema.contentMediaType || '').startsWith('image/'))
const pointer = parts => '/' + parts.map(part => part.replace(/~/g, '~0').replace(/\//g, '~1')).join('/')

// 顶层名称保持兼容；嵌套对象使用 JSON Pointer，不猜测数组下标或创建数组元素。
export function runtimeInputPath(schema, path) {
  if (typeof path !== 'string' || !path) return undefined
  const parts = Object.hasOwn(schema?.properties || {}, path) ? [path] : path.startsWith('/')
    ? path.slice(1).split('/').map(part => /~(?![01])/u.test(part) ? '' : part.replace(/~1/g, '/').replace(/~0/g, '~'))
    : path.split('.')
  if (parts.length > 16 || parts.some(part => !part || unsafeNames.includes(part))) return undefined
  let current = schema
  for (const part of parts) {
    if (!isObjectSchema(current) || !Object.hasOwn(current.properties || {}, part)) return undefined
    current = current.properties[part]
  }
  return { parts, schema: current }
}

export function runtimeImageInputOptions(schema) {
  const fields = []
  const visit = (current, parents = []) => {
    if (parents.length >= 16 || !isObjectSchema(current)) return
    for (const [name, field] of Object.entries(current.properties || {})) {
      if (!name || unsafeNames.includes(name)) continue
      const parts = [...parents, name]
      if (isImageSchema(field)) fields.push({ name: parents.length ? pointer(parts) : name, label: `${field.title || name}（${parents.length ? pointer(parts) : name}）`, schema: field })
      else visit(field, parts)
    }
  }
  visit(schema)
  return fields
}

export function resolveRuntimeImageInput(schema, pageConfig) {
  const mapping = pageConfig?.inputMapping
  if (mapping && Object.hasOwn(mapping, 'image')) {
    const field = runtimeInputPath(schema, mapping.image)
    if (!field || !isImageSchema(field.schema)) throw new Error('输入图片映射无效，请管理员选择已发布输入定义中的图片字段（不支持数组路径）')
    return mapping.image
  }
  const candidates = runtimeImageInputOptions(schema)
  const images = candidates.filter(field => String(field.schema.contentMediaType || '').startsWith('image/'))
  const choices = images.length ? images : candidates
  if (choices.length > 1) throw new Error('有多个图片或网址字段，请管理员配置输入图片映射')
  return choices[0]?.name
}

export function runtimeInputValue(schema, input, path) {
  const field = runtimeInputPath(schema, path)
  if (!field) return undefined
  let current = input
  for (const part of field.parts) {
    if (!current || typeof current !== 'object' || Array.isArray(current) || !Object.hasOwn(current, part)) return undefined
    current = current[part]
  }
  return current
}

export function setRuntimeInputValue(schema, input, path, value) {
  const field = runtimeInputPath(schema, path)
  if (!field) throw new Error('输入字段映射无效，请重新加载页面并检查配置')
  const write = (current, index) => {
    if (current != null && (typeof current !== 'object' || Array.isArray(current))) throw new Error('图片所属参数应为对象，请先修正输入结构')
    const result = { ...(current || {}) }
    const name = field.parts[index]
    result[name] = index === field.parts.length - 1 ? value : write(Object.hasOwn(result, name) ? result[name] : undefined, index + 1)
    return result
  }
  return write(input, 0)
}

// 仅隐藏上传控件已负责的叶子字段，保留同级参数；校验仍使用完整的原始契约。
export function runtimeInputFormSchema(schema, hiddenFields) {
  const paths = hiddenFields.map(path => runtimeInputPath(schema, path)?.parts).filter(Boolean)
  const visit = (current, parents = []) => {
    if (!isObjectSchema(current)) return current
    const properties = Object.fromEntries(Object.entries(current.properties || {}).flatMap(([name, field]) => {
      const parts = [...parents, name]
      if (paths.some(path => path.length === parts.length && path.every((part, index) => part === parts[index]))) return []
      if (!paths.some(path => path.length > parts.length && parts.every((part, index) => part === path[index]))) return [[name, field]]
      const child = visit(field, parts)
      return Object.keys(child.properties || {}).length ? [[name, child]] : []
    }))
    return { ...current, properties, required: (Array.isArray(current.required) ? current.required : []).filter(name => Object.hasOwn(properties, name)) }
  }
  return visit(schema)
}
