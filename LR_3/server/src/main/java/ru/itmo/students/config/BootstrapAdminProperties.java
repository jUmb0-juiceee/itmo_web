package ru.itmo.students.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.bootstrap-admin")
public record BootstrapAdminProperties(boolean enabled, String username, String email, String password) {
    @Override public String toString() { return "BootstrapAdminProperties[password=<redacted>]"; }
}
