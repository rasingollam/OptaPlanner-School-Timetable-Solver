package com.school.timetabling.rest.dto;

import com.school.timetabling.domain.Timeslot;

import java.util.List;

public class TimetableRequest {
    private List<Timeslot> timeslotList;
    private List<ClassInfo> classList;
    private TeacherWorkloadConfig teacherWorkloadConfig;
    private List<String> subjectList;
    private List<LessonAssignment> lessonAssignmentList;

    public TimetableRequest() {}

    // Getters and setters
    public List<Timeslot> getTimeslotList() { return timeslotList; }
    public void setTimeslotList(List<Timeslot> timeslotList) { this.timeslotList = timeslotList; }
    
    public List<ClassInfo> getClassList() { return classList; }
    public void setClassList(List<ClassInfo> classList) { this.classList = classList; }
    
    public TeacherWorkloadConfig getTeacherWorkloadConfig() { return teacherWorkloadConfig; }
    public void setTeacherWorkloadConfig(TeacherWorkloadConfig teacherWorkloadConfig) { 
        this.teacherWorkloadConfig = teacherWorkloadConfig; 
    }
    
    public List<String> getSubjectList() { return subjectList; }
    public void setSubjectList(List<String> subjectList) { this.subjectList = subjectList; }
    
    public List<LessonAssignment> getLessonAssignmentList() { return lessonAssignmentList; }
    public void setLessonAssignmentList(List<LessonAssignment> lessonAssignmentList) { 
        this.lessonAssignmentList = lessonAssignmentList; 
    }

    public static class TeacherWorkloadConfig {
        private int totalTimeslotsPerWeek;
        private int freePeriodsPerTeacherPerWeek;
        private int maxPeriodsPerTeacherPerWeek;

        public TeacherWorkloadConfig() {}

        // Getters and setters
        public int getTotalTimeslotsPerWeek() { return totalTimeslotsPerWeek; }
        public void setTotalTimeslotsPerWeek(int totalTimeslotsPerWeek) { 
            this.totalTimeslotsPerWeek = totalTimeslotsPerWeek; 
        }
        
        public int getFreePeriodsPerTeacherPerWeek() { return freePeriodsPerTeacherPerWeek; }
        public void setFreePeriodsPerTeacherPerWeek(int freePeriodsPerTeacherPerWeek) { 
            this.freePeriodsPerTeacherPerWeek = freePeriodsPerTeacherPerWeek; 
        }
        
        public int getMaxPeriodsPerTeacherPerWeek() { return maxPeriodsPerTeacherPerWeek; }
        public void setMaxPeriodsPerTeacherPerWeek(int maxPeriodsPerTeacherPerWeek) { 
            this.maxPeriodsPerTeacherPerWeek = maxPeriodsPerTeacherPerWeek; 
        }
    }

    public static class ClassInfo {
        private String grade;
        private List<String> classes;

        public ClassInfo() {}

        // Getters and setters
        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }
        
        public List<String> getClasses() { return classes; }
        public void setClasses(List<String> classes) { this.classes = classes; }
    }

    public static class LessonAssignment {
        private String subject;
        private String grade;
        private List<String> possibleTeachers;
        private int periodsPerWeek;
        private int maxPeriodsPerDay;

        public LessonAssignment() {}

        // Getters and setters
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        
        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }
        
        public List<String> getPossibleTeachers() { return possibleTeachers; }
        public void setPossibleTeachers(List<String> possibleTeachers) { 
            this.possibleTeachers = possibleTeachers; 
        }
        
        public int getPeriodsPerWeek() { return periodsPerWeek; }
        public void setPeriodsPerWeek(int periodsPerWeek) { this.periodsPerWeek = periodsPerWeek; }
        
        public int getMaxPeriodsPerDay() { return maxPeriodsPerDay; }
        public void setMaxPeriodsPerDay(int maxPeriodsPerDay) { this.maxPeriodsPerDay = maxPeriodsPerDay; }
    }
}
