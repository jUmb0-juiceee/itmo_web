package ru.itmo.students.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/** Typed POST/PUT input after scalar parsing; the service normalizes and validates its business rules. */
public record StudentWriteRequest(
        Integer isu, String fio, String group, Integer dorm, Integer room, LocalDate date,
        @JsonProperty("isForeigner") Boolean isForeigner, String notes) {
}
