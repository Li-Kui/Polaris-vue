package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.ModelExecutionObservation;
import com.polaris.ai.runtime.ModelExecutionObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 仅记录 Runtime 元数据，不记录 Prompt、Credential 或媒体正文。 */
@Component
public class SafeLoggingModelExecutionObserver
        implements ModelExecutionObserver {

    private static final Logger log = LoggerFactory.getLogger(
            SafeLoggingModelExecutionObserver.class);

    @Override
    public void onTerminal(ModelExecutionObservation value) {
        log.info("Model runtime terminal traceId={} modelId={} modelCode={} "
                        + "modelRevision={} connectionId={} connectionRevision={} "
                        + "provider={} protocol={} capability={} activeFeatures={} "
                        + "latencyMs={} success={} errorCategory={} "
                        + "providerRequestId={} usageSource={} inputTokens={} "
                        + "outputTokens={} totalTokens={} inputCharacters={} "
                        + "audioInputMillis={} audioOutputMillis={}",
                value.traceId(), value.modelId(), value.modelCode(),
                value.modelRevision(), value.connectionId(),
                value.connectionRevision(), value.providerCode(),
                value.protocolCode(), value.capabilityCode(),
                value.activeFeatures(), value.latencyMillis(), value.success(),
                value.errorCategory(), value.providerRequestId(),
                value.usage().source(), value.usage().inputTokens(),
                value.usage().outputTokens(), value.usage().totalTokens(),
                value.usage().inputCharacters(),
                value.usage().audioInputMillis(),
                value.usage().audioOutputMillis());
    }
}
