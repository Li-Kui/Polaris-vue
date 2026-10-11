package com.polaris.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;
import tools.jackson.databind.JsonNode;

/** 显式手工调用绑定页面所见快照，不接受租户、主体或远端工具名称。 */
@Data
public class McpInvokeRequest {
    private Long revision;
    private Long connectionRevision;
    private Long catalogRevision;
    private String structureHash;
    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private JsonNode arguments;
}
