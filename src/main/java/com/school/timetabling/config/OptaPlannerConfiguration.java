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
        
        // MAXIMUM ACCURACY: Multi-phase configuration with extensive solving time
        ConstructionHeuristicPhaseConfig constructionPhase = new ConstructionHeuristicPhaseConfig();
        constructionPhase.setConstructionHeuristicType(ConstructionHeuristicType.FIRST_FIT);
        
        // Phase 1: Initial exploration (5 minutes)
        LocalSearchPhaseConfig initialSearch = new LocalSearchPhaseConfig();
        initialSearch.setLocalSearchType(LocalSearchType.LATE_ACCEPTANCE);
        TerminationConfig phase1Termination = new TerminationConfig();
        phase1Termination.setSpentLimit(Duration.ofMinutes(5));
        initialSearch.setTerminationConfig(phase1Termination);
        
        // Phase 2: Deep optimization (10 minutes)
        LocalSearchPhaseConfig deepSearch = new LocalSearchPhaseConfig();
        deepSearch.setLocalSearchType(LocalSearchType.LATE_ACCEPTANCE);
        TerminationConfig phase2Termination = new TerminationConfig();
        phase2Termination.setSpentLimit(Duration.ofMinutes(10));
        deepSearch.setTerminationConfig(phase2Termination);
        
        // Phase 3: Fine-tuning (15 minutes)
        LocalSearchPhaseConfig fineTuning = new LocalSearchPhaseConfig();
        fineTuning.setLocalSearchType(LocalSearchType.LATE_ACCEPTANCE);
        TerminationConfig phase3Termination = new TerminationConfig();
        phase3Termination.setSpentLimit(Duration.ofMinutes(15));
        fineTuning.setTerminationConfig(phase3Termination);
        
        solverConfig.setPhaseConfigList(Arrays.asList(constructionPhase, initialSearch, deepSearch, fineTuning));
        
        // MAXIMUM SOLVING TIME: 30 minutes total for highest accuracy
        TerminationConfig terminationConfig = new TerminationConfig();
        terminationConfig.setSpentLimit(Duration.ofMinutes(30)); // 30 minutes total (60x original)
        terminationConfig.setUnimprovedSpentLimit(Duration.ofMinutes(5)); // Stop if no improvement for 5 minutes
        terminationConfig.setBestScoreLimit("0hard/*soft"); // Stop when feasible solution found
        solverConfig.setTerminationConfig(terminationConfig);
        
        // Performance optimization for long runs
        solverConfig.setEnvironmentMode(EnvironmentMode.REPRODUCIBLE);
        solverConfig.setMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_AUTO);
        
        return solverConfig;
    }

    @Bean
    public SolverManager<TimeTable, UUID> solverManager(SolverConfig solverConfig) {
        return SolverManager.create(solverConfig);
    }
}
