package com.polaris.platform.dto;

public record McpCatalogRefreshResult(long catalogRevision, int discovered, int added,
        int changed, int missing, int unchanged) {
}
