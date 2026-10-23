// Display labels only: schema keys, submitted values and exported data stay intact.
const labels = {
  messages: '对话消息', message: '消息', prompt: '提示词', input: '输入内容',
  query: '查询内容', question: '问题', text: '文本内容', content: '内容',
  systemprompt: '系统提示词', additionalsystemprompt: '补充系统提示词',
  structuredoutputschema: '结构化输出格式', maxwaitseconds: '最长等待时间（秒）',
  model: '模型', modelid: '模型', temperature: '随机程度', maxtokens: '最大输出长度',
  topp: '采样范围', stream: '流式输出', mode: '生成方式', size: '图片尺寸',
  count: '数量', n: '数量', image: '图片', images: '图片列表', imageurl: '图片网址',
  imageurls: '图片网址列表', url: '网址', file: '文件', files: '文件列表',
  filename: '文件名', name: '名称', title: '标题', description: '说明',
  role: '角色', output: '输出内容', result: '结果', data: '数据',
  status: '状态', type: '类型', id: '编号', total: '总数', value: '数值',
  label: '名称', category: '分类', date: '日期', time: '时间', month: '月份',
  year: '年份', revenue: '收入', sales: '销售额', amount: '金额', price: '价格',
  score: '评分', summary: '摘要', items: '项目列表', records: '数据记录',
  limit: '最多返回条数', page: '页码', pagesize: '每页条数', keyword: '关键词',
  keywords: '关键词', language: '语言', format: '输出格式', params: '参数',
  options: '选项', config: '配置', payload: '处理参数', seed: '随机种子'
}
const normalize = value => String(value).replace(/[\s_-]/g, '').toLowerCase()

export function workflowDisplayLabel(key, title = '') {
  // A configured business title takes precedence; translate known technical titles too.
  const text = String(title || key || '')
  const normalized = normalize(text)
  return Object.hasOwn(labels, normalized) ? labels[normalized] : text
}

export function workflowOptionLabel(value) {
  const options = {text_to_image: '文生图', image_to_image: '图生图', background_replacement: '更换背景', background_replace: '更换背景',
    user: '用户', assistant: '助手', system: '系统', auto: '自动',
    zh: '中文', 'zh-CN': '简体中文', en: '英文'}
  return Object.hasOwn(options, String(value)) ? options[String(value)] : String(value)
}

export function workflowDisplayPath(path, schema) {
  let current = schema
  const parts = []
  for (const match of String(path).matchAll(/\.([^.[\]]+)|\[(\d+)\]/g)) {
    if (match[1]) {
      current = current?.properties?.[match[1]]
      parts.push(workflowDisplayLabel(match[1], current?.title))
    } else {
      current = current?.items
      parts.push(`第 ${Number(match[2]) + 1} 项`)
    }
  }
  return parts.join(' / ') || '输入内容'
}
