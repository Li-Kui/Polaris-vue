package com.polaris.ai.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨线程传递当前 AI 对话的上下文信息（如 conversationId、fileUrl 等）的持有者，
 * 并支持跨线程安全的单次对话任务（Task JSON）收集与清理。
 * 
 * @author polaris
 */
public class ChatContextHolder {

    private static final ThreadLocal<Long> CONVERSATION_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> FILE_URL_HOLDER = new ThreadLocal<>();

    /**
     * 以 conversationId 为 Key 存储当前单次对话请求所产生的异步任务 JSON 列表
     * 具备线程安全与跨线程池收集能力，生命周期由单次 chat 请求管控。
     */
    private static final ConcurrentHashMap<Long, List<String>> CONVERSATION_TASKS_MAP = new ConcurrentHashMap<>();

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

    /**
     * 记录指定会话本次对话请求中产生的生图任务 JSON
     */
    public static void addTaskJson(Long conversationId, String taskJson) {
        if (conversationId == null || taskJson == null || taskJson.trim().isEmpty()) {
            return;
        }
        CONVERSATION_TASKS_MAP.computeIfAbsent(conversationId, k -> Collections.synchronizedList(new ArrayList<>())).add(taskJson);
    }

    /**
     * 获取指定会话本次对话请求中收集到的所有生图任务 JSON 列表
     */
    public static List<String> getTaskJsonList(Long conversationId) {
        if (conversationId == null) {
            return Collections.emptyList();
        }
        List<String> list = CONVERSATION_TASKS_MAP.get(conversationId);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    /**
     * 清理指定会话的挂载任务列表（单次请求结束或开始时显式调用）
     */
    public static void clearTasks(Long conversationId) {
        if (conversationId != null) {
            CONVERSATION_TASKS_MAP.remove(conversationId);
        }
    }

    /**
     * 清理当前线程挂载的 ThreadLocal 上下文（不清理会话级 TASK_MAP）
     */
    public static void clearThreadContext() {
        CONVERSATION_ID_HOLDER.remove();
        FILE_URL_HOLDER.remove();
    }

    @Deprecated
    public static void clear() {
        clearThreadContext();
    }
}

