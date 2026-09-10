const isParallelReturn = (edge, parallelId) => edge?.target === parallelId
  && String(edge?.targetPort || '').startsWith('parallel-return:')

const parallelUpstreamNodeIds = (definition, parallelId) => {
  const result = new Set()
  const pending = [parallelId]
  while (pending.length) {
    const current = pending.shift()
    ;(definition?.edges || []).filter(edge => edge.target === current
      && !String(edge.targetPort || '').startsWith('parallel-return:'))
      .forEach(edge => {
        if (edge.source === '__start__' || edge.source === parallelId || result.has(edge.source)) return
        result.add(edge.source)
        pending.push(edge.source)
      })
  }
  return result
}

export const PARALLEL_DEFAULT_CONFIG = {
  version: 2,
  completionMode: 'ALL_SUCCEEDED',
  branches: [
    {key: 'branch_1', name: '任务线 1', resultNodeId: ''},
    {key: 'branch_2', name: '任务线 2', resultNodeId: ''}
  ]
}

export function normalizeParallelConfig(config) {
  const current = config && typeof config === 'object' && !Array.isArray(config) ? config : {}
  const source = Array.isArray(current.branches) && current.branches.length
    ? current.branches : PARALLEL_DEFAULT_CONFIG.branches
  const used = new Set()
  const branches = source.map((branch, index) => {
    let key = String(branch?.key || `branch_${index + 1}`)
    if (!/^[A-Za-z][A-Za-z0-9_.-]{0,63}$/.test(key) || used.has(key)) {
      let suffix = index + 1
      do key = `branch_${suffix++}`; while (used.has(key))
    }
    used.add(key)
    return {
      key,
      name: String(branch?.name || `任务线 ${index + 1}`),
      resultNodeId: String(branch?.resultNodeId || '')
    }
  })
  while (branches.length < 2) {
    const index = branches.length + 1
    branches.push({key: `branch_${index}`, name: `任务线 ${index}`, resultNodeId: ''})
  }
  return {version: 2, completionMode: 'ALL_SUCCEEDED', branches: branches.slice(0, 20)}
}

/** 任务线入口必须是尚未接入主图的节点，或当前并行节点已经连接的入口。 */
export function parallelEntryNodeOptions(definition, parallelId) {
  if (!parallelId) return []
  const upstream = parallelUpstreamNodeIds(definition, parallelId)
  return (definition?.nodes || []).filter(node => {
    if (!node?.id || node.id === parallelId || upstream.has(node.id)) return false
    const incoming = (definition?.edges || []).filter(edge => edge.target === node.id
      && !String(edge.targetPort || '').startsWith('parallel-return:'))
    return incoming.length === 0 || incoming.every(edge => edge.source === parallelId
      && edge.kind === 'PARALLEL')
  }).map(node => ({id: node.id, name: node.name}))
}

export function parallelEntryOptionsForBranch(
  options, branchTargets, branchKey, continuationTarget
) {
  const occupied = new Set(Object.entries(branchTargets || {})
    .filter(([key]) => key !== branchKey)
    .map(([, target]) => target)
    .filter(Boolean))
  if (continuationTarget) occupied.add(continuationTarget)
  return (options || []).filter(option => !occupied.has(option.id))
}

export function parallelAvailableContinuationOptions(options, branchTargets) {
  const occupied = new Set(Object.values(branchTargets || {}).filter(Boolean))
  return (options || []).filter(option => !occupied.has(option.id))
}

export function workflowNodeOptionLabel(node) {
  const id = String(node?.id || '')
  const name = String(node?.name || '').trim()
  return name && name !== id ? `${name} · ${id}` : (name || id)
}

/** 返回一条任务线真实可达、且所有路径都汇入的末节点。 */
export function parallelResultNodeOptions(
  definition, parallelId, entryTarget, continuationTarget, otherEntryTargets = []
) {
  if (!parallelId || !entryTarget) return []
  const edges = definition?.edges || []
  const boundaries = new Set(['__end__', parallelId, continuationTarget, ...otherEntryTargets].filter(Boolean))
  const reachable = new Set()
  const pending = [entryTarget]
  while (pending.length) {
    const current = pending.shift()
    if (!current || boundaries.has(current) || reachable.has(current)) continue
    reachable.add(current)
    edges.filter(edge => edge.source === current && !isParallelReturn(edge, parallelId))
      .forEach(edge => pending.push(edge.target))
  }

  const allPathsReach = candidateId => {
    const memo = new Map()
    const visiting = new Set()
    const visit = current => {
      if (current === candidateId) return true
      if (!current || boundaries.has(current)) return false
      if (memo.has(current)) return memo.get(current)
      if (visiting.has(current)) return false
      visiting.add(current)
      const outgoing = edges.filter(edge => edge.source === current
        && !isParallelReturn(edge, parallelId))
      const result = outgoing.length > 0 && outgoing.every(edge => visit(edge.target))
      visiting.delete(current)
      memo.set(current, result)
      return result
    }
    return visit(entryTarget)
  }

  return (definition?.nodes || []).filter(node => {
    if (!reachable.has(node.id)
      || ['condition', 'parallel', 'llm_classifier', 'loop'].includes(node.type)) return false
    const outgoing = edges.filter(edge => edge.source === node.id)
    const terminal = !outgoing.length || outgoing.every(edge =>
      isParallelReturn(edge, parallelId) || boundaries.has(edge.target))
    return terminal && allPathsReach(node.id)
  }).map(node => ({id: node.id, name: node.name}))
}

/** 根据每条任务线的末节点重建系统返回边，并移除末节点原先直达后续的边。 */
export function withSynchronizedParallelReturns(
  definition, parallelId, branches, continuationTarget, createEdgeId
) {
  const terminalTargets = new Set(['__end__'])
  if (continuationTarget) terminalTargets.add(continuationTarget)
  const resultIds = new Set((branches || []).map(branch => branch.resultNodeId).filter(Boolean))
  const edges = (definition?.edges || []).filter(edge => {
    if (isParallelReturn(edge, parallelId)) return false
    return !(resultIds.has(edge.source) && terminalTargets.has(edge.target))
  })
  const returns = (branches || []).filter(branch => branch.key && branch.resultNodeId)
    .map(branch => ({
      id: createEdgeId(),
      source: branch.resultNodeId,
      target: parallelId,
      targetPort: `parallel-return:${branch.key}`,
      kind: 'NORMAL',
      default: false
    }))
  return [...edges, ...returns]
}
