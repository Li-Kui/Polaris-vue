import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import {readFile} from 'node:fs/promises'
import {compileScript, parse} from '@vue/compiler-sfc'
import {transformWithEsbuild} from 'vite'
import * as Vue from 'vue'
import * as contact from '../src/utils/customerContactState.js'
import * as voice from '../src/utils/customerVoiceConfiguration.js'
import * as service from '../src/utils/customerServiceState.js'

const source = await readFile(new URL('../src/views/platform/customerService/index.vue', import.meta.url), 'utf8')
const compiled = await transformWithEsbuild(compileScript(parse(source).descriptor, { id: 'customer-ui' }).content, 'customer-ui.js', { format: 'cjs', target: 'esnext' })
function fixture() {
  const calls = [], notices = [], rows = [
    { id: 1, revision: 2, status: 'ACTIVE', currentReleaseId: 'published', config: { name: '星河客服', persona: '规则', welcomeMessage: '你好', noAnswerMessage: '没有依据', modelId: 11, knowledgeBaseIds: [11], capabilities: [], voice: { vocabulary: ['星河归档中心'] } } },
    { id: 2, status: 'DISABLED', currentReleaseId: 'old', config: { name: '停用客服' } },
    { id: 3, status: 'ACTIVE', currentReleaseId: null, config: { name: '未发布客服' } }
  ]
  const api = {
    listCustomerServices: async () => { calls.push('list'); return { data: structuredClone(rows) } },
    listCustomerSpeechModels: async () => ({ data: [] }),
    updateCustomerService: async (...args) => { calls.push(['update', ...args]) }
  }
  const modules = {
    vue: { ...Vue, onMounted() {} }, 'element-plus': { ElMessage: { warning: value => notices.push(value), success() {} }, ElMessageBox: {} },
    '@/api/platform/customerService': api, '@/api/ai/model': { listAvailableModel: async () => ({ data: [] }) }, '@/api/ai/knowledge': { listKnowledge: async () => ({ data: [] }) },
    '@/utils/customerContactState': contact, '@/utils/customerVoiceConfiguration': voice, '@/utils/customerServiceState': service,
    './customerService.scss': {}, ...Object.fromEntries(['CustomerAvatar', 'CustomerConversation', 'CustomerCapabilityEditor', 'CustomerChannelPanel', 'CustomerAvatarEditor', 'CustomerRecordPanel', 'CustomerContactEditor'].map(name => [`./${name}.vue`, {}]))
  }
  const module = { exports: {} }
  vm.runInNewContext(compiled.code, { module, exports: module.exports, require: name => { assert.ok(name in modules, name); return modules[name] } })
  const scope = Vue.effectScope(), state = scope.run(() => module.exports.default.setup({}, { expose() {} }))
  state.formRef.value = { validate: async () => true, scrollToField() {} }
  return { state, calls, notices, rows, dispose: () => scope.stop() }
}
test('筛选与卡片/列表切换只改变视图，不改发布和原始列表，不重新请求接口', async () => {
  const f = fixture(); await f.state.loadApps()
  f.state.search.value = '星河'; assert.equal(f.state.filteredApps.value.length, 1)
  f.state.search.value = ''; f.state.publishFilter.value = 'DRAFT'; assert.equal(f.state.filteredApps.value[0].id, 3)
  f.state.publishFilter.value = 'ALL'; f.state.statusFilter.value = 'DISABLED'; assert.equal(f.state.filteredApps.value[0].id, 2)
  f.state.listView.value = 'table'; assert.equal(f.state.apps.value.length, 3); assert.deepEqual(f.calls, ['list'])
  assert.equal(f.rows[0].currentReleaseId, 'published'); f.dispose()
})
test('分区编辑保留未保存值，只在保存时带原修订提交，取消不写回原客服', async () => {
  const f = fixture(); await f.state.openEdit(f.rows[0])
  f.state.form.name = '新名称'; f.state.editorTab.value = 'voice'; f.state.editorTab.value = 'knowledge'
  assert.equal(f.state.form.name, '新名称'); assert.equal(f.rows[0].config.name, '星河客服'); assert.equal(f.calls.length, 0)
  await f.state.save()
  assert.equal(f.calls[0][0], 'update'); assert.equal(f.calls[0][1], 1); assert.equal(f.calls[0][2], 2)
  assert.equal(f.calls[0][3].name, '新名称'); assert.equal(f.state.editorOpen.value, false); f.dispose()
})
test('隐藏分区校验失败自动切到对应分区且不提交，术语与人工配置错误定位正确', async () => {
  const f = fixture(); await f.state.openEdit(f.rows[0]); let scrolled
  f.state.formRef.value = { validate: async () => { throw { modelId: [{ message: '必填' }] } }, scrollToField: key => { scrolled = key } }
  await f.state.save(); assert.equal(f.state.editorTab.value, 'knowledge'); assert.equal(scrolled, 'modelId'); assert.equal(f.calls.length, 0)
  f.state.formRef.value.validate = async () => true
  f.state.vocabularyText.value = '重复\n重复'; await f.state.save(); assert.equal(f.state.editorTab.value, 'voice'); assert.equal(f.calls.length, 0)
  f.state.vocabularyText.value = ''; f.state.form.contact = { name: '人工', webUrl: 'http://invalid.example' }
  await f.state.save(); assert.equal(f.state.editorTab.value, 'contact'); assert.equal(f.calls.length, 0); f.dispose()
})
