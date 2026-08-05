package com.cgcpms.projectfile;

import com.cgcpms.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class OfficePreviewClient {
    private static final int MAX_OUTPUT_BYTES = 100 * 1024 * 1024;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final URI endpoint;

    public OfficePreviewClient(
            @Value("${project-file.preview-converter-url:http://office-preview:8080/convert}") String endpoint) {
        this.endpoint = URI.create(endpoint);
    }

    public byte[] convert(byte[] source, String originalName) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/octet-stream")
                    .header("X-File-Name", safeName(originalName))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(source))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new BusinessException("OFFICE_PREVIEW_CONVERSION_FAILED", "Office预览转换失败");
            }
            byte[] body = response.body();
            if (body == null || body.length < 5 || body.length > MAX_OUTPUT_BYTES
                    || body[0] != '%' || body[1] != 'P' || body[2] != 'D' || body[3] != 'F' || body[4] != '-') {
                throw new BusinessException("OFFICE_PREVIEW_OUTPUT_INVALID", "Office预览输出无效");
            }
            return body;
        } catch (BusinessException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new BusinessException("OFFICE_PREVIEW_TIMEOUT", "Office预览转换超时", exception);
        } catch (Exception exception) {
            throw new BusinessException("OFFICE_PREVIEW_UNAVAILABLE", "Office预览服务暂不可用", exception);
        }
    }

    private String safeName(String name) {
        String safe = name == null ? "document.docx" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.length() > 120 ? safe.substring(safe.length() - 120) : safe;
    }
}
