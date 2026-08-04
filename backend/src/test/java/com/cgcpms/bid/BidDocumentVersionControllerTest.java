package com.cgcpms.bid;

import com.cgcpms.bid.controller.BidDocumentVersionController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BidDocumentVersionControllerTest {

    @Test
    void documentWritesRequireFileAndStatusAuthorities() throws Exception {
        String expected = "(hasAuthority('bid:file:manage') and hasAuthority('bid:status')) or hasRole('SUPER_ADMIN')";

        assertEquals(expected, authorization("append", Long.class,
                com.cgcpms.bid.dto.BidDocumentCreateRequest.class));
        assertEquals(expected, authorization("finalizeVersion", Long.class, Long.class));
        assertEquals(expected, authorization("voidVersion", Long.class, Long.class,
                com.cgcpms.bid.dto.BidDocumentVoidRequest.class));
    }

    private String authorization(String method, Class<?>... parameterTypes) throws Exception {
        return BidDocumentVersionController.class.getDeclaredMethod(method, parameterTypes)
                .getAnnotation(PreAuthorize.class).value();
    }
}
