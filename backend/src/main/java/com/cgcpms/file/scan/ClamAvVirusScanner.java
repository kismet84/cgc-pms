package com.cgcpms.file.scan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClamAvVirusScanner implements VirusScanner {

    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final int CHUNK_SIZE = 8 * 1024;
    private static final int MAX_RESPONSE_BYTES = 4 * 1024;

    private final VirusScanProperties properties;

    @Override
    public ScanResult scan(byte[] content) {
        if (!properties.isEnabled()) {
            return ScanResult.unavailable("病毒扫描未启用");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.getHost(), properties.getPort()),
                    properties.getConnectTimeoutMillis());
            socket.setSoTimeout(properties.getReadTimeoutMillis());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write(INSTREAM_COMMAND);
            for (int offset = 0; offset < content.length; offset += CHUNK_SIZE) {
                int length = Math.min(CHUNK_SIZE, content.length - offset);
                output.writeInt(length);
                output.write(content, offset, length);
            }
            output.writeInt(0);
            output.flush();

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            for (int value; response.size() < MAX_RESPONSE_BYTES
                    && (value = socket.getInputStream().read()) >= 0 && value != 0; ) {
                response.write(value);
            }
            return parseResponse(response.toString(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            log.warn("ClamAV scan unavailable: host={} port={} errorType={}",
                    properties.getHost(), properties.getPort(), exception.getClass().getSimpleName());
            return ScanResult.unavailable(exception.getClass().getSimpleName());
        }
    }

    private ScanResult parseResponse(String rawResponse) {
        String response = rawResponse == null ? "" : rawResponse.trim();
        if (response.endsWith(" OK")) {
            return ScanResult.clean();
        }
        int foundIndex = response.lastIndexOf(" FOUND");
        if (foundIndex > 0) {
            int separator = response.lastIndexOf(": ", foundIndex);
            String threat = separator >= 0 ? response.substring(separator + 2, foundIndex) : "UNKNOWN";
            return ScanResult.infected(threat);
        }
        return ScanResult.unavailable(response.isBlank() ? "EMPTY_RESPONSE" : response);
    }
}
