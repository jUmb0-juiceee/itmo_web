package ru.itmo.students.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 72) String password) {
    public LoginRequest { username = username == null ? null : username.strip(); }
    @Override public String toString() { return "LoginRequest[password=<redacted>]"; }
}
