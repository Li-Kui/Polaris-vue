const snapshotFields = ['toolId', 'revision', 'connectionRevision', 'catalogRevision', 'structureHash']

export function mcpSnapshot(resource) {
  const snapshot = resource?.attributes?.snapshot
  if (!resource?.available || resource.kind !== 'MCP_TOOL' || !snapshot
    || typeof snapshot.toolId !== 'string' || !/^[1-9][0-9]{0,18}$/.test(snapshot.toolId)
    || snapshot.toolId !== String(resource.resourceId)
    || typeof snapshot.structureHash !== 'string'
    || !/^[0-9a-f]{64}$/.test(snapshot.structureHash || '')
    || !['revision', 'connectionRevision', 'catalogRevision'].every(field =>
      Number.isSafeInteger(snapshot[field]) && snapshot[field] > 0)) return null
  return Object.fromEntries(snapshotFields.map(field => [field, snapshot[field]]))
}

export function mcpSnapshotMatches(config, resource) {
  const snapshot = mcpSnapshot(resource)
  return !!snapshot && snapshotFields.every(field => config?.[field] === snapshot[field])
}

export function mcpNodeSchema(config, resource, descriptor) {
  const ready = mcpSnapshotMatches(config, resource)
    && resource.attributes.inputSchema && resource.attributes.outputSchema
  return {
    inputSchema: ready ? resource.attributes.inputSchema : descriptor?.inputSchema || {type: 'object', properties: {}, additionalProperties: false},
    outputSchema: ready ? resource.attributes.outputSchema : descriptor?.outputSchema || {type: 'object', properties: {}},
    source: ready ? 'MCP 工具契约' : '节点契约',
    sourceCode: ready ? 'MCP_TOOL' : 'NODE_CONTRACT',
    sourceVersion: ready ? snapshotFields.map(field => config[field]).join(':') : '',
    diagnostics: ready ? [] : ['MCP 工具未绑定、不可用或版本已变化，请明确采用当前已审批版本']
  }
}
