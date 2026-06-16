package com.cgcpms.cost.service;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.cost.entity.CostSubject;
import com.cgcpms.cost.mapper.CostSubjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;
import java.util.List;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class CostSubjectServiceTest {

    private static final long USER_ID = 1L;
    private static final long TENANT_ID = 0L;

    @Autowired
    private CostSubjectService costSubjectService;

    @Autowired
    private CostSubjectMapper costSubjectMapper;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("create reports business duplicate when code is occupied by logically deleted subject")
    void createDuplicateCodeOccupiedByDeletedSubject() {
        setAdminContext();

        CostSubject deletedSubject = new CostSubject();
        deletedSubject.setTenantId(TENANT_ID);
        deletedSubject.setParentId(0L);
        deletedSubject.setSubjectCode("TEST-DELETED-DUP");
        deletedSubject.setSubjectName("已删除科目");
        deletedSubject.setSubjectType("MATERIAL");
        deletedSubject.setLevel(1);
        deletedSubject.setSortOrder(1);
        deletedSubject.setStatus("ENABLE");
        costSubjectMapper.insert(deletedSubject);
        costSubjectMapper.deleteById(deletedSubject.getId());

        CostSubject duplicate = new CostSubject();
        duplicate.setSubjectCode("TEST-DELETED-DUP");
        duplicate.setSubjectName("重复科目");
        duplicate.setSubjectType("MATERIAL");
        duplicate.setSortOrder(2);
        duplicate.setStatus("ENABLE");

        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> costSubjectService.create(duplicate));
        Assertions.assertEquals("SUBJECT_CODE_DUPLICATE", exception.getCode());
    }

    private void setAdminContext() {
        var claims = Jwts.claims()
                .subject("admin")
                .add("userId", USER_ID)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .build();
        UserContext.set(claims);
    }
}
