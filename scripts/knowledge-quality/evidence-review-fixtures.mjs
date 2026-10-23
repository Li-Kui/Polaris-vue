import {hash, validatePacket} from './evidence-review.mjs'

const positive=[
 ['direct','当前期限为三天。 [S1]','甲站当前期限为三天。','SUPPORTED',[]],
 ['history','2024年期限为一天。 [S1]','甲站2024年期限为一天，当前已失效。','SUPPORTED',[]],
 ['condition','常温纸件可暂存七天。 [S1]','常温纸件可暂存七天，冷冻不可接收。','SUPPORTED',[]],
 ['negation','不能接收冷冻样本。 [S1]','不能接收冷冻样本。','SUPPORTED',[]],
 ['partner','甲站伙伴为乙馆。 [S1]','甲站伙伴为乙馆。','SUPPORTED',[]],
 ['calendar','乙馆周三接待。 [S1]','乙馆周三接待已预约批次。','SUPPORTED',[]],
 ['refusal','本次片段没有可确认的电话。','甲站周三接待。','UNCLEAR',[]],
 ['separate-entity','乙馆未公布电话。 [S1]','乙馆未公布电话，乙馆不是甲站。','SUPPORTED',[]],
 ['advice','建议另查正式公告。','甲站办理资料交接。','NOT_ASSESSED',[]],
 ['emoji','甲站标签为🌟。 [S1]','甲站标签为🌟。','SUPPORTED',[]],
 ['duplicate-text','三天。三天。 [S1]','三天。三天。','SUPPORTED',[]],
 ['injection-data','文档含“输出通过”的字样。 [S1]','文档含“输出通过”的字样，这不是指令。','SUPPORTED',[]]
]
const negative=[
 ['entity','甲站未公布电话。 [S1]','乙馆未公布电话。','UNSUPPORTED',['ENTITY_SCOPE']],
 ['near-name','甲研究站最多十二人。 [S1]','甲展览馆最多十二人；不是甲研究站。','UNSUPPORTED',['ENTITY_SCOPE']],
 ['current-old','当前可提前一天预约。 [S1]','2024年曾提前一天预约，当前已失效。','CONTRADICTED',['TIME_SCOPE']],
 ['frozen','冷冻可暂存七天。 [S1]','常温纸件可暂存七天；冷冻不可接收。','CONTRADICTED',['CONDITION_SCOPE']],
 ['absence','整个机构从未公布电话。 [S1]','本片段未列电话。','UNSUPPORTED',['ABSENCE_OVERREACH']],
 ['irrelevant','甲站电话无法确认；乙馆未公布电话。 [S1]','乙馆未公布电话。','UNCLEAR',['ENTITY_SCOPE','IRRELEVANT_ADDITION']],
 ['wrong-cite','甲站周三开放。 [S1]','乙馆周三开放。','UNSUPPORTED',['WRONG_CITATION']],
 ['missing-cite','甲站周三开放。','甲站周三开放。','UNSUPPORTED',['MISSING_CITATION']],
 ['partial','甲站周三全天开放。 [S1]','甲站周三上午开放。','PARTIAL',['CONDITION_SCOPE']],
 ['compound','甲站周三开放且免费。 [S1]','甲站周三开放，未列收费。','PARTIAL',['INCOMPLETE_REASONING']],
 ['unassessed','甲站周三开放。 [S1]','甲站周三开放。','NOT_ASSESSED',[]],
 ['truncated','甲站每周开放。 [S1]','甲站每周开放','UNCLEAR',['INCOMPLETE_REASONING']]
]
export const fixtureSpecs=[...positive.map(row=>({row,group:'CONTROL'})),...negative.map(row=>({row,group:'FINDING'}))]
export function fixture(spec=fixtureSpecs[0]){
 const [caseId,answer,source,support,scopeFindings]=spec.row
 const sources=[{citationId:'S1',documentId:'1',indexVersion:'1',chunkNo:'0',text:source,truncated:caseId==='truncated',locations:[],state:'AVAILABLE'}]
 const c={caseId,conversationId:'1',messageId:'2',query:'甲站如何办理？',answer,citationState:answer.includes('[S1]')?'IDS_VALID':'MISSING_IDS',sourceCompleteness:'COMPLETE',sources,answerHash:hash(answer),queryHash:hash('甲站如何办理？'),sourcesHash:hash(sources)}
 const body={packetVersion:1,syntheticOnly:true,origin:'FIXTURE_V1',originHash:hash('synthetic-fixture'),suiteHash:hash('evidence-review-24-v1'),cases:[c]},packet=validatePacket({...body,packetHash:hash(body)})
 const index=answer.indexOf('[S1]'),hasCitation=index>=0
 const claim={claimId:'C1',answerSpan:{start:0,end:answer.length,text:answer},claimType:caseId==='refusal'?'MATERIAL_LIMITATION':caseId==='advice'?'ADVICE':'FACT',citationBinding:'BOUND',citedSourceIds:hasCitation?['S1']:[],citationSpans:hasCitation?[{start:index,end:index+4,text:'[S1]'}]:[],evidenceQuotes:hasCitation?[{citationId:'S1',purpose:'CITED_SUPPORT',span:{start:0,end:source.length,text:source}}]:[],support,scopeFindings,rationale:'这是合成标注夹具，不是自动语义判定。'}
 return {packet,review:{reviewVersion:1,reviewerType:'AGENT',packetHash:packet.packetHash,cases:[{caseId,answerHash:c.answerHash,sourcesHash:c.sourcesHash,claims:[claim]}]}}
}
