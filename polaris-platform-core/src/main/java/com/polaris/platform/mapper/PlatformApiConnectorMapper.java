package com.polaris.platform.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.platform.domain.PlatformApiConnector;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlatformApiConnectorMapper {
    PlatformApiConnector selectById(@Param("id") Long id);
    List<PlatformApiConnector> selectByTenantId(@Param("tenantId") Long tenantId);
    @InterceptorIgnore(tenantLine = "true")
    PlatformApiConnector selectWorkflowResource(
            @Param("tenantId") Long tenantId, @Param("id") Long id);
    @InterceptorIgnore(tenantLine = "true")
    List<PlatformApiConnector> selectWorkflowResources(@Param("tenantId") Long tenantId);
    List<PlatformApiConnector> selectList(PlatformApiConnector query);
    int insert(PlatformApiConnector connector);
    int update(PlatformApiConnector connector);
    int deleteById(@Param("id") Long id);
}
