package ru.itmo.students.dto;

/** External page numbers start at 1; conversion to Spring's zero-based pages belongs to the service. */
public record StudentQueryRequest(
        Integer page, Integer limit, String search, String group,
        Integer dorm, Integer room, Boolean isForeigner, String sort) {
}
