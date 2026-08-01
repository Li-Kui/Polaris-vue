package com.polaris.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** 为仍使用 Jackson 2 API 的 LangChain4j/LangGraph4j 工作流组件提供 Mapper。 */
@Configuration
public class WorkflowJacksonConfig {

    @Bean(name = "workflowObjectMapper")
    @Primary
    public ObjectMapper workflowObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
