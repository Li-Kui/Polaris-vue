package com.polaris.ai.runtime.image;

import com.polaris.ai.image.ImageProviderDispatcher;
import org.springframework.stereotype.Component;

@Component
public final class ImageVariationCapabilityAdapter
        extends AbstractImageCapabilityAdapter {

    public ImageVariationCapabilityAdapter(ImageProviderDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public String capabilityCode() {
        return "IMAGE_VARIATION";
    }
}
