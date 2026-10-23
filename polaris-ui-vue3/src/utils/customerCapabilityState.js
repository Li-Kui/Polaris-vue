export function customerCapabilityDraft(value) {
  return {
    code: value?.code || '', name: value?.name || '', description: value?.description || '',
    definitionId: value?.definitionId || null, versionId: value?.versionId || '',
    inputSources: { ...value?.inputSources }, outputFields: [...(value?.outputFields || [])]
  }
}

export function customerCapabilityContract(draft, contract) {
  if (draft.definitionId !== contract.definitionId || draft.versionId !== contract.versionId) return null
  const sources = ['USER_INPUT', 'PLATFORM_USER_ID', 'TENANT_ID']
  return {
    ...customerCapabilityDraft(draft),
    inputSources: Object.fromEntries(contract.inputs.map(input => [input.name,
      sources.includes(draft.inputSources[input.name]) ? draft.inputSources[input.name] : 'USER_INPUT'])),
    outputFields: draft.outputFields.filter(field => contract.outputFields.includes(field))
  }
}

export function customerCapabilitySave(values, index, draft) {
  if (index !== null && (!Number.isInteger(index) || index < 0 || index >= values.length)) throw new Error('待编辑的能力已变化，请重新打开')
  if (values.some((value, position) => position !== index && value.code === draft.code)) throw new Error('能力编码不能重复')
  if (index === null && values.length >= 5) throw new Error('最多配置 5 项能力')
  const result = values.map(customerCapabilityDraft)
  if (index === null) result.push(customerCapabilityDraft(draft))
  else result[index] = customerCapabilityDraft(draft)
  return result
}
