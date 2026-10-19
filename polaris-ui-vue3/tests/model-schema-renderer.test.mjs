import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
import {createRequire} from 'node:module'
import test from 'node:test'
import {compileScript, parse} from '@vue/compiler-sfc'
import * as Vue from 'vue'
import {createSSRApp} from 'vue'
import ElementPlus, {ID_INJECTION_KEY} from 'element-plus'

// Match the renderer to Vue itself; VitePress also installs a newer renderer.
const require = createRequire(import.meta.url)
const {renderToString} = createRequire(require.resolve('vue'))('@vue/server-renderer')

const source = readFileSync(new URL('../src/components/model-center/SchemaFormRenderer.vue', import.meta.url), 'utf8')
const {descriptor} = parse(source)
const script = compileScript(descriptor, {id: 'model-schema-renderer', inlineTemplate: true}).content
  .replace(/import\s*\{([\s\S]*?)\}\s*from\s*['"]vue['"]/g,
    (_, bindings) => `const {${bindings.replace(/\s+as\s+/g, ':')}} = Vue`)
  .replace('export default', 'const Renderer =')
const Renderer = vm.runInNewContext(`${script}\nRenderer`, {Vue})

async function render(properties, modelValue = {}) {
  const updates = []
  const app = createSSRApp(Renderer, {schema: {properties}, modelValue,
    'onUpdate:modelValue': value => updates.push(value)}).use(ElementPlus)
  app.provide(ID_INJECTION_KEY, {prefix: 100, current: 0})
  const errors = []
  app.config.errorHandler = error => errors.push(error)
  const html = await renderToString(app)
  assert.deepEqual(errors.map(error => error.message), [], '真实 Element Plus 控件不应因 Schema 边界而崩溃')
  assert.deepEqual(updates, [], '渲染不能因空边界而夹断或改写已保存配置')
  return html
}

test('模型详情的空上限不转换为零，向量维度与最大输入长度可以渲染', async () => {
  const html = await render({
    dimension: {type: 'integer', title: '向量维度', minimum: 1, maximum: null},
    maxInputTokens: {type: 'integer', title: '最大输入长度', minimum: 1, maximum: null}
  }, {dimension: 1024, maxInputTokens: 8192})
  assert.match(html, /min="1"/)
  assert.match(html, /max="9007199254740991"/)
})

test('空下限不限制负数，明确的零上限仍保留', async () => {
  const html = await render({pitch: {type: 'number', title: '音调', minimum: null, maximum: 0}}, {pitch: -5})
  assert.match(html, /min="-9007199254740991"/)
  assert.match(html, /max="0"/)
})

test('编辑器上下文缺失边界与详情显式空边界均可渲染', async () => {
  for (const bounds of [{}, {minimum: null, maximum: null}, {minimum: 0, maximum: 2}]) {
    const html = await render({temperature: {type: 'number', ...bounds}}, {temperature: 0})
    assert.match(html, /el-input-number/)
  }
})
