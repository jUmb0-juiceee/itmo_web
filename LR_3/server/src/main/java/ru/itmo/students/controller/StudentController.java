package ru.itmo.students.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import ru.itmo.students.dto.*;
import ru.itmo.students.service.StudentService;
import ru.itmo.students.validation.StudentInputParser;
import ru.itmo.students.web.QueryMapping;
import java.net.URI;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping(path = "/api/students", produces = MediaType.APPLICATION_JSON_VALUE)
public class StudentController implements StudentApi {
    private final StudentService service;
    private final StudentInputParser parser;

    public StudentController(StudentService service, StudentInputParser parser) {
        this.service = service;
        this.parser = parser;
    }

    @Override
    @GetMapping
    public ResponseEntity<StudentPageResponse> list(@RequestParam Map<String, String> query) {
        return ResponseEntity.ok(service.findAll(parser.query(query)));
    }

    @Override
    @QueryMapping
    public ResponseEntity<StudentPageResponse> query(@RequestBody StudentQueryRequest query) {
        return ResponseEntity.ok(service.findAll(query));
    }

    @Override
    @GetMapping("/{isu}")
    public ResponseEntity<StudentResponse> get(@PathVariable int isu) { return ResponseEntity.ok(service.findByIsu(isu)); }

    @Override
    @PostMapping
    public ResponseEntity<StudentResponse> post(@RequestBody StudentWriteRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/students/" + response.isu())).body(response);
    }

    @Override
    @PutMapping("/{isu}")
    public ResponseEntity<StudentResponse> put(@PathVariable int isu, @RequestBody StudentWriteRequest request) {
        return ResponseEntity.ok(service.replace(isu, request));
    }

    @Override
    @PatchMapping("/{isu}")
    public ResponseEntity<StudentResponse> patch(@PathVariable int isu, @RequestBody StudentPatchRequest request) {
        return ResponseEntity.ok(service.patch(isu, request));
    }

    @Override
    @DeleteMapping("/{isu}")
    public ResponseEntity<Void> delete(@PathVariable int isu) {
        service.delete(isu);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.noContent().header(HttpHeaders.ALLOW, "GET, HEAD, POST, QUERY, OPTIONS").build();
    }

    /** Keeps unsupported collection methods at 405 instead of matching the custom QUERY mapping. */
    @RequestMapping
    public void unsupported(HttpServletRequest request) throws HttpRequestMethodNotSupportedException {
        throw new HttpRequestMethodNotSupportedException(request.getMethod(), List.of("GET", "HEAD", "POST", "QUERY", "OPTIONS"));
    }
}
