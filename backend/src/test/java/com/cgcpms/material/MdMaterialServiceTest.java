package com.cgcpms.material;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.service.MdMaterialService;
import com.cgcpms.material.vo.MdMaterialVO;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
@DisplayName("MdMaterialService — CRUD 基础测试")
class MdMaterialServiceTest {

    private static final long USER_ID = 1L;
    private static final long TENANT_ID = 0L;

    @Autowired
    private MdMaterialService mdMaterialService;

    @BeforeEach
    void setUp() {
        setAdminContext();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @Transactional
    @DisplayName("创建材料并查询验证")
    void testCreateMaterial() {
        MdMaterial material = new MdMaterial();
        material.setMaterialCode("TEST-MAT-001");
        material.setMaterialName("测试材料");
        material.setUnit("个");
        material.setStatus("ENABLE");

        Long id = mdMaterialService.create(material);
        assertNotNull(id, "创建后应返回 ID");

        MdMaterialVO saved = mdMaterialService.getById(id);
        assertNotNull(saved, "应能查询到创建的材料");
        assertEquals("TEST-MAT-001", saved.getMaterialCode());
        assertEquals("测试材料", saved.getMaterialName());
    }

    @Test
    @Transactional
    @DisplayName("分页查询材料列表")
    void testGetPage() {
        MdMaterial material = new MdMaterial();
        material.setMaterialCode("PAGE-TEST");
        material.setMaterialName("分页测试材料");
        material.setUnit("个");
        material.setStatus("ENABLE");
        mdMaterialService.create(material);

        var page = mdMaterialService.getPage(1, 10, null, null, null, null);
        assertNotNull(page);
        assertTrue(page.getTotal() > 0, "应能查到数据");
    }

    private void setAdminContext() {
        var claims = Jwts.claims()
                .subject("admin")
                .add("userId", USER_ID)
                .add("username", "admin")
                .add("tenantId", TENANT_ID)
                .add("roleCodes", List.of("ADMIN"))
                .build();
        UserContext.set(claims);
    }
}
