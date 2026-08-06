package com.cgcpms.document.canvas;

import com.cgcpms.common.exception.BusinessException;
import com.cgcpms.document.catalog.DocumentTemplateFieldCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class DocumentCanvasCompiler {
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "page", "elements", "tables");
    private static final Set<String> PAGE_FIELDS = Set.of("size", "orientation", "marginMm");
    private static final Set<String> MARGIN_FIELDS = Set.of("top", "right", "bottom", "left");
    private static final Set<String> ELEMENT_FIELDS = Set.of("id", "type", "xMm", "yMm", "widthMm", "heightMm",
            "text", "fieldPath", "fontSizePt", "align", "repeat", "zIndex");
    private static final Set<String> TABLE_FIELDS = Set.of("id", "collectionPath", "xMm", "yMm", "widthMm",
            "heightMm", "columns");
    private static final Set<String> COLUMN_FIELDS = Set.of("fieldPath", "header", "widthMm");
    private static final Set<String> ELEMENT_TYPES = Set.of("TEXT", "FIELD");
    private static final Set<String> ALIGNMENTS = Set.of("LEFT", "CENTER", "RIGHT");
    private static final Set<String> REPEATS = Set.of("BODY", "HEADER", "FOOTER");

    private final ObjectMapper objectMapper;

    public DocumentCanvasCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Compilation compile(String json, DocumentTemplateFieldCatalog.Catalog catalog) {
        JsonNode root = parse(json);
        requireObject(root, "$");
        rejectUnknown(root, ROOT_FIELDS, "$");
        String schemaVersion = requireText(root, "schemaVersion", "$");
        if (!catalog.schemaVersion().equals(schemaVersion)) {
            throw new BusinessException("DOCUMENT_SCHEMA_VERSION_MISMATCH", "画布与字段目录契约版本不一致");
        }

        Page page = page(root.required("page"));
        JsonNode elements = requireArray(root, "elements", "$");
        JsonNode tables = requireArray(root, "tables", "$");
        if (elements.size() + tables.size() == 0 || elements.size() + tables.size() > 200) {
            invalid("$.elements", "画布元素数量必须为1到200");
        }

        Set<String> ids = new LinkedHashSet<>();
        Set<String> fields = new LinkedHashSet<>();
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {
            compileElement(elements.get(i), i, page, catalog, ids, fields, body);
        }
        for (int i = 0; i < tables.size(); i++) {
            compileTable(tables.get(i), i, page, catalog, ids, fields, body);
        }

        String html = "<html><head><meta charset=\"UTF-8\"/><style>"
                + "@page{size:A4 " + page.orientation().toLowerCase(Locale.ROOT) + ";margin:0}"
                + "html,body{margin:0;padding:0}body{position:relative;width:" + number(page.widthMm())
                + "mm;min-height:" + number(page.heightMm()) + "mm;font-family:sans-serif}"
                + ".canvas-item{box-sizing:border-box;overflow:hidden;white-space:pre-wrap}"
                + ".canvas-item--body{position:absolute}.canvas-item--repeat{position:fixed}"
                + "table{border-collapse:collapse;table-layout:fixed;page-break-inside:auto;"
                + "-fs-table-paginate:paginate;overflow:visible}thead{display:table-header-group}"
                + "tr{page-break-inside:avoid}th,td{border:0.2mm solid #333;padding:1mm}"
                + "</style></head><body>" + body + "</body></html>";
        return new Compilation(html, Collections.unmodifiableSet(new LinkedHashSet<>(fields)));
    }

    private void compileElement(JsonNode node, int index, Page page, DocumentTemplateFieldCatalog.Catalog catalog,
                                Set<String> ids, Set<String> fields, StringBuilder html) {
        String path = "$.elements[" + index + "]";
        requireObject(node, path);
        rejectUnknown(node, ELEMENT_FIELDS, path);
        uniqueId(requireText(node, "id", path), ids, path + ".id");
        String type = requireText(node, "type", path).toUpperCase(Locale.ROOT);
        if (!ELEMENT_TYPES.contains(type)) invalid(path + ".type", "仅支持TEXT或FIELD");
        Rect rect = rect(node, path, page);
        double fontSize = optionalNumber(node, "fontSizePt", 10, path, 6, 72);
        double zIndex = optionalNumber(node, "zIndex", 0, path, 0, 100);
        if (zIndex != Math.rint(zIndex)) invalid(path + ".zIndex", "层级必须是整数");
        String align = optionalText(node, "align", "LEFT", path).toUpperCase(Locale.ROOT);
        if (!ALIGNMENTS.contains(align)) invalid(path + ".align", "仅支持LEFT、CENTER或RIGHT");
        String repeat = optionalText(node, "repeat", "BODY", path).toUpperCase(Locale.ROOT);
        if (!REPEATS.contains(repeat)) invalid(path + ".repeat", "仅支持BODY、HEADER或FOOTER");

        String content;
        if ("FIELD".equals(type)) {
            String fieldPath = requireText(node, "fieldPath", path);
            DocumentTemplateFieldCatalog.Field field = catalog.field(fieldPath);
            if (field == null) fieldUnavailable(fieldPath);
            if (field.collectionPath() != null) contextInvalid(fieldPath);
            fields.add(fieldPath);
            content = "{{" + fieldPath + "}}";
        } else {
            content = escapeHtml(requireText(node, "text", path));
        }
        html.append("<div class=\"canvas-item ")
                .append("BODY".equals(repeat) ? "canvas-item--body" : "canvas-item--repeat")
                .append("\" data-repeat=\"").append(repeat).append("\" style=\"").append(rect.css())
                .append("font-size:").append(number(fontSize)).append("pt;text-align:")
                .append(align.toLowerCase(Locale.ROOT)).append(";z-index:").append(number(zIndex))
                .append("\">").append(content).append("</div>");
    }

    private void compileTable(JsonNode node, int index, Page page, DocumentTemplateFieldCatalog.Catalog catalog,
                              Set<String> ids, Set<String> fields, StringBuilder html) {
        String path = "$.tables[" + index + "]";
        requireObject(node, path);
        rejectUnknown(node, TABLE_FIELDS, path);
        uniqueId(requireText(node, "id", path), ids, path + ".id");
        String collectionPath = requireText(node, "collectionPath", path);
        if (!catalog.collectionPaths().contains(collectionPath)) {
            throw new BusinessException("DOCUMENT_FIELD_CONTEXT_INVALID", "集合上下文不存在: " + collectionPath);
        }
        Rect rect = rect(node, path, page);
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
        html.append("<table style=\"").append(rect.tableCss()).append("\"><thead>").append(headers)
                .append("</thead><tbody>{{#each ").append(collectionPath).append("}}")
                .append(cells).append("{{/each}}</tbody></table>");
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
            return "margin-left:" + value(xMm) + "mm;margin-top:" + value(yMm) + "mm;width:"
                    + value(widthMm) + "mm;";
        }

        private static String value(double value) {
            return Double.toString(value).replaceAll("\\.0$", "");
        }
    }
}
