const isLoopReturn = (edge, loopId) => edge?.target === loopId
  && edge?.targetPort === 'loop-return'

/**
 * 返回从循环入口真实可达、并且可以安全作为“本轮结束”的节点。
 *
 * 末节点允许仍然连接“全部完成”出口或流程结束；选择后这条普通出口会由
 * 系统转换成隐藏的 loop-return。这样已有的线性流程可以直接包进循环，
 * 不需要用户先删除连线再配置循环。
 */
export function loopResultNodeOptions(definition, loopId, bodyTarget, exitTarget) {
  if (!loopId || !bodyTarget) return []
  const edges = definition?.edges || []
  const reachable = new Set()
  const pending = [bodyTarget]

  while (pending.length) {
    const current = pending.shift()
    if (!current || current === loopId || current === exitTarget
        || current === '__end__' || reachable.has(current)) continue
    reachable.add(current)
    edges.filter(edge => edge.source === current && !isLoopReturn(edge, loopId))
      .forEach(edge => pending.push(edge.target))
  }

  const terminalTargets = new Set(['__end__'])
  if (exitTarget) terminalTargets.add(exitTarget)
  const allPathsReach = candidateId => {
    const memo = new Map()
    const visiting = new Set()
    const visit = current => {
      if (current === candidateId) return true
      if (!current || current === loopId || terminalTargets.has(current)) return false
      if (memo.has(current)) return memo.get(current)
      if (visiting.has(current)) return false
      visiting.add(current)
      const outgoing = edges.filter(edge => edge.source === current
        && !isLoopReturn(edge, loopId))
      const result = outgoing.length > 0 && outgoing.every(edge => visit(edge.target))
      visiting.delete(current)
      memo.set(current, result)
      return result
    }
    return visit(bodyTarget)
  }
  return (definition?.nodes || []).filter(node => {
    if (!reachable.has(node.id) || node.id === exitTarget
        || ['condition', 'parallel', 'llm_classifier', 'loop'].includes(node.type)) return false
    const outgoing = edges.filter(edge => edge.source === node.id)
    const terminal = !outgoing.length || outgoing.every(edge =>
      isLoopReturn(edge, loopId) || terminalTargets.has(edge.target))
    return terminal && allPathsReach(node.id)
  }).map(node => ({id: node.id, name: node.name}))
}

/** 将用户选择的末节点接回循环，并移除它原先直达完成出口的连线。 */
export function withSynchronizedLoopReturn(
  definition, loopId, resultNodeId, exitTarget, createEdgeId
) {
  const terminalTargets = new Set(['__end__'])
  if (exitTarget) terminalTargets.add(exitTarget)
  const edges = (definition?.edges || []).filter(edge => {
    if (isLoopReturn(edge, loopId)) return false
    return !(resultNodeId && edge.source === resultNodeId
      && terminalTargets.has(edge.target))
  })
  if (!resultNodeId) return edges
  return [...edges, {
    id: createEdgeId(),
    source: resultNodeId,
    target: loopId,
    targetPort: 'loop-return',
    kind: 'NORMAL',
    default: false
  }]
}
