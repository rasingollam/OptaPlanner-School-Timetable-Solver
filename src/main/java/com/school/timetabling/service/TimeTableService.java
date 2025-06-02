package com.school.timetabling.service;

import com.school.timetabling.domain.*;
import com.school.timetabling.rest.dto.TimetableRequest;
import com.school.timetabling.solver.TimeTableConstraintConfig;
import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.api.solver.SolverStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class TimeTableService {

    @Autowired
    private SolverManager<TimeTable, UUID> solverManager;

    private Map<String, Map<String, Integer>> unassignedPeriods = new HashMap<>();
    private Map<String, Map<String, Map<String, Integer>>> detailedUnassignedPeriods = new HashMap<>();

    public TimeTable solve(TimetableRequest request) throws ExecutionException, InterruptedException {
        // Configure constraints with values from request
        configureConstraints(request);
        
        TimeTable problem = convertRequestToProblem(request);
        
        UUID problemId = UUID.randomUUID();
        
        // MAXIMUM ACCURACY solver configuration
        System.out.println("=== MAXIMUM ACCURACY Solver Configuration ===");
        System.out.println("Problem size: " + problem.getLessonList().size() + " lessons");
        System.out.println("Available timeslots: " + problem.getTimeslotList().size());
        System.out.println("Student groups: " + problem.getStudentGroupList().size());
        System.out.println("MAXIMUM solving time: 30 minutes (for highest quality solutions)");
        System.out.println("Multi-phase approach: Construction → Initial (5m) → Deep (10m) → Fine-tuning (15m)");
        System.out.println("Starting MAXIMUM ACCURACY solver...");
        System.out.println("⚠️  This will take up to 30 minutes for best results. Please be patient.");
        
        long startTime = System.currentTimeMillis();
        SolverJob<TimeTable, UUID> solverJob = solverManager.solve(problemId, problem);
        
        // Enhanced progress monitoring for very long runs
        TimeTable bestSolution = null;
        int progressCheckCount = 0;
        String lastBestScore = "N/A";
        long lastScoreChangeTime = startTime;
        
        while (!solverJob.getSolverStatus().equals(SolverStatus.NOT_SOLVING)) {
            try {
                bestSolution = solverJob.getFinalBestSolution();
                if (bestSolution != null && bestSolution.getScore() != null) {
                    String currentScore = bestSolution.getScore().toString();
                    long elapsedMinutes = (System.currentTimeMillis() - startTime) / 60000;
                    long elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000) % 60;
                    
                    // Track score improvements
                    if (!currentScore.equals(lastBestScore)) {
                        lastBestScore = currentScore;
                        lastScoreChangeTime = System.currentTimeMillis();
                        
                        System.out.println(String.format("[%02d:%02d] 🔄 Score improved: %s", 
                            elapsedMinutes, elapsedSeconds, currentScore));
                        
                        // Check for excellent solutions
                        if (bestSolution.getScore().isFeasible()) {
                            int softScore = bestSolution.getScore().softScore();
                            if (softScore >= -10) {
                                System.out.println("🎯 EXCELLENT solution found! (Soft score: " + softScore + ")");
                            } else if (softScore >= -50) {
                                System.out.println("✨ HIGH-QUALITY solution found! (Soft score: " + softScore + ")");
                            } else if (softScore >= 0) {
                                System.out.println("🎯 PERFECT feasible solution found!");
                            }
                        }
                    } else if (progressCheckCount % 24 == 0) { // Every 2 minutes
                        long noImprovementTime = (System.currentTimeMillis() - lastScoreChangeTime) / 60000;
                        System.out.println(String.format("[%02d:%02d] 🔍 Current: %s (no improvement for %d min)", 
                            elapsedMinutes, elapsedSeconds, currentScore, noImprovementTime));
                    }
                }
                progressCheckCount++;
                Thread.sleep(5000); // Check every 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        TimeTable solution = solverJob.getFinalBestSolution();
        long totalSolvingTime = (System.currentTimeMillis() - startTime) / 1000;
        
        System.out.println("=== MAXIMUM ACCURACY Solving Complete ===");
        System.out.println("Total solving time: " + formatTime(totalSolvingTime));
        System.out.println("Final score: " + solution.getScore());
        System.out.println("Solution feasible: " + (solution.getScore() != null && solution.getScore().isFeasible()));
        System.out.println("Quality rating: " + assessSolutionQuality(solution));
        System.out.println("🏆 Solution quality optimized with " + formatTime(totalSolvingTime) + " of computation");
        
        // Detailed solution analysis
        analyzeSolutionQuality(solution);
        
        // Clean up constraint configuration
        TimeTableConstraintConfig.clearConfiguration();
        
        return solution;
    }
    
    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
    
    private String assessSolutionQuality(TimeTable solution) {
        if (solution.getScore() == null) return "Unknown";
        
        if (!solution.getScore().isFeasible()) {
            return "🔴 Infeasible";
        }
        
        int softScore = solution.getScore().softScore();
        if (softScore >= -5) return "🏆 PERFECT";
        if (softScore >= -15) return "🎯 EXCELLENT";
        if (softScore >= -30) return "✨ VERY GOOD";
        if (softScore >= -50) return "🟢 GOOD";
        if (softScore >= -100) return "🟡 FAIR";
        return "🟠 ACCEPTABLE";
    }

    private void analyzeSolutionQuality(TimeTable solution) {
        System.out.println("\n=== Solution Quality Analysis ===");
        
        int assignedLessons = 0;
        int unassignedLessons = 0;
        Map<String, Integer> teacherAssignments = new HashMap<>();
        Map<String, Set<String>> dailySubjectDistribution = new HashMap<>();
        
        for (Lesson lesson : solution.getLessonList()) {
            if (lesson.getTimeslot() != null) {
                assignedLessons++;
                teacherAssignments.merge(lesson.getTeacher(), 1, Integer::sum);
                
                // Track daily subject distribution - Fixed: use getGrade() + getClassName() instead of getName()
                String key = lesson.getStudentGroup().getGrade() + lesson.getStudentGroup().getClassName() + "-" + 
                           lesson.getTimeslot().getDayOfWeek();
                dailySubjectDistribution.computeIfAbsent(key, k -> new HashSet<>())
                                      .add(lesson.getSubject());
            } else {
                unassignedLessons++;
            }
        }
        
        System.out.println("Assigned lessons: " + assignedLessons);
        System.out.println("Unassigned lessons: " + unassignedLessons);
        System.out.println("Assignment rate: " + 
            String.format("%.2f%%", (assignedLessons * 100.0) / solution.getLessonList().size()));
        
        // Teacher workload analysis
        System.out.println("\nTeacher workload distribution:");
        teacherAssignments.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " periods"));
        
        // Subject distribution analysis
        double avgSubjectsPerDay = dailySubjectDistribution.values().stream()
            .mapToInt(Set::size)
            .average()
            .orElse(0.0);
        System.out.println("\nAverage subjects per day per class: " + String.format("%.2f", avgSubjectsPerDay));
    }

    public Map<String, Map<String, Integer>> getUnassignedPeriods() {
        return unassignedPeriods;
    }

    public Map<String, Map<String, Map<String, Integer>>> getDetailedUnassignedPeriods() {
        return detailedUnassignedPeriods;
    }

    private void configureConstraints(TimetableRequest request) {
        // Build maxPeriodsPerDay configuration from request
        Map<String, Map<String, Integer>> maxPeriodsConfig = new HashMap<>();
        
        System.out.println("=== Building Constraint Configuration ===");
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            String subject = assignment.getSubject();
            String grade = assignment.getGrade();
            int maxPeriodsPerDay = assignment.getMaxPeriodsPerDay();
            
            // Log what we're configuring
            System.out.println("Configuring: " + subject + " - Grade " + grade + " = " + maxPeriodsPerDay + " periods/day");
            
            maxPeriodsConfig.computeIfAbsent(subject, k -> new HashMap<>())
                           .put(grade, maxPeriodsPerDay);
        }
        
        // Log final configuration map
        System.out.println("Final maxPeriodsConfig map:");
        maxPeriodsConfig.forEach((subject, gradeMap) -> {
            gradeMap.forEach((grade, maxPerDay) -> {
                System.out.println("  " + subject + " -> " + grade + " -> " + maxPerDay);
            });
        });
        
        // Get teacher workload limit from request (no hardcoded fallback)
        int maxPeriodsPerTeacher;
        if (request.getTeacherWorkloadConfig() != null && 
            request.getTeacherWorkloadConfig().getMaxPeriodsPerTeacherPerWeek() > 0) {
            maxPeriodsPerTeacher = request.getTeacherWorkloadConfig().getMaxPeriodsPerTeacherPerWeek();
        } else {
            // Calculate reasonable default based on request data
            int totalTimeslots = request.getTimeslotList() != null ? request.getTimeslotList().size() : 40;
            maxPeriodsPerTeacher = Math.max(20, totalTimeslots / 2); // Conservative estimate
            System.out.println("WARNING: No teacherWorkloadConfig.maxPeriodsPerTeacherPerWeek specified, using calculated default: " + maxPeriodsPerTeacher);
        }
        
        // Set the configuration for the constraint provider
        TimeTableConstraintConfig.setConfiguration(maxPeriodsConfig, maxPeriodsPerTeacher);
        
        // Validate configuration was set correctly
        if (!TimeTableConstraintConfig.isConfigured()) {
            throw new IllegalStateException("Failed to configure constraints from request data");
        }
        
        // Test the configuration immediately after setting
        System.out.println("=== Testing Configuration ===");
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            String subject = assignment.getSubject();
            String grade = assignment.getGrade();
            int expectedMax = assignment.getMaxPeriodsPerDay();
            int actualMax = TimeTableConstraintConfig.getMaxPeriodsPerDay(subject, grade);
            
            if (expectedMax != actualMax) {
                System.out.println("ERROR: Configuration mismatch for " + subject + " - Grade " + grade + 
                                 ": expected " + expectedMax + ", got " + actualMax);
                throw new IllegalStateException("Configuration verification failed");
            } else {
                System.out.println("VERIFIED: " + subject + " - Grade " + grade + " = " + actualMax + " periods/day");
            }
        }
        
        System.out.println("=== Constraint Configuration Complete ===");
    }

    private TimeTable convertRequestToProblem(TimetableRequest request) {
        // Reset unassigned periods tracker
        unassignedPeriods.clear();
        detailedUnassignedPeriods.clear();
        
        // Create timeslots with IDs
        List<Timeslot> timeslots = new ArrayList<>();
        for (int i = 0; i < request.getTimeslotList().size(); i++) {
            Timeslot ts = request.getTimeslotList().get(i);
            ts.setId((long) i);
            timeslots.add(ts);
        }

        // Generate student groups based on classList from request
        List<StudentGroup> studentGroups = new ArrayList<>();
        for (TimetableRequest.ClassInfo classInfo : request.getClassList()) {
            String grade = classInfo.getGrade();
            for (String className : classInfo.getClasses()) {
                StudentGroup group = new StudentGroup(
                    grade + className, grade, className, 30
                );
                studentGroups.add(group);
            }
        }

        // Generate ALL required lessons and let OptaPlanner handle teacher assignments
        List<Lesson> lessons = generateAllRequiredLessons(request, studentGroups);

        return new TimeTable(timeslots, studentGroups, lessons);
    }

    private List<Lesson> generateAllRequiredLessons(TimetableRequest request, List<StudentGroup> studentGroups) {
        List<Lesson> lessons = new ArrayList<>();
        long lessonId = 0;
        
        // Track teacher workload
        Map<String, Integer> teacherWorkload = new HashMap<>();
        int maxPeriodsPerTeacher = getMaxPeriodsPerTeacher(request);
        
        System.out.println(String.format("=== Teacher Assignment Analysis ==="));
        System.out.println(String.format("Max periods per teacher: %d", maxPeriodsPerTeacher));
        
        // Initialize all possible teachers from all subjects
        Set<String> allTeachers = new HashSet<>();
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            allTeachers.addAll(assignment.getPossibleTeachers());
        }
        
        for (String teacher : allTeachers) {
            teacherWorkload.put(teacher, 0);
        }
        
        System.out.println(String.format("Total teachers available: %d", allTeachers.size()));
        
        // Calculate total demand
        int totalDemand = 0;
        Map<String, Integer> subjectDemand = new HashMap<>();
        
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            String grade = assignment.getGrade();
            int periodsPerWeek = assignment.getPeriodsPerWeek();
            
            // Count classes for this grade
            int classCount = studentGroups.stream()
                .filter(sg -> sg.getGrade().equals(grade))
                .collect(Collectors.toList()).size();
            
            int subjectTotal = periodsPerWeek * classCount;
            subjectDemand.put(assignment.getSubject() + "-Grade" + grade, subjectTotal);
            totalDemand += subjectTotal;
        }
        
        System.out.println(String.format("Total demand: %d periods", totalDemand));
        System.out.println(String.format("Total capacity: %d periods (%d teachers × %d periods)", 
            allTeachers.size() * maxPeriodsPerTeacher, allTeachers.size(), maxPeriodsPerTeacher));
        
        // Create lessons with improved teacher assignment
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            String subject = assignment.getSubject();
            String grade = assignment.getGrade();
            int periodsPerWeek = assignment.getPeriodsPerWeek();
            List<String> possibleTeachers = assignment.getPossibleTeachers();
            
            // Find all student groups for this grade
            List<StudentGroup> classesForGrade = studentGroups.stream()
                .filter(sg -> sg.getGrade().equals(grade))
                .sorted((sg1, sg2) -> sg1.getClassName().compareTo(sg2.getClassName()))
                .collect(Collectors.toList());
            
            System.out.println(String.format("\n--- Processing %s Grade %s ---", subject, grade));
            System.out.println(String.format("Classes: %d, Periods per class: %d, Total periods needed: %d", 
                classesForGrade.size(), periodsPerWeek, classesForGrade.size() * periodsPerWeek));
            System.out.println(String.format("Available teachers: %s", possibleTeachers));
            
            // Sort teachers by current workload (least loaded first)
            List<String> sortedTeachers = possibleTeachers.stream()
                .sorted((t1, t2) -> teacherWorkload.get(t1).compareTo(teacherWorkload.get(t2)))
                .collect(Collectors.toList());
            
            // Assign teachers to classes using load balancing
            for (int classIndex = 0; classIndex < classesForGrade.size(); classIndex++) {
                StudentGroup studentGroup = classesForGrade.get(classIndex);
                String assignedTeacher = null;
                
                // Try to find a teacher with capacity
                for (String candidateTeacher : sortedTeachers) {
                    if (teacherWorkload.get(candidateTeacher) + periodsPerWeek <= maxPeriodsPerTeacher) {
                        assignedTeacher = candidateTeacher;
                        break;
                    }
                }
                
                if (assignedTeacher != null) {
                    // Update teacher workload
                    teacherWorkload.put(assignedTeacher, teacherWorkload.get(assignedTeacher) + periodsPerWeek);
                    
                    // Create all lessons for this class with the assigned teacher
                    for (int period = 0; period < periodsPerWeek; period++) {
                        Lesson lesson = new Lesson(
                            lessonId++,
                            subject,
                            assignedTeacher,
                            studentGroup
                        );
                        lessons.add(lesson);
                    }
                    
                    System.out.println(String.format("✓ Assigned %s to teach %s for class %s%s (%d periods, workload: %d/%d)", 
                        assignedTeacher, subject, grade, studentGroup.getClassName(), 
                        periodsPerWeek, teacherWorkload.get(assignedTeacher), maxPeriodsPerTeacher));
                    
                    // Re-sort teachers for next assignment
                    sortedTeachers.sort((t1, t2) -> teacherWorkload.get(t1).compareTo(teacherWorkload.get(t2)));
                } else {
                    // Track unassigned periods
                    String gradeStr = grade;
                    String className = studentGroup.getClassName();
                    
                    unassignedPeriods.computeIfAbsent(gradeStr, k -> new HashMap<>())
                                   .merge(subject, periodsPerWeek, Integer::sum);
                    
                    detailedUnassignedPeriods.computeIfAbsent(gradeStr, k -> new HashMap<>())
                                           .computeIfAbsent(subject, k -> new HashMap<>())
                                           .put(className, periodsPerWeek);
                    
                    System.out.println(String.format("✗ Could not assign teacher for %s - Grade %s Class %s (%d periods unassigned)", 
                        subject, grade, className, periodsPerWeek));
                    
                    // Show teacher availability
                    System.out.println("Current teacher workloads for this subject:");
                    for (String teacher : possibleTeachers) {
                        int currentLoad = teacherWorkload.get(teacher);
                        int remaining = maxPeriodsPerTeacher - currentLoad;
                        System.out.println(String.format("  %s: %d/%d (remaining: %d, needed: %d)", 
                            teacher, currentLoad, maxPeriodsPerTeacher, remaining, periodsPerWeek));
                    }
                }
            }
        }
        
        System.out.println(String.format("\n=== Final Results ==="));
        System.out.println(String.format("Generated %d lessons total with teacher assignments", lessons.size()));
        
        // Log final teacher workloads
        System.out.println("\nFinal teacher workloads:");
        teacherWorkload.entrySet().stream()
            .filter(entry -> entry.getValue() > 0) // Only show teachers with assignments
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                double utilization = (entry.getValue() * 100.0) / maxPeriodsPerTeacher;
                String status = entry.getValue() >= maxPeriodsPerTeacher ? " (AT CAPACITY)" :
                               entry.getValue() >= maxPeriodsPerTeacher * 0.9 ? " (NEAR CAPACITY)" : "";
                System.out.println(String.format("  %s: %d/%d periods (%.1f%% utilization)%s", 
                    entry.getKey(), entry.getValue(), maxPeriodsPerTeacher, utilization, status));
            });
        
        // Calculate and display assignment statistics
        int totalAssigned = lessons.size();
        int totalUnassigned = totalDemand - totalAssigned;
        double successRate = (totalAssigned * 100.0) / totalDemand;
        
        System.out.println(String.format("\n=== Assignment Statistics ==="));
        System.out.println(String.format("Total demand: %d periods", totalDemand));
        System.out.println(String.format("Successfully assigned: %d periods (%.1f%%)", totalAssigned, successRate));
        System.out.println(String.format("Unassigned: %d periods (%.1f%%)", totalUnassigned, 100.0 - successRate));
        
        // Log unassigned summary
        if (!unassignedPeriods.isEmpty()) {
            System.out.println("\nUnassigned periods breakdown:");
            unassignedPeriods.forEach((grade, subjectMap) -> {
                subjectMap.forEach((subject, count) -> {
                    System.out.println(String.format("  Grade %s - %s: %d periods unassigned", grade, subject, count));
                });
            });
            
            // Suggest solutions
            System.out.println("\n=== Suggested Solutions ===");
            System.out.println("1. Increase maxPeriodsPerTeacherPerWeek in teacherWorkloadConfig");
            System.out.println("2. Add more teachers to subjects with unassigned periods");
            System.out.println("3. Reduce periodsPerWeek for some subjects");
            System.out.println("4. Reduce number of classes per grade");
        }
        
        return lessons;
    }
    
    private int getMaxPeriodsPerTeacher(TimetableRequest request) {
        if (request.getTeacherWorkloadConfig() != null && 
            request.getTeacherWorkloadConfig().getMaxPeriodsPerTeacherPerWeek() > 0) {
            return request.getTeacherWorkloadConfig().getMaxPeriodsPerTeacherPerWeek();
        } else {
            // Calculate reasonable default based on request data
            int totalTimeslots = request.getTimeslotList() != null ? request.getTimeslotList().size() : 40;
            return Math.max(20, totalTimeslots / 2); // Conservative estimate
        }
    }
}
