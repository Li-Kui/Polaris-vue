import {createHash, randomUUID} from 'node:crypto'
import {open, readFile} from 'node:fs/promises'
import {resolve} from 'node:path'
import {pathToFileURL} from 'node:url'
import {suite} from './suite.mjs'
import {checkSnapshot, scoreCase, validateManifest} from './evaluate.mjs'

const fail=code=>{throw new Error(code)}
const hash=value=>createHash('sha256').update(JSON.stringify(value)).digest('hex')
const normalize=text=>text.normalize('NFKC').replace(/\s+/gu,'')
const dev=()=>suite.cases.filter(c=>c.split==='dev')
const fingerprint=hits=>hash(hits.map(h=>({documentId:h.documentId,chunkNo:h.chunkNo??null,content:h.content})))
export const retrievalControlOrder=()=>dev().flatMap((item,i)=>(i%2?[10,5]:[5,10]).map(topK=>({caseId:item.id,topK})))

export function scoreRetrievalControl(item,response,manifest,topK) {
  const score=scoreCase(item,response,manifest,0.5,topK)
  const known=new Set(Object.values(manifest.documents))
  if(response.results.some(h=>!known.has(h.documentId)||h.content.length>4000))fail('UNEXPECTED_CORPUS_HIT')
  const supporting=response.results.filter(h=>item.evidence.some(e=>h.documentId===manifest.documents[e.documentKey]&&normalize(h.content).includes(normalize(e.text)))).length
  const ranks=item.evidence.map(e=>response.results.find(h=>h.documentId===manifest.documents[e.documentKey]&&normalize(h.content).includes(normalize(e.text)))?.rank??null)
  return {...score,topK,supportRanks:ranks,annotationSupportingHits:item.evidence.length?supporting:null,
    unlabeledCandidateCount:item.evidence.length?response.results.length-supporting:null,
    contentCharacters:response.results.reduce((total,h)=>total+h.content.length,0),
    resultFingerprint:fingerprint(response.results),first5Fingerprint:fingerprint(response.results.slice(0,5))}
}

const ratio=(a,b)=>b?a/b:null
function metrics(rows) {
  const ok=rows.filter(r=>r.status==='OK'),positive=ok.filter(r=>r.answerable),negative=ok.filter(r=>!r.answerable)
  const times=ok.map(r=>r.durationMs).sort((a,b)=>a-b),percentile=p=>times.length?times[Math.ceil(times.length*p)-1]:null
  return {total:rows.length,completed:ok.length,errors:rows.filter(r=>r.status==='ERROR').length,notRun:rows.filter(r=>r.status==='NOT_RUN').length,
    answerableCompleted:positive.length,noAnswerCompleted:negative.length,
    evidenceHitRate:ratio(positive.filter(r=>r.evidenceHit).length,positive.length),completeEvidenceRate:ratio(positive.filter(r=>r.completeEvidence).length,positive.length),
    meanEvidenceCoverage:ratio(positive.reduce((n,r)=>n+r.evidenceCoverage,0),positive.length),mrr:ratio(positive.reduce((n,r)=>n+(r.firstEvidenceRank?1/r.firstEvidenceRank:0),0),positive.length),
    answerableEmptyRate:ratio(positive.filter(r=>r.empty).length,positive.length),noAnswerEmptyRate:ratio(negative.filter(r=>r.empty).length,negative.length),
    meanResultCount:ratio(ok.reduce((n,r)=>n+r.resultCount,0),ok.length),meanContentCharacters:ratio(ok.reduce((n,r)=>n+r.contentCharacters,0),ok.length),
    meanUnlabeledCandidatesOnAnswerable:ratio(positive.reduce((n,r)=>n+r.unlabeledCandidateCount,0),positive.length),
    durationP50Ms:percentile(0.5),durationP95Ms:percentile(0.95)}
}
export function retrievalControlReport(manifest,observations,{runId,mode='RECORDED',modelViewHash=null}={}) {
  const hashes=validateManifest(suite,manifest)
  if(!/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(runId??'')||!['RECORDED','LIVE_LOCAL','SCORER_FIXTURE'].includes(mode)||!Array.isArray(observations)
      ||(modelViewHash!==null&&!/^[a-f0-9]{64}$/.test(modelViewHash)))fail('INVALID_RUN')
  const selected=dev(),records=new Map()
  for(const row of observations){const key=`${row.caseId}:${row.topK}`;if(!selected.some(c=>c.id===row.caseId)||![5,10].includes(row.topK)||records.has(key))fail('INVALID_CONTROL_OBSERVATION');records.set(key,row)}
  const cases=selected.map(item=>{
    const side=topK=>{const row=records.get(`${item.id}:${topK}`);return !row?{caseId:item.id,topK,status:'NOT_RUN'}:row.error?{caseId:item.id,topK,status:'ERROR'}:scoreRetrievalControl(item,row.response,manifest,topK)}
    const top5=side(5),top10=side(10),paired=top5.status==='OK'&&top10.status==='OK'
    return {caseId:item.id,category:item.category,top5,top10,prefixSame:paired?top5.first5Fingerprint===top10.first5Fingerprint:null}
  })
  const top5=metrics(cases.map(c=>c.top5)),top10=metrics(cases.map(c=>c.top10)),complete=top5.completed===selected.length&&top10.completed===selected.length
  const paired=cases.filter(c=>c.top5.status==='OK'&&c.top10.status==='OK')
  return {reportVersion:1,runId,mode,...hashes,manifestHash:hash(manifest),modelViewHash,split:'dev',holdoutExecuted:false,activeTopKExpected:5,minScore:0.5,
    requestOrder:'ALTERNATING_PER_CASE_5_10_THEN_10_5',allCasesCompleted:complete,qualityReleaseApproved:false,causalImprovementProven:false,
    summary:{selectedCases:selected.length,expectedDiagnosticRequests:selected.length*2,completed:top5.completed+top10.completed,errors:top5.errors+top10.errors,notRun:top5.notRun+top10.notRun,
      pairedCases:paired.length,prefixDifferentCases:paired.filter(c=>!c.prefixSame).map(c=>c.caseId)},top5,top10,
    delta:complete?Object.fromEntries(['evidenceHitRate','completeEvidenceRate','meanEvidenceCoverage','mrr','meanResultCount','meanContentCharacters','meanUnlabeledCandidatesOnAnswerable','durationP50Ms','durationP95Ms'].map(key=>[key,top10[key]-top5[key]])):null,
    improvedEvidenceCases:paired.filter(c=>c.top10.evidenceCoverage!==null&&c.top10.evidenceCoverage>c.top5.evidenceCoverage).map(c=>c.caseId),
    evidenceRegressions:paired.filter(c=>c.top10.evidenceCoverage!==null&&c.top10.evidenceCoverage<c.top5.evidenceCoverage).map(c=>c.caseId),
    categories:Object.fromEntries([...new Set(cases.map(c=>c.category))].map(category=>[category,{top5:metrics(cases.filter(c=>c.category===category).map(c=>c.top5)),top10:metrics(cases.filter(c=>c.category===category).map(c=>c.top10))}])),
    limitations:['40条开发题的真实双请求，不从Top10前缀假装Top5请求；不运行20条保留题、不生成回答',
      '仅文档ID与完整标注字符串覆盖；未命中标注的候选不等于无关噪声，非空不等于应回答',
      '每参数每题一次、确定性交替顺序，非随机重复试验，不能证明普遍因果收益或精确费用',
      '字符数是返回候选的UTF-16长度，不是最终上下文长度或Token；诊断延迟不是回答延迟',
      '语料真实性依赖操作人确认和另行物理对账，工具不上传、不保存参数、不重建、不改授权'],cases}
}

export async function collectRetrievalControl(manifest,options,{request=fetch,wait=ms=>new Promise(done=>setTimeout(done,ms)),checkpoint=async()=>{}}={}) {
  validateManifest(suite,manifest)
  const url=new URL(options.base??'http://localhost:8081'),order=retrievalControlOrder()
  if(!['http:','https:'].includes(url.protocol)||!['localhost','127.0.0.1','[::1]'].includes(url.hostname)||url.username||url.password||url.pathname!=='/'||url.search||url.hash
      ||!options.token||options.confirmEmbedding!==true||!Number.isSafeInteger(options.maxCalls)||options.maxCalls<1||options.maxCalls>order.length)fail('INVALID_COLLECTION_OPTIONS')
  const observations=[],runId=randomUUID();let modelViewHash,initialKbHash
  const api=async(path,body)=>{const response=await request(new URL(path,url),{method:body?'POST':'GET',redirect:'error',headers:{'Platform-Token':options.token,...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(70000)});const payload=await response.json();if(!response.ok||payload?.code!==200)fail('REQUEST_FAILED');return payload.data}
  const check=async()=>{
    const kb=await api(`/ai/knowledge/${manifest.knowledgeId}`);checkSnapshot(manifest,kb)
    if(!kb?.name?.startsWith('QA')||kb.retrievalTopK!==5||kb.retrievalMinScore!==0.5)fail('QA_CONFIGURATION_CHANGED')
    const kbHash=hash(kb);if(initialKbHash&&initialKbHash!==kbHash)fail('QA_CONFIGURATION_CHANGED');initialKbHash=kbHash
    const model=await api(`/ai/model-center/models/${manifest.embeddingModelId}`)
    if(String(model?.id)!==manifest.embeddingModelId||String(model?.revision)!==manifest.embeddingModelRevision)fail('MODEL_CHANGED')
    const currentModelHash=hash(model);if(modelViewHash&&modelViewHash!==currentModelHash)fail('MODEL_CHANGED');modelViewHash=currentModelHash
    const result=await api(`/ai/knowledge/document/list?knowledgeBaseId=${manifest.knowledgeId}&pageNum=1&pageSize=100`),docs=result?.rows??result?.records
    if(!Array.isArray(docs)||docs.length!==suite.documents.length||suite.documents.some(d=>!docs.some(x=>String(x.id)===manifest.documents[d.key]&&x.name===d.fileName&&x.status==='2'&&x.moderationStatus==='SAFE')))fail('CORPUS_CHANGED')
  }
  await check()
  for(const [i,step] of order.slice(0,options.maxCalls).entries()){
    if(i)await wait(5200)
    try{
      await check();await checkpoint({runId,...step,phase:'BEFORE_DISPATCH',dispatchOrdinal:i+1,completedRequests:observations.length})
      const item=dev().find(c=>c.id===step.caseId)
      const response=await api(`/ai/knowledge/${manifest.knowledgeId}/retrieval-test`,{query:item.query,topK:step.topK,minScore:0.5,embeddingConfirmed:true})
      await check();scoreRetrievalControl(item,response,manifest,step.topK);observations.push({...step,response})
    }catch{
      observations.push({...step,error:true});await checkpoint({runId,...step,phase:'ERROR',dispatchOrdinal:i+1,completedRequests:observations.length-1});break
    }
    await checkpoint({runId,...step,phase:'COMPLETED',dispatchOrdinal:i+1,completedRequests:observations.length})
  }
  return retrievalControlReport(manifest,observations,{runId,mode:'LIVE_LOCAL',modelViewHash})
}

export async function main(args) {
  const allowed=['--manifest','--out','--max-calls','--confirm-embedding','--base'],options={}
  if(args.length%2)fail('INVALID_ARGUMENTS')
  for(let i=0;i<args.length;i+=2){if(!allowed.includes(args[i])||options[args[i]]||!args[i+1])fail('INVALID_ARGUMENTS');options[args[i]]=args[i+1]}
  if(!options['--out']||!options['--manifest']||options['--confirm-embedding']!=='yes')fail('EXPLICIT_EMBEDDING_CONFIRMATION_REQUIRED')
  const manifest=JSON.parse(await readFile(options['--manifest'],'utf8'));validateManifest(suite,manifest)
  const output=await open(resolve(options['--out']),'wx',0o600)
  try{
    const report=await collectRetrievalControl(manifest,{base:options['--base'],token:process.env.POLARIS_QA_TOKEN,maxCalls:Number(options['--max-calls']),confirmEmbedding:true},{checkpoint:async progress=>{
      await output.truncate(0);await output.write(JSON.stringify({inProgress:true,...progress}),0,'utf8');await output.sync()
    }})
    await output.truncate(0);await output.write(JSON.stringify(report,null,2),0,'utf8')
    return {runId:report.runId,allCasesCompleted:report.allCasesCompleted,summary:report.summary,top5:report.top5,top10:report.top10,qualityReleaseApproved:false}
  }finally{await output.close()}
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href)main(process.argv.slice(2)).then(result=>{console.log(JSON.stringify(result));if(!result.allCasesCompleted)process.exitCode=2}).catch(()=>{console.error('检索对照未完成；请检查清单、本机服务、预算和独立输出文件，未输出原始错误或凭证。');process.exitCode=1})
