package ru.itmo.students.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "students")
public class StudentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer isu;

    @Column(nullable = false, length = 150)
    private String fio;

    @Column(name = "student_group", nullable = false, length = 10)
    private String group;

    private Integer dorm;
    private Integer room;

    @Column(name = "settlement_date")
    private LocalDate date;

    @Column(name = "is_foreigner", nullable = false)
    private boolean foreigner;

    @Column(nullable = false, length = 500)
    private String notes;

    protected StudentEntity() { }

    public StudentEntity(Integer isu, String fio, String group, Integer dorm, Integer room,
                         LocalDate date, boolean foreigner, String notes) {
        replace(isu, fio, group, dorm, room, date, foreigner, notes);
    }

    public void replace(Integer isu, String fio, String group, Integer dorm, Integer room,
                        LocalDate date, boolean foreigner, String notes) {
        this.isu = isu;
        this.fio = fio;
        this.group = group;
        this.dorm = dorm;
        this.room = room;
        this.date = date;
        this.foreigner = foreigner;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public Integer getIsu() { return isu; }
    public String getFio() { return fio; }
    public String getGroup() { return group; }
    public Integer getDorm() { return dorm; }
    public Integer getRoom() { return room; }
    public LocalDate getDate() { return date; }
    public boolean isForeigner() { return foreigner; }
    public String getNotes() { return notes; }
}
