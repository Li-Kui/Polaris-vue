package com.polaris.platform.service;

import com.polaris.platform.connector.DatasourceConnectorExecutor;
import com.polaris.platform.domain.PlatformDatasource;
import com.polaris.platform.mapper.PlatformDatasourceMapper;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PlatformDatasourceService {

    @Autowired
    private PlatformDatasourceMapper datasourceMapper;

    @Autowired
    private DatasourceConnectorExecutor executor;

    public List<PlatformDatasource> listByTenantId(Long tenantId) {
        if (!PlatformTenantGuard.belongsToCurrentTenant(tenantId)) {
            return List.of();
        }
        return datasourceMapper.selectByTenantId(tenantId);
    }

    public List<PlatformDatasource> list(PlatformDatasource query) {
        query.setTenantId(PlatformTenantGuard.requireTenantId());
        return datasourceMapper.selectList(query);
    }

    public PlatformDatasource getById(Long id) {
        PlatformDatasource datasource = datasourceMapper.selectById(id);
        return datasource != null && PlatformTenantGuard.belongsToCurrentTenant(datasource.getTenantId())
                ? datasource : null;
    }

    public int insert(PlatformDatasource ds) {
        ds.setTenantId(PlatformTenantGuard.requireTenantId());
        return datasourceMapper.insert(ds);
    }

    public int update(PlatformDatasource ds) {
        PlatformDatasource current = getById(ds.getId());
        if (current == null) {
            return 0;
        }
        ds.setTenantId(current.getTenantId());
        return datasourceMapper.update(ds);
    }

    public int deleteById(Long id) {
        if (getById(id) == null) {
            return 0;
        }
        return datasourceMapper.deleteById(id);
    }

    public boolean testConnection(PlatformDatasource ds) {
        return executor.testConnection(ds);
    }

    public List<Map<String, Object>> executeQuery(Long id, String sql, int maxRows) {
        PlatformDatasource ds = getById(id);
        if (ds == null) {
            throw new RuntimeException("数据源不存在");
        }
        return executor.executeQuery(ds, sql, maxRows);
    }
}
