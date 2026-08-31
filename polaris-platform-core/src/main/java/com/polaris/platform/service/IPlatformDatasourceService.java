package com.polaris.platform.service;

import com.polaris.platform.domain.PlatformDatasource;
import com.polaris.platform.dto.DatasourceSaveRequest;
import com.polaris.platform.dto.DatasourceTestResponse;

import java.util.List;
import java.util.Map;

/**
 * 中台外部数据源服务层接口
 *
 * @author polaris
 */
public interface IPlatformDatasourceService {

    List<PlatformDatasource> selectDatasourceListByTenantId(Long tenantId);

    List<PlatformDatasource> selectDatasourceList(PlatformDatasource datasource);

    PlatformDatasource selectDatasourceById(Long id);

    PlatformDatasource insertDatasource(DatasourceSaveRequest request, String operator);

    PlatformDatasource insertSharedDatasource(DatasourceSaveRequest request, String operator);

    PlatformDatasource updateDatasource(DatasourceSaveRequest request, String operator);

    int deleteDatasourceById(Long id);

    long countDatasourceUsages(Long id);

    DatasourceTestResponse testDatasourceConnection(DatasourceSaveRequest request);

    DatasourceTestResponse testSavedDatasourceConnection(Long id);

    List<Map<String, Object>> executeDatasourceQuery(
            Long id,
            String sql,
            Map<String, Object> parameters,
            int maxRows,
            Integer queryTimeoutSeconds);

    List<Map<String, Object>> executeWorkflowDatasourceQuery(
            Long tenantId,
            Long id,
            String sql,
            Map<String, Object> parameters,
            int maxRows,
            Integer queryTimeoutSeconds);
}
