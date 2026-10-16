import assert from 'node:assert/strict'
import test from 'node:test'
import {mkdtemp, readFile, stat, writeFile} from 'node:fs/promises'
import {join} from 'node:path'
import {tmpdir} from 'node:os'
import {suite} from './suite.mjs'
import {scoreCase, validateSuite} from './evaluate.mjs'
import {
    collectRetrievalControl,
    main,
    retrievalControlOrder,
    retrievalControlReport,
    scoreRetrievalControl
} from './retrieval-control.mjs'

const manifest={knowledgeId:'11',indexVersion:'1',embeddingModelId:'10',embeddingModelRevision:'1',embeddingSchemaHash:'schema',embeddingDimension:2,indexSignature:'index',chunkSize:300,chunkOverlap:30,splitterType:'RECURSIVE',corpusVerified:true,corpusHash:validateSuite(suite).corpusHash,documents:Object.fromEntries(suite.documents.map((d,i)=>[d.key,String(39+i)]))}
const runId='11111111-2222-3333-4444-555555555555',item=id=>suite.cases.find(c=>c.id===id)
const response=(topK=5)=>({knowledgeId:11,indexVersion:1,strategy:'VECTOR_V1',topK,minScore:0.5,state:'FOUND',durationMs:20,
  results:suite.documents.slice(0,topK).map((d,i)=>({rank:i+1,documentId:manifest.documents[d.key],chunkNo:'0',score:0.9-i/100,content:d.content}))})
const observations=()=>retrievalControlOrder().map(step=>({...step,response:response(step.topK)}))
test('固定40开发题各两次，共80请求；先后顺序各半，不混入保留集',()=>{
  const order=retrievalControlOrder();assert.equal(order.length,80)
  assert.ok(order.every(row=>item(row.caseId)?.split==='dev'))
  assert.deepEqual(order.slice(0,4).map(r=>r.topK),[5,10,10,5])
  assert.equal(order.filter((r,i)=>i%2===0&&r.topK===5).length,20)
  assert.equal(new Set(order.map(r=>`${r.caseId}:${r.topK}`)).size,80)
})
test('旧Top5计分默认不变，Top10须显式指定，仍拒绝20和错误结构',()=>{
  assert.equal(scoreCase(item('direct-01'),response(),manifest,0.5).completeEvidence,true)
  assert.throws(()=>scoreCase(item('direct-01'),response(10),manifest,0.5))
  assert.equal(scoreCase(item('direct-01'),response(10),manifest,0.5,10).completeEvidence,true)
  assert.throws(()=>scoreCase(item('direct-01'),{...response(10),topK:20},manifest,0.5,20))
  for(const bad of [{results:response(10).results.concat(response().results)},{state:'EMPTY'},{durationMs:-1},{indexVersion:2},{minScore:0.3}])assert.throws(()=>scoreRetrievalControl(item('direct-01'),{...response(10),...bad},manifest,10))
})
test('完整证据按实际文档和完整字符串；标注外候选单列且不冒充语义噪声',()=>{
  const a=scoreRetrievalControl(item('table_cross-06'),response(5),manifest,5),b=scoreRetrievalControl(item('table_cross-06'),response(10),manifest,10)
  assert.equal(a.evidenceCoverage,0.5);assert.equal(b.evidenceCoverage,1)
  assert.equal(a.annotationSupportingHits,1);assert.equal(b.annotationSupportingHits,2)
  assert.equal(a.unlabeledCandidateCount,4);assert.equal(b.unlabeledCandidateCount,8)
  assert.equal(b.contentCharacters,suite.documents.reduce((n,d)=>n+d.content.length,0))
  const negative=scoreRetrievalControl(item('no_answer-01'),response(10),manifest,10)
  assert.equal(negative.evidenceCoverage,null);assert.equal(negative.unlabeledCandidateCount,null)
  assert.ok(!('noiseRate' in negative));assert.ok(!('refusalAccuracy' in negative))
})
test('真实前五结果配对；不假定Top10前缀等于Top5',()=>{
  const input=observations(),ten=input.find(r=>r.topK===10);ten.response.results=[...ten.response.results].reverse().map((r,i)=>({...r,rank:i+1}))
  const report=retrievalControlReport(manifest,input,{runId,mode:'SCORER_FIXTURE'})
  assert.equal(report.allCasesCompleted,true);assert.equal(report.summary.pairedCases,40)
  assert.deepEqual(report.summary.prefixDifferentCases,['direct-01'])
  assert.equal(report.top5.answerableCompleted,33);assert.equal(report.top5.noAnswerCompleted,7)
  assert.equal(report.top10.completeEvidenceRate,1);assert.equal(report.holdoutExecuted,false)
  assert.equal(report.qualityReleaseApproved,false);assert.equal(report.causalImprovementProven,false)
})
test('首错/缺题/无答案空召回分别记录，不完整对照无全量delta',()=>{
  const report=retrievalControlReport(manifest,[{caseId:'direct-01',topK:5,error:'private error'},{caseId:'no_answer-01',topK:10,response:{...response(10),state:'EMPTY',results:[]}}],{runId})
  assert.equal(report.summary.errors,1);assert.equal(report.summary.notRun,78);assert.equal(report.delta,null)
  assert.equal(report.top10.noAnswerEmptyRate,1);assert.ok(!JSON.stringify(report).includes('private error'))
  for(const input of [[observations()[0],observations()[0]],[{caseId:'no_answer-08',topK:5,response:response()}],[{caseId:'direct-01',topK:6,response:response()}]])assert.throws(()=>retrievalControlReport(manifest,input,{runId}))
})
test('报告不含题目/正文/名称/异常；未知资料和超长片段失败关闭',()=>{
  const json=JSON.stringify(retrievalControlReport(manifest,observations(),{runId}))
  for(const text of [item('direct-01').query,suite.documents[0].content,suite.documents[0].fileName,'星河归档'])assert.ok(!json.includes(text))
  for(const change of [{documentId:'999'},{content:'x'.repeat(4001)},{score:NaN},{rank:2}]){const r=response();Object.assign(r.results[0],change);assert.throws(()=>scoreRetrievalControl(item('direct-01'),r,manifest,5))}
})
function fixture({drift=false,modelDrift=false,corpusDrift=false,failed=false}={}) {
  const calls=[],checkpoints=[],waits=[];let kbChecks=0,modelChecks=0
  const api=data=>new Response(JSON.stringify({code:200,data}),{headers:{'Content-Type':'application/json'}})
  const request=async(url,options)=>{
    calls.push({path:url.pathname,method:options.method,body:options.body})
    if(url.pathname==='/ai/knowledge/11'){kbChecks++;return api({...manifest,id:11,indexVersion:1,embeddingModelId:10,embeddingModelRevision:1,name:'QA fixed',indexStatus:'READY',retrievalTopK:5,retrievalMinScore:0.5,...(drift&&kbChecks>1?{indexVersion:2}:{})})}
    if(url.pathname==='/ai/model-center/models/10'){modelChecks++;return api({id:10,revision:modelDrift&&modelChecks>1?2:1})}
    if(url.pathname==='/ai/knowledge/document/list')return api({rows:suite.documents.map(d=>({id:manifest.documents[d.key],name:d.fileName,status:'2',moderationStatus:corpusDrift?'BLOCKED':'SAFE'}))})
    if(url.pathname==='/ai/knowledge/11/retrieval-test'){if(failed)throw new Error('private provider credential');const body=JSON.parse(options.body);return api(response(body.topK))}
    throw new Error('UNEXPECTED_PATH')
  }
  return {calls,checkpoints,waits,dependencies:{request,wait:async ms=>waits.push(ms),checkpoint:async row=>checkpoints.push(row)}}
}
const options={base:'http://localhost:8081',token:'fixture-token',confirmEmbedding:true,maxCalls:4}
test('80请求有预算且限定本机；不接受保留集、远端、凭证URL或隐式模型确认',async()=>{
  for(const change of [{maxCalls:81},{maxCalls:0},{confirmEmbedding:false},{base:'https://example.com'},{base:'http://name:pass@localhost:8081'},{base:'http://localhost:8081/path'}]){
    const f=fixture();await assert.rejects(()=>collectRetrievalControl(manifest,{...options,...change},f.dependencies));assert.equal(f.calls.length,0)
  }
  await assert.rejects(()=>main(['--manifest','x','--out','y','--split','holdout']))
})
test('只真实诊断POST，不创建会话/改参数；串行冷却与派发前检查点',async()=>{
  const f=fixture(),report=await collectRetrievalControl(manifest,options,f.dependencies)
  assert.equal(report.summary.completed,4);assert.equal(report.summary.notRun,76)
  assert.deepEqual(f.waits,[5200,5200,5200]);assert.equal(f.checkpoints[0].phase,'BEFORE_DISPATCH')
  const posts=f.calls.filter(c=>c.method==='POST');assert.equal(posts.length,4);assert.ok(posts.every(c=>c.path==='/ai/knowledge/11/retrieval-test'))
  assert.deepEqual(posts.map(c=>JSON.parse(c.body).topK),[5,10,10,5])
  assert.ok(f.calls.every(c=>['GET','POST'].includes(c.method)))
})
test('索引/模型/审核漂移或请求失败首错停止，不重试、不保留异常',async()=>{
  for(const config of [{drift:true},{modelDrift:true},{failed:true}]){
    const f=fixture(config),report=await collectRetrievalControl(manifest,options,f.dependencies)
    assert.equal(report.summary.errors,1);assert.equal(report.summary.notRun,79)
    assert.ok(f.calls.filter(c=>c.method==='POST').length<=1);assert.ok(!JSON.stringify(report).includes('private provider'))
  }
  const f=fixture({corpusDrift:true});await assert.rejects(()=>collectRetrievalControl(manifest,options,f.dependencies));assert.equal(f.calls.filter(c=>c.method==='POST').length,0)
})
test('派发后漂移丢弃响应，不把已经返回的结果当作有效质量数据',async()=>{
  const f=fixture();let dispatched=false
  const request=async(url,opts)=>{const r=await f.dependencies.request(url,opts);if(opts.method==='POST')dispatched=true;if(dispatched&&url.pathname==='/ai/knowledge/11'){const data=await r.json();data.data.indexVersion=2;return new Response(JSON.stringify(data))}return r}
  const report=await collectRetrievalControl(manifest,options,{...f.dependencies,request})
  assert.equal(report.summary.completed,0);assert.equal(report.summary.errors,1);assert.equal(report.delta,null)
})
test('HTTP200中的业务错误不冒充空召回，首错停止且不泄露错误正文',async()=>{
  const f=fixture(),request=async(url,opts)=>opts.method==='POST'?new Response(JSON.stringify({code:502,msg:'private provider detail'})):f.dependencies.request(url,opts)
  const report=await collectRetrievalControl(manifest,options,{...f.dependencies,request})
  assert.equal(report.summary.completed,0);assert.equal(report.summary.errors,1);assert.equal(report.summary.notRun,79)
  assert.ok(!JSON.stringify(report).includes('private provider detail'));assert.equal(report.top5.noAnswerEmptyRate,null)
})
test('修订不变但模型公开视图或库资料改变也停止，不能悄悄换配置',async()=>{
  for(const path of ['/ai/model-center/models/10','/ai/knowledge/11']){
    const f=fixture();let seen=0
    const request=async(url,opts)=>{const r=await f.dependencies.request(url,opts);if(url.pathname===path&&++seen>1){const body=await r.json();body.data.description='changed';return new Response(JSON.stringify(body))}return r}
    const report=await collectRetrievalControl(manifest,options,{...f.dependencies,request})
    assert.equal(report.summary.errors,1);assert.equal(report.summary.completed,0);assert.equal(f.calls.filter(c=>c.method==='POST').length,0)
  }
})
test('CLI文件冲突或未确认在请求前拒绝，输出0600，既有文件不覆盖',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'retrieval-control-test-')),path=join(dir,'manifest.json'),out=join(dir,'report.json')
  await writeFile(path,JSON.stringify(manifest));await writeFile(out,'existing',{mode:0o600})
  const args=['--manifest',path,'--out',out,'--confirm-embedding','yes','--max-calls','1']
  await assert.rejects(()=>main(args));assert.equal(await readFile(out,'utf8'),'existing');assert.equal((await stat(out)).mode&0o777,0o600)
  const empty=join(dir,'not-created.json');await assert.rejects(()=>main(['--manifest',path,'--out',empty,'--max-calls','1']));await assert.rejects(()=>stat(empty))
})
