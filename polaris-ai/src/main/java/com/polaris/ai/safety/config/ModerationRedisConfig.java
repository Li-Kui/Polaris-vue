package com.polaris.ai.safety.config;

import com.polaris.ai.safety.rule.DictionarySnapshotManager;
import com.polaris.ai.safety.service.IDictionaryVersionNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
public class ModerationRedisConfig {
    private static final Logger log = LoggerFactory.getLogger(ModerationRedisConfig.class);

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisMessageListenerContainer moderationRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            DictionarySnapshotManager snapshotManager) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListener listener = (Message message, byte[] pattern) -> {
            try {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                long version = Long.parseLong(body.trim());
                log.info("收到 Redis 词库新版本广播通知: version={}", version);
                snapshotManager.reloadPublishedIfNewer(version);
            } catch (Exception e) {
                log.warn("处理 Redis 词库版本通知异常", e);
            }
        };

        container.addMessageListener(listener, new ChannelTopic(IDictionaryVersionNotifier.CHANNEL));
        return container;
    }
}
