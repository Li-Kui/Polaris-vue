package com.polaris.ai.runtime.image;

import com.polaris.ai.image.ImageProviderDispatcher;
import org.springframework.stereotype.Component;

@Component
public final class ImageGenerationCapabilityAdapter
        extends AbstractImageCapabilityAdapter {

    public ImageGenerationCapabilityAdapter(ImageProviderDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public String capabilityCode() {
        return "IMAGE_GENERATION";
    }
}
