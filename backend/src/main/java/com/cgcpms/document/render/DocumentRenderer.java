package com.cgcpms.document.render;

public interface DocumentRenderer {
    RenderedDocument render(String html);
    String rendererId();
    String rendererVersion();
}
