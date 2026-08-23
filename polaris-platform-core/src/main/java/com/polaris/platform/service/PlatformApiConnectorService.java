package com.polaris.platform.service;

import com.polaris.platform.connector.ApiConnectorExecutor;
import com.polaris.platform.domain.PlatformApiConnector;
import com.polaris.platform.mapper.PlatformApiConnectorMapper;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PlatformApiConnectorService {

    @Autowired
    private PlatformApiConnectorMapper connectorMapper;

    @Autowired
    private ApiConnectorExecutor executor;

    public List<PlatformApiConnector> listByTenantId(Long tenantId) {
        if (!PlatformTenantGuard.belongsToCurrentTenant(tenantId)) {
            return List.of();
        }
        return connectorMapper.selectByTenantId(tenantId);
    }

    public List<PlatformApiConnector> list(PlatformApiConnector query) {
        query.setTenantId(PlatformTenantGuard.requireTenantId());
        return connectorMapper.selectList(query);
    }

    public PlatformApiConnector getById(Long id) {
        PlatformApiConnector connector = connectorMapper.selectById(id);
        return connector != null && PlatformTenantGuard.belongsToCurrentTenant(connector.getTenantId())
                ? connector : null;
    }

    public int insert(PlatformApiConnector connector) {
        connector.setTenantId(PlatformTenantGuard.requireTenantId());
        return connectorMapper.insert(connector);
    }

    public int update(PlatformApiConnector connector) {
        PlatformApiConnector current = getById(connector.getId());
        if (current == null) {
            return 0;
        }
        connector.setTenantId(current.getTenantId());
        return connectorMapper.update(connector);
    }

    public int deleteById(Long id) {
        if (getById(id) == null) {
            return 0;
        }
        return connectorMapper.deleteById(id);
    }

    public ResponseEntity<String> execute(Long id, String path, HttpMethod method, Object body, Map<String, String> queryParams) {
        PlatformApiConnector connector = getById(id);
        if (connector == null) {
            throw new RuntimeException("连接器不存在");
        }
        return executor.execute(connector, path, method, body, queryParams);
    }
}
