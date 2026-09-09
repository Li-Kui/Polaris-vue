package com.polaris.platform.mapper;

import com.polaris.platform.domain.PlatformUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlatformUserMapper {
    PlatformUser selectById(@Param("id") Long id);
    PlatformUser selectAuthUser(@Param("userId") Long userId, @Param("tenantId") Long tenantId,
                                @Param("username") String username);
    PlatformUser selectByUsername(@Param("tenantId") Long tenantId, @Param("username") String username);
    List<PlatformUser> selectByTenantId(@Param("tenantId") Long tenantId);
    int insert(PlatformUser user);
    int update(PlatformUser user);
    int deleteById(@Param("id") Long id);
    int updateLastLoginTime(@Param("id") Long id);
}
