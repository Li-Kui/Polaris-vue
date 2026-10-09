package com.polaris.ai.modelcenter.service;

import com.polaris.ai.modelcenter.dto.ProviderConnectionCreateRequest;
import com.polaris.ai.modelcenter.dto.ProviderConnectionQuery;
import com.polaris.ai.modelcenter.dto.ProviderConnectionUpdateRequest;
import com.polaris.ai.modelcenter.vo.ProviderConnectionRuntime;
import com.polaris.ai.modelcenter.vo.ProviderConnectionVO;

import java.util.List;

/** Provider Connection 管理与内部 Runtime 的统一服务边界。 */
public interface IAiProviderConnectionService {

    ProviderConnectionVO get(Long id);

    List<ProviderConnectionVO> list(ProviderConnectionQuery query);

    Long create(ProviderConnectionCreateRequest request);

    void update(Long id, ProviderConnectionUpdateRequest request);

    void delete(Long id);

    void changeStatus(Long id, String status, Long expectedRevision);

    ProviderConnectionRuntime getRuntime(Long id);
}
