package ru.itmo.students.config;

import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;

@Configuration
public class OpenApiConfiguration {
    /** Explicit schemas keep raw JsonNode/PATCH input and the custom QUERY verb accurate. */
    @Bean
    public OpenAPI studentsOpenApi() throws IOException {
        try (var input = new ClassPathResource("openapi/students-api.yaml").getInputStream()) {
            return Yaml.mapper().readValue(input, OpenAPI.class);
        }
    }
}
