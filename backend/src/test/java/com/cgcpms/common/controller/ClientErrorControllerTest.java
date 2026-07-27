package com.cgcpms.common.controller;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class ClientErrorControllerTest {

    @Test
    void acceptsOnlyLowCardinalityTagsAndRecordsOneCounter(CapturedOutput output) {
        var registry = new SimpleMeterRegistry();
        var beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("meterRegistry", registry);
        var provider = beanFactory.getBeanProvider(
                io.micrometer.core.instrument.MeterRegistry.class);
        var controller = new ClientErrorController(provider);
        var report = new ClientErrorController.ClientErrorReport(
                "V2",
                "VUE",
                "TYPE_ERROR",
                "a".repeat(64));

        controller.report(report);

        assertEquals(1, registry.get("frontend.client.errors")
                .tags("app", "V2", "source", "VUE")
                .counter()
                .count());
        assertEquals(2, registry.get("frontend.client.errors").counter().getId().getTags().size());
        assertTrue(output.getOut().contains("fingerprintPrefix=" + "a".repeat(12)));
        assertFalse(output.getOut().contains("a".repeat(64)));
    }

    @Test
    void rejectsRawMessagesUrlsAndUnboundedKinds() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            assertFalse(validator.validate(new ClientErrorController.ClientErrorReport(
                    "LEGACY", "WINDOW", "ERROR", "b".repeat(64))).iterator().hasNext());
            assertTrue(validator.validate(new ClientErrorController.ClientErrorReport(
                    "LEGACY", "WINDOW", "password=secret https://host/path", "raw-message")).size() >= 2);
        }
    }
}
