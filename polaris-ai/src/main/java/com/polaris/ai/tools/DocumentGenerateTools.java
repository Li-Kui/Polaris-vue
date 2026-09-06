package com.polaris.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.polaris.ai.tools.base.*;
import com.polaris.ai.utils.ToolSseHolder;
import com.polaris.common.config.PolarisConfig;
import com.polaris.common.constant.Constants;
import com.polaris.common.utils.DateUtils;
import com.polaris.common.utils.StringUtils;
import com.polaris.common.utils.uuid.IdUtils;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CancellationException;

/**
 * AI 工具类 —— 提供在对话中生成精美 Word、Excel 文件并下载的功能
 *
 * @author polaris
 */
@Slf4j
@Component
@AiAgentTool(value = "文档生成工具", scope = ToolScope.UNIVERSAL, requirement = ToolRequirement.NONE)
public class DocumentGenerateTools implements AiTool {

    /**
     * 生成 Excel 并返回可供下载的绝对链接
     */
    @Tool("根据给定的数据内容和列名生成Excel电子表格文件(.xlsx)，并返回可以直接下载的超链接。")
    @AiToolPermission(sideEffect = ToolSideEffect.WRITE)
    public String generateExcel(
            @P("要生成的文件名，例如 '2026年销售业绩表.xlsx'，必须以 .xlsx 结尾") String fileName,
            @P("表格的主标题，将显示在表格第一行第一列（合并单元格居中），例如：'北辰AI 2026年销售业绩汇总表'") String title,
            @P("JSON格式的数据内容，必须包含 headers(列标题数组) 和 rows(数据行二维数组)。例如：{\"headers\":[\"姓名\",\"部门\",\"绩效\"],\"rows\":[[\"张三\",\"研发部\",\"A\"],[\"李四\",\"市场部\",\"B\"]]}。请确保数据结构完整。") String jsonData
    ) {
        ToolSseHolder.ensureActive();
        log.info(">>> AI 请求生成 Excel. fileName: {}, title: {}", fileName, title);
        
        if (StringUtils.isEmpty(fileName) || !fileName.toLowerCase().endsWith(".xlsx")) {
            fileName = "ai_export_" + System.currentTimeMillis() + ".xlsx";
        }
        
        try {
            JSONObject json = JSON.parseObject(jsonData);
            JSONArray headers = json.getJSONArray("headers");
            JSONArray rows = json.getJSONArray("rows");
            
            if (headers == null || headers.isEmpty()) {
                return "生成失败：未提供表格列标题 headers";
            }
            
            // 1. 创建 XSSFWorkbook
            XSSFWorkbook workbook = new XSSFWorkbook();
            XSSFSheet sheet = workbook.createSheet(StringUtils.isNotEmpty(title) ? title : "数据汇总");
            sheet.setDefaultRowHeightInPoints(20);
            
            // 2. 创建精美的主题色彩（科技感商务蓝配色）
            java.awt.Color primaryColor = new java.awt.Color(31, 78, 121); // #1F4E79 深蓝
            java.awt.Color zebraColor = new java.awt.Color(242, 245, 249);   // #F2F5F9 极淡蓝灰
            java.awt.Color borderColor = new java.awt.Color(211, 211, 211); // #D3D3D3 浅灰边框
            
            XSSFColor headerBg = new XSSFColor(primaryColor, new DefaultIndexedColorMap());
            XSSFColor zebraBg = new XSSFColor(zebraColor, new DefaultIndexedColorMap());
            XSSFColor borderBg = new XSSFColor(borderColor, new DefaultIndexedColorMap());
            
            // 3. 构建常用字体
            XSSFFont titleFont = workbook.createFont();
            titleFont.setFontName("微软雅黑");
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setBold(true);
            titleFont.setColor(new XSSFColor(java.awt.Color.WHITE, new DefaultIndexedColorMap()));
            
            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setBold(true);
            headerFont.setColor(new XSSFColor(java.awt.Color.WHITE, new DefaultIndexedColorMap()));
            
            XSSFFont bodyFont = workbook.createFont();
            bodyFont.setFontName("微软雅黑");
            bodyFont.setFontHeightInPoints((short) 10);
            
            // 4. 构建样式
            // 大标题样式
            XSSFCellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(headerBg);
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            
            // 表头样式
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(headerBg);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(headerStyle, BorderStyle.THIN, borderBg);
            
            // 奇数行样式（斑马纹）
            XSSFCellStyle oddStyle = workbook.createCellStyle();
            oddStyle.setFont(bodyFont);
            oddStyle.setFillForegroundColor(zebraBg);
            oddStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            oddStyle.setAlignment(HorizontalAlignment.CENTER);
            oddStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(oddStyle, BorderStyle.THIN, borderBg);
            
            // 偶数行样式
            XSSFCellStyle evenStyle = workbook.createCellStyle();
            evenStyle.setFont(bodyFont);
            evenStyle.setAlignment(HorizontalAlignment.CENTER);
            evenStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(evenStyle, BorderStyle.THIN, borderBg);
            
            int currentRowIndex = 0;
            
            // 5. 写入主标题
            if (StringUtils.isNotEmpty(title)) {
                Row titleRow = sheet.createRow(currentRowIndex);
                titleRow.setHeightInPoints(40);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue(title);
                titleCell.setCellStyle(titleStyle);
                
                // 合并大标题单元格
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.size() - 1));
                currentRowIndex++;
            }
            
            // 6. 写入表头
            Row headerRow = sheet.createRow(currentRowIndex);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.getString(i));
                cell.setCellStyle(headerStyle);
            }
            currentRowIndex++;
            
            // 7. 写入数据行
            if (rows != null && !rows.isEmpty()) {
                for (int r = 0; r < rows.size(); r++) {
                    ToolSseHolder.ensureActive();
                    Row row = sheet.createRow(currentRowIndex);
                    row.setHeightInPoints(22);
                    
                    JSONArray rowData = rows.getJSONArray(r);
                    CellStyle currentStyle = (r % 2 == 1) ? oddStyle : evenStyle;
                    
                    for (int c = 0; c < headers.size(); c++) {
                        Cell cell = row.createCell(c);
                        if (rowData != null && c < rowData.size()) {
                            Object val = rowData.get(c);
                            if (val instanceof Number) {
                                cell.setCellValue(((Number) val).doubleValue());
                            } else {
                                cell.setCellValue(val != null ? val.toString() : "");
                            }
                        } else {
                            cell.setCellValue("");
                        }
                        cell.setCellStyle(currentStyle);
                    }
                    currentRowIndex++;
                }
            }
            
            // 8. 自动调整列宽，设置最小保底宽度，美化排版
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                int columnWidth = sheet.getColumnWidth(i);
                // 确保列宽至少 16 个字符宽度，防止遮挡
                if (columnWidth < 16 * 256) {
                    sheet.setColumnWidth(i, 16 * 256);
                } else {
                    sheet.setColumnWidth(i, columnWidth + 4 * 256); // 增加内边距
                }
            }
            
            // 9. 物理保存文件
            String downloadPath = saveDocument(workbook, fileName);
            
            log.info(">>> Excel 生成成功，相对下载路径为: {}", downloadPath);
            return "生成成功！您可以点击下载生成的 Excel 表格文件：[" + fileName + "](" + downloadPath + ")";
            
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            log.error(">>> Excel 电子表格文件生成失败", e);
            return "生成失败，发生系统异常：" + e.getMessage();
        }
    }
    
    /**
     * 生成 Word 并返回可供下载的绝对链接
     */
    @Tool("根据指定的标题和段落内容生成Word文档(.docx)，支持大标题、二级标题、正文段落和表格的排版，并返回下载链接。")
    @AiToolPermission(sideEffect = ToolSideEffect.WRITE)
    public String generateWord(
            @P("要生成的文件名，例如 '项目设计说明书.docx'，必须以 .docx 结尾") String fileName,
            @P("文档的大标题，将显示在Word文档的第一页顶部，例如：'北辰智能体工作流设计方案'") String title,
            @P("JSON数组格式的内容段落，每一个元素必须包含 type 和对应的内容。支持类型包括：\n" +
               "1. type='heading'（二级标题，内容字段为 text）\n" +
               "2. type='paragraph'（正文段落，内容字段为 text）\n" +
               "3. type='table'（表格，必须包含 headers 数组和 rows 二维数组，如: {\"headers\":[\"指标\",\"数值\"],\"rows\":[[\"访问量\",\"10万\"],[\"转化率\",\"2%\"]]}）\n" +
               "例如：[{\"type\":\"heading\",\"text\":\"一、项目背景\"},{\"type\":\"paragraph\",\"text\":\"这是一个AI驱动的管理系统...\"},{\"type\":\"table\",\"headers\":[\"姓名\",\"角色\"],\"rows\":[[\"小明\",\"项目经理\"]]}]") String contentJson
    ) {
        ToolSseHolder.ensureActive();
        log.info(">>> AI 请求生成 Word. fileName: {}, title: {}", fileName, title);
        
        if (StringUtils.isEmpty(fileName) || !fileName.toLowerCase().endsWith(".docx")) {
            fileName = "ai_doc_" + System.currentTimeMillis() + ".docx";
        }
        
        try (XWPFDocument doc = new XWPFDocument()) {
            // 1. 设置主标题
            if (StringUtils.isNotEmpty(title)) {
                XWPFParagraph titlePara = doc.createParagraph();
                titlePara.setAlignment(ParagraphAlignment.CENTER);
                titlePara.setSpacingAfter(400); // 增加段后间距
                
                XWPFRun titleRun = titlePara.createRun();
                titleRun.setText(title);
                titleRun.setFontSize(22); // 二号
                titleRun.setBold(true);
                titleRun.setFontFamily("微软雅黑");
                titleRun.setColor("1F4E79"); // 标题字海军蓝
            }
            
            // 2. 解析正文 JSON
            JSONArray elements = JSON.parseArray(contentJson);
            if (elements != null && !elements.isEmpty()) {
                for (int i = 0; i < elements.size(); i++) {
                    JSONObject el = elements.getJSONObject(i);
                    String type = el.getString("type");
                    
                    if ("heading".equalsIgnoreCase(type)) {
                        // 二级标题
                        XWPFParagraph headingPara = doc.createParagraph();
                        headingPara.setSpacingBefore(300);
                        headingPara.setSpacingAfter(120);
                        
                        XWPFRun run = headingPara.createRun();
                        run.setText(el.getString("text"));
                        run.setFontSize(15); // 三号/小三
                        run.setBold(true);
                        run.setFontFamily("微软雅黑");
                        run.setColor("2F5597"); // 淡宝蓝
                        
                    } else if ("paragraph".equalsIgnoreCase(type)) {
                        // 正文段落
                        XWPFParagraph pPara = doc.createParagraph();
                        pPara.setSpacingAfter(180);
                        pPara.setIndentationFirstLine(400); // 首行缩进
                        
                        XWPFRun run = pPara.createRun();
                        run.setText(el.getString("text"));
                        run.setFontSize(11); // 小四
                        run.setFontFamily("宋体");
                        run.setColor("333333"); // 深灰正文，阅读更柔和
                        
                    } else if ("table".equalsIgnoreCase(type)) {
                        // 表格
                        JSONArray headers = el.getJSONArray("headers");
                        JSONArray rows = el.getJSONArray("rows");
                        if (headers != null && !headers.isEmpty()) {
                            XWPFTable table = doc.createTable();
                            
                            // 设置表格占满宽度
                            CTTblPr tblPr = table.getCTTbl().getTblPr();
                            if (tblPr != null) {
                                CTTblWidth tblWidth = tblPr.isSetTblW() ? tblPr.getTblW() : tblPr.addNewTblW();
                                tblWidth.setW(java.math.BigInteger.valueOf(5000));
                                tblWidth.setType(STTblWidth.PCT);
                            }
                            
                            // 1) 写入表头
                            XWPFTableRow headerRow = table.getRow(0);
                            for (int h = 0; h < headers.size(); h++) {
                                XWPFTableCell cell = (h == 0) ? headerRow.getCell(0) : headerRow.createCell();
                                cell.setText(headers.getString(h));
                                cell.setColor("1F4E79"); // 表头背景海军蓝
                                cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                                // 白色加粗字体通过 Run 设置较复杂，此处只使用普通文本设置
                            }
                            
                            // 2) 写入数据行
                            if (rows != null) {
                                for (int r = 0; r < rows.size(); r++) {
                                    JSONArray rowData = rows.getJSONArray(r);
                                    XWPFTableRow row = table.createRow();
                                    
                                    for (int c = 0; c < headers.size(); c++) {
                                        XWPFTableCell cell = row.getCell(c);
                                        if (cell == null) {
                                            cell = row.createCell();
                                        }
                                        String cellValue = (rowData != null && c < rowData.size()) ? rowData.getString(c) : "";
                                        cell.setText(cellValue);
                                        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                                        
                                        // 斑马纹奇数行背景微调
                                        if (r % 2 == 1) {
                                            cell.setColor("F2F5F9");
                                        }
                                    }
                                }
                            }
                            // 增加表格后空行，使排版疏密得体
                            XWPFParagraph emptyP = doc.createParagraph();
                            emptyP.setSpacingBefore(120);
                        }
                    }
                }
            }
            
            // 3. 保存文件
            String downloadPath = saveDocument(doc, fileName);
            
            log.info(">>> Word 生成成功，相对下载路径为: {}", downloadPath);
            return "生成成功！您可以点击下载生成的 Word 文档文件：[" + fileName + "](" + downloadPath + ")";
            
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            log.error(">>> Word 电子文档文件生成失败", e);
            return "生成失败，发生系统异常：" + e.getMessage();
        }
    }
    
    // ──────────────────────────────────────────
    // 内部私有辅助方法
    // ──────────────────────────────────────────
    
    /**
     * 将 Workbook 保存到指定目录，返回相对 URL 下载地址
     */
    private String saveDocument(Workbook workbook, String originalFileName) throws IOException {
        ToolSseHolder.ensureActive();
        String baseDir = PolarisConfig.getUploadPath();
        String datePath = DateUtils.datePath();
        String fileDir = baseDir + File.separator + datePath;
        
        File dir = new File(fileDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        String cleanFileName = FilenameUtils.getBaseName(originalFileName).replaceAll("[\\\\/:*?\"<>|]", "_");
        String extension = FilenameUtils.getExtension(originalFileName);
        if (StringUtils.isEmpty(extension)) {
            extension = "xlsx";
        }
        
        String uniqueFileName = IdUtils.fastSimpleUUID() + "_" + cleanFileName + "." + extension;
        String absolutePath = fileDir + File.separator + uniqueFileName;
        File outputFile = new File(absolutePath);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            ToolSseHolder.ensureActive();
            workbook.write(fos);
            ToolSseHolder.ensureActive();
        } catch (IOException | RuntimeException e) {
            if (outputFile.exists() && !outputFile.delete()) {
                log.warn(">>> 删除未完成的 Excel 文件失败: {}", absolutePath);
            }
            throw e;
        }
        
        return Constants.RESOURCE_PREFIX + "/upload/" + datePath + "/" + uniqueFileName;
    }
    
    /**
     * 将 XWPFDocument 保存到指定目录，返回相对 URL 下载地址
     */
    private String saveDocument(XWPFDocument document, String originalFileName) throws IOException {
        ToolSseHolder.ensureActive();
        String baseDir = PolarisConfig.getUploadPath();
        String datePath = DateUtils.datePath();
        String fileDir = baseDir + File.separator + datePath;
        
        File dir = new File(fileDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        String cleanFileName = FilenameUtils.getBaseName(originalFileName).replaceAll("[\\\\/:*?\"<>|]", "_");
        String extension = FilenameUtils.getExtension(originalFileName);
        if (StringUtils.isEmpty(extension)) {
            extension = "docx";
        }
        
        String uniqueFileName = IdUtils.fastSimpleUUID() + "_" + cleanFileName + "." + extension;
        String absolutePath = fileDir + File.separator + uniqueFileName;
        File outputFile = new File(absolutePath);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            ToolSseHolder.ensureActive();
            document.write(fos);
            ToolSseHolder.ensureActive();
        } catch (IOException | RuntimeException e) {
            if (outputFile.exists() && !outputFile.delete()) {
                log.warn(">>> 删除未完成的 Word 文件失败: {}", absolutePath);
            }
            throw e;
        }
        
        return Constants.RESOURCE_PREFIX + "/upload/" + datePath + "/" + uniqueFileName;
    }
    
    /**
     * 辅助设置 Excel 单元格边框与边框颜色
     */
    private void setBorder(XSSFCellStyle style, BorderStyle borderStyle, XSSFColor borderColor) {
        style.setBorderTop(borderStyle);
        style.setBorderBottom(borderStyle);
        style.setBorderLeft(borderStyle);
        style.setBorderRight(borderStyle);
        
        style.setTopBorderColor(borderColor);
        style.setBottomBorderColor(borderColor);
        style.setLeftBorderColor(borderColor);
        style.setRightBorderColor(borderColor);
    }
}
