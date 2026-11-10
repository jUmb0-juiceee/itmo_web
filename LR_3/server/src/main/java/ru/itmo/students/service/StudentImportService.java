package ru.itmo.students.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.students.entity.StudentEntity;
import ru.itmo.students.mapper.StudentMapper;
import ru.itmo.students.repository.StudentRepository;
import ru.itmo.students.validation.StudentInputParser;
import ru.itmo.students.validation.StudentValidator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;

/** Internal, explicitly enabled startup import; it is not exposed as an HTTP endpoint. */
@Service
public class StudentImportService {
    private final ObjectMapper json;
    private final StudentInputParser parser;
    private final StudentValidator validator;
    private final StudentMapper mapper;
    private final StudentRepository students;

    public StudentImportService(ObjectMapper json, StudentInputParser parser, StudentValidator validator,
                                StudentMapper mapper, StudentRepository students) {
        this.json = json;
        this.parser = parser;
        this.validator = validator;
        this.mapper = mapper;
        this.students = students;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result importFile(Path path) throws IOException {
        JsonNode rows = json.readTree(path.toFile());
        if (rows == null || !rows.isArray()) throw new IllegalArgumentException("Student import must be a JSON array");
        var validated = new ArrayList<StudentEntity>();
        var ids = new HashSet<Integer>();
        for (JsonNode row : rows) {
            var value = validator.normalize(parser.write(row, false), false);
            if (!ids.add(value.isu())) throw new IllegalArgumentException("Duplicate ISU in import: " + value.isu());
            validated.add(mapper.toEntity(value));
        }
        // Validate the complete file before inserting anything. Never replace existing records.
        var additions = new ArrayList<StudentEntity>();
        for (var student : validated) if (!students.existsByIsu(student.getIsu())) additions.add(student);
        students.saveAllAndFlush(additions);
        return new Result(validated.size(), additions.size(), validated.size() - additions.size());
    }

    public record Result(int validated, int imported, int existing) { }
}
