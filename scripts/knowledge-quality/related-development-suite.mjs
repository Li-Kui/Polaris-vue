// C1 独立合成开发集，不是 B3.22 的旧保留集，不代表业务语义验收。
export const suite = Object.freeze({
  id: 'c1-explicit-related-dev-v1',
  documents: [
    {key: 'handover', fileName: 'qa-c1-handover.md', content: '# 沧岚资料站现行交接规则\n\n本文件是虚构的 2027 年现行资料。沧岚资料站办理常规纸质资料交接，应至少提前四个工作日预约；工作日采用白榆协作中心的服务日历。预约申请须注明资料箱数量与交接编号。沧岚资料站不自行定义协作中心的停服日，计算预约截止日时须联合查阅服务日历。旧试运行规则不能作为现行期限依据。'},
    {key: 'cold', fileName: 'qa-c1-cold.md', content: '# 沧岚资料站冷链交接规则\n\n本文件为虚构的现行资料。冷链资料交接应至少提前六个服务日预约，服务日采用白榆协作中心的服务日历。运输箱须使用青色封签，交接现场核对封签编号。运输温度范围未在这份资料中给出，不可根据其他业务猜测。'},
    {key: 'access', fileName: 'qa-c1-access.md', content: '# 沧岚资料站来访核验规则\n\n本文件为虚构的现行资料。资料交接来访者应携带蓝色访客证，并在预约时提交交接编号。可以预约的服务日采用白榆协作中心的服务日历。访客证只证明已登记，不替代交接资料的审批。本文件未公布夜间联系电话。'},
    {key: 'billing', fileName: 'qa-c1-billing.md', content: '# 沧岚资料站结算规则\n\n本文件为虚构的现行资料。已完成的常规资料交接，结算申请应在交接后两个服务日内提交，服务日采用白榆协作中心的服务日历。申请须附交接编号与资料箱清单。该期限不是资料交接的预约期限。'},
    {key: 'calendar', fileName: 'qa-c1-calendar.md', content: '# 白榆协作中心服务日历\n\n本文件为虚构的 2027 年现行服务日历，供沧岚资料站常规交接、冷链交接、来访预约及结算共同使用。通常周一至周五为服务日；2027 年 4 月 8 日为演练停服日，不计入服务日。周末不提供常规交接服务。本日历未公布该年度其余临时停服日期，不能据此声称全年停服日已经列全。'},
    {key: 'retired', fileName: 'qa-c1-retired.md', content: '# 沧岚资料站失效试运行记录\n\n本文件为虚构的 2025 年历史资料，已经失效。试运行期间常规资料交接只需提前一个工作日预约，冷链资料交接只需提前两个工作日预约。历史安排不能作为 2027 年办理期限依据，现行规则另见对应交接规则和白榆协作中心服务日历。'}
  ],
  cases: [
    {id: 'handover-calendar', query: '沧岚资料站2027年常规资料交接要提前多久预约？白榆协作中心4月8日算服务日吗？不要用试运行期限。', related: ['calendar'], evidence: ['handover', 'calendar']},
    {id: 'cold-calendar', query: '沧岚资料站冷链资料交接的预约期限和封签颜色是什么？白榆协作中心2027年4月8日是否计入服务日？', related: ['calendar'], evidence: ['cold', 'calendar']},
    {id: 'access-calendar', query: '去沧岚资料站交接资料应带什么访客证？白榆协作中心2027年4月8日能否作为预约服务日？', related: ['calendar'], evidence: ['access', 'calendar']},
    {id: 'billing-calendar', query: '沧岚资料站完成交接后多久提交结算申请？白榆协作中心2027年4月8日算结算服务日吗？', related: ['calendar'], evidence: ['billing', 'calendar']},
    {id: 'two-rule-documents', query: '沧岚资料站2027年常规和冷链交接分别需要提前几个服务日预约？两类期限不要混用。', related: ['cold'], evidence: ['handover', 'cold']},
    {id: 'unknown-calendar', query: '沧岚资料站和白榆协作中心2027年全年所有临时停服日是什么？现有服务日历是否列全？', related: ['calendar'], evidence: ['calendar'], noCompleteAnswer: true}
  ],
  generationCaseIds: ['handover-calendar', 'two-rule-documents'],
  budget: {documents: 6, contentCharacters: 6000, retrievalRequests: 12, answerRequests: 2}
})
