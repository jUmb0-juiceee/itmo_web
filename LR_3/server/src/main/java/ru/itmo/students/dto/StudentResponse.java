package ru.itmo.students.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record StudentResponse(
        Integer isu, String fio, String group, Integer dorm, Integer room, LocalDate date,
        @JsonProperty("isForeigner") boolean isForeigner, String notes) {
}
