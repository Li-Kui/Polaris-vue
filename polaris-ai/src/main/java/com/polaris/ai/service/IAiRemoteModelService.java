package com.polaris.ai.service;

import com.polaris.ai.dto.FetchModelsRequest;

import java.util.List;

/**
 * AI 远程模型列表拉取服务
 * 通过后端代理调用各提供商的 OpenAI 兼容 /models 端点，获取可用模型 ID 列表
 *
 * @author polaris
 */
public interface IAiRemoteModelService {

    /**
     * 拉取远程可用模型列表
     *
     * @param req 包含 provider、apiKey、baseUrl 的请求参数
     * @return 模型 ID 列表
     * @throws IllegalArgumentException 参数校验失败
     * @throws RuntimeException         远程调用异常
     */
    List<String> fetchRemoteModels(FetchModelsRequest req);
}
