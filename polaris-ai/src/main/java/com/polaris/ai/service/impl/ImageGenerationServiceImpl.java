package com.polaris.ai.service.impl;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.domain.AiImageTask;
import com.polaris.ai.enums.ImageGenerationMode;
import com.polaris.ai.image.ImageGenCommand;
import com.polaris.ai.image.ImageStorageHelper;
import com.polaris.ai.modelcenter.runtime.ImageRuntimeService;
import com.polaris.ai.modelcenter.service.ModelDefaultInternalService;
import com.polaris.ai.runtime.ModelRuntimeSnapshot;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.image.ImageCapabilityInvocation;
import com.polaris.ai.service.IAiImageTaskService;
import com.polaris.ai.service.IImageGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;

/** 图片任务编排：同步冻结 Runtime，异步阶段不再选择模型或读取旧模型字段。 */
@Slf4j
@Service
public class ImageGenerationServiceImpl implements IImageGenerationService {

    private static final int MAX_IMAGES_PER_REQUEST = 10;

    private final ImageRuntimeService runtimeService;
    private final ModelDefaultInternalService defaultService;
    private final ImageStorageHelper imageStorageHelper;
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final IAiImageTaskService imageTaskService;
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    public ImageGenerationServiceImpl(
            ImageRuntimeService runtimeService,
            ModelDefaultInternalService defaultService,
            ImageStorageHelper imageStorageHelper,
            ThreadPoolTaskExecutor threadPoolTaskExecutor,
            IAiImageTaskService imageTaskService) {
        this.runtimeService = runtimeService;
        this.defaultService = defaultService;
        this.imageStorageHelper = imageStorageHelper;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
        this.imageTaskService = imageTaskService;
    }

    @Override
    public AiImageTask submit(ImageGenCommand command) {
        ImageGenerationMode mode = requireMode(command);
        List<String> sources = command.getSourceImages() == null
                ? List.of() : List.copyOf(command.getSourceImages());
        validateInputs(mode, command, sources);

        String capability = capabilityFor(mode);
        Long modelId = command.getModelConfigId() != null
                ? command.getModelConfigId()
                : defaultService.resolveModelId(capability);
        int targetCount = Math.min(
                command.getN() > 0 ? command.getN() : 1,
                MAX_IMAGES_PER_REQUEST);
        String taskId = "img_" + UUID.randomUUID().toString()
                .replace("-", "");
        ImageCapabilityInvocation initial = invocation(
                capability, mode, command, sources, command.getPrompt(),
                command.getSize(), targetCount, taskId);

        // 请求线程内完成模型权限、Capability 与 Connection 校验。
        ModelRuntimeSpec runtime = runtimeService.resolve(modelId, initial);
        String size = selectSize(command.getSize(), runtime);
        ImageCapabilityInvocation resolvedInvocation = invocation(
                capability, mode, command, sources, command.getPrompt(),
                size, targetCount, taskId);
        ModelRuntimeSnapshot snapshot = runtimeService.snapshot(runtime);

        AiImageTask task = createTask(
                command, mode, sources, targetCount, size, taskId,
                runtime, snapshot);
        imageTaskService.createTask(task);

        threadPoolTaskExecutor.execute(() -> executeAsync(
                command, mode, sources, targetCount, size, taskId,
                runtime, resolvedInvocation));
        return task;
    }

    private void executeAsync(
            ImageGenCommand command,
            ImageGenerationMode mode,
            List<String> sources,
            int targetCount,
            String size,
            String taskId,
            ModelRuntimeSpec runtime,
            ImageCapabilityInvocation initialInvocation) {
        long start = System.currentTimeMillis();
        try {
            List<String> urls = new ArrayList<>();
            List<String> prompts = command.getPrompts();
            if (prompts != null && !prompts.isEmpty()) {
                for (String prompt : prompts) {
                    urls.addAll(runtimeService.execute(runtime, invocation(
                            runtime.capabilityCode(), mode, command, sources,
                            prompt, size, 1, taskId)).value().imageUrls());
                }
            } else {
                int maxNativeCount = maxNativeCount(runtime);
                int remaining = targetCount;
                while (remaining > 0) {
                    int batch = Math.min(remaining, maxNativeCount);
                    ImageCapabilityInvocation invocation =
                            remaining == targetCount && batch == targetCount
                            ? initialInvocation
                            : invocation(runtime.capabilityCode(), mode, command,
                            sources, command.getPrompt(), size, batch, taskId);
                    urls.addAll(runtimeService.execute(runtime, invocation)
                            .value().imageUrls());
                    remaining -= batch;
                }
            }
            if (urls.isEmpty()) {
                throw new IllegalStateException("IMAGE_RESULT_EMPTY");
            }
            String rawResult = urls.size() == 1
                    ? urls.get(0) : JSON.toJSONString(urls);
            String stored = imageStorageHelper.transferAllToLocal(
                    rawResult, threadPoolTaskExecutor);
            imageTaskService.markSuccess(
                    taskId, stored == null || stored.isBlank()
                            ? rawResult : stored,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("图片任务执行失败, taskId={}", taskId, e);
            imageTaskService.markFail(taskId, safeMessage(e));
        }
    }

    private AiImageTask createTask(
            ImageGenCommand command,
            ImageGenerationMode mode,
            List<String> sources,
            int count,
            String size,
            String taskId,
            ModelRuntimeSpec runtime,
            ModelRuntimeSnapshot snapshot) {
        AiImageTask task = new AiImageTask();
        task.setTaskId(taskId);
        task.setPrompt(command.getPrompt());
        task.setStatus("0");
        task.setGenerationMode(mode.getCode());
        task.setModelConfigId(runtime.modelId());
        task.setModelRevision(runtime.modelRevision());
        task.setConnectionId(runtime.connectionId());
        task.setConnectionRevision(runtime.connectionRevision());
        task.setCapabilityCode(runtime.capabilityCode());
        task.setSchemaHash(runtime.schemaHash());
        task.setRuntimeSnapshot(writeSnapshot(snapshot));
        task.setProvider(runtime.providerCode());
        task.setConversationId(command.getConversationId());
        if (!sources.isEmpty()) {
            task.setSourceImages(JSON.toJSONString(sources));
        }
        if (command.getMaskImage() != null
                && !command.getMaskImage().isBlank()) {
            task.setMaskImage(command.getMaskImage());
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("size", size);
        parameters.put("n", count);
        parameters.put("negativePrompt", command.getNegativePrompt());
        task.setImageParams(JSON.toJSONString(parameters));
        task.setCreateTime(new Date());
        return task;
    }

    private ImageCapabilityInvocation invocation(
            String capability,
            ImageGenerationMode mode,
            ImageGenCommand command,
            List<String> sources,
            String prompt,
            String size,
            int count,
            String taskId) {
        return new ImageCapabilityInvocation(
                capability, mode.getCode(), prompt,
                command.getNegativePrompt(), sources, command.getMaskImage(),
                size, count, taskId, command.getExpandParams(),
                command.getUpscaleFactor(), command.getExtra());
    }

    private ImageGenerationMode requireMode(ImageGenCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("IMAGE_COMMAND_REQUIRED");
        }
        ImageGenerationMode mode = ImageGenerationMode.fromCode(
                command.getGenerationMode());
        if (mode == null) {
            throw new IllegalArgumentException(
                    "不支持的生成能力: " + command.getGenerationMode());
        }
        return mode;
    }

    private void validateInputs(
            ImageGenerationMode mode,
            ImageGenCommand command,
            List<String> sources) {
        if (mode.isNeedsSource() && sources.isEmpty()) {
            throw new IllegalArgumentException(
                    "能力[" + mode.getLabel() + "]需要至少一张源图");
        }
        if (mode.isNeedsMask() && (command.getMaskImage() == null
                || command.getMaskImage().isBlank())) {
            throw new IllegalArgumentException(
                    "能力[" + mode.getLabel() + "]需要遮罩图");
        }
    }

    private String capabilityFor(ImageGenerationMode mode) {
        return switch (mode) {
            case TEXT_TO_IMAGE -> "IMAGE_GENERATION";
            case IMAGE_TO_IMAGE, MULTI_IMAGE -> "IMAGE_VARIATION";
            case INPAINTING, OBJECT_REMOVAL -> "IMAGE_INPAINT";
            default -> "IMAGE_EDIT";
        };
    }

    private String selectSize(String requested, ModelRuntimeSpec runtime) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        Object configured = runtime.invocationParameters().get("size");
        return configured == null ? "1024x1024" : configured.toString();
    }

    private int maxNativeCount(ModelRuntimeSpec runtime) {
        String model = runtime.modelName() == null
                ? "" : runtime.modelName().toLowerCase();
        if (model.contains("dall-e-3")) {
            return 1;
        }
        return "ARK".equalsIgnoreCase(runtime.providerCode()) ? 10 : 4;
    }

    private String writeSnapshot(ModelRuntimeSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("IMAGE_RUNTIME_SNAPSHOT_FAILED", e);
        }
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName() : message;
    }

    @Override
    public List<String> listSupportedModes() {
        List<String> result = new ArrayList<>();
        for (ImageGenerationMode mode : ImageGenerationMode.values()) {
            result.add(mode.getCode());
        }
        return Collections.unmodifiableList(result);
    }
}
