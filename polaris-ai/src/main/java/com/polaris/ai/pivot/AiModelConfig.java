package com.polaris.ai.pivot;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * AI 模型配置类
 * 重构为返回具有热切换能力的 StreamingChatModel 动态代理 Bean
 * 
 * @author polaris
 */
@Configuration
public class AiModelConfig 
{
    private static final Logger log = LoggerFactory.getLogger(AiModelConfig.class);

    /**
     * 注册具有热切换能力的流式对话模型代理
     */
    @Bean
    public StreamingChatModel streamingChatModel(AiModelFactory factory)
    {
        log.info(">>> 注册 StreamingChatModel 动态热切换代理 Bean");
        return new StreamingChatModel() {
            @Override
            public void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {
                factory.getDefaultStreamingModel().chat(messages, handler);
            }
        };
    }
}
