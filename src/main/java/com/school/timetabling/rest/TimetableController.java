package com.school.timetabling.rest;

import com.school.timetabling.domain.Lesson;
import com.school.timetabling.domain.TimeTable;
import com.school.timetabling.rest.dto.TimetableRequest;
import com.school.timetabling.rest.dto.TimetableResponse;
import com.school.timetabling.service.TimeTableService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/timetable")
@CrossOrigin(origins = "*")
public class TimetableController {

    @Autowired
    private TimeTableService timeTableService;

    @PostMapping("/solve")
    public TimetableResponse solveTimetable(@RequestBody TimetableRequest request) {
        try {
            TimeTable solution = timeTableService.solve(request);
            return convertToResponse(solution);
        } catch (Exception e) {
            e.printStackTrace();
            TimetableResponse errorResponse = new TimetableResponse();
            errorResponse.setFeasible(false);
            errorResponse.setScore("Error: " + e.getMessage());
            errorResponse.setMessage("Failed to solve timetable: " + e.getMessage());
            return errorResponse;
        }
    }

    @GetMapping("/health")
    public String health() {
        return "Timetabling Service is running";
    }

    private TimetableResponse convertToResponse(TimeTable solution) {
        TimetableResponse response = new TimetableResponse();
        
        // Basic solution info
        response.setScore(solution.getScore() != null ? solution.getScore().toString() : "N/A");
        response.setFeasible(solution.getScore() != null && solution.getScore().isFeasible());
        
        // Convert student group schedules - simplified structure
        Map<String, Map<String, Map<String, Map<String, Object>>>> studentGroupSchedules = new HashMap<>();
        
        // Group lessons by student group
        Map<String, List<Lesson>> lessonsByGroup = solution.getLessonList().stream()
            .filter(lesson -> lesson.getTimeslot() != null)
            .collect(Collectors.groupingBy(lesson -> 
                lesson.getStudentGroup().getGrade() + lesson.getStudentGroup().getClassName()));
        
        lessonsByGroup.forEach((groupName, lessons) -> {
            Map<String, Map<String, Object>> weekSchedule = new HashMap<>();
            
            lessons.forEach(lesson -> {
                String day = lesson.getTimeslot().getDayOfWeek().toString();
                String time = lesson.getTimeslot().getStartTime().toString();
                
                Map<String, Object> lessonInfo = new HashMap<>();
                lessonInfo.put("subject", lesson.getSubject());
                lessonInfo.put("teacher", lesson.getTeacher());
                lessonInfo.put("startTime", lesson.getTimeslot().getStartTime().toString());
                lessonInfo.put("endTime", lesson.getTimeslot().getEndTime().toString());
                
                weekSchedule.computeIfAbsent(day, k -> new HashMap<>()).put(time, lessonInfo);
            });
            
            // Fixed: Create the correct structure for studentGroupSchedules
            Map<String, Map<String, Map<String, Object>>> classData = new HashMap<>();
            classData.put("weekSchedule", weekSchedule);
            studentGroupSchedules.put(groupName, classData);
        });
        
        response.setStudentGroupSchedules(studentGroupSchedules);
        
        // Add unassigned periods information
        response.setUnassignedPeriods(timeTableService.getUnassignedPeriods());
        response.setDetailedUnassignedPeriods(timeTableService.getDetailedUnassignedPeriods());
        
        // Calculate teacher workload
        response.setTeacherWorkloadSummary(calculateTeacherWorkload(solution));
        
        // Generate unassigned summary - using simple map instead of missing method
        response.setUnassignedSummary(generateSimpleUnassignedSummary());
        
        // Set appropriate message
        if (response.isFeasible()) {
            response.setMessage("Timetable generated successfully!");
        } else {
            response.setMessage("Timetable generated but may not satisfy all constraints. Check the score for details.");
        }
        
        return response;
    }

    private Map<String, Integer> calculateTeacherWorkload(TimeTable solution) {
        Map<String, Integer> workload = new HashMap<>();
        for (Lesson lesson : solution.getLessonList()) {
            if (lesson.getTimeslot() != null && lesson.getTeacher() != null) {
                workload.merge(lesson.getTeacher(), 1, Integer::sum);
            }
        }
        return workload;
    }

    private Map<String, Object> generateSimpleUnassignedSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        // Calculate total unassigned periods
        int totalUnassigned = timeTableService.getUnassignedPeriods().values().stream()
            .mapToInt(gradeMap -> gradeMap.values().stream().mapToInt(Integer::intValue).sum())
            .sum();
        
        summary.put("totalUnassignedPeriods", totalUnassigned);
        summary.put("affectedGrades", timeTableService.getUnassignedPeriods().keySet().size());
        
        return summary;
    }
}
