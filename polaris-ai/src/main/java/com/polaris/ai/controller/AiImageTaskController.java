package com.polaris.ai.controller;

import com.polaris.ai.domain.AiImageTask;
import com.polaris.ai.image.ImageGenCommand;
import com.polaris.ai.service.IImageGenerationService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * AI 图像生成任务状态轮询控制器
 * 
 * @author polaris
 */
@Tag(name = "AI绘画状态查询")
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/chat/image-task")
public class AiImageTaskController extends BaseController {

    @Autowired
    private com.polaris.ai.service.IAiImageTaskService imageTaskService;

    @Autowired
    private IImageGenerationService imageGenerationService;

    /**
     * 直连发起绘图任务（显式绘图面板使用，绕开聊天工具）
     * POST /ai/chat/image-task/generate
     *
     * @param cmd 绘图命令（含能力、源图、遮罩、尺寸等）
     * @return 已落库任务（含 taskId，前端据此轮询状态）
     */
    @Operation(summary = "直连发起绘图任务")
    @PostMapping("/generate")
    public ResultData generate(@RequestBody ImageGenCommand cmd) {
        if (cmd.getPrompt() == null || cmd.getPrompt().trim().isEmpty()) {
            return fail("提示词不能为空");
        }
        try {
            AiImageTask task = imageGenerationService.submit(cmd);
            return ok(task);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("发起绘图任务失败: " + e.getMessage());
        }
    }

    /**
     * 获取绘图任务状态
     * GET /ai/chat/image-task/status/{taskId}
     *
     * @param taskId 任务 ID
     * @return 绘图任务详情（含 status 状态与 imageUrl 链接）
     */
    @Operation(summary = "获取绘图任务状态")
    @GetMapping("/status/{taskId}")
    public ResultData getTaskStatus(@PathVariable String taskId) {
        AiImageTask task = imageTaskService.getTask(taskId);
        if (task == null) {
            return fail("未找到对应绘图任务");
        }
        return ok(task);
    }

    /**
     * 查询当前后台可用绘图能力（前端据此动态渲染能力面板，不展示无模型支持的能力）
     * GET /ai/chat/image-task/capabilities
     *
     * @return 能力 code 列表
     */
    @Operation(summary = "查询当前可用绘图能力")
    @GetMapping("/capabilities")
    public ResultData capabilities() {
        return ok(imageGenerationService.listSupportedModes());
    }
}
