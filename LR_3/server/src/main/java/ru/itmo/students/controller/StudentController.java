package ru.itmo.students.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
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
@Hidden // Avoid treating QUERY's method condition as mappings for every standard verb.
@RequestMapping(path = "/api/requests", produces = MediaType.APPLICATION_JSON_VALUE)
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
    public ResponseEntity<StudentPageResponse> query(@RequestBody JsonNode query) {
        return ResponseEntity.ok(service.findAll(parser.query(query)));
    }

    @Override
    @GetMapping("/{isu}")
    public ResponseEntity<StudentResponse> get(@PathVariable int isu) { return ResponseEntity.ok(service.findByIsu(isu)); }

    @Override
    @PostMapping
    public ResponseEntity<StudentResponse> post(@RequestBody JsonNode request) {
        var response = service.create(parser.write(request, false));
        return ResponseEntity.created(URI.create("/api/requests/" + response.isu())).body(response);
    }

    @Override
    @PutMapping("/{isu}")
    public ResponseEntity<StudentResponse> put(@PathVariable int isu, @RequestBody JsonNode request) {
        return ResponseEntity.ok(service.replace(isu, parser.write(request, false)));
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
