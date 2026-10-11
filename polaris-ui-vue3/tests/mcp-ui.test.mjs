import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import {parse} from '@vue/compiler-sfc'

// 直接测试现有 Options API 方法；不引入额外测试框架，也不连接后端。
const source = readFileSync(new URL('../src/views/platform/mcp/index.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const script = descriptor.script.content.replace(/^import .*$/gm, '').replace('export default {', 'const component = {')

function methods(listMcpConnection = async () => ({ data: { rows: [], total: 0 } })) {
  return vm.runInNewContext(`${script}\ncomponent.methods`, {
    listMcpConnection, McpToolCatalog: {}, McpToolInvoker: {}
  })
}

function context() {
  return { loading: false, loadError: false, loadSequence: 0, rows: [], total: 0,
    query: { pageNum: 1, pageSize: 10, connectionName: '', status: '' } }
}

function deferred() {
  let resolve, reject
  const promise = new Promise((accept, fail) => { resolve = accept; reject = fail })
  return { promise, resolve, reject }
}

test('查询快照独立，旧响应不会覆盖新筛选结果', async () => {
  const first = deferred(), second = deferred(), requests = []
  const api = methods(query => {
    requests.push(query)
    return requests.length === 1 ? first.promise : second.promise
  })
  const state = context()
  const oldRequest = api.getList.call(state)
  state.query.connectionName = '新筛选'
  const newRequest = api.getList.call(state)
  assert.equal(requests[0].connectionName, '')
  second.resolve({ data: { rows: [{ id: 2 }], total: 1 } })
  await newRequest
  first.resolve({ data: { rows: [{ id: 1 }], total: 99 } })
  await oldRequest
  assert.equal(state.rows[0].id, 2)
  assert.equal(state.total, 1)
  assert.equal(state.loading, false)
  assert.equal(state.loadError, false)
})

test('旧失败不会将新查询误标为加载失败', async () => {
  const first = deferred(), second = deferred()
  let count = 0
  const api = methods(() => ++count === 1 ? first.promise : second.promise)
  const state = context()
  const oldRequest = api.getList.call(state)
  const newRequest = api.getList.call(state)
  second.resolve({ data: { rows: [{ id: 2 }], total: 1 } })
  await newRequest
  first.reject(new Error('旧请求失败'))
  await oldRequest
  assert.equal(state.loadError, false)
  assert.equal(state.rows[0].id, 2)
})

test('最新查询失败时保留旧列表并显式标记错误', async () => {
  const api = methods(async () => { throw new Error('请求失败') })
  const state = context()
  state.rows = [{ id: 1 }]
  state.total = 1
  await api.getList.call(state)
  assert.equal(state.rows[0].id, 1)
  assert.equal(state.total, 1)
  assert.equal(state.loadError, true)
  assert.equal(state.loading, false)
})

test('重置筛选回到第一页并保留分页大小', () => {
  const api = methods(), state = context()
  let calls = 0
  state.query = { pageNum: 3, pageSize: 20, connectionName: '名称', status: '1' }
  state.getList = () => { calls++ }
  state.search = api.search.bind(state)
  api.resetQuery.call(state)
  assert.equal(state.query.pageNum, 1)
  assert.equal(state.query.pageSize, 20)
  assert.equal(state.query.connectionName, '')
  assert.equal(state.query.status, '')
  assert.equal(calls, 1)
})

test('行内调用记录传递连接 ID 而非整个连接对象', () => {
  const api = methods(), calls = []
  const state = { probing: false, $refs: { invocations: { showRecords: id => calls.push(id) } } }
  api.handleRowCommand.call(state, 'records', { id: 42, status: '0' })
  assert.deepEqual(calls, [42])
})

test('更多菜单保持修改、预览及删除原有处理流程', () => {
  const api = methods(), calls = [], row = { id: 42, status: '0' }
  const state = { probing: false,
    handleEdit: value => calls.push(['edit', value]),
    handleProbe: (value, discover) => calls.push(['preview', value, discover]),
    handleDelete: value => calls.push(['delete', value]) }
  for (const command of ['edit', 'preview', 'delete']) api.handleRowCommand.call(state, command, row)
  assert.deepEqual(calls, [['edit', row], ['preview', row, true], ['delete', row]])
})

test('探测中及停用连接不会发起额外发现请求', () => {
  const api = methods(), calls = []
  const state = { probing: true, handleProbe: () => calls.push('preview') }
  api.handleRowCommand.call(state, 'preview', { id: 42, status: '0' })
  state.probing = false
  api.handleRowCommand.call(state, 'preview', { id: 42, status: '1' })
  assert.deepEqual(calls, [])
})
