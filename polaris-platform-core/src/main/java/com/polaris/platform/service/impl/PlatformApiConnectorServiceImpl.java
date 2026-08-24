package com.polaris.platform.service.impl;

import com.polaris.platform.connector.ApiConnectorExecutor;
import com.polaris.platform.domain.PlatformApiConnector;
import com.polaris.platform.mapper.PlatformApiConnectorMapper;
import com.polaris.platform.service.IPlatformApiConnectorService;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 中台 API 连接器服务层实现类
 *
 * @author polaris
 */
@Service
public class PlatformApiConnectorServiceImpl implements IPlatformApiConnectorService {

    @Autowired
    private PlatformApiConnectorMapper platformApiConnectorMapper;

    @Autowired
    private ApiConnectorExecutor apiConnectorExecutor;

    @Override
    public List<PlatformApiConnector> selectConnectorListByTenantId(Long tenantId) {
        if (!PlatformTenantGuard.belongsToCurrentTenant(tenantId)) {
            return List.of();
        }
        return platformApiConnectorMapper.selectByTenantId(tenantId);
    }

    @Override
    public List<PlatformApiConnector> selectConnectorList(PlatformApiConnector connector) {
        connector.setTenantId(PlatformTenantGuard.requireTenantId());
        return platformApiConnectorMapper.selectList(connector);
    }

    @Override
    public PlatformApiConnector selectConnectorById(Long id) {
        PlatformApiConnector connector = platformApiConnectorMapper.selectById(id);
        return connector != null && PlatformTenantGuard.belongsToCurrentTenant(connector.getTenantId())
                ? connector : null;
    }

    @Override
    public int insertConnector(PlatformApiConnector connector) {
        connector.setTenantId(PlatformTenantGuard.requireTenantId());
        return platformApiConnectorMapper.insert(connector);
    }

    @Override
    public int updateConnector(PlatformApiConnector connector) {
        PlatformApiConnector current = selectConnectorById(connector.getId());
        if (current == null) {
            return 0;
        }
        connector.setTenantId(current.getTenantId());
        return platformApiConnectorMapper.update(connector);
    }

    @Override
    public int deleteConnectorById(Long id) {
        if (selectConnectorById(id) == null) {
            return 0;
        }
        return platformApiConnectorMapper.deleteById(id);
    }

    @Override
    public ResponseEntity<String> invokeConnector(Long id, String path, HttpMethod method, Object body,
                                                  Map<String, String> queryParams) {
        PlatformApiConnector connector = selectConnectorById(id);
        if (connector == null) {
            throw new RuntimeException("连接器不存在");
        }
        return apiConnectorExecutor.execute(connector, path, method, body, queryParams);
    }
}
