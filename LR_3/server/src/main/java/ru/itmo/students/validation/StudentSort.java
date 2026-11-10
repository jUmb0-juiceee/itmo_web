package ru.itmo.students.validation;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import ru.itmo.students.exception.ApiException;
import java.util.Map;

public final class StudentSort {
    private static final Map<String, String> FIELDS = Map.of(
            "isu", "isu", "fio", "fio", "group", "group", "dorm", "dorm", "room", "room",
            "date", "date", "isForeigner", "foreigner", "notes", "notes");

    private StudentSort() { }

    public static Sort parse(String input) {
        if (input == null || input.isBlank()) return Sort.by("id");
        String[] parts = input.strip().split(",", -1);
        if (parts.length != 2 || !FIELDS.containsKey(parts[0])
                || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ошибка в параметре сортировки",
                    Map.of("sort", "Используйте разрешённое поле и направление asc или desc."));
        }
        var direction = parts[1].equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, FIELDS.get(parts[0])).and(Sort.by("id"));
    }
}
