package com.school.timetabling.solver;

import com.school.timetabling.domain.Lesson;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

import static org.optaplanner.core.api.score.stream.ConstraintCollectors.count;

public class TimeTableConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                // Hard constraints
                teacherConflict(constraintFactory),
                studentGroupConflict(constraintFactory),
                maxPeriodsPerDayPerSubject(constraintFactory),
                teacherWorkloadLimit(constraintFactory)
        };
    }

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
                .groupBy(Lesson::getStudentGroup, Lesson::getSubject, 
                        lesson -> lesson.getTimeslot().getDayOfWeek(), count())
                .filter((studentGroup, subject, dayOfWeek, lessonCount) -> {
                    // Get maxPeriodsPerDay from the lesson's maxPeriodsPerDay field
                    int maxPeriodsPerDay = getMaxPeriodsPerDay(subject, studentGroup.getGrade());
                    return lessonCount > maxPeriodsPerDay;
                })
                .penalize(HardSoftScore.ONE_HARD,
                        (studentGroup, subject, dayOfWeek, lessonCount) -> {
                            int maxPeriodsPerDay = getMaxPeriodsPerDay(subject, studentGroup.getGrade());
                            return lessonCount - maxPeriodsPerDay;
                        })
                .asConstraint("Max periods per day per subject");
    }

    private Constraint teacherWorkloadLimit(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(Lesson.class)
                .groupBy(Lesson::getTeacher, count())
                .filter((teacher, lessonCount) -> {
                    // Get dynamic workload limit from the TimeTable's configuration
                    int maxPeriodsPerTeacher = getMaxPeriodsPerTeacher();
                    return lessonCount > maxPeriodsPerTeacher;
                })
                .penalize(HardSoftScore.ONE_HARD, 
                        (teacher, lessonCount) -> {
                            int maxPeriodsPerTeacher = getMaxPeriodsPerTeacher();
                            return lessonCount - maxPeriodsPerTeacher;
                        })
                .asConstraint("Teacher workload limit");
    }

    private int getMaxPeriodsPerDay(String subject, String grade) {
        // Get the configuration from the TimeTable's constraint configuration
        // This should be injected from the request configuration
        return TimeTableConstraintConfig.getMaxPeriodsPerDay(subject, grade);
    }
    
    private int getMaxPeriodsPerTeacher() {
        // Get the configuration from the TimeTable's constraint configuration
        return TimeTableConstraintConfig.getMaxPeriodsPerTeacher();
    }
}
