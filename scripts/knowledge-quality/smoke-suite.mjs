// 既有 QA 合成库的小规模接通验证；不能替代 60 条固定集。
const documentKey = 'existing-qa'
export const suite = {
  version: 'EXISTING_QA_SMOKE_V1',
  documents: [{key: documentKey, fileName: 'polaris-qa-knowledge-20261012.txt',
    content: '合成知识助手验收资料\n以下内容全部为虚构测试数据，不代表任何真实企业制度。\n' +
      '星河演示中心的资料归档日是每月第三个星期三。合成项目代号为晨星，资料保留期为九十天。\n' +
      '验收问题：星河演示中心的资料归档日是什么时候？\n标准事实：每月第三个星期三。'}],
  cases: [
    {id: 'smoke-direct', category: 'DIRECT', split: 'dev', query: '星河演示中心的资料归档日是什么时候？',
      evidence: [{documentKey, text: '资料归档日是每月第三个星期三'}]},
    {id: 'smoke-synonym', category: 'SYNONYM', split: 'dev', query: '星河演示中心的资料通常能留存多久？',
      evidence: [{documentKey, text: '资料保留期为九十天'}]},
    {id: 'smoke-term', category: 'TERM', split: 'dev', query: '星河演示中心的合成项目代号是什么？',
      evidence: [{documentKey, text: '合成项目代号为晨星'}]},
    {id: 'smoke-no-answer', category: 'NO_ANSWER', split: 'dev', query: '星河演示中心停车每小时多少钱？', evidence: []}
  ]
}
