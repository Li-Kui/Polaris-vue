import assert from 'node:assert/strict'
import test from 'node:test'
import {mkdtemp, readFile, stat, writeFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {suite} from './suite.mjs'
import {validateSuite} from './evaluate.mjs'
import {
    collectGeneration,
    evidenceCoverage,
    generationHash,
    generationReport,
    main,
    pilotIds,
    reviewGeneration,
    validateGenerationManifest
} from './generation.mjs'

const runId='11111111-2222-3333-4444-555555555555'
const model={id:5,revision:2,capabilities:[]}
const manifest={knowledgeId:'11',indexVersion:'1',embeddingModelId:'10',embeddingModelRevision:'1',embeddingSchemaHash:'fixture-schema',embeddingDimension:2,indexSignature:'fixture-index',chunkSize:300,chunkOverlap:30,splitterType:'RECURSIVE',corpusVerified:true,corpusHash:validateSuite(suite).corpusHash,documents:Object.fromEntries(suite.documents.map((doc,i)=>[doc.key,String(50+i)])),answerModelConfigId:'5',answerModelRevision:'2',answerModelAggregateHash:generationHash(model)}
const item=id=>suite.cases.find(c=>c.id===id)
const observation=(id='direct-01')=>({caseId:id,status:'COMPLETED',conversationId:'400',messageId:'900',answer:'合成回答：每月第三个星期三 [S1]',citationState:'IDS_VALID',previews:item(id).evidence.map(e=>({citationId:'S1',documentId:manifest.documents[e.documentKey],text:e.text,truncated:false,locations:[]}))})
const packetFor=observations=>({packetVersion:1,runId,syntheticOnly:true,observations})
const reportFor=observations=>generationReport(manifest,observations,{runId,mode:'SCORER_FIXTURE'})

test('固定12条只用开发集，三条单库正例/两条关联/七条无答案，保留集不混入',()=>{
  assert.equal(pilotIds.length,12);assert.equal(new Set(pilotIds).size,12)
  assert.ok(pilotIds.every(id=>item(id)?.split==='dev'))
  assert.equal(pilotIds.filter(id=>item(id).category==='NO_ANSWER').length,7)
  assert.deepEqual(pilotIds.filter(id=>item(id).category==='TABLE_CROSS'),['table_cross-01','table_cross-06','table_cross-07'])
})
test('索引清单和回答模型修订/配置摘要均必要，不能悄悄换模型',()=>{
  validateGenerationManifest(manifest)
  for(const fields of [{corpusVerified:false},{answerModelConfigId:'0'},{answerModelRevision:null},{answerModelAggregateHash:'wrong'}])assert.throws(()=>validateGenerationManifest({...manifest,...fields}))
})
test('候选数对照只允许清单明确记录5或10，未填保持旧基线5且不写生效参数',()=>{
  validateGenerationManifest({...manifest,retrievalTopK:10})
  for(const value of [0,6,50,'10',null])assert.throws(()=>validateGenerationManifest({...manifest,retrievalTopK:value}))
  const old=reportFor([observation()]);assert.ok(!('retrievalTopK' in old))
  assert.equal(generationReport({...manifest,retrievalTopK:10},[observation()],{runId}).retrievalTopK,10)
})
test('实际入选片段同时匹配文档与完整标注，部分证据不冒充充分',()=>{
  const cross=item('table_cross-06'),previews=observation(cross.id).previews
  assert.equal(evidenceCoverage(cross,previews,manifest).completeEvidence,true)
  assert.equal(evidenceCoverage(cross,previews.slice(0,1),manifest).evidenceCoverage,0.5)
  assert.equal(evidenceCoverage(item('direct-01'),[{documentId:manifest.documents.visit,text:item('direct-01').evidence[0].text}],manifest).completeEvidence,false)
})
test('无答案非空、编号合法、拒答关键词都不自动判语义正确',()=>{
  const row={...observation('no_answer-01'),answer:'无法排除负责人为某人 [S1]',previews:[{documentId:manifest.documents.archive,text:'相关但没有负责人姓名'}]}
  const report=reportFor([row]);assert.equal(report.summary.noAnswerNonempty,1);assert.equal(report.qualityReleaseApproved,false)
  assert.ok(!('refusalAccuracy' in report.summary));assert.ok(!('answerAccuracy' in report.summary))
})
test('完整子集不冒充40条或保留集，错误和未执行不混入拒答',()=>{
  const report=reportFor(pilotIds.map(id=>observation(id)))
  assert.equal(report.allSelectedCompleted,true);assert.equal(report.totalDevCases,40);assert.equal(report.notSelectedDevCases,28);assert.equal(report.holdoutExecuted,false)
  const partial=reportFor([{caseId:'direct-01',status:'ERROR'}]);assert.equal(partial.summary.errors,1);assert.equal(partial.summary.notRun,11)
})
test('公开报告只有摘要/ID/状态，问题、正文、来源名称和异常细节不外传',()=>{
  const o=observation();o.privateError='private secret';const report=reportFor([o]),json=JSON.stringify(report)
  assert.ok(!json.includes(o.answer));assert.ok(!json.includes(o.previews[0].text));assert.ok(!json.includes('private secret'));assert.ok(!json.includes(item(o.caseId).query))
  assert.throws(()=>reportFor([o,o]));assert.throws(()=>reportFor([{...o,caseId:'no_answer-08'}]))
})
test('审阅绑定原答复/实际片段摘要，AGENT审阅不冒充人工放行',()=>{
  const observations=[observation()],report=reportFor(observations),row=report.cases[0]
  const decisions={runId,reviewerType:'AGENT',cases:[{caseId:row.caseId,label:'SUPPORTED_ANSWER',answerHash:row.answerHash,previewHash:row.previewHash,rationale:'合成回答与标注事实和实际片段相符'}]}
  const result=reviewGeneration(report,manifest,packetFor(observations),decisions)
  assert.equal(result.reviewerType,'AGENT');assert.equal(result.counts.SUPPORTED_ANSWER,1);assert.equal(result.qualityReleaseApproved,false)
  assert.ok(!JSON.stringify(result).includes(decisions.cases[0].rationale))
  assert.throws(()=>reviewGeneration({...report,summary:{completed:99}},manifest,packetFor(observations),decisions))
  assert.throws(()=>reviewGeneration(report,manifest,packetFor([{...observations[0],answer:'被替换'}]),decisions))
})
test('缺失支持不能标全部正确，无答案/不完整支持的拒答不能算错误拒答',()=>{
  const observations=[{...observation('table_cross-06'),previews:observation('table_cross-06').previews.slice(0,1)}],report=reportFor(observations),row=report.cases[3]
  const decision={caseId:row.caseId,label:'SUPPORTED_ANSWER',answerHash:row.answerHash,previewHash:row.previewHash,rationale:'人工备注'}
  for(const label of ['SUPPORTED_ANSWER','WRONG_REFUSAL'])assert.throws(()=>reviewGeneration(report,manifest,packetFor(observations),{runId,reviewerType:'AGENT',cases:[{...decision,label}]}))
  assert.equal(reviewGeneration(report,manifest,packetFor(observations),{runId,reviewerType:'AGENT',cases:[{...decision,label:'PARTIAL_ANSWER'}]}).counts.PARTIAL_ANSWER,1)
})

function fixture({streamError=false,nonemptyInitial=false,drift=false,empty=false}={}) {
  const calls=[],checkpoints=[];let sent=false,checks=0
  const api=value=>new Response(JSON.stringify({code:200,data:value}),{headers:{'Content-Type':'application/json'}})
  const request=async(url,options)=>{
    const path=url.pathname;calls.push({path,method:options.method,body:options.body})
    if(path==='/ai/knowledge/11'){checks++;return api({id:11,name:'QA fixed fixture',indexStatus:'READY',retrievalTopK:5,retrievalMinScore:0.5,...Object.fromEntries(['indexVersion','embeddingModelId','embeddingModelRevision','embeddingSchemaHash','embeddingDimension','indexSignature','chunkSize','chunkOverlap','splitterType'].map(key=>[key,manifest[key]])),...(drift&&checks>1?{indexVersion:2}:{})})}
    if(path==='/ai/model-center/models/5')return api(model)
    if(path==='/ai/knowledge/document/list')return api({rows:suite.documents.map(doc=>({id:Number(manifest.documents[doc.key]),name:doc.fileName,status:'2',moderationStatus:'SAFE'}))})
    if(path==='/ai/chat/conversations')return api({id:400,modelConfigId:5,knowledgeBaseId:11})
    if(path.endsWith('/title'))return api(null)
    if(path==='/ai/chat/stream'){
      sent=true;if(streamError)throw new Error('private credential error')
      const input=JSON.parse(options.body);assert.equal(input.message,item('direct-01').query);assert.deepEqual(input.attachmentTokens,[]);assert.equal(input.enableSearch,false)
      return new Response(empty?'event: knowledge_retrieval\ndata: {"state":"EMPTY","answerStopped":true}\n\nevent: error\ndata: 停止回答\n\n':'event: message\ndata: fixture\n\nevent: done\ndata: [DONE]\n\n')
    }
    if(path==='/ai/chat/conversations/400/messages')return api(!sent?(nonemptyInitial?[{role:'user'}]:[]):empty?[]:[{role:'user',content:item('direct-01').query},{id:900,role:'assistant',content:observation().answer,moderationStatus:'SAFE',citationValidation:{state:'IDS_VALID'},attachedDocs:[{docId:50,knowledgeBaseId:11,citationIds:['S1']}]}])
    if(path.endsWith('/citations/S1'))return api({citationId:'S1',documentId:50,indexVersion:1,text:item('direct-01').evidence[0].text,locations:[],truncated:false})
    throw new Error('UNEXPECTED_PATH')
  }
  return {calls,checkpoints,dependencies:{request,wait:async()=>{},checkpoint:async p=>{checkpoints.push(p)}}}
}
const options={base:'http://localhost:8081',token:'fixture-token',confirmModelCalls:true,maxCalls:1}
test('真实采集必须明确预算并限本机；缺确认、非法预算/标签、远端地址均零请求',async()=>{
  for(const change of [{confirmModelCalls:false},{maxCalls:13},{maxCalls:0},{base:'https://example.com'},{base:'http://name:pass@localhost:8081'},{label:'unsafe label'}]){
    const f=fixture();await assert.rejects(()=>collectGeneration(manifest,{...options,...change},f.dependencies));assert.equal(f.calls.length,0)
  }
})
test('一题一独立空会话，派发前检查点；只收实际片段，不再做诊断检索',async()=>{
  const f=fixture(),result=await collectGeneration(manifest,options,f.dependencies)
  assert.equal(result.report.summary.completed,1);assert.equal(result.report.summary.notRun,11);assert.equal(result.packet.observations[0].previews[0].text,item('direct-01').evidence[0].text)
  assert.equal(f.calls.filter(c=>c.path==='/ai/chat/stream').length,1);assert.ok(!f.calls.some(c=>c.path.includes('retrieval-test')))
  assert.equal(f.checkpoints[0].phase,'BEFORE_DISPATCH');assert.equal(f.checkpoints[1].phase,'COMPLETED')
})
test('来源/配置漂移、旧会话非空、首个请求失败均停止剩余调用且不输出异常',async()=>{
  for(const config of [{drift:true},{nonemptyInitial:true},{streamError:true}]){
    const f=fixture(config),result=await collectGeneration(manifest,{...options,maxCalls:12},f.dependencies)
    assert.equal(result.report.summary.errors,1);assert.equal(result.report.summary.notRun,11)
    assert.ok(f.calls.filter(c=>c.path==='/ai/chat/stream').length<=1);assert.ok(!JSON.stringify(result).includes('private credential'))
  }
})
test('空召回停止作为独立状态，不冒充模型生成了拒答',async()=>{
  const f=fixture({empty:true}),result=await collectGeneration(manifest,options,f.dependencies)
  assert.equal(result.report.summary.stoppedEmpty,1);assert.equal(result.report.summary.completed,0);assert.equal(result.packet.observations[0].answer,'')
})
test('CLI旧输出或审阅文件冲突在网络调用前拒绝，私有文件权限不扩大',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'generation-test-')),path=join(dir,'manifest.json'),out=join(dir,'report.json'),packet=join(dir,'packet.json')
  await writeFile(path,JSON.stringify(manifest));await writeFile(packet,'existing')
  await assert.rejects(()=>main(['collect','--manifest',path,'--out',out,'--review-packet',packet,'--confirm-model-calls','yes','--max-calls','1']))
  assert.equal(await readFile(packet,'utf8'),'existing');assert.equal((await stat(out)).mode&0o777,0o600)
  await assert.rejects(()=>main(['collect','--manifest',path,'--out',out,'--review-packet',packet,'--confirm-model-calls','yes','--max-calls','1']))
})
