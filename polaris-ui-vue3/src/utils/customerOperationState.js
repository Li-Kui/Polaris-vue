import {customerParameterIssue, customerParameterLabel} from './customerParameterState.js'

export function customerOperationInput(capability, values) {
  const entries = []
  for (const parameter of capability.inputs) {
    const label = customerParameterLabel(parameter)
    let value = values[parameter.name]
    if (value === '' || value === undefined || value === null) {
      if (parameter.required) throw new Error(`请填写 ${label}`)
      continue
    }
    if (parameter.type === 'integer' || parameter.type === 'number') {
      if (typeof value === 'boolean' || typeof value === 'string' && !value.trim()) throw new Error(`${label} 须为有效数字`)
      value = Number(value)
      if (!Number.isFinite(value) || parameter.type === 'integer' && !Number.isSafeInteger(value)) throw new Error(`${label} 须为有效数字，整数不能超过安全精度`)
    } else if (parameter.type === 'boolean') {
      if (typeof value !== 'boolean') throw new Error(`${label} 须选择是或否`)
    } else if (typeof value !== 'string' || value.length > 2000) throw new Error(`${label} 须为 2000 字以内文本`)
    const issue = customerParameterIssue(parameter, value)
    if (issue) throw new Error(issue)
    entries.push([parameter.name, value])
  }
  return Object.fromEntries(entries)
}

export function customerOperationRequest(capability, values, pending, makeId = () => crypto.randomUUID()) {
  if (pending) return pending
  return Object.freeze({ requestId: makeId(), capabilityCode: capability.code, input: Object.freeze(customerOperationInput(capability, values)) })
}

export function mergeCustomerOperation(operations, value, requestId) {
  if (value.requestId !== requestId) return operations
  const found = operations.some(item => item.requestId === requestId)
  return found ? operations.map(item => item.requestId === requestId ? value : item) : [...operations, value]
}

export function customerOperationActive(operation) {
  return ['QUEUED', 'RUNNING', 'RECOVERING', 'NEEDS_ATTENTION', 'WAITING_APPROVAL', 'WAITING_TIMER', 'WAITING_EVENT'].includes(operation.status)
}

export function customerOperationBlocking(operation) {
  return !['SUCCEEDED', 'FAILED', 'CANCELLED', 'REJECTED', 'RESULT_REJECTED'].includes(operation.status)
}

export function customerOperationLabel(state) {
  return ({ QUEUED: '排队中', RUNNING: '执行中', RECOVERING: '恢复中', NEEDS_ATTENTION: '需要检查', SUCCEEDED: '已完成',
    FAILED: '执行失败', REJECTED: '执行被拒绝', CANCELLED: '已取消', RESULT_REJECTED: '结果未通过展示校验', UNAVAILABLE: '当前结果不可读取' })[state] || '状态待核对'
}
