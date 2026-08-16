package com.cgcpms.quality.service;

import com.cgcpms.quality.handler.QualityConsequenceWorkflowHandler;
import com.cgcpms.quality.handler.QualityRectificationWorkflowHandler;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualitySafetyServiceContractTest {

    private static final Set<String> PUBLIC_SIGNATURES = Set.of(
            "listPlans(Long)->List",
            "createPlan(PlanCommand)->QualityInspectionPlan",
            "updatePlan(Long,PlanCommand)->QualityInspectionPlan",
            "activatePlan(Long)->QualityInspectionPlan",
            "completePlan(Long)->QualityInspectionPlan",
            "listInspections(Long)->List",
            "formOptions(Long)->Map",
            "createInspection(InspectionCommand)->QualityInspectionRecord",
            "createIssue(Long,IssueCommand)->QualitySafetyIssue",
            "submitInspection(Long)->QualityInspectionRecord",
            "listIssues(Long,String)->List",
            "createRectification(RectificationCommand)->QualityRectification",
            "submitRectification(Long)->QualityRectification",
            "reinspect(Long,ReinspectionCommand)->QualityRectification",
            "createConsequence(ConsequenceCommand)->QualityConsequence",
            "postConsequence(Long)->QualityConsequence",
            "submitConsequence(Long)->QualityConsequence",
            "onRectificationRunning(WfInstance)->void",
            "onRectificationApproved(WfInstance)->void",
            "onRectificationRejected(WfInstance)->void",
            "onRectificationWithdrawn(WfInstance)->void",
            "onConsequenceRunning(WfInstance)->void",
            "onConsequenceApproved(WfInstance)->void",
            "onConsequenceRejected(WfInstance)->void",
            "onConsequenceWithdrawn(WfInstance)->void",
            "trace(Long)->Trace");

    private static final Set<String> QUERY_SIGNATURES = Set.of(
            "listPlans(Long)->List",
            "listInspections(Long)->List",
            "formOptions(Long)->Map",
            "listIssues(Long,String)->List",
            "trace(Long)->Trace");

    private static final Set<String> HANDLER_SIGNATURES = Set.of(
            "supportBusinessType()->String",
            "isCritical()->boolean",
            "beforeSubmit(WorkflowContext)->void",
            "onRunning(WorkflowContext)->void",
            "onApproved(WorkflowContext)->void",
            "onRejected(WorkflowContext)->void",
            "onWithdrawn(WorkflowContext)->void");

    @Test
    void keepsFacadeConstructorPublicApiTransactionsAndWorkflowCallbacks() {
        Constructor<?> constructor = Arrays.stream(QualitySafetyService.class.getDeclaredConstructors())
                .filter(candidate -> Modifier.isPublic(candidate.getModifiers()))
                .findFirst().orElseThrow();
        assertEquals(List.of(
                "QualityInspectionPlanMapper", "QualityInspectionRecordMapper", "QualitySafetyIssueMapper",
                "QualityRectificationMapper", "QualityConsequenceMapper", "QualityPartnerEvaluationMapper",
                "ProjectAccessChecker", "ProjectExecutionGuard", "PmProjectMapper", "MdPartnerMapper",
                "CtContractMapper", "SysFileMapper", "CostItemMapper", "CostSubjectV2Service",
                "CostSubjectResolver", "BusinessCodeGenerator", "JdbcTemplate", "WorkflowEngine",
                "AccountingPeriodGuard"),
                Arrays.stream(constructor.getParameterTypes()).map(Class::getSimpleName).toList());
        assertNotNull(QualitySafetyService.class.getAnnotation(Service.class));

        Map<String, Method> publicMethods = Arrays.stream(QualitySafetyService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .collect(Collectors.toMap(QualitySafetyServiceContractTest::signature, method -> method));
        assertEquals(PUBLIC_SIGNATURES, publicMethods.keySet());

        Set<String> transactional = publicMethods.entrySet().stream()
                .filter(entry -> entry.getValue().getAnnotation(Transactional.class) != null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        assertEquals(21, transactional.size());
        assertEquals(PUBLIC_SIGNATURES.stream()
                .filter(signature -> !QUERY_SIGNATURES.contains(signature))
                .collect(Collectors.toSet()), transactional);
        for (String method : transactional) {
            assertArrayEquals(new Class<?>[]{Exception.class},
                    publicMethods.get(method).getAnnotation(Transactional.class).rollbackFor());
        }

        assertEquals(HANDLER_SIGNATURES, publicSignatures(QualityRectificationWorkflowHandler.class));
        assertEquals(HANDLER_SIGNATURES, publicSignatures(QualityConsequenceWorkflowHandler.class));
    }

    @Test
    void freezesErrorCodesOptimisticGuardAndConsequencePostingOrder() throws IOException {
        String source = Files.readString(serviceSource());
        Matcher matcher = Pattern.compile("new BusinessException\\(\\s*\"([A-Z0-9_]+)\"").matcher(source);
        Map<String, Long> counts = new TreeMap<>();
        while (matcher.find()) counts.merge(matcher.group(1), 1L, Long::sum);
        assertEquals(60L, counts.values().stream().mapToLong(Long::longValue).sum());
        String canonicalCodes = counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        assertEquals("026816af93c500becb9e7ecf6860f5d624e73767a82636a4e1b8aaf81a061d15",
                sha256(canonicalCodes));

        String reinspection = section(source, "public QualityRectification reinspect", "public QualityConsequence createConsequence");
        assertBefore(reinspection, ".eq(QualityRectification::getVersion, rectification.getVersion())",
                ".setSql(\"version = version + 1\")");

        String approved = section(source, "public void onConsequenceApproved", "public void onConsequenceRejected");
        assertBefore(approved, "requireConsequenceCostSubject(consequence)", "createCostIfRequired(consequence)");
        assertBefore(approved, "createCostIfRequired(consequence)", "evaluationMapper.insert(evaluation)");
        assertBefore(approved, "evaluationMapper.insert(evaluation)",
                ".set(QualityConsequence::getStatus, \"POSTED\")");
    }

    @Test
    void requiresPackagePrivatePlainTraceAssembler() throws ClassNotFoundException {
        Class<?> type = Class.forName("com.cgcpms.quality.service.QualitySafetyTraceAssembler");
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertFalse(Modifier.isPublic(type.getModifiers()));
        assertNull(type.getAnnotation(Service.class));
        assertNull(type.getAnnotation(Component.class));
        assertTrue(Arrays.stream(type.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
        assertTrue(Arrays.stream(type.getDeclaredMethods())
                .noneMatch(method -> method.getAnnotation(Transactional.class) != null));
    }

    private static Set<String> publicSignatures(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(QualitySafetyServiceContractTest::signature)
                .collect(Collectors.toSet());
    }

    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"))
                + "->" + method.getReturnType().getSimpleName();
    }

    private static Path serviceSource() {
        Path source = Path.of("src/main/java/com/cgcpms/quality/service/QualitySafetyService.java");
        return Files.exists(source) ? source : Path.of("backend").resolve(source);
    }

    private static String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0 && endIndex > startIndex);
        return source.substring(startIndex, endIndex);
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex,
                () -> first + " must remain before " + second);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
