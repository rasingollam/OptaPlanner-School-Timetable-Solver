package com.school.timetabling.solver;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe configuration class to store constraint parameters from the request.
 * This allows the constraint provider to access dynamic values instead of hard-coded ones.
 */
public class TimeTableConstraintConfig {
    
    private static final ThreadLocal<Map<String, Map<String, Integer>>> maxPeriodsPerDayConfig = 
            new ThreadLocal<Map<String, Map<String, Integer>>>() {
                @Override
                protected Map<String, Map<String, Integer>> initialValue() {
                    return new HashMap<>();
                }
            };
    
    private static final ThreadLocal<Integer> maxPeriodsPerTeacher = 
            new ThreadLocal<Integer>() {
                @Override
                protected Integer initialValue() {
                    return 13; // Default value
                }
            };
    
    /**
     * Set the constraint configuration from the request
     * @param subjectGradeMaxPeriods Map of subject -> grade -> maxPeriodsPerDay
     * @param teacherMaxPeriods Maximum periods per teacher per week
     */
    public static void setConfiguration(Map<String, Map<String, Integer>> subjectGradeMaxPeriods, 
                                      int teacherMaxPeriods) {
        maxPeriodsPerDayConfig.set(subjectGradeMaxPeriods);
        maxPeriodsPerTeacher.set(teacherMaxPeriods);
    }
    
    /**
     * Get maximum periods per day for a subject and grade
     * @param subject Subject name
     * @param grade Grade name
     * @return Maximum periods per day, default 1 if not configured
     */
    public static int getMaxPeriodsPerDay(String subject, String grade) {
        Map<String, Map<String, Integer>> config = maxPeriodsPerDayConfig.get();
        return config.getOrDefault(subject, new HashMap<>()).getOrDefault(grade, 1);
    }
    
    /**
     * Get maximum periods per teacher per week
     * @return Maximum periods per teacher
     */
    public static int getMaxPeriodsPerTeacher() {
        return maxPeriodsPerTeacher.get();
    }
    
    /**
     * Clear the configuration (useful for cleanup)
     */
    public static void clearConfiguration() {
        maxPeriodsPerDayConfig.remove();
        maxPeriodsPerTeacher.remove();
    }
}
