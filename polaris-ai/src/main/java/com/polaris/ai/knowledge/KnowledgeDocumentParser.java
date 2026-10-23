package com.polaris.ai.knowledge;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** 知识库专用解析；不改变普通聊天附件的旧契约，也不以正文字符串表达失败。 */
@Component
public class KnowledgeDocumentParser {
    public static final String VERSION = "KNOWLEDGE_PARSE_V1";
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    public static final int MAX_CHARACTERS = 500_000;
    private static final Set<String> TEXT_TYPES = Set.of("txt", "md", "json", "csv", "xml", "html", "java", "py", "js", "ts");

    public Result parse(Path path, String originalName) {
        if (path == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return failed("FILE_MISSING");
        String extension = originalName == null || !originalName.contains(".") ? ""
                : originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!TEXT_TYPES.contains(extension) && !Set.of("pdf", "docx", "xlsx", "xls").contains(extension)) return failed("UNSUPPORTED_TYPE");
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            }
            if (bytes.length > MAX_FILE_BYTES) return failed("FILE_TOO_LARGE");
            if ("docx".equals(extension) || "xlsx".equals(extension)) validateZipBudget(bytes);
            Builder builder = new Builder();
            if (TEXT_TYPES.contains(extension)) {
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                if (text.startsWith("\uFEFF")) text = text.substring(1);
                if (text.indexOf('\0') >= 0) return failed("INVALID_TEXT_ENCODING");
                builder.add("TEXT", "全文", text);
                if ("md".equals(extension) || "csv".equals(extension)) builder.warnings.add("PLAIN_TEXT_LAYOUT");
            } else if ("pdf".equals(extension)) {
                try (PDDocument pdf = PDDocument.load(bytes)) {
                    if (pdf.getNumberOfPages() > 200) return failed("RESOURCE_LIMIT");
                    PDFTextStripper stripper = new PDFTextStripper();
                    for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                        stripper.setStartPage(page); stripper.setEndPage(page);
                        String text = stripper.getText(pdf);
                        if (text.isBlank()) builder.warnings.add("PDF_EMPTY_PAGE");
                        builder.add("PDF_PAGE", "第 " + page + " 页", text);
                    }
                }
                if (builder.blocks.isEmpty()) return failed("OCR_REQUIRED");
                builder.warnings.add("PDF_READING_ORDER_UNVERIFIED");
            } else if ("docx".equals(extension)) {
                try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                    int ordinal = 0;
                    for (IBodyElement element : word.getBodyElements()) {
                        ordinal++;
                        if (element instanceof XWPFParagraph paragraph) builder.add("WORD_PARAGRAPH", "正文段落 " + ordinal, paragraph.getText());
                        else if (element instanceof XWPFTable table) {
                            int rowNo = 0;
                            for (XWPFTableRow row : table.getRows()) {
                                builder.add("WORD_TABLE_ROW", "正文表格 " + ordinal + " 行 " + (++rowNo),
                                        String.join(" | ", row.getTableCells().stream().map(XWPFTableCell::getText).toList()));
                            }
                        }
                    }
                }
                builder.warnings.add("WORD_HEADERS_FOOTERS_NOT_INCLUDED");
                builder.warnings.add("TABLE_MERGES_UNVERIFIED");
            } else {
                try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                    DataFormatter formatter = new DataFormatter(Locale.ROOT);
                    int cells = 0;
                    if (workbook.getNumberOfSheets() > 100) return failed("RESOURCE_LIMIT");
                    for (int sheetNo = 0; sheetNo < workbook.getNumberOfSheets(); sheetNo++) {
                        Sheet sheet = workbook.getSheetAt(sheetNo);
                        if (sheet.getNumMergedRegions() > 0) builder.warnings.add("TABLE_MERGES_UNVERIFIED");
                        for (Row row : sheet) {
                            int width = Math.max(0, row.getLastCellNum());
                            cells += width;
                            if (width > 500 || cells > 100_000) return failed("RESOURCE_LIMIT");
                            List<String> values = new ArrayList<>();
                            for (int column = 0; column < width; column++) {
                                Cell cell = row.getCell(column);
                                if (cell != null && cell.getCellType() == CellType.FORMULA) {
                                    // 不执行公式、不访问外部引用；明确使用缓存值。
                                    builder.warnings.add("FORMULA_CACHED_VALUE");
                                    String cached = cachedValue(cell, formatter);
                                    if (cached == null) builder.warnings.add("FORMULA_CACHE_MISSING");
                                    values.add(cached == null ? "" : cached);
                                } else values.add(cell == null ? "" : formatter.formatCellValue(cell));
                            }
                            if (values.stream().anyMatch(value -> !value.isBlank())) builder.add("SHEET_ROW",
                                    "工作表 " + (sheetNo + 1) + " 行 " + (row.getRowNum() + 1),
                                    "[工作表 " + (sheetNo + 1) + ": " + sheet.getSheetName() + "] " + String.join(" | ", values));
                        }
                    }
                }
                builder.warnings.add("TABLE_HEADER_SEMANTICS_UNVERIFIED");
            }
            String content = String.join("\n\n", builder.blocks.stream().map(Block::text).toList());
            if (content.isBlank()) return failed("EMPTY_CONTENT");
            return new Result(true, null, sha256(bytes), sha256(content.getBytes(StandardCharsets.UTF_8)),
                    content, List.copyOf(builder.blocks), List.copyOf(builder.warnings));
        } catch (LimitExceeded e) {return failed("RESOURCE_LIMIT");}
        catch (java.nio.charset.CharacterCodingException e) {return failed("INVALID_TEXT_ENCODING");}
        catch (Exception e) {return failed("PARSE_FAILED");}
    }

    private static String cachedValue(Cell cell, DataFormatter formatter) {
        if (cell instanceof org.apache.poi.xssf.usermodel.XSSFCell xmlCell
                && (!xmlCell.getCTCell().isSetV() || xmlCell.getCTCell().getV() == null || xmlCell.getCTCell().getV().isBlank())) return null;
        return switch (cell.getCachedFormulaResultType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> formatter.formatRawCellContents(cell.getNumericCellValue(),
                    cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString());
            default -> null;
        };
    }

    private static void validateZipBudget(byte[] bytes) throws java.io.IOException {
        long total = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (var zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
            while (zip.getNextEntry() != null) {
                if (++entries > 2000) throw new LimitExceeded();
                long entrySize = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entrySize += read; total += read;
                    if (entrySize > 8 * 1024 * 1024L || total > 32 * 1024 * 1024L) throw new LimitExceeded();
                }
            }
        }
        if (entries == 0) throw new java.io.IOException("INVALID_CONTAINER");
    }

    public static String sha256(byte[] value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch (Exception e) {throw new IllegalStateException("摘要计算失败");}
    }
    private static Result failed(String code) {return new Result(false, code, null, null, "", List.of(), List.of());}
    private static class LimitExceeded extends RuntimeException { }
    private static class Builder {
        final List<Block> blocks = new ArrayList<>();
        final Set<String> warnings = new LinkedHashSet<>();
        int characters;
        void add(String kind, String location, String text) {
            if (text == null || text.isBlank()) return;
            characters += text.length() + 2;
            if (characters > MAX_CHARACTERS || blocks.size() >= 20_000) throw new LimitExceeded();
            blocks.add(new Block(kind, location, text));
        }
    }
    public record Block(String kind, String location, String text) { }
    public record Result(boolean success, String errorCode, String fileHash, String contentHash,
                         String content, List<Block> blocks, List<String> warnings) { }
}
