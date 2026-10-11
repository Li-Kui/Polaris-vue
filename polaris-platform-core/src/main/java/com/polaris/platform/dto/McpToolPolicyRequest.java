package com.polaris.platform.dto;

import lombok.Data;

/** 审核必须基于页面展示的精确快照，不接受调用方指定租户或分类白名单。 */
@Data
public class McpToolPolicyRequest {
    private Long revision;
    private Long connectionRevision;
    private Long catalogRevision;
    private String structureHash;
    /** APPROVE 或 REVOKE。 */
    private String action;
    /** APPROVE 时必须由管理员显式确认，不使用远端 readOnlyHint 自动填充。 */
    private Boolean confirmedReadOnly;
    private String reviewNote;
}
