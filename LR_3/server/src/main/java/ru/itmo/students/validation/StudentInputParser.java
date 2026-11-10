package ru.itmo.students.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.itmo.students.dto.*;
import ru.itmo.students.exception.ApiException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Converts external scalar values without Jackson's implicit boolean/number coercions. */
@Component
public class StudentInputParser {
    private static final Set<String> FIELDS = Set.of("isu", "fio", "group", "dorm", "room", "date", "isForeigner", "notes");
    private final ObjectMapper json;

    public StudentInputParser(ObjectMapper json) { this.json = json; }

    public StudentWriteRequest write(JsonNode body, boolean patch) {
        requireObject(body);
        var errors = new LinkedHashMap<String, Object>();
        for (String field : new String[]{"isu", "fio", "group"}) {
            if (absent(body.get(field))) errors.put(field, "Поле обязательно для заполнения.");
        }
        if (!errors.isEmpty()) throw new ApiException(patch ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_REQUEST,
                "Ошибка валидации", errors);
        Integer isu = integer(body.get("isu"), "isu", errors);
        String fio = text(body.get("fio"), "fio", errors);
        String group = text(body.get("group"), "group", errors);
        Integer dorm = integer(body.get("dorm"), "dorm", errors);
        Integer room = integer(body.get("room"), "room", errors);
        String dateString = text(body.get("date"), "date", errors);
        LocalDate date = null;
        if (dateString != null && !dateString.isBlank()) {
            try {
                if (!dateString.strip().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("format", "", 0);
                date = LocalDate.parse(dateString.strip());
            } catch (DateTimeParseException exception) {
                errors.put("date", "Некорректная дата. Используйте формат ГГГГ-ММ-ДД.");
            }
        }
        Boolean foreigner = bool(body.get("isForeigner"), "isForeigner", errors);
        String notes = text(body.get("notes"), "notes", errors);
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Ошибка валидации", errors);
        return new StudentWriteRequest(isu, fio, group, dorm, room, date, foreigner, notes);
    }

    public StudentWriteRequest merge(StudentResponse current, StudentPatchRequest patch) {
        var merged = json.valueToTree(current);
        var object = (com.fasterxml.jackson.databind.node.ObjectNode) merged;
        patch.fields().forEach((field, value) -> { if (FIELDS.contains(field)) object.set(field, value); });
        return write(object, true);
    }

    public StudentQueryRequest query(Map<String, String> params) {
        JsonNode body = json.valueToTree(params);
        return query(body);
    }

    public StudentQueryRequest query(JsonNode body) {
        requireObject(body);
        int page = 1;
        int limit = 20;
        try {
            if (!absent(body.get("page"))) page = paginationInteger(body.get("page"));
            if (!absent(body.get("limit"))) limit = paginationInteger(body.get("limit"));
        } catch (NumberFormatException exception) { page = 1; limit = 20; }
        var errors = new LinkedHashMap<String, Object>();
        String search = text(body.get("search"), "search", errors);
        String group = text(body.get("group"), "group", errors);
        Integer dorm = integer(body.get("dorm"), "dorm", errors);
        Integer room = integer(body.get("room"), "room", errors);
        Boolean foreigner = bool(body.get("isForeigner"), "isForeigner", errors);
        String sort = text(body.get("sort"), "sort", errors);
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Ошибка в параметрах запроса", errors);
        return new StudentQueryRequest(page, limit, search, group, dorm, room, foreigner, sort);
    }

    private int paginationInteger(JsonNode node) {
        if (!(node.isIntegralNumber() || node.isTextual())) throw new NumberFormatException();
        return Integer.parseInt(node.asText().strip());
    }

    private Integer integer(JsonNode node, String field, Map<String, Object> errors) {
        if (absent(node)) return null;
        if (node.isIntegralNumber() && node.canConvertToInt()) return node.intValue();
        if (node.isTextual()) {
            try { return Integer.valueOf(node.textValue().strip()); }
            catch (NumberFormatException ignored) { /* Report as a field error below. */ }
        }
        errors.put(field, "Поле должно содержать целое число.");
        return null;
    }

    private String text(JsonNode node, String field, Map<String, Object> errors) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.textValue();
        errors.put(field, "Поле должно содержать строку.");
        return null;
    }

    private Boolean bool(JsonNode node, String field, Map<String, Object> errors) {
        if (node == null || node.isNull()) return null;
        if (node.isBoolean()) return node.booleanValue();
        if (node.isTextual()) {
            if (node.textValue().strip().equalsIgnoreCase("true")) return true;
            if (node.textValue().strip().equalsIgnoreCase("false")) return false;
        }
        errors.put(field, "Поле должно содержать true или false.");
        return null;
    }

    private boolean absent(JsonNode node) {
        return node == null || node.isNull() || (node.isTextual() && node.textValue().isBlank());
    }

    private void requireObject(JsonNode body) {
        if (body == null || !body.isObject()) throw new ApiException(HttpStatus.BAD_REQUEST, "Тело запроса должно быть JSON-объектом");
    }
}
