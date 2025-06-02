package com.school.timetabling.domain;

import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.List;
import java.util.stream.Collectors;

@PlanningSolution
public class TimeTable {

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "timeslotRange")
    private List<Timeslot> timeslotList;

    @ProblemFactCollectionProperty
    private List<StudentGroup> studentGroupList;

    @PlanningEntityCollectionProperty
    private List<Lesson> lessonList;

    @PlanningScore
    private HardSoftScore score;

    public TimeTable() {}

    public TimeTable(List<Timeslot> timeslotList, List<StudentGroup> studentGroupList, List<Lesson> lessonList) {
        this.timeslotList = timeslotList;
        this.studentGroupList = studentGroupList;
        this.lessonList = lessonList;
    }

    // Getters and setters
    public List<Timeslot> getTimeslotList() { return timeslotList; }
    public void setTimeslotList(List<Timeslot> timeslotList) { this.timeslotList = timeslotList; }

    public List<StudentGroup> getStudentGroupList() { return studentGroupList; }
    public void setStudentGroupList(List<StudentGroup> studentGroupList) { this.studentGroupList = studentGroupList; }

    public List<Lesson> getLessonList() { return lessonList; }
    public void setLessonList(List<Lesson> lessonList) { this.lessonList = lessonList; }

    public HardSoftScore getScore() { return score; }
    public void setScore(HardSoftScore score) { this.score = score; }

    @ValueRangeProvider(id = "teacherRange")
    public List<String> getTeacherList() {
        // Collect all unique teachers from all lessons
        return lessonList.stream()
            .flatMap(lesson -> lesson.getPossibleTeachers().stream())
            .distinct()
            .collect(Collectors.toList());
    }
}
