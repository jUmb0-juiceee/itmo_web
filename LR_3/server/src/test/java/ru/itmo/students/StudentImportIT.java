package ru.itmo.students;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.students.service.StudentImportService;
import ru.itmo.students.repository.StudentRepository;
import ru.itmo.students.entity.StudentEntity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration-test")
@Transactional
class StudentImportIT {
    @Autowired StudentImportService importer;
    @Autowired StudentRepository students;
    @Autowired ObjectMapper json;
    @TempDir Path directory;

    @Test
    void importIsIdempotentAndDoesNotOverwriteExistingStudents() throws Exception {
        students.saveAndFlush(new StudentEntity(123456, "Иванов Иван Иванович", "M3301", null, null, null, false, "Сохранить"));
        Path file = write(List.of(row(123456, "Иванов Иван Иванович"), row(654321, "Петров Пётр Петрович")));
        assertThat(importer.importFile(file)).isEqualTo(new StudentImportService.Result(2, 1, 1));
        assertThat(students.findByIsu(123456).orElseThrow().getNotes()).isEqualTo("Сохранить");
        assertThat(importer.importFile(file)).isEqualTo(new StudentImportService.Result(2, 0, 2));
    }

    @Test
    void invalidRowPreventsTheWholeImport() throws Exception {
        Path file = write(List.of(row(123456, "Иванов Иван Иванович"), row(654321, "Invalid Name")));
        assertThatThrownBy(() -> importer.importFile(file)).isInstanceOf(RuntimeException.class);
        assertThat(students.findByIsu(123456)).isEmpty();
    }

    @Test
    void duplicateIsuPreventsTheWholeImport() throws Exception {
        Path file = write(List.of(row(123456, "Иванов Иван Иванович"), row(123456, "Петров Пётр Петрович")));
        assertThatThrownBy(() -> importer.importFile(file)).isInstanceOf(IllegalArgumentException.class);
        assertThat(students.findByIsu(123456)).isEmpty();
    }

    private Path write(Object value) throws Exception {
        Path file = directory.resolve("students.json");
        json.writeValue(file.toFile(), value);
        return file;
    }
    private Map<String, Object> row(int isu, String fio) { return Map.of("isu", isu, "fio", fio, "group", "M3301"); }
}
