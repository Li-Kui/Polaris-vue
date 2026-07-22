import request from '@/utils/request'

// 直连发起绘图任务
export function generateImage(data) {
  return request({
    url: '/ai/chat/image-task/generate',
    method: 'post',
    data
  })
}

// 轮询绘图任务状态
export function getImageTaskStatus(taskId) {
  return request({
    url: `/ai/chat/image-task/status/${taskId}`,
    method: 'get'
  })
}

// 查询当前可用绘图能力
export function listImageCapabilities() {
  return request({
    url: '/ai/chat/image-task/capabilities',
    method: 'get'
  })
}
