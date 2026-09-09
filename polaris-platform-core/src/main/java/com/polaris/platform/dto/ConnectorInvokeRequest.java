package com.polaris.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 中台 API 连接器调用请求
 *
 * @author polaris
 */
@Data
@Schema(description = "中台 API 连接器调用请求")
public class ConnectorInvokeRequest {

    @Schema(description = "相对于连接器基础地址的请求路径")
    private String path;

    @Schema(description = "HTTP 请求方法，为空时默认 GET",
            allowableValues = {"GET", "POST", "PUT", "PATCH", "DELETE"}, defaultValue = "GET")
    private String method;

    @Schema(description = "请求体")
    private Object body;

    @Schema(description = "查询参数")
    private Map<String, String> queryParams;
}
