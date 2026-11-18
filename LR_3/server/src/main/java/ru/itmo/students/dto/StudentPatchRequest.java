package ru.itmo.students.dto;
import com.fasterxml.jackson.annotation.*;
import java.util.*;
/** Typed partial update. Setters run only for supplied JSON properties. */
public class StudentPatchRequest {
    private final Set<String> present = new HashSet<>();
    public boolean has(String field) { return present.contains(field); }
    private Integer isu;
    @JsonSetter("isu") public void setIsu(Integer value) { present.add("isu"); isu=value; }
    public Integer isu() { return isu; }
    private String fio;
    @JsonSetter("fio") public void setFio(String value) { present.add("fio"); fio=value; }
    public String fio() { return fio; }
    private String group;
    @JsonSetter("group") public void setGroup(String value) { present.add("group"); group=value; }
    public String group() { return group; }
    private Integer dorm;
    @JsonSetter("dorm") public void setDorm(Integer value) { present.add("dorm"); dorm=value; }
    public Integer dorm() { return dorm; }
    private Integer room;
    @JsonSetter("room") public void setRoom(Integer value) { present.add("room"); room=value; }
    public Integer room() { return room; }
    private java.time.LocalDate date;
    @JsonSetter("date") public void setDate(java.time.LocalDate value) { present.add("date"); date=value; }
    public java.time.LocalDate date() { return date; }
    private Boolean isForeigner;
    @JsonSetter("isForeigner") public void setIsForeigner(Boolean value) { present.add("isForeigner"); isForeigner=value; }
    public Boolean isForeigner() { return isForeigner; }
    private String notes;
    @JsonSetter("notes") public void setNotes(String value) { present.add("notes"); notes=value; }
    public String notes() { return notes; }
    @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown patch field: " + field); }
}
