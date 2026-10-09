package com.polaris.ai.modelcenter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Provider Connection 列表查询条件。 */
@Data
@Schema(description = "Provider Connection 查询条件")
public class ProviderConnectionQuery {

    private String connectionName;
    private String providerCode;
    private String protocolCode;
    private String networkMode;
    private String status;
}
