package com.cgcpms.document.render;

public record RenderedDocument(byte[] content, String sha256, int pageCount) {
    public RenderedDocument {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
