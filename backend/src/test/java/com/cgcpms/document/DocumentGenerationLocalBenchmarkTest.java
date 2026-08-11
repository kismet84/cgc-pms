package com.cgcpms.document;

import com.cgcpms.document.config.DocumentGenerationProperties;
import com.cgcpms.document.render.OpenHtmlToPdfDocumentRenderer;
import com.cgcpms.document.render.RenderedDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "cgc.m91.pdfBenchmark", matches = "true")
class DocumentGenerationLocalBenchmarkTest {

    private static final int WARMUP_RUNS = 5;
    private static final int SAMPLE_RUNS = 30;
    private static final String INLINE_PNG = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    @Test
    void recordsRepeatableLocalRendererBaselineWithoutDeclaringProductThresholds() throws Exception {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        OpenHtmlToPdfDocumentRenderer renderer = new OpenHtmlToPdfDocumentRenderer(properties);
        try {
            printEnvironment(renderer, properties);
            for (Scenario scenario : List.of(
                    scenario("small-20", 20),
                    scenario("long-120", 120),
                    scenario("long-200", 200))) {
                runScenario(renderer, scenario);
            }
        } finally {
            renderer.close();
        }
    }

    private void runScenario(OpenHtmlToPdfDocumentRenderer renderer, Scenario scenario) throws Exception {
        int warmupFailures = 0;
        Set<String> failureKinds = new LinkedHashSet<>();
        for (int index = 0; index < WARMUP_RUNS; index++) {
            try {
                renderer.render(scenario.html());
            } catch (RuntimeException exception) {
                warmupFailures++;
                failureKinds.add("warmup:" + exception.getClass().getSimpleName());
            }
        }
        System.gc();
        Thread.sleep(200L);

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long baselineHeap = memory.getHeapMemoryUsage().getUsed();
        AtomicLong peakHeap = new AtomicLong(baselineHeap);
        ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("m91-pdf-heap-sampler").factory());
        sampler.scheduleAtFixedRate(() -> peakHeap.accumulateAndGet(
                memory.getHeapMemoryUsage().getUsed(), Math::max), 0L, 5L, TimeUnit.MILLISECONDS);

        List<Long> elapsedNanos = new ArrayList<>(SAMPLE_RUNS);
        List<Long> failedElapsedNanos = new ArrayList<>();
        int failures = 0;
        int pdfBytes = 0;
        int pages = 0;
        try {
            for (int index = 0; index < SAMPLE_RUNS; index++) {
                long started = System.nanoTime();
                try {
                    RenderedDocument rendered = renderer.render(scenario.html());
                    elapsedNanos.add(System.nanoTime() - started);
                    pdfBytes = rendered.content().length;
                    pages = rendered.pageCount();
                } catch (RuntimeException exception) {
                    failures++;
                    failedElapsedNanos.add(System.nanoTime() - started);
                    failureKinds.add("sample:" + exception.getClass().getSimpleName());
                }
            }
        } finally {
            sampler.shutdownNow();
            sampler.awaitTermination(5L, TimeUnit.SECONDS);
        }

        elapsedNanos.sort(Comparator.naturalOrder());
        System.out.printf(Locale.ROOT,
                "M91_F13_BENCHMARK scenario=%s rows=%d htmlBytes=%d pdfBytes=%d pages=%d "
                        + "warmup=%d warmupFailures=%d samples=%d successes=%d p50Ms=%s p95Ms=%s "
                        + "baselineHeapBytes=%d peakHeapBytes=%d heapDeltaBytes=%d failures=%d "
                        + "failureRate=%.4f failureMaxMs=%s failureKinds=%s%n",
                scenario.name(), scenario.rows(), scenario.html().getBytes(StandardCharsets.UTF_8).length,
                pdfBytes, pages, WARMUP_RUNS, warmupFailures, SAMPLE_RUNS, elapsedNanos.size(),
                formatPercentileMillis(elapsedNanos, 0.50), formatPercentileMillis(elapsedNanos, 0.95),
                baselineHeap, peakHeap.get(), Math.max(0L, peakHeap.get() - baselineHeap),
                failures, failures / (double) SAMPLE_RUNS,
                formatMaxMillis(failedElapsedNanos), failureKinds.isEmpty() ? "none" : String.join(",", failureKinds));
        assertEquals(0, warmupFailures, "benchmark warmup failures must remain visible");
        assertEquals(SAMPLE_RUNS, elapsedNanos.size() + failures, "benchmark must account for every sample");
        assertEquals(0, failures, "benchmark failures must remain visible");
        assertTrue(pdfBytes > 0 && pages > 0);
    }

    private void printEnvironment(OpenHtmlToPdfDocumentRenderer renderer,
                                  DocumentGenerationProperties properties) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = -1L;
        if (ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
            totalMemory = operatingSystem.getTotalMemorySize();
        }
        List<String> benchmarkJvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(this::isBenchmarkJvmArgument)
                .sorted()
                .toList();
        System.out.printf(Locale.ROOT,
                "M91_F13_ENV renderer=%s version=%s java=%s vendor=%s vm=%s vmVersion=%s os=%s/%s processors=%d "
                        + "physicalMemoryBytes=%d maxHeapBytes=%d concurrency=%d queue=%d timeoutSeconds=%d "
                        + "maxPdfBytes=%d maxPages=%d benchmarkJvmArgs=%s%n",
                renderer.rendererId(), renderer.rendererVersion(), System.getProperty("java.version"),
                System.getProperty("java.vendor"), System.getProperty("java.vm.name"),
                System.getProperty("java.vm.version"), System.getProperty("os.name"), System.getProperty("os.arch"),
                runtime.availableProcessors(), totalMemory, runtime.maxMemory(), properties.getConcurrency(),
                properties.getQueueCapacity(), properties.getTimeoutSeconds(), properties.getMaxPdfBytes(),
                properties.getMaxPages(), String.join(",", benchmarkJvmArgs));
        assertTrue(benchmarkJvmArgs.contains("-Xms512m"), "benchmark requires fixed initial heap");
        assertTrue(benchmarkJvmArgs.contains("-Xmx2048m"), "benchmark requires fixed maximum heap");
        assertTrue(benchmarkJvmArgs.contains("-XX:+UseG1GC"), "benchmark requires fixed garbage collector");
        assertTrue(benchmarkJvmArgs.contains("-Dfile.encoding=UTF-8"), "benchmark requires fixed file encoding");
        assertTrue(benchmarkJvmArgs.contains("-Duser.timezone=Asia/Shanghai"), "benchmark requires fixed timezone");
    }

    private boolean isBenchmarkJvmArgument(String argument) {
        return argument.startsWith("-Xms")
                || argument.startsWith("-Xmx")
                || argument.matches("-XX:[+-]Use.+GC")
                || argument.startsWith("-Dfile.encoding=")
                || argument.startsWith("-Duser.timezone=");
    }

    private String formatPercentileMillis(List<Long> sortedNanos, double percentile) {
        return sortedNanos.isEmpty() ? "N/A" : String.format(Locale.ROOT, "%.3f",
                percentileMillis(sortedNanos, percentile));
    }

    private String formatMaxMillis(List<Long> nanos) {
        return nanos.isEmpty() ? "N/A" : String.format(Locale.ROOT, "%.3f",
                nanos.stream().mapToLong(Long::longValue).max().orElse(0L) / 1_000_000.0);
    }

    private double percentileMillis(List<Long> sortedNanos, double percentile) {
        int index = Math.min(sortedNanos.size() - 1,
                Math.max(0, (int) Math.ceil(sortedNanos.size() * percentile) - 1));
        return sortedNanos.get(index) / 1_000_000.0;
    }

    private Scenario scenario(String name, int rows) {
        StringBuilder bodyRows = new StringBuilder(rows * 96);
        for (int index = 1; index <= rows; index++) {
            bodyRows.append("<tr><td>").append(index).append("</td><td>工程材料采购明细-")
                    .append(index).append("</td><td>").append(index).append(".00</td></tr>");
        }
        String html = """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><style>
                    @page { size: A4; margin: 18mm 12mm 20mm; }
                    table { width: 100%%; border-collapse: collapse; }
                    thead { display: table-header-group; }
                    tr { page-break-inside: avoid; }
                    th, td { border: 1px solid #333; padding: 3px; }
                  </style></head>
                  <body>
                    <h1>付款申请单</h1><p><img src="%s" alt="logo" /> 申请金额：123456.78 元</p>
                    <table><thead><tr><th>序号</th><th>用途</th><th>金额</th></tr></thead>
                    <tbody>%s</tbody></table>
                  </body>
                </html>
                """.formatted(INLINE_PNG, bodyRows);
        return new Scenario(name, rows, html);
    }

    private record Scenario(String name, int rows, String html) {}
}
