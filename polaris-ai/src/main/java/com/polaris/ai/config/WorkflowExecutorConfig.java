package com.polaris.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** 工作流专用有界线程池，避免请求按次创建裸线程。 */
@Configuration
@EnableScheduling
public class WorkflowExecutorConfig {

    @Bean(name = "workflowTaskExecutor")
    public ThreadPoolTaskExecutor workflowTaskExecutor(
            @Value("${ai.workflow.executor.core-pool-size:4}") int corePoolSize,
            @Value("${ai.workflow.executor.max-pool-size:16}") int maxPoolSize,
            @Value("${ai.workflow.executor.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-workflow-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "workflowNodeTaskExecutor")
    public ThreadPoolTaskExecutor workflowNodeTaskExecutor(
            @Value("${ai.workflow.node-executor.core-pool-size:8}") int corePoolSize,
            @Value("${ai.workflow.node-executor.max-pool-size:32}") int maxPoolSize,
            @Value("${ai.workflow.node-executor.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-workflow-node-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
