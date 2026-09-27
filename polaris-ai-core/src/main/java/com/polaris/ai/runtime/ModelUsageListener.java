package com.polaris.ai.runtime;

import com.polaris.ai.runtime.usage.NormalizedUsage;

/** 将统一 Usage 接入现有计量/记账设施的扩展点。 */
@FunctionalInterface
public interface ModelUsageListener {

    void onUsage(ModelRuntimeSpec runtime, NormalizedUsage usage);
}
