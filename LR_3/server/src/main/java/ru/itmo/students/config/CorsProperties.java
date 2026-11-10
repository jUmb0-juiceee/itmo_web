package ru.itmo.students.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {
    public CorsProperties { allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream().map(String::strip).toList(); }
}
