package com.polaris.ai.utils;

/**
 * 跨线程传递当前 AI 对话的上下文信息（如 conversationId、fileUrl 等）的持有者
 * 
 * @author polaris
 */
public class ChatContextHolder {

    private static final ThreadLocal<Long> CONVERSATION_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> FILE_URL_HOLDER = new ThreadLocal<>();

    public static void setConversationId(Long conversationId) {
        CONVERSATION_ID_HOLDER.set(conversationId);
    }

    public static Long getConversationId() {
        return CONVERSATION_ID_HOLDER.get();
    }

    public static void setFileUrl(String fileUrl) {
        FILE_URL_HOLDER.set(fileUrl);
    }

    public static String getFileUrl() {
        return FILE_URL_HOLDER.get();
    }

    public static void clear() {
        CONVERSATION_ID_HOLDER.remove();
        FILE_URL_HOLDER.remove();
    }
}
