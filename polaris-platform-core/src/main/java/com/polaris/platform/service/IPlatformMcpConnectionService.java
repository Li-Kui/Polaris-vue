package com.polaris.platform.service;

import com.polaris.platform.domain.PlatformMcpConnection;
import com.polaris.platform.dto.McpConnectionProbeResult;
import com.polaris.platform.dto.McpConnectionSaveRequest;

import java.util.List;

public interface IPlatformMcpConnectionService {
    List<PlatformMcpConnection> selectList(String connectionName, String status);
    PlatformMcpConnection selectById(Long id);
    PlatformMcpConnection insert(McpConnectionSaveRequest request);
    PlatformMcpConnection update(McpConnectionSaveRequest request);
    int delete(Long id, Long revision);
    McpConnectionProbeResult probe(Long id, boolean discoverTools);
}
