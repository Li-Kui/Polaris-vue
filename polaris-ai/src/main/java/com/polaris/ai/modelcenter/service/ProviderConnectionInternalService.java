package com.polaris.ai.modelcenter.service;

import com.polaris.ai.domain.AiProviderConnection;
import com.polaris.ai.mapper.AiProviderConnectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Provider Connection 的内部只读查询边界。 */
@Service
@Transactional(readOnly = true)
public class ProviderConnectionInternalService {

    private final AiProviderConnectionMapper connectionMapper;

    public ProviderConnectionInternalService(AiProviderConnectionMapper connectionMapper) {
        this.connectionMapper = connectionMapper;
    }

    public AiProviderConnection findById(Long connectionId) {
        AiProviderConnection query = new AiProviderConnection();
        query.setId(requireId(connectionId));
        return connectionMapper.selectAccessibleById(query);
    }

    public AiProviderConnection findEnabledById(Long connectionId) {
        AiProviderConnection connection = findById(connectionId);
        return connection != null && "1".equals(connection.getStatus())
                ? connection : null;
    }

    private Long requireId(Long connectionId) {
        return Objects.requireNonNull(connectionId, "connectionId");
    }
}
