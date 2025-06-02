package com.school.timetabling.rest.dto;

import java.util.Map;

public class TimetableResponse {
    private String score;
    private boolean feasible;
    private String message;
    
    // Using generic Map structure instead of inner classes to avoid complexity
    private Map<String, Map<String, Map<String, Map<String, Object>>>> studentGroupSchedules;
    private Map<String, Map<String, Integer>> unassignedPeriods;
    private Map<String, Map<String, Map<String, Integer>>> detailedUnassignedPeriods;
    private Map<String, Integer> teacherWorkloadSummary;
    private Map<String, Object> unassignedSummary;

    // Constructors
    public TimetableResponse() {}

    // Getters and Setters
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Map<String, Map<String, Map<String, Object>>>> getStudentGroupSchedules() {
        return studentGroupSchedules;
    }

    public void setStudentGroupSchedules(Map<String, Map<String, Map<String, Map<String, Object>>>> studentGroupSchedules) {
        this.studentGroupSchedules = studentGroupSchedules;
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

    public Map<String, Object> getUnassignedSummary() {
        return unassignedSummary;
    }

    public void setUnassignedSummary(Map<String, Object> unassignedSummary) {
        this.unassignedSummary = unassignedSummary;
    }

    // Static inner classes for better structure (optional - can be used later)
    public static class ClassSchedule {
        private Map<String, Map<String, LessonInfo>> weekSchedule;

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

        // Getters and Setters
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
}
