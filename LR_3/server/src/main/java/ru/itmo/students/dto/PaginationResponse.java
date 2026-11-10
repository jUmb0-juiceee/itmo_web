package ru.itmo.students.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaginationResponse(
        int page, int limit, long total,
        @JsonProperty("total_pages") long totalPages,
        @JsonProperty("has_next") boolean hasNext,
        @JsonProperty("has_prev") boolean hasPrev) {
}
