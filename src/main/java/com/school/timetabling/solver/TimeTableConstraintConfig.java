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
            
            // Debug logging
            System.out.println("=== Constraint Configuration Set (GLOBAL STATIC) ===");
            System.out.println("Max periods per teacher: " + teacherMaxPeriods);
            System.out.println("Max periods per day config:");
            configCopy.forEach((subject, gradeMap) -> {
                gradeMap.forEach((grade, maxPerDay) -> {
                    System.out.println("  " + subject + " - Grade " + grade + ": " + maxPerDay + " periods/day");
                });
            });
            System.out.println("Config object hash: " + maxPeriodsPerDayConfig.hashCode());
            System.out.println("Thread: " + Thread.currentThread().getName());
            System.out.println("=============================================");
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
            
            // Enhanced debugging
            System.out.println("=== getMaxPeriodsPerDay Debug (GLOBAL) ===");
            System.out.println("Requesting: " + subject + " - Grade " + grade);
            System.out.println("Thread: " + Thread.currentThread().getName());
            System.out.println("Config null? " + (config == null));
            System.out.println("Config empty? " + (config != null ? config.isEmpty() : "N/A"));
            System.out.println("Config hash: " + (config != null ? config.hashCode() : "N/A"));
            
            if (config != null && !config.isEmpty()) {
                System.out.println("Available subjects in config: " + config.keySet());
                if (config.containsKey(subject)) {
                    System.out.println("Subject '" + subject + "' found. Available grades: " + config.get(subject).keySet());
                    Map<String, Integer> gradeMap = config.get(subject);
                    if (gradeMap.containsKey(grade)) {
                        int maxPeriods = gradeMap.get(grade);
                        System.out.println("SUCCESS: Found " + subject + " - Grade " + grade + ": " + maxPeriods + " periods/day");
                        return maxPeriods;
                    } else {
                        System.out.println("Grade '" + grade + "' not found for subject '" + subject + "'");
                    }
                } else {
                    System.out.println("Subject '" + subject + "' not found in configuration");
                }
            }
            
            System.out.println("WARNING: No maxPeriodsPerDay configuration found, using default 1 for " + subject + " - Grade " + grade);
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
                System.out.println("WARNING: No maxPeriodsPerTeacher configuration found, using default 20");
                return 20;
            }
            
            System.out.println("Looking up maxPeriodsPerTeacher: " + maxPeriods + " (from request)");
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
            System.out.println("Constraint configuration cleared (GLOBAL STATIC)");
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
            boolean configured = config != null && !config.isEmpty() && teacherMax != null;
            System.out.println("Configuration check (GLOBAL): " + configured + 
                             " (config: " + (config != null ? config.size() : "null") + 
                             " subjects, teacherMax: " + teacherMax + ")");
            return configured;
        } finally {
            lock.readLock().unlock();
        }
    }
}
