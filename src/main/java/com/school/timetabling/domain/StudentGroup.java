package com.school.timetabling.domain;

import org.optaplanner.core.api.domain.lookup.PlanningId;

public class StudentGroup {
    @PlanningId
    private String id;
    private String grade;
    private String className;
    private int studentCount;

    public StudentGroup() {}

    public StudentGroup(String id, String grade, String className, int studentCount) {
        this.id = id;
        this.grade = grade;
        this.className = className;
        this.studentCount = studentCount;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public int getStudentCount() { return studentCount; }
    public void setStudentCount(int studentCount) { this.studentCount = studentCount; }

    @Override
    public String toString() {
        return grade + className;
    }
}
