package com.polaris.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** 为工作流契约、编译与持久化提供统一的 Jackson Mapper。 */
@Configuration
public class WorkflowJacksonConfig {

    @Bean(name = "workflowObjectMapper")
    @Primary
    public ObjectMapper workflowObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
