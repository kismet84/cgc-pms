package com.cgcpms.file.service;

import com.cgcpms.common.exception.BusinessException;

import java.util.Set;

/**
 * 文件类型联合校验器 — 扩展名、MIME、魔术字节三元校验。
 * <p>
 * <b>允许矩阵</b>: PDF, JPEG, PNG, GIF, WebP, DOCX, XLSX, PPTX, TXT, CSV
 * <p>
 * <b>BREAKING CHANGE</b>: 以下格式不再接受:
 * doc, xls, ppt, bmp, zip, rar, 7z
 */
public final class FileTypeValidator {

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L; // 50 MB

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".docx", ".xlsx", ".pptx",
            ".txt", ".csv"
    );

    // ---- magic bytes signatures ----
    private static final byte[] PDF_SIG = {0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-
    private static final byte[] JPEG_SIG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] GIF89A_SIG = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}; // GIF89a
    private static final byte[] GIF87A_SIG = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61}; // GIF87a
    private static final byte[] ZIP_SIG = {0x50, 0x4B, 0x03, 0x04}; // PK\x03\x04
    private static final byte[] RIFF_SIG = {0x52, 0x49, 0x46, 0x46}; // RIFF
    private static final byte[] WEBP_SIG = {0x57, 0x45, 0x42, 0x50}; // WEBP

    // No constructor needed — uses implicit public default constructor

    /**
     * 文件类型校验结果。
     *
     * @param sanitizedName 清洗后的文件名（控制字符替换为下划线）
     * @param extension      小写扩展名（含点，如 ".pdf"）
     * @param detectedMime   魔术字节对应的 MIME 类型
     */
    public record ValidationResult(String sanitizedName, String extension, String detectedMime) {
    }

    /**
     * 联合校验：扩展名白名单 + MIME 声明 vs 魔术字节匹配。
     *
     * @param originalFilename  客户端上传时的原始文件名
     * @param clientContentType 客户端声明的 Content-Type（可为 null）
     * @param content           文件字节内容
     * @return 校验结果（含清洗文件名、扩展名、检测到的 MIME）
     * @throws BusinessException 任一校验不通过
     */
    public ValidationResult validate(String originalFilename, String clientContentType, byte[] content) {
        // 空文件名
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("FILE_EMPTY", "上传文件不能为空（无文件名）");
        }

        // 空内容
        if (content == null || content.length == 0) {
            throw new BusinessException("FILE_EMPTY", "上传文件不能为空");
        }

        // 文件大小
        if (content.length > MAX_FILE_SIZE) {
            throw new BusinessException("FILE_TOO_LARGE", "文件大小不能超过 50MB");
        }

        // 清洗文件名中的控制字符
        String sanitizedName = sanitizeFilename(originalFilename);

        // 扩展名
        String ext = getExtension(sanitizedName).toLowerCase();
        if (ext.isEmpty()) {
            throw new BusinessException("FILE_TYPE_NOT_ALLOWED", "文件缺少扩展名");
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("FILE_TYPE_NOT_ALLOWED", "不支持的文件类型: " + ext);
        }

        // 魔术字节检测
        String detectedMime = detectMimeByMagic(content);
        if (detectedMime == null) {
            throw new BusinessException("FILE_TYPE_NOT_ALLOWED",
                    "无法识别的文件格式: " + sanitizedName);
        }

        // 魔术字节 MIME 与客户端声明 MIME 交叉校验
        if (clientContentType != null && !clientContentType.isBlank()) {
            if (!isMimeCompatible(ext, detectedMime, clientContentType)) {
                throw new BusinessException("FILE_TYPE_NOT_ALLOWED",
                        "文件内容与声明的类型不匹配: 声明=" + clientContentType
                                + ", 扩展名=" + ext + ", 实际=" + detectedMime);
            }
        }

        // 扩展名与实际魔术字节是否一致
        if (!isExtensionCompatibleWithMagic(ext, detectedMime)) {
            throw new BusinessException("FILE_TYPE_NOT_ALLOWED",
                    "文件扩展名与实际内容不匹配: " + ext + " vs " + detectedMime);
        }

        return new ValidationResult(sanitizedName, ext, detectedMime);
    }

    // ---- private helpers ----

    /**
     * 将文件名中的控制字符替换为下划线。
     */
    private static String sanitizeFilename(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.'));
    }

    /**
     * 根据魔术字节检测 MIME 类型。
     *
     * @return MIME 字符串，无法识别返回 null
     */
    private static String detectMimeByMagic(byte[] content) {
        if (content == null || content.length < 4) return null;

        // PDF: starts with %PDF-
        if (startsWith(content, PDF_SIG)) {
            return "application/pdf";
        }

        // JPEG: starts with FF D8 FF
        if (startsWith(content, JPEG_SIG)) {
            return "image/jpeg";
        }

        // PNG: 8-byte signature
        if (startsWith(content, PNG_SIG)) {
            return "image/png";
        }

        // GIF: GIF89a or GIF87a
        if (startsWith(content, GIF89A_SIG) || startsWith(content, GIF87A_SIG)) {
            return "image/gif";
        }

        // WebP: RIFF....WEBP
        if (startsWith(content, RIFF_SIG) && content.length >= 12) {
            if (content[8] == WEBP_SIG[0] && content[9] == WEBP_SIG[1]
                    && content[10] == WEBP_SIG[2] && content[11] == WEBP_SIG[3]) {
                return "image/webp";
            }
        }

        // ZIP-based (DOCX/XLSX/PPTX): PK\x03\x04 + search [Content_Types].xml
        if (startsWith(content, ZIP_SIG)) {
            if (containsBytes(content, "Content_Types]".getBytes())) {
                return "application/vnd.openxmlformats-officedocument";
            }
            // It's a ZIP but not an Office Open XML file — reject
            return null;
        }

        // TXT/CSV: no null bytes (non-binary heuristic)
        if (isTextContent(content)) {
            return "text/plain";
        }

        return null;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) return false;
        }
        return true;
    }

    /**
     * 在 content 中搜索 pattern 字节序列。
     */
    private static boolean containsBytes(byte[] content, byte[] pattern) {
        outer:
        for (int i = 0; i <= content.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (content[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 检查内容是否为纯文本（不含 null 字节）。
     */
    private static boolean isTextContent(byte[] content) {
        for (byte b : content) {
            if (b == 0) return false;
        }
        return true;
    }

    /**
     * 检查客户端声明的 MIME 是否与魔术字节检测结果兼容。
     */
    private static boolean isMimeCompatible(String ext, String detectedMime, String clientMime) {
        // Normalize to lowercase
        String cm = clientMime.toLowerCase().trim();

        // Direct match
        if (cm.equals(detectedMime)) return true;

        // JPEG aliases
        if (detectedMime.equals("image/jpeg") && (cm.equals("image/pjpeg") || cm.equals("image/jpg"))) return true;

        // PNG alias
        if (detectedMime.equals("image/png") && cm.equals("image/x-png")) return true;

        // Office Open XML: various client MIME values
        if (detectedMime.startsWith("application/vnd.openxmlformats-officedocument")) {
            if (ext.equals(".docx") && (cm.contains("wordprocessingml") || cm.contains("vnd.openxmlformats")))
                return true;
            if (ext.equals(".xlsx") && (cm.contains("spreadsheetml") || cm.contains("vnd.openxmlformats")))
                return true;
            if (ext.equals(".pptx") && (cm.contains("presentationml") || cm.contains("vnd.openxmlformats")))
                return true;
        }

        // Text aliases
        if (detectedMime.equals("text/plain")) {
            if (cm.equals("text/csv") || cm.equals("application/csv")
                    || cm.startsWith("text/") || cm.equals("application/octet-stream")) {
                return true;
            }
        }

        // Generic octet-stream or unknown — allow if extension matches magic
        if (cm.equals("application/octet-stream") || cm.isEmpty()) return true;

        return false;
    }

    /**
     * 检查扩展名是否与魔术字节检测结果一致。
     */
    private static boolean isExtensionCompatibleWithMagic(String ext, String detectedMime) {
        return switch (ext) {
            case ".pdf" -> detectedMime.equals("application/pdf");
            case ".jpg", ".jpeg" -> detectedMime.equals("image/jpeg");
            case ".png" -> detectedMime.equals("image/png");
            case ".gif" -> detectedMime.equals("image/gif");
            case ".webp" -> detectedMime.equals("image/webp");
            case ".docx" -> detectedMime.startsWith("application/vnd.openxmlformats-officedocument");
            case ".xlsx" -> detectedMime.startsWith("application/vnd.openxmlformats-officedocument");
            case ".pptx" -> detectedMime.startsWith("application/vnd.openxmlformats-officedocument");
            case ".txt", ".csv" -> detectedMime.equals("text/plain");
            default -> false;
        };
    }
}
