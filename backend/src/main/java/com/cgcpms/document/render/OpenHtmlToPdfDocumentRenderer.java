package com.cgcpms.document.render;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.config.DocumentGenerationProperties;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.annotation.PreDestroy;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class OpenHtmlToPdfDocumentRenderer implements DocumentRenderer {
    private static final String FONT_FAMILY = "CGC PMS Document Font";
    private static final String BUNDLED_FONT = "fonts/ttf/NotoSansSC/NotoSansSC-Regular.ttf";
    private static final String DEFAULT_FONT_STYLE = "<style>html,body{font-family:'" + FONT_FAMILY
            + "',sans-serif !important;}</style>";

    private final DocumentGenerationProperties properties;
    private final ThreadPoolExecutor executor;

    public OpenHtmlToPdfDocumentRenderer(DocumentGenerationProperties properties) {
        this.properties = properties;
        int concurrency = Math.max(1, properties.getConcurrency());
        this.executor = new ThreadPoolExecutor(concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, properties.getQueueCapacity())),
                Thread.ofPlatform().name("document-render-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public RenderedDocument render(String html) {
        Future<byte[]> future;
        try {
            future = executor.submit(() -> renderInternal(html));
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            throw new BusinessException("DOCUMENT_RENDER_BUSY", "文档渲染容量已满，请稍后重试");
        }
        try {
            byte[] content = future.get(Math.max(1, properties.getTimeoutSeconds()), TimeUnit.SECONDS);
            return inspect(content);
        } catch (java.util.concurrent.TimeoutException exception) {
            future.cancel(true);
            throw new BusinessException("DOCUMENT_RENDER_TIMEOUT", "文档渲染超时");
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new BusinessException("DOCUMENT_RENDER_INTERRUPTED", "文档渲染被中断");
        } catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof BusinessException businessException) throw businessException;
            throw new BusinessException("DOCUMENT_RENDER_FAILED", "文档渲染失败", cause);
        }
    }

    private byte[] renderInternal(String html) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            addConfiguredFont(builder);
            builder.useExternalResourceAccessControl(
                    (uri, type) -> uri != null && uri.startsWith("data:image/"),
                    ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
            builder.useExternalResourceAccessControl(
                    (uri, type) -> uri != null && uri.startsWith("data:image/"),
                    ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
            builder.withHtmlContent(applyDefaultFont(html), "https://document.invalid/");
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("DOCUMENT_RENDER_FAILED", "文档渲染失败", exception);
        }
    }

    private void addConfiguredFont(PdfRendererBuilder builder) {
        ClassLoader classLoader = OpenHtmlToPdfDocumentRenderer.class.getClassLoader();
        if (classLoader.getResource(BUNDLED_FONT) == null) {
            throw new BusinessException("DOCUMENT_FONT_UNAVAILABLE", "随包文档字体不可用");
        }
        builder.useFont(() -> classLoader.getResourceAsStream(BUNDLED_FONT), FONT_FAMILY);
    }

    private String applyDefaultFont(String html) {
        int closingHead = html.toLowerCase(java.util.Locale.ROOT).indexOf("</head>");
        if (closingHead >= 0) {
            return html.substring(0, closingHead) + DEFAULT_FONT_STYLE + html.substring(closingHead);
        }
        int htmlStart = html.toLowerCase(java.util.Locale.ROOT).indexOf("<html");
        int htmlOpenEnd = htmlStart < 0 ? -1 : html.indexOf('>', htmlStart);
        if (htmlOpenEnd >= 0) {
            return html.substring(0, htmlOpenEnd + 1) + "<head>" + DEFAULT_FONT_STYLE + "</head>"
                    + html.substring(htmlOpenEnd + 1);
        }
        return "<html><head>" + DEFAULT_FONT_STYLE + "</head><body>" + html + "</body></html>";
    }

    private RenderedDocument inspect(byte[] content) {
        if (content.length < 5 || !"%PDF-".equals(new String(content, 0, 5, StandardCharsets.US_ASCII))) {
            throw new BusinessException("DOCUMENT_OUTPUT_INVALID", "渲染结果不是有效PDF");
        }
        if (content.length > properties.getMaxPdfBytes()) {
            throw new BusinessException("DOCUMENT_OUTPUT_TOO_LARGE", "PDF超过大小限制");
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            int pages = document.getNumberOfPages();
            if (pages < 1 || pages > properties.getMaxPages()) {
                throw new BusinessException("DOCUMENT_PAGE_LIMIT_EXCEEDED", "PDF页数超过限制");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new RenderedDocument(content, HexFormat.of().formatHex(digest.digest(content)), pages);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("DOCUMENT_OUTPUT_INVALID", "无法验证PDF输出", exception);
        }
    }

    @Override
    public String rendererId() { return "openhtmltopdf"; }

    @Override
    public String rendererVersion() { return "1.1.40/pdfbox-3.0.8/noto-sans-sc-6.1.0"; }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
