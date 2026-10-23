import {open, stat} from 'node:fs/promises'
import {readFileSync} from 'node:fs'
import {dirname, resolve} from 'node:path'
import {pathToFileURL} from 'node:url'
import {generationHash as hash, generationReport} from './generation.mjs'
import {suite as fixedSuite} from './suite.mjs'
import {generationCaseIds, suite as naturalSuite} from './natural-long-suite.mjs'
import {validateSuite} from './evaluate.mjs'

export {hash}
export const supports=['SUPPORTED','PARTIAL','UNSUPPORTED','CONTRADICTED','UNCLEAR','NOT_ASSESSED']
export const findings=['ENTITY_SCOPE','TIME_SCOPE','CONDITION_SCOPE','ABSENCE_OVERREACH','IRRELEVANT_ADDITION','MISSING_CITATION','WRONG_CITATION','INCOMPLETE_REASONING']
const id=value=>typeof value==='string'&&/^[1-9][0-9]{0,18}$/.test(value)
const digest=value=>typeof value==='string'&&/^[a-f0-9]{64}$/.test(value)
const fail=code=>{throw new Error(code)}
const object=value=>value!==null&&typeof value==='object'&&!Array.isArray(value)
function keys(value,required,optional=[]){
  if(!object(value)||required.some(k=>!Object.hasOwn(value,k))||Object.keys(value).some(k=>!required.includes(k)&&!optional.includes(k)))fail('INVALID_SCHEMA')
}
function text(value,max,empty=false){if(typeof value!=='string'||value.length>max||(!empty&&!value.trim())||!wellFormed(value))fail('INVALID_TEXT')}
function wellFormed(value){for(let i=0;i<value.length;i++){const c=value.charCodeAt(i);if(c>=0xd800&&c<=0xdbff){const n=value.charCodeAt(++i);if(!(n>=0xdc00&&n<=0xdfff))return false}else if(c>=0xdc00&&c<=0xdfff)return false}return true}
const boundary=(value,n)=>!(n>0&&n<value.length&&value.charCodeAt(n-1)>=0xd800&&value.charCodeAt(n-1)<=0xdbff&&value.charCodeAt(n)>=0xdc00&&value.charCodeAt(n)<=0xdfff)
export function checkSpan(value,span){
  keys(span,['start','end','text'])
  if(!Number.isSafeInteger(span.start)||!Number.isSafeInteger(span.end)||span.start<0||span.end<=span.start||span.end>value.length||!boundary(value,span.start)||!boundary(value,span.end)||value.slice(span.start,span.end)!==span.text)fail('INVALID_SPAN')
}
const withoutHash=({packetHash,...body})=>body

/** Local records bind evidence, but do not prove live authorization or provider request contents. */
export function validatePacket(packet){
  keys(packet,['packetVersion','syntheticOnly','origin','originHash','suiteHash','cases','packetHash'])
  if(packet.packetVersion!==1||packet.syntheticOnly!==true||!['FIXED_V1','NATURAL_LONG_V1','FIXTURE_V1'].includes(packet.origin)||!digest(packet.originHash)||!digest(packet.suiteHash)||!digest(packet.packetHash)||packet.packetHash!==hash(withoutHash(packet)))fail('PACKET_BINDING_MISMATCH')
  if(!Array.isArray(packet.cases)||!packet.cases.length||packet.cases.length>40||JSON.stringify(packet).length>4*1024*1024)fail('PACKET_LIMIT')
  const seen=new Set()
  for(const c of packet.cases){
    keys(c,['caseId','conversationId','messageId','query','answer','citationState','sourceCompleteness','sources','answerHash','queryHash','sourcesHash'])
    if(typeof c.caseId!=='string'||!/^[a-z][a-z0-9-]{0,63}$/.test(c.caseId)||seen.has(c.caseId)||!id(c.conversationId)||!id(c.messageId))fail('INVALID_CASE')
    seen.add(c.caseId);text(c.query,2000);text(c.answer,200000)
    if(!['IDS_VALID','INVALID_IDS','MISSING_IDS','UNVERIFIED',null].includes(c.citationState)||!['COMPLETE','UNAVAILABLE'].includes(c.sourceCompleteness)||!Array.isArray(c.sources)||c.sources.length>50)fail('INVALID_SOURCES')
    const ids=new Set();let chars=0
    for(const s of c.sources){
      keys(s,['citationId','documentId','indexVersion','chunkNo','text','truncated','locations','state'])
      if(!/^S([1-9]|[1-4][0-9]|50)$/.test(s.citationId)||ids.has(s.citationId)||!id(s.documentId)||(s.indexVersion!==null&&!id(s.indexVersion))||(s.chunkNo!==null&&!/^[0-9]{1,9}$/.test(s.chunkNo))||typeof s.truncated!=='boolean'||!Array.isArray(s.locations)||s.locations.length>100||s.locations.some(l=>typeof l!=='string'||l.length>1000))fail('INVALID_SOURCE')
      ids.add(s.citationId)
      if(s.state==='AVAILABLE'){text(s.text,4000);chars+=s.text.length}else if(s.state!=='UNAVAILABLE'||s.text!==null)fail('INVALID_SOURCE')
    }
    if(chars>12000||(c.sourceCompleteness==='COMPLETE'&&(!c.sources.length||c.sources.some(s=>s.state!=='AVAILABLE'))))fail('SOURCE_LIMIT_OR_INCOMPLETE')
    if(c.answerHash!==hash(c.answer)||c.queryHash!==hash(c.query)||c.sourcesHash!==hash(c.sources))fail('CASE_BINDING_MISMATCH')
  }
  return packet
}

export function adaptRecorded({format,packet,report,manifest}){
  if(!['fixed-v1','natural-long-v1'].includes(format)||packet?.syntheticOnly!==true)fail('UNSUPPORTED_RECORDED_FORMAT')
  let rows,definition
  if(format==='fixed-v1'){
    definition=fixedSuite
    if(packet.packetVersion!==1||packet.runId!==report?.runId||!Array.isArray(packet.observations)||report.observationsHash!==hash(packet.observations)||hash(generationReport(manifest,packet.observations,{runId:report.runId,label:report.label,mode:report.mode}))!==hash(report))fail('RECORDED_BINDING_MISMATCH')
    rows=packet.observations.filter(c=>c.status==='COMPLETED')
  }else{
    definition=naturalSuite
    const expected=validateSuite(definition)
    if(report?.mode!=='LIVE_LOCAL_NATURAL_LONG_SYNTHETIC'||report.complete!==true||report.holdoutExecuted!==false||report.suiteHash!==expected.suiteHash||report.corpusHash!==expected.corpusHash||!Array.isArray(packet.cases)||!Array.isArray(report.cases)||report.cases.length!==packet.cases.length||report.chatRequests!==packet.cases.length||packet.cases.length!==generationCaseIds.length||generationCaseIds.some(key=>!packet.cases.some(c=>c.caseId===key)))fail('RECORDED_BINDING_MISMATCH')
    rows=packet.cases
  }
  const normalized=[]
  for(const row of rows){
    const summary=report.cases.find(c=>c.caseId===row.caseId),item=definition.cases.find(c=>c.id===row.caseId&&c.split==='dev')
    const query=format==='fixed-v1'?item?.query:row.query
    if(!item||query!==item.query||summary?.status!=='COMPLETED'||String(summary.conversationId)!==String(row.conversationId)||String(summary.messageId)!==String(row.messageId)||summary.answerHash!==hash(row.answer)||summary.previewHash!==hash(row.previews))fail('RECORDED_BINDING_MISMATCH')
    const sources=row.previews.map(p=>({citationId:p.citationId,documentId:String(p.documentId),indexVersion:p.indexVersion==null?null:String(p.indexVersion),chunkNo:p.chunkNo==null?null:String(p.chunkNo),text:p.text,truncated:p.truncated,locations:p.locations??[],state:'AVAILABLE'}))
    normalized.push({caseId:row.caseId,conversationId:String(row.conversationId),messageId:String(row.messageId),query,answer:row.answer,citationState:summary.citationState??null,sourceCompleteness:'COMPLETE',sources,answerHash:hash(row.answer),queryHash:hash(query),sourcesHash:hash(sources)})
  }
  const body={packetVersion:1,syntheticOnly:true,origin:format==='fixed-v1'?'FIXED_V1':'NATURAL_LONG_V1',originHash:hash({packet,report,...(manifest?{manifest}:{})}),suiteHash:validateSuite(definition).suiteHash,cases:normalized}
  return validatePacket({...body,packetHash:hash(body)})
}

/** Reviewer asserts citation-to-claim association; the validator only checks exact syntax and spans. */
export function reviewEvidence(packet,review){
  validatePacket(packet);keys(review,['reviewVersion','reviewerType','packetHash','cases'])
  if(review.reviewVersion!==1||!['HUMAN','AGENT','MODEL_SUGGESTION'].includes(review.reviewerType)||review.packetHash!==packet.packetHash||!Array.isArray(review.cases)||review.cases.length>packet.cases.length)fail('REVIEW_BINDING_MISMATCH')
  const seen=new Set(),result=[]
  for(const row of review.cases){
    keys(row,['caseId','answerHash','sourcesHash','claims'])
    const c=packet.cases.find(c=>c.caseId===row.caseId)
    if(!c||seen.has(row.caseId)||row.answerHash!==c.answerHash||row.sourcesHash!==c.sourcesHash||!Array.isArray(row.claims)||row.claims.length>50)fail('REVIEW_BINDING_MISMATCH')
    seen.add(row.caseId);const claimIds=new Set(),spans=[]
    for(const claim of row.claims){
      keys(claim,['claimId','answerSpan','claimType','citationBinding','citedSourceIds','citationSpans','evidenceQuotes','support','scopeFindings','rationale'])
      if(typeof claim.claimId!=='string'||!/^C([1-9]|[1-4][0-9]|50)$/.test(claim.claimId)||claimIds.has(claim.claimId)||!['FACT','MATERIAL_LIMITATION','ADVICE','UNCERTAIN'].includes(claim.claimType)||!['BOUND','UNBOUND'].includes(claim.citationBinding)||!supports.includes(claim.support))fail('INVALID_CLAIM')
      claimIds.add(claim.claimId);checkSpan(c.answer,claim.answerSpan);spans.push([claim.answerSpan.start,claim.answerSpan.end])
      if(!Array.isArray(claim.citedSourceIds)||!Array.isArray(claim.citationSpans)||!Array.isArray(claim.evidenceQuotes)||claim.citedSourceIds.length>50||claim.citationSpans.length>50||claim.evidenceQuotes.length>100||new Set(claim.citedSourceIds).size!==claim.citedSourceIds.length||!Array.isArray(claim.scopeFindings)||new Set(claim.scopeFindings).size!==claim.scopeFindings.length||claim.scopeFindings.some(f=>!findings.includes(f)))fail('INVALID_CLAIM')
      const cited=new Set()
      for(const span of claim.citationSpans){
        checkSpan(c.answer,span)
        if(!/^\[S([1-9]|[1-4][0-9]|50)\]$/.test(span.text))fail('INVALID_CITATION_SPAN')
        cited.add(span.text.slice(1,-1))
      }
      if(claim.citedSourceIds.some(s=>!c.sources.some(p=>p.citationId===s))||hash([...cited].sort())!==hash([...claim.citedSourceIds].sort())||(claim.citationBinding==='UNBOUND'&&(cited.size||claim.citedSourceIds.length||claim.citationSpans.length)))fail('CITATION_BINDING_MISMATCH')
      for(const quote of claim.evidenceQuotes){
        keys(quote,['citationId','purpose','span'])
        const source=c.sources.find(s=>s.citationId===quote.citationId)
        if(!source||source.state!=='AVAILABLE'||!['CITED_SUPPORT','OTHER_CONTEXT'].includes(quote.purpose)||(quote.purpose==='CITED_SUPPORT'&&!claim.citedSourceIds.includes(source.citationId)))fail('INVALID_EVIDENCE_QUOTE')
        checkSpan(source.text,quote.span)
      }
      text(claim.rationale,2000,claim.support==='NOT_ASSESSED')
      if(['SUPPORTED','PARTIAL','CONTRADICTED'].includes(claim.support)&&!claim.evidenceQuotes.length)fail('EVIDENCE_REQUIRED')
      if(claim.support==='SUPPORTED'&&claim.claimType==='FACT'&&(!claim.citedSourceIds.length||!claim.evidenceQuotes.some(q=>q.purpose==='CITED_SUPPORT')))fail('CITED_EVIDENCE_REQUIRED')
    }
    // Covered text is not proof that every factual claim was extracted. Overlaps may be legitimate.
    spans.sort((a,b)=>a[0]-b[0]);const merged=[]
    for(const span of spans){const last=merged.at(-1);if(last&&span[0]<=last[1])last[1]=Math.max(last[1],span[1]);else merged.push([...span])}
    const unreviewed=[];let cursor=0
    for(const [start,end] of merged){if(start>cursor)unreviewed.push({start:cursor,end:start});cursor=end}
    if(cursor<c.answer.length)unreviewed.push({start:cursor,end:c.answer.length})
    result.push({caseHash:hash(row.caseId),conversationId:c.conversationId,messageId:c.messageId,answerHash:c.answerHash,sourcesHash:c.sourcesHash,sourceCompleteness:c.sourceCompleteness,citationState:c.citationState,reviewedClaims:row.claims.length,notAssessedClaims:row.claims.filter(cl=>cl.support==='NOT_ASSESSED').length,unreviewedTextRanges:unreviewed,claimExtractionCompleteness:'NOT_VERIFIED',counts:Object.fromEntries(supports.map(s=>[s,row.claims.filter(cl=>cl.support===s).length])),findings:Object.fromEntries(findings.map(f=>[f,row.claims.filter(cl=>cl.scopeFindings.includes(f)).length])),claims:row.claims.map(cl=>({claimHash:hash(cl.claimId),spanHash:hash(cl.answerSpan),support:cl.support,scopeFindings:cl.scopeFindings,citationBinding:cl.citationBinding,quoteHashes:cl.evidenceQuotes.map(q=>hash(q)),rationaleHash:hash(cl.rationale)}))})
  }
  return {reviewVersion:1,packetHash:packet.packetHash,reviewHash:hash(review),reviewerType:review.reviewerType,recordsValid:true,qualityReleaseApproved:false,semanticVerification:'REVIEWER_ASSERTIONS_ONLY',liveAuthorizationVerified:false,holdoutExecuted:false,totalCases:packet.cases.length,reviewedCases:result.length,unreviewedCases:packet.cases.length-result.length,cases:result}
}

export const REVIEW_POLICY_VERSION='EVIDENCE_REVIEW_V1'

/** 仅确定性位置观察，不判断引用蕴含、不选择“最近引用”替代审阅者绑定。旧审阅报告不变。 */
export function citationAnchorObservations(packet,review){
  reviewEvidence(packet,review)
  const cases=review.cases.map(row=>{
    const c=packet.cases.find(c=>c.caseId===row.caseId),markers=[],pattern=/\[S([1-9]|[1-4][0-9]|50)\]/g
    let totalMarkers=0,match
    while((match=pattern.exec(c.answer))!==null){totalMarkers++;if(markers.length<2000)markers.push({id:`S${match[1]}`,start:match.index,end:match.index+match[0].length})}
    const uses=new Map(),assigned=new Set()
    for(const claim of row.claims)for(const s of claim.citationSpans){assigned.add(s.start);if(!uses.has(s.start))uses.set(s.start,new Set());uses.get(s.start).add(claim.claimId)}
    const claims=row.claims.map(claim=>{
      const selected=claim.citationSpans.map(s=>({id:s.text.slice(1,-1),start:s.start,end:s.end})),selectedPositions=new Set(selected.map(s=>s.start))
      const alternatives=markers.filter(m=>claim.citedSourceIds.includes(m.id)&&!selectedPositions.has(m.start))
      const before=selected.filter(m=>m.end<=claim.answerSpan.start),inside=selected.filter(m=>m.start<claim.answerSpan.end&&m.end>claim.answerSpan.start),after=selected.filter(m=>m.start>=claim.answerSpan.end)
      const repeatedBefore=before.filter(m=>alternatives.some(other=>other.id===m.id&&other.start>=claim.answerSpan.end))
      const shared=selected.filter(m=>(uses.get(m.start)?.size??0)>1)
      return {claimHash:hash(claim.claimId),claimRange:{start:claim.answerSpan.start,end:claim.answerSpan.end},selectedMarkers:selected,
        beforeClaimCount:before.length,overlappingClaimCount:inside.length,afterClaimCount:after.length,
        alternateSameIdCount:alternatives.length,alternativeCountComplete:totalMarkers<=2000,alternateSameIdMarkers:alternatives.slice(0,20),alternativesTruncated:alternatives.length>20,
        sharedMarkerCount:shared.length,codes:[...(repeatedBefore.length?['REPEATED_ID_BEFORE_CLAIM']:[]),...(shared.length?['SHARED_MARKER']:[])],
        automaticRebinding:false,semanticBindingVerified:false}
    })
    const unassigned=markers.filter(m=>!assigned.has(m.start))
    return {caseHash:hash(row.caseId),totalMarkers,inventoryTruncated:totalMarkers>2000,recognizedMarkerSyntax:'SINGLE_BRACKET_S1_TO_S50_ONLY',
      unassignedMarkerCount:totalMarkers>2000?null:unassigned.length,unassignedMarkers:unassigned.slice(0,100),unassignedListTruncated:unassigned.length>100,
      positionalWarningClaims:claims.filter(cl=>cl.codes.includes('REPEATED_ID_BEFORE_CLAIM')).length,claims}
  })
  return {anchorVersion:1,packetHash:packet.packetHash,reviewHash:hash(review),observationType:'DETERMINISTIC_POSITIONS_ONLY',
    qualityReleaseApproved:false,automaticRebinding:false,liveAuthorizationVerified:false,claimExtractionCompleteness:'NOT_VERIFIED',cases}
}
export const REVIEW_SYSTEM=readFileSync(new URL('../../polaris-ai/src/main/resources/knowledge/evidence-review-v1.txt',import.meta.url),'utf8').trim()

export function modelReviewInput(packet,caseId){
  validatePacket(packet);const c=packet.cases.find(c=>c.caseId===caseId)
  if(!c)fail('INVALID_CASE')
  if(c.sourceCompleteness!=='COMPLETE')fail('SOURCE_UNAVAILABLE')
  const content=JSON.stringify({question:c.query,answer:c.answer,sources:c.sources.map(s=>({id:s.citationId,documentId:s.documentId,indexVersion:s.indexVersion,chunkNo:s.chunkNo,locations:s.locations,truncated:s.truncated,text:s.text}))})
  if(content.length+REVIEW_SYSTEM.length>30000)fail('INPUT_LIMIT')
  return {policyVersion:REVIEW_POLICY_VERSION,system:REVIEW_SYSTEM,data:content,inputHash:hash({system:REVIEW_SYSTEM,data:content})}
}
function locate(value,needle,occurrence){
  text(needle,200000);if(!Number.isSafeInteger(occurrence)||occurrence<0||occurrence>1000)fail('INVALID_OCCURRENCE')
  let start=-1,from=0
  for(let i=0;i<=occurrence;i++){start=value.indexOf(needle,from);if(start<0)fail('QUOTE_OR_CLAIM_NOT_FOUND');from=start+1}
  const span={start,end:start+needle.length,text:needle};checkSpan(value,span);return span
}
export function decodeModelSuggestion(packet,caseId,raw){
  validatePacket(packet);text(raw,32768);if(Buffer.byteLength(raw,'utf8')>32768)fail('OUTPUT_LIMIT')
  let parsed;try{parsed=JSON.parse(raw)}catch{fail('INVALID_MODEL_JSON')}
  keys(parsed,['claims']);if(!Array.isArray(parsed.claims)||!parsed.claims.length||parsed.claims.length>50)fail('INVALID_MODEL_SCHEMA')
  const c=packet.cases.find(c=>c.caseId===caseId);if(!c)fail('INVALID_CASE')
  const claims=parsed.claims.map((cl,i)=>{
    keys(cl,['text','occurrence','claimType','citationBinding','citations','quotes','support','scopeFindings','rationale'])
    if(!Array.isArray(cl.citations)||cl.citations.length>50||!Array.isArray(cl.quotes)||cl.quotes.length>100)fail('INVALID_MODEL_SCHEMA')
    const citationSpans=cl.citations.map(s=>{keys(s,['id','occurrence']);if(!c.sources.some(p=>p.citationId===s.id))fail('INVALID_MODEL_CITATION');return locate(c.answer,`[${s.id}]`,s.occurrence)})
    const evidenceQuotes=cl.quotes.map(q=>{keys(q,['id','text','occurrence','purpose']);const source=c.sources.find(s=>s.citationId===q.id&&s.state==='AVAILABLE');if(!source)fail('INVALID_MODEL_QUOTE');return {citationId:q.id,purpose:q.purpose,span:locate(source.text,q.text,q.occurrence)}})
    return {claimId:`C${i+1}`,answerSpan:locate(c.answer,cl.text,cl.occurrence),claimType:cl.claimType,citationBinding:cl.citationBinding,citedSourceIds:[...new Set(cl.citations.map(s=>s.id))],citationSpans,evidenceQuotes,support:cl.support,scopeFindings:cl.scopeFindings,rationale:cl.rationale}
  })
  const review={reviewVersion:1,reviewerType:'MODEL_SUGGESTION',packetHash:packet.packetHash,cases:[{caseId,answerHash:c.answerHash,sourcesHash:c.sourcesHash,claims}]}
  return {review,report:reviewEvidence(packet,review)}
}

// Only fixed-size diagnostic metadata is retained; never copy arbitrary error/data objects.
export function sanitizeFrameClasses(value){
  const fields=['evaluatedFrames','textFrames','reasoningFrames','roleFrames','toolFrames','finishFrames','usageFrames','doneFrames','otherFrames','invalidFrames','oversizedFrames','budgetSkippedFrames']
  if(!object(value)||value.version!==1||fields.some(k=>!Number.isSafeInteger(value[k])||value[k]<0||value[k]>1000000000)
      ||value.evaluatedFrames>20000||fields.slice(1,10).some(k=>value[k]>value.evaluatedFrames))return null
  const exclusive=value.doneFrames+value.otherFrames+value.invalidFrames
  if(exclusive>value.evaluatedFrames||fields.slice(1,7).some(k=>value[k]>value.evaluatedFrames-exclusive))return null
  return Object.fromEntries(['version',...fields].map(k=>[k,value[k]]))
}
export function sanitizeUpstreamDiagnostics(value){
  if(!object(value)||value.version!==1)return null
  const flags=['instrumented','responseHeadersReceived','bodyCompleted','transportFailed','cancellationRequested','countersSaturated','frozen']
  const times=['firstRequestMs','headersMs','firstBodyByteMs','firstSseEventMs']
  const counts=['requestAttempts','bodyChunks','bodyBytes','sseEvents']
  if(flags.some(k=>typeof value[k]!=='boolean')
      ||times.some(k=>value[k]!==null&&(!Number.isSafeInteger(value[k])||value[k]<0||value[k]>3600000))
      ||counts.some(k=>!Number.isSafeInteger(value[k])||value[k]<0||value[k]>1000000000)
      ||(value.httpStatus!==null&&(!Number.isSafeInteger(value.httpStatus)||value.httpStatus<100||value.httpStatus>599)))return null
  const result=Object.fromEntries(['version',...flags,...times,...counts,'httpStatus'].map(k=>[k,value[k]]))
  if(Object.hasOwn(value,'frameClasses')){
    const classes=sanitizeFrameClasses(value.frameClasses)
    if(!classes)return null
    const classified=classes.evaluatedFrames+classes.oversizedFrames+classes.budgetSkippedFrames
    if(value.countersSaturated?classified<value.sseEvents:classified!==value.sseEvents)return null
    result.frameClasses=classes
  }
  return result
}

export function sanitizeDiagnostics(value){
  if(!object(value)||value.version!==1||typeof value.traceId!=='string'||!/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(value.traceId)
      ||![30000,60000].includes(value.waitTimeoutMs)||!['AWAITING_EVENTS','WAIT_BUDGET_EXPIRED','STREAM_COMPLETED','COMPLETION_MISSING','NON_STOP_COMPLETION','OUTPUT_LIMIT','REASONING_LIMIT','UNEXPECTED_EVENT','UPSTREAM_ERROR','INTERRUPTED'].includes(value.termination)
      ||typeof value.budgetExpired!=='boolean'||typeof value.completedEventReceived!=='boolean')return null
  const numeric=['elapsedMs','firstEventMs','firstTextMs','firstReasoningMs','textEvents','textCharacters','reasoningEvents','reasoningCharacters']
  const nullable=new Set(['firstEventMs','firstTextMs','firstReasoningMs'])
  if(numeric.some(k=>!(nullable.has(k)&&value[k]===null)&&(!Number.isSafeInteger(value[k])||value[k]<0||value[k]>3600000)))return null
  const result=Object.fromEntries(['version','traceId','waitTimeoutMs',...numeric,'completedEventReceived','budgetExpired','termination'].map(k=>[k,value[k]]))
  if(Object.hasOwn(value,'reviewMode')){
    if(!['MODEL_DEFAULT','DASHSCOPE_NON_THINKING_V1'].includes(value.reviewMode))return null
    result.reviewMode=value.reviewMode
  }
  if(Object.hasOwn(value,'upstream')){
    const upstream=value.upstream==null?null:sanitizeUpstreamDiagnostics(value.upstream)
    if(value.upstream!=null&&!upstream)return null
    result.upstream=upstream
  }
  return result
}

/** Explicit local QA runtime endpoint; no credential extraction or direct provider calls. */
export async function collectModelReviews(packet,options,{request=fetch,checkpoint=async()=>{},wait=ms=>new Promise(r=>setTimeout(r,ms))}={}){
  validatePacket(packet)
  const waitTimeoutMs=options.waitTimeoutMs??30000
  const reviewMode=options.reviewMode??'MODEL_DEFAULT'
  if(!['MODEL_DEFAULT','DASHSCOPE_NON_THINKING_V1'].includes(reviewMode))fail('INVALID_REVIEW_MODE')
  const url=new URL(options.base??'http://localhost:8081')
  if(!['http:','https:'].includes(url.protocol)||!['localhost','127.0.0.1','[::1]'].includes(url.hostname)||url.username||url.password||url.pathname!=='/'||url.search||url.hash||!options.token||!id(options.modelId)||!digest(options.modelHash)||options.confirmModelCalls!==true||!Number.isSafeInteger(options.maxCalls)||options.maxCalls<1||options.maxCalls>8||packet.cases.length>options.maxCalls||![30000,60000].includes(waitTimeoutMs))fail('INVALID_MODEL_OPTIONS')
  const rows=[],privateRows=[]
  for(const c of packet.cases){
    let dispatched=false,httpStatus=null,businessCode=null,phase='INPUT',diagnostics=null
    try{
      modelReviewInput(packet,c.caseId)
      if(rows.length)await wait(5200)
      await checkpoint({caseHash:hash(c.caseId),phase:'BEFORE_DISPATCH',attempted:rows.filter(r=>r.dispatched).length})
      dispatched=true;phase='RUNTIME'
      const response=await request(new URL(`/ai/chat/conversations/${c.conversationId}/messages/${c.messageId}/evidence-review`,url),{method:'POST',redirect:'error',headers:{'Platform-Token':options.token,'Content-Type':'application/json'},body:JSON.stringify({modelId:options.modelId,modelViewHash:options.modelHash,answerHash:c.answerHash,queryHash:c.queryHash,sourcePrefixes:c.sources.map(s=>({citationId:s.citationId,documentId:s.documentId,chunkNo:s.chunkNo,textHash:hash(s.text)})),modelConfirmed:true,waitTimeoutMs,...(options.reviewMode!=null?{reviewMode}:{})}),signal:AbortSignal.timeout(waitTimeoutMs+5000)})
      httpStatus=response.status
      if(!response.ok)fail('RUNTIME_REJECTED')
      const bytes=await readResponse(response,65536),payload=JSON.parse(bytes)
      businessCode=Number.isSafeInteger(payload?.code)&&payload.code>=100&&payload.code<=599?payload.code:null
      diagnostics=sanitizeDiagnostics(payload?.data?.diagnostics)
      if(diagnostics&&diagnostics.waitTimeoutMs!==waitTimeoutMs)fail('WAIT_BUDGET_MISMATCH')
      if((reviewMode!=='MODEL_DEFAULT'&&diagnostics?.reviewMode!==reviewMode)
          ||(diagnostics?.reviewMode!=null&&diagnostics.reviewMode!==reviewMode))fail('REVIEW_MODE_UNVERIFIED')
      if(waitTimeoutMs===60000&&payload?.code===200&&!diagnostics)fail('WAIT_BUDGET_UNVERIFIED')
      if(payload.code!==200||payload.data?.state!=='COMPLETED'||payload.data?.policyVersion!==REVIEW_POLICY_VERSION||payload.data.answerHash!==c.answerHash||payload.data.queryHash!==c.queryHash)fail('RUNTIME_REJECTED')
      phase='OUTPUT'
      // Retain bounded candidate text privately even if exact-schema validation fails; never retry to replace it.
      if(typeof payload.data.suggestion!=='string'||Buffer.byteLength(payload.data.suggestion,'utf8')>32768)fail('OUTPUT_LIMIT')
      const candidate={caseId:c.caseId,rawSuggestion:payload.data.suggestion,state:'CANDIDATE_UNVERIFIED'}
      privateRows.push(candidate)
      const decoded=decodeModelSuggestion(packet,c.caseId,payload.data.suggestion)
      candidate.state='STRUCTURE_VALID';candidate.review=decoded.review;candidate.report=decoded.report
      const u=payload.data.usage
      const usage=u?Object.fromEntries(['inputTokens','outputTokens','totalTokens'].map(k=>[k,Number.isSafeInteger(u[k])&&u[k]>=0?u[k]:null])):null
      rows.push({caseHash:hash(c.caseId),dispatched:true,state:'COMPLETED',suggestionHash:hash(payload.data.suggestion),report:decoded.report,anchorObservations:citationAnchorObservations(packet,decoded.review),usage,diagnostics})
      await checkpoint({caseHash:hash(c.caseId),phase:'COMPLETED',attempted:rows.filter(r=>r.dispatched).length})
    }catch{
      rows.push({caseHash:hash(c.caseId),dispatched,state:'NOT_REVIEWED',reason:phase==='INPUT'?'INPUT_UNAVAILABLE_OR_LIMIT':phase==='OUTPUT'?'OUTPUT_CONTRACT_INVALID':'RUNTIME_UNVERIFIED',httpStatus,businessCode,diagnostics})
      await checkpoint({caseHash:hash(c.caseId),phase:'STOPPED',attempted:rows.filter(r=>r.dispatched).length});break
    }
  }
  return {report:{reviewVersion:1,packetHash:packet.packetHash,modelId:options.modelId,modelHash:options.modelHash,waitTimeoutMs,reviewMode,reviewerType:'MODEL_SUGGESTION',modelRequests:rows.filter(r=>r.dispatched).length,providerHttpRequests:null,providerCost:null,retries:0,qualityReleaseApproved:false,total:packet.cases.length,completed:rows.filter(r=>r.state==='COMPLETED').length,notRun:packet.cases.length-rows.length,cases:rows},privateRows}
}
async function readResponse(response,limit){const reader=response.body.getReader();let total=0;const chunks=[];try{while(true){const r=await reader.read();if(r.done)break;total+=r.value.length;if(total>limit)fail('RESPONSE_LIMIT');chunks.push(Buffer.from(r.value))}}finally{await reader.cancel()}return Buffer.concat(chunks).toString('utf8')}
async function readJson(path){const file=await open(resolve(path),'r');try{if((await file.stat()).size>8*1024*1024)fail('FILE_LIMIT');return JSON.parse(await file.readFile('utf8'))}finally{await file.close()}}
export async function writePrivate(path,value){
  const parent=await stat(dirname(resolve(path)))
  if(!parent.isDirectory()||(parent.mode&0o077)!==0)fail('PRIVATE_DIRECTORY_REQUIRED')
  const file=await open(resolve(path),'wx',0o600);try{await file.writeFile(JSON.stringify(value,null,2))}finally{await file.close()}
}
export async function main(args){
  const [command,...pairs]=args,opts={}
  if(pairs.length%2)fail('INVALID_ARGUMENTS')
  const allowed={adapt:['--format','--packet','--report','--manifest','--out'],review:['--packet','--decisions','--out'],anchors:['--packet','--decisions','--out'],model:['--packet','--model-id','--model-hash','--confirm-model-calls','--max-calls','--wait-ms','--review-mode','--base','--out','--private-out']}
  for(let i=0;i<pairs.length;i+=2){if(!allowed[command]?.includes(pairs[i])||Object.hasOwn(opts,pairs[i]))fail('INVALID_ARGUMENTS');opts[pairs[i]]=pairs[i+1]}
  if(!allowed[command]||!opts['--out']||!opts['--packet'])fail('INVALID_ARGUMENTS')
  if(command==='adapt'){const packet=adaptRecorded({format:opts['--format'],packet:await readJson(opts['--packet']),report:await readJson(opts['--report']),...(opts['--manifest']?{manifest:await readJson(opts['--manifest'])}:{})});await writePrivate(opts['--out'],packet);return {adapted:packet.cases.length,packetHash:packet.packetHash}}
  const packet=validatePacket(await readJson(opts['--packet']))
  if(command==='review'){const report=reviewEvidence(packet,await readJson(opts['--decisions']));await writePrivate(opts['--out'],report);return {reviewedCases:report.reviewedCases,qualityReleaseApproved:false}}
  if(command==='anchors'){const report=citationAnchorObservations(packet,await readJson(opts['--decisions']));await writePrivate(opts['--out'],report);return {cases:report.cases.length,qualityReleaseApproved:false}}
  if(opts['--confirm-model-calls']!=='yes'||!opts['--private-out'])fail('MODEL_CONFIRMATION_REQUIRED')
  // Reserve both paths before any network call; do not leave room for clobber or symlink races.
  if(resolve(opts['--out'])===resolve(opts['--private-out']))fail('OUTPUT_CONFLICT')
  for(const p of [opts['--out'],opts['--private-out']]){const parent=await stat(dirname(resolve(p)));if((parent.mode&0o077)!==0)fail('PRIVATE_DIRECTORY_REQUIRED')}
  const out=await open(resolve(opts['--out']),'wx',0o600)
  try{const priv=await open(resolve(opts['--private-out']),'wx',0o600)
    try{
      const result=await collectModelReviews(packet,{base:opts['--base'],token:process.env.POLARIS_QA_TOKEN,modelId:opts['--model-id'],modelHash:opts['--model-hash'],confirmModelCalls:true,maxCalls:Number(opts['--max-calls']),...(opts['--wait-ms']?{waitTimeoutMs:Number(opts['--wait-ms'])}:{}),...(opts['--review-mode']?{reviewMode:opts['--review-mode']}:{})},{checkpoint:async p=>{await out.truncate(0);await out.write(JSON.stringify({inProgress:true,...p}),0,'utf8');await out.sync()}})
      await out.truncate(0);await out.write(JSON.stringify(result.report,null,2),0,'utf8');await priv.writeFile(JSON.stringify(result.privateRows,null,2));return {completed:result.report.completed,modelRequests:result.report.modelRequests,qualityReleaseApproved:false}
    }finally{await priv.close()}
  }finally{await out.close()}
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href)main(process.argv.slice(2)).then(result=>console.log(JSON.stringify(result))).catch(()=>{console.error('证据审阅未完成；请检查私有输入、绑定摘要和显式预算。未输出正文、错误详情或凭证。');process.exitCode=1})
