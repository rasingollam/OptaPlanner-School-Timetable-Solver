package com.school.timetabling.service;

import com.school.timetabling.domain.*;
import com.school.timetabling.rest.dto.TimetableRequest;
import com.school.timetabling.solver.TimeTableConstraintConfig;
import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.api.solver.SolverStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TimeTableService.class);

    @Autowired
    private SolverManager<TimeTable, UUID> solverManager;

    private Map<String, Map<String, Integer>> unassignedPeriods = new HashMap<>();
    private Map<String, Map<String, Map<String, Integer>>> detailedUnassignedPeriods = new HashMap<>();

    public TimeTable solve(TimetableRequest request) throws ExecutionException, InterruptedException {
        // Configure constraints with values from request
        configureConstraints(request);
        
        TimeTable problem = convertRequestToProblem(request);
        
        UUID problemId = UUID.randomUUID();
        
        log.info("=== MAXIMUM ACCURACY Solver Configuration ===");
        log.info("Problem size: {} lessons", problem.getLessonList().size());
        log.info("Available timeslots: {}", problem.getTimeslotList().size());
        log.info("Student groups: {}", problem.getStudentGroupList().size());
        log.info("Starting MAXIMUM ACCURACY solver...");
        
        long startTime = System.currentTimeMillis();
        SolverJob<TimeTable, UUID> solverJob = solverManager.solve(problemId, problem);
        
        // Enhanced progress monitoring for very long runs
        TimeTable bestSolution = null;
        String lastBestScore = "N/A";
        
        while (!solverJob.getSolverStatus().equals(SolverStatus.NOT_SOLVING)) {
            try {
                bestSolution = solverJob.getFinalBestSolution();
                if (bestSolution != null && bestSolution.getScore() != null) {
                    String currentScore = bestSolution.getScore().toString();
                    
                    // Track score improvements
                    if (!currentScore.equals(lastBestScore)) {
                        lastBestScore = currentScore;
                        long elapsedMinutes = (System.currentTimeMillis() - startTime) / 60000;
                        long elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000) % 60;
                        log.info("[{}:{}] Score improved: {}", String.format("%02d", elapsedMinutes), String.format("%02d", elapsedSeconds), currentScore);
                        
                        if (bestSolution.getScore().isFeasible()) {
                            int softScore = bestSolution.getScore().softScore();
                            if (softScore >= 0) {
                                log.info("🎯 PERFECT feasible solution found!");
                            } else if (softScore >= -10) {
                                log.info("🎯 EXCELLENT solution found! (Soft score: {})", softScore);
                            }
                        }
                    }
                }
                Thread.sleep(5000); // Check every 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        TimeTable solution = solverJob.getFinalBestSolution();
        long totalSolvingTime = (System.currentTimeMillis() - startTime) / 1000;
        
        log.info("=== MAXIMUM ACCURACY Solving Complete ===");
        log.info("Total solving time: {}", formatTime(totalSolvingTime));
        log.info("Final score: {}", solution.getScore());
        log.info("Solution feasible: {}", (solution.getScore() != null && solution.getScore().isFeasible()));
        log.info("Quality rating: {}", assessSolutionQuality(solution));
        
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
        log.info("=== Solution Quality Analysis ===");
        
        int assignedLessons = 0;
        int unassignedLessons = 0;
        
        for (Lesson lesson : solution.getLessonList()) {
            if (lesson.getTimeslot() != null) {
                assignedLessons++;
            } else {
                unassignedLessons++;
            }
        }
        
        log.info("Assigned lessons: {}", assignedLessons);
        log.info("Unassigned lessons: {}", unassignedLessons);
        log.info("Assignment rate: {}%", String.format("%.2f", (assignedLessons * 100.0) / solution.getLessonList().size()));
    }

    public Map<String, Map<String, Integer>> getUnassignedPeriods() {
        return unassignedPeriods;
    }

    public Map<String, Map<String, Map<String, Integer>>> getDetailedUnassignedPeriods() {
        return detailedUnassignedPeriods;
    }

    private void configureConstraints(TimetableRequest request) {
        Map<String, Map<String, Integer>> maxPeriodsConfig = new HashMap<>();
        
        log.debug("=== Building Constraint Configuration ===");
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            String subject = assignment.getSubject();
            String grade = assignment.getGrade();
            int maxPeriodsPerDay = assignment.getMaxPeriodsPerDay();
            
            log.debug("Configuring: {} - Grade {} = {} periods/day", subject, grade, maxPeriodsPerDay);
            
            maxPeriodsConfig.computeIfAbsent(subject, k -> new HashMap<>())
                           .put(grade, maxPeriodsPerDay);
        }
        
        int maxPeriodsPerTeacher;
        if (request.getTeacherWorkloadConfig() != null && 
            request.getTeacherWorkloadConfig().getMaxPeriodsPerTeacherPerWeek() > 0) {
            maxPeriodsPerTeacher = request.getTeacherWorkloadConfig().getMaxPeriodsPerTeacherPerWeek();
        } else {
            int totalTimeslots = request.getTimeslotList() != null ? request.getTimeslotList().size() : 40;
            maxPeriodsPerTeacher = Math.max(20, totalTimeslots / 2);
            log.warn("No teacherWorkloadConfig.maxPeriodsPerTeacherPerWeek specified, using calculated default: {}", maxPeriodsPerTeacher);
        }
        
        TimeTableConstraintConfig.setConfiguration(maxPeriodsConfig, maxPeriodsPerTeacher);
        
        if (!TimeTableConstraintConfig.isConfigured()) {
            throw new IllegalStateException("Failed to configure constraints from request data");
        }
        log.debug("=== Constraint Configuration Complete ===");
    }

    private TimeTable convertRequestToProblem(TimetableRequest request) {
        unassignedPeriods.clear();
        detailedUnassignedPeriods.clear();
        
        List<Timeslot> timeslots = new ArrayList<>();
        for (int i = 0; i < request.getTimeslotList().size(); i++) {
            Timeslot ts = request.getTimeslotList().get(i);
            ts.setId((long) i);
            timeslots.add(ts);
        }

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

        List<Lesson> lessons = generateAllRequiredLessons(request, studentGroups);

        return new TimeTable(timeslots, studentGroups, lessons);
    }

    private List<Lesson> generateAllRequiredLessons(TimetableRequest request, List<StudentGroup> studentGroups) {
        List<Lesson> lessons = new ArrayList<>();
        long lessonId = 0;
        
        Map<String, Integer> teacherWorkload = new HashMap<>();
        int maxPeriodsPerTeacher = getMaxPeriodsPerTeacher(request);
        
        log.info("=== Teacher Assignment Analysis ===");
        log.info("Max periods per teacher: {}", maxPeriodsPerTeacher);
        
        Set<String> allTeachers = new HashSet<>();
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            allTeachers.addAll(assignment.getPossibleTeachers());
        }
        
        for (String teacher : allTeachers) {
            teacherWorkload.put(teacher, 0);
        }
        
        log.info("Total teachers available: {}", allTeachers.size());
        
        int totalDemand = 0;
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            String grade = assignment.getGrade();
            int periodsPerWeek = assignment.getPeriodsPerWeek();
            long classCount = studentGroups.stream().filter(sg -> sg.getGrade().equals(grade)).count();
            totalDemand += periodsPerWeek * classCount;
        }
        
        log.info("Total demand: {} periods", totalDemand);
        log.info("Total capacity: {} periods ({} teachers × {} periods)", 
            allTeachers.size() * maxPeriodsPerTeacher, allTeachers.size(), maxPeriodsPerTeacher);
        
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            String subject = assignment.getSubject();
            String grade = assignment.getGrade();
            int periodsPerWeek = assignment.getPeriodsPerWeek();
            List<String> possibleTeachers = assignment.getPossibleTeachers();
            
            List<StudentGroup> classesForGrade = studentGroups.stream()
                .filter(sg -> sg.getGrade().equals(grade))
                .sorted((sg1, sg2) -> sg1.getClassName().compareTo(sg2.getClassName()))
                .collect(Collectors.toList());
            
            log.debug("--- Processing {} Grade {} ---", subject, grade);
            
            List<String> sortedTeachers = possibleTeachers.stream()
                .sorted((t1, t2) -> teacherWorkload.get(t1).compareTo(teacherWorkload.get(t2)))
                .collect(Collectors.toList());
            
            for (StudentGroup studentGroup : classesForGrade) {
                String assignedTeacher = null;
                
                for (String candidateTeacher : sortedTeachers) {
                    if (teacherWorkload.get(candidateTeacher) + periodsPerWeek <= maxPeriodsPerTeacher) {
                        assignedTeacher = candidateTeacher;
                        break;
                    }
                }
                
                if (assignedTeacher != null) {
                    teacherWorkload.merge(assignedTeacher, periodsPerWeek, Integer::sum);
                    
                    for (int period = 0; period < periodsPerWeek; period++) {
                        lessons.add(new Lesson(lessonId++, subject, assignedTeacher, studentGroup));
                    }
                    
                    log.debug("✓ Assigned {} to teach {} for class {}{}", 
                        assignedTeacher, subject, grade, studentGroup.getClassName());
                    
                    sortedTeachers.sort((t1, t2) -> teacherWorkload.get(t1).compareTo(teacherWorkload.get(t2)));
                } else {
                    unassignedPeriods.computeIfAbsent(grade, k -> new HashMap<>())
                                   .merge(subject, periodsPerWeek, Integer::sum);
                    
                    detailedUnassignedPeriods.computeIfAbsent(grade, k -> new HashMap<>())
                                           .computeIfAbsent(subject, k -> new HashMap<>())
                                           .put(studentGroup.getClassName(), periodsPerWeek);
                    
                    log.warn("✗ Could not assign teacher for {} - Grade {} Class {} ({} periods unassigned)", 
                        subject, grade, studentGroup.getClassName(), periodsPerWeek);
                }
            }
        }
        
        log.info("=== Final Results ===");
        log.info("Generated {} lessons total with teacher assignments", lessons.size());
        
        int totalAssigned = lessons.size();
        double successRate = (totalDemand > 0) ? (totalAssigned * 100.0) / totalDemand : 0.0;
        
        log.info("=== Assignment Statistics ===");
        log.info("Total demand: {} periods", totalDemand);
        log.info("Successfully assigned: {} periods ({}%)", totalAssigned, String.format("%.1f", successRate));
        log.info("Unassigned: {} periods ({}%)", (totalDemand - totalAssigned), String.format("%.1f", 100.0 - successRate));
        
        if (!unassignedPeriods.isEmpty()) {
            log.warn("Unassigned periods breakdown:");
            unassignedPeriods.forEach((grade, subjectMap) -> {
                subjectMap.forEach((subject, count) -> {
                    log.warn("  Grade {} - {}: {} periods unassigned", grade, subject, count);
                });
            });
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
