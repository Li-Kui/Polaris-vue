import request from '@/utils/request'

// 获取会话列表
export function listConversations() {
  return request({
    url: '/ai/chat/conversations',
    method: 'get'
  })
}

// 新建会话
export function createConversation(model, knowledgeBaseId) {
  return request({
    url: '/ai/chat/conversations',
    method: 'post',
    params: { model, knowledgeBaseId }
  })
}

// 重命名会话
export function renameConversation(id, title) {
  return request({
    url: `/ai/chat/conversations/${id}/title`,
    method: 'put',
    params: { title }
  })
}

// 更新会话的大模型和知识库配置
export function updateConversationConfig(id, model, knowledgeBaseId) {
  return request({
    url: `/ai/chat/conversations/${id}/config`,
    method: 'put',
    params: { model, knowledgeBaseId }
  })
}

// 删除会话
export function deleteConversation(id) {
  return request({
    url: `/ai/chat/conversations/${id}`,
    method: 'delete'
  })
}

// 获取会话消息历史
export function listMessages(conversationId) {
  return request({
    url: `/ai/chat/conversations/${conversationId}/messages`,
    method: 'get'
  })
}
