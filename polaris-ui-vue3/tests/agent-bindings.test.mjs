import assert from 'node:assert/strict'
import test from 'node:test'
import {readFileSync} from 'node:fs'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import {
    bindingPayload,
    bindingsChanged,
    invocationSourceLabel,
    isWorkflowInvocation
} from '../src/components/agent/agentBindings.js'

test('绑定保存只发送管理员配置，空查询参数规范化且不改变原对象', () => {
  const row = {toolId: 10, purpose: '公开查询', queryArgument: ' ', fixedArgumentsJson: '{}', snapshot: {secret: true}, externalQuery: 'PRIVATE', revision: 1}
  assert.deepEqual(bindingPayload([row]), [{toolId: 10, purpose: '公开查询', queryArgument: null, fixedArgumentsJson: '{}'}])
  assert.equal(row.queryArgument, ' ')
})
test('协作审计使用独立来源，未知来源不误标为手工', () => {
  assert.equal(invocationSourceLabel('AGENT_WORKFLOW'), '协作智能体')
  assert.equal(invocationSourceLabel('UNKNOWN'), '未知来源')
  assert.equal(isWorkflowInvocation('AGENT_WORKFLOW'), true)
  assert.equal(isWorkflowInvocation('MANUAL'), false)
})
test('工具身份不变时也可以显式采用新的审批版本，刷新标记不进入请求', () => {
  const row = {toolId: 10, purpose: '公开查询', queryArgument: 'query', fixedArgumentsJson: '{}'}
  const baseline = JSON.stringify(bindingPayload([row]))
  assert.equal(bindingsChanged([row], baseline), false)
  row.refreshSnapshot = true
  assert.equal(bindingsChanged([row], baseline), true)
  assert.equal(Object.hasOwn(bindingPayload([row])[0], 'refreshSnapshot'), false)
})
test('绑定与主页面 Vue 模板编译通过', () => {
  for (const path of ['../src/components/agent/AgentMcpBindings.vue', '../src/views/ai/agent.vue']) {
    const {descriptor, errors} = parse(readFileSync(new URL(path, import.meta.url), 'utf8'))
    assert.deepEqual(errors, [])
    const script = compileScript(descriptor, {id: 'agent-bindings'})
    assert.deepEqual(compileTemplate({source: descriptor.template.content, filename: path, id: 'agent-bindings', compilerOptions: {bindingMetadata: script.bindings}}).errors, [])
  }
})
