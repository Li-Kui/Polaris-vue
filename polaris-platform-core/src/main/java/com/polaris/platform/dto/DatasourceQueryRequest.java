package com.polaris.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

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
}
