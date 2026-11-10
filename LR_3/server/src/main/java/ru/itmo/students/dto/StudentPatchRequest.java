package ru.itmo.students.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Keeps absent fields distinct from explicit nulls; values are validated in the service. */
public record StudentPatchRequest(Map<String, JsonNode> fields) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public StudentPatchRequest {
        fields = Map.copyOf(fields);
    }

    @Override
    @JsonValue
    public Map<String, JsonNode> fields() { return fields; }
}
