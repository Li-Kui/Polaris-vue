package com.polaris.ai.attachment;

import com.polaris.common.config.PolarisConfig;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * AI 对话附件解析工具类
 * 支持解析 PDF, Word (docx), Excel (xlsx/xls), 纯文本 (txt, md, json, csv, xml) 等常用格式文件。
 * 支持自动映射本地 /profile 上传资源以及下载外部 http/https 链接进行解析。
 *
 * @author polaris
 */
public class AttachmentParserHelper {
    private static final Logger log = LoggerFactory.getLogger(AttachmentParserHelper.class);

    /**
     * 解析附件内容
     *
     * @param fileUrl 附件 URL (可能是本地 /profile/... 相对路径，或者是 http/https 网络路径)
     * @return 提取出来的文本内容。若无法解析或出错，将返回对应的友好说明文本。
     */
    public static String parse(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return "";
        }

        fileUrl = fileUrl.trim();
        log.info("开始解析AI对话附件资源: {}", fileUrl);

        boolean isNetworkUrl = fileUrl.toLowerCase(Locale.ROOT).startsWith("http://") 
                || fileUrl.toLowerCase(Locale.ROOT).startsWith("https://");

        File fileToProcess = null;
        boolean needDeleteTempFile = false;

        try {
            if (isNetworkUrl) {
                // 如果是远程网络链接，下载文件到临时文件夹
                String extension = getFileExtension(fileUrl);
                fileToProcess = File.createTempFile("ai_attachment_", extension);
                needDeleteTempFile = true;
                
                downloadFile(fileUrl, fileToProcess);
                log.info("已下载远程文件至临时文件: {}", fileToProcess.getAbsolutePath());
            } else {
                // 如果是本地北辰的 /profile 资源
                String localPath = PolarisConfig.getProfile();
                String relativePath = fileUrl;
                if (fileUrl.startsWith("/profile")) {
                    relativePath = fileUrl.substring("/profile".length());
                }
                fileToProcess = new File(localPath + relativePath);
                log.info("映射到本地绝对路径: {}", fileToProcess.getAbsolutePath());
            }

            if (fileToProcess == null || !fileToProcess.exists()) {
                return "[未找到附件对应的物理文件]";
            }

            // 根据文件名后缀进行多路文本提取
            String fileName = fileToProcess.getName().toLowerCase(Locale.ROOT);
            if (fileName.endsWith(".pdf")) {
                return parsePdf(fileToProcess);
            } else if (fileName.endsWith(".docx")) {
                return parseDocx(fileToProcess);
            } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                return parseExcel(fileToProcess);
            } else if (fileName.endsWith(".txt") || fileName.endsWith(".md") 
                    || fileName.endsWith(".json") || fileName.endsWith(".xml") 
                    || fileName.endsWith(".csv") || fileName.endsWith(".html")
                    || fileName.endsWith(".java") || fileName.endsWith(".py")
                    || fileName.endsWith(".js") || fileName.endsWith(".ts")) {
                return parsePlainText(fileToProcess);
            } else if (fileName.endsWith(".png") || fileName.endsWith(".jpg") 
                    || fileName.endsWith(".jpeg") || fileName.endsWith(".gif") 
                    || fileName.endsWith(".webp") || fileName.endsWith(".bmp")) {
                return "[该附件为图片文件，当前 AI 暂不支持直接读取图片，建议您直接用文字向 AI 描述图片上的内容]";
            } else {
                // 对于未知类型的二进制文件，默认作为文本试读取，读不出再友好提示
                try {
                    String plainText = parsePlainText(fileToProcess);
                    if (plainText != null && plainText.length() > 0) {
                        return plainText;
                    }
                } catch (Exception ignored) {}
                return "[暂不支持该附件类型的文本解析]";
            }

        } catch (Exception e) {
            log.error("附件解析过程发生异常, fileUrl={}", fileUrl, e);
            return "[解析附件时发生错误: " + e.getMessage() + "]";
        } finally {
            if (needDeleteTempFile && fileToProcess != null && fileToProcess.exists()) {
                try {
                    Files.delete(fileToProcess.toPath());
                    log.info("临时文件已成功删除: {}", fileToProcess.getAbsolutePath());
                } catch (Exception e) {
                    log.warn("无法删除临时文件: {}", fileToProcess.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * 提取 PDF 文本
     */
    private static String parsePdf(File file) throws Exception {
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 提取 Word (.docx) 文本
     */
    private static String parseDocx(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    /**
     * 提取 Excel (.xlsx / .xls) 文本并转换为按制表符排列的网格字串
     */
    private static String parseExcel(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            StringBuilder sb = new StringBuilder();
            int sheetsCount = workbook.getNumberOfSheets();
            for (int i = 0; i < sheetsCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                sb.append("[工作表: ").append(sheet.getSheetName()).append("]\n");
                for (Row row : sheet) {
                    StringBuilder rowSb = new StringBuilder();
                    boolean hasContent = false;
                    for (Cell cell : row) {
                        String value = getCellValueAsString(cell).trim();
                        if (!value.isEmpty()) {
                            hasContent = true;
                        }
                        rowSb.append(value).append("\t");
                    }
                    if (hasContent) {
                        sb.append(rowSb).append("\n");
                    }
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * 获取单元格中的值作为字符串
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double numValue = cell.getNumericCellValue();
                if (numValue == (long) numValue) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }

    /**
     * 读取文本文件内容
     */
    private static String parsePlainText(File file) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 下载网络资源文件
     */
    private static void downloadFile(String fileUrl, File targetFile) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");
        conn.connect();
        
        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception("下载失败，HTTP 状态码为: " + conn.getResponseCode());
        }

        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 获取文件后缀名
     */
    private static String getFileExtension(String url) {
        if (url == null) {
            return "";
        }
        int lastSlash = url.lastIndexOf('/');
        String filename = lastSlash >= 0 ? url.substring(lastSlash + 1) : url;
        int lastDot = filename.lastIndexOf('.');
        if (lastDot >= 0) {
            String ext = filename.substring(lastDot);
            int queryIndex = ext.indexOf('?');
            if (queryIndex >= 0) {
                return ext.substring(0, queryIndex);
            }
            return ext;
        }
        return "";
    }
}
