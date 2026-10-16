// 固定合成资料；问题和标注不导出为待入库文档，避免把答案题库索引进去。
export const version = 'SYNTHETIC_RETRIEVAL_V1'

const facts = [
  ['archive', '星河归档中心', 'CX-101', '归档日为每月第三个星期三', '资料保留期为九十天'],
  ['visit', '青岚访客中心', 'FK-208', '访客登记开放时间为工作日九点至十六点', '预约至少提前两个工作日'],
  ['device', '晨光设备中心', 'SB-309', '设备巡检安排在每周二上午', '备用电池更换周期为十八个月'],
  ['training', '云帆培训中心', 'PX-410', '新成员培训安排在每月第二个星期五', '每班最多接收十二人'],
  ['backup', '白鹭备份中心', 'BF-512', '全量备份安排在每周日凌晨两点', '离线副本保留八周'],
  ['sample', '蓝鲸样本中心', 'YP-613', '样本接收时间为工作日十三点至十七点', '常温样本保存七天'],
  ['meeting', '橙湾会议中心', 'HY-714', '预约释放时间为每周一上午十点', '会议室最大容量为二十四人'],
  ['repair', '松塔维修中心', 'WX-815', '集中维护窗口为每月最后一个星期六', '报修响应时限为四个工作小时'],
  ['book', '紫藤图书中心', 'TS-916', '盘点安排在每季度第一个星期一', '普通借阅期限为二十一天'],
  ['delivery', '银杉配送中心', 'PS-017', '集中发运时间为工作日十五点', '签收回执保留六个月']
]

export const documents = facts.map(([key, name, code, first, second], index) => ({
  key, fileName: `qa-quality-${key}.md`,
  content: `# ${name}合成规则\n\n以下全部为虚构验收资料，不代表真实企业制度。\n\n服务编号：${code}。${first}。${second}。\n\n` +
    (index < 5
      ? `## 配额表\n\n| 档位 | 配额单位 | 每日上限 |\n| --- | --- | --- |\n| 基础档 | 次 | ${10 + index * 10} |\n| 扩展档 | 次 | ${25 + index * 10} |\n`
      : `## 联合办理\n\n${name}的联合办理伙伴为${facts[index - 5][1]}，办理时同时遵守伙伴的服务规则。\n`)
}))

const evidence = (i, text) => ({documentKey: facts[i][0], text})
const directQuestions = [
  ['星河归档中心每月哪天归档？', '星河归档中心的资料保留多久？'],
  ['青岚访客中心什么时候开放登记？', '青岚访客中心预约需要提前多久？'],
  ['晨光设备中心什么时候巡检？', '晨光设备中心多久更换备用电池？'],
  ['云帆培训中心何时安排新成员培训？', '云帆培训中心每班人数上限是多少？'],
  ['白鹭备份中心何时进行全量备份？', '白鹭备份中心离线副本保留多久？'],
  ['蓝鲸样本中心什么时候接收样本？', '蓝鲸样本中心常温样本保存多久？'],
  ['橙湾会议中心何时释放预约？', '橙湾会议中心会议室能容纳多少人？'],
  ['松塔维修中心维护窗口是什么时候？', '松塔维修中心报修响应时限是多少？'],
  ['紫藤图书中心什么时候盘点？', '紫藤图书中心普通借阅期限是多久？'],
  ['银杉配送中心什么时候集中发运？', '银杉配送中心签收回执保留多久？']
]
const synonymQuestions = [
  '星河归档中心的材料在哪个日期统一入档？', '我去青岚访客中心办理来访登记，几点可以办？',
  '晨光设备中心例行检查设备排在哪天？', '刚加入云帆培训中心，入职课程通常何时开？',
  '白鹭备份中心的完整数据副本几点制作？', '蓝鲸样本中心收件窗口下午几点营业？',
  '橙湾会议中心什么时候放出可订的会议名额？', '松塔维修中心每月集中检修选哪一天？',
  '紫藤图书中心多久清点一次藏书，具体哪天？', '银杉配送中心每天几点统一寄出？'
]
const unknownQuestions = [
  '星河归档中心的财务负责人姓名是什么？', '青岚访客中心停车每小时多少钱？',
  '晨光设备中心的设备采购预算是多少？', '云帆培训中心是否提供住宿及其价格？',
  '白鹭备份中心使用哪家云服务商？', '蓝鲸样本中心冷冻样本的保存期限是什么？',
  '橙湾会议中心投影仪的分辨率是多少？', '松塔维修中心的夜间紧急联系电话是什么？',
  '紫藤图书中心电子书会员每年多少钱？', '银杉配送中心国际件的关税由谁承担？'
]

const groups = {
  DIRECT: facts.flatMap((row, i) => [3, 4].map((field, j) => ({
    query: directQuestions[i][j], evidence: [evidence(i, row[field])]
  }))),
  SYNONYM: facts.map((row, i) => ({query: synonymQuestions[i], evidence: [evidence(i, row[3])]})),
  TERM: facts.map((row, i) => ({query: directQuestions[i][1].replace(row[1], `服务编号 ${row[2]} 对应中心`),
    evidence: [evidence(i, `服务编号：${row[2]}`), evidence(i, row[4])]})),
  TABLE_CROSS: facts.map((row, i) => i < 5 ? {
    query: `${row[1]}的基础档和扩展档每日上限分别是多少次？`,
    evidence: [evidence(i, `基础档 | 次 | ${10 + i * 10}`), evidence(i, `扩展档 | 次 | ${25 + i * 10}`)]
  } : {
    query: `${row[1]}的联合办理伙伴是谁，伙伴的首项时间安排是什么？`,
    evidence: [evidence(i, `联合办理伙伴为${facts[i - 5][1]}`), evidence(i - 5, facts[i - 5][3])]
  }),
  NO_ANSWER: unknownQuestions.map(query => ({query, evidence: []}))
}

export const cases = Object.entries(groups).flatMap(([category, rows]) => rows.map((row, i) => ({
  id: `${category.toLowerCase()}-${String(i + 1).padStart(2, '0')}`, category,
  split: i < (category === 'DIRECT' ? 12 : 7) ? 'dev' : 'holdout', ...row
})))

export const suite = {version, documents, cases}
