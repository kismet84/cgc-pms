package com.cgcpms.inventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatStockServiceContractTest {

    private static final Set<String> TRANSACTIONAL_SIGNATURES = Set.of(
            "stockIn(Long,Long,BigDecimal)->MatStock",
            "stockIn(Long,Long,BigDecimal,String,Long)->MatStock",
            "stockIn(Long,Long,BigDecimal,String,Long,Long)->MatStock",
            "stockInValued(Long,Long,BigDecimal,BigDecimal,String,Long,Long)->MatStock",
            "stockOut(Long,Long,BigDecimal)->MatStock",
            "stockOut(Long,Long,BigDecimal,String,Long)->MatStock",
            "stockOut(Long,Long,BigDecimal,String,Long,Long)->MatStock",
            "stockOutValued(Long,Long,BigDecimal,String,Long,Long)->StockMovementResult",
            "stockOutValued(Long,Long,BigDecimal,String,Long,Long,Long)->StockMovementResult",
            "stockOutAtUnitCost(Long,Long,BigDecimal,BigDecimal,String,Long,Long)->StockMovementResult",
            "transfer(StockTransferDTO)->StockTransferVO",
            "updateSafetyStockThreshold(Long,BigDecimal)->MatStock",
            "updateReplenishmentSettings(Long,BigDecimal,BigDecimal,Integer)->MatStock",
            "updateReplenishmentSettings(Long,BigDecimal,BigDecimal,Integer,boolean)->MatStock");

    @Test
    void keepsPublicConstructorMethodsAndTransactionBoundary() {
        Constructor<?> constructor = Arrays.stream(MatStockService.class.getDeclaredConstructors())
                .filter(candidate -> Modifier.isPublic(candidate.getModifiers()))
                .findFirst().orElseThrow();
        assertEquals(List.of(
                "MatStockMapper", "MatStockTransferMapper", "MatStockTxnMapper", "MatWarehouseMapper",
                "MdMaterialMapper", "ProjectAccessChecker", "MatPurchaseOrderMapper",
                "MatPurchaseOrderItemMapper", "BusinessReferenceService"),
                Arrays.stream(constructor.getParameterTypes()).map(Class::getSimpleName).toList());
        assertNotNull(MatStockService.class.getAnnotation(Service.class));

        Set<String> publicMethods = Arrays.stream(MatStockService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(MatStockServiceContractTest::signature)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "stockIn(Long,Long,BigDecimal)->MatStock",
                "stockIn(Long,Long,BigDecimal,String,Long)->MatStock",
                "stockIn(Long,Long,BigDecimal,String,Long,Long)->MatStock",
                "stockInValued(Long,Long,BigDecimal,BigDecimal,String,Long,Long)->MatStock",
                "stockOut(Long,Long,BigDecimal)->MatStock",
                "stockOut(Long,Long,BigDecimal,String,Long)->MatStock",
                "stockOut(Long,Long,BigDecimal,String,Long,Long)->MatStock",
                "stockOutValued(Long,Long,BigDecimal,String,Long,Long)->StockMovementResult",
                "stockOutValued(Long,Long,BigDecimal,String,Long,Long,Long)->StockMovementResult",
                "stockOutAtUnitCost(Long,Long,BigDecimal,BigDecimal,String,Long,Long)->StockMovementResult",
                "getPage(Long,Long,Long,String,long,long)->PageResult",
                "getLedger(Long,Long,Long,String,String,String,long,long)->MatStockLedgerVO",
                "getKpi(Long,Long)->StockKpiVO", "getTransferCandidates(Long)->List",
                "getConsumptionBaseline(Long)->StockConsumptionBaselineVO",
                "transfer(StockTransferDTO)->StockTransferVO", "getIncomingSupplies(Long)->List",
                "updateSafetyStockThreshold(Long,BigDecimal)->MatStock",
                "updateReplenishmentSettings(Long,BigDecimal,BigDecimal,Integer)->MatStock",
                "updateReplenishmentSettings(Long,BigDecimal,BigDecimal,Integer,boolean)->MatStock",
                "toStockVO(MatStock)->MatStockVO"), publicMethods);

        List<Method> transactionalMethods = Arrays.stream(MatStockService.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(Transactional.class) != null)
                .toList();
        assertEquals(14, transactionalMethods.size());
        assertEquals(TRANSACTIONAL_SIGNATURES,
                transactionalMethods.stream().map(MatStockServiceContractTest::signature).collect(Collectors.toSet()));
        for (Method method : transactionalMethods) {
            assertArrayEquals(new Class<?>[]{Exception.class},
                    method.getAnnotation(Transactional.class).rollbackFor());
        }
    }

    @Test
    void keepsMovementResultRecordStable() {
        assertEquals(List.of("stock:MatStock", "unitCost:BigDecimal", "amount:BigDecimal"),
                Arrays.stream(MatStockService.StockMovementResult.class.getRecordComponents())
                        .map(component -> component.getName() + ":" + component.getType().getSimpleName())
                        .toList());
    }

    @Test
    void keepsReadOperationsAsPackagePrivatePlainHelper() {
        assertTrue(Modifier.isFinal(MatStockReadOperations.class.getModifiers()));
        assertFalse(Modifier.isPublic(MatStockReadOperations.class.getModifiers()));
        assertNull(MatStockReadOperations.class.getAnnotation(Service.class));
        assertNull(MatStockReadOperations.class.getAnnotation(Component.class));
        assertTrue(Arrays.stream(MatStockReadOperations.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
        assertTrue(Arrays.stream(MatStockReadOperations.class.getDeclaredMethods())
                .noneMatch(method -> method.getAnnotation(Transactional.class) != null));
    }

    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"))
                + "->" + method.getReturnType().getSimpleName();
    }
}
