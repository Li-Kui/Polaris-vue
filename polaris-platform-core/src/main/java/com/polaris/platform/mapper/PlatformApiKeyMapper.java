package com.polaris.platform.mapper;

import com.polaris.platform.domain.PlatformApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlatformApiKeyMapper {
    PlatformApiKey selectById(@Param("id") Long id);
    PlatformApiKey selectAuthApiKeyByHash(@Param("apiKeyHash") String apiKeyHash);
    List<PlatformApiKey> selectByTenantId(@Param("tenantId") Long tenantId);
    int insert(PlatformApiKey apiKey);
    int update(PlatformApiKey apiKey);
    int deleteById(@Param("id") Long id);
    int updateLastUsedTime(@Param("id") Long id);
}
