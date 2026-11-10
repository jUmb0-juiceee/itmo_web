package ru.itmo.students.dto;

import java.util.List;

public record StudentPageResponse(List<StudentResponse> data, PaginationResponse pagination) {
    public StudentPageResponse { data = List.copyOf(data); }
}
