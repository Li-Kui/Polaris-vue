package com.polaris.ai.runtime.stream;

import com.polaris.ai.runtime.usage.NormalizedUsage;

import java.util.Objects;

/** Provider 报告或本地可靠计算得到的 Usage。 */
public record UsageEvent(NormalizedUsage usage) implements ModelStreamEvent {

    public UsageEvent {
        usage = Objects.requireNonNull(usage, "usage");
    }
}
