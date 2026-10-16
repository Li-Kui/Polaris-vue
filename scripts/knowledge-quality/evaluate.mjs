import {createHash} from 'node:crypto'
import {mkdir, open, readFile, writeFile} from 'node:fs/promises'
import {resolve} from 'node:path'
import {pathToFileURL} from 'node:url'
import {suite as fixedSuite} from './suite.mjs'
import {suite as smokeSuite} from './smoke-suite.mjs'
import {suite as naturalLongSuite} from './natural-long-suite.mjs'

const fail = code => {throw new Error(code)}
const hash = value => createHash('sha256').update(JSON.stringify(value)).digest('hex')
const positiveId = value => typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value)
const normalize = value => value.normalize('NFKC').replace(/\s+/gu, '')
const snapshotFields = ['knowledgeId', 'indexVersion', 'embeddingModelId', 'embeddingModelRevision',
  'embeddingSchemaHash', 'embeddingDimension', 'indexSignature', 'chunkSize', 'chunkOverlap', 'splitterType']

export function validateSuite(suite) {
  if (!suite?.version || !Array.isArray(suite.documents) || !Array.isArray(suite.cases) || !suite.cases.length) fail('INVALID_SUITE')
  const docs = new Map()
  for (const doc of suite.documents) {
    if (!/^[a-z][a-z0-9-]*$/.test(doc.key) || docs.has(doc.key) || typeof doc.content !== 'string'
      || !doc.content.trim() || !/^[a-z0-9-]+\.(md|txt)$/.test(doc.fileName)) fail('INVALID_DOCUMENT')
    docs.set(doc.key, doc)
  }
  const ids = new Set(), queries = new Set()
  for (const item of suite.cases) {
    if (!item.id || ids.has(item.id) || !['dev', 'holdout'].includes(item.split) || !item.category
      || typeof item.query !== 'string' || !item.query.trim() || item.query.length > 2000
      || queries.has(item.query) || !Array.isArray(item.evidence)) fail('INVALID_CASE')
    ids.add(item.id); queries.add(item.query)
    if ((item.category === 'NO_ANSWER') !== (item.evidence.length === 0)) fail('INVALID_LABEL')
    for (const e of item.evidence) {
      if (!docs.has(e.documentKey) || typeof e.text !== 'string' || !e.text.trim()
        || !normalize(docs.get(e.documentKey).content).includes(normalize(e.text))) fail('INVALID_EVIDENCE')
    }
  }
  return {suiteHash: hash(suite), corpusHash: hash(suite.documents)}
}

export function validateManifest(suite, manifest) {
  const hashes = validateSuite(suite)
  if (!manifest || manifest.corpusHash !== hashes.corpusHash || manifest.corpusVerified !== true
    || !positiveId(manifest.knowledgeId) || !positiveId(manifest.indexVersion)
    || !positiveId(manifest.embeddingModelId) || !positiveId(manifest.embeddingModelRevision)
    || !Number.isSafeInteger(manifest.embeddingDimension) || manifest.embeddingDimension < 1
    || !Number.isSafeInteger(manifest.chunkSize) || manifest.chunkSize < 1
    || !Number.isSafeInteger(manifest.chunkOverlap) || manifest.chunkOverlap < 0 || manifest.chunkOverlap >= manifest.chunkSize
    || typeof manifest.embeddingSchemaHash !== 'string' || !manifest.embeddingSchemaHash
    || typeof manifest.indexSignature !== 'string' || !manifest.indexSignature
    || typeof manifest.splitterType !== 'string' || !manifest.splitterType) fail('INVALID_MANIFEST')
  const ids = suite.documents.map(d => manifest.documents?.[d.key])
  if (ids.some(id => !positiveId(id)) || new Set(ids).size !== ids.length
    || Object.keys(manifest.documents).length !== ids.length) fail('INVALID_DOCUMENT_MAPPING')
  return hashes
}

function expectedSnapshot(manifest) {return Object.fromEntries(snapshotFields.map(key => [key, manifest[key] ?? null]))}
export function checkSnapshot(manifest, kb) {
  const current = {...kb, knowledgeId: String(kb.id), indexVersion: String(kb.indexVersion),
    embeddingModelId: String(kb.embeddingModelId), embeddingModelRevision: String(kb.embeddingModelRevision)}
  if (kb.indexStatus !== 'READY' || snapshotFields.some(key => current[key] !== manifest[key])) fail('SNAPSHOT_CHANGED')
}

export function scoreCase(item, response, manifest, minScore, topK = 5) {
  if (![5, 10].includes(topK) || String(response?.knowledgeId) !== manifest.knowledgeId || String(response?.indexVersion) !== manifest.indexVersion
    || response.strategy !== 'VECTOR_V1' || response.topK !== topK || response.minScore !== minScore
    || !['FOUND', 'EMPTY'].includes(response.state) || !Array.isArray(response.results)
    || response.results.length > topK || (response.state === 'EMPTY') !== (response.results.length === 0)
    || !Number.isFinite(response.durationMs) || response.durationMs < 0) fail('INVALID_RESPONSE')
  response.results.forEach((hit, i) => {
    if (hit.rank !== i + 1 || !positiveId(hit.documentId) || typeof hit.content !== 'string' || !hit.content.trim()
      || !Number.isFinite(hit.score) || hit.score < 0 || hit.score > 1) fail('INVALID_HIT')
  })
  const ranks = item.evidence.map(e => {
    const hit = response.results.find(h => h.documentId === manifest.documents[e.documentKey]
      && normalize(h.content).includes(normalize(e.text)))
    return hit?.rank ?? null
  })
  const covered = ranks.filter(rank => rank !== null)
  return {caseId: item.id, category: item.category, status: 'OK', answerable: ranks.length > 0,
    evidenceHit: covered.length > 0, completeEvidence: ranks.length > 0 && covered.length === ranks.length,
    evidenceCoverage: ranks.length ? covered.length / ranks.length : null,
    firstEvidenceRank: covered.length ? Math.min(...covered) : null,
    empty: response.state === 'EMPTY', resultCount: response.results.length, durationMs: response.durationMs}
}

const ratio = (count, total) => total ? count / total : null
function metrics(rows) {
  const ok = rows.filter(row => row.status === 'OK'), positive = ok.filter(row => row.answerable)
  const negative = ok.filter(row => !row.answerable)
  const times = ok.map(row => row.durationMs).sort((a, b) => a - b)
  const percentile = p => times.length ? times[Math.max(0, Math.ceil(times.length * p) - 1)] : null
  return {total: rows.length, completed: ok.length, errors: rows.filter(row => row.status === 'ERROR').length,
    notRun: rows.filter(row => row.status === 'NOT_RUN').length, completionRate: ratio(ok.length, rows.length),
    answerableCompleted: positive.length, noAnswerCompleted: negative.length,
    evidenceHitAt5: ratio(positive.filter(row => row.evidenceHit).length, positive.length),
    completeEvidenceAt5: ratio(positive.filter(row => row.completeEvidence).length, positive.length),
    meanEvidenceCoverage: ratio(positive.reduce((sum, row) => sum + row.evidenceCoverage, 0), positive.length),
    mrrAt5: ratio(positive.reduce((sum, row) => sum + (row.firstEvidenceRank ? 1 / row.firstEvidenceRank : 0), 0), positive.length),
    answerableEmptyRate: ratio(positive.filter(row => row.empty).length, positive.length),
    noAnswerEmptyRate: ratio(negative.filter(row => row.empty).length, negative.length),
    retrievalDurationP50Ms: percentile(0.5), retrievalDurationP95Ms: percentile(0.95)}
}

export function evaluate(suite, manifest, observations, {split = 'dev', minScore = 0.5, mode = 'RECORDED'} = {}) {
  const hashes = validateManifest(suite, manifest)
  if (!['dev', 'holdout'].includes(split) || !Number.isFinite(minScore) || minScore < 0 || minScore > 1
    || !Array.isArray(observations)) fail('INVALID_RUN')
  const selected = suite.cases.filter(item => item.split === split), ids = new Set(selected.map(c => c.id))
  if (!selected.length) fail('EMPTY_SPLIT')
  const seen = new Set()
  for (const observation of observations) {
    if (!ids.has(observation.caseId) || seen.has(observation.caseId)) fail('INVALID_OBSERVATION_SET')
    seen.add(observation.caseId)
  }
  const records = new Map(observations.map(o => [o.caseId, o]))
  const rows = selected.map(item => {
    const record = records.get(item.id)
    if (!record) return {caseId: item.id, category: item.category, status: 'NOT_RUN'}
    if (record.error) return {caseId: item.id, category: item.category, status: 'ERROR', errorCode: 'REQUEST_FAILED'}
    return scoreCase(item, record.response, manifest, minScore)
  })
  const summary = metrics(rows)
  return {reportVersion: 1, generatedAt: new Date().toISOString(), suiteVersion: suite.version, ...hashes, mode, split,
    snapshot: expectedSnapshot(manifest), documentMappingHash: hash(manifest.documents), topK: 5, minScore,
    allCasesCompleted: summary.completed === summary.total, qualityReleaseApproved: false,
    limitations: ['仅字符串支持证据检查，不是语义正确率或生成拒答评测',
      'NO_ANSWER 空召回率不等于拒答率；无答案也可能召回相关但不足以回答的片段',
      '错误与未执行单独计数；指标仅基于完成样本，不能掩盖不完整运行',
      '来源映射和语料一致性依赖人工确认；当前服务没有文档正文修订摘要',
      '不统计提供器 HTTP 次数、Token、费用；P95 为小样本最近秩统计'],
    summary, categories: Object.fromEntries([...new Set(rows.map(row => row.category))]
      .map(category => [category, metrics(rows.filter(row => row.category === category))])), cases: rows}
}

export async function collect(suite, manifest, {base, token, split, minScore, maxCalls, holdoutConfirmed = false},
  {request = fetch, wait = ms => new Promise(resolveWait => setTimeout(resolveWait, ms))} = {}) {
  validateManifest(suite, manifest)
  const url = new URL(base)
  if (!['http:', 'https:'].includes(url.protocol) || !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
    || url.username || url.password || url.pathname !== '/' || url.search || url.hash || !token
    || !['dev', 'holdout'].includes(split) || (split === 'holdout' && !holdoutConfirmed)
    || !Number.isFinite(minScore) || minScore < 0 || minScore > 1) fail('INVALID_COLLECTION_OPTIONS')
  const selected = suite.cases.filter(item => item.split === split)
  if (!Number.isSafeInteger(maxCalls) || maxCalls < 1 || maxCalls > selected.length) fail('INVALID_CALL_BUDGET')
  const observations = []
  const api = async (path, body) => {
    let response, payload
    try {
      response = await request(new URL(path, url), {method: body ? 'POST' : 'GET', redirect: 'error',
        headers: {'Platform-Token': token, ...(body ? {'Content-Type': 'application/json'} : {})},
        ...(body ? {body: JSON.stringify(body)} : {}), signal: AbortSignal.timeout(70000)})
      payload = await response.json()
    } catch {fail('REQUEST_FAILED')}
    if (!response.ok || payload?.code !== 200) fail('REQUEST_FAILED')
    return payload.data
  }
  for (const item of selected.slice(0, maxCalls)) {
    if (observations.length) await wait(5200)
    try {
      checkSnapshot(manifest, await api(`/ai/knowledge/${manifest.knowledgeId}`))
      const response = await api(`/ai/knowledge/${manifest.knowledgeId}/retrieval-test`,
        {query: item.query, topK: 5, minScore, embeddingConfirmed: true})
      checkSnapshot(manifest, await api(`/ai/knowledge/${manifest.knowledgeId}`))
      scoreCase(item, response, manifest, minScore)
      observations.push({caseId: item.id, response})
    } catch {
      // 不复制服务器异常正文、问题或凭证，不重试；首个错误停止余下付费调用。
      observations.push({caseId: item.id, error: true})
      break
    }
  }
  return evaluate(suite, manifest, observations, {split, minScore, mode: 'LIVE_LOCAL'})
}

export function compareReports(before, after) {
  if (!before?.allCasesCompleted || !after?.allCasesCompleted || before.reportVersion !== 1 || after.reportVersion !== 1
    || !['LIVE_LOCAL', 'RECORDED'].includes(before.mode) || before.mode !== after.mode
    || ['suiteHash', 'corpusHash', 'split', 'documentMappingHash', 'topK'].some(key => before[key] !== after[key])
    || hash(before.snapshot) !== hash(after.snapshot)
    || hash(before.cases?.map(row => row.caseId)) !== hash(after.cases?.map(row => row.caseId))) fail('INCOMPARABLE_REPORTS')
  const names = ['evidenceHitAt5', 'completeEvidenceAt5', 'meanEvidenceCoverage', 'mrrAt5',
    'answerableEmptyRate', 'noAnswerEmptyRate', 'retrievalDurationP50Ms', 'retrievalDurationP95Ms']
  return {comparisonVersion: 1, qualityReleaseApproved: false, split: before.split,
    beforeMinScore: before.minScore, afterMinScore: after.minScore,
    delta: Object.fromEntries(names.map(name => [name,
      before.summary[name] == null || after.summary[name] == null ? null : after.summary[name] - before.summary[name]])),
    evidenceRegressions: after.cases.filter(row => {
      const previous = before.cases.find(item => item.caseId === row.caseId)
      return row.answerable && row.evidenceCoverage < previous.evidenceCoverage
    }).map(row => row.caseId)}
}

export async function main(args) {
  const [command, ...pairs] = args, options = {}
  if (pairs.length % 2) fail('INVALID_ARGUMENTS')
  for (let i = 0; i < pairs.length; i += 2) {
    if (!pairs[i].startsWith('--') || options[pairs[i]]) fail('INVALID_ARGUMENTS')
    options[pairs[i]] = pairs[i + 1]
  }
  const allowed = {
    'export-corpus': ['--out', '--suite'],
    evaluate: ['--manifest', '--observations', '--out', '--split', '--min-score', '--suite'],
    compare: ['--before', '--after', '--out'],
    collect: ['--manifest', '--out', '--split', '--min-score', '--base', '--max-calls', '--confirm-embedding', '--confirm-holdout', '--suite']
  }
  if (!allowed[command] || Object.keys(options).some(key => !allowed[command].includes(key)) || !options['--out']) fail('INVALID_ARGUMENTS')
  validateSuite(fixedSuite)
  if (options['--suite'] && !['fixed', 'smoke', 'natural-long'].includes(options['--suite'])) fail('INVALID_SUITE_SELECTION')
  const selectedSuite = options['--suite'] === 'natural-long' ? naturalLongSuite : options['--suite'] === 'smoke' ? smokeSuite : fixedSuite
  if (command === 'export-corpus') {
    const out = resolve(options['--out'])
    await mkdir(out, {mode: 0o700}) // 已存在即拒绝；不覆盖已有资料。
    for (const doc of selectedSuite.documents) await writeFile(resolve(out, doc.fileName), doc.content, {flag: 'wx', mode: 0o600})
    await writeFile(resolve(out, 'manifest-template.json'), JSON.stringify({
      ...expectedSnapshot({}), corpusHash: validateSuite(selectedSuite).corpusHash, corpusVerified: false,
      documents: Object.fromEntries(selectedSuite.documents.map(doc => [doc.key, '填写实际文档ID']))
    }, null, 2), {flag: 'wx', mode: 0o600})
    return {exportedDocuments: selectedSuite.documents.length, uploaded: false}
  }
  if (command === 'compare') {
    const comparison = compareReports(JSON.parse(await readFile(options['--before'], 'utf8')),
      JSON.parse(await readFile(options['--after'], 'utf8')))
    await writeFile(resolve(options['--out']), JSON.stringify(comparison, null, 2), {flag: 'wx', mode: 0o600})
    return comparison
  }
  const manifest = JSON.parse(await readFile(options['--manifest'], 'utf8'))
  validateManifest(selectedSuite, manifest)
  const config = {split: options['--split'] ?? 'dev', minScore: Number(options['--min-score'] ?? 0.5)}
  let report
  if (command === 'evaluate') report = evaluate(selectedSuite, manifest,
    JSON.parse(await readFile(options['--observations'], 'utf8')), config)
  else {
    if (options['--confirm-embedding'] !== 'yes') fail('EMBEDDING_CONFIRMATION_REQUIRED')
    // 先独占预留输出文件，已有报告或不可写目录不会在付费调用之后才被发现。
    const output = await open(resolve(options['--out']), 'wx', 0o600)
    try {
      report = await collect(selectedSuite, manifest, {...config, base: options['--base'] ?? 'http://localhost:8081',
        token: process.env.POLARIS_QA_TOKEN, maxCalls: Number(options['--max-calls']),
        holdoutConfirmed: options['--confirm-holdout'] === 'yes'})
      await output.writeFile(JSON.stringify(report, null, 2))
    } finally {await output.close()}
    return {mode: report.mode, split: report.split, allCasesCompleted: report.allCasesCompleted, summary: report.summary}
  }
  await writeFile(resolve(options['--out']), JSON.stringify(report, null, 2), {flag: 'wx', mode: 0o600})
  return {mode: report.mode, split: report.split, allCasesCompleted: report.allCasesCompleted, summary: report.summary}
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main(process.argv.slice(2)).then(result => {
    console.log(JSON.stringify(result))
    if (result.allCasesCompleted === false) process.exitCode = 2
  }).catch(() => {
    console.error('评测未完成：请检查参数、清单及本地服务；未输出原始错误或凭证。')
    process.exitCode = 1
  })
}
