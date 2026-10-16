import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'

const source = readFileSync(new URL('../src/views/ai/knowledge.vue', import.meta.url), 'utf8')

test('知识库使用模型中心稳定投影，不因缺失旧维度字段禁用可用向量模型', () => {
  const {descriptor} = parse(source)
  const script = compileScript(descriptor, {id: 'knowledge-model-test'})
  const template = compileTemplate({source: descriptor.template.content, filename: 'knowledge.vue',
    id: 'knowledge-model-test', compilerOptions: {bindingMetadata: script.bindings}})
  assert.deepEqual(template.errors, [])
  assert.doesNotMatch(descriptor.template.content, /model\.embeddingDimension/)
  assert.match(descriptor.template.content, /:value="model.id"/)
  assert.match(descriptor.template.content, /保存时由后端探测并校验向量维度/)
})
