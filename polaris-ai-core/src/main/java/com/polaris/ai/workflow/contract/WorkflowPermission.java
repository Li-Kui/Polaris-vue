package com.polaris.ai.workflow.contract;

/** 管理端、中台和 API 调用方共用的稳定工作流权限码。 */
public final class WorkflowPermission {

    public static final String READ = "workflow:read";
    public static final String EDIT = "workflow:edit";
    public static final String PUBLISH = "workflow:publish";
    public static final String EXECUTE = "workflow:execute";
    public static final String CANCEL = "workflow:cancel";
    public static final String DEBUG = "workflow:debug";
    public static final String APPROVE = "workflow:approve";
    public static final String ADMIN = "workflow:admin";

    private WorkflowPermission() {
    }
}
