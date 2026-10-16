import {open, readFile} from 'node:fs/promises'
import {resolve} from 'node:path'
import {pathToFileURL} from 'node:url'
import {generationHash, pilotIds, reviewGeneration} from './generation.mjs'

const fail=code=>{throw new Error(code)}
const complete=bundle=>{
  const review=reviewGeneration(bundle.report,bundle.manifest,bundle.packet,bundle.decisions)
  if(!bundle.report.allSelectedCompleted || bundle.report.summary.completed!==pilotIds.length
      || bundle.report.summary.errors || bundle.report.summary.notRun || bundle.report.summary.stoppedEmpty
      || review.reviewed!==pilotIds.length || review.unreviewed)fail('COMPLETE_GENERATION_REVIEW_REQUIRED')
  const ids=bundle.report.cases.map(row=>row.conversationId)
  if(new Set(ids).size!==ids.length)fail('INDEPENDENT_CONVERSATIONS_REQUIRED')
  return review
}

// 仅离线配对，不读取凭证、不访问网络、不修改知识库，也不将语义审阅伪装成自动评分。
export function compareGeneration(before,after) {
  const left=complete(before),right=complete(after)
  if((before.manifest.retrievalTopK??5)!==5 || after.manifest.retrievalTopK!==10
      || generationHash({...before.manifest,retrievalTopK:5})!==generationHash({...after.manifest,retrievalTopK:5})
      || before.report.suiteHash!==after.report.suiteHash || before.report.mode!==after.report.mode
      || left.reviewerType!==right.reviewerType || before.report.runId===after.report.runId)fail('INCOMPATIBLE_GENERATION_CONTROL')
  const oldConversations=new Set(before.report.cases.map(row=>row.conversationId))
  if(after.report.cases.some(row=>oldConversations.has(row.conversationId)))fail('REUSED_CONTROL_CONVERSATION')
  const rows=before.report.cases.map(row=>{
    const next=after.report.cases.find(r=>r.caseId===row.caseId)
    const projection=(r,review)=>({status:r.status,label:review.cases.find(c=>c.caseId===r.caseId).label,
      sourceCount:r.sourceCount,citationState:r.citationState,coveredEvidenceCount:r.coveredEvidenceCount,
      expectedEvidenceCount:r.expectedEvidenceCount,completeEvidence:r.completeEvidence,evidenceCoverage:r.evidenceCoverage})
    return {caseId:row.caseId,category:row.category,before:projection(row,left),after:projection(next,right)}
  })
  const positive=rows.filter(row=>row.before.expectedEvidenceCount>0)
  const evidence=side=>({answerableCases:positive.length,
    completeEvidenceCases:positive.filter(row=>row[side].completeEvidence).length,
    meanEvidenceCoverage:positive.reduce((total,row)=>total+row[side].evidenceCoverage,0)/positive.length})
  return {comparisonVersion:1,mode:before.report.mode,beforeRunId:before.report.runId,afterRunId:after.report.runId,
    beforeManifestHash:before.report.manifestHash,afterManifestHash:after.report.manifestHash,
    beforeReportHash:generationHash(before.report),afterReportHash:generationHash(after.report),
    beforeReviewHash:generationHash(left),afterReviewHash:generationHash(right),
    changedParameter:{name:'retrievalTopK',before:5,after:10},minScore:0.5,selectedCases:rows.length,
    reviewerType:left.reviewerType,holdoutExecuted:false,notSelectedDevCases:28,
    evidence:{before:evidence('before'),after:evidence('after')},labels:{before:left.counts,after:right.counts},
    labelChanges:rows.filter(row=>row.before.label!==row.after.label).map(row=>({caseId:row.caseId,before:row.before.label,after:row.after.label})),
    qualityReleaseApproved:false,causalImprovementProven:false,
    limitations:['只比较固定12条开发题，不是完整开发集、保留集或业务准确率',
      '语义标签来自声明的审阅者；AGENT不是人工签字，不按关键词、相似度或合法编号自动判正确',
      '每个参数各一次且非随机交错，供应商权重/线路与完整运行时策略未冻结，不能证明因果收益',
      '证据覆盖按实际入选片段的文档ID与完整标注字符串检查，不是语义引用支持率',
      '本工具只读本地文件，不能证明参数已恢复、原文物理摘要未变或测量精确费用；这些需另行核验'],cases:rows}
}

export async function main(args) {
  const options={}
  const required=['--before-manifest','--before-report','--before-review-packet','--before-decisions',
    '--after-manifest','--after-report','--after-review-packet','--after-decisions','--out']
  if(args.length!==required.length*2)fail('INVALID_ARGUMENTS')
  for(let i=0;i<args.length;i+=2){if(!required.includes(args[i]) || options[args[i]] || !args[i+1])fail('INVALID_ARGUMENTS');options[args[i]]=args[i+1]}
  const bundle=async prefix=>{
    const values={}
    for(const key of ['manifest','report','review-packet','decisions'])values[key==='review-packet'?'packet':key]=JSON.parse(await readFile(options[`--${prefix}-${key}`],'utf8'))
    return values
  }
  const result=compareGeneration(await bundle('before'),await bundle('after'))
  const output=await open(resolve(options['--out']),'wx',0o600)
  try{await output.writeFile(JSON.stringify(result,null,2))}finally{await output.close()}
  return result
}
if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href)main(process.argv.slice(2)).then(result=>{
  console.log(JSON.stringify({selectedCases:result.selectedCases,reviewerType:result.reviewerType,labelChanges:result.labelChanges,qualityReleaseApproved:false}))
}).catch(()=>{console.error('生成对照未完成；请检查完整报告、原始审阅包、审阅绑定、同一配置及独立输出文件。');process.exitCode=1})
