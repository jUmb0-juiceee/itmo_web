package ru.itmo.students.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import ru.itmo.students.dto.*;
import ru.itmo.students.entity.StudentEntity;
import ru.itmo.students.exception.ApiException;
import ru.itmo.students.mapper.StudentMapper;
import ru.itmo.students.repository.StudentRepository;
import ru.itmo.students.repository.StudentSpecifications;
import ru.itmo.students.validation.*;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class JpaStudentService implements StudentService {
    private final StudentRepository students;
    private final StudentMapper mapper;
    private final StudentValidator validator;
    private final StudentInputParser parser;

    public JpaStudentService(StudentRepository students, StudentMapper mapper,
                             StudentValidator validator, StudentInputParser parser) {
        this.students = students;
        this.mapper = mapper;
        this.validator = validator;
        this.parser = parser;
    }

    @Override
    public StudentPageResponse findAll(StudentQueryRequest input) {
        var query = validator.normalizeQuery(input);
        var specification = StudentSpecifications.matching(query);
        long total = students.count(specification);
        long offset = ((long) query.page() - 1) * query.limit();
        List<StudentResponse> data = List.of();
        if (offset < total) {
            if (offset > Integer.MAX_VALUE) throw new ApiException(HttpStatus.BAD_REQUEST, "Слишком большой номер страницы");
            var page = PageRequest.of(query.page() - 1, query.limit(), StudentSort.parse(query.sort()));
            data = students.findAll(specification, page).getContent().stream().map(mapper::toResponse).toList();
        }
        long pages = total == 0 ? 0 : (total - 1) / query.limit() + 1;
        return new StudentPageResponse(data, new PaginationResponse(query.page(), query.limit(), total, pages,
                query.page() < pages, query.page() > 1));
    }

    @Override
    public StudentResponse findByIsu(int isu) {
        return mapper.toResponse(students.findByIsu(isu).orElseThrow(this::notFound));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public StudentResponse create(StudentWriteRequest request) {
        var normalized = validator.normalize(request, false);
        ensureIsuAvailable(normalized.isu(), null);
        return mapper.toResponse(students.saveAndFlush(mapper.toEntity(normalized)));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public StudentResponse replace(int isu, StudentWriteRequest request) {
        var entity = forUpdate(isu);
        var normalized = validator.normalize(request, false);
        ensureIsuAvailable(normalized.isu(), entity.getIsu());
        mapper.replace(entity, normalized);
        students.flush();
        return mapper.toResponse(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public StudentResponse patch(int isu, StudentPatchRequest patch) {
        var entity = forUpdate(isu);
        var normalized = validator.normalize(parser.merge(mapper.toResponse(entity), patch), true);
        ensureIsuAvailable(normalized.isu(), entity.getIsu());
        mapper.replace(entity, normalized);
        students.flush();
        return mapper.toResponse(entity);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(int isu) {
        students.delete(forUpdate(isu));
        students.flush();
    }

    private StudentEntity forUpdate(int isu) { return students.findByIsuForUpdate(isu).orElseThrow(this::notFound); }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "Студент не найден"); }

    private void ensureIsuAvailable(Integer candidate, Integer current) {
        if (!candidate.equals(current) && students.existsByIsu(candidate)) {
            throw new ApiException(HttpStatus.CONFLICT, "Студент с таким ИСУ ID уже существует",
                    Map.of("isu", "Студент с таким ИСУ ID уже существует"));
        }
    }
}
