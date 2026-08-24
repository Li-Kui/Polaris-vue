package com.polaris.platform.service;

import com.polaris.platform.domain.PlatformDatasource;

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

    int insertDatasource(PlatformDatasource datasource);

    int updateDatasource(PlatformDatasource datasource);

    int deleteDatasourceById(Long id);

    boolean testDatasourceConnection(PlatformDatasource datasource);

    List<Map<String, Object>> executeDatasourceQuery(Long id, String sql, int maxRows);
}
