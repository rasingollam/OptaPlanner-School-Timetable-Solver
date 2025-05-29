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
                    // Get maxPeriodsPerDay based on subject - this should be configurable
                    int maxPeriodsPerDay = getMaxPeriodsPerDay(subject);
                    return lessonCount > maxPeriodsPerDay;
                })
                .penalize(HardSoftScore.ONE_HARD,
                        (studentGroup, subject, dayOfWeek, lessonCount) -> {
                            int maxPeriodsPerDay = getMaxPeriodsPerDay(subject);
                            return lessonCount - maxPeriodsPerDay;
                        })
                .asConstraint("Max periods per day per subject");
    }

    private Constraint teacherWorkloadLimit(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(Lesson.class)
                .groupBy(Lesson::getTeacher, count())
                .filter((teacher, lessonCount) -> lessonCount > 13) // Use maxPeriodsPerTeacherPerWeek from config
                .penalize(HardSoftScore.ONE_HARD, 
                        (teacher, lessonCount) -> lessonCount - 13)
                .asConstraint("Teacher workload limit");
    }

    private int getMaxPeriodsPerDay(String subject) {
        // This should be made configurable based on the request
        switch (subject.toLowerCase()) {
            case "math": return 2;
            case "english": return 2;
            default: return 1;
        }
    }
}
