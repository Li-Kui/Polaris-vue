package com.polaris.ai.modelcenter.audio;

import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.audio.AudioSttInvocation;
import com.polaris.ai.runtime.audio.AudioSttResult;
import com.polaris.ai.runtime.audio.AudioTtsInvocation;
import com.polaris.ai.runtime.audio.AudioTtsResult;

public interface AudioProtocolClient {

    boolean supports(String protocolCode);

    AudioProtocolResult<AudioTtsResult> synthesize(
            ModelRuntimeSpec runtime, AudioTtsInvocation invocation);

    AudioProtocolResult<AudioSttResult> transcribe(
            ModelRuntimeSpec runtime, AudioSttInvocation invocation);
}
