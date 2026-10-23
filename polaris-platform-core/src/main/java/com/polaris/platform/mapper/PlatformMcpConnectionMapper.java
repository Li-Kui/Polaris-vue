package com.polaris.platform.mapper;

import com.polaris.platform.domain.PlatformMcpConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlatformMcpConnectionMapper {
    List<PlatformMcpConnection> selectList(@Param("tenantId") Long tenantId,
            @Param("connectionName") String connectionName, @Param("status") String status);

    PlatformMcpConnection selectById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    PlatformMcpConnection lockById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    int invalidateCatalog(@Param("tenantId") Long tenantId, @Param("id") Long id);

    int recordDiscovery(@Param("tenantId") Long tenantId, @Param("id") Long id,
            @Param("success") boolean success);

    int insert(PlatformMcpConnection connection);

    int update(PlatformMcpConnection connection);

    int deleteById(@Param("tenantId") Long tenantId, @Param("id") Long id,
            @Param("revision") Long revision);
}
