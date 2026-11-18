package ru.itmo.students.controller;

import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import ru.itmo.students.dto.*;
import java.util.Map;

/**
 * HTTP adapter contract, implemented by StudentController.
 * Implementations must delegate to StudentService and never access repositories directly.
 */
public interface StudentApi {
    ResponseEntity<StudentPageResponse> list(Map<String, String> query);
    ResponseEntity<StudentPageResponse> query(StudentQueryRequest query);
    ResponseEntity<StudentResponse> get(int isu);
    ResponseEntity<StudentResponse> post(StudentWriteRequest request);
    ResponseEntity<StudentResponse> put(int isu, StudentWriteRequest request);
    ResponseEntity<StudentResponse> patch(int isu, StudentPatchRequest request);
    ResponseEntity<Void> delete(int isu);
}
