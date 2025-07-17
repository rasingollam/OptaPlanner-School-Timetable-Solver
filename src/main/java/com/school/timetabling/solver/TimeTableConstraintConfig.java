package com.school.timetabling.solver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Global configuration class to store constraint parameters from the request.
 * This allows the constraint provider to access dynamic values instead of hard-coded ones.
 * Uses global static variables instead of ThreadLocal to work with OptaPlanner's multi-threading.
 */
public class TimeTableConstraintConfig {
    
    // Use global static variables with synchronization instead of ThreadLocal
    private static volatile Map<String, Map<String, Integer>> maxPeriodsPerDayConfig = new HashMap<>();
    private static volatile Integer maxPeriodsPerTeacher = 20;
    
    // ReadWrite lock for thread safety
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    /**
     * Set the constraint configuration from the request
     * @param subjectGradeMaxPeriods Map of subject -> grade -> maxPeriodsPerDay from request
     * @param teacherMaxPeriods Maximum periods per teacher per week from request
     */
    public static void setConfiguration(Map<String, Map<String, Integer>> subjectGradeMaxPeriods, 
                                      int teacherMaxPeriods) {
        lock.writeLock().lock();
        try {
            // Create a defensive copy to ensure thread safety
            Map<String, Map<String, Integer>> configCopy = new HashMap<>();
            for (Map.Entry<String, Map<String, Integer>> entry : subjectGradeMaxPeriods.entrySet()) {
                configCopy.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
            
            maxPeriodsPerDayConfig = configCopy;
            maxPeriodsPerTeacher = teacherMaxPeriods;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get maximum periods per day for a subject and grade
     * @param subject Subject name
     * @param grade Grade name
     * @return Maximum periods per day from request, default 1 if not configured
     */
    public static int getMaxPeriodsPerDay(String subject, String grade) {
        lock.readLock().lock();
        try {
            Map<String, Map<String, Integer>> config = maxPeriodsPerDayConfig;
            
            if (config != null && !config.isEmpty()) {
                if (config.containsKey(subject)) {
                    Map<String, Integer> gradeMap = config.get(subject);
                    if (gradeMap.containsKey(grade)) {
                        return gradeMap.get(grade);
                    }
                }
            }
            
            return 1;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get maximum periods per teacher per week
     * @return Maximum periods per teacher from request
     */
    public static int getMaxPeriodsPerTeacher() {
        lock.readLock().lock();
        try {
            Integer maxPeriods = maxPeriodsPerTeacher;
            
            if (maxPeriods == null) {
                return 20;
            }
            
            return maxPeriods;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clear the configuration (useful for cleanup)
     */
    public static void clearConfiguration() {
        lock.writeLock().lock();
        try {
            maxPeriodsPerDayConfig = new HashMap<>();
            maxPeriodsPerTeacher = 20;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Check if configuration has been set
     * @return true if configuration is available
     */
    public static boolean isConfigured() {
        lock.readLock().lock();
        try {
            Map<String, Map<String, Integer>> config = maxPeriodsPerDayConfig;
            Integer teacherMax = maxPeriodsPerTeacher;
            return config != null && !config.isEmpty() && teacherMax != null;
        } finally {
            lock.readLock().unlock();
        }
    }
}
