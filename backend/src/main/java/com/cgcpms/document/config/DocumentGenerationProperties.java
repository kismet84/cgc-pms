package com.cgcpms.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.generation")
public class DocumentGenerationProperties {
    private boolean enabled = false;
    private boolean paymentEnabled = false;
    private boolean settlementEnabled = false;
    private int maxTemplateBytes = 512 * 1024;
    private int maxCollectionItems = 200;
    private int maxImageBytes = 1024 * 1024;
    private int maxTotalImageBytes = 3 * 1024 * 1024;
    private int maxPdfBytes = 20 * 1024 * 1024;
    private int maxPages = 100;
    private int timeoutSeconds = 15;
    private int concurrency = 2;
    private int queueCapacity = 2;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { this.enabled = value; }
    public boolean isPaymentEnabled() { return paymentEnabled; }
    public void setPaymentEnabled(boolean value) { this.paymentEnabled = value; }
    public boolean isSettlementEnabled() { return settlementEnabled; }
    public void setSettlementEnabled(boolean value) { this.settlementEnabled = value; }
    public int getMaxTemplateBytes() { return maxTemplateBytes; }
    public void setMaxTemplateBytes(int value) { this.maxTemplateBytes = value; }
    public int getMaxCollectionItems() { return maxCollectionItems; }
    public void setMaxCollectionItems(int value) { this.maxCollectionItems = value; }
    public int getMaxImageBytes() { return maxImageBytes; }
    public void setMaxImageBytes(int value) { this.maxImageBytes = value; }
    public int getMaxTotalImageBytes() { return maxTotalImageBytes; }
    public void setMaxTotalImageBytes(int value) { this.maxTotalImageBytes = value; }
    public int getMaxPdfBytes() { return maxPdfBytes; }
    public void setMaxPdfBytes(int value) { this.maxPdfBytes = value; }
    public int getMaxPages() { return maxPages; }
    public void setMaxPages(int value) { this.maxPages = value; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int value) { this.timeoutSeconds = value; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int value) { this.concurrency = value; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int value) { this.queueCapacity = value; }
}
