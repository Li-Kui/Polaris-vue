package com.polaris.ai.runtime.usage;

/** Usage 数值的可信来源；计费策略不得从该枚举隐式推导。 */
public enum UsageSource {
    PROVIDER_REPORTED,
    LOCAL_TOKENIZER,
    CHAR_ESTIMATED,
    UNKNOWN
}
