package com.polaris.ai.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 错误消息翻译器，将底层 API 英文或 JSON 错误进行智能剥离和中文化转译
 *
 * @author polaris
 */
@Slf4j
public class AiErrorTranslator {

    /**
     * 将异常对象转换为用户友好的中文描述
     *
     * @param throwable 异常对象
     * @return 友好描述
     */
    public static String translate(Throwable throwable) {
        if (throwable == null) {
            return "未知服务异常，请重试";
        }
        return translate(throwable.getMessage());
    }

    /**
     * 将原始错误消息字符串转换为用户友好的中文描述
     *
     * @param rawMessage 原始异常消息
     * @return 友好描述
     */
    public static String translate(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return "AI 服务响应异常，请稍后再试";
        }

        String trimmed = rawMessage.trim();
        String messageToMatch = trimmed;

        // 1. 尝试解析是否为 JSON 格式的错误响应（例如 OpenAI 或兼容服务返回的 JSON Error）
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JSONObject jsonObject = JSON.parseObject(trimmed);
                if (jsonObject.containsKey("error")) {
                    JSONObject errorObj = jsonObject.getJSONObject("error");
                    if (errorObj != null) {
                        // 优先获取 message，若没有则获取 code
                        if (errorObj.containsKey("message")) {
                            messageToMatch = errorObj.getString("message");
                        } else if (errorObj.containsKey("code")) {
                            messageToMatch = errorObj.getString("code");
                        }
                    }
                }
            } catch (Exception e) {
                // 解析 JSON 异常，降级到使用原始 rawMessage 匹配
                log.debug("解析错误 JSON 报文失败，降级为文本匹配: {}", rawMessage);
            }
        }

        if (messageToMatch == null || messageToMatch.trim().isEmpty()) {
            return "AI 服务响应异常，请稍后再试";
        }

        String lowerMessage = messageToMatch.toLowerCase();

        // 2. 命中常见错误场景进行翻译

        // 额度耗尽 / 欠费 / 免费配额到期
        if (lowerMessage.contains("quota") ||
            lowerMessage.contains("exhausted") ||
            lowerMessage.contains("insufficient_quota") ||
            lowerMessage.contains("freetieronly") ||
            lowerMessage.contains("credit") ||
            lowerMessage.contains("balance") ||
            lowerMessage.contains("欠费") ||
            lowerMessage.contains("额度已用尽")) {
            return "您的 AI 额度已用尽或账户欠费，请联系管理员配置充值，或在右上角更换其他模型。";
        }

        // API Key 错误
        if (lowerMessage.contains("api_key") ||
            lowerMessage.contains("api key") ||
            lowerMessage.contains("apikey") ||
            lowerMessage.contains("invalid key") ||
            lowerMessage.contains("credential") ||
            lowerMessage.contains("unauthorized") ||
            lowerMessage.contains("invalid_api_key") ||
            lowerMessage.contains("invalid_key")) {
            return "当前模型的 API Key 配置无效或已过期，请检查后台模型配置。";
        }

        // 模型不存在或未启用
        if (lowerMessage.contains("model_not_found") ||
            lowerMessage.contains("model not found") ||
            lowerMessage.contains("no such model") ||
            lowerMessage.contains("does not exist") ||
            lowerMessage.contains("not support") ||
            lowerMessage.contains("unsupported")) {
            return "指定的 AI 模型不存在，或当前通道不支持该模型，请检查模型名称设置。";
        }

        // 限流 / 请求频繁
        if (lowerMessage.contains("rate_limit") ||
            lowerMessage.contains("rate limit") ||
            lowerMessage.contains("too many requests") ||
            lowerMessage.contains("429") ||
            lowerMessage.contains("limit reached")) {
            return "AI 服务请求过于频繁，已触发限流保护，请稍候再试。";
        }

        // 网络超时与连接失败
        if (lowerMessage.contains("timeout") ||
            lowerMessage.contains("time out") ||
            lowerMessage.contains("connect") ||
            lowerMessage.contains("connection") ||
            lowerMessage.contains("host") ||
            lowerMessage.contains("http") ||
            lowerMessage.contains("refused") ||
            lowerMessage.contains("socket")) {
            return "与 AI 服务端连接超时，请检查系统网络状态或稍后再试。";
        }

        // 3. 兜底逻辑：如果是已解析成功的干净 Message 文本，且不是一大串 JSON，则可以直接展示
        if (!messageToMatch.equals(trimmed)) {
            return messageToMatch;
        }

        // 如果是系统底层异常堆栈（通常包含 .java、Exception、UnknownHostException 等），则隐藏并返回友好默认提示
        if (trimmed.contains("Exception") || trimmed.contains(".java") || trimmed.contains("Stack")) {
            return "AI 服务系统异常，请联系管理员或稍后再试";
        }

        return trimmed;
    }
}
