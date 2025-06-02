package com.school.timetabling.service;

import com.school.timetabling.domain.*;
import com.school.timetabling.rest.dto.TimetableRequest;
import com.school.timetabling.rest.dto.TimetableResponse;
// import com.school.timetabling.solver.TimeTableConstraintConfig;
// import org.optaplanner.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FeasibilityAnalysisService {

    public TimetableResponse.FeasibilityAnalysis analyzeFeasibility(TimeTable solution, TimetableRequest request) {
        TimetableResponse.FeasibilityAnalysis analysis = new TimetableResponse.FeasibilityAnalysis();
        
        // Parse the score
        HardSoftScore score = (HardSoftScore) solution.getScore();
        boolean isFeasible = score != null && score.isFeasible();
        
        analysis.setFeasible(isFeasible);
        analysis.setTotalHardViolations(score != null ? Math.abs(score.hardScore()) : 0);
        analysis.setTotalSoftViolations(score != null ? Math.abs(score.softScore()) : 0);
        
        if (!isFeasible) {
            // Analyze specific constraint violations
            analyzeConstraintViolations(solution, request, analysis);
            generateRecommendations(solution, request, analysis);
            generateSummary(analysis);
        } else {
            analysis.setSummary("All constraints satisfied. Timetable is feasible.");
        }
        
        return analysis;
    }
    
    private void analyzeConstraintViolations(TimeTable solution, TimetableRequest request, TimetableResponse.FeasibilityAnalysis analysis) {
        List<TimetableResponse.ConstraintViolation> hardViolations = new ArrayList<>();
        
        // 1. Teacher Conflict Analysis
        analyzeTeacherConflicts(solution, hardViolations);
        
        // 2. Student Group Conflict Analysis
        analyzeStudentGroupConflicts(solution, hardViolations);
        
        // 3. Max Periods Per Day Violations
        analyzeMaxPeriodsPerDay(solution, request, hardViolations);
        
        // 4. Teacher Workload Violations
        analyzeTeacherWorkloadViolations(solution, request, hardViolations);
        
        // 5. Unassigned Lessons Analysis
        analyzeUnassignedLessons(solution, hardViolations);
        
        analysis.setHardConstraintViolations(hardViolations);
    }
    
    private void analyzeTeacherConflicts(TimeTable solution, List<TimetableResponse.ConstraintViolation> violations) {
        Map<String, Map<Timeslot, List<Lesson>>> teacherTimeslotMap = new HashMap<>();
        
        // Group lessons by teacher and timeslot
        for (Lesson lesson : solution.getLessonList()) {
            if (lesson.getTimeslot() != null && lesson.getTeacher() != null) {
                teacherTimeslotMap.computeIfAbsent(lesson.getTeacher(), k -> new HashMap<>())
                                 .computeIfAbsent(lesson.getTimeslot(), k -> new ArrayList<>())
                                 .add(lesson);
            }
        }
        
        // Find conflicts
        Set<String> conflictingTeachers = new HashSet<>();
        int conflictCount = 0;
        
        for (Map.Entry<String, Map<Timeslot, List<Lesson>>> teacherEntry : teacherTimeslotMap.entrySet()) {
            String teacher = teacherEntry.getKey();
            for (Map.Entry<Timeslot, List<Lesson>> timeslotEntry : teacherEntry.getValue().entrySet()) {
                if (timeslotEntry.getValue().size() > 1) {
                    conflictingTeachers.add(teacher);
                    conflictCount += timeslotEntry.getValue().size() - 1;
                }
            }
        }
        
        if (conflictCount > 0) {
            TimetableResponse.ConstraintViolation violation = new TimetableResponse.ConstraintViolation(
                "Teacher Conflict",
                "Teachers assigned to multiple classes at the same time",
                conflictCount
            );
            violation.setAffectedEntities(new ArrayList<>(conflictingTeachers));
            violation.setSuggestedFix("Reduce teacher workload or add more qualified teachers for conflicting subjects");
            violation.setSeverity("HIGH");
            violations.add(violation);
        }
    }
    
    private void analyzeStudentGroupConflicts(TimeTable solution, List<TimetableResponse.ConstraintViolation> violations) {
        Map<StudentGroup, Map<Timeslot, List<Lesson>>> groupTimeslotMap = new HashMap<>();
        
        // Group lessons by student group and timeslot
        for (Lesson lesson : solution.getLessonList()) {
            if (lesson.getTimeslot() != null && lesson.getStudentGroup() != null) {
                groupTimeslotMap.computeIfAbsent(lesson.getStudentGroup(), k -> new HashMap<>())
                               .computeIfAbsent(lesson.getTimeslot(), k -> new ArrayList<>())
                               .add(lesson);
            }
        }
        
        // Find conflicts
        Set<String> conflictingGroups = new HashSet<>();
        int conflictCount = 0;
        
        for (Map.Entry<StudentGroup, Map<Timeslot, List<Lesson>>> groupEntry : groupTimeslotMap.entrySet()) {
            StudentGroup group = groupEntry.getKey();
            for (Map.Entry<Timeslot, List<Lesson>> timeslotEntry : groupEntry.getValue().entrySet()) {
                if (timeslotEntry.getValue().size() > 1) {
                    conflictingGroups.add(group.getGrade() + group.getClassName());
                    conflictCount += timeslotEntry.getValue().size() - 1;
                }
            }
        }
        
        if (conflictCount > 0) {
            TimetableResponse.ConstraintViolation violation = new TimetableResponse.ConstraintViolation(
                "Student Group Conflict",
                "Student groups assigned to multiple lessons at the same time",
                conflictCount
            );
            violation.setAffectedEntities(new ArrayList<>(conflictingGroups));
            violation.setSuggestedFix("Reduce total lesson requirements or increase available timeslots");
            violation.setSeverity("HIGH");
            violations.add(violation);
        }
    }
    
    private void analyzeMaxPeriodsPerDay(TimeTable solution, TimetableRequest request, List<TimetableResponse.ConstraintViolation> violations) {
        Map<String, Map<String, Integer>> maxPeriodsConfig = new HashMap<>();
        
        // Build expected limits from request
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            maxPeriodsConfig.computeIfAbsent(assignment.getSubject(), k -> new HashMap<>())
                           .put(assignment.getGrade(), assignment.getMaxPeriodsPerDay());
        }
        
        // Analyze actual vs. expected
        Map<String, List<String>> violatingSubjects = new HashMap<>();
        int totalViolations = 0;
        
        // Group lessons by subject, grade, student group, and day
        for (Lesson lesson : solution.getLessonList()) {
            if (lesson.getTimeslot() != null && lesson.getStudentGroup() != null) {
                String subject = lesson.getSubject();
                String grade = lesson.getStudentGroup().getGrade();
                String groupKey = grade + lesson.getStudentGroup().getClassName();
                DayOfWeek day = lesson.getTimeslot().getDayOfWeek();
                
                // Count periods per day for this subject-group combination
                long periodsOnDay = solution.getLessonList().stream()
                    .filter(l -> l.getTimeslot() != null)
                    .filter(l -> l.getSubject().equals(subject))
                    .filter(l -> l.getStudentGroup().equals(lesson.getStudentGroup()))
                    .filter(l -> l.getTimeslot().getDayOfWeek().equals(day))
                    .count();
                
                Integer maxAllowed = maxPeriodsConfig.getOrDefault(subject, new HashMap<>()).get(grade);
                if (maxAllowed != null && periodsOnDay > maxAllowed) {
                    violatingSubjects.computeIfAbsent(subject + " (Grade " + grade + ")", k -> new ArrayList<>())
                                   .add(groupKey + " on " + day + ": " + periodsOnDay + " periods (max: " + maxAllowed + ")");
                    totalViolations += (int)(periodsOnDay - maxAllowed);
                }
            }
        }
        
        if (totalViolations > 0) {
            TimetableResponse.ConstraintViolation violation = new TimetableResponse.ConstraintViolation(
                "Max Periods Per Day Exceeded",
                "Some subjects exceed the maximum allowed periods per day",
                totalViolations
            );
            violation.setAffectedEntities(violatingSubjects.keySet().stream().collect(Collectors.toList()));
            violation.setSuggestedFix("Increase maxPeriodsPerDay for affected subjects or spread lessons across more days");
            violation.setSeverity("MEDIUM");
            violations.add(violation);
        }
    }
    
    private void analyzeTeacherWorkloadViolations(TimeTable solution, TimetableRequest request, List<TimetableResponse.ConstraintViolation> violations) {
        int maxPeriodsPerTeacher = getMaxPeriodsPerTeacher(request);
        
        // Count actual workloads
        Map<String, Integer> teacherWorkloads = new HashMap<>();
        for (Lesson lesson : solution.getLessonList()) {
            if (lesson.getTimeslot() != null && lesson.getTeacher() != null) {
                teacherWorkloads.merge(lesson.getTeacher(), 1, Integer::sum);
            }
        }
        
        // Find overloaded teachers
        List<String> overloadedTeachers = new ArrayList<>();
        int totalOverload = 0;
        
        for (Map.Entry<String, Integer> entry : teacherWorkloads.entrySet()) {
            if (entry.getValue() > maxPeriodsPerTeacher) {
                int overload = entry.getValue() - maxPeriodsPerTeacher;
                overloadedTeachers.add(entry.getKey() + " (" + entry.getValue() + "/" + maxPeriodsPerTeacher + " periods)");
                totalOverload += overload;
            }
        }
        
        if (totalOverload > 0) {
            TimetableResponse.ConstraintViolation violation = new TimetableResponse.ConstraintViolation(
                "Teacher Workload Exceeded",
                "Some teachers exceed maximum allowed periods per week",
                totalOverload
            );
            violation.setAffectedEntities(overloadedTeachers);
            violation.setSuggestedFix("Increase maxPeriodsPerTeacherPerWeek or hire additional teachers for overloaded subjects");
            violation.setSeverity("HIGH");
            violations.add(violation);
        }
    }
    
    private void analyzeUnassignedLessons(TimeTable solution, List<TimetableResponse.ConstraintViolation> violations) {
        List<String> unassignedLessons = new ArrayList<>();
        int unassignedCount = 0;
        
        for (Lesson lesson : solution.getLessonList()) {
            if (lesson.getTimeslot() == null) {
                unassignedLessons.add(lesson.getSubject() + " for " + 
                    lesson.getStudentGroup().getGrade() + lesson.getStudentGroup().getClassName());
                unassignedCount++;
            }
        }
        
        if (unassignedCount > 0) {
            TimetableResponse.ConstraintViolation violation = new TimetableResponse.ConstraintViolation(
                "Unassigned Lessons",
                "Some lessons could not be assigned to any timeslot",
                unassignedCount
            );
            violation.setAffectedEntities(unassignedLessons.stream().distinct().collect(Collectors.toList()));
            violation.setSuggestedFix("Add more timeslots, reduce lesson requirements, or adjust constraint limits");
            violation.setSeverity("HIGH");
            violations.add(violation);
        }
    }
    
    private void generateRecommendations(TimeTable solution, TimetableRequest request, TimetableResponse.FeasibilityAnalysis analysis) {
        List<String> recommendations = new ArrayList<>();
        
        // Analyze the types of violations and suggest specific actions
        for (TimetableResponse.ConstraintViolation violation : analysis.getHardConstraintViolations()) {
            switch (violation.getConstraintName()) {
                case "Teacher Conflict":
                    recommendations.add("🧑‍🏫 Reduce overlapping teacher assignments by hiring more teachers or reducing concurrent class requirements");
                    recommendations.add("📊 Consider increasing maxPeriodsPerTeacherPerWeek to distribute load more evenly");
                    break;
                    
                case "Student Group Conflict":
                    recommendations.add("📅 Add more timeslots to the weekly schedule to accommodate all lessons");
                    recommendations.add("📚 Reduce periodsPerWeek for some subjects to fit within available time");
                    break;
                    
                case "Max Periods Per Day Exceeded":
                    recommendations.add("⏰ Increase maxPeriodsPerDay for subjects that require more intensive scheduling");
                    recommendations.add("📆 Spread lessons across more days of the week");
                    break;
                    
                case "Teacher Workload Exceeded":
                    recommendations.add("👥 Hire additional teachers for subjects with high demand");
                    recommendations.add("⚖️ Increase maxPeriodsPerTeacherPerWeek limit if teachers can handle more periods");
                    break;
                    
                case "Unassigned Lessons":
                    recommendations.add("🕐 Add more timeslots to accommodate all required lessons");
                    recommendations.add("📉 Reduce total lesson requirements (periodsPerWeek) for some subjects");
                    break;
            }
        }
        
        // General recommendations based on overall infeasibility
        if (analysis.getTotalHardViolations() > 0) {
            recommendations.add("🔍 Run the solver for longer (increase termination time) to find better solutions");
            recommendations.add("⚙️ Consider relaxing some constraint limits temporarily to identify the main bottleneck");
        }
        
        // Remove duplicates and set
        analysis.setRecommendedActions(recommendations.stream().distinct().collect(Collectors.toList()));
    }
    
    private void generateSummary(TimetableResponse.FeasibilityAnalysis analysis) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("❌ Timetable is NOT FEASIBLE. ");
        summary.append(String.format("Found %d hard constraint violations. ", analysis.getTotalHardViolations()));
        
        // Categorize violations by severity
        Map<String, Long> violationCounts = analysis.getHardConstraintViolations().stream()
            .collect(Collectors.groupingBy(TimetableResponse.ConstraintViolation::getConstraintName, Collectors.counting()));
        
        if (!violationCounts.isEmpty()) {
            summary.append("Main issues: ");
            violationCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> summary.append(entry.getKey()).append(" (").append(entry.getValue()).append("), "));
            
            // Remove trailing comma and space
            if (summary.toString().endsWith(", ")) {
                summary.setLength(summary.length() - 2);
            }
        }
        
        summary.append(". Check recommendations below for specific solutions.");
        
        analysis.setSummary(summary.toString());
    }
    
    private int getMaxPeriodsPerTeacher(TimetableRequest request) {
        if (request.getTeacherWorkloadConfig() != null && 
            request.getTeacherWorkloadConfig().getMaxPeriodsPerTeacherPerWeek() > 0) {
            return request.getTeacherWorkloadConfig().getMaxPeriodsPerTeacherPerWeek();
        }
        return 20; // Default fallback
    }
}
