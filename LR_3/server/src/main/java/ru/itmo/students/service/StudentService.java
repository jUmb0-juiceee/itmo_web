package ru.itmo.students.service;

import ru.itmo.students.dto.*;

/** Business boundary: no servlet types or HTTP response objects. */
public interface StudentService {
    StudentPageResponse findAll(StudentQueryRequest query);
    StudentResponse findByIsu(int isu);
    StudentResponse create(StudentWriteRequest request);
    StudentResponse replace(int isu, StudentWriteRequest request);
    StudentResponse patch(int isu, StudentPatchRequest request);
    void delete(int isu);
}
