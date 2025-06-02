const API_BASE_URL = 'http://localhost:8080/api/timetable';
let currentTimetableData = null;
let subjectColorMap = new Map();
let availableDays = [];
let availableTimeSlots = [];
let currentRequestData = null; // Add this to store the original request

async function loadRequestJson() {
    // Try multiple possible paths for request.json
    const possiblePaths = [
        './request.json',           // Same directory as HTML
        '../request.json',          // Parent directory (original location)
        '/request.json',            // Root of web server
        'request.json'              // Relative to current path
    ];
    
    let loadedSuccessfully = false;
    
    for (const path of possiblePaths) {
        try {
            const response = await fetch(path);
            if (response.ok) {
                const data = await response.json();
                document.getElementById('jsonInput').value = JSON.stringify(data, null, 2);
                extractTimeslotInfo(data.timeslotList);
                showApiStatus(`Request.json loaded successfully from ${path}`, 'success');
                loadedSuccessfully = true;
                break;
            }
        } catch (error) {
            // Continue to next path
            console.log(`Failed to load from ${path}:`, error.message);
        }
    }
    
    if (!loadedSuccessfully) {
        console.warn('Could not load request.json from any path');
        showApiStatus('Could not load request.json file. Please create one or serve files through a web server (not file://).', 'warning');
        loadEmptyConfiguration();
    }
}

function loadEmptyConfiguration() {
    const emptyConfig = {
        "timeslotList": [],
        "classList": [],
        "teacherWorkloadConfig": {
            "totalTimeslotsPerWeek": 40,
            "freePeriodsPerTeacherPerWeek": 5,
            "maxPeriodsPerTeacherPerWeek": 20
        },
        "subjectList": [],
        "lessonAssignmentList": []
    };
    
    document.getElementById('jsonInput').value = JSON.stringify(emptyConfig, null, 2);
    clearValidation();
}

function extractTimeslotInfo(timeslotList) {
    if (!timeslotList || !Array.isArray(timeslotList)) {
        return;
    }
    
    const days = new Set();
    const times = new Set();
    
    timeslotList.forEach(slot => {
        days.add(slot.dayOfWeek);
        times.add(slot.startTime);
    });
    
    availableDays = Array.from(days).sort();
    availableTimeSlots = Array.from(times).sort();
}

function assignSubjectColors(subjects) {
    subjectColorMap.clear();
    
    // Assign color index based on subject order
    subjects.forEach((subject, index) => {
        subjectColorMap.set(subject, index);
    });
}

function getSubjectColorClass(subject) {
    const colorIndex = subjectColorMap.get(subject);
    return colorIndex !== undefined ? `subject-color-${colorIndex}` : 'subject-color-0';
}

function createSubjectLegend(subjects) {
    if (!subjects || subjects.length === 0) return '';
    
    let legendHtml = '<div class="subject-legend"><strong>Subject Colors:</strong>';
    
    subjects.forEach(subject => {
        const colorClass = getSubjectColorClass(subject);
        legendHtml += `
            <div class="legend-item">
                <div class="legend-color ${colorClass}"></div>
                <span>${subject}</span>
            </div>
        `;
    });
    
    legendHtml += '</div>';
    return legendHtml;
}

function validateJSON(jsonString) {
    try {
        const parsed = JSON.parse(jsonString);
        return { valid: true, data: parsed };
    } catch (error) {
        return { valid: false, error: error.message };
    }
}

function clearValidation() {
    const input = document.getElementById('jsonInput');
    const errorDiv = document.getElementById('jsonError');
    input.classList.remove('is-invalid');
    errorDiv.textContent = '';
}

function showValidationError(message) {
    const input = document.getElementById('jsonInput');
    const errorDiv = document.getElementById('jsonError');
    input.classList.add('is-invalid');
    errorDiv.textContent = message;
}

function showApiStatus(message, type = 'info') {
    const statusDiv = document.getElementById('apiStatus');
    const icon = type === 'danger' ? 'exclamation-triangle' : 
                 type === 'success' ? 'check-circle' : 
                 type === 'warning' ? 'exclamation-triangle' : 'info-circle';
    
    statusDiv.innerHTML = `
        <div class="alert alert-${type} alert-custom" role="alert">
            <i class="fas fa-${icon} me-2"></i>
            ${message}
        </div>
    `;
    
    if (type === 'success' || type === 'danger') {
        setTimeout(() => {
            statusDiv.innerHTML = '';
        }, 8000);
    }
}

async function processTimetable() {
    const jsonInput = document.getElementById('jsonInput');
    const processBtn = document.getElementById('processBtn');
    const loadingIndicator = document.getElementById('loadingIndicator');
    
    clearValidation();
    
    const jsonString = jsonInput.value.trim();
    if (!jsonString) {
        showValidationError('Please enter JSON configuration');
        return;
    }
    
    const validation = validateJSON(jsonString);
    if (!validation.valid) {
        showValidationError(`Invalid JSON: ${validation.error}`);
        return;
    }
    
    // Store the request data for later use
    currentRequestData = validation.data;
    
    // Extract timeslot info from current input
    extractTimeslotInfo(validation.data.timeslotList);
    
    // Show loading state
    processBtn.disabled = true;
    loadingIndicator.classList.remove('d-none');
    showApiStatus('Processing timetable request...', 'info');
    
    try {
        const response = await fetch(`${API_BASE_URL}/solve`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: jsonString
        });
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const result = await response.json();
        currentTimetableData = result;
        
        displayResults(result);
        showApiStatus('Timetable generated successfully!', 'success');
        
    } catch (error) {
        console.error('Error processing timetable:', error);
        showApiStatus(`Error: ${error.message}`, 'danger');
    } finally {
        processBtn.disabled = false;
        loadingIndicator.classList.add('d-none');
    }
}

function displayResults(data) {
    // Hide welcome message and show results
    document.getElementById('welcomeMessage').style.display = 'none';
    document.getElementById('resultsPanel').classList.remove('d-none');
    
    // Extract unique subjects from the response
    const subjects = extractSubjectsFromResponse(data);
    assignSubjectColors(subjects);
    
    // Display score and feasibility
    displayScore(data);
    
    // Display feasibility analysis if available
    displayFeasibilityAnalysis(data.feasibilityAnalysis);
    
    // Populate class selector
    populateClassSelector(data.studentGroupSchedules);
    
    // Display teacher workload
    displayTeacherWorkload(data.teacherWorkloadSummary);
    
    // Display unassigned summary (enhanced)
    displayUnassignedSummary(data.unassignedSummary);
    
    // Display detailed unassigned periods information
    displayDetailedUnassignedPeriods(data);
}

function displayScore(data) {
    const scoreDisplay = document.getElementById('scoreDisplay');
    const feasibilityBadge = document.getElementById('feasibilityBadge');
    
    // Display score
    scoreDisplay.textContent = data.score || 'N/A';
    
    // Display feasibility
    if (data.feasible) {
        feasibilityBadge.innerHTML = '<span class="badge bg-success">Feasible</span>';
        scoreDisplay.className = 'h3 score-good';
    } else {
        feasibilityBadge.innerHTML = '<span class="badge bg-danger">Not Feasible</span>';
        scoreDisplay.className = 'h3 score-warning';
    }
}

function extractSubjectsFromResponse(data) {
    const subjects = new Set();
    
    if (data.studentGroupSchedules) {
        Object.values(data.studentGroupSchedules).forEach(classData => {
            if (classData.weekSchedule) {
                Object.values(classData.weekSchedule).forEach(daySchedule => {
                    Object.values(daySchedule).forEach(lesson => {
                        if (lesson.subject) {
                            subjects.add(lesson.subject);
                        }
                    });
                });
            }
        });
    }
    
    return Array.from(subjects).sort();
}

function populateClassSelector(studentGroupSchedules) {
    const classSelect = document.getElementById('classSelect');
    classSelect.innerHTML = '<option value="">Choose a class...</option>';
    
    if (studentGroupSchedules) {
        Object.keys(studentGroupSchedules).sort().forEach(className => {
            const option = document.createElement('option');
            option.value = className;
            option.textContent = className;
            classSelect.appendChild(option);
        });
        
        // Auto-select first class if available
        const firstClass = Object.keys(studentGroupSchedules)[0];
        if (firstClass) {
            classSelect.value = firstClass;
            displayTimetable();
        }
    }
}

function displayTimetable() {
    const classSelect = document.getElementById('classSelect');
    const selectedClass = classSelect.value;
    const timetableDisplay = document.getElementById('timetableDisplay');
    
    if (!selectedClass || !currentTimetableData || !currentTimetableData.studentGroupSchedules) {
        timetableDisplay.innerHTML = '<p class="text-muted">Please select a class to view the timetable.</p>';
        return;
    }
    
    const classData = currentTimetableData.studentGroupSchedules[selectedClass];
    if (!classData || !classData.weekSchedule) {
        timetableDisplay.innerHTML = '<p class="text-muted">No schedule data available for this class.</p>';
        return;
    }
    
    // Extract subjects for this class
    const classSubjects = extractSubjectsFromClassData(classData);
    
    // Generate timetable HTML
    let html = `
        ${createSubjectLegend(classSubjects)}
        <div class="table-responsive">
            <table class="table table-bordered timetable-table">
                <thead class="table-dark">
                    <tr>
                        <th>Time</th>
                        ${generateDayHeaders(classData.weekSchedule)}
                    </tr>
                </thead>
                <tbody>
                    ${generateTimetableRows(classData.weekSchedule)}
                </tbody>
            </table>
        </div>
    `;
    
    timetableDisplay.innerHTML = html;
}

function extractSubjectsFromClassData(classData) {
    const subjects = new Set();
    
    if (classData.weekSchedule) {
        Object.values(classData.weekSchedule).forEach(daySchedule => {
            Object.values(daySchedule).forEach(lesson => {
                if (lesson.subject) {
                    subjects.add(lesson.subject);
                }
            });
        });
    }
    
    return Array.from(subjects).sort();
}

function generateDayHeaders(weekSchedule) {
    const days = Object.keys(weekSchedule).sort();
    return days.map(day => `<th>${day}</th>`).join('');
}

function generateTimetableRows(weekSchedule) {
    // Get all unique time slots across all days
    const timeSlots = new Set();
    Object.values(weekSchedule).forEach(daySchedule => {
        Object.keys(daySchedule).forEach(time => {
            timeSlots.add(normalizeTime(time));
        });
    });
    
    // Sort time slots
    const sortedTimeSlots = Array.from(timeSlots).sort();
    const days = Object.keys(weekSchedule).sort();
    
    return sortedTimeSlots.map(timeSlot => {
        let row = `<tr><td class="time-slot"><strong>${timeSlot}</strong></td>`;
        
        days.forEach(day => {
            const daySchedule = weekSchedule[day] || {};
            const lesson = findLessonByTime(daySchedule, timeSlot);
            
            if (lesson) {
                const colorClass = getSubjectColorClass(lesson.subject);
                row += `
                    <td class="lesson-cell ${colorClass}">
                        <div class="lesson-content">
                            <div class="subject">${lesson.subject}</div>
                            <div class="teacher">${lesson.teacher || 'N/A'}</div>
                            <small class="time-range">${lesson.startTime}-${lesson.endTime}</small>
                        </div>
                    </td>
                `;
            } else {
                row += '<td class="empty-cell">-</td>';
            }
        });
        
        row += '</tr>';
        return row;
    }).join('');
}

function normalizeTime(time) {
    // Handle both "07:50" and "07:50:00" formats
    if (time.length === 5) {
        return time + ':00';
    }
    return time;
}

function findLessonByTime(daySchedule, targetTime) {
    // Try exact match first
    if (daySchedule[targetTime]) {
        return daySchedule[targetTime];
    }
    
    // Try without seconds
    const timeWithoutSeconds = targetTime.substring(0, 5);
    if (daySchedule[timeWithoutSeconds]) {
        return daySchedule[timeWithoutSeconds];
    }
    
    // Try finding by startTime property
    for (const [key, lesson] of Object.entries(daySchedule)) {
        if (lesson.startTime && normalizeTime(lesson.startTime) === targetTime) {
            return lesson;
        }
    }
    
    return null;
}

function displayTeacherWorkload(teacherWorkloadSummary) {
    const container = document.getElementById('teacherWorkload');
    
    if (!teacherWorkloadSummary || Object.keys(teacherWorkloadSummary).length === 0) {
        container.innerHTML = '<p class="text-muted">No teacher workload data available.</p>';
        return;
    }
    
    // Get max periods from current request or use reasonable default
    const maxPeriodsPerTeacher = currentRequestData?.teacherWorkloadConfig?.maxPeriodsPerTeacherPerWeek || 40;
    
    // Sort teachers by workload (descending)
    const sortedTeachers = Object.entries(teacherWorkloadSummary)
        .sort(([,a], [,b]) => b - a);
    
    let html = `
        <div class="workload-summary mb-4">
            <h6 class="mb-3">
                <i class="fas fa-chart-bar me-2"></i>
                Teacher Workload Distribution
            </h6>
            <div class="row">
    `;
    
    sortedTeachers.forEach(([teacher, periods]) => {
        const utilization = (periods / maxPeriodsPerTeacher) * 100;
        const progressClass = utilization >= 90 ? 'bg-danger' : 
                            utilization >= 75 ? 'bg-warning' : 'bg-success';
        
        html += `
            <div class="col-md-6 col-lg-4 mb-3">
                <div class="card h-100">
                    <div class="card-body">
                        <h6 class="card-title text-truncate" title="${teacher}">${teacher}</h6>
                        <div class="progress mb-2" style="height: 20px;">
                            <div class="progress-bar ${progressClass}" role="progressbar" 
                                 style="width: ${utilization}%" 
                                 aria-valuenow="${utilization}" aria-valuemin="0" aria-valuemax="100">
                                ${utilization.toFixed(1)}%
                            </div>
                        </div>
                        <div class="d-flex justify-content-between">
                            <small class="text-muted">Periods:</small>
                            <small><strong>${periods}/${maxPeriodsPerTeacher}</strong></small>
                        </div>
                    </div>
                </div>
            </div>
        `;
    });
    
    html += '</div></div>';
    container.innerHTML = html;
}

function displayFeasibilityAnalysis(feasibilityAnalysis) {
    const container = document.getElementById('feasibilityAnalysis');
    
    if (!feasibilityAnalysis) {
        container.innerHTML = '';
        return;
    }
    
    if (feasibilityAnalysis.feasible) {
        container.innerHTML = `
            <div class="alert alert-success">
                <h6><i class="fas fa-check-circle me-2"></i>Timetable is Feasible</h6>
                <p class="mb-0">${feasibilityAnalysis.summary}</p>
            </div>
        `;
        return;
    }
    
    // Display detailed feasibility analysis for infeasible solutions
    let html = `
        <div class="card border-danger">
            <div class="card-header bg-danger bg-opacity-10">
                <div class="d-flex justify-content-between align-items-center">
                    <h6 class="mb-0 text-danger">
                        <i class="fas fa-exclamation-triangle me-2"></i>
                        Feasibility Analysis
                    </h6>
                    <div>
                        <button class="btn btn-sm btn-outline-primary me-2" onclick="exportFeasibilityReport()">
                            <i class="fas fa-download me-1"></i>Export Report
                        </button>
                        <span class="badge bg-danger">${feasibilityAnalysis.totalHardViolations} violations</span>
                    </div>
                </div>
            </div>
            <div class="card-body">
                <div class="alert alert-danger">
                    <p class="mb-0">${feasibilityAnalysis.summary}</p>
                </div>
                
                ${generateViolationsAccordion(feasibilityAnalysis.hardConstraintViolations)}
                
                ${generateRecommendationsSection(feasibilityAnalysis.recommendedActions)}
            </div>
        </div>
    `;
    
    container.innerHTML = html;
}

function generateViolationsAccordion(violations) {
    if (!violations || violations.length === 0) {
        return '<p class="text-muted">No constraint violations detected.</p>';
    }
    
    let html = `
        <div class="mb-4">
            <h6 class="mb-3">
                <i class="fas fa-list-alt me-2"></i>
                Constraint Violations Details
            </h6>
            <div class="accordion" id="violationsAccordion">
    `;
    
    violations.forEach((violation, index) => {
        const collapseId = `violation-${index}`;
        const severityClass = getSeverityClass(violation.severity);
        const severityIcon = getSeverityIcon(violation.severity);
        
        html += `
            <div class="accordion-item border-${severityClass}">
                <h2 class="accordion-header">
                    <button class="accordion-button ${index === 0 ? '' : 'collapsed'}" type="button" 
                            data-bs-toggle="collapse" data-bs-target="#${collapseId}">
                        <div class="d-flex align-items-center w-100">
                            <i class="${severityIcon} text-${severityClass} me-2"></i>
                            <strong class="me-auto">${violation.constraintName}</strong>
                            <div class="ms-3">
                                <span class="badge bg-${severityClass} me-2">${violation.severity}</span>
                                <span class="badge bg-secondary">${violation.violationCount} violations</span>
                            </div>
                        </div>
                    </button>
                </h2>
                <div id="${collapseId}" class="accordion-collapse collapse ${index === 0 ? 'show' : ''}" 
                     data-bs-parent="#violationsAccordion">
                    <div class="accordion-body">
                        <div class="row">
                            <div class="col-md-6">
                                <h6 class="text-muted mb-2">Description</h6>
                                <p>${violation.description}</p>
                                
                                <h6 class="text-muted mb-2">Suggested Solution</h6>
                                <div class="alert alert-info">
                                    <i class="fas fa-lightbulb me-2"></i>
                                    ${violation.suggestedFix}
                                </div>
                            </div>
                            <div class="col-md-6">
                                <h6 class="text-muted mb-2">Affected Entities</h6>
                                ${generateAffectedEntitiesList(violation.affectedEntities)}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    });
    
    html += '</div></div>';
    return html;
}

function generateAffectedEntitiesList(affectedEntities) {
    if (!affectedEntities || affectedEntities.length === 0) {
        return '<p class="text-muted">No specific entities affected.</p>';
    }
    
    let html = '<div class="list-group list-group-flush">';
    
    affectedEntities.slice(0, 10).forEach(entity => {
        html += `
            <div class="list-group-item list-group-item-action py-2">
                <small>${entity}</small>
            </div>
        `;
    });
    
    if (affectedEntities.length > 10) {
        html += `
            <div class="list-group-item text-center">
                <small class="text-muted">
                    ... and ${affectedEntities.length - 10} more entities
                </small>
            </div>
        `;
    }
    
    html += '</div>';
    return html;
}

function generateRecommendationsSection(recommendations) {
    if (!recommendations || recommendations.length === 0) {
        return '';
    }
    
    let html = `
        <div class="mt-4">
            <h6 class="mb-3">
                <i class="fas fa-tools me-2"></i>
                Recommended Actions
            </h6>
            <div class="row">
    `;
    
    recommendations.forEach((recommendation, index) => {
        html += `
            <div class="col-md-6 mb-3">
                <div class="card h-100 border-success">
                    <div class="card-body">
                        <div class="d-flex align-items-start">
                            <div class="bg-success bg-opacity-10 rounded-circle p-2 me-3">
                                <span class="text-success fw-bold">${index + 1}</span>
                            </div>
                            <div class="flex-grow-1">
                                <p class="mb-0">${recommendation}</p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    });
    
    html += '</div></div>';
    return html;
}

function getSeverityClass(severity) {
    switch (severity?.toUpperCase()) {
        case 'HIGH': return 'danger';
        case 'MEDIUM': return 'warning';
        case 'LOW': return 'info';
        default: return 'secondary';
    }
}

function getSeverityIcon(severity) {
    switch (severity?.toUpperCase()) {
        case 'HIGH': return 'fas fa-exclamation-triangle';
        case 'MEDIUM': return 'fas fa-exclamation-circle';
        case 'LOW': return 'fas fa-info-circle';
        default: return 'fas fa-question-circle';
    }
}

function exportFeasibilityReport() {
    if (!currentTimetableData || !currentTimetableData.feasibilityAnalysis) {
        showApiStatus('No feasibility analysis data available for export', 'warning');
        return;
    }
    
    const analysis = currentTimetableData.feasibilityAnalysis;
    const timestamp = new Date().toISOString().slice(0, 19).replace(/:/g, '-');
    
    // Generate detailed report
    const report = {
        metadata: {
            exportDate: new Date().toISOString(),
            timetableFeasible: analysis.feasible,
            totalHardViolations: analysis.totalHardViolations,
            totalSoftViolations: analysis.totalSoftViolations,
            score: currentTimetableData.score
        },
        summary: analysis.summary,
        violations: analysis.hardConstraintViolations.map(violation => ({
            constraintName: violation.constraintName,
            severity: violation.severity,
            violationCount: violation.violationCount,
            description: violation.description,
            suggestedFix: violation.suggestedFix,
            affectedEntities: violation.affectedEntities
        })),
        recommendations: analysis.recommendedActions,
        teacherWorkload: currentTimetableData.teacherWorkloadSummary,
        unassignedSummary: currentTimetableData.unassignedSummary
    };
    
    // Create downloadable JSON file
    const blob = new Blob([JSON.stringify(report, null, 2)], { 
        type: 'application/json' 
    });
    
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `timetable-feasibility-report-${timestamp}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    
    showApiStatus('Feasibility report exported successfully', 'success');
}

function exportTimetableAsCSV() {
    if (!currentTimetableData || !currentTimetableData.studentGroupSchedules) {
        showApiStatus('No timetable data available for export', 'warning');
        return;
    }
    
    const timestamp = new Date().toISOString().slice(0, 19).replace(/:/g, '-');
    let csvContent = 'Class,Day,Time,Subject,Teacher,Duration\n';
    
    Object.entries(currentTimetableData.studentGroupSchedules).forEach(([className, classData]) => {
        if (classData.weekSchedule) {
            Object.entries(classData.weekSchedule).forEach(([day, daySchedule]) => {
                Object.entries(daySchedule).forEach(([time, lesson]) => {
                    const duration = lesson.endTime ? `${lesson.startTime}-${lesson.endTime}` : lesson.startTime;
                    csvContent += `"${className}","${day}","${time}","${lesson.subject}","${lesson.teacher || 'N/A'}","${duration}"\n`;
                });
            });
        }
    });
    
    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `timetable-${timestamp}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    
    showApiStatus('Timetable exported as CSV successfully', 'success');
}

function displayUnassignedSummary(unassignedSummary) {
    const countDisplay = document.getElementById('unassignedCount');
    const messageDisplay = document.getElementById('unassignedMessage');
    
    // Handle both possible field names from backend
    const totalUnassigned = unassignedSummary?.totalUnassignedPeriods || 
                           calculateTotalUnassignedFromData(currentTimetableData?.unassignedPeriods) || 0;
    
    countDisplay.textContent = totalUnassigned;
    
    if (totalUnassigned > 0) {
        countDisplay.className = 'h3 score-warning';
        
        // Calculate affected classes from unassignedPeriods data
        const affectedClasses = calculateAffectedClasses(currentTimetableData?.unassignedPeriods);
        
        messageDisplay.innerHTML = `
            ${affectedClasses} classes affected
            <br><small class="text-info">
                <i class="fas fa-info-circle me-1"></i>
                View details below for breakdown
            </small>
        `;
    } else {
        countDisplay.className = 'h3 score-good';
        messageDisplay.textContent = 'All periods assigned successfully';
    }
}

function calculateTotalUnassignedFromData(unassignedPeriods) {
    if (!unassignedPeriods) return 0;
    
    let total = 0;
    Object.values(unassignedPeriods).forEach(gradeData => {
        Object.values(gradeData).forEach(periods => {
            total += periods;
        });
    });
    return total;
}

function calculateAffectedClasses(unassignedPeriods) {
    if (!unassignedPeriods) return 0;
    
    // This is a simplified calculation - ideally we'd get this from detailedUnassignedPeriods
    return Object.keys(unassignedPeriods).length;
}

function displayDetailedUnassignedPeriods(data) {
    const container = document.getElementById('teacherWorkload');
    
    // Check for unassigned data
    const hasUnassignedSummary = data.unassignedSummary && data.unassignedSummary.totalUnassignedPeriods > 0;
    const hasUnassignedPeriods = data.unassignedPeriods && Object.keys(data.unassignedPeriods).length > 0;
    const hasDetailedUnassigned = data.detailedUnassignedPeriods && Object.keys(data.detailedUnassignedPeriods).length > 0;
    
    const hasUnassignedData = hasUnassignedSummary || hasUnassignedPeriods || hasDetailedUnassigned;
    
    // Get existing teacher workload HTML
    const existingHTML = container.innerHTML;
    
    // Add class assignment summary
    const classAssignmentSummary = generateClassAssignmentSummary(data);
    
    if (hasUnassignedData) {
        const unassignedSection = `
            <div class="mt-4">
                <div class="card border-warning">
                    <div class="card-header bg-warning bg-opacity-10">
                        <h6 class="mb-0 text-warning">
                            <i class="fas fa-exclamation-triangle me-2"></i>
                            Unassigned Periods Analysis
                        </h6>
                    </div>
                    <div class="card-body">
                        ${generateUnassignedPeriodsHTML(data)}
                    </div>
                </div>
            </div>
        `;
        
        // Combine all sections
        container.innerHTML = existingHTML + classAssignmentSummary + unassignedSection;
    } else {
        // Only add class assignment summary if no unassigned data
        container.innerHTML = existingHTML + classAssignmentSummary;
    }
}

function generateClassAssignmentSummary(data) {
    if (!data.studentGroupSchedules) {
        return '';
    }
    
    // Calculate assignment statistics
    const classes = Object.keys(data.studentGroupSchedules);
    let totalAssignedPeriods = 0;
    let classBreakdown = [];
    
    classes.forEach(className => {
        const classData = data.studentGroupSchedules[className];
        let classPeriods = 0;
        const subjectCounts = {};
        
        if (classData.weekSchedule) {
            Object.values(classData.weekSchedule).forEach(daySchedule => {
                Object.values(daySchedule).forEach(lesson => {
                    classPeriods++;
                    totalAssignedPeriods++;
                    subjectCounts[lesson.subject] = (subjectCounts[lesson.subject] || 0) + 1;
                });
            });
        }
        
        classBreakdown.push({
            className,
            periods: classPeriods,
            subjects: Object.keys(subjectCounts).length,
            subjectCounts
        });
    });
    
    // Sort classes by name
    classBreakdown.sort((a, b) => a.className.localeCompare(b.className));
    
    let html = `
        <div class="mt-4">
            <div class="card border-success">
                <div class="card-header bg-success bg-opacity-10">
                    <h6 class="mb-0 text-success">
                        <i class="fas fa-check-circle me-2"></i>
                        Class Assignment Summary
                    </h6>
                </div>
                <div class="card-body">
                    <div class="row mb-3">
                        <div class="col-md-4">
                            <div class="text-center">
                                <div class="h4 text-success">${classes.length}</div>
                                <small class="text-muted">Total Classes</small>
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="text-center">
                                <div class="h4 text-primary">${totalAssignedPeriods}</div>
                                <small class="text-muted">Assigned Periods</small>
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="text-center">
                                <div class="h4 text-info">${(totalAssignedPeriods / classes.length).toFixed(1)}</div>
                                <small class="text-muted">Avg Periods/Class</small>
                            </div>
                        </div>
                    </div>
                    
                    <div class="row">
    `;
    
    classBreakdown.forEach(classInfo => {
        const subjectList = Object.entries(classInfo.subjectCounts)
            .map(([subject, count]) => `${subject} (${count})`)
            .join(', ');
        
        html += `
            <div class="col-md-6 col-lg-4 mb-3">
                <div class="card h-100 border-light">
                    <div class="card-body">
                        <h6 class="card-title text-primary">${classInfo.className}</h6>
                        <div class="mb-2">
                            <small class="text-muted">Periods:</small>
                            <span class="badge bg-primary ms-1">${classInfo.periods}</span>
                        </div>
                        <div class="mb-2">
                            <small class="text-muted">Subjects:</small>
                            <span class="badge bg-info ms-1">${classInfo.subjects}</span>
                        </div>
                        <div class="mt-2">
                            <small class="text-muted d-block">Subject breakdown:</small>
                            <small class="text-secondary">${subjectList}</small>
                        </div>
                    </div>
                </div>
            </div>
        `;
    });
    
    html += `
                    </div>
                </div>
            </div>
        </div>
    `;
    
    return html;
}

// Color palette for subjects - SOLID COLORS
const COLOR_PALETTE = [
    '#667eea', '#f093fb', '#4facfe', '#43e97b', '#fa709a',
    '#976d33', '#3d6664', '#ff9a9e', '#a18cd1', '#8f7770',
    '#a18d4a', '#74b9ff', '#fd79a8', '#fdcb6e', '#55a3ff'
];

// Add CSS classes dynamically - SOLID COLORS
function addSubjectColorStyles() {
    if (document.getElementById('subject-color-styles')) {
        return; // Already added
    }
    
    const style = document.createElement('style');
    style.id = 'subject-color-styles';
    
    let css = '';
    COLOR_PALETTE.forEach((color, index) => {
        css += `
            .subject-color-${index} {
                background-color: ${color} !important;
                color: white !important;
                border: none !important;
            }
            .legend-color.subject-color-${index} {
                background-color: ${color} !important;
                width: 20px;
                height: 20px;
                border-radius: 4px;
                display: inline-block;
                margin-right: 8px;
            }
        `;
    });
    
    style.textContent = css;
    document.head.appendChild(style);
}

// Initialize page
document.addEventListener('DOMContentLoaded', function() {
    // Add subject color styles
    addSubjectColorStyles();
    
    // Check if we're running from file:// protocol
    if (window.location.protocol === 'file:') {
        showApiStatus('⚠️ Running from file:// protocol. For best experience, serve files through a web server. See instructions below.', 'warning');
        
        // Add server setup instructions
        setTimeout(() => {
            const statusDiv = document.getElementById('apiStatus');
            statusDiv.innerHTML += `
                <div class="alert alert-info alert-custom mt-2" role="alert">
                    <strong>How to serve files:</strong><br>
                    <code>cd /home/rasi/Documents/Education/OptaPlanner-School-Timetable-Solver</code><br>
                    <code>python3 -m http.server 8000</code><br>
                    Then open: <a href="http://localhost:8000/web-ui/" target="_blank">http://localhost:8000/web-ui/</a>
                </div>
            `;
        }, 1000);
    }
    
    // Load actual request.json by default
    loadRequestJson();
    
    // Add Enter key support for processing
    document.getElementById('jsonInput').addEventListener('keydown', function(e) {
        if (e.ctrlKey && e.key === 'Enter') {
            processTimetable();
        }
    });
});
