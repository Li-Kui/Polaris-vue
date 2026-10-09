package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.CapabilityExecutor;
import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.ModelRuntimeSnapshot;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.image.ImageCapabilityInvocation;
import com.polaris.ai.runtime.image.ImageCapabilityResult;
import org.springframework.stereotype.Service;

/** IMAGE_* Capability 的统一 Runtime 入口。 */
@Service
public class ImageRuntimeService {

    private final ModelRuntimeResolver runtimeResolver;
    private final CapabilityExecutor capabilityExecutor;

    public ImageRuntimeService(
            ModelRuntimeResolver runtimeResolver,
            CapabilityExecutor capabilityExecutor) {
        this.runtimeResolver = runtimeResolver;
        this.capabilityExecutor = capabilityExecutor;
    }

    public ModelRuntimeSpec resolve(
            Long modelId, ImageCapabilityInvocation invocation) {
        return runtimeResolver.resolve(new ModelRuntimeRequest<>(
                modelId, invocation, null, null));
    }

    public ModelExecutionResult<ImageCapabilityResult> execute(
            ModelRuntimeSpec runtime,
            ImageCapabilityInvocation invocation) {
        return capabilityExecutor.execute(
                runtime, invocation, ImageCapabilityResult.class);
    }

    public ModelRuntimeSnapshot snapshot(ModelRuntimeSpec runtime) {
        return new ModelRuntimeSnapshot(
                runtime.modelId(), runtime.modelCode(), runtime.modelRevision(),
                runtime.connectionId(), runtime.connectionRevision(),
                runtime.providerCode(), runtime.protocolCode(), runtime.baseUrl(),
                runtime.modelName(), runtime.capabilityCode(),
                runtime.invocationParameters(), runtime.featureParameters(),
                runtime.runtimePolicy(), runtime.schemaVersion(),
                runtime.schemaHash());
    }
}
