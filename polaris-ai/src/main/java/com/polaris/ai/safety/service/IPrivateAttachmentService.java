package com.polaris.ai.safety.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.polaris.ai.safety.dto.PrivateAttachmentUploadResult;
import com.polaris.ai.safety.dto.ResolvedAttachment;
import com.polaris.ai.safety.model.PrivateAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI 私有附件服务层接口
 *
 * @author polaris
 */
public interface IPrivateAttachmentService extends IService<PrivateAttachment> {

    /**
     * 暂存上传的私有附件
     */
    PrivateAttachmentUploadResult stage(MultipartFile file, Long userId);

    /**
     * 解析附件用于下载或查看，严格校验归属用户
     */
    ResolvedAttachment resolveForDownload(String token, Long userId);

    /**
     * 批量解析用户拥有的有效附件（供 AI 输入前置准备使用）
     */
    List<ResolvedAttachment> resolveOwned(List<String> tokens, Long userId);

    /**
     * 删除处于暂存态的附件
     */
    void deleteStaged(String token, Long userId);

    /**
     * 标记附件已被会话引用
     */
    void markClaimed(List<String> tokens, Long userId);

    /**
     * 标记附件因输入违规被拒绝
     */
    void markRejected(List<String> tokens, Long userId);
}
