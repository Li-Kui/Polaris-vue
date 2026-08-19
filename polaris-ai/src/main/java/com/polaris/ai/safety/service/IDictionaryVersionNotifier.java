package com.polaris.ai.safety.service;

/**
 * AI 词库发布版本通知广播服务层接口
 *
 * @author polaris
 */
public interface IDictionaryVersionNotifier {

    String CHANNEL = "ai:moderation:dictionary:published";

    /**
     * 广播最新发布的词库版本号
     */
    void publish(long version);
}
