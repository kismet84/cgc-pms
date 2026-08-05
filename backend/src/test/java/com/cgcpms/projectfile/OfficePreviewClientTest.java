package com.cgcpms.projectfile;

import com.cgcpms.common.exception.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfficePreviewClientTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> receivedName = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/convert", this::respond);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/convert";
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void acceptsPdfAndSanitizesNullUnsafeAndLongNames() {
        OfficePreviewClient client = new OfficePreviewClient(baseUrl + "?mode=valid");
        assertEquals("%PDF-ok", new String(client.convert(new byte[]{1}, null), StandardCharsets.US_ASCII));
        assertEquals("document.docx", receivedName.get());

        client.convert(new byte[]{1}, "含 空格.docx");
        assertEquals("____.docx", receivedName.get());

        client.convert(new byte[]{1}, "x".repeat(130) + ".docx");
        assertEquals(120, receivedName.get().length());
    }

    @Test
    void rejectsStatusAndEveryInvalidPdfPrefixPosition() {
        assertEquals("OFFICE_PREVIEW_CONVERSION_FAILED", error("failed").getCode());
        for (String mode : new String[]{"short", "p0", "p1", "p2", "p3", "p4"}) {
            assertEquals("OFFICE_PREVIEW_OUTPUT_INVALID", error(mode).getCode());
        }
    }

    @Test
    void mapsConnectionFailureToUnavailable() {
        int port = server.getAddress().getPort();
        server.stop(0);
        server = null;
        BusinessException error = assertThrows(BusinessException.class,
                () -> new OfficePreviewClient("http://127.0.0.1:" + port + "/convert")
                        .convert(new byte[]{1}, "file.docx"));
        assertEquals("OFFICE_PREVIEW_UNAVAILABLE", error.getCode());
    }

    private BusinessException error(String mode) {
        return assertThrows(BusinessException.class,
                () -> new OfficePreviewClient(baseUrl + "?mode=" + mode)
                        .convert(new byte[]{1}, "file.docx"));
    }

    private void respond(HttpExchange exchange) throws IOException {
        receivedName.set(exchange.getRequestHeaders().getFirst("X-File-Name"));
        String query = exchange.getRequestURI().getQuery();
        String mode = query == null ? "valid" : query.substring("mode=".length());
        int status = "failed".equals(mode) ? 422 : 200;
        byte[] body = switch (mode) {
            case "short" -> "bad".getBytes(StandardCharsets.US_ASCII);
            case "p0" -> "XPDF-".getBytes(StandardCharsets.US_ASCII);
            case "p1" -> "%XDF-".getBytes(StandardCharsets.US_ASCII);
            case "p2" -> "%PXF-".getBytes(StandardCharsets.US_ASCII);
            case "p3" -> "%PDX-".getBytes(StandardCharsets.US_ASCII);
            case "p4" -> "%PDFX".getBytes(StandardCharsets.US_ASCII);
            default -> "%PDF-ok".getBytes(StandardCharsets.US_ASCII);
        };
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
