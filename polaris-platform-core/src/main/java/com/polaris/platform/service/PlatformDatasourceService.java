package com.polaris.platform.service;

import com.polaris.platform.connector.DatasourceConnectorExecutor;
import com.polaris.platform.domain.PlatformDatasource;
import com.polaris.platform.mapper.PlatformDatasourceMapper;
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
        return datasourceMapper.selectByTenantId(tenantId);
    }

    public List<PlatformDatasource> list(PlatformDatasource query) {
        return datasourceMapper.selectList(query);
    }

    public PlatformDatasource getById(Long id) {
        return datasourceMapper.selectById(id);
    }

    public int insert(PlatformDatasource ds) {
        return datasourceMapper.insert(ds);
    }

    public int update(PlatformDatasource ds) {
        return datasourceMapper.update(ds);
    }

    public int deleteById(Long id) {
        return datasourceMapper.deleteById(id);
    }

    public boolean testConnection(PlatformDatasource ds) {
        return executor.testConnection(ds);
    }

    public List<Map<String, Object>> executeQuery(Long id, String sql, int maxRows) {
        PlatformDatasource ds = datasourceMapper.selectById(id);
        if (ds == null) {
            throw new RuntimeException("数据源不存在");
        }
        return executor.executeQuery(ds, sql, maxRows);
    }
}
