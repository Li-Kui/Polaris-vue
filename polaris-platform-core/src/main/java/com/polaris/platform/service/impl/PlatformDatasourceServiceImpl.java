package com.polaris.platform.service.impl;

import com.polaris.platform.connector.DatasourceConnectorExecutor;
import com.polaris.platform.domain.PlatformDatasource;
import com.polaris.platform.mapper.PlatformDatasourceMapper;
import com.polaris.platform.service.IPlatformDatasourceService;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 中台外部数据源服务层实现类
 *
 * @author polaris
 */
@Service
public class PlatformDatasourceServiceImpl implements IPlatformDatasourceService {

    @Autowired
    private PlatformDatasourceMapper platformDatasourceMapper;

    @Autowired
    private DatasourceConnectorExecutor datasourceConnectorExecutor;

    @Override
    public List<PlatformDatasource> selectDatasourceListByTenantId(Long tenantId) {
        if (!PlatformTenantGuard.belongsToCurrentTenant(tenantId)) {
            return List.of();
        }
        return platformDatasourceMapper.selectByTenantId(tenantId);
    }

    @Override
    public List<PlatformDatasource> selectDatasourceList(PlatformDatasource datasource) {
        datasource.setTenantId(PlatformTenantGuard.requireTenantId());
        return platformDatasourceMapper.selectList(datasource);
    }

    @Override
    public PlatformDatasource selectDatasourceById(Long id) {
        PlatformDatasource datasource = platformDatasourceMapper.selectById(id);
        return datasource != null && PlatformTenantGuard.belongsToCurrentTenant(datasource.getTenantId())
                ? datasource : null;
    }

    @Override
    public int insertDatasource(PlatformDatasource datasource) {
        datasource.setTenantId(PlatformTenantGuard.requireTenantId());
        return platformDatasourceMapper.insert(datasource);
    }

    @Override
    public int updateDatasource(PlatformDatasource datasource) {
        PlatformDatasource current = selectDatasourceById(datasource.getId());
        if (current == null) {
            return 0;
        }
        datasource.setTenantId(current.getTenantId());
        return platformDatasourceMapper.update(datasource);
    }

    @Override
    public int deleteDatasourceById(Long id) {
        if (selectDatasourceById(id) == null) {
            return 0;
        }
        return platformDatasourceMapper.deleteById(id);
    }

    @Override
    public boolean testDatasourceConnection(PlatformDatasource datasource) {
        return datasourceConnectorExecutor.testConnection(datasource);
    }

    @Override
    public List<Map<String, Object>> executeDatasourceQuery(Long id, String sql, int maxRows) {
        PlatformDatasource datasource = selectDatasourceById(id);
        if (datasource == null) {
            throw new RuntimeException("数据源不存在");
        }
        return datasourceConnectorExecutor.executeQuery(datasource, sql, maxRows);
    }
}
