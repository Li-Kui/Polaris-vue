package com.polaris.ai.service;

import com.polaris.ai.domain.AiImageTask;
import com.polaris.ai.image.ImageGenCommand;

/**
 * 绘图任务编排服务：聊天工具与直连接口共用。
 * 负责能力入参校验、能力路由选模型、任务落库与异步派发。
 *
 * @author polaris
 */
public interface IImageGenerationService {

    /**
     * 提交绘图任务：校验能力入参 → 路由模型 → 落库 → 异步派发。
     *
     * @param cmd 绘图命令
     * @return 已落库的任务（含 taskId、status=0）
     * @throws IllegalArgumentException 能力不支持或必填源图/遮罩缺失
     * @throws IllegalStateException    无可用模型或 Key 为空
     */
    AiImageTask submit(ImageGenCommand cmd);

    /**
     * 查询当前后台可用的绘图能力 code 列表（已配置且启用的 IMAGE 模型声明支持的并集）。
     *
     * @return 能力 code 列表，可能为空
     */
    java.util.List<String> listSupportedModes();
}
