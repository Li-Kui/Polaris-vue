// 仅展示服务端为本轮实际片段分配的编号，不从模型正文推导或伪造来源。
export function formatKnowledgeCitations(doc) {
  return knowledgeCitationIds(doc).map(value => `[${value}]`).join(' ')
}

export function knowledgeCitationIds(doc) {
  if (!Array.isArray(doc?.citationIds)) return []
  return [...new Set(doc.citationIds.slice(0, 50).filter(value =>
    typeof value === 'string' && /^S[1-9][0-9]?$/.test(value) && Number(value.slice(1)) <= 50
  ))]
}

// 同一编号若映射到不同文档，不在前端任选一份；来源仍由消息绑定决定。
function citationOwners(docs) {
  const owners = new Map(), ambiguous = new Set()
  for (const doc of Array.isArray(docs) ? docs : []) {
    if (!doc || typeof doc !== 'object' || Array.isArray(doc)) continue
    const key = doc.docId != null ? `id:${doc.docId}` : `name:${doc.docName || ''}`
    for (const id of knowledgeCitationIds(doc)) {
      if (owners.has(id) && owners.get(id) !== key) ambiguous.add(id)
      owners.set(id, key)
    }
  }
  for (const id of ambiguous) owners.delete(id)
  return owners
}

export function referencedKnowledgeCitationIds(content, docs) {
  const owners = citationOwners(docs)
  // 代码和 Markdown 链接中的示例编号不作为回答引用。
  const text = String(content || '').replace(/```[\s\S]*?(?:```|$)|`[^`\n]*(?:`|$)|\[[^\]\n]+\]\([^\n)]*\)/g, '')
  return [...new Set([...text.matchAll(/\[(S[1-9][0-9]?)\]/g)].map(m => m[1]).filter(id => owners.has(id)))]
}

export function knowledgeSourceGroups(docs, content) {
  const owners = citationOwners(docs), referenced = new Set(referencedKnowledgeCitationIds(content, docs)), groups = new Map()
  for (const doc of Array.isArray(docs) ? docs : []) {
    if (!doc || typeof doc !== 'object' || Array.isArray(doc)) continue
    const key = doc.docId != null ? `id:${doc.docId}` : `name:${doc.docName || ''}`
    if (!groups.has(key)) groups.set(key, {key, docId: doc.docId, docName: doc.docName || '未命名文档', citationIds: [], confidence: doc.confidence, matchType: doc.matchType})
    const group = groups.get(key)
    group.citationIds = [...new Set([...group.citationIds, ...knowledgeCitationIds(doc).filter(id => owners.get(id) === key)])]
  }
  return [...groups.values()].map(group => ({...group, referencedIds: group.citationIds.filter(id => referenced.has(id))}))
    .sort((a, b) => Number(Boolean(b.referencedIds.length)) - Number(Boolean(a.referencedIds.length)))
}

// 只装饰已转义／渲染 HTML 的文本节点，不替换属性、链接或代码中的内容。
// 插入的 HTML 只含固定模板和白名单 S1～S50，不拼接模型文本或文档名称。
export function decorateKnowledgeCitations(html, message) {
  if (!message?.id || message.streaming || message.moderationStatus === 'INTERRUPTED_BLOCKED') return html
  const allowed = new Set(referencedKnowledgeCitationIds(message.content, message.attachedDocs))
  if (!allowed.size) return html
  const protectedTags = new Set(['pre', 'code', 'a', 'button', 'script', 'style']), stack = []
  return String(html).split(/(<[^>]+>)/g).map(part => {
    if (part.startsWith('<')) {
      const tag = part.match(/^<\s*(\/?)\s*([a-z][\w-]*)/i)
      if (tag && protectedTags.has(tag[2].toLowerCase())) {
        const name = tag[2].toLowerCase()
        if (!tag[1] && !/\/\s*>$/.test(part)) stack.push(name)
        else if (tag[1] && stack.at(-1) === name) stack.pop()
      }
      return part
    }
    if (stack.length) return part
    return part.replace(/\[(S[1-9][0-9]?)\]/g, (value, id) => allowed.has(id)
      ? `<button type="button" class="knowledge-inline-citation" data-knowledge-citation="${id}" aria-label="查看参考片段 ${id}">[${id}]</button>` : value)
  }).join('')
}

// 状态只能来自服务端最终核对；不根据模型正文或已有来源卡片自行判定通过。
export function knowledgeCitationNotice(validation, streaming, hasSources) {
  if (streaming && hasSources) return {kind: 'pending', text: '回答生成中，引用编号尚未完成核对。'}
  if (!validation && !hasSources) return null
  const unavailable = {kind: 'warning', text: '引用编号尚未核对，不能据此确认回答有依据。'}
  if (!validation || validation.version !== 1 || !Number.isInteger(validation.referencedCount) ||
      validation.referencedCount < 0 || validation.referencedCount > 50 || !Number.isInteger(validation.invalidCount) ||
      validation.invalidCount < 0 || validation.invalidCount > 100000) return unavailable
  if (validation.state === 'IDS_VALID' && validation.referencedCount > 0 && validation.invalidCount === 0) {
    return {kind: 'checked', text: '已识别的引用编号已核对：属于原参考来源，但未核验证据支持度或当前访问状态。'}
  }
  if (validation.state === 'INVALID_IDS' && validation.invalidCount > 0) {
    return {kind: 'warning', text: '回答含有无效或不属于本轮来源的引用编号；请核对原片段，不将这些编号视为可信依据。'}
  }
  if (validation.state === 'MISSING_IDS' && validation.referencedCount === 0 && validation.invalidCount === 0) {
    return {kind: 'warning', text: '回答未标注可核对的来源编号，不能据此确认结论有依据；你仍可查看参考片段。'}
  }
  return unavailable
}
