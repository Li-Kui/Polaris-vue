package com.polaris.platform.service;

import com.polaris.platform.domain.PlatformApiConnector;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

/**
 * 中台 API 连接器服务层接口
 *
 * @author polaris
 */
public interface IPlatformApiConnectorService {

    List<PlatformApiConnector> selectConnectorListByTenantId(Long tenantId);

    List<PlatformApiConnector> selectConnectorList(PlatformApiConnector connector);

    PlatformApiConnector selectConnectorById(Long id);

    int insertConnector(PlatformApiConnector connector);

    int updateConnector(PlatformApiConnector connector);

    int deleteConnectorById(Long id);

    ResponseEntity<String> invokeConnector(Long id, String path, HttpMethod method, Object body,
                                           Map<String, String> queryParams);
}
