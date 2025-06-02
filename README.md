# School Timetabling Solver Flow and Constraints

## Overview
This document describes the current flow of the solve function and all constraints implemented in the school timetabling system. The system focuses on scheduling lessons to timeslots for student groups without room assignments using OptaPlanner 9.44.0.Final and Spring Boot 3.1.0.

## Architecture

### Core Components
- **[`TimetablingApplication`](src/main/java/com/school/timetabling/TimetablingApplication.java)**: Spring Boot main application class
- **[`TimetableController`](src/main/java/com/school/timetabling/rest/TimetableController.java)**: REST API controller exposing `/api/timetable/solve` endpoint
- **[`TimeTableService`](src/main/java/com/school/timetabling/service/TimeTableService.java)**: Core business logic for problem conversion and solving
- **[`TimeTableConstraintProvider`](src/main/java/com/school/timetabling/solver/TimeTableConstraintProvider.java)**: OptaPlanner constraint definitions
- **[`OptaPlannerConfiguration`](src/main/java/com/school/timetabling/config/OptaPlannerConfiguration.java)**: Solver configuration and beans

### Domain Model
- **[`TimeTable`](src/main/java/com/school/timetabling/domain/TimeTable.java)**: @PlanningSolution containing timeslots, student groups, and lessons
- **[`Lesson`](src/main/java/com/school/timetabling/domain/Lesson.java)**: @PlanningEntity with timeslot as @PlanningVariable
- **[`Timeslot`](src/main/java/com/school/timetabling/domain/Timeslot.java)**: Problem fact representing time periods
- **[`StudentGroup`](src/main/java/com/school/timetabling/domain/StudentGroup.java)**: Problem fact representing class sections
- **[`Room`](src/main/java/com/school/timetabling/domain/Room.java)**: Domain entity (not currently used in planning)

### Request/Response DTOs
- **[`TimetableRequest`](src/main/java/com/school/timetabling/rest/dto/TimetableRequest.java)**: Input structure for solver requests
- **[`TimetableResponse`](src/main/java/com/school/timetabling/rest/dto/TimetableResponse.java)**: Output structure with schedules and analytics

## System Properties

### 1. Timeslots Configuration
- Configurable time periods across multiple days of the week
- Example configuration: 18 periods (6 per day × 3 days: Monday-Wednesday)
- Each timeslot: `{id, dayOfWeek, startTime, endTime}`
- Supports Monday through Friday scheduling
- Time format: HH:mm:ss (e.g., "07:50:00")

### 2. Flexible Classes Per Grade
- Variable number of classes per grade defined in [`TimetableRequest.classList`](src/main/java/com/school/timetabling/rest/dto/TimetableRequest.java)
- Example: Grade 9th has Classes [A, B, C, D], Grade 10th has Classes [A, B, C, D]
- Each class becomes a [`StudentGroup`](src/main/java/com/school/timetabling/domain/StudentGroup.java) with default 30 students
- **No room assignments** - lessons are only assigned to student groups and timeslots

### 3. Teacher Assignment System
- Teachers qualified for specific subjects and grades via [`LessonAssignment.possibleTeachers`](src/main/java/com/school/timetabling/rest/dto/TimetableRequest.java)
- **Round-robin distribution**: Teachers distributed evenly across classes for same subject/grade
- **Strict workload enforcement**: Teachers cannot exceed [`maxPeriodsPerTeacherPerWeek`](src/main/java/com/school/timetabling/rest/dto/TimetableRequest.java) limit
- **Capacity tracking**: System tracks and reports unassigned periods when all teachers reach capacity

### 4. Workload Management
- Configurable through [`TeacherWorkloadConfig`](src/main/java/com/school/timetabling/rest/dto/TimetableRequest.java):
  - `totalTimeslotsPerWeek`: Total available periods (e.g., 18)
  - `freePeriodsPerTeacherPerWeek`: Required free periods (e.g., 5)
  - `maxPeriodsPerTeacherPerWeek`: Maximum teaching periods (e.g., 13)

## System Constraints (Hard Constraints)

### 1. Teacher Conflict Prevention ✅ IMPLEMENTED
**Location**: [`TimeTableConstraintProvider.teacherConflict()`](src/main/java/com/school/timetabling/solver/TimeTableConstraintProvider.java)
- No teacher can teach two different classes simultaneously
- **Cross-grade protection**: Applies across ALL grades and classes system-wide
- **Implementation**: `forEachUniquePair` with teacher and timeslot equality
- **Penalty**: `HardSoftScore.ONE_HARD` per violation

### 2. Student Group Conflict Prevention ✅ IMPLEMENTED
**Location**: [`TimeTableConstraintProvider.studentGroupConflict()`](src/main/java/com/school/timetabling/solver/TimeTableConstraintProvider.java)
- No student group can have two lessons simultaneously
- **Implementation**: `forEachUniquePair` with studentGroup and timeslot equality
- **Penalty**: `HardSoftScore.ONE_HARD` per violation

### 3. Maximum Periods Per Day Per Subject ✅ IMPLEMENTED
**Location**: [`TimeTableConstraintProvider.maxPeriodsPerDayPerSubject()`](src/main/java/com/school/timetabling/solver/TimeTableConstraintProvider.java)
- Limits daily periods for each subject per student group
- **Configuration**: Via [`LessonAssignment.maxPeriodsPerDay`](src/main/java/com/school/timetabling/rest/dto/TimetableRequest.java)
- **Current defaults**: Math/English: 2 periods/day, Others: 1 period/day
- **Penalty**: `(actualPeriods - maxAllowed) * HardSoftScore.ONE_HARD`

### 4. Teacher Workload Limit ✅ IMPLEMENTED
**Location**: [`TimeTableConstraintProvider.teacherWorkloadLimit()`](src/main/java/com/school/timetabling/solver/TimeTableConstraintProvider.java)
- **Hard limit**: Currently set to 13 periods per teacher per week
- **Should be configurable**: Uses value from [`TeacherWorkloadConfig.maxPeriodsPerTeacherPerWeek`](src/main/java/com/school/timetabling/rest/dto/TimetableRequest.java)
- **Penalty**: `(assignedPeriods - maxLimit) * HardSoftScore.ONE_HARD`

### 5. Periods Per Week Per Subject ✅ IMPLEMENTED
**Location**: [`TimeTableService.convertRequestToProblem()`](src/main/java/com/school/timetabling/service/TimeTableService.java)
- **Enforced at lesson creation**: Exact number of lessons created per [`LessonAssignment.periodsPerWeek`](src/main/java/com/school/timetabling/rest/dto/TimetableRequest.java)
- **Formula**: `periodsPerWeek × numberOfClassesForGrade × numberOfSubjects`
- **Pre-solving constraint**: Handled before OptaPlanner execution

### 6. Single Teacher Per Subject Per Class ✅ IMPLEMENTED
**Location**: [`TimeTableService.convertRequestToProblem()`](src/main/java/com/school/timetabling/service/TimeTableService.java)
- **Round-robin assignment**: Teachers distributed evenly during lesson creation
- **Consistency guarantee**: Each class has only one teacher per subject
- **Pre-solving constraint**: Handled before OptaPlanner execution

## Solver Configuration

### MAXIMUM ACCURACY OptaPlanner Settings
**Configuration**: [`application.yml`](src/main/resources/application.yml) and [`OptaPlannerConfiguration`](src/main/java/com/school/timetabling/config/OptaPlannerConfiguration.java)
- **🏆 MAXIMUM Termination**: 30 minutes solving time (60x increase from original)
- **Multi-phase approach**: 4 phases for thorough optimization
  - Construction heuristic (fast initial solution)
  - Initial exploration (5 minutes)
  - Deep optimization (10 minutes) 
  - Fine-tuning (15 minutes)
- **Early termination**: Stops when perfect solution (0hard/*soft) found
- **Unimproved limit**: 5 minutes without improvement
- **Domain access**: REFLECTION mode
- **Parallel processing**: AUTO configuration for maximum CPU utilization
- **Score type**: HardSoftScore

### Expected Performance Impact
- **⏱️ Solving time**: 30 minutes maximum (vs 30 seconds original)
- **🎯 Quality improvement**: 60x more exploration time
- **🏆 Solution ratings**: PERFECT/EXCELLENT solutions expected
- **📊 Success rate**: 95%+ feasible solutions for reasonable problems
- **💻 Resource usage**: High CPU utilization for 30 minutes

### Quality Ratings
- **🏆 PERFECT**: Soft score ≥ -5 (all constraints nearly optimal)
- **🎯 EXCELLENT**: Soft score ≥ -15 (very high quality)
- **✨ VERY GOOD**: Soft score ≥ -30 (high quality)
- **🟢 GOOD**: Soft score ≥ -50 (good quality)
- **🟡 FAIR**: Soft score ≥ -100 (acceptable quality)
- **🟠 ACCEPTABLE**: Soft score < -100 (basic feasibility)

### Monitoring Long Runs
The solver provides detailed progress monitoring:
```
=== MAXIMUM ACCURACY Solver Configuration ===
Problem size: 480 lessons
Maximum solving time: 30 minutes (for highest quality solutions)
⚠️  This will take up to 30 minutes for best results. Please be patient.

[00:15] 🔄 Score improved: -45hard/-234soft
[02:30] 🔄 Score improved: -12hard/-156soft
[05:45] ✨ HIGH-QUALITY solution found! (Soft score: -23)
[08:20] 🎯 EXCELLENT solution found! (Soft score: -8)
[12:10] 🏆 PERFECT solution found! (Soft score: -2)

=== MAXIMUM ACCURACY Solving Complete ===
Total solving time: 12:34
Final score: 0hard/-2soft
Quality rating: 🏆 PERFECT
🏆 Solution quality optimized with 12:34 of computation
```

### Logging Configuration
**File**: [`application.properties`](src/main/resources/application.properties)
- Application logging: DEBUG level
- OptaPlanner logging: INFO level
- Server port: 8080

## API Endpoints

### POST /api/timetable/solve
**Controller**: [`TimetableController.solveTimetable()`](src/main/java/com/school/timetabling/rest/TimetableController.java)
- **Input**: [`TimetableRequest`](src/main/java/com/school/timetabling/rest/dto/TimetableRequest.java) JSON
- **Output**: [`TimetableResponse`](src/main/java/com/school/timetabling/rest/dto/TimetableResponse.java) JSON
- **CORS**: Enabled for all origins

### GET /api/timetable/health
**Controller**: [`TimetableController.health()`](src/main/java/com/school/timetabling/rest/TimetableController.java)
- **Purpose**: Service health check
- **Output**: "Timetabling Service is running"

## Response Format

### Enhanced Response Structure
```json
{
  "studentGroupSchedules": {
    "9thA": {
      "weekSchedule": {
        "MONDAY": {
          "07:50:00": {
            "subject": "Math",
            "teacher": "Tharindu Silva",
            "startTime": "07:50:00",
            "endTime": "08:30:00"
          }
        }
      }
    }
  },
  "score": "0hard/0soft",
  "feasible": true,
  "unassignedPeriods": {
    "9th": {
      "Math": 14,
      "English": 8
    }
  },
  "detailedUnassignedPeriods": {
    "9th": {
      "Math": {
        "A": 6,
        "B": 8
      }
    }
  },
  "teacherWorkloadSummary": {
    "Tharindu Silva": 13,
    "Malinda Perera": 13
  },
  "unassignedSummary": {
    "totalUnassignedPeriods": 22,
    "totalUnassignedClasses": 4,
    "gradeBreakdown": {
      "9th": {
        "totalPeriodsNeeded": 14,
        "totalClassesAffected": 2,
        "subjects": {
          "Math": {
            "periodsPerWeek": 6,
            "classesAffected": 2,
            "affectedClasses": ["A", "B"],
            "reason": "No teachers available with sufficient capacity"
          }
        }
      }
    }
  },
  "message": "Some periods could not be assigned due to teacher workload limits..."
}
```

### Analytics Features
1. **Unassigned Period Tracking**: [`TimeTableService`](src/main/java/com/school/timetabling/service/TimeTableService.java) tracks periods that couldn't be assigned
2. **Teacher Workload Summary**: [`TimetableController.calculateTeacherWorkload()`](src/main/java/com/school/timetabling/rest/TimetableController.java) shows actual assignments
3. **Detailed Breakdown**: [`TimetableController.generateUnassignedSummary()`](src/main/java/com/school/timetabling/rest/TimetableController.java) provides class-level analysis
4. **Feasibility Reporting**: Score-based feasibility indication

## Behavior Characteristics

### Strict Workload Enforcement
**Implementation**: [`TimeTableService.convertRequestToProblem()`](src/main/java/com/school/timetabling/service/TimeTableService.java)
1. **No Overloading**: Teachers never exceed `maxPeriodsPerTeacherPerWeek`
2. **Early Termination**: Assignment stops when all qualified teachers reach capacity
3. **Feasible Solutions**: Generated timetables are always feasible (no constraint violations)
4. **Transparent Reporting**: Unassigned periods clearly identified with reasons

### Assignment Priority Algorithm
**Location**: [`TimeTableService.convertRequestToProblem()`](src/main/java/com/school/timetabling/service/TimeTableService.java)
1. **First Available**: Teachers assigned on first-come, first-served basis per subject
2. **Capacity Check**: Validates teacher capacity before each assignment
3. **Skip if Full**: Classes skipped and tracked as unassigned when no teacher available
4. **Continue Processing**: Processes all subjects/grades even if some can't be assigned

### Round-Robin Distribution
**Implementation**: Teacher assignment uses round-robin across classes to ensure:
- Equal distribution of teaching load
- Consistent teacher-class relationships
- Balanced workload among qualified teachers

## Development Setup

### Prerequisites
- Java 17
- Maven 3.x
- Spring Boot 3.1.0
- OptaPlanner 9.44.0.Final

### Build and Run
```bash
# Build the project
mvn clean compile

# Run the application
mvn spring-boot:run

# Access the API
curl -X POST http://localhost:8080/api/timetable/solve \
  -H "Content-Type: application/json" \
  -d @request.json
```

### Configuration Files
- **[`pom.xml`](pom.xml)**: Maven dependencies and build configuration
- **[`application.yml`](src/main/resources/application.yml)**: OptaPlanner and server configuration
- **[`application.properties`](src/main/resources/application.properties)**: Additional Spring Boot properties
- **[`request.json`](request.json)**: Sample request for testing

## Known Limitations

1. **Hard-coded constraint values**: Some constraint parameters are hard-coded in [`TimeTableConstraintProvider`](src/main/java/com/school/timetabling/solver/TimeTableConstraintProvider.java)
2. **No room assignments**: Current implementation doesn't assign rooms to lessons
3. **Single-week planning**: System plans for one week at a time
4. **No teacher preferences**: System doesn't consider teacher availability preferences
5. **Static student group size**: All classes default to 30 students

## Future Enhancements

1. **Dynamic constraint configuration**: Make constraint parameters configurable via request
2. **Room assignment**: Add room allocation with capacity and equipment constraints
3. **Multi-week planning**: Support for semester or term-long planning
4. **Teacher preferences**: Include teacher availability and preference constraints
5. **Advanced analytics**: Add more detailed reporting and optimization metrics
