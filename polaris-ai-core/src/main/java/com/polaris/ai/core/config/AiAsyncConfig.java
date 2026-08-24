package com.polaris.ai.core.config;

import com.polaris.ai.core.context.CallerContextTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AI 异步线程池配置。
 * 自动传播 CallerContext 到子线程。
 */
@Configuration
public class AiAsyncConfig {

    @Bean
    public CallerContextTaskDecorator callerContextTaskDecorator() {
        return new CallerContextTaskDecorator();
    }

    @Bean("aiTaskExecutor")
    public TaskExecutor aiTaskExecutor(CallerContextTaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-async-");
        executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor;
    }
}
