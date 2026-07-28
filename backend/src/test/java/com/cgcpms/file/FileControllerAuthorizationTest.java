package com.cgcpms.file;

import com.cgcpms.file.controller.FileController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FileControllerAuthorizationTest {

    @Test
    void cashJournalAuthoritiesCanEnterGenericFileEndpoints() throws Exception {
        assertAuthorizationContains("upload",
                new Class<?>[]{MultipartFile.class, String.class, Long.class, String.class},
                "cashbook:journal:maintain");
        assertAuthorizationContains("listByBusiness", new Class<?>[]{String.class, Long.class},
                "cashbook:journal:query");
        assertAuthorizationContains("getUrl", new Class<?>[]{Long.class}, "cashbook:journal:query");
        assertAuthorizationContains("delete", new Class<?>[]{Long.class}, "cashbook:journal:maintain");
    }

    @Test
    void variationAuthoritiesAreScopedByReadAndDocumentAction() throws Exception {
        Class<?>[] uploadParameters = {MultipartFile.class, String.class, Long.class, String.class};
        assertAuthorizationContains("upload", uploadParameters, "variation:order:edit");
        assertAuthorizationContains("upload", uploadParameters, "variation:owner:submit");
        assertAuthorizationContains("upload", uploadParameters, "variation:owner:review");
        assertAuthorizationContains("listByBusiness", new Class<?>[]{String.class, Long.class},
                "variation:order:query");
        assertAuthorizationContains("listByBusiness", new Class<?>[]{String.class, Long.class},
                "variation:trace");
        assertAuthorizationContains("getUrl", new Class<?>[]{Long.class}, "variation:order:query");
        assertAuthorizationContains("delete", new Class<?>[]{Long.class}, "variation:owner:review");
    }

    @Test
    void subcontractMeasureAuthoritiesCanEnterOnlyTheirFileActions() throws Exception {
        Class<?>[] uploadParameters = {MultipartFile.class, String.class, Long.class, String.class};
        assertAuthorizationContains("upload", uploadParameters, "subcontract:measure:edit");
        assertAuthorizationContains("listByBusiness", new Class<?>[]{String.class, Long.class},
                "subcontract:measure:query");
        assertAuthorizationContains("getUrl", new Class<?>[]{Long.class}, "subcontract:measure:query");
        assertAuthorizationContains("delete", new Class<?>[]{Long.class}, "subcontract:measure:edit");
    }

    @Test
    void settlementAuthoritiesCanEnterOnlyTheirFileActions() throws Exception {
        Class<?>[] uploadParameters = {MultipartFile.class, String.class, Long.class, String.class};
        assertAuthorizationContains("upload", uploadParameters, "settlement:edit");
        assertAuthorizationContains("listByBusiness", new Class<?>[]{String.class, Long.class},
                "settlement:query");
        assertAuthorizationContains("getUrl", new Class<?>[]{Long.class}, "settlement:query");
        assertAuthorizationContains("delete", new Class<?>[]{Long.class}, "settlement:edit");
    }

    @Test
    void paymentClosedLoopAuthoritiesCanManageTheirOwnEvidence() throws Exception {
        Class<?>[] uploadParameters = {MultipartFile.class, String.class, Long.class, String.class};
        for (String authority : new String[]{"expense:edit", "payment:app:edit", "invoice:edit"}) {
            assertAuthorizationContains("upload", uploadParameters, authority);
            assertAuthorizationContains("delete", new Class<?>[]{Long.class}, authority);
        }
        for (String authority : new String[]{"expense:query", "payment:app:query", "invoice:query"}) {
            assertAuthorizationContains("listByBusiness", new Class<?>[]{String.class, Long.class}, authority);
            assertAuthorizationContains("getUrl", new Class<?>[]{Long.class}, authority);
        }
    }

    @Test
    void procurementAuthoritiesCanManageTheirOwnEvidence() throws Exception {
        Class<?>[] uploadParameters = {MultipartFile.class, String.class, Long.class, String.class};
        for (String authority : new String[]{"purchase:request:edit", "purchase:order:edit", "receipt:edit"}) {
            assertAuthorizationContains("upload", uploadParameters, authority);
            assertAuthorizationContains("delete", new Class<?>[]{Long.class}, authority);
        }
        for (String authority : new String[]{"purchase:request:list", "purchase:order:query", "receipt:query"}) {
            assertAuthorizationContains("listByBusiness", new Class<?>[]{String.class, Long.class}, authority);
            assertAuthorizationContains("getUrl", new Class<?>[]{Long.class}, authority);
        }
    }

    private void assertAuthorizationContains(String method, Class<?>[] parameterTypes, String authority)
            throws Exception {
        PreAuthorize annotation = FileController.class.getMethod(method, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertTrue(annotation.value().contains(authority), method + " 缺少 " + authority + " 授权分支");
    }
}
