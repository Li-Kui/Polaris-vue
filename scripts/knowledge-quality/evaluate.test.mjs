import assert from 'node:assert/strict'
import test from 'node:test'
import {mkdtemp, readFile, stat, writeFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {suite} from './suite.mjs'
import {suite as smoke} from './smoke-suite.mjs'
import {collect, compareReports, evaluate, main, scoreCase, validateManifest, validateSuite} from './evaluate.mjs'

function manifestFor(dataset = suite) {
  return {knowledgeId: '4', indexVersion: '1', embeddingModelId: '9', embeddingModelRevision: '1',
    embeddingSchemaHash: 'schema-fixture', embeddingDimension: 2, indexSignature: 'fixture-index',
    chunkSize: 300, chunkOverlap: 30, splitterType: 'RECURSIVE', corpusVerified: true,
    corpusHash: validateSuite(dataset).corpusHash,
    documents: Object.fromEntries(dataset.documents.map((doc, i) => [doc.key, String(i + 15)]))}
}
function responseFor(item, manifest, results) {
  return {knowledgeId: Number(manifest.knowledgeId), indexVersion: Number(manifest.indexVersion),
    strategy: 'VECTOR_V1', topK: 5, minScore: 0.5, state: item.evidence.length ? 'FOUND' : 'EMPTY', durationMs: 25,
    results: results ?? item.evidence.map((e, i) => ({rank: i + 1,
      documentId: manifest.documents[e.documentKey], content: e.text, score: 0.8}))}
}
const manifest = manifestFor()
const first = suite.cases[0]
const copy = value => structuredClone(value)

test('固定语料为十份，题库六十条；40 开发/20 保留，五类配额固定', () => {
  validateSuite(suite); validateSuite(smoke)
  assert.equal(suite.documents.length, 10); assert.equal(suite.cases.length, 60)
  assert.equal(suite.cases.filter(c => c.split === 'dev').length, 40)
  assert.equal(suite.cases.filter(c => c.split === 'holdout').length, 20)
  assert.deepEqual(Object.fromEntries(['DIRECT', 'SYNONYM', 'TERM', 'TABLE_CROSS', 'NO_ANSWER']
    .map(category => [category, suite.cases.filter(c => c.category === category).length])),
  {DIRECT: 20, SYNONYM: 10, TERM: 10, TABLE_CROSS: 10, NO_ANSWER: 10})
  for (const item of suite.cases) assert.ok(!suite.documents.some(doc => doc.content.includes(item.query)))
})

test('重复问题、未知来源和不在正文里的支持标注均拒绝', () => {
  for (const mutate of [s => {s.cases[1].query = s.cases[0].query},
    s => {s.cases[0].evidence[0].documentKey = 'absent'}, s => {s.cases[0].evidence[0].text = '伪造答案'},
    s => {s.cases[0].evidence = []}]) {
    const invalid = copy(suite); mutate(invalid); assert.throws(() => validateSuite(invalid))
  }
})

test('人工确认、语料摘要、索引/模型和 ID 映射缺失均拒绝', () => {
  for (const mutate of [m => {m.corpusVerified = false}, m => {m.corpusHash = 'wrong'},
    m => {m.documents.archive = m.documents.visit}, m => {m.documents.archive = '1e3'},
    m => {m.embeddingModelRevision = null}, m => {m.indexSignature = ''}, m => {m.chunkOverlap = m.chunkSize}]) {
    const invalid = copy(manifest); mutate(invalid); assert.throws(() => validateManifest(suite, invalid))
  }
})

test('正确文档必须同时含支持片段；只命中文件或错误文档正文均不能得分', () => {
  assert.equal(scoreCase(first, responseFor(first, manifest), manifest, 0.5).evidenceHit, true)
  for (const hit of [{rank: 1, documentId: '15', content: '只有相关主题', score: 0.9},
    {rank: 1, documentId: '16', content: first.evidence[0].text, score: 0.9}]) {
    assert.equal(scoreCase(first, responseFor(first, manifest, [hit]), manifest, 0.5).evidenceHit, false)
  }
})

test('表格与跨段分别检查所有必需支持，部分命中不等于完整证据', () => {
  for (const item of [suite.cases.find(c => c.category === 'TABLE_CROSS'),
    suite.cases.find(c => c.category === 'TABLE_CROSS' && c.evidence[0].documentKey !== c.evidence[1].documentKey)]) {
    const response = responseFor(item, manifest); response.results.pop()
    const score = scoreCase(item, response, manifest, 0.5)
    assert.equal(score.evidenceHit, true); assert.equal(score.completeEvidence, false); assert.equal(score.evidenceCoverage, 0.5)
  }
})

test('仅归一化宽度和空白，不使用模型自评或宽泛关键词打分', () => {
  const response = responseFor(first, manifest); response.results[0].content = first.evidence[0].text.split('').join(' ')
  assert.equal(scoreCase(first, response, manifest, 0.5).completeEvidence, true)
  response.results[0].content = '星期三'; assert.equal(scoreCase(first, response, manifest, 0.5).completeEvidence, false)
})

test('错误、未执行、空命中三者分开，未执行不能算成全空召回', () => {
  const report = evaluate(suite, manifest, [{caseId: first.id, error: 'secret provider body'}])
  assert.equal(report.summary.errors, 1); assert.equal(report.summary.notRun, 39)
  assert.equal(report.summary.evidenceHitAt5, null); assert.equal(report.allCasesCompleted, false)
  assert.doesNotMatch(JSON.stringify(report), /secret provider body/)
})

test('完整本地假响应只验证计分公式，报告不自动放行质量', () => {
  const observations = suite.cases.filter(c => c.split === 'dev').map(item => ({caseId: item.id, response: responseFor(item, manifest)}))
  const report = evaluate(suite, manifest, observations, {mode: 'SCORER_FIXTURE'})
  assert.equal(report.summary.evidenceHitAt5, 1); assert.equal(report.summary.completeEvidenceAt5, 1)
  assert.equal(report.summary.mrrAt5, 1); assert.equal(report.summary.noAnswerEmptyRate, 1)
  assert.equal(report.summary.retrievalDurationP95Ms, 25); assert.equal(report.qualityReleaseApproved, false)
  assert.throws(() => compareReports(report, report), /INCOMPARABLE/)
})

test('无答案召回相关但不足的片段只记录非空，不称作错误回答或错误拒答', () => {
  const item = suite.cases.find(c => c.category === 'NO_ANSWER')
  const response = {...responseFor(item, manifest), state: 'FOUND', results: [{rank: 1, documentId: '15', content: '相关但无答案', score: 0.6}]}
  const report = evaluate(suite, manifest, [{caseId: item.id, response}])
  assert.equal(report.summary.noAnswerEmptyRate, 0); assert.equal(report.summary.answerableCompleted, 0)
  assert.ok(!('refusalAccuracy' in report.summary))
})

test('报告不保存问题、支持正文、来源名称或检索载荷', () => {
  const report = evaluate(suite, manifest, [{caseId: first.id, response: responseFor(first, manifest)}])
  const json = JSON.stringify(report)
  assert.ok(!json.includes(first.query)); assert.ok(!json.includes(first.evidence[0].text))
  assert.ok(!json.includes('星河归档')); assert.ok(!json.includes('Platform-Token'))
})

test('重复观察、混入保留集、索引/策略漂移和无效响应拒绝计分', () => {
  const record = {caseId: first.id, response: responseFor(first, manifest)}
  assert.throws(() => evaluate(suite, manifest, [record, record]))
  assert.throws(() => evaluate(suite, manifest, [{...record, caseId: suite.cases.find(c => c.split === 'holdout').id}]))
  for (const change of [{indexVersion: 2}, {topK: 20}, {minScore: 0.3}, {strategy: 'OTHER'}, {durationMs: -1}, {state: 'EMPTY'}]) {
    assert.throws(() => scoreCase(first, {...record.response, ...change}, manifest, 0.5))
  }
  assert.throws(() => evaluate(smoke, manifestFor(smoke), [], {split: 'holdout'}), /EMPTY_SPLIT/)
})

test('非连续排名、空正文、非有限分数和非法文档 ID 拒绝', () => {
  for (const change of [{rank: 6}, {content: ''}, {score: NaN}, {documentId: '0'}]) {
    const response = responseFor(first, manifest); Object.assign(response.results[0], change)
    assert.throws(() => scoreCase(first, response, manifest, 0.5))
  }
})

function localApi(dataset, manifest, mutate = () => {}) {
  const calls = []
  const request = async (url, options) => {
    calls.push({url: String(url), options})
    let data = {...manifest, id: Number(manifest.knowledgeId), indexVersion: Number(manifest.indexVersion),
      embeddingModelId: Number(manifest.embeddingModelId), embeddingModelRevision: Number(manifest.embeddingModelRevision), indexStatus: 'READY'}
    if (options.method === 'POST') data = responseFor(dataset.cases.find(c => c.query === JSON.parse(options.body).query), manifest)
    mutate(data, options, calls.length)
    return {ok: true, json: async () => ({code: 200, data})}
  }
  return {calls, request, wait: async () => {}}
}

const config = {base: 'http://localhost:8081', token: 'private-fixture-token', split: 'dev', minScore: 0.5, maxCalls: 4}
test('真实采集仅串行诊断 POST，前后复查快照，无模型生成/上传/重建入口', async () => {
  const m = manifestFor(smoke), api = localApi(smoke, m)
  const waits = []; api.wait = async ms => {waits.push(ms)}
  const report = await collect(smoke, m, config, api)
  assert.equal(api.calls.length, 12); assert.equal(report.allCasesCompleted, true)
  assert.deepEqual(waits, [5200, 5200, 5200])
  const posts = api.calls.filter(call => call.options.method === 'POST')
  assert.equal(posts.length, 4)
  for (const call of posts) {
    assert.ok(call.url.endsWith('/retrieval-test')); assert.equal(call.options.redirect, 'error')
    assert.deepEqual(Object.keys(JSON.parse(call.options.body)), ['query', 'topK', 'minScore', 'embeddingConfirmed'])
    assert.ok(!call.url.includes('星河'))
  }
  assert.doesNotMatch(JSON.stringify(report), /private-fixture-token/)
})

test('非本机地址、URL 凭证、子路径和错误预算在请求前拒绝', async () => {
  for (const options of [{base: 'https://example.com'}, {base: 'http://u:p@localhost'},
    {base: 'http://localhost/path'}, {base: 'file:///tmp'}, {base: 'http://localhost/?token=x'},
    {maxCalls: 0}, {maxCalls: 5}, {token: ''}, {minScore: NaN}]) {
    const m = manifestFor(smoke), api = localApi(smoke, m)
    await assert.rejects(collect(smoke, m, {...config, ...options}, api)); assert.equal(api.calls.length, 0)
  }
})

test('保留集必须额外确认，开发集调用预算可形成明确不完整报告', async () => {
  const api = localApi(suite, manifest)
  await assert.rejects(collect(suite, manifest, {...config, split: 'holdout'}, api))
  assert.equal(api.calls.length, 0)
  const partial = await collect(suite, manifest, {...config, maxCalls: 1}, api)
  assert.equal(partial.summary.completed, 1); assert.equal(partial.summary.notRun, 39); assert.equal(partial.allCasesCompleted, false)
})

test('开始前漂移不派发；派发后漂移丢弃响应，并停止后续调用', async () => {
  const m = manifestFor(smoke)
  for (const point of [1, 3]) {
    const api = localApi(smoke, m, (data, options, count) => {if (count === point) data.indexVersion = 2})
    const report = await collect(smoke, m, config, api)
    assert.equal(api.calls.filter(call => call.options.method === 'POST').length, point === 1 ? 0 : 1)
    assert.equal(report.summary.errors, 1); assert.equal(report.summary.completed, 0); assert.equal(report.summary.notRun, 3)
  }
})

test('网络或提供器错误只记录安全错误，不自动重试或继续消耗预算', async () => {
  let count = 0
  const report = await collect(smoke, manifestFor(smoke), config,
    {request: async () => {count++; throw new Error('Authorization: secret; raw query')}, wait: async () => {}})
  assert.equal(count, 1); assert.equal(report.summary.errors, 1)
  assert.doesNotMatch(JSON.stringify(report), /Authorization|secret|raw query/)
})

test('对比只允许完整、相同语料/来源/索引/模型快照，不跨集比较平均分', () => {
  const m = manifestFor(smoke), observations = smoke.cases.map(item => ({caseId: item.id, response: responseFor(item, m)}))
  const before = evaluate(smoke, m, observations), after = copy(before)
  assert.equal(compareReports(before, after).delta.evidenceHitAt5, 0)
  for (const change of [r => {r.split = 'holdout'}, r => {r.allCasesCompleted = false},
    r => {r.snapshot.indexVersion = '2'}, r => {r.documentMappingHash = 'wrong'}, r => {r.suiteHash = 'wrong'}]) {
    const invalid = copy(after); change(invalid); assert.throws(() => compareReports(before, invalid))
  }
})

test('CLI 不接受未知参数、任意题库路径，或隐式确认外部 Embedding', async () => {
  await assert.rejects(main(['collect', '--out', '/private/tmp/not-created.json', '--confirm', 'yes']))
  await assert.rejects(main(['collect', '--out', '/private/tmp/not-created.json', '--suite', '/tmp/unknown.mjs']))
})

test('本地假服务用 HTTP 200 携带业务错误时停止，不把失败当 EMPTY', async () => {
  let calls = 0
  const report = await collect(smoke, manifestFor(smoke), config, {wait: async () => {},
    request: async () => {calls++; return {ok: true, json: async () => ({code: 403, msg: 'secret details'})}}})
  assert.equal(calls, 1); assert.equal(report.summary.errors, 1); assert.equal(report.summary.completed, 0)
  assert.doesNotMatch(JSON.stringify(report), /secret details/)
})

test('输出文件已经存在时拒绝采集，不覆写历史报告', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'polaris-eval-output-'))
  const manifestFile = join(dir, 'manifest.json'), out = join(dir, 'report.json')
  await writeFile(manifestFile, JSON.stringify(manifest)); await writeFile(out, 'existing report')
  await assert.rejects(main(['collect', '--manifest', manifestFile, '--out', out,
    '--max-calls', '1', '--confirm-embedding', 'yes']), {code: 'EEXIST'})
  assert.equal(await readFile(out, 'utf8'), 'existing report')
})

test('未确认调用在打开输出文件前拒绝；离线复算输出私有、不含载荷', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'polaris-eval-offline-'))
  const manifestFile = join(dir, 'manifest.json'), observationsFile = join(dir, 'observations.json'), out = join(dir, 'report.json')
  await writeFile(manifestFile, JSON.stringify(manifest))
  await assert.rejects(main(['collect', '--manifest', manifestFile, '--out', out, '--max-calls', '1']), /CONFIRMATION_REQUIRED/)
  await assert.rejects(stat(out), {code: 'ENOENT'})
  await writeFile(observationsFile, JSON.stringify([{caseId: first.id, response: responseFor(first, manifest)}]))
  const result = await main(['evaluate', '--manifest', manifestFile, '--observations', observationsFile, '--out', out])
  assert.equal(result.allCasesCompleted, false)
  const json = await readFile(out, 'utf8'); assert.ok(!json.includes(first.query)); assert.ok(!json.includes(first.evidence[0].text))
  assert.equal((await stat(out)).mode & 0o777, 0o600)
})
