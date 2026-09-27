package com.polaris.ai.runtime.image;

import com.polaris.ai.image.ImageProviderDispatcher;
import org.springframework.stereotype.Component;

@Component
public final class ImageInpaintCapabilityAdapter
        extends AbstractImageCapabilityAdapter {

    public ImageInpaintCapabilityAdapter(ImageProviderDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public String capabilityCode() {
        return "IMAGE_INPAINT";
    }
}
