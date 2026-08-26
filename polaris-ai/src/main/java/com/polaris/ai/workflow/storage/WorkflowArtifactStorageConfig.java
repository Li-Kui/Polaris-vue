package com.polaris.ai.workflow.storage;

import com.polaris.ai.workflow.spi.WorkflowArtifactStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 工作流产物存储装配配置。 */
@Configuration
public class WorkflowArtifactStorageConfig {

    @Bean
    @ConditionalOnMissingBean(WorkflowArtifactStorage.class)
    public WorkflowArtifactStorage localWorkflowArtifactStorage(
            @Value("${polaris.profile:./profile}") String profile) {
        return new LocalWorkflowArtifactStorage(profile);
    }
}
