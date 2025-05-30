package com.school.timetabling.service;

import com.school.timetabling.domain.*;
import com.school.timetabling.rest.dto.TimetableRequest;
import com.school.timetabling.solver.TimeTableConstraintConfig;
import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        
        // Test configuration one more time before solving
        System.out.println("=== Pre-Solve Configuration Test ===");
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            String subject = assignment.getSubject();
            String grade = assignment.getGrade();
            int actualMax = TimeTableConstraintConfig.getMaxPeriodsPerDay(subject, grade);
            System.out.println("Pre-solve: " + subject + " - Grade " + grade + " = " + actualMax + " periods/day");
        }
        
        SolverJob<TimeTable, UUID> solverJob = solverManager.solve(problemId, problem);
        
        TimeTable solution = solverJob.getFinalBestSolution();
        
        // DON'T clean up constraint configuration yet - let's see if it's still there
        System.out.println("=== Post-Solve Configuration Test ===");
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            String subject = assignment.getSubject();
            String grade = assignment.getGrade();
            int actualMax = TimeTableConstraintConfig.getMaxPeriodsPerDay(subject, grade);
            System.out.println("Post-solve: " + subject + " - Grade " + grade + " = " + actualMax + " periods/day");
        }
        
        // Clean up constraint configuration
        TimeTableConstraintConfig.clearConfiguration();
        
        return solution;
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

        // Get workload configuration
        int maxPeriodsPerTeacher = request.getTeacherWorkloadConfig() != null ? 
            request.getTeacherWorkloadConfig().getMaxPeriodsPerTeacherPerWeek() : 30;

        // Phase 1: Assign teachers to classes using balanced distribution
        Map<String, Map<String, String>> classTeacherAssignments = assignTeachersToClasses(request, maxPeriodsPerTeacher);
        
        // Phase 2: Generate lessons based on teacher assignments
        List<Lesson> lessons = generateLessonsFromAssignments(request, studentGroups, classTeacherAssignments);

        return new TimeTable(timeslots, studentGroups, lessons);
    }

    private Map<String, Map<String, String>> assignTeachersToClasses(TimetableRequest request, int maxPeriodsPerTeacher) {
        // Structure: subject -> className -> assignedTeacher
        Map<String, Map<String, String>> assignments = new HashMap<>();
        
        // Track teacher workloads globally across all subjects
        Map<String, Integer> globalTeacherWorkload = new HashMap<>();
        
        // Initialize all possible teachers with 0 workload
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            for (String teacher : assignment.getPossibleTeachers()) {
                globalTeacherWorkload.putIfAbsent(teacher, 0);
            }
        }
        
        // Log the workload limit being used
        System.out.println("Using maxPeriodsPerTeacher from request: " + maxPeriodsPerTeacher);
        
        // Process each subject-grade combination
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            String subject = assignment.getSubject();
            String grade = assignment.getGrade();
            int periodsPerClass = assignment.getPeriodsPerWeek();
            
            // Log the constraint being used for this subject
            System.out.println("Using maxPeriodsPerDay from request for " + subject + " - Grade " + grade + ": " + assignment.getMaxPeriodsPerDay());
            
            // Find classes for this grade
            List<String> classesForGrade = request.getClassList().stream()
                .filter(classInfo -> classInfo.getGrade().equals(grade))
                .flatMap(classInfo -> classInfo.getClasses().stream())
                .sorted() // Ensure consistent ordering
                .collect(Collectors.toList());
            
            // Get available teachers sorted by current workload (ascending)
            List<String> availableTeachers = assignment.getPossibleTeachers().stream()
                .sorted((t1, t2) -> globalTeacherWorkload.get(t1).compareTo(globalTeacherWorkload.get(t2)))
                .collect(Collectors.toList());
            
            Map<String, String> subjectAssignments = new HashMap<>();
            List<String> unassignedClasses = new ArrayList<>();
            
            // Round-robin assignment with workload balancing
            int teacherIndex = 0;
            for (String className : classesForGrade) {
                String fullClassName = grade + className;
                String assignedTeacher = null;
                
                // Try to find a teacher with available capacity, starting from least loaded
                int attempts = 0;
                while (attempts < availableTeachers.size()) {
                    String candidateTeacher = availableTeachers.get(teacherIndex % availableTeachers.size());
                    int currentWorkload = globalTeacherWorkload.get(candidateTeacher);
                    
                    if (currentWorkload + periodsPerClass <= maxPeriodsPerTeacher) {
                        assignedTeacher = candidateTeacher;
                        globalTeacherWorkload.put(candidateTeacher, currentWorkload + periodsPerClass);
                        subjectAssignments.put(fullClassName, assignedTeacher);
                        
                        // Move to next teacher for next class (round-robin)
                        teacherIndex = (teacherIndex + 1) % availableTeachers.size();
                        break;
                    }
                    
                    // Try next teacher
                    teacherIndex = (teacherIndex + 1) % availableTeachers.size();
                    attempts++;
                }
                
                // If no teacher found, track as unassigned
                if (assignedTeacher == null) {
                    unassignedClasses.add(className);
                    
                    // Track unassigned periods
                    unassignedPeriods.computeIfAbsent(grade, k -> new HashMap<>())
                        .merge(subject, periodsPerClass, Integer::sum);
                    
                    detailedUnassignedPeriods
                        .computeIfAbsent(grade, k -> new HashMap<>())
                        .computeIfAbsent(subject, k -> new HashMap<>())
                        .merge(className, periodsPerClass, Integer::sum);
                }
            }
            
            assignments.put(subject + "-" + grade, subjectAssignments);
            
            // Log assignment results
            int totalClasses = classesForGrade.size();
            int assignedClasses = totalClasses - unassignedClasses.size();
            int totalPeriods = totalClasses * periodsPerClass;
            int assignedPeriods = assignedClasses * periodsPerClass;
            int unassignedPeriodsCount = unassignedClasses.size() * periodsPerClass;
            
            System.out.println(String.format(
                "Grade %s - %s: %d/%d classes assigned, %d/%d periods assigned, %d periods unassigned for classes %s", 
                grade, subject, assignedClasses, totalClasses, assignedPeriods, totalPeriods, 
                unassignedPeriodsCount, unassignedClasses
            ));
        }
        
        // Log final teacher workload distribution
        System.out.println("\nFinal teacher workload distribution (limit: " + maxPeriodsPerTeacher + "):");
        globalTeacherWorkload.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> System.out.println(String.format("  %s: %d periods", entry.getKey(), entry.getValue())));
        
        return assignments;
    }

    private List<Lesson> generateLessonsFromAssignments(TimetableRequest request, 
                                                       List<StudentGroup> studentGroups, 
                                                       Map<String, Map<String, String>> assignments) {
        List<Lesson> lessons = new ArrayList<>();
        long lessonId = 0;
        
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            String subject = assignment.getSubject();
            String grade = assignment.getGrade();
            String assignmentKey = subject + "-" + grade;
            
            Map<String, String> classTeachers = assignments.get(assignmentKey);
            if (classTeachers == null) continue;
            
            // Create lessons for each assigned class
            for (Map.Entry<String, String> entry : classTeachers.entrySet()) {
                String fullClassName = entry.getKey();
                String teacher = entry.getValue();
                
                // Find the corresponding student group
                // The StudentGroup constructor takes: (name, grade, className, size)
                // So we need to match by the full name which is grade + className
                StudentGroup studentGroup = studentGroups.stream()
                    .filter(sg -> {
                        // Match by constructing the expected full name from grade and className
                        String expectedName = sg.getGrade() + sg.getClassName();
                        return expectedName.equals(fullClassName);
                    })
                    .findFirst()
                    .orElse(null);
                
                if (studentGroup != null) {
                    // Create the specified number of lessons for this class
                    for (int i = 0; i < assignment.getPeriodsPerWeek(); i++) {
                        lessons.add(new Lesson(
                            lessonId++,
                            subject,
                            teacher,
                            studentGroup
                        ));
                    }
                } else {
                    // Log warning if student group not found
                    System.err.println("Warning: Could not find student group for " + fullClassName);
                }
            }
        }
        
        System.out.println(String.format("\nGenerated %d lessons total", lessons.size()));
        return lessons;
    }
}
