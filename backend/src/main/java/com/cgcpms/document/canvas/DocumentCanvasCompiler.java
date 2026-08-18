package com.cgcpms.document.canvas;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

@Component
public class DocumentCanvasCompiler {
    private static final Set<String> V1_ROOT_FIELDS = Set.of("layoutVersion", "schemaVersion", "page", "elements", "tables");
    private static final Set<String> V2_ROOT_FIELDS = Set.of("layoutVersion", "schemaVersion", "page", "elements",
            "tables", "sections");
    private static final Set<String> PAGE_FIELDS = Set.of("size", "orientation", "marginMm");
    private static final Set<String> MARGIN_FIELDS = Set.of("top", "right", "bottom", "left");
    private static final Set<String> ELEMENT_FIELDS = Set.of("id", "type", "xMm", "yMm", "widthMm", "heightMm",
            "text", "fieldPath", "fontSizePt", "align", "repeat", "zIndex");
    private static final Set<String> TABLE_FIELDS = Set.of("id", "collectionPath", "xMm", "yMm", "widthMm",
            "heightMm", "columns");
    private static final Set<String> COLUMN_FIELDS = Set.of("fieldPath", "header", "widthMm");
    private static final Set<String> ELEMENT_TYPES = Set.of("TEXT", "FIELD", "DIVIDER");
    private static final Set<String> ALIGNMENTS = Set.of("LEFT", "CENTER", "RIGHT");
    private static final Set<String> REPEATS = Set.of("BODY", "HEADER", "FOOTER");
    private static final Set<String> SECTION_FIELDS = Set.of("id", "type", "title", "columns", "cells",
            "collectionPath", "fieldPath", "text", "labels");
    private static final Set<String> GRID_CELL_FIELDS = Set.of("label", "fieldPath", "text", "colSpan");
    private static final Set<String> FLOW_SECTION_TYPES = Set.of("FIELD_GRID", "COLLECTION_TABLE", "NOTE",
            "SIGNATURE_GRID");

    private final ObjectMapper objectMapper;

    public DocumentCanvasCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Compilation compile(String json, DocumentTemplateFieldCatalog.Catalog catalog) {
        JsonNode root = parse(json);
        requireObject(root, "$");
        int layoutVersion = root.has("layoutVersion")
                ? requireInteger(root, "layoutVersion", "$", 1, 2) : 1;
        rejectUnknown(root, layoutVersion == 1 ? V1_ROOT_FIELDS : V2_ROOT_FIELDS, "$");
        String schemaVersion = requireText(root, "schemaVersion", "$");
        if (!catalog.schemaVersion().equals(schemaVersion)) {
            throw new BusinessException("DOCUMENT_SCHEMA_VERSION_MISMATCH", "画布与字段目录契约版本不一致");
        }

        Page page = page(root.required("page"));
        JsonNode elements = requireArray(root, "elements", "$");
        JsonNode tables = requireArray(root, "tables", "$");
        JsonNode sections = layoutVersion == 2 ? requireArray(root, "sections", "$") : null;
        if (layoutVersion == 2 && !tables.isEmpty()) invalid("$.tables", "v2流式版式不允许混用旧流式表格");
        int itemCount = elements.size() + tables.size() + (sections == null ? 0 : sections.size());
        if (itemCount == 0 || itemCount > 200) {
            invalid("$.elements", "画布元素数量必须为1到200");
        }

        Set<String> ids = new LinkedHashSet<>();
        Set<String> fields = new LinkedHashSet<>();
        List<BodyElement> bodyElements = new ArrayList<>();
        List<RepeatElement> repeatElements = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        StringBuilder header = new StringBuilder();
        StringBuilder footer = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {
            compileElement(elements.get(i), i, page, catalog, ids, fields, body, header, footer,
                    bodyElements, repeatElements);
        }
        double pageTopReserveMm = layoutVersion == 2 ? pageTopReserve(page, repeatElements) : 0;
        double pageBottomReserveMm = layoutVersion == 2 ? pageBottomReserve(page, repeatElements) : 0;
        double tableBottomMm = 0;
        List<Integer> tableIndexes = IntStream.range(0, tables.size()).boxed()
                .sorted(Comparator.comparingDouble(index -> tables.get(index).path("yMm")
                        .asDouble(Double.POSITIVE_INFINITY)))
                .toList();
        for (int index : tableIndexes) {
            tableBottomMm = compileTable(tables.get(index), index, page, catalog, ids, fields, body,
                    bodyElements, tableBottomMm);
        }
        if (sections != null) {
            compileSections(sections, page, catalog, ids, fields, body, bodyElements, tableBottomMm,
                    pageTopReserveMm);
        }

        String pageMargin = layoutVersion == 2
                ? number(pageTopReserveMm) + "mm 0 " + number(pageBottomReserveMm) + "mm 0" : "0";
        double bodyHeightMm = layoutVersion == 2
                ? page.heightMm() - pageTopReserveMm - pageBottomReserveMm : page.heightMm();
        String pageRegions = layoutVersion == 2
                ? "@top-center{content:element(pageHeader)}@bottom-center{content:element(pageFooter)} " : "";
        String repeatRegions = layoutVersion == 2
                ? "<div class=\"page-header\">" + header + "</div><div class=\"page-footer\">" + footer + "</div>"
                : header.toString() + footer;
        String html = "<html><head><meta charset=\"UTF-8\"/><style>"
                + "@page{size:A4 " + page.orientation().toLowerCase(Locale.ROOT) + ";margin:" + pageMargin
                + ";" + pageRegions + "}"
                + "html,body{margin:0;padding:0}body{position:relative;width:" + number(page.widthMm())
                + "mm;min-height:" + number(bodyHeightMm) + "mm;font-family:sans-serif}"
                + ".page-header{position:running(pageHeader);width:" + number(page.widthMm()) + "mm;height:"
                + number(pageTopReserveMm) + "mm}.page-footer{position:running(pageFooter);width:"
                + number(page.widthMm()) + "mm;height:" + number(pageBottomReserveMm) + "mm}"
                + ".canvas-item{box-sizing:border-box;overflow:hidden;white-space:pre-wrap}"
                + ".canvas-item--body{position:absolute;"
                + (layoutVersion == 2 ? "transform:translateY(-" + number(pageTopReserveMm) + "mm);" : "")
                + "}.canvas-item--repeat{position:"
                + (layoutVersion == 2 ? "absolute" : "fixed") + "}"
                + ".canvas-item[data-repeat=FOOTER]::after{content:\" · 第 \" counter(page) \" 页 / 共 \" counter(pages) \" 页\"}"
                + "table{border-collapse:collapse;table-layout:fixed;page-break-inside:auto;"
                + "-fs-table-paginate:paginate;overflow:visible}thead{display:table-header-group}"
                + "tr{page-break-inside:avoid}th,td{border:0.2mm solid #333;padding:1mm}"
                + ".flow-root{position:relative;box-sizing:border-box}.flow-section{margin:0 0 4mm}"
                + ".flow-section__title{font-size:11pt;font-weight:700;border-left:1mm solid #333;"
                + "padding-left:2mm;margin:0 0 2mm}.flow-grid{width:100%;border-collapse:collapse}"
                + ".flow-grid th{width:18%;background:#f3f4f6;text-align:left;font-weight:600}"
                + ".flow-note{border:0.2mm solid #333;min-height:14mm;padding:2mm;white-space:pre-wrap}"
                + ".flow-signatures{page-break-inside:avoid}.flow-signatures td{height:16mm;vertical-align:top}"
                + ".value-money{text-align:right}.value-date,.value-status{text-align:center}"
                + "</style></head><body>" + repeatRegions + body + "</body></html>";
        return new Compilation(html, Collections.unmodifiableSet(new LinkedHashSet<>(fields)));
    }

    private void compileSections(JsonNode sections, Page page, DocumentTemplateFieldCatalog.Catalog catalog,
                                 Set<String> ids, Set<String> fields, StringBuilder html,
                                 List<BodyElement> bodyElements, double tableBottomMm,
                                 double pageTopReserveMm) {
        if (sections.isEmpty()) invalid("$.sections", "v2流式区块不能为空");
        double bodyBottom = bodyElements.stream()
                .mapToDouble(element -> element.rect().yMm() + element.rect().heightMm()).max().orElse(page.top());
        double flowTop = Math.max(Math.max(bodyBottom, tableBottomMm), page.top());
        if (flowTop >= page.heightMm() - page.bottom()) invalid("$.sections", "流式内容没有可用页面空间");
        html.append("<main class=\"flow-root\" style=\"margin-left:").append(number(page.left()))
                .append("mm;margin-right:").append(number(page.right())).append("mm;margin-top:-")
                .append(number(pageTopReserveMm)).append("mm;padding-top:")
                .append(number(flowTop)).append("mm\">");
        for (int i = 0; i < sections.size(); i++) {
            JsonNode section = sections.get(i);
            String path = "$.sections[" + i + "]";
            requireObject(section, path);
            rejectUnknown(section, SECTION_FIELDS, path);
            uniqueId(requireText(section, "id", path), ids, path + ".id");
            String type = requireText(section, "type", path).toUpperCase(Locale.ROOT);
            if (!FLOW_SECTION_TYPES.contains(type)) invalid(path + ".type", "不支持的流式区块类型");
            switch (type) {
                case "FIELD_GRID" -> compileFieldGrid(section, path, catalog, fields, html);
                case "COLLECTION_TABLE" -> compileCollectionSection(section, path, catalog, fields, html);
                case "NOTE" -> compileNote(section, path, catalog, fields, html);
                case "SIGNATURE_GRID" -> compileSignatureGrid(section, path, html);
                default -> throw new IllegalStateException("Unexpected flow section type: " + type);
            }
        }
        html.append("</main>");
    }

    private void compileFieldGrid(JsonNode section, String path, DocumentTemplateFieldCatalog.Catalog catalog,
                                  Set<String> fields, StringBuilder html) {
        rejectPresent(section, path, "collectionPath", "fieldPath", "text", "labels");
        int columns = requireInteger(section, "columns", path, 1, 3);
        JsonNode cells = requireArray(section, "cells", path);
        if (cells.isEmpty() || cells.size() > 60) invalid(path + ".cells", "信息表单元格必须为1到60个");
        sectionStart(section, path, html, "flow-section flow-section--field-grid");
        html.append("<table class=\"flow-grid\"><tbody><tr>");
        int used = 0;
        for (int i = 0; i < cells.size(); i++) {
            JsonNode cell = cells.get(i);
            String cellPath = path + ".cells[" + i + "]";
            requireObject(cell, cellPath);
            rejectUnknown(cell, GRID_CELL_FIELDS, cellPath);
            String label = requireText(cell, "label", cellPath);
            boolean hasField = cell.has("fieldPath");
            boolean hasText = cell.has("text");
            if (hasField == hasText) invalid(cellPath, "字段与静态文字必须且只能提供一个");
            int span = cell.has("colSpan") ? requireInteger(cell, "colSpan", cellPath, 1, columns) : 1;
            if (used > 0 && used + span > columns) {
                html.append("</tr><tr>");
                used = 0;
            }
            String value;
            String valueClass = "";
            if (hasField) {
                String fieldPath = requireText(cell, "fieldPath", cellPath);
                DocumentTemplateFieldCatalog.Field field = scalarField(catalog, fieldPath);
                fields.add(fieldPath);
                value = "{{" + fieldPath + "}}";
                valueClass = valueClass(field);
            } else {
                value = escapeHtml(requireText(cell, "text", cellPath));
            }
            html.append("<th>").append(escapeHtml(label)).append("</th><td class=\"")
                    .append(valueClass).append("\" colspan=\"").append(span * 2 - 1).append("\">")
                    .append(value).append("</td>");
            used += span;
            if (used == columns && i + 1 < cells.size()) {
                html.append("</tr><tr>");
                used = 0;
            }
        }
        html.append("</tr></tbody></table></section>");
    }

    private void compileCollectionSection(JsonNode section, String path,
                                          DocumentTemplateFieldCatalog.Catalog catalog, Set<String> fields,
                                          StringBuilder html) {
        rejectPresent(section, path, "cells", "fieldPath", "text", "labels");
        String collectionPath = requireText(section, "collectionPath", path);
        if (!catalog.collectionPaths().contains(collectionPath)) contextInvalid(collectionPath);
        JsonNode columns = requireArray(section, "columns", path);
        if (columns.isEmpty() || columns.size() > 8) invalid(path + ".columns", "流式明细表列数必须为1到8");
        sectionStart(section, path, html, "flow-section flow-section--collection");
        StringBuilder headers = new StringBuilder("<tr>");
        StringBuilder cells = new StringBuilder("<tr>");
        double width = 100.0 / columns.size();
        for (int i = 0; i < columns.size(); i++) {
            JsonNode column = columns.get(i);
            String columnPath = path + ".columns[" + i + "]";
            requireObject(column, columnPath);
            rejectUnknown(column, Set.of("fieldPath", "header"), columnPath);
            String fieldPath = requireText(column, "fieldPath", columnPath);
            DocumentTemplateFieldCatalog.Field field = catalog.field(fieldPath);
            if (field == null) fieldUnavailable(fieldPath);
            if (!collectionPath.equals(field.collectionPath())) contextInvalid(fieldPath);
            fields.add(fieldPath);
            headers.append("<th style=\"width:").append(number(width)).append("%\">")
                    .append(escapeHtml(optionalText(column, "header", field.label(), columnPath))).append("</th>");
            cells.append("<td class=\"").append(valueClass(field)).append("\">{{")
                    .append(fieldPath.substring(collectionPath.length() + 1)).append("}}</td>");
        }
        html.append("<table class=\"flow-grid\"><thead>").append(headers).append("</tr></thead><tbody>{{#each ")
                .append(collectionPath).append("}}").append(cells).append("</tr>{{/each}}</tbody></table></section>");
    }

    private void compileNote(JsonNode section, String path, DocumentTemplateFieldCatalog.Catalog catalog,
                             Set<String> fields, StringBuilder html) {
        rejectPresent(section, path, "columns", "cells", "collectionPath", "labels");
        boolean hasField = section.has("fieldPath");
        boolean hasText = section.has("text");
        if (hasField == hasText) invalid(path, "说明区字段与静态文字必须且只能提供一个");
        String value;
        if (hasField) {
            String fieldPath = requireText(section, "fieldPath", path);
            scalarField(catalog, fieldPath);
            fields.add(fieldPath);
            value = "{{" + fieldPath + "}}";
        } else {
            value = escapeHtml(requireText(section, "text", path));
        }
        sectionStart(section, path, html, "flow-section flow-section--note");
        html.append("<div class=\"flow-note\">").append(value).append("</div></section>");
    }

    private void compileSignatureGrid(JsonNode section, String path, StringBuilder html) {
        rejectPresent(section, path, "columns", "cells", "collectionPath", "fieldPath", "text");
        JsonNode labels = requireArray(section, "labels", path);
        if (labels.size() < 2 || labels.size() > 6) invalid(path + ".labels", "手签栏必须包含2到6个签认项");
        sectionStart(section, path, html, "flow-section flow-signatures");
        html.append("<table class=\"flow-grid\"><tr>");
        for (int i = 0; i < labels.size(); i++) {
            JsonNode label = labels.get(i);
            if (!label.isTextual() || label.textValue().isBlank()) invalid(path + ".labels[" + i + "]", "必须是非空字符串");
            html.append("<td>").append(escapeHtml(label.textValue())).append("：</td>");
        }
        html.append("</tr></table></section>");
    }

    private void sectionStart(JsonNode section, String path, StringBuilder html, String className) {
        html.append("<section class=\"").append(className).append("\">");
        if (section.has("title")) {
            html.append("<h2 class=\"flow-section__title\">")
                    .append(escapeHtml(requireText(section, "title", path))).append("</h2>");
        }
    }

    private DocumentTemplateFieldCatalog.Field scalarField(DocumentTemplateFieldCatalog.Catalog catalog,
                                                             String fieldPath) {
        DocumentTemplateFieldCatalog.Field field = catalog.field(fieldPath);
        if (field == null) fieldUnavailable(fieldPath);
        if (field.collectionPath() != null) contextInvalid(fieldPath);
        return field;
    }

    private String valueClass(DocumentTemplateFieldCatalog.Field field) {
        return switch (field.valueType().toUpperCase(Locale.ROOT)) {
            case "MONEY", "NUMBER", "DECIMAL", "INTEGER" -> "value-money";
            case "DATE", "DATETIME" -> "value-date";
            case "ENUM", "STATUS", "BOOLEAN" -> "value-status";
            default -> "";
        };
    }

    private void rejectPresent(JsonNode node, String path, String... names) {
        for (String name : names) if (node.has(name)) invalid(path + "." + name, "当前区块不允许该字段");
    }

    private void compileElement(JsonNode node, int index, Page page, DocumentTemplateFieldCatalog.Catalog catalog,
                                Set<String> ids, Set<String> fields, StringBuilder html,
                                StringBuilder header, StringBuilder footer,
                                List<BodyElement> bodyElements, List<RepeatElement> repeatElements) {
        String path = "$.elements[" + index + "]";
        requireObject(node, path);
        rejectUnknown(node, ELEMENT_FIELDS, path);
        String id = requireText(node, "id", path);
        uniqueId(id, ids, path + ".id");
        String type = requireText(node, "type", path).toUpperCase(Locale.ROOT);
        if (!ELEMENT_TYPES.contains(type)) invalid(path + ".type", "仅支持TEXT、FIELD或DIVIDER");
        Rect rect = rect(node, path, page);
        double fontSize = optionalNumber(node, "fontSizePt", 10, path, 6, 72);
        double zIndex = optionalNumber(node, "zIndex", 0, path, 0, 100);
        if (zIndex != Math.rint(zIndex)) invalid(path + ".zIndex", "层级必须是整数");
        String align = optionalText(node, "align", "LEFT", path).toUpperCase(Locale.ROOT);
        if (!ALIGNMENTS.contains(align)) invalid(path + ".align", "仅支持LEFT、CENTER或RIGHT");
        String repeat = optionalText(node, "repeat", "BODY", path).toUpperCase(Locale.ROOT);
        if (!REPEATS.contains(repeat)) invalid(path + ".repeat", "仅支持BODY、HEADER或FOOTER");
        if ("BODY".equals(repeat)) bodyElements.add(new BodyElement(id, rect));
        else repeatElements.add(new RepeatElement(repeat, rect));

        String content;
        if ("FIELD".equals(type)) {
            String fieldPath = requireText(node, "fieldPath", path);
            DocumentTemplateFieldCatalog.Field field = catalog.field(fieldPath);
            if (field == null) fieldUnavailable(fieldPath);
            if (field.collectionPath() != null) contextInvalid(fieldPath);
            fields.add(fieldPath);
            JsonNode labelNode = node.get("text");
            if (labelNode != null && !labelNode.isTextual()) invalid(path + ".text", "必须是字符串");
            String label = labelNode == null || labelNode.textValue().isBlank()
                    ? "" : escapeHtml(labelNode.textValue()) + " ";
            content = label + "{{" + fieldPath + "}}";
        } else if ("DIVIDER".equals(type)) {
            content = "<hr style=\"border:0;border-top:0.3mm solid #333;margin:0\"/>";
        } else {
            content = escapeHtml(requireText(node, "text", path));
        }
        StringBuilder target = "HEADER".equals(repeat) ? header : "FOOTER".equals(repeat) ? footer : html;
        String rectCss = "FOOTER".equals(repeat) ? rect.footerCss(page.heightMm()) : rect.css();
        target.append("<div class=\"canvas-item ")
                .append("BODY".equals(repeat) ? "canvas-item--body" : "canvas-item--repeat")
                .append("\" data-repeat=\"").append(repeat).append("\" style=\"").append(rectCss)
                .append("font-size:").append(number(fontSize)).append("pt;text-align:")
                .append(align.toLowerCase(Locale.ROOT)).append(";z-index:").append(number(zIndex))
                .append("\">").append(content).append("</div>");
    }

    private double compileTable(JsonNode node, int index, Page page, DocumentTemplateFieldCatalog.Catalog catalog,
                                Set<String> ids, Set<String> fields, StringBuilder html,
                                List<BodyElement> bodyElements, double previousBottomMm) {
        String path = "$.tables[" + index + "]";
        requireObject(node, path);
        rejectUnknown(node, TABLE_FIELDS, path);
        uniqueId(requireText(node, "id", path), ids, path + ".id");
        String collectionPath = requireText(node, "collectionPath", path);
        if (!catalog.collectionPaths().contains(collectionPath)) {
            throw new BusinessException("DOCUMENT_FIELD_CONTEXT_INVALID", "集合上下文不存在: " + collectionPath);
        }
        Rect rect = rect(node, path, page);
        if (rect.yMm() < previousBottomMm - 0.0001) {
            invalid(path, "流式表格设计占位不得重叠");
        }
        for (BodyElement element : bodyElements) {
            if (rect.overlapsHorizontally(element.rect())
                    && element.rect().yMm() + element.rect().heightMm() > rect.yMm() + 0.0001) {
                invalid(path, "流式表格可能与正文元素重叠: " + element.id());
            }
        }
        JsonNode columns = requireArray(node, "columns", path);
        if (columns.isEmpty() || columns.size() > 30) invalid(path + ".columns", "表格列数必须为1到30");

        double totalWidth = 0;
        StringBuilder headers = new StringBuilder("<tr>");
        StringBuilder cells = new StringBuilder("<tr>");
        for (int i = 0; i < columns.size(); i++) {
            JsonNode column = columns.get(i);
            String columnPath = path + ".columns[" + i + "]";
            requireObject(column, columnPath);
            rejectUnknown(column, COLUMN_FIELDS, columnPath);
            String fieldPath = requireText(column, "fieldPath", columnPath);
            DocumentTemplateFieldCatalog.Field field = catalog.field(fieldPath);
            if (field == null) fieldUnavailable(fieldPath);
            if (!collectionPath.equals(field.collectionPath())) contextInvalid(fieldPath);
            double width = requireNumber(column, "widthMm", columnPath, 1, rect.widthMm());
            totalWidth += width;
            fields.add(fieldPath);
            headers.append("<th style=\"width:").append(number(width)).append("mm\">")
                    .append(escapeHtml(optionalText(column, "header", field.label(), columnPath))).append("</th>");
            cells.append("<td>{{").append(fieldPath.substring(collectionPath.length() + 1)).append("}}</td>");
        }
        if (Math.abs(totalWidth - rect.widthMm()) > 0.01) invalid(path + ".columns", "列宽总和必须等于表格宽度");
        headers.append("</tr>");
        cells.append("</tr>");
        double gapMm = rect.yMm() - previousBottomMm;
        if (gapMm > 0) {
            html.append("<div class=\"canvas-table-spacer\" style=\"height:")
                    .append(number(gapMm)).append("mm\"></div>");
        }
        html.append("<table style=\"").append(rect.tableCss()).append("\"><thead>").append(headers)
                .append("</thead><tbody>{{#each ").append(collectionPath).append("}}")
                .append(cells).append("{{/each}}</tbody></table>");
        return rect.yMm() + rect.heightMm();
    }

    private double pageTopReserve(Page page, List<RepeatElement> repeatElements) {
        return repeatElements.stream().filter(element -> "HEADER".equals(element.repeat()))
                .mapToDouble(element -> element.rect().yMm() + element.rect().heightMm() + 3)
                .max().orElse(page.top());
    }

    private double pageBottomReserve(Page page, List<RepeatElement> repeatElements) {
        double contentBottom = repeatElements.stream().filter(element -> "FOOTER".equals(element.repeat()))
                .mapToDouble(element -> element.rect().yMm() - 4).min()
                .orElse(page.heightMm() - page.bottom());
        return Math.max(page.bottom(), page.heightMm() - contentBottom);
    }

    private Page page(JsonNode node) {
        String path = "$.page";
        requireObject(node, path);
        rejectUnknown(node, PAGE_FIELDS, path);
        if (!"A4".equals(requireText(node, "size", path))) invalid(path + ".size", "仅支持A4");
        String orientation = requireText(node, "orientation", path).toUpperCase(Locale.ROOT);
        if (!Set.of("PORTRAIT", "LANDSCAPE").contains(orientation)) {
            invalid(path + ".orientation", "仅支持PORTRAIT或LANDSCAPE");
        }
        JsonNode margin = node.get("marginMm");
        requireObject(margin, path + ".marginMm");
        rejectUnknown(margin, MARGIN_FIELDS, path + ".marginMm");
        double top = requireNumber(margin, "top", path + ".marginMm", 0, 50);
        double right = requireNumber(margin, "right", path + ".marginMm", 0, 50);
        double bottom = requireNumber(margin, "bottom", path + ".marginMm", 0, 50);
        double left = requireNumber(margin, "left", path + ".marginMm", 0, 50);
        double width = "PORTRAIT".equals(orientation) ? 210 : 297;
        double height = "PORTRAIT".equals(orientation) ? 297 : 210;
        if (left + right >= width || top + bottom >= height) invalid(path + ".marginMm", "页边距无可用区域");
        return new Page(orientation, width, height, top, right, bottom, left);
    }

    private Rect rect(JsonNode node, String path, Page page) {
        double x = requireNumber(node, "xMm", path, 0, page.widthMm());
        double y = requireNumber(node, "yMm", path, 0, page.heightMm());
        double width = requireNumber(node, "widthMm", path, 0.1, page.widthMm());
        double height = requireNumber(node, "heightMm", path, 0.1, page.heightMm());
        if (x < page.left() || y < page.top() || x + width > page.widthMm() - page.right() + 0.0001
                || y + height > page.heightMm() - page.bottom() + 0.0001) {
            throw new BusinessException("DOCUMENT_CANVAS_OVERFLOW", "元素越出页面安全区域: " + path);
        }
        return new Rect(x, y, width, height);
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) invalid("$", "画布模型不能为空");
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new BusinessException("DOCUMENT_DESIGN_SCHEMA_INVALID", "画布模型不是合法JSON", exception);
        }
    }

    private void rejectUnknown(JsonNode node, Set<String> allowed, String path) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) invalid(path + "." + name, "未知字段");
        }
    }

    private JsonNode requireArray(JsonNode node, String name, String path) {
        JsonNode value = node.get(name);
        if (value == null || !value.isArray()) invalid(path + "." + name, "必须是数组");
        return value;
    }

    private void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) invalid(path, "必须是对象");
    }

    private String requireText(JsonNode node, String name, String path) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) invalid(path + "." + name, "必须是非空字符串");
        return value.textValue();
    }

    private String optionalText(JsonNode node, String name, String fallback, String path) {
        return node.has(name) ? requireText(node, name, path) : fallback;
    }

    private double requireNumber(JsonNode node, String name, String path, double min, double max) {
        JsonNode value = node.get(name);
        if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())
                || value.doubleValue() < min || value.doubleValue() > max) {
            invalid(path + "." + name, "数值必须在" + number(min) + "到" + number(max) + "之间");
        }
        return value.doubleValue();
    }

    private double optionalNumber(JsonNode node, String name, double fallback, String path, double min, double max) {
        return node.has(name) ? requireNumber(node, name, path, min, max) : fallback;
    }

    private int requireInteger(JsonNode node, String name, String path, int min, int max) {
        double value = requireNumber(node, name, path, min, max);
        if (value != Math.rint(value)) invalid(path + "." + name, "必须是整数");
        return (int) value;
    }

    private void uniqueId(String id, Set<String> ids, String path) {
        if (!id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}") || !ids.add(id)) invalid(path, "ID格式非法或重复");
    }

    private void fieldUnavailable(String path) {
        throw new BusinessException("DOCUMENT_FIELD_UNAVAILABLE", "字段目录不存在字段: " + path);
    }

    private void contextInvalid(String path) {
        throw new BusinessException("DOCUMENT_FIELD_CONTEXT_INVALID", "字段集合上下文错误: " + path);
    }

    private void invalid(String path, String message) {
        throw new BusinessException("DOCUMENT_DESIGN_SCHEMA_INVALID", path + ": " + message);
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String number(double value) {
        return Double.toString(value).replaceAll("\\.0$", "");
    }

    public record Compilation(String html, Set<String> fieldManifest) {
    }

    private record Page(String orientation, double widthMm, double heightMm, double top, double right,
                        double bottom, double left) {
    }

    private record Rect(double xMm, double yMm, double widthMm, double heightMm) {
        String css() {
            return "left:" + value(xMm) + "mm;top:" + value(yMm) + "mm;width:" + value(widthMm)
                    + "mm;height:" + value(heightMm) + "mm;";
        }

        String tableCss() {
            return "margin-left:" + value(xMm) + "mm;width:" + value(widthMm) + "mm;min-height:"
                    + value(heightMm) + "mm;";
        }

        String footerCss(double pageHeightMm) {
            double bottomMm = pageHeightMm - yMm - heightMm;
            return "left:" + value(xMm) + "mm;bottom:" + value(bottomMm) + "mm;width:" + value(widthMm)
                    + "mm;height:" + value(heightMm) + "mm;";
        }

        boolean overlapsHorizontally(Rect other) {
            return xMm < other.xMm + other.widthMm && xMm + widthMm > other.xMm;
        }

        private static String value(double value) {
            return Double.toString(value).replaceAll("\\.0$", "");
        }
    }

    private record BodyElement(String id, Rect rect) {
    }

    private record RepeatElement(String repeat, Rect rect) {
    }
}
