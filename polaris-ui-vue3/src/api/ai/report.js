import request from '@/utils/request'
import {getToken} from '@/utils/auth'

/**
 * 归档保存分析报告
 */
export function saveReport(data) {
  return request({
    url: '/ai/report',
    method: 'post',
    data: data
  })
}

/**
 * 条件分页查询报告列表
 */
export function listReports(query) {
  return request({
    url: '/ai/report/list',
    method: 'get',
    params: query
  })
}

/**
 * 获取报告详情
 */
export function getReport(id) {
  return request({
    url: '/ai/report/' + id,
    method: 'get'
  })
}

/**
 * 删除报告
 */
export function delReport(id) {
  return request({
    url: '/ai/report/' + id,
    method: 'delete'
  })
}

/**
 * 修改更新分析报告
 */
export function updateReport(data) {
  return request({
    url: '/ai/report',
    method: 'put',
    data: data
  })
}

/**
 * AI 智能体美化重塑报告 (同步后备)
 */
export function refineReport(content) {
  return request({
    url: '/ai/report/refine',
    method: 'post',
    data: { content },
    timeout: 120000
  })
}

/**
 * AI 智能体美化重塑报告 (SSE 流式)
 */
export async function refineReportStream(content, { onChunk, onComplete, onError }) {
  try {
    const url = (import.meta.env.VITE_APP_BASE_API || '') + '/ai/report/refine-stream'
    console.log('>>> [SSE 发起请求目标]:', url)
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Authorization': 'Bearer ' + getToken()
      },
      body: JSON.stringify({ content })
    })

    console.log('>>> [SSE 响应 HTTP 状态码]:', response.status)
    if (!response.ok) {
      const errorText = await response.text()
      console.error('>>> [SSE 响应错误内容]:', errorText)
      throw new Error(`HTTP error! status: ${response.status}, text: ${errorText}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let fullText = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      console.log('>>> [SSE 接收到数据切片]:', chunk)
      // 清理 SSE "data:" 标签
      const cleanChunk = chunk.replace(/^data:\s*/gm, '').replace(/\[DONE\]/g, '')
      fullText += cleanChunk
      if (onChunk) onChunk(cleanChunk, fullText)
    }

    console.log('>>> [SSE 完成全部流式接收], 总长度:', fullText.length)
    if (onComplete) onComplete(fullText)
  } catch (err) {
    console.error('>>> [SSE 发生异常]:', err)
    if (onError) onError(err)
    else throw err
  }
}

