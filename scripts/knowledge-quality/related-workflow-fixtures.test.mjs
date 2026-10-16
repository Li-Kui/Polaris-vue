import assert from 'node:assert/strict'
import test from 'node:test'
import {definition, missingQuery, query, variants} from './related-workflow-fixtures.mjs'

test('C1 工作流仅知识库读取与确定性下游复制，无回答模型、外部写入或重试', () => {
  for (const item of variants) {
    const flow = definition(item.options)
    assert.deepEqual(flow.nodes.map(node => node.type), ['knowledge_retrieval', 'transform'])
    assert.ok(flow.nodes.every(node => node.retryPolicy.maxAttempts === 1 && node.onError === 'FAIL'))
    assert.equal(flow.nodes[1].inputMapping.source.expression, '$.nodes.retrieve.output')
    assert.equal(flow.outputs.references.expression, '$.nodes.retrieve.output.results')
    assert.equal(flow.outputs.knowledgeContext.expression, '$.nodes.retrieve.output.knowledgeContext')
    assert.equal(flow.inputs.additionalProperties, false)
    assert.equal(flow.nodes[0].resourceRefs.length, item.id === 'multiple' ? 2 : 1)
  }
})

test('缺省工作流不写入关联输入，新输入有明确数组边界，缺失控制题不复用旧保留集', () => {
  const legacy = definition({related: false}), related = definition()
  assert.ok(!Object.hasOwn(legacy.inputs.properties, 'relatedDocumentIds'))
  assert.ok(!Object.hasOwn(legacy.nodes[0].inputMapping, 'relatedDocumentIds'))
  assert.equal(related.inputs.properties.relatedDocumentIds.maxItems, 3)
  assert.equal(related.inputs.properties.relatedDocumentIds.uniqueItems, true)
  assert.ok(query.includes('沧岚资料站')); assert.notEqual(query, missingQuery)
})
