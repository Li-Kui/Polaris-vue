package com.polaris.ai.customer.application;

import com.polaris.common.exception.ServiceException;

import java.net.URI;

/** 管理员配置的人工联系信息；仅展示链接或电话，不建立坐席会话、不代用户发送消息。 */
public record CustomerServiceContactConfig(String name, String webUrl, String phone, String serviceHours, String note) {
    public CustomerServiceContactConfig {
        name = text(name, 64);
        webUrl = text(webUrl, 500);
        phone = text(phone, 32);
        serviceHours = text(serviceHours, 120);
        note = text(note == null ? null : note.replace("\r\n", "\n"), 500, true);
        if (name == null || webUrl == null && phone == null) throw invalid();
        if (webUrl != null) {
            try {
                var uri = URI.create(webUrl);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                        || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535
                        || webUrl.contains("\\")) throw invalid();
            } catch (IllegalArgumentException malformed) { throw invalid(); }
        }
        if (phone != null && (!phone.matches("\\+?[0-9 ()-]+")
                || phone.replaceAll("[^0-9]", "").length() < 4 || phone.replaceAll("[^0-9]", "").length() > 20)) throw invalid();
    }
    private static String text(String value, int limit) {
        return text(value, limit, false);
    }
    private static String text(String value, int limit, boolean multiline) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > limit || value.codePoints().anyMatch(code -> Character.isISOControl(code) && !(multiline && code == '\n'))) throw invalid();
        return value.strip();
    }
    private static ServiceException invalid() {
        return new ServiceException("INVALID_INPUT: 请填写人工入口名称、HTTPS客服网页或有效电话号码，检查字段长度及格式", 400);
    }
}
