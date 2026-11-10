package ru.itmo.students.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public record ApiError(String error, @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> details) {
    public ApiError { details = Map.copyOf(details); }
}
