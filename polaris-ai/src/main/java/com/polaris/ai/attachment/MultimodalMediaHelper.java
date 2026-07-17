package com.polaris.ai.attachment;

import com.polaris.common.config.PolarisConfig;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 多模态媒体资源辅助组件
 * 专门负责图片判定、多模态 Base64 自适应压缩、以及运行时 PDF 页面渲染
 * 
 * @author polaris
 */
@Component
public class MultimodalMediaHelper {
    
    private static final Logger log = LoggerFactory.getLogger(MultimodalMediaHelper.class);

    /**
     * 判断是否是图片文件
     */
    public boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext) || "gif".equals(ext) || "webp".equals(ext) || "bmp".equals(ext);
    }

    /**
     * 获取图片 Mime 类型
     */
    public String getImageMimeType(String fileName) {
        if (fileName == null) return "image/jpeg";
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        switch (ext) {
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "bmp": return "image/bmp";
            default: return "image/jpeg";
        }
    }

    /**
     * 构建多模态图片 UserMessage
     */
    public UserMessage buildImageMessage(String userContent, String fileUrl, String fileName) {
        String base64 = convertImageToBase64(fileUrl);
        if (base64 != null) {
            List<Content> contents = new ArrayList<>();
            contents.add(TextContent.from(userContent));
            contents.add(ImageContent.from(base64, getImageMimeType(fileName)));
            return UserMessage.from(contents);
        }
        return UserMessage.from(userContent);
    }

    /**
     * 构建多模态 PDF 图文 UserMessage
     */
    public UserMessage buildPdfMultimodalMessage(String userContent, String fileUrl, String fileName, String fileContent) {
        List<String> pagesBase64 = renderPdfPagesToBase64(fileUrl, 3);
        if (!pagesBase64.isEmpty()) {
            List<Content> contents = new ArrayList<>();
            String combinedPrompt = userContent + "\n\n"
                    + "--------------------------------------------------\n"
                    + "[已为您解析并关联对话 PDF 附件: " + fileName + "]\n"
                    + "--------------------------------------------------\n"
                    + fileContent;
            contents.add(TextContent.from(combinedPrompt));
            for (String pageBase64 : pagesBase64) {
                contents.add(ImageContent.from(pageBase64, "image/png"));
            }
            return UserMessage.from(contents);
        } else {
            // 降级为文本拼接
            if (fileContent != null && !fileContent.trim().isEmpty()) {
                String combinedPrompt = userContent + "\n\n"
                        + "--------------------------------------------------\n"
                        + "[已为您解析并关联对话附件: " + fileName + "]\n"
                        + "--------------------------------------------------\n"
                        + fileContent;
                return UserMessage.from(combinedPrompt);
            }
            return UserMessage.from(userContent);
        }
    }

    /**
     * 读取图片文件并转换为经过自适应压缩的 Base64 格式
     */
    public String convertImageToBase64(String fileUrl) {
        try {
            String localPath = PolarisConfig.getProfile();
            String relativePath = fileUrl;
            if (fileUrl.startsWith("/profile")) {
                relativePath = fileUrl.substring("/profile".length());
            }
            File file = new File(localPath + relativePath);
            if (file.exists()) {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                // 动态缩放与压缩，初始参数设为安全的 600x600, 0.45f，并在内部支持自适应降级循环
                byte[] compressedBytes = compressImage(fileBytes, 600, 600, 0.45f);
                return Base64.getEncoder().encodeToString(compressedBytes);
            } else {
                log.warn(">>> 未找到多模态图片文件: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error(">>> 读取图片并转换为 Base64 失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 自适应多级降级图片压缩，控制在 120000 字符内，保障通义千问等大模型网关安全
     */
    private byte[] compressImage(byte[] imageBytes, int maxWidth, int maxHeight, float quality) {
        byte[] resultBytes = imageBytes;
        int currentWidth = maxWidth;
        int currentHeight = maxHeight;
        float currentQuality = quality;

        try {
            for (int i = 0; i < 4; i++) {
                resultBytes = compressImageOnce(imageBytes, currentWidth, currentHeight, currentQuality);
                String base64 = Base64.getEncoder().encodeToString(resultBytes);
                
                if (base64.length() < 120000) {
                    log.info(">>> 图片动态压缩第 {} 次成功，原体积: {} bytes, 压缩后体积: {} bytes, Base64字符长度: {}", 
                            i + 1, imageBytes.length, resultBytes.length, base64.length());
                    break;
                }
                
                log.warn(">>> 图片第 {} 次压缩后 Base64 长度 {} 仍超 120000 限额，启动等比降级...", i + 1, base64.length());
                currentWidth = (int) (currentWidth * 0.75);
                currentHeight = (int) (currentHeight * 0.75);
                currentQuality = currentQuality * 0.75f;
            }
        } catch (Exception e) {
            log.error(">>> 自适应压缩失败，回退到原图: {}", e.getMessage());
        }
        return resultBytes;
    }

    /**
     * 单次图像缩放与 JPEG 质量压缩核心方法
     */
    private byte[] compressImageOnce(byte[] imageBytes, int maxWidth, int maxHeight, float quality) throws Exception {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageBytes)) {
            BufferedImage originalImage = ImageIO.read(bais);
            if (originalImage == null) return imageBytes;

            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            if (width > maxWidth || height > maxHeight) {
                double widthRatio = (double) maxWidth / width;
                double heightRatio = (double) maxHeight / height;
                double ratio = Math.min(widthRatio, heightRatio);
                width = (int) (width * ratio);
                height = (int) (height * ratio);
            }

            BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = resizedImage.createGraphics();
            g2d.drawImage(originalImage, 0, 0, width, height, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            java.util.Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                javax.imageio.ImageWriter writer = writers.next();
                try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(quality);
                    }
                    writer.write(null, new javax.imageio.IIOImage(resizedImage, null, null), param);
                } catch (Exception err) {
                    baos.reset();
                    ImageIO.write(resizedImage, "jpg", baos);
                } finally {
                    writer.dispose();
                }
            } else {
                ImageIO.write(resizedImage, "jpg", baos);
            }

            return baos.toByteArray();
        }
    }

    /**
     * 运行时将 PDF 文档前 3 页通过 PDFBox 渲染为 Base64 图片
     */
    public List<String> renderPdfPagesToBase64(String fileUrl, int maxPages) {
        List<String> resultList = new ArrayList<>();
        try {
            String localPath = PolarisConfig.getProfile();
            String relativePath = fileUrl;
            if (fileUrl.startsWith("/profile")) {
                relativePath = fileUrl.substring("/profile".length());
            }
            File file = new File(localPath + relativePath);
            if (!file.exists()) {
                log.warn(">>> 未找到多模态 PDF 文件: {}", file.getAbsolutePath());
                return resultList;
            }

            try (PDDocument document = PDDocument.load(file)) {
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                int pagesToRender = Math.min(document.getNumberOfPages(), maxPages);
                log.info(">>> 开始对 PDF [{}] 进行渲染，总页数: {}, 限制渲染页数: {}", file.getName(), document.getNumberOfPages(), pagesToRender);
                for (int i = 0; i < pagesToRender; i++) {
                    BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 110, ImageType.RGB);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bim, "png", baos);
                    byte[] bytes = baos.toByteArray();
                    resultList.add(Base64.getEncoder().encodeToString(bytes));
                }
            }
        } catch (Exception e) {
            log.error(">>> 渲染 PDF 页面为图片 Base64 失败: {}", e.getMessage(), e);
        }
        return resultList;
    }
}
