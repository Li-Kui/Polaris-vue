import {createHash, randomUUID} from 'node:crypto'
import {open, readFile} from 'node:fs/promises'
import {resolve} from 'node:path'
import {pathToFileURL} from 'node:url'
import {suite} from './suite.mjs'
import {validateManifest, validateSuite} from './evaluate.mjs'

// 固定开发集子集：三条单库正例、两条关联题、七条无答案题；不改题、不访问保留集。
export const pilotIds = ['direct-01', 'direct-03', 'table_cross-01', 'table_cross-06', 'table_cross-07',
  ...Array.from({length: 7}, (_, i) => `no_answer-${String(i + 1).padStart(2, '0')}`)]
const fail = code => {throw new Error(code)}
export const generationHash = value => createHash('sha256').update(JSON.stringify(value)).digest('hex')
const positiveId = value => typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value)
const normalized = value => value.normalize('NFKC').replace(/\s+/gu, '')
const cases = () => pilotIds.map(id => suite.cases.find(c => c.id === id && c.split === 'dev'))
const snapshotKeys = ['indexVersion', 'embeddingModelId', 'embeddingModelRevision', 'embeddingSchemaHash',
  'embeddingDimension', 'indexSignature', 'chunkSize', 'chunkOverlap', 'splitterType']

export function validateGenerationManifest(manifest) {
  validateManifest(suite, manifest)
  if (!positiveId(manifest.answerModelConfigId) || !positiveId(manifest.answerModelRevision)
      || !/^[a-f0-9]{64}$/.test(manifest.answerModelAggregateHash ?? '')) fail('INVALID_ANSWER_MODEL_SNAPSHOT')
  if (manifest.retrievalTopK!==undefined && ![5,10].includes(manifest.retrievalTopK)) fail('INVALID_TOP_K_CONTROL')
}

export function evidenceCoverage(item, previews, manifest) {
  if (!Array.isArray(previews) || previews.length > 50) fail('INVALID_PREVIEWS')
  for (const p of previews) {
    if (!positiveId(p.documentId) || !Object.values(manifest.documents).includes(p.documentId)
        || typeof p.text !== 'string' || !p.text.trim() || p.text.length > 4000) fail('INVALID_PREVIEW')
  }
  const covered = item.evidence.filter(e => previews.some(p => p.documentId === manifest.documents[e.documentKey]
    && normalized(p.text).includes(normalized(e.text)))).length
  return {expectedEvidenceCount: item.evidence.length, coveredEvidenceCount: covered,
    completeEvidence: item.evidence.length > 0 && covered === item.evidence.length,
    evidenceCoverage: item.evidence.length ? covered / item.evidence.length : null}
}

export function generationReport(manifest, observations, {runId, label = 'BASELINE', mode = 'RECORDED'} = {}) {
  validateGenerationManifest(manifest)
  if (!/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(runId ?? '') || typeof label !== 'string' || !/^[A-Z0-9_-]{1,48}$/.test(label)
      || !['RECORDED','LIVE_LOCAL','SCORER_FIXTURE'].includes(mode) || !Array.isArray(observations)) fail('INVALID_RUN')
  const seen = new Set()
  for (const row of observations) {
    if (!pilotIds.includes(row.caseId) || seen.has(row.caseId) || !['COMPLETED', 'STOPPED_EMPTY', 'ERROR'].includes(row.status)) fail('INVALID_OBSERVATION')
    seen.add(row.caseId)
  }
  const records = new Map(observations.map(row => [row.caseId, row]))
  const rows = cases().map(item => {
    const row = records.get(item.id)
    if (!row) return {caseId: item.id, category: item.category, status: 'NOT_RUN'}
    if (row.status === 'ERROR') return {caseId: item.id, category: item.category, status: 'ERROR'}
    if (!positiveId(row.conversationId) || typeof row.answer !== 'string') fail('INVALID_ANSWER')
    const coverage = evidenceCoverage(item, row.previews, manifest)
    if (row.status === 'STOPPED_EMPTY' && (row.answer || row.previews.length)) fail('INVALID_EMPTY_STOP')
    if (row.status === 'COMPLETED' && (!row.answer.trim() || row.answer.length > 200000 || !positiveId(row.messageId))) fail('INVALID_ANSWER')
    return {caseId: item.id, category: item.category, status: row.status, conversationId: row.conversationId,
      messageId: row.messageId ?? null, answerHash: generationHash(row.answer), previewHash: generationHash(row.previews),
      sourceCount: row.previews.length, citationState: ['IDS_VALID','INVALID_IDS','MISSING_IDS','UNVERIFIED'].includes(row.citationState) ? row.citationState : null, ...coverage}
  })
  return {reportVersion: 1, runId, label, mode, suiteHash: validateSuite(suite).suiteHash, corpusHash: manifest.corpusHash,
    manifestHash: generationHash(manifest), ...(manifest.retrievalTopK===undefined?{}:{retrievalTopK:manifest.retrievalTopK}),
    split: 'dev', selectedCaseIds: pilotIds, totalDevCases: 40, notSelectedDevCases: 28,
    holdoutExecuted: false, observationsHash: generationHash(observations), qualityReleaseApproved: false,
    allSelectedCompleted: rows.every(r => ['COMPLETED','STOPPED_EMPTY'].includes(r.status)),
    summary: {selected: rows.length, completed: rows.filter(r => r.status === 'COMPLETED').length,
      stoppedEmpty: rows.filter(r => r.status === 'STOPPED_EMPTY').length, errors: rows.filter(r => r.status === 'ERROR').length,
      notRun: rows.filter(r => r.status === 'NOT_RUN').length,
      noAnswerNonempty: rows.filter(r => r.category === 'NO_ANSWER' && r.sourceCount > 0).length},
    limitations: ['证据覆盖只按文档ID与完整标注字符串检查，不是语义支持度',
      '不按拒答关键词或引用编号自动判正确；回答需另行审阅，AGENT审阅不是人工质量放行',
      '固定12条开发集试验，不是完整40条或20条保留集；新模型/新库不是旧检索基线的直接收益对照',
      '请求预算不等于供应商HTTP次数或费用；模型修订/公开配置摘要不能冻结供应商模型权重及所有业务策略'], cases: rows}
}

export function reviewGeneration(report, manifest, packet, decisions) {
  validateGenerationManifest(manifest)
  if (packet.packetVersion !== 1 || packet.syntheticOnly !== true || report.reportVersion !== 1 || report.manifestHash !== generationHash(manifest)
      || report.observationsHash !== generationHash(packet.observations) || packet.runId !== report.runId
      || generationHash(generationReport(manifest,packet.observations,{runId:report.runId,label:report.label,mode:report.mode}))!==generationHash(report)
      || decisions.runId !== report.runId || !['HUMAN','AGENT'].includes(decisions.reviewerType) || !Array.isArray(decisions.cases)) fail('REVIEW_BINDING_MISMATCH')
  const labels = ['SUPPORTED_ANSWER','PARTIAL_ANSWER','CORRECT_REFUSAL','WRONG_REFUSAL','UNSUPPORTED_ANSWER','UNCLEAR']
  const seen = new Set(), reviewed = []
  for (const decision of decisions.cases) {
    const row = report.cases.find(r => r.caseId === decision.caseId)
    if (!row || !['COMPLETED','STOPPED_EMPTY'].includes(row.status) || seen.has(row.caseId) || !labels.includes(decision.label)
        || decision.answerHash !== row.answerHash || decision.previewHash !== row.previewHash
        || typeof decision.rationale !== 'string' || !decision.rationale.trim() || decision.rationale.length > 2000) fail('INVALID_REVIEW')
    if ((['SUPPORTED_ANSWER','WRONG_REFUSAL'].includes(decision.label) && !row.completeEvidence)
        || (decision.label === 'PARTIAL_ANSWER' && row.coveredEvidenceCount === 0)
        || (decision.label === 'CORRECT_REFUSAL' && row.completeEvidence)) fail('REVIEW_CONTRADICTS_EVIDENCE')
    seen.add(row.caseId); reviewed.push({caseId:row.caseId,category:row.category,label:decision.label,rationaleHash:generationHash(decision.rationale)})
  }
  return {reviewVersion:1,runId:report.runId,reviewerType:decisions.reviewerType,reviewed:reviewed.length,
    unreviewed:report.cases.filter(r=>['COMPLETED','STOPPED_EMPTY'].includes(r.status)).length-reviewed.length,
    counts:Object.fromEntries(labels.map(label=>[label,reviewed.filter(r=>r.label===label).length])),qualityReleaseApproved:false,cases:reviewed}
}

export async function collectGeneration(manifest, options, {request = fetch, wait = ms => new Promise(done => setTimeout(done, ms)), checkpoint = async () => {}} = {}) {
  validateGenerationManifest(manifest)
  const url = new URL(options.base ?? 'http://localhost:8081')
  if (!['http:','https:'].includes(url.protocol) || !['localhost','127.0.0.1','[::1]'].includes(url.hostname)
      || url.username || url.password || url.pathname !== '/' || url.search || url.hash || !options.token
      || options.confirmModelCalls !== true || !Number.isSafeInteger(options.maxCalls) || options.maxCalls < 1 || options.maxCalls > 12
      || (options.label!==undefined && !/^[A-Z0-9_-]{1,48}$/.test(options.label))) fail('INVALID_COLLECTION_OPTIONS')
  const observations = [], runId = randomUUID(), knownDocs = new Set(Object.values(manifest.documents))
  const api = async (path, body, method = body ? 'POST' : 'GET') => {
    const response = await request(new URL(path,url),{method,redirect:'error',headers:{'Platform-Token':options.token,...(body?{'Content-Type':'application/json'}:{})},
      ...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(90000)})
    if (!response.ok) fail('REQUEST_FAILED')
    const result = await response.json();if(result.code!==200)fail('REQUEST_FAILED');return result.data
  }
  const check = async () => {
    const kb = await api(`/ai/knowledge/${manifest.knowledgeId}`)
    if (!kb?.name?.startsWith('QA') || String(kb.id)!==manifest.knowledgeId || kb.indexStatus!=='READY'
        || snapshotKeys.some(key => typeof manifest[key]==='string'?String(kb[key])!==manifest[key]:kb[key]!==manifest[key])
        || kb.retrievalTopK!==(manifest.retrievalTopK??5) || kb.retrievalMinScore!==0.5) fail('SNAPSHOT_CHANGED')
    const model=await api(`/ai/model-center/models/${manifest.answerModelConfigId}`)
    if(String(model.id)!==manifest.answerModelConfigId || String(model.revision)!==manifest.answerModelRevision || generationHash(model)!==manifest.answerModelAggregateHash)fail('MODEL_CHANGED')
    const response=await api(`/ai/knowledge/document/list?knowledgeBaseId=${manifest.knowledgeId}&pageNum=1&pageSize=100`)
    const docs=response?.rows??response?.records
    if(!Array.isArray(docs) || docs.length!==suite.documents.length || suite.documents.some(doc=>!docs.some(d=>String(d.id)===manifest.documents[doc.key] && d.name===doc.fileName && d.status==='2' && d.moderationStatus==='SAFE')))fail('CORPUS_CHANGED')
  }
  await check()
  const selected=cases().slice(0,options.maxCalls)
  for (const item of selected) {
    if (observations.length) await wait(5200)
    let conversationId
    try {
      await check()
      const conv=await api(`/ai/chat/conversations?modelConfigId=${manifest.answerModelConfigId}&knowledgeBaseId=${manifest.knowledgeId}`,null,'POST')
      conversationId=String(conv.id)
      if (!positiveId(conversationId) || String(conv.knowledgeBaseId)!==manifest.knowledgeId || String(conv.modelConfigId)!==manifest.answerModelConfigId)fail('INVALID_CONVERSATION')
      const initial=await api(`/ai/chat/conversations/${conversationId}/messages`);if(!Array.isArray(initial)||initial.length)fail('CONVERSATION_NOT_EMPTY')
      await api(`/ai/chat/conversations/${conversationId}/title?title=${encodeURIComponent(`QA B34 ${(options.label??'BASELINE').slice(0,12)} ${item.id} ${runId.slice(0,8)}`)}`,null,'PUT')
      await checkpoint({runId,caseId:item.id,conversationId,phase:'BEFORE_DISPATCH'})
      const response=await request(new URL('/ai/chat/stream',url),{method:'POST',redirect:'error',headers:{'Platform-Token':options.token,'Content-Type':'application/json'},
        body:JSON.stringify({conversationId:Number(conversationId),message:item.query,attachmentTokens:[],agentCode:null,enableSearch:false}),signal:AbortSignal.timeout(90000)})
      if(!response.ok)fail('STREAM_FAILED')
      const reader=response.body.getReader(),decoder=new TextDecoder();let raw='',bytes=0
      try {while(true){const {value,done}=await reader.read();if(done)break;bytes+=value.byteLength;if(bytes>2*1024*1024)fail('STREAM_LIMIT');raw+=decoder.decode(value,{stream:true})}raw+=decoder.decode()}finally{await reader.cancel()}
      const events=raw.split(/\r?\n\r?\n/).filter(Boolean).map(block=>({event:block.match(/^event:\s*(.+)$/m)?.[1].trim(),data:[...block.matchAll(/^data:\s?(.*)$/gm)].map(m=>m[1]).join('\n')}))
      const empty=events.some(e=>e.event==='knowledge_retrieval' && JSON.parse(e.data).state==='EMPTY' && JSON.parse(e.data).answerStopped===true)
      const history=await api(`/ai/chat/conversations/${conversationId}/messages`)
      if (empty) {
        if(history.length || !events.some(e=>e.event==='error') || events.some(e=>['message','done','doc_recognized'].includes(e.event)))fail('INVALID_EMPTY_STOP')
        observations.push({caseId:item.id,status:'STOPPED_EMPTY',conversationId,answer:'',previews:[]})
      } else {
        if(events.some(e=>e.event==='error') || !events.some(e=>e.event==='done') || history.length!==2)fail('STREAM_FAILED')
        const user=history.find(m=>m.role==='user'),answer=history.find(m=>m.role==='assistant')
        if(user?.content!==item.query || answer?.moderationStatus!=='SAFE' || !Array.isArray(answer.attachedDocs))fail('INVALID_HISTORY')
        const previews=[],ids=new Set()
        for(const doc of answer.attachedDocs) {
          if(!knownDocs.has(String(doc.docId)) || String(doc.knowledgeBaseId)!==manifest.knowledgeId || !Array.isArray(doc.citationIds))fail('INVALID_SOURCE')
          for(const id of doc.citationIds) {
            if(ids.size>=50 || !/^S([1-9]|[1-4][0-9]|50)$/.test(id) || ids.has(id))fail('INVALID_CITATION');ids.add(id)
            const p=await api(`/ai/chat/conversations/${conversationId}/messages/${answer.id}/citations/${id}`)
            if(p.citationId!==id || String(p.documentId)!==String(doc.docId) || String(p.indexVersion)!==manifest.indexVersion)fail('SOURCE_CHANGED')
            previews.push({citationId:id,documentId:String(p.documentId),text:p.text,truncated:p.truncated,locations:p.locations})
          }
        }
        observations.push({caseId:item.id,status:'COMPLETED',conversationId,messageId:String(answer.id),answer:answer.content,citationState:answer.citationValidation?.state,previews})
      }
      await check();await checkpoint({runId,caseId:item.id,conversationId,phase:'COMPLETED'})
    } catch {
      // 错误不冒充拒答；不拷贝异常、令牌或服务端原始错误，不重试，首错停止后续调用。
      const index=observations.findIndex(row=>row.caseId===item.id);if(index>=0)observations.splice(index,1)
      observations.push({caseId:item.id,status:'ERROR'});await checkpoint({runId,caseId:item.id,conversationId,phase:'ERROR'});break
    }
  }
  const report=generationReport(manifest,observations,{runId,label:options.label??'BASELINE',mode:'LIVE_LOCAL'})
  return {report,packet:{packetVersion:1,runId,syntheticOnly:true,observations}}
}

export async function main(args) {
  const [command,...pairs]=args,options={}
  if(pairs.length%2)fail('INVALID_ARGUMENTS')
  for(let i=0;i<pairs.length;i+=2){if(!pairs[i].startsWith('--')||options[pairs[i]])fail('INVALID_ARGUMENTS');options[pairs[i]]=pairs[i+1]}
  const allowed={collect:['--manifest','--out','--review-packet','--max-calls','--confirm-model-calls','--base','--label'],review:['--manifest','--report','--review-packet','--decisions','--out']}
  if(!allowed[command] || Object.keys(options).some(key=>!allowed[command].includes(key)) || !options['--out'] || !options['--manifest'] || !options['--review-packet'])fail('INVALID_ARGUMENTS')
  const manifest=JSON.parse(await readFile(options['--manifest'],'utf8'));validateGenerationManifest(manifest)
  if(command==='review') {
    const result=reviewGeneration(JSON.parse(await readFile(options['--report'],'utf8')),manifest,JSON.parse(await readFile(options['--review-packet'],'utf8')),JSON.parse(await readFile(options['--decisions'],'utf8')))
    const output=await open(resolve(options['--out']),'wx',0o600);try{await output.writeFile(JSON.stringify(result,null,2))}finally{await output.close()}return result
  }
  if(options['--confirm-model-calls']!=='yes')fail('MODEL_CALL_CONFIRMATION_REQUIRED')
  const output=await open(resolve(options['--out']),'wx',0o600)
  try {
    const packet=await open(resolve(options['--review-packet']),'wx',0o600)
    try {
      const result=await collectGeneration(manifest,{base:options['--base'],token:process.env.POLARIS_QA_TOKEN,maxCalls:Number(options['--max-calls']),confirmModelCalls:true,label:options['--label']},
        {checkpoint:async progress=>{await output.truncate(0);await output.write(JSON.stringify({inProgress:true,...progress}),0,'utf8');await output.sync()}})
      await output.truncate(0);await output.write(JSON.stringify(result.report,null,2),0,'utf8');await packet.writeFile(JSON.stringify(result.packet,null,2))
      return {runId:result.report.runId,allSelectedCompleted:result.report.allSelectedCompleted,summary:result.report.summary,qualityReleaseApproved:false}
    } finally {await packet.close()}
  } finally {await output.close()}
}
if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href)main(process.argv.slice(2)).then(result=>{
  console.log(JSON.stringify(result));if(result.allSelectedCompleted===false)process.exitCode=2
}).catch(()=>{console.error('生成评测未完成；请检查本地服务、清单、预算及私有输出文件，未输出原始错误或凭证。');process.exitCode=1})
