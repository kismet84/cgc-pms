package com.cgcpms.material;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.material.entity.MdMaterial;
import com.cgcpms.material.mapper.MdMaterialMapper;
import com.cgcpms.material.service.MdMaterialImportService;
import io.jsonwebtoken.Jwts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.main.allow-circular-references=true"})
@ActiveProfiles("local")
class MdMaterialImportServiceTest {

    @Autowired private MdMaterialImportService service;
    @Autowired private MdMaterialMapper materialMapper;

    private String nonce;
    private String source;
    private String existingCode;

    @BeforeEach
    void setUp() {
        nonce = Long.toUnsignedString(System.nanoTime());
        source = "IMPORT-TEST-" + nonce;
        existingCode = "EXIST-" + nonce;
        var claims = Jwts.claims().subject("admin")
                .add("userId", 1L).add("username", "admin").add("tenantId", 0L)
                .add("roleCodes", List.of("ADMIN")).build();
        UserContext.set(claims);

        MdMaterial existing = new MdMaterial();
        existing.setMaterialCode(existingCode);
        existing.setMaterialName("既有材料-" + nonce);
        existing.setSpecification("原规格");
        existing.setUnit("原单位");
        existing.setStatus("ENABLE");
        materialMapper.insert(existing);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("template is blank and import is partial, conflict-safe and idempotent")
    void templateAndImportContract() throws Exception {
        byte[] template = service.template();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            assertNotNull(workbook.getSheet("材料导入"));
            assertEquals(0, workbook.getSheet("材料导入").getLastRowNum());
            assertNotNull(workbook.getSheet("填写说明"));
        }

        byte[] content = workbookWithRows(template);
        var first = service.importFile(file(content));
        assertEquals(4, first.total());
        assertEquals(2, first.created());
        assertEquals(1, first.priceUpdated());
        assertEquals(1, first.conflictsCreated());
        assertEquals(1, first.failed());
        assertEquals("MATERIAL_IMPORT_INFO_PRICE_INVALID", first.errors().getFirst().code());

        List<MdMaterial> imported = materialMapper.selectList(null).stream()
                .filter(value -> source.equals(value.getInfoPriceSource())).toList();
        assertEquals(3, imported.size());
        assertEquals(2, imported.stream().filter(value -> value.getMaterialName().equals("冲突材料-" + nonce)).count());
        assertEquals(1, imported.stream().filter(value -> Integer.valueOf(1).equals(value.getInfoPriceReviewRequired())).count());

        MdMaterial existing = imported.stream().filter(value -> existingCode.equals(value.getMaterialCode()))
                .findFirst().orElseThrow();
        assertEquals("既有材料-" + nonce, existing.getMaterialName());
        assertEquals("原规格", existing.getSpecification());
        assertEquals("原单位", existing.getUnit());
        assertEquals(0, new BigDecimal("88.12").compareTo(existing.getTaxInclusiveInfoPrice()));

        var second = service.importFile(file(content));
        assertEquals(0, second.created());
        assertEquals(3, second.skipped());
        assertEquals(1, second.failed());
        assertEquals(3, materialMapper.selectList(null).stream()
                .filter(value -> source.equals(value.getInfoPriceSource())).count());

        BusinessException invalidHeader = assertThrows(BusinessException.class,
                () -> service.importFile(file(workbookWithInvalidHeader(template))));
        assertEquals("MATERIAL_IMPORT_HEADER_INVALID", invalidHeader.getCode());
        assertEquals(3, materialMapper.selectList(null).stream()
                .filter(value -> source.equals(value.getInfoPriceSource())).count());
    }

    private byte[] workbookWithInvalidHeader(byte[] template) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getSheet("材料导入").getRow(0).getCell(0).setCellValue("错误表头");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbookWithRows(byte[] template) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheet("材料导入");
            row(sheet, 1, "", "冲突材料-" + nonce, "一级-" + nonce, "二级-" + nonce,
                    "S1", "吨", "", "43", "2026-07", source, "已人工校正", "13", "", "R1", "");
            row(sheet, 2, "", "冲突材料-" + nonce, "一级-" + nonce, "二级-" + nonce,
                    "S1", "吨", "", "45", "2026-07", source, "建议抽检", "13", "ENABLE", "R2", "");
            row(sheet, 3, "", "非法价格-" + nonce, "", "", "", "", "",
                    "88.123", "2026-07", source, "", "", "", "BAD", "");
            row(sheet, 4, existingCode, "不得覆盖名称", "不应创建分类", "不应创建二级",
                    "不得覆盖规格", "不得覆盖单位", "不得覆盖品牌", "88.12", "2026-07",
                    source, "已人工校正", "99", "DISABLE", "EXISTING", "不得覆盖备注");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void row(Sheet sheet, int index, String... values) {
        Row row = sheet.createRow(index);
        for (int column = 0; column < values.length; column++) row.createCell(column).setCellValue(values[column]);
    }

    private MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "materials.xlsx", MdMaterialImportService.MIME, content);
    }
}
