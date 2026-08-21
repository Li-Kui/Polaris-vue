package com.polaris.platform.mapper;

import com.polaris.platform.domain.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TenantMapper {
    Tenant selectById(@Param("tenantId") Long tenantId);
    Tenant selectByCode(@Param("tenantCode") String tenantCode);
    List<Tenant> selectList(Tenant query);
    int insert(Tenant tenant);
    int update(Tenant tenant);
    int deleteById(@Param("tenantId") Long tenantId);
    int updateUsedTokens(@Param("tenantId") Long tenantId, @Param("tokens") long tokens);
}
