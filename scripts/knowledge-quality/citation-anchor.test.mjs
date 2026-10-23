import assert from 'node:assert/strict'
import test from 'node:test'
import {mkdtemp, readFile, stat, writeFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {citationAnchorObservations, decodeModelSuggestion, hash, main, reviewEvidence} from './evidence-review.mjs'
import {fixture} from './evidence-review-fixtures.mjs'

function sample(answer,claims){
 const f=fixture(),c=f.packet.cases[0];c.answer=answer;c.answerHash=hash(answer)
 c.sources[0].text='甲站提前三天。乙馆周三接待。';c.sourcesHash=hash(c.sources)
 const {packetHash,...body}=f.packet;f.packet.packetHash=hash(body)
 const raw=JSON.stringify({claims:claims.map(({text,occurrence=0,citations=[]})=>({text,occurrence,claimType:'FACT',citationBinding:citations.length?'BOUND':'UNBOUND',
  citations:citations.map(n=>({id:'S1',occurrence:n})),quotes:[],support:'NOT_ASSESSED',scopeFindings:[],rationale:''}))})
 return {...f,...decodeModelSuggestion(f.packet,'direct',raw)}
}
const observe=f=>citationAnchorObservations(f.packet,f.review)
test('复用前句重复标记只提示位置疑点，不改模型建议或历史契约',()=>{
 const f=sample('甲站提前三天。 [S1]\n乙馆周三接待。 [S1]',[{text:'甲站提前三天。',citations:[0]},{text:'乙馆周三接待。',citations:[0]}])
 const before=hash(f.review),legacy=hash(reviewEvidence(f.packet,f.review)),out=observe(f)
 assert.equal(out.cases[0].positionalWarningClaims,1);assert.deepEqual(out.cases[0].claims[1].codes,['REPEATED_ID_BEFORE_CLAIM','SHARED_MARKER'])
 assert.equal(out.cases[0].unassignedMarkerCount,1);assert.equal(out.automaticRebinding,false);assert.equal(out.qualityReleaseApproved,false)
 assert.equal(hash(f.review),before);assert.equal(hash(reviewEvidence(f.packet,f.review)),legacy)
})
test('正确的两处重复引用无前句位置警告，但不宣布语义通过',()=>{
 const out=observe(sample('甲站提前三天。 [S1]\n乙馆周三接待。 [S1]',[{text:'甲站提前三天。',citations:[0]},{text:'乙馆周三接待。',citations:[1]}]))
 assert.equal(out.cases[0].positionalWarningClaims,0);assert.equal(out.cases[0].unassignedMarkerCount,0)
 assert.ok(out.cases[0].claims.every(c=>c.semanticBindingVerified===false))
})
test('合法前置共享引用只记共享，不按最近标记强制拒绝',()=>{
 const out=observe(sample('[S1] 甲站提前三天。乙馆周三接待。',[{text:'甲站提前三天。',citations:[0]},{text:'乙馆周三接待。',citations:[0]}]))
 assert.equal(out.cases[0].positionalWarningClaims,0);assert.deepEqual(out.cases[0].claims[0].codes,['SHARED_MARKER'])
})
test('句后段落共享引用合法，不冒充每个主张已获语义支持',()=>{
 const out=observe(sample('甲站提前三天。乙馆周三接待。 [S1]',[{text:'甲站提前三天。',citations:[0]},{text:'乙馆周三接待。',citations:[0]}]))
 assert.equal(out.cases[0].positionalWarningClaims,0);assert.equal(out.cases[0].claims[0].afterClaimCount,1)
})
test('主张本身覆盖引用时标记重叠，而非误报前句复用',()=>{
 const text='甲站提前三天。 [S1]',out=observe(sample(text,[{text,citations:[0]}]))
 assert.equal(out.cases[0].claims[0].overlappingClaimCount,1);assert.equal(out.cases[0].positionalWarningClaims,0)
})
test('未分配编号不自动判缺引用或找全事实，拒答也不强凑绑定',()=>{
 const out=observe(sample('本次无法确认。 [S1]',[{text:'本次无法确认。'}]))
 assert.equal(out.cases[0].unassignedMarkerCount,1);assert.equal(out.cases[0].claims[0].selectedMarkers.length,0)
 assert.equal(out.claimExtractionCompleteness,'NOT_VERIFIED')
})
test('emoji 与重复主张遵循原 UTF16 精确出现序号',()=>{
 const out=observe(sample('🌟甲站。 [S1] 🌟甲站。 [S1]',[{text:'🌟甲站。',occurrence:1,citations:[1]}]))
 assert.equal(out.cases[0].claims[0].claimRange.start,11);assert.equal(out.cases[0].positionalWarningClaims,0)
})
test('组合编号语法未识别显式声明，不声称清点全部引用',()=>{
 const out=observe(sample('甲站。 [S1,S2] [S1]',[{text:'甲站。',citations:[0]}]))
 assert.equal(out.cases[0].totalMarkers,1);assert.equal(out.cases[0].recognizedMarkerSyntax,'SINGLE_BRACKET_S1_TO_S50_ONLY')
})
test('超大标记清单截断有界，不把局部清点伪装为完整统计',()=>{
 const out=observe(sample('甲站。 '+'[S1]'.repeat(2100),[{text:'甲站。',citations:[0]}]))
 assert.equal(out.cases[0].totalMarkers,2100);assert.equal(out.cases[0].inventoryTruncated,true);assert.equal(out.cases[0].unassignedMarkerCount,null)
 assert.equal(out.cases[0].claims[0].alternateSameIdMarkers.length,20);assert.equal(out.cases[0].unassignedMarkers.length,100)
})
test('独立位置报告不复制问题、答案、引句、理由或任意字段',()=>{
 const f=sample('私有合成句。 [S1]',[{text:'私有合成句。',citations:[0]}]),out=JSON.stringify(observe(f))
 for(const value of [f.packet.cases[0].answer,f.packet.cases[0].sources[0].text,f.packet.cases[0].query,'私有合成句'])assert.ok(!out.includes(value))
})
test('绑定漂移和伪造引用位置先按旧契约拒绝，不启用宽松修复',()=>{
 const f=sample('甲站。 [S1]',[{text:'甲站。',citations:[0]}]);f.review.cases[0].claims[0].citationSpans[0].start=0
 assert.throws(()=>observe(f));f.packet.cases[0].answer+='变更';assert.throws(()=>observe(f))
})
test('真实失败形态：UNBOUND却声称CITED_SUPPORT即使引文真实也拒绝，不偷补绑定',()=>{
 const f=sample('甲站。 [S1]',[{text:'甲站。',citations:[0]}]),before=hash(f.review)
 const raw={claims:[{text:'甲站。',occurrence:0,claimType:'FACT',citationBinding:'UNBOUND',citations:[],quotes:[{id:'S1',text:f.packet.cases[0].sources[0].text,occurrence:0,purpose:'CITED_SUPPORT'}],support:'SUPPORTED',scopeFindings:[],rationale:'合成错误记录'}]}
 assert.throws(()=>decodeModelSuggestion(f.packet,'direct',JSON.stringify(raw)),{message:'INVALID_EVIDENCE_QUOTE'})
 assert.equal(hash(f.review),before)
})
test('独立anchors命令零网络私有输出且不覆盖原证据',async()=>{
 const root=await mkdtemp(join(tmpdir(),'polaris-anchor-fixture-')),f=sample('甲站。 [S1]',[{text:'甲站。',citations:[0]}])
 const packet=join(root,'packet.json'),decisions=join(root,'decisions.json'),out=join(root,'anchors.json')
 await writeFile(packet,JSON.stringify(f.packet),{mode:0o600});await writeFile(decisions,JSON.stringify(f.review),{mode:0o600})
 const result=await main(['anchors','--packet',packet,'--decisions',decisions,'--out',out])
 assert.equal(result.cases,1);assert.equal((await stat(out)).mode&0o777,0o600)
 const saved=await readFile(out,'utf8');assert.equal(JSON.parse(saved).liveAuthorizationVerified,false)
 await assert.rejects(()=>main(['anchors','--packet',packet,'--decisions',decisions,'--out',out]))
 assert.equal(await readFile(out,'utf8'),saved)
})
