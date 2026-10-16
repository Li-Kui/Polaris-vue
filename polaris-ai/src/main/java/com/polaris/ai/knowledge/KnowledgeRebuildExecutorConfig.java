package com.polaris.ai.knowledge;

import com.polaris.ai.core.context.CallerContextTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class KnowledgeRebuildExecutorConfig {
    @Bean("knowledgeRebuildExecutor")
    public ThreadPoolTaskExecutor executor(CallerContextTaskDecorator decorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); executor.setMaxPoolSize(2); executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("knowledge-rebuild-"); executor.setTaskDecorator(decorator);
        // 不能退回请求线程执行；拒绝时持久化明确结果，不假装提交成功。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
