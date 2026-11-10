package ru.itmo.students.mapper;

import org.springframework.stereotype.Component;
import ru.itmo.students.dto.StudentResponse;
import ru.itmo.students.dto.StudentWriteRequest;
import ru.itmo.students.entity.StudentEntity;

@Component
public class StudentMapper {
    public StudentResponse toResponse(StudentEntity entity) {
        return new StudentResponse(entity.getIsu(), entity.getFio(), entity.getGroup(),
                entity.getDorm(), entity.getRoom(), entity.getDate(), entity.isForeigner(), entity.getNotes());
    }

    /** Call only after normalization and validation; optional defaults are applied by the service. */
    public StudentEntity toEntity(StudentWriteRequest request) {
        return new StudentEntity(request.isu(), request.fio(), request.group(), request.dorm(), request.room(),
                request.date(), Boolean.TRUE.equals(request.isForeigner()), request.notes() == null ? "" : request.notes());
    }

    public void replace(StudentEntity entity, StudentWriteRequest request) {
        entity.replace(request.isu(), request.fio(), request.group(), request.dorm(), request.room(),
                request.date(), Boolean.TRUE.equals(request.isForeigner()), request.notes());
    }
}
