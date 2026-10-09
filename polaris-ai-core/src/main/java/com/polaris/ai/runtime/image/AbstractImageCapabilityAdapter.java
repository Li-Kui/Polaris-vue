package com.polaris.ai.runtime.image;

import com.polaris.ai.image.ImageGenRequest;
import com.polaris.ai.image.ImageProviderDispatcher;
import com.polaris.ai.runtime.CapabilityAdapter;
import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.usage.NormalizedUsage;

/** 图片 Capability Adapter 的公共协议桥接。 */
abstract class AbstractImageCapabilityAdapter implements
        CapabilityAdapter<ImageCapabilityInvocation, ImageCapabilityResult> {

    private final ImageProviderDispatcher dispatcher;

    AbstractImageCapabilityAdapter(ImageProviderDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public Class<ImageCapabilityInvocation> invocationType() {
        return ImageCapabilityInvocation.class;
    }

    @Override
    public Class<ImageCapabilityResult> resultType() {
        return ImageCapabilityResult.class;
    }

    @Override
    public ModelExecutionResult<ImageCapabilityResult> execute(
            ModelRuntimeSpec runtime,
            ImageCapabilityInvocation invocation) {
        ImageGenRequest request = new ImageGenRequest();
        request.setRuntime(runtime);
        request.setGenerationMode(invocation.generationMode());
        request.setPrompt(invocation.prompt());
        request.setNegativePrompt(invocation.negativePrompt());
        request.setSourceImageUrls(invocation.sourceImageUrls());
        request.setRefImageUrl(invocation.sourceImageUrls().isEmpty()
                ? null : invocation.sourceImageUrls().get(0));
        request.setMaskImageUrl(invocation.maskImageUrl());
        request.setSize(invocation.size());
        request.setN(invocation.count());
        request.setTaskId(invocation.taskId());
        request.setExpandParams(invocation.expandParams());
        request.setUpscaleFactor(invocation.upscaleFactor());
        request.setExtra(invocation.overrides());
        try {
            return new ModelExecutionResult<>(
                    new ImageCapabilityResult(dispatcher.generateList(request)),
                    NormalizedUsage.unknown(), null);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("IMAGE_PROVIDER_REQUEST_FAILED", e);
        }
    }
}
