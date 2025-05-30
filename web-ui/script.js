const API_BASE_URL = 'http://localhost:8080/api/timetable';
let currentTimetableData = null;
let subjectColorMap = new Map();
let availableDays = [];
let availableTimeSlots = [];

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
    
    // Populate class selector
    populateClassSelector(data.studentGroupSchedules);
    
    // Display teacher workload
    displayTeacherWorkload(data.teacherWorkloadSummary);
    
    // Display unassigned summary (enhanced)
    displayUnassignedSummary(data.unassignedSummary);
    
    // Display detailed unassigned periods information
    displayDetailedUnassignedPeriods(data);
}

function extractSubjectsFromResponse(data) {
    const subjects = new Set();
    
    if (data.studentGroupSchedules) {
        Object.values(data.studentGroupSchedules).forEach(classSchedule => {
            if (classSchedule.weekSchedule) {
                Object.values(classSchedule.weekSchedule).forEach(daySchedule => {
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

function displayScore(data) {
    const scoreDisplay = document.getElementById('scoreDisplay');
    const feasibilityBadge = document.getElementById('feasibilityBadge');
    
    scoreDisplay.textContent = data.score;
    
    if (data.feasible) {
        scoreDisplay.className = 'h3 score-good';
        feasibilityBadge.innerHTML = '<span class="badge bg-success">Feasible</span>';
    } else {
        scoreDisplay.className = 'h3 score-danger';
        feasibilityBadge.innerHTML = '<span class="badge bg-danger">Not Feasible</span>';
    }
}

function populateClassSelector(studentGroupSchedules) {
    const selector = document.getElementById('classSelect');
    selector.innerHTML = '<option value="">Choose a class...</option>';
    
    if (!studentGroupSchedules) return;
    
    Object.keys(studentGroupSchedules).sort().forEach(className => {
        const option = document.createElement('option');
        option.value = className;
        option.textContent = className;
        selector.appendChild(option);
    });
}

function displayTimetable() {
    const selector = document.getElementById('classSelect');
    const display = document.getElementById('timetableDisplay');
    
    if (!selector.value || !currentTimetableData) {
        display.innerHTML = '';
        return;
    }
    
    const classSchedule = currentTimetableData.studentGroupSchedules[selector.value];
    if (!classSchedule || !classSchedule.weekSchedule) {
        display.innerHTML = '<p class="text-muted">No schedule available for this class.</p>';
        return;
    }
    
    const subjects = extractSubjectsFromResponse(currentTimetableData);
    display.innerHTML = generateTimetableHTML(selector.value, classSchedule.weekSchedule, subjects);
}

function generateTimetableHTML(className, weekSchedule, subjects) {
    // Use dynamic days and time slots if available, otherwise fall back to defaults
    const days = availableDays.length > 0 ? availableDays : ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
    const timeSlots = availableTimeSlots.length > 0 ? availableTimeSlots : ['07:50:00', '08:30:00', '09:10:00', '09:50:00', '10:50:00', '11:30:00', '12:10:00', '12:50:00'];
    
    let html = `
        <div class="class-header">
            <i class="fas fa-users me-2"></i>
            Class ${className} - Weekly Timetable
        </div>
        ${createSubjectLegend(subjects)}
        <div class="timetable-grid">
            <table class="table table-bordered timetable-table mb-0">
                <thead>
                    <tr>
                        <th style="width: 100px;">Time</th>
    `;
    
    days.forEach(day => {
        html += `<th class="day-header">${day}</th>`;
    });
    html += '</tr></thead><tbody>';
    
    timeSlots.forEach(timeSlot => {
        // Handle both time formats (with and without seconds)
        const timeKey = timeSlot.length === 5 ? timeSlot : timeSlot.substring(0, 5);
        html += `<tr><td class="time-slot">${timeKey}</td>`;
        
        days.forEach(day => {
            // Try both time formats when looking for lessons
            const lesson = weekSchedule[day] && (
                weekSchedule[day][timeSlot] || 
                weekSchedule[day][timeKey] ||
                weekSchedule[day][timeSlot + ':00']
            );
            
            if (lesson) {
                const subjectColorClass = getSubjectColorClass(lesson.subject);
                const startTime = lesson.startTime ? lesson.startTime.substring(0, 5) : timeKey;
                const endTime = lesson.endTime ? lesson.endTime.substring(0, 5) : '';
                
                html += `
                    <td>
                        <div class="lesson-card ${subjectColorClass}">
                            <div class="subject-name">${lesson.subject}</div>
                            <div class="teacher-name">${lesson.teacher}</div>
                            <div class="time-display">${startTime}${endTime ? ` - ${endTime}` : ''}</div>
                        </div>
                    </td>
                `;
            } else {
                html += '<td class="empty-slot">Free Period</td>';
            }
        });
        
        html += '</tr>';
    });
    
    html += '</tbody></table></div>';
    return html;
}

function displayTeacherWorkload(workloadSummary) {
    const container = document.getElementById('teacherWorkload');
    
    if (!workloadSummary || Object.keys(workloadSummary).length === 0) {
        container.innerHTML = '<p class="text-muted">No teacher workload data available.</p>';
        return;
    }
    
    // Get max workload from the current data or use default
    const maxWorkload = Math.max(...Object.values(workloadSummary), 20);
    let html = '<div class="row">';
    
    Object.entries(workloadSummary).forEach(([teacher, workload], index) => {
        const percentage = Math.min((workload / maxWorkload) * 100, 100);
        
        if (index % 2 === 0 && index > 0) {
            html += '</div><div class="row mt-3">';
        }
        
        html += `
            <div class="col-md-6 mb-3">
                <div class="card">
                    <div class="card-body">
                        <h6 class="card-title mb-3">${teacher}</h6>
                        <div class="workload-bar">
                            <div class="workload-fill" style="width: ${percentage}%"></div>
                            <div class="workload-text">${workload}/${maxWorkload}</div>
                        </div>
                        <small class="text-muted mt-1 d-block">
                            ${workload > maxWorkload ? 'Overloaded' : workload > maxWorkload * 0.75 ? 'Heavy Load' : 'Normal Load'}
                        </small>
                    </div>
                </div>
            </div>
        `;
    });
    
    html += '</div>';
    container.innerHTML = html;
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
    
    // Check for unassigned data in multiple possible locations
    const hasUnassignedData = (data.unassignedSummary && data.unassignedSummary.totalUnassignedPeriods > 0) ||
                             (data.unassignedPeriods && Object.keys(data.unassignedPeriods).length > 0);
    
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
        
        // Get existing teacher workload HTML
        const existingHTML = container.innerHTML;
        
        // Append unassigned section
        container.innerHTML = existingHTML + unassignedSection;
    }
}

function generateUnassignedPeriodsHTML(data) {
    // Use the actual data structure from backend
    const unassignedPeriods = data.unassignedPeriods || {};
    const detailedUnassignedPeriods = data.detailedUnassignedPeriods || {};
    const unassignedSummary = data.unassignedSummary;
    
    // Calculate totals from actual data
    const totalUnassignedPeriods = calculateTotalUnassignedFromData(unassignedPeriods);
    const totalGrades = Object.keys(unassignedPeriods).length;
    
    // Calculate total affected classes from detailed data
    let totalAffectedClasses = 0;
    Object.values(detailedUnassignedPeriods).forEach(gradeData => {
        Object.values(gradeData).forEach(subjectData => {
            totalAffectedClasses += Object.keys(subjectData).length;
        });
    });
    
    let html = `
        <div class="row mb-3">
            <div class="col-md-4">
                <div class="text-center">
                    <div class="h4 text-warning">${totalUnassignedPeriods}</div>
                    <small class="text-muted">Total Unassigned Periods</small>
                </div>
            </div>
            <div class="col-md-4">
                <div class="text-center">
                    <div class="h4 text-info">${totalAffectedClasses}</div>
                    <small class="text-muted">Classes Affected</small>
                </div>
            </div>
            <div class="col-md-4">
                <div class="text-center">
                    <div class="h4 text-secondary">${totalGrades}</div>
                    <small class="text-muted">Grades Affected</small>
                </div>
            </div>
        </div>
    `;

    if (Object.keys(unassignedPeriods).length > 0) {
        html += '<div class="accordion" id="unassignedAccordion">';
        
        Object.entries(unassignedPeriods).forEach(([grade, gradeSubjects], index) => {
            const collapseId = `collapse-${grade}`;
            const isFirstItem = index === 0;
            
            // Calculate grade totals
            const gradeTotal = Object.values(gradeSubjects).reduce((sum, periods) => sum + periods, 0);
            const gradeAffectedClasses = detailedUnassignedPeriods[grade] ? 
                Object.values(detailedUnassignedPeriods[grade]).reduce((sum, subjectData) => 
                    sum + Object.keys(subjectData).length, 0) : 0;
            
            html += `
                <div class="accordion-item">
                    <h2 class="accordion-header">
                        <button class="accordion-button ${isFirstItem ? '' : 'collapsed'}" type="button" 
                                data-bs-toggle="collapse" data-bs-target="#${collapseId}">
                            <strong>Grade ${grade}</strong>
                            <span class="ms-auto me-3">
                                <span class="badge bg-warning">${gradeTotal} periods</span>
                                <span class="badge bg-info">${gradeAffectedClasses} classes</span>
                            </span>
                        </button>
                    </h2>
                    <div id="${collapseId}" class="accordion-collapse collapse ${isFirstItem ? 'show' : ''}" 
                         data-bs-parent="#unassignedAccordion">
                        <div class="accordion-body">
                            ${generateGradeBreakdownHTMLFromBackendData(grade, gradeSubjects, detailedUnassignedPeriods[grade] || {})}
                        </div>
                    </div>
                </div>
            `;
        });
        
        html += '</div>';
    }

    return html;
}

function generateGradeBreakdownHTMLFromBackendData(grade, gradeSubjects, detailedGradeData) {
    let html = '';
    
    Object.entries(gradeSubjects).forEach(([subject, totalPeriods]) => {
        const subjectColorClass = getSubjectColorClass(subject);
        const subjectDetailedData = detailedGradeData[subject] || {};
        const affectedClasses = Object.keys(subjectDetailedData);
        
        html += `
            <div class="card mb-3 border-start border-4" style="border-left-color: var(--bs-warning) !important;">
                <div class="card-body">
                    <div class="row align-items-center">
                        <div class="col-md-3">
                            <div class="d-flex align-items-center">
                                <div class="legend-color ${subjectColorClass} me-2" style="width: 20px; height: 20px; border-radius: 4px;"></div>
                                <strong>${subject}</strong>
                            </div>
                        </div>
                        <div class="col-md-3">
                            <small class="text-muted">Unassigned Periods:</small><br>
                            <span class="badge bg-secondary">${totalPeriods}</span>
                        </div>
                        <div class="col-md-3">
                            <small class="text-muted">Classes Affected:</small><br>
                            <span class="badge bg-warning">${affectedClasses.length}</span>
                        </div>
                        <div class="col-md-3">
                            <small class="text-muted">Reason:</small><br>
                            <span class="text-danger small">Teacher capacity limit reached</span>
                        </div>
                    </div>
                    
                    ${generateClassLevelBreakdownFromBackendData(grade, subject, subjectDetailedData)}
                </div>
            </div>
        `;
    });
    
    return html;
}

function generateClassLevelBreakdownFromBackendData(grade, subject, subjectDetailedData) {
    if (!subjectDetailedData || Object.keys(subjectDetailedData).length === 0) {
        return '';
    }
    
    let html = `
        <div class="mt-3">
            <h6 class="text-muted mb-2">
                <i class="fas fa-users me-1"></i>
                Affected Classes Detail:
            </h6>
            <div class="row">
    `;
    
    Object.entries(subjectDetailedData).forEach(([className, unassignedCount]) => {
        const fullClassName = `${grade}${className}`;
        
        html += `
            <div class="col-md-3 mb-2">
                <div class="card card-body py-2 text-center bg-light">
                    <div class="fw-bold">${fullClassName}</div>
                    <small class="text-danger">${unassignedCount} periods unassigned</small>
                </div>
            </div>
        `;
    });
    
    html += `
            </div>
        </div>
    `;
    
    return html;
}

// Add toggle functionality for detailed view
function toggleUnassignedDetails() {
    const detailsSection = document.getElementById('unassignedDetails');
    if (detailsSection) {
        detailsSection.classList.toggle('d-none');
    }
}

// Initialize page
document.addEventListener('DOMContentLoaded', function() {
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
