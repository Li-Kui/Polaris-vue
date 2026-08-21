package com.polaris.platform.service;

import com.polaris.platform.connector.ApiConnectorExecutor;
import com.polaris.platform.domain.PlatformApiConnector;
import com.polaris.platform.mapper.PlatformApiConnectorMapper;
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
        return connectorMapper.selectByTenantId(tenantId);
    }

    public List<PlatformApiConnector> list(PlatformApiConnector query) {
        return connectorMapper.selectList(query);
    }

    public PlatformApiConnector getById(Long id) {
        return connectorMapper.selectById(id);
    }

    public int insert(PlatformApiConnector connector) {
        return connectorMapper.insert(connector);
    }

    public int update(PlatformApiConnector connector) {
        return connectorMapper.update(connector);
    }

    public int deleteById(Long id) {
        return connectorMapper.deleteById(id);
    }

    public ResponseEntity<String> execute(Long id, String path, HttpMethod method, Object body, Map<String, String> queryParams) {
        PlatformApiConnector connector = connectorMapper.selectById(id);
        if (connector == null) {
            throw new RuntimeException("连接器不存在");
        }
        return executor.execute(connector, path, method, body, queryParams);
    }
}
