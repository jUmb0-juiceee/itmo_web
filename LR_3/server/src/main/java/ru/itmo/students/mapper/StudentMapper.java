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

    public StudentWriteRequest merge(StudentEntity e, ru.itmo.students.dto.StudentPatchRequest p) {
        return new StudentWriteRequest(p.has("isu") ? p.isu() : e.getIsu(), p.has("fio") ? p.fio() : e.getFio(), p.has("group") ? p.group() : e.getGroup(), p.has("dorm") ? p.dorm() : e.getDorm(), p.has("room") ? p.room() : e.getRoom(), p.has("date") ? p.date() : e.getDate(), p.has("isForeigner") ? p.isForeigner() : e.isForeigner(), p.has("notes") ? p.notes() : e.getNotes());
    }
    public static ru.itmo.students.dto.UserResponse user(ru.itmo.students.entity.UserEntity e) {
        return new ru.itmo.students.dto.UserResponse(e.getId(),e.getUsername(),e.getEmail(),e.getRole());
    }
}
