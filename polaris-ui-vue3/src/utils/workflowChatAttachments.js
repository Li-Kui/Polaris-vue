export const chatAttachmentAccept = '.pdf,.docx,.xlsx,.xls,.txt,.md,.json,.csv,.xml,.png,.jpg,.jpeg,.gif,.bmp'
export const chatAttachmentLimit = 5

export function chatAttachmentError(file, count) {
  if (count >= chatAttachmentLimit) return '每条消息最多上传 5 个附件'
  if (!file || !file.size) return '不能上传空文件'
  if (file.size > 10 * 1024 * 1024) return '附件最大 10MB'
  const extension = '.' + (file.name || '').split('.').pop().toLowerCase()
  if (!chatAttachmentAccept.split(',').includes(extension)) return '不支持该文件类型，请上传图片、PDF、Word、Excel或文本'
  return ''
}

export function chatAttachmentSize(size) {
  return size >= 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.ceil(size / 1024))} KB`
}

export async function resolveChatAttachmentDownload(data, contentDisposition) {
  // 原附件（包括 JSON 文件）带有下载响应头；业务错误没有，不能当原文件保存。
  if (!(data instanceof Blob) || contentDisposition || !/json/i.test(data.type)) return data
  let result
  try { result = JSON.parse(await data.text()) }
  catch (error) { return data }
  if (result?.code != null && result.code !== 200) throw new Error(result.msg || '附件下载失败，请重新上传')
  return data
}

export function chatExecutionInput(messages) {
  const history = messages.filter(message => message.state === 'done')
  return {
    input: {
      messages: history.map(message => ({ role: message.role === 'bot' ? 'assistant' : message.role, content: message.content })),
      currentMessage: history.at(-1)?.content || ''
    },
    attachments: history.flatMap((message, messageIndex) => message.attachments?.length
      ? [{ messageIndex, tokens: message.attachments.map(file => file.token) }] : [])
  }
}
