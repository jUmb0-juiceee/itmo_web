package ru.itmo.students.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.itmo.students.dto.StudentQueryRequest;
import ru.itmo.students.entity.StudentEntity;
import java.util.ArrayList;
import java.util.Locale;

public final class StudentSpecifications {
    private StudentSpecifications() { }

    public static Specification<StudentEntity> matching(StudentQueryRequest input) {
        return (student, query, builder) -> {
            var criteria = new ArrayList<Predicate>();
            if (input.group() != null) criteria.add(builder.equal(student.get("group"), input.group()));
            if (input.dorm() != null) criteria.add(builder.equal(student.get("dorm"), input.dorm()));
            if (input.room() != null) criteria.add(builder.equal(student.get("room"), input.room()));
            if (input.isForeigner() != null) criteria.add(builder.equal(student.get("foreigner"), input.isForeigner()));
            if (!input.search().isEmpty()) {
                String escaped = input.search().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                criteria.add(builder.or(
                        builder.like(builder.lower(student.get("fio")), pattern, '\\'),
                        builder.like(builder.lower(student.get("notes")), pattern, '\\')));
            }
            return builder.and(criteria.toArray(Predicate[]::new));
        };
    }
}
