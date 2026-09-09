package com.polaris.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 中台外部数据源查询请求
 *
 * @author polaris
 */
@Data
@Schema(description = "中台外部数据源查询请求")
public class DatasourceQueryRequest {

    @Schema(description = "只读 SQL 查询语句")
    private String sql;

    @Schema(description = "最大返回行数")
    private int maxRows;

    @Schema(description = "命名查询参数")
    private Map<String, Object> parameters;

    @Schema(description = "查询超时时间，单位秒")
    private Integer queryTimeoutSeconds;
}
