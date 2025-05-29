package com.school.timetabling.service;

import com.school.timetabling.domain.*;
import com.school.timetabling.rest.dto.TimetableRequest;
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
        TimeTable problem = convertRequestToProblem(request);
        
        UUID problemId = UUID.randomUUID();
        SolverJob<TimeTable, UUID> solverJob = solverManager.solve(problemId, problem);
        
        return solverJob.getFinalBestSolution();
    }

    public Map<String, Map<String, Integer>> getUnassignedPeriods() {
        return unassignedPeriods;
    }

    public Map<String, Map<String, Map<String, Integer>>> getDetailedUnassignedPeriods() {
        return detailedUnassignedPeriods;
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
            request.getTeacherWorkloadConfig().getMaxPeriodsPerTeacherPerWeek() : 13;

        // Generate lessons with strict teacher workload limits
        List<Lesson> lessons = new ArrayList<>();
        long lessonId = 0;
        Map<String, Integer> teacherWorkload = new HashMap<>();
        
        for (TimetableRequest.LessonAssignment assignment : request.getLessonAssignmentList()) {
            // Find matching classes for this grade
            List<String> classesForGrade = request.getClassList().stream()
                .filter(classInfo -> classInfo.getGrade().equals(assignment.getGrade()))
                .flatMap(classInfo -> classInfo.getClasses().stream())
                .collect(Collectors.toList());
            
            List<String> availableTeachers = assignment.getPossibleTeachers();
            int periodsPerClass = assignment.getPeriodsPerWeek();
            
            // Distribute classes among teachers with strict workload limits
            Map<String, List<String>> teacherClassAssignment = new HashMap<>();
            List<String> unassignedClasses = new ArrayList<>();
            
            for (String className : classesForGrade) {
                String assignedTeacher = null;
                
                // Try to find a teacher with available capacity
                for (String teacher : availableTeachers) {
                    int currentWorkload = teacherWorkload.getOrDefault(teacher, 0);
                    if (currentWorkload + periodsPerClass <= maxPeriodsPerTeacher) {
                        assignedTeacher = teacher;
                        teacherWorkload.merge(teacher, periodsPerClass, Integer::sum);
                        teacherClassAssignment.computeIfAbsent(teacher, k -> new ArrayList<>()).add(className);
                        break;
                    }
                }
                
                // If no teacher available, track unassigned periods
                if (assignedTeacher == null) {
                    unassignedClasses.add(className);
                    
                    // Track in simple format (existing)
                    unassignedPeriods.computeIfAbsent(assignment.getGrade(), k -> new HashMap<>())
                        .merge(assignment.getSubject(), periodsPerClass, Integer::sum);
                    
                    // Track in detailed format (new)
                    detailedUnassignedPeriods
                        .computeIfAbsent(assignment.getGrade(), k -> new HashMap<>())
                        .computeIfAbsent(assignment.getSubject(), k -> new HashMap<>())
                        .merge(className, periodsPerClass, Integer::sum);
                }
            }
            
            // Log assignment summary for this subject-grade combination
            int totalClassesForGrade = classesForGrade.size();
            int assignedClassesCount = totalClassesForGrade - unassignedClasses.size();
            int totalPeriodsRequired = totalClassesForGrade * periodsPerClass;
            int assignedPeriods = assignedClassesCount * periodsPerClass;
            int unassignedPeriodsCount = unassignedClasses.size() * periodsPerClass;
            
            System.out.println(String.format(
                "Grade %s - %s: %d/%d classes assigned, %d/%d periods assigned, %d periods unassigned for classes %s", 
                assignment.getGrade(), 
                assignment.getSubject(),
                assignedClassesCount,
                totalClassesForGrade,
                assignedPeriods,
                totalPeriodsRequired,
                unassignedPeriodsCount,
                unassignedClasses
            ));
            
            // Create lessons only for assigned teacher-class combinations
            for (Map.Entry<String, List<String>> entry : teacherClassAssignment.entrySet()) {
                String teacher = entry.getKey();
                List<String> assignedClasses = entry.getValue();
                
                for (String className : assignedClasses) {
                    // Find the corresponding student group
                    StudentGroup studentGroup = studentGroups.stream()
                        .filter(sg -> sg.getGrade().equals(assignment.getGrade()) && 
                                     sg.getClassName().equals(className))
                        .findFirst()
                        .orElse(null);
                    
                    if (studentGroup != null) {
                        // Create lessons based on periodsPerWeek
                        for (int i = 0; i < assignment.getPeriodsPerWeek(); i++) {
                            lessons.add(new Lesson(
                                lessonId++,
                                assignment.getSubject(),
                                teacher,
                                studentGroup
                            ));
                        }
                    }
                }
            }
        }

        return new TimeTable(timeslots, studentGroups, lessons);
    }
}
