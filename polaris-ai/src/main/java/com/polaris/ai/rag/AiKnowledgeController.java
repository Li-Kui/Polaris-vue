package com.polaris.ai.rag;

import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.config.PolarisConfig;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import com.polaris.common.enums.BusinessType;
import com.polaris.common.utils.SecurityUtils;
import com.polaris.common.utils.file.FileUploadUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI 知识库管理控制器
 *
 * @author polaris
 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "AI知识库管理")
@RestController
@RequestMapping("/ai/knowledge")
public class AiKnowledgeController extends BaseController {
    @Autowired
    private AiKnowledgeService aiKnowledgeService;

    // ================================================================
    //  知识库管理接口
    // ================================================================

    /**
     * 查询知识库列表
     */
    @Operation(summary = "查询知识库列表")
    @GetMapping("/list")
    public ResultData<Page<AiKnowledgeBase>> list(AiKnowledgeBase knowledgeBase) {
        startPage();
        List<AiKnowledgeBase> list = aiKnowledgeService.listKnowledgeBase(knowledgeBase);
        return ok(getDataPage(list));
    }

    /**
     * 获取知识库详细信息
     */
    @Operation(summary = "获取知识库详细信息")
    @GetMapping("/{id}")
    public ResultData getInfo(@PathVariable Long id) {
        return ok(aiKnowledgeService.selectKnowledgeBaseById(id));
    }

    /**
     * 新增知识库
     */
    @Operation(summary = "新增知识库")
    @Log(title = "知识库管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData add(@RequestBody AiKnowledgeBase knowledgeBase) {
        knowledgeBase.setCreateBy(SecurityUtils.getUsername());
        // 自动绑定当前创建用户所在的部门ID，实现物理归属划分
        if (SecurityUtils.getLoginUser() != null && SecurityUtils.getLoginUser().getUser() != null) {
            knowledgeBase.setDeptId(SecurityUtils.getLoginUser().getUser().getDeptId());
        }
        return toAjaxResult(aiKnowledgeService.insertKnowledgeBase(knowledgeBase));
    }

    /**
     * 修改知识库
     */
    @Operation(summary = "修改知识库")
    @Log(title = "知识库管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData edit(@RequestBody AiKnowledgeBase knowledgeBase) {
        knowledgeBase.setUpdateBy(SecurityUtils.getUsername());
        return toAjaxResult(aiKnowledgeService.updateKnowledgeBase(knowledgeBase));
    }

    /**
     * 删除知识库
     */
    @Operation(summary = "删除知识库")
    @Log(title = "知识库管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData remove(@PathVariable Long id) {
        return toAjaxResult(aiKnowledgeService.deleteKnowledgeBase(id));
    }

    // ================================================================
    //  文档管理接口
    // ================================================================

    /**
     * 查询指定知识库下的文档列表
     */
    @Operation(summary = "查询指定知识库下的文档列表")
    @GetMapping("/document/list")
    public ResultData<Page<AiDocument>> documentList(AiDocument document) {
        startPage();
        List<AiDocument> list = aiKnowledgeService.listDocument(document);
        return ok(getDataPage(list));
    }

    /**
     * 上传文档并进行异步向量化导入
     */
    @Operation(summary = "上传文档并开始向量化导入")
    @Log(title = "知识库文档管理", businessType = BusinessType.INSERT)
    @PostMapping("/document/upload")
    public ResultData uploadDocument(
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId,
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return fail("上传文件不可为空");
        }

        try {
            // 1. 上传文件到北辰本地上传目录
            String filePath = PolarisConfig.getUploadPath();
            String fileUrl = FileUploadUtils.upload(filePath, file);

            // 2. 插入文档记录到数据库
            AiDocument document = new AiDocument();
            document.setKnowledgeBaseId(knowledgeBaseId);
            document.setName(file.getOriginalFilename());
            document.setFileUrl(fileUrl);
            document.setCreateBy(SecurityUtils.getUsername());
            aiKnowledgeService.insertDocument(document);

            // 3. 异步触发向量化入库
            aiKnowledgeService.importDocumentAsync(document.getId());

            return ResultData.ok(document, "上传成功，后台开始执行向量化导入");
        } catch (Exception e) {
            return fail("文档上传及导入失败: " + e.getMessage());
        }
    }

    /**
     * 删除指定文档
     */
    @Operation(summary = "删除指定文档")
    @Log(title = "知识库文档管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/document/{id}")
    public ResultData removeDocument(@PathVariable Long id) {
        return toAjaxResult(aiKnowledgeService.deleteDocument(id));
    }

    /**
     * 重新构建单个文档的向量索引
     */
    @Operation(summary = "重新构建单个文档的向量索引")
    @Log(title = "知识库文档管理", businessType = BusinessType.UPDATE)
    @PostMapping("/document/{id}/rebuild")
    public ResultData rebuildDocument(@PathVariable Long id) {
        aiKnowledgeService.importDocumentAsync(id);
        return ok("已加入后台任务重新向量化");
    }
}
