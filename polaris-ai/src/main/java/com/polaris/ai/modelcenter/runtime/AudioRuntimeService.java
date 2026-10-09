package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.modelcenter.schema.ParameterPolicyDefinition;
import com.polaris.ai.runtime.CapabilityExecutor;
import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.audio.AudioSttInvocation;
import com.polaris.ai.runtime.audio.AudioSttResult;
import com.polaris.ai.runtime.audio.AudioTtsInvocation;
import com.polaris.ai.runtime.audio.AudioTtsResult;
import com.polaris.ai.runtime.stream.ModelStreamEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;

/** Audio 业务入口；请求覆盖仍经过统一 Parameter Policy。 */
@Service
public class AudioRuntimeService {

    private final ModelRuntimeResolver runtimeResolver;
    private final CapabilityExecutor capabilityExecutor;

    public AudioRuntimeService(
            ModelRuntimeResolver runtimeResolver,
            CapabilityExecutor capabilityExecutor) {
        this.runtimeResolver = runtimeResolver;
        this.capabilityExecutor = capabilityExecutor;
    }

    public ModelExecutionResult<AudioTtsResult> synthesize(
            Long modelId, AudioTtsInvocation invocation) {
        ModelRuntimeSpec runtime = resolve(modelId, invocation,
                invocation.stream() ? Set.of("STREAMING") : Set.of(),
                invocation.overrides());
        return capabilityExecutor.execute(runtime, invocation, AudioTtsResult.class);
    }

    public Flow.Publisher<ModelStreamEvent> stream(
            Long modelId, AudioTtsInvocation invocation) {
        ModelRuntimeSpec runtime = resolve(
                modelId, invocation, Set.of("STREAMING"), invocation.overrides());
        return capabilityExecutor.stream(runtime, invocation);
    }

    public ModelExecutionResult<AudioSttResult> transcribe(
            Long modelId, AudioSttInvocation invocation) {
        ModelRuntimeSpec runtime = resolve(
                modelId, invocation, Set.of(), invocation.overrides());
        return capabilityExecutor.execute(runtime, invocation, AudioSttResult.class);
    }

    private ModelRuntimeSpec resolve(
            Long modelId,
            com.polaris.ai.runtime.CapabilityInvocation invocation,
            Set<String> features,
            Map<String, Object> overrides) {
        CapabilityParameterOverride override = new CapabilityParameterOverride(
                ParameterPolicyDefinition.ParameterSource.REQUEST,
                overrides, Map.of());
        return runtimeResolver.resolve(new ModelRuntimeRequest<>(
                modelId, invocation, features, List.of(override)));
    }
}
