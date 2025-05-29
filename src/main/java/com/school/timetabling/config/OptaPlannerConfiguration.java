package com.school.timetabling.config;

import com.school.timetabling.domain.TimeTable;
import com.school.timetabling.solver.TimeTableConstraintProvider;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.UUID;

@Configuration
public class OptaPlannerConfiguration {

    @Bean
    public SolverFactory<TimeTable> solverFactory() {
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(TimeTable.class)
                .withEntityClasses(com.school.timetabling.domain.Lesson.class)
                .withConstraintProviderClass(TimeTableConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(Duration.ofSeconds(30)));

        return SolverFactory.create(solverConfig);
    }

    @Bean
    public SolverManager<TimeTable, UUID> solverManager(SolverFactory<TimeTable> solverFactory) {
        return SolverManager.create(solverFactory);
    }
}
