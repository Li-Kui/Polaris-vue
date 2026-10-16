import assert from 'node:assert/strict'
import test from 'node:test'
import {mkdtemp, readdir, readFile, stat, writeFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {generationCaseIds, suite} from './natural-long-suite.mjs'
import {collect, evaluate, main, validateManifest, validateSuite} from './evaluate.mjs'
import {suite as fixedSuite} from './suite.mjs'

const hashes=validateSuite(suite)
const manifest={knowledgeId:'90',indexVersion:'1',embeddingModelId:'10',embeddingModelRevision:'1',embeddingSchemaHash:'schema',embeddingDimension:1024,indexSignature:'index',chunkSize:700,chunkOverlap:60,splitterType:'RECURSIVE',corpusVerified:true,corpusHash:hashes.corpusHash,documents:Object.fromEntries(suite.documents.map((d,i)=>[d.key,String(200+i)]))}
const response=(results=[])=>({knowledgeId:90,indexVersion:1,strategy:'VECTOR_V1',topK:5,minScore:0.5,state:results.length?'FOUND':'EMPTY',durationMs:5,results:results.map((r,i)=>({rank:i+1,score:0.8,...r}))})

test('独立自然行文合成集固定五文档八开发题，不扩写原60题或访问保留集',()=>{
 assert.equal(suite.documents.length,5);assert.equal(suite.cases.length,8);assert.ok(suite.cases.every(c=>c.split==='dev'))
 assert.equal(fixedSuite.cases.length,60);assert.equal(fixedSuite.cases.filter(c=>c.split==='holdout').length,20)
 assert.ok(suite.documents.find(d=>d.key==='current').content.length>=3000)
 assert.equal(suite.cases.filter(c=>c.category==='NO_ANSWER').length,2)
})
test('问题与标注清单不入正文，不以相同段落重复填充自然长文',()=>{
 for(const doc of suite.documents){for(const c of suite.cases)assert.ok(!doc.content.includes(c.query));const p=doc.content.split(/\n\s*\n/).filter(x=>x.length>40);assert.equal(new Set(p).size,p.length)}
 assert.equal(new Set(suite.documents.map(d=>d.fileName)).size,5)
})
test('四个生成题在召回前固定，包含版本/条件/跨文档/无答案边界',()=>{
 assert.deepEqual(generationCaseIds,['scope-current','condition-not-storage','linked-calendar','unknown-contact'])
 assert.ok(generationCaseIds.every(id=>suite.cases.some(c=>c.id===id)))
 assert.equal(new Set(generationCaseIds).size,4)
})
test('现行、失效、近邻、伙伴独立来源，不把同句出现在错误文档当命中',()=>{
 validateManifest(suite,manifest);const item=suite.cases.find(c=>c.id==='scope-current')
 const wrong=evaluate(suite,manifest,[{caseId:item.id,response:response([{documentId:manifest.documents.retired,content:item.evidence[0].text}])}])
 assert.equal(wrong.cases.find(c=>c.caseId===item.id).completeEvidence,false)
 const right=evaluate(suite,manifest,[{caseId:item.id,response:response([{documentId:manifest.documents.current,content:item.evidence[0].text}])}])
 assert.equal(right.cases.find(c=>c.caseId===item.id).completeEvidence,true)
})
test('跨文档题缺少伙伴片段只记部分，不改标注或将其他题的来源补进来',()=>{
 const item=suite.cases.find(c=>c.id==='linked-calendar'),one=item.evidence[0]
 const r=evaluate(suite,manifest,[{caseId:item.id,response:response([{documentId:manifest.documents.current,content:one.text}])}])
 const row=r.cases.find(c=>c.caseId===item.id);assert.equal(row.evidenceCoverage,0.5);assert.equal(row.completeEvidence,false)
 assert.equal(r.summary.notRun,7);assert.equal(r.qualityReleaseApproved,false)
})
test('无答案非空只记录召回，公开报告不泄露问题/正文或冒充拒答质量',()=>{
 const c=suite.cases.find(c=>c.id==='unknown-contact'),text='相关但未提供号码的合成片段'
 const r=evaluate(suite,manifest,[{caseId:c.id,response:response([{documentId:manifest.documents.current,content:text}])}]),json=JSON.stringify(r)
 assert.equal(r.summary.noAnswerCompleted,1);assert.equal(r.summary.noAnswerEmptyRate,0);assert.ok(!('refusalAccuracy' in r.summary))
 assert.ok(!json.includes(c.query));assert.ok(!json.includes(text));assert.equal(r.qualityReleaseApproved,false)
})
test('显式选择自然长文集导出五份原文，私有权限、不导出题库、不覆盖旧目录',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'natural-corpus-')),out=join(dir,'corpus')
 assert.deepEqual(await main(['export-corpus','--suite','natural-long','--out',out]),{exportedDocuments:5,uploaded:false})
 const names=await readdir(out);assert.equal(names.length,6)
 for(const d of suite.documents){assert.equal(await readFile(join(out,d.fileName),'utf8'),d.content);assert.equal((await stat(join(out,d.fileName))).mode&0o777,0o600)}
 const m=JSON.parse(await readFile(join(out,'manifest-template.json'),'utf8'));assert.equal(m.corpusHash,hashes.corpusHash);assert.equal(m.corpusVerified,false)
 await assert.rejects(()=>main(['export-corpus','--suite','natural-long','--out',out]));await assert.rejects(()=>main(['export-corpus','--suite','other','--out',join(dir,'bad')]))
})
test('新集沿用本机预算限制与首错停止，不偷偷请求生成或原保留集',async()=>{
 const calls=[];const request=async(url,options)=>{calls.push(url.pathname);if(url.pathname.endsWith('/retrieval-test'))throw new Error('private secret');return new Response(JSON.stringify({code:200,data:{id:90,indexStatus:'READY',...manifest}}))}
 const options={base:'http://localhost:8081',token:'fixture',split:'dev',minScore:0.5,maxCalls:8}
 const r=await collect(suite,manifest,options,{request,wait:async()=>{}});assert.equal(r.summary.errors,1);assert.equal(r.summary.notRun,7);assert.equal(calls.filter(p=>p.endsWith('/retrieval-test')).length,1)
 assert.ok(!calls.some(p=>p.includes('/ai/chat/')));assert.ok(!JSON.stringify(r).includes('private secret'))
 await assert.rejects(()=>collect(suite,manifest,{...options,base:'https://example.com'},{request}))
 await assert.rejects(()=>collect(suite,manifest,{...options,maxCalls:9},{request}))
 await assert.rejects(()=>collect(suite,manifest,{...options,split:'holdout'},{request}))
})
test('CLI离线评分显式选中新集的八题，不能混用fixed清单或冒充全部完成',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'natural-score-')),m=join(dir,'manifest.json'),observations=join(dir,'observations.json'),out=join(dir,'report.json')
 const item=suite.cases[0]
 await writeFile(m,JSON.stringify(manifest),{mode:0o600});await writeFile(observations,JSON.stringify([{caseId:item.id,response:response([{documentId:manifest.documents.current,content:item.evidence[0].text}])}]),{mode:0o600})
 const result=await main(['evaluate','--suite','natural-long','--manifest',m,'--observations',observations,'--out',out])
 assert.equal(result.summary.total,8);assert.equal(result.summary.completed,1);assert.equal(result.summary.notRun,7);assert.equal(result.allCasesCompleted,false)
 const report=JSON.parse(await readFile(out,'utf8'));assert.equal(report.suiteHash,hashes.suiteHash);assert.equal(report.qualityReleaseApproved,false);assert.equal((await stat(out)).mode&0o777,0o600)
 await assert.rejects(()=>main(['evaluate','--suite','fixed','--manifest',m,'--observations',observations,'--out',join(dir,'wrong.json')]))
})
