package com.school.timetabling.rest.dto;

import java.util.List;
import java.util.Map;

public class TimetableResponse {
    private Map<String, StudentGroupSchedule> studentGroupSchedules;
    private String score;
    private boolean feasible;
    private Map<String, Map<String, Integer>> unassignedPeriods;
    private Map<String, Map<String, Map<String, Integer>>> detailedUnassignedPeriods;
    private Map<String, Integer> teacherWorkloadSummary;
    private String message;
    private UnassignedSummary unassignedSummary;

    public TimetableResponse() {}

    // Getters and setters
    public Map<String, StudentGroupSchedule> getStudentGroupSchedules() {
        return studentGroupSchedules;
    }

    public void setStudentGroupSchedules(Map<String, StudentGroupSchedule> studentGroupSchedules) {
        this.studentGroupSchedules = studentGroupSchedules;
    }

    public String getScore() {
        return score;
    }

    public void setScore(String score) {
        this.score = score;
    }

    public boolean isFeasible() {
        return feasible;
    }

    public void setFeasible(boolean feasible) {
        this.feasible = feasible;
    }

    public Map<String, Map<String, Integer>> getUnassignedPeriods() {
        return unassignedPeriods;
    }

    public void setUnassignedPeriods(Map<String, Map<String, Integer>> unassignedPeriods) {
        this.unassignedPeriods = unassignedPeriods;
    }

    public Map<String, Map<String, Map<String, Integer>>> getDetailedUnassignedPeriods() {
        return detailedUnassignedPeriods;
    }

    public void setDetailedUnassignedPeriods(Map<String, Map<String, Map<String, Integer>>> detailedUnassignedPeriods) {
        this.detailedUnassignedPeriods = detailedUnassignedPeriods;
    }

    public Map<String, Integer> getTeacherWorkloadSummary() {
        return teacherWorkloadSummary;
    }

    public void setTeacherWorkloadSummary(Map<String, Integer> teacherWorkloadSummary) {
        this.teacherWorkloadSummary = teacherWorkloadSummary;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UnassignedSummary getUnassignedSummary() {
        return unassignedSummary;
    }

    public void setUnassignedSummary(UnassignedSummary unassignedSummary) {
        this.unassignedSummary = unassignedSummary;
    }

    public static class StudentGroupSchedule {
        private Map<String, Map<String, LessonInfo>> weekSchedule;

        public StudentGroupSchedule() {}

        public Map<String, Map<String, LessonInfo>> getWeekSchedule() {
            return weekSchedule;
        }

        public void setWeekSchedule(Map<String, Map<String, LessonInfo>> weekSchedule) {
            this.weekSchedule = weekSchedule;
        }
    }

    public static class LessonInfo {
        private String subject;
        private String teacher;
        private String startTime;
        private String endTime;

        public LessonInfo() {}

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getTeacher() {
            return teacher;
        }

        public void setTeacher(String teacher) {
            this.teacher = teacher;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }
    }

    public static class UnassignedSummary {
        private int totalUnassignedPeriods;
        private int totalUnassignedClasses;
        private Map<String, GradeUnassignedInfo> gradeBreakdown;

        // Getters and setters
        public int getTotalUnassignedPeriods() { return totalUnassignedPeriods; }
        public void setTotalUnassignedPeriods(int totalUnassignedPeriods) { this.totalUnassignedPeriods = totalUnassignedPeriods; }

        public int getTotalUnassignedClasses() { return totalUnassignedClasses; }
        public void setTotalUnassignedClasses(int totalUnassignedClasses) { this.totalUnassignedClasses = totalUnassignedClasses; }

        public Map<String, GradeUnassignedInfo> getGradeBreakdown() { return gradeBreakdown; }
        public void setGradeBreakdown(Map<String, GradeUnassignedInfo> gradeBreakdown) { this.gradeBreakdown = gradeBreakdown; }
    }

    public static class GradeUnassignedInfo {
        private int totalPeriodsNeeded;
        private int totalClassesAffected;
        private Map<String, SubjectUnassignedInfo> subjects;

        // Getters and setters
        public int getTotalPeriodsNeeded() { return totalPeriodsNeeded; }
        public void setTotalPeriodsNeeded(int totalPeriodsNeeded) { this.totalPeriodsNeeded = totalPeriodsNeeded; }

        public int getTotalClassesAffected() { return totalClassesAffected; }
        public void setTotalClassesAffected(int totalClassesAffected) { this.totalClassesAffected = totalClassesAffected; }

        public Map<String, SubjectUnassignedInfo> getSubjects() { return subjects; }
        public void setSubjects(Map<String, SubjectUnassignedInfo> subjects) { this.subjects = subjects; }
    }

    public static class SubjectUnassignedInfo {
        private int periodsPerWeek;
        private int classesAffected;
        private List<String> affectedClasses;
        private String reason;

        // Getters and setters
        public int getPeriodsPerWeek() { return periodsPerWeek; }
        public void setPeriodsPerWeek(int periodsPerWeek) { this.periodsPerWeek = periodsPerWeek; }

        public int getClassesAffected() { return classesAffected; }
        public void setClassesAffected(int classesAffected) { this.classesAffected = classesAffected; }

        public List<String> getAffectedClasses() { return affectedClasses; }
        public void setAffectedClasses(List<String> affectedClasses) { this.affectedClasses = affectedClasses; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
