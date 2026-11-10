package ru.itmo.students.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import ru.itmo.students.service.StudentImportService;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class StudentImportConfiguration {
    private static final Logger log = LoggerFactory.getLogger(StudentImportConfiguration.class);

    @Bean
    @Order(10)
    public ApplicationRunner initialStudentImport(StudentImportService importer, ObjectMapper json,
            @Value("${app.import-students.enabled}") boolean enabled,
            @Value("${app.import-students.path}") String input,
            @Value("${app.import-students.report}") String report) {
        return args -> {
            if (!enabled) return;
            if (input.isBlank() || report.isBlank()) throw new IllegalArgumentException("Import input and report paths are required");
            var result = importer.importFile(Path.of(input));
            Path output = Path.of(report).toAbsolutePath();
            Files.createDirectories(output.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), result);
            log.info("Student import complete: validated={}, imported={}, existing={}",
                    result.validated(), result.imported(), result.existing());
        };
    }
}
