package com.cgcpms.file;

import com.cgcpms.file.scan.ClamAvVirusScanner;
import com.cgcpms.file.scan.VirusScanProperties;
import com.cgcpms.file.scan.VirusScanner;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClamAvVirusScannerTest {

    @Test
    void cleanResponsePassesAfterSendingCompleteInstreamPayload() throws Exception {
        byte[] content = "%PDF-1.4 clean".getBytes(StandardCharsets.US_ASCII);
        assertEquals(VirusScanner.ScanResult.Status.CLEAN,
                scanAgainstFakeDaemon(content, "stream: OK\0").status());
    }

    @Test
    void infectedResponseReturnsSignatureName() throws Exception {
        VirusScanner.ScanResult result = scanAgainstFakeDaemon(
                "%PDF-1.4 EICAR".getBytes(StandardCharsets.US_ASCII),
                "stream: Eicar-Signature FOUND\0");

        assertEquals(VirusScanner.ScanResult.Status.INFECTED, result.status());
        assertEquals("Eicar-Signature", result.detail());
    }

    private VirusScanner.ScanResult scanAgainstFakeDaemon(byte[] expectedContent, String response) throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             var executor = Executors.newSingleThreadExecutor()) {
            var serverResult = executor.submit(() -> {
                try (var socket = server.accept()) {
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    byte[] command = input.readNBytes("zINSTREAM\0".length());
                    assertEquals("zINSTREAM\0", new String(command, StandardCharsets.US_ASCII));
                    int length = input.readInt();
                    byte[] payload = input.readNBytes(length);
                    assertEquals(new String(expectedContent, StandardCharsets.US_ASCII),
                            new String(payload, StandardCharsets.US_ASCII));
                    assertEquals(0, input.readInt());
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    return true;
                }
            });

            VirusScanProperties properties = new VirusScanProperties();
            properties.setEnabled(true);
            properties.setHost("127.0.0.1");
            properties.setPort(server.getLocalPort());
            properties.setConnectTimeoutMillis(1_000);
            properties.setReadTimeoutMillis(1_000);
            VirusScanner.ScanResult result = new ClamAvVirusScanner(properties).scan(expectedContent);

            assertEquals(true, serverResult.get(2, TimeUnit.SECONDS));
            return result;
        }
    }
}
