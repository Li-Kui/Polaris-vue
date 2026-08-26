package com.polaris.platform.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.platform.domain.PlatformDatasource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlatformDatasourceMapper {
    PlatformDatasource selectById(@Param("id") Long id);
    List<PlatformDatasource> selectByTenantId(@Param("tenantId") Long tenantId);
    @InterceptorIgnore(tenantLine = "true")
    PlatformDatasource selectWorkflowResource(
            @Param("tenantId") Long tenantId, @Param("id") Long id);
    @InterceptorIgnore(tenantLine = "true")
    List<PlatformDatasource> selectWorkflowResources(@Param("tenantId") Long tenantId);
    List<PlatformDatasource> selectList(PlatformDatasource query);
    int insert(PlatformDatasource ds);
    int update(PlatformDatasource ds);
    int deleteById(@Param("id") Long id);
}
