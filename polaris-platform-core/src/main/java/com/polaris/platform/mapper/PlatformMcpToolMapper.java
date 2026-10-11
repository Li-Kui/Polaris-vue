package com.polaris.platform.mapper;

import com.polaris.platform.domain.PlatformMcpTool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlatformMcpToolMapper {
    List<PlatformMcpTool> selectWorkflowResources(@Param("tenantId") Long tenantId);
    List<PlatformMcpTool> selectByConnection(@Param("tenantId") Long tenantId,
            @Param("connectionId") Long connectionId, @Param("catalogStatus") String catalogStatus);
    List<PlatformMcpTool> selectByNames(@Param("tenantId") Long tenantId,
            @Param("connectionId") Long connectionId, @Param("names") List<String> names);
    int markMissing(@Param("tenantId") Long tenantId, @Param("connectionId") Long connectionId,
            @Param("names") List<String> names, @Param("operator") String operator);
    PlatformMcpTool selectById(@Param("tenantId") Long tenantId, @Param("id") Long id);
    int insert(PlatformMcpTool tool);
    int update(PlatformMcpTool tool);
    int markSeen(@Param("tenantId") Long tenantId, @Param("id") Long id);
    int invalidateByConnection(@Param("tenantId") Long tenantId, @Param("connectionId") Long connectionId);
    long countByConnection(@Param("tenantId") Long tenantId, @Param("connectionId") Long connectionId);
}
