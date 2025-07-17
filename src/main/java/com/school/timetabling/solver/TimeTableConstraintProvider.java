package com.school.timetabling.solver;

import com.school.timetabling.domain.Lesson;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

import java.time.Duration;
import java.time.LocalTime;

import static org.optaplanner.core.api.score.stream.ConstraintCollectors.count;
import static org.optaplanner.core.api.score.stream.Joiners.equal;
import static org.optaplanner.core.api.score.stream.Joiners.lessThan;

public class TimeTableConstraintProvider implements ConstraintProvider {

    private Constraint teacherConflict(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getTeacher),
                        Joiners.equal(Lesson::getTimeslot))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher conflict");
    }

    private Constraint studentGroupConflict(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachUniquePair(Lesson.class,
                        Joiners.equal(Lesson::getStudentGroup),
                        Joiners.equal(Lesson::getTimeslot))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Student group conflict");
    }

    private Constraint maxPeriodsPerDayPerSubject(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(Lesson.class)
                .filter(lesson -> lesson.getTimeslot() != null) // Only consider assigned lessons
                .groupBy(
                    Lesson::getStudentGroup, 
                    Lesson::getSubject, 
                    lesson -> lesson.getTimeslot().getDayOfWeek(), 
                    count()
                )
                .filter((studentGroup, subject, dayOfWeek, lessonCount) -> {
                    // Get maxPeriodsPerDay from configuration
                    int maxPeriodsPerDay = TimeTableConstraintConfig.getMaxPeriodsPerDay(subject, studentGroup.getGrade());
                    boolean violates = lessonCount > maxPeriodsPerDay;
                    
                    return violates;
                })
                .penalize(HardSoftScore.ONE_HARD,
                        (studentGroup, subject, dayOfWeek, lessonCount) -> {
                            int maxPeriodsPerDay = TimeTableConstraintConfig.getMaxPeriodsPerDay(subject, studentGroup.getGrade());
                            int violation = lessonCount - maxPeriodsPerDay;
                            return violation;
                        })
                .asConstraint("Max periods per day per subject");
    }

    public Constraint teacherWorkloadLimit(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(Lesson.class)
                .filter(lesson -> lesson.getTeacher() != null) // Only count assigned lessons
                .groupBy(Lesson::getTeacher, count())
                .filter((teacher, lessonCount) -> {
                    int maxPeriodsPerTeacher = TimeTableConstraintConfig.getMaxPeriodsPerTeacher();
                    return lessonCount > maxPeriodsPerTeacher;
                })
                .penalize(HardSoftScore.ONE_HARD,
                    (teacher, lessonCount) -> {
                        int maxPeriodsPerTeacher = TimeTableConstraintConfig.getMaxPeriodsPerTeacher();
                        int excess = lessonCount - maxPeriodsPerTeacher;

                        return excess;
                    })
                .asConstraint("Teacher workload limit");
    }

    // Add a soft constraint to prefer assigning teachers (to avoid null assignments)
    public Constraint preferAssignedTeachers(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(Lesson.class)
                .filter(lesson -> lesson.getTeacher() == null)
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Prefer assigned teachers");
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
            // Hard constraints (must be satisfied)
            teacherConflict(constraintFactory),
            studentGroupConflict(constraintFactory),
            maxPeriodsPerDayPerSubject(constraintFactory),
            teacherWorkloadLimit(constraintFactory),
            
            // Soft constraints for optimization (simplified for compatibility)
            teacherWorkloadBalance(constraintFactory),
            minimizeGapsInDailySchedule(constraintFactory),
            preferMorningPeriodsForCoreSubjects(constraintFactory),
            distributeSubjectsEvenly(constraintFactory)
        };
    }

    // New soft constraints for better optimization
    private Constraint teacherWorkloadBalance(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Lesson.class)
            .filter(lesson -> lesson.getTimeslot() != null && lesson.getTeacher() != null)
            .groupBy(Lesson::getTeacher, count())
            .penalize(HardSoftScore.ONE_SOFT, 
                (teacher, lessonCount) -> {
                    // Penalize deviation from average workload
                    int maxWorkload = TimeTableConstraintConfig.getMaxPeriodsPerTeacher();
                    int averageWorkload = maxWorkload / 2;
                    return Math.abs(lessonCount - averageWorkload);
                })
            .asConstraint("Teacher workload balance");
    }
    
    private Constraint minimizeGapsInDailySchedule(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Lesson.class)
            .filter(lesson -> lesson.getTimeslot() != null)
            .join(Lesson.class,
                equal(Lesson::getStudentGroup),
                equal(lesson -> lesson.getTimeslot().getDayOfWeek()),
                lessThan(lesson -> lesson.getTimeslot().getStartTime()))
            .penalize(HardSoftScore.ONE_SOFT,
                (lesson1, lesson2) -> {
                    // Calculate time gap and penalize larger gaps
                    Duration gap = Duration.between(
                        lesson1.getTimeslot().getEndTime(),
                        lesson2.getTimeslot().getStartTime()
                    );
                    return (int) gap.toMinutes() / 10; // Penalize 10-minute gaps
                })
            .asConstraint("Minimize gaps in daily schedule");
    }
    
    private Constraint preferMorningPeriodsForCoreSubjects(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Lesson.class)
            .filter(lesson -> lesson.getTimeslot() != null)
            .filter(lesson -> isCoreSubject(lesson.getSubject()))
            .filter(lesson -> lesson.getTimeslot().getStartTime().isAfter(LocalTime.of(11, 0)))
            .penalize(HardSoftScore.ONE_SOFT)
            .asConstraint("Prefer morning periods for core subjects");
    }
    
    private Constraint distributeSubjectsEvenly(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Lesson.class)
            .filter(lesson -> lesson.getTimeslot() != null)
            .groupBy(Lesson::getStudentGroup, 
                    lesson -> lesson.getTimeslot().getDayOfWeek(),
                    Lesson::getSubject,
                    count())
            .filter((studentGroup, day, subject, count) -> count > 2)
            .penalize(HardSoftScore.ONE_SOFT, 
                (studentGroup, day, subject, count) -> count - 2)
            .asConstraint("Distribute subjects evenly across days");
    }
    
    private boolean isCoreSubject(String subject) {
        return subject.equals("Mathematics") || 
               subject.equals("English") || 
               subject.equals("Science") ||
               subject.equals("Sinhala");
    }
}
