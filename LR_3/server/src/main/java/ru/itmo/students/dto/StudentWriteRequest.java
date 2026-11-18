package ru.itmo.students.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record StudentWriteRequest(
    @NotNull @Min(100000) @Max(999999) Integer isu,
    @NotBlank @Size(max=150) @Pattern(regexp="^[А-ЯЁ][а-яё\\-]+ [А-ЯЁ][а-яё\\-]+ [А-ЯЁ][а-яё\\-]+$") String fio,
    @NotBlank @Pattern(regexp="^[A-Z][0-9]{4}$") String group,
    @Min(1) @Max(999) Integer dorm,
    @Min(1) @Max(9999) Integer room,
    LocalDate date,
    @JsonProperty("isForeigner") Boolean isForeigner,
    @ru.itmo.students.validation.CodePointSize String notes) { }
