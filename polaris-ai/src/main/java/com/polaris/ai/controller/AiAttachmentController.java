package com.polaris.ai.controller;

import com.polaris.ai.safety.dto.PrivateAttachmentUploadResult;
import com.polaris.ai.safety.dto.ResolvedAttachment;
import com.polaris.ai.safety.service.IPrivateAttachmentService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Tag(name = "AI私有附件管理")
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/attachments")
public class AiAttachmentController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AiAttachmentController.class);

    @Autowired
    private IPrivateAttachmentService privateAttachmentService;

    public void setPrivateAttachmentService(IPrivateAttachmentService privateAttachmentService) {
        this.privateAttachmentService = privateAttachmentService;
    }

    @Operation(summary = "上传并暂存AI附件")
    @PostMapping
    public ResultData upload(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getUserId();
        PrivateAttachmentUploadResult result = privateAttachmentService.stage(file, userId);
        return ok(result);
    }

    @Operation(summary = "下载/查看当前用户拥有的AI附件")
    @GetMapping("/{token}/content")
    public void download(@PathVariable String token, HttpServletResponse response) {
        Long userId = SecurityUtils.getUserId();
        ResolvedAttachment resolved = privateAttachmentService.resolveForDownload(token, userId);
        try {
            response.setContentType(resolved.mediaType() != null ? resolved.mediaType() : "application/octet-stream");
            String encodedName = URLEncoder.encode(resolved.originalName(), StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedName + "\"");
            Files.copy(resolved.path(), response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            log.error("流式输出附件内容失败, token={}", token, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "删除暂存AI附件")
    @DeleteMapping("/{token}")
    public ResultData delete(@PathVariable String token) {
        Long userId = SecurityUtils.getUserId();
        privateAttachmentService.deleteStaged(token, userId);
        return ok();
    }
}
