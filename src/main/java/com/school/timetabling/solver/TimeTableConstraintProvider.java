package com.school.timetabling.solver;

import com.school.timetabling.domain.Lesson;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

import static org.optaplanner.core.api.score.stream.ConstraintCollectors.count;

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
                    
                    if (violates) {
                        System.out.println("CONSTRAINT VIOLATION: " + studentGroup.getGrade() + studentGroup.getClassName() + 
                                         " has " + lessonCount + " " + subject + " periods on " + dayOfWeek + 
                                         " (max allowed: " + maxPeriodsPerDay + ")");
                    }
                    
                    return violates;
                })
                .penalize(HardSoftScore.ONE_HARD,
                        (studentGroup, subject, dayOfWeek, lessonCount) -> {
                            int maxPeriodsPerDay = TimeTableConstraintConfig.getMaxPeriodsPerDay(subject, studentGroup.getGrade());
                            int violation = lessonCount - maxPeriodsPerDay;
                            System.out.println("PENALTY: " + violation + " points for " + subject + " in " + 
                                             studentGroup.getGrade() + studentGroup.getClassName() + " on " + dayOfWeek);
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
                        System.out.println("Teacher workload violation: " + teacher + " has " + lessonCount + 
                                         " lessons (limit: " + maxPeriodsPerTeacher + ", excess: " + excess + ")");
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
                // Hard constraints
                teacherConflict(constraintFactory),
                studentGroupConflict(constraintFactory),
                maxPeriodsPerDayPerSubject(constraintFactory),
                teacherWorkloadLimit(constraintFactory),
                
                // Soft constraints
                preferAssignedTeachers(constraintFactory)
        };
    }
}
