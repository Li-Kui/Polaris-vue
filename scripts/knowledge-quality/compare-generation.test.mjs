import assert from 'node:assert/strict'
import test from 'node:test'
import {mkdtemp, readFile, stat, writeFile} from 'node:fs/promises'
import {join} from 'node:path'
import {tmpdir} from 'node:os'
import {suite} from './suite.mjs'
import {validateSuite} from './evaluate.mjs'
import {generationHash, generationReport, pilotIds} from './generation.mjs'
import {compareGeneration, main} from './compare-generation.mjs'

const manifest={knowledgeId:'11',indexVersion:'1',embeddingModelId:'10',embeddingModelRevision:'1',embeddingSchemaHash:'schema',embeddingDimension:2,indexSignature:'index',chunkSize:300,chunkOverlap:30,splitterType:'RECURSIVE',corpusVerified:true,corpusHash:validateSuite(suite).corpusHash,documents:Object.fromEntries(suite.documents.map((d,i)=>[d.key,String(50+i)])),answerModelConfigId:'5',answerModelRevision:'1',answerModelAggregateHash:generationHash({fixture:true})}
function bundle(topK,{change={},mode='SCORER_FIXTURE',reviewerType='AGENT'}={}) {
  const m={...manifest,...change,...(topK===10?{retrievalTopK:10}:{})}
  const runId=topK===5?'11111111-2222-3333-4444-555555555555':'66666666-7777-8888-9999-aaaaaaaaaaaa'
  const observations=pilotIds.map((id,i)=>{
    const item=suite.cases.find(c=>c.id===id),selected=topK===5&&['table_cross-06','table_cross-07'].includes(id)?item.evidence.slice(0,1):item.evidence
    return {caseId:id,status:'COMPLETED',conversationId:String((topK===5?100:200)+i),messageId:String((topK===5?300:400)+i),answer:'私有合成答复 '+id,citationState:'IDS_VALID',previews:selected.length?selected.map(e=>({documentId:m.documents[e.documentKey],text:e.text})):[{documentId:m.documents.archive,text:'私有相关片段但无答案'}]}
  })
  const report=generationReport(m,observations,{runId,mode})
  const decisions={runId,reviewerType,cases:report.cases.map(row=>({caseId:row.caseId,answerHash:row.answerHash,previewHash:row.previewHash,
    label:row.category==='NO_ANSWER'?'CORRECT_REFUSAL':row.completeEvidence?'SUPPORTED_ANSWER':'PARTIAL_ANSWER',rationale:'私有逐项审阅理由'}))}
  return {manifest:m,report,packet:{packetVersion:1,runId,syntheticOnly:true,observations},decisions}
}
test('离线Top5/10配对只改变候选数，区分实际证据覆盖和审阅标签，永不自动放行',()=>{
  const result=compareGeneration(bundle(5),bundle(10))
  assert.equal(result.selectedCases,12);assert.equal(result.reviewerType,'AGENT')
  assert.equal(result.evidence.before.completeEvidenceCases,3);assert.equal(result.evidence.after.completeEvidenceCases,5)
  assert.equal(result.labels.before.PARTIAL_ANSWER,2);assert.equal(result.labels.after.SUPPORTED_ANSWER,5)
  assert.equal(result.labels.after.CORRECT_REFUSAL,7);assert.equal(result.labelChanges.length,2)
  assert.equal(result.qualityReleaseApproved,false);assert.equal(result.causalImprovementProven,false)
  assert.equal(result.holdoutExecuted,false);assert.equal(result.notSelectedDevCases,28)
})
test('模型/索引/库/文档映射或采集模式不同不能冒充单参数对照',()=>{
  for(const change of [{knowledgeId:'12'},{indexVersion:'2'},{embeddingModelRevision:'2'},{answerModelRevision:'2'},{chunkSize:301},{documents:{...manifest.documents,archive:'99'}}])assert.throws(()=>compareGeneration(bundle(5),bundle(10,{change})))
  assert.throws(()=>compareGeneration(bundle(5),bundle(10,{mode:'RECORDED'})))
  assert.throws(()=>compareGeneration(bundle(5),bundle(10,{reviewerType:'HUMAN'})))
  assert.throws(()=>compareGeneration(bundle(10),bundle(5)))
})
test('不完整运行、漏审、篡改报告或原包、重复会话均拒绝比较',()=>{
  for(const mutate of [b=>{b.report.allSelectedCompleted=false},b=>{b.decisions.cases.pop()},b=>{b.packet.observations[0].answer='篡改'},b=>{b.report.summary.completed=11},b=>{b.decisions.cases[0].answerHash='wrong'}]){
    const b=bundle(10);mutate(b);assert.throws(()=>compareGeneration(bundle(5),b))
  }
  const b=bundle(10);b.packet.observations[1].conversationId=b.packet.observations[0].conversationId
  b.report=generationReport(b.manifest,b.packet.observations,{runId:b.report.runId,mode:b.report.mode})
  assert.throws(()=>compareGeneration(bundle(5),b),/INDEPENDENT_CONVERSATIONS_REQUIRED/)
})
test('两轮必须使用互不重用的会话，不能拿旧历史冒充新参数生成',()=>{
  const b=bundle(10);b.packet.observations[0].conversationId='100'
  b.report=generationReport(b.manifest,b.packet.observations,{runId:b.report.runId,mode:b.report.mode})
  assert.throws(()=>compareGeneration(bundle(5),b),/REUSED_CONTROL_CONVERSATION/)
})
test('真实有效但含空召回停止或请求错误的报告不进入完整生成对照',()=>{
  for(const status of ['STOPPED_EMPTY','ERROR']){
    const b=bundle(10)
    b.packet.observations[0]=status==='ERROR'?{caseId:'direct-01',status}:{caseId:'direct-01',status,conversationId:'200',answer:'',previews:[]}
    b.report=generationReport(b.manifest,b.packet.observations,{runId:b.report.runId,mode:b.report.mode})
    b.decisions.cases=b.report.cases.filter(r=>r.status!=='ERROR').map(r=>({caseId:r.caseId,answerHash:r.answerHash,previewHash:r.previewHash,
      label:r.status==='STOPPED_EMPTY'||r.category==='NO_ANSWER'?'CORRECT_REFUSAL':'SUPPORTED_ANSWER',rationale:'逐项审阅'}))
    assert.throws(()=>compareGeneration(bundle(5),b),/COMPLETE_GENERATION_REVIEW_REQUIRED/)
  }
})
test('公共对照不复制问答、片段、理由或原始异常',()=>{
  const result=JSON.stringify(compareGeneration(bundle(5),bundle(10)))
  for(const text of ['私有合成答复','私有相关片段','私有逐项审阅理由',suite.cases[0].query,suite.documents[0].content])assert.ok(!result.includes(text))
})
test('CLI离线输出0600，不覆盖已有文件，未知/缺失参数不接受',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'generation-control-test-')),args=[]
  for(const [prefix,b] of [['before',bundle(5)],['after',bundle(10)]])for(const [key,value] of Object.entries(b)){
    const name=key==='packet'?'review-packet':key,path=join(dir,`${prefix}-${name}.json`)
    await writeFile(path,JSON.stringify(value),{mode:0o600});args.push(`--${prefix}-${name}`,path)
  }
  const out=join(dir,'comparison.json');args.push('--out',out)
  await main(args);assert.equal((await stat(out)).mode&0o777,0o600);const saved=await readFile(out,'utf8')
  await assert.rejects(()=>main(args));assert.equal(await readFile(out,'utf8'),saved)
  await assert.rejects(()=>main([...args,'--confirm-model-calls','yes']));await assert.rejects(()=>main(args.slice(2)))
})
