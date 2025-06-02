package com.school.timetabling.config;

import com.school.timetabling.domain.TimeTable;
import com.school.timetabling.solver.TimeTableConstraintProvider;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.optaplanner.core.config.score.director.ScoreDirectorFactoryConfig;
import org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicType;
import org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig;
import org.optaplanner.core.config.localsearch.LocalSearchType;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@Configuration
public class OptaPlannerConfiguration {

    @Bean
    public SolverConfig solverConfig() {
        SolverConfig solverConfig = new SolverConfig();
        
        // Set solution and entity classes
        solverConfig.setSolutionClass(TimeTable.class);
        solverConfig.setEntityClassList(Arrays.asList(com.school.timetabling.domain.Lesson.class));
        
        // Enhanced scoring configuration
        ScoreDirectorFactoryConfig scoreDirectorFactoryConfig = new ScoreDirectorFactoryConfig();
        scoreDirectorFactoryConfig.setConstraintProviderClass(TimeTableConstraintProvider.class);
        scoreDirectorFactoryConfig.setInitializingScoreTrend("ONLY_DOWN");
        solverConfig.setScoreDirectorFactoryConfig(scoreDirectorFactoryConfig);
        
        // Use simpler construction heuristic that doesn't require difficulty comparator
        ConstructionHeuristicPhaseConfig constructionPhase = new ConstructionHeuristicPhaseConfig();
        constructionPhase.setConstructionHeuristicType(ConstructionHeuristicType.FIRST_FIT);
        
        // Local search phase
        LocalSearchPhaseConfig localSearchPhase = new LocalSearchPhaseConfig();
        localSearchPhase.setLocalSearchType(LocalSearchType.LATE_ACCEPTANCE);
        
        // Set termination for local search phase
        TerminationConfig localSearchTermination = new TerminationConfig();
        localSearchTermination.setSpentLimit(Duration.ofMinutes(2));
        localSearchPhase.setTerminationConfig(localSearchTermination);
        
        solverConfig.setPhaseConfigList(Arrays.asList(constructionPhase, localSearchPhase));
        
        // Enhanced termination with multiple criteria
        TerminationConfig terminationConfig = new TerminationConfig();
        terminationConfig.setSpentLimit(Duration.ofMinutes(3));  // 3 minutes total
        terminationConfig.setUnimprovedSpentLimit(Duration.ofSeconds(45));  // Stop if no improvement for 45s
        terminationConfig.setBestScoreLimit("0hard/*soft");  // Stop when feasible solution found
        solverConfig.setTerminationConfig(terminationConfig);
        
        // Environment configuration for performance
        solverConfig.setEnvironmentMode(EnvironmentMode.REPRODUCIBLE);  // For consistent results
        solverConfig.setMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_AUTO);
        
        return solverConfig;
    }

    @Bean
    public SolverManager<TimeTable, UUID> solverManager(SolverConfig solverConfig) {
        return SolverManager.create(solverConfig);
    }
}
