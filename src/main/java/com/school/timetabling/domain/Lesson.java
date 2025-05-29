package com.school.timetabling.domain;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class Lesson {
    @PlanningId
    private Long id;
    private String subject;
    private String teacher;
    private StudentGroup studentGroup;

    @PlanningVariable(valueRangeProviderRefs = "timeslotRange")
    private Timeslot timeslot;

    public Lesson() {}

    public Lesson(Long id, String subject, String teacher, StudentGroup studentGroup) {
        this.id = id;
        this.subject = subject;
        this.teacher = teacher;
        this.studentGroup = studentGroup;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getTeacher() { return teacher; }
    public void setTeacher(String teacher) { this.teacher = teacher; }

    public StudentGroup getStudentGroup() { return studentGroup; }
    public void setStudentGroup(StudentGroup studentGroup) { this.studentGroup = studentGroup; }

    public Timeslot getTimeslot() { return timeslot; }
    public void setTimeslot(Timeslot timeslot) { this.timeslot = timeslot; }

    @Override
    public String toString() {
        return subject + "(" + id + ")";
    }
}
