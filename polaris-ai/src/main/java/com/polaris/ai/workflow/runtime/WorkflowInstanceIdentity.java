package com.polaris.ai.workflow.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 当前工作流运行实例的数据库租约身份。 */
@Component
public class WorkflowInstanceIdentity {

    private final String id;

    public WorkflowInstanceIdentity(@Value("${ai.workflow.instance-id:}") String configuredId) {
        String value = configuredId == null ? "" : configuredId.trim();
        this.id = value.isEmpty() ? UUID.randomUUID().toString() : abbreviate(value, 64);
    }

    public String id() {
        return id;
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
