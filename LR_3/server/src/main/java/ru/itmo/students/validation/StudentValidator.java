package ru.itmo.students.validation;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.itmo.students.dto.StudentQueryRequest;
import ru.itmo.students.dto.StudentWriteRequest;
import ru.itmo.students.exception.ApiException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class StudentValidator {
    private static final Pattern FIO = Pattern.compile("^[А-ЯЁ][а-яё\\-]+ [А-ЯЁ][а-яё\\-]+ [А-ЯЁ][а-яё\\-]+$");
    private static final Pattern GROUP = Pattern.compile("^[A-Z][0-9]{4}$");

    public StudentWriteRequest normalize(StudentWriteRequest input, boolean patch) {
        var errors = new LinkedHashMap<String, Object>();
        String fio = input.fio() == null ? null : input.fio().strip();
        String group = input.group() == null ? null : input.group().strip();
        if (input.isu() == null) errors.put("isu", "Поле 'ИСУ ID' обязательно для заполнения.");
        if (fio == null || fio.isBlank()) errors.put("fio", "Поле 'ФИО' обязательно для заполнения.");
        if (group == null || group.isBlank()) errors.put("group", "Поле 'Группа' обязательно для заполнения.");
        if (!errors.isEmpty()) throw new ApiException(patch ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_REQUEST,
                "Ошибка валидации", errors);

        if (input.isu() < 100000 || input.isu() > 999999) errors.put("isu", "ИСУ ID должен состоять ровно из 6 цифр.");
        if (fio.length() > 150) errors.put("fio", "ФИО не должно превышать 150 символов.");
        else if (!FIO.matcher(fio).matches()) errors.put("fio", "ФИО должно состоять из трёх слов на русском языке, каждое с заглавной буквы.");
        if (group.length() > 10 || !GROUP.matcher(group).matches())
            errors.put("group", "Группа должна состоять из заглавной латинской буквы и 4 цифр (например, M3301).");
        if (input.dorm() != null && (input.dorm() < 1 || input.dorm() > 999))
            errors.put("dorm", "Номер общежития должен быть целым числом от 1 до 999.");
        if (input.room() != null && (input.room() < 1 || input.room() > 9999))
            errors.put("room", "Номер комнаты должен быть целым числом от 1 до 9999.");
        if (input.date() != null && (input.date().getYear() < 2000 || input.date().getYear() > 9999))
            errors.put("date", "Год даты заселения должен быть от 2000 до 9999.");
        String notes = input.notes() == null ? "" : input.notes().strip();
        if (notes.codePointCount(0, notes.length()) > 500) errors.put("notes", "Заметки не должны превышать 500 символов.");
        int count = (input.dorm() == null ? 0 : 1) + (input.room() == null ? 0 : 1) + (input.date() == null ? 0 : 1);
        if (count != 0 && count != 3) errors.put("non_field_errors",
                "Общежитие, комната и дата заселения должны быть заполнены вместе либо все очищены.");
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Ошибка валидации", errors);
        return new StudentWriteRequest(input.isu(), fio, group, input.dorm(), input.room(), input.date(),
                Boolean.TRUE.equals(input.isForeigner()), notes);
    }

    public StudentQueryRequest normalizeQuery(StudentQueryRequest input) {
        int page = input.page() == null || input.page() < 1 ? 1 : input.page();
        int limit = input.limit() == null || input.limit() < 1 || input.limit() > 100 ? 20 : input.limit();
        var errors = new LinkedHashMap<String, Object>();
        String group = input.group() == null || input.group().isBlank() ? null : input.group().strip();
        if (group != null && !GROUP.matcher(group).matches()) errors.put("group", "Некорректный формат группы.");
        if (input.dorm() != null && input.dorm() < 1) errors.put("dorm", "Номер общежития должен быть положительным целым числом.");
        if (input.room() != null && input.room() < 1) errors.put("room", "Номер комнаты должен быть положительным целым числом.");
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Ошибка в параметрах фильтрации", errors);
        StudentSort.parse(input.sort());
        return new StudentQueryRequest(page, limit, input.search() == null ? "" : input.search().strip(), group,
                input.dorm(), input.room(), input.isForeigner(), input.sort());
    }
}
