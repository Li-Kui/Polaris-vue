package com.polaris.ai.safety.service.impl;

import com.polaris.ai.safety.service.IDictionaryVersionNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 词库发布版本通知广播服务层实现类
 *
 * @author polaris
 */
@Service
public class DictionaryVersionNotifierImpl implements IDictionaryVersionNotifier {

    private static final Logger log = LoggerFactory.getLogger(DictionaryVersionNotifierImpl.class);

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publish(long version) {
        if (redisTemplate != null) {
            try {
                redisTemplate.convertAndSend(CHANNEL, String.valueOf(version));
                log.info("广播词库发布版本通知成功: version={}", version);
            } catch (Exception e) {
                log.warn("Redis广播词库发布版本通知失败, version={}", version, e);
            }
        }
    }
}
