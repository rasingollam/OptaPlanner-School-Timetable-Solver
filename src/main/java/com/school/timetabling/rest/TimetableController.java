package com.school.timetabling.rest;

import com.school.timetabling.domain.TimeTable;
import com.school.timetabling.domain.Lesson;
import com.school.timetabling.domain.Timeslot;
import com.school.timetabling.domain.StudentGroup;
import com.school.timetabling.rest.dto.TimetableRequest;
import com.school.timetabling.rest.dto.TimetableResponse;
import com.school.timetabling.service.TimeTableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
// import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/timetable")
@CrossOrigin(origins = "*")
public class TimetableController {

    @Autowired
    private TimeTableService timeTableService;

    @PostMapping("/solve")
    public ResponseEntity<TimetableResponse> solveTimetable(@RequestBody TimetableRequest request) {
        try {
            TimeTable solution = timeTableService.solve(request);
            
            // Calculate unassigned periods from the solution (not from service)
            Map<String, Map<String, Integer>> unassignedPeriods = calculateUnassignedPeriods(solution, request);
            Map<String, Map<String, Map<String, Integer>>> detailedUnassignedPeriods = calculateDetailedUnassignedPeriods(solution);

            // Convert to response format
            TimetableResponse response = convertToResponse(solution);

            // Add unassigned periods information
            response.setUnassignedPeriods(unassignedPeriods);
            response.setDetailedUnassignedPeriods(detailedUnassignedPeriods);

            // Calculate teacher workload summary
            Map<String, Integer> teacherWorkload = calculateTeacherWorkload(solution);
            response.setTeacherWorkloadSummary(teacherWorkload);

            // Generate unassigned summary
            TimetableResponse.UnassignedSummary summary = generateUnassignedSummary(detailedUnassignedPeriods, request);
            response.setUnassignedSummary(summary);

            // Add informational message
            if (!unassignedPeriods.isEmpty()) {
                response.setMessage("Some periods could not be assigned due to teacher workload limits. " +
                        "Check unassignedSummary for detailed breakdown by grade and class.");
            } else {
                response.setMessage("All periods successfully assigned within teacher workload limits.");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private TimetableResponse.UnassignedSummary generateUnassignedSummary(
            Map<String, Map<String, Map<String, Integer>>> detailedUnassignedPeriods,
            TimetableRequest request) {
        
        TimetableResponse.UnassignedSummary summary = new TimetableResponse.UnassignedSummary();
        Map<String, TimetableResponse.GradeUnassignedInfo> gradeBreakdown = new HashMap<>();
        
        int totalUnassignedPeriods = 0;
        int totalUnassignedClasses = 0;
        
        for (Map.Entry<String, Map<String, Map<String, Integer>>> gradeEntry : detailedUnassignedPeriods.entrySet()) {
            String grade = gradeEntry.getKey();
            TimetableResponse.GradeUnassignedInfo gradeInfo = new TimetableResponse.GradeUnassignedInfo();
            Map<String, TimetableResponse.SubjectUnassignedInfo> subjects = new HashMap<>();
            
            int gradeUnassignedPeriods = 0;
            int gradeUnassignedClasses = 0;
            
            for (Map.Entry<String, Map<String, Integer>> subjectEntry : gradeEntry.getValue().entrySet()) {
                String subject = subjectEntry.getKey();
                TimetableResponse.SubjectUnassignedInfo subjectInfo = new TimetableResponse.SubjectUnassignedInfo();
                
                List<String> affectedClasses = new ArrayList<>(subjectEntry.getValue().keySet());
                int periodsPerClass = subjectEntry.getValue().values().iterator().next(); // All classes have same periods per week
                
                subjectInfo.setPeriodsPerWeek(periodsPerClass);
                subjectInfo.setClassesAffected(affectedClasses.size());
                subjectInfo.setAffectedClasses(affectedClasses);
                subjectInfo.setReason("No teachers available with sufficient capacity");
                
                subjects.put(subject, subjectInfo);
                
                gradeUnassignedPeriods += affectedClasses.size() * periodsPerClass;
                gradeUnassignedClasses += affectedClasses.size();
            }
            
            gradeInfo.setTotalPeriodsNeeded(gradeUnassignedPeriods);
            gradeInfo.setTotalClassesAffected(gradeUnassignedClasses);
            gradeInfo.setSubjects(subjects);
            
            gradeBreakdown.put(grade, gradeInfo);
            
            totalUnassignedPeriods += gradeUnassignedPeriods;
            totalUnassignedClasses += gradeUnassignedClasses;
        }
        
        summary.setTotalUnassignedPeriods(totalUnassignedPeriods);
        summary.setTotalUnassignedClasses(totalUnassignedClasses);
        summary.setGradeBreakdown(gradeBreakdown);
        
        return summary;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Timetabling Service is running");
    }

    private TimetableResponse convertToResponse(TimeTable solution) {
        TimetableResponse response = new TimetableResponse();

        // Group lessons by student group and day
        Map<String, TimetableResponse.StudentGroupSchedule> studentGroupSchedules = new HashMap<>();

        for (Lesson lesson : solution.getLessonList()) {
            if (lesson.getTimeslot() != null) {
                StudentGroup studentGroup = lesson.getStudentGroup();
                String studentGroupId = studentGroup.getGrade() + studentGroup.getClassName();
                Timeslot timeslot = lesson.getTimeslot();

                // Get or create student group schedule
                TimetableResponse.StudentGroupSchedule groupSchedule =
                        studentGroupSchedules.computeIfAbsent(studentGroupId,
                                k -> new TimetableResponse.StudentGroupSchedule());

                // Get or create week schedule (String -> String -> LessonInfo)
                Map<String, Map<String, TimetableResponse.LessonInfo>> weekSchedule =
                        groupSchedule.getWeekSchedule();
                if (weekSchedule == null) {
                    weekSchedule = new HashMap<>();
                    groupSchedule.setWeekSchedule(weekSchedule);
                }

                // Get or create day schedule using String representation of DayOfWeek
                String dayOfWeekStr = timeslot.getDayOfWeek().toString();
                Map<String, TimetableResponse.LessonInfo> daySchedule =
                        weekSchedule.computeIfAbsent(dayOfWeekStr, k -> new HashMap<>());

                // Create lesson info
                TimetableResponse.LessonInfo lessonInfo = new TimetableResponse.LessonInfo();
                lessonInfo.setSubject(lesson.getSubject());
                lessonInfo.setTeacher(lesson.getTeacher());
                lessonInfo.setStartTime(timeslot.getStartTime().toString());
                lessonInfo.setEndTime(timeslot.getEndTime().toString());

                // Add to day schedule using start time as key
                daySchedule.put(timeslot.getStartTime().toString(), lessonInfo);
            }
        }

        response.setStudentGroupSchedules(studentGroupSchedules);
        response.setScore(solution.getScore() != null ? solution.getScore().toString() : "N/A");
        response.setFeasible(solution.getScore() != null && solution.getScore().isFeasible());

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

    private Map<String, Map<String, Integer>> calculateUnassignedPeriods(TimeTable solution, TimetableRequest request) {
        // Use the unassigned periods calculated during lesson generation in the service
        return timeTableService.getUnassignedPeriods();
    }

    private Map<String, Map<String, Map<String, Integer>>> calculateDetailedUnassignedPeriods(TimeTable solution) {
        // Use the detailed unassigned periods calculated during lesson generation in the service
        return timeTableService.getDetailedUnassignedPeriods();
    }
}
