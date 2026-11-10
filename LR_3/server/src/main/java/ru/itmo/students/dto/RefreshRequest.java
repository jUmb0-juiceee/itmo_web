package ru.itmo.students.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(@NotBlank @Size(max = 8192) String refreshToken) {
    @Override public String toString() { return "RefreshRequest[refreshToken=<redacted>]"; }
}
