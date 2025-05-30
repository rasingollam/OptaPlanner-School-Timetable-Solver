const API_BASE_URL = 'http://localhost:8080/api/timetable';
let currentTimetableData = null;
let subjectColorMap = new Map();
let availableDays = [];
let availableTimeSlots = [];

// Color palette for dynamic subject assignment
const COLOR_PALETTE = [
    'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
    'linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%)',
    'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)',
    'linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%)',
    'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
    'linear-gradient(135deg, #fad0c4 0%, #ffd1ff 100%)',
    'linear-gradient(135deg, #ffeaa7 0%, #fab1a0 100%)',
    'linear-gradient(135deg, #74b9ff 0%, #0984e3 100%)',
    'linear-gradient(135deg, #fd79a8 0%, #e84393 100%)',
    'linear-gradient(135deg, #fdcb6e 0%, #e17055 100%)',
    'linear-gradient(135deg, #55a3ff 0%, #003d82 100%)'
];

// Sample data as fallback when request.json cannot be loaded
const FALLBACK_CONFIG = {
    "timeslotList": [
        {"id": 1, "dayOfWeek": "MONDAY", "startTime": "07:50:00", "endTime": "08:30:00"},
        {"id": 2, "dayOfWeek": "MONDAY", "startTime": "08:30:00", "endTime": "09:10:00"},
        {"id": 3, "dayOfWeek": "MONDAY", "startTime": "09:10:00", "endTime": "09:50:00"},
        {"id": 4, "dayOfWeek": "MONDAY", "startTime": "09:50:00", "endTime": "10:30:00"},
        {"id": 5, "dayOfWeek": "MONDAY", "startTime": "10:50:00", "endTime": "11:30:00"},
        {"id": 6, "dayOfWeek": "MONDAY", "startTime": "11:30:00", "endTime": "12:10:00"},
        {"id": 7, "dayOfWeek": "TUESDAY", "startTime": "07:50:00", "endTime": "08:30:00"},
        {"id": 8, "dayOfWeek": "TUESDAY", "startTime": "08:30:00", "endTime": "09:10:00"},
        {"id": 9, "dayOfWeek": "TUESDAY", "startTime": "09:10:00", "endTime": "09:50:00"},
        {"id": 10, "dayOfWeek": "TUESDAY", "startTime": "09:50:00", "endTime": "10:30:00"},
        {"id": 11, "dayOfWeek": "TUESDAY", "startTime": "10:50:00", "endTime": "11:30:00"},
        {"id": 12, "dayOfWeek": "TUESDAY", "startTime": "11:30:00", "endTime": "12:10:00"},
        {"id": 13, "dayOfWeek": "WEDNESDAY", "startTime": "07:50:00", "endTime": "08:30:00"},
        {"id": 14, "dayOfWeek": "WEDNESDAY", "startTime": "08:30:00", "endTime": "09:10:00"},
        {"id": 15, "dayOfWeek": "WEDNESDAY", "startTime": "09:10:00", "endTime": "09:50:00"},
        {"id": 16, "dayOfWeek": "WEDNESDAY", "startTime": "09:50:00", "endTime": "10:30:00"},
        {"id": 17, "dayOfWeek": "WEDNESDAY", "startTime": "10:50:00", "endTime": "11:30:00"},
        {"id": 18, "dayOfWeek": "WEDNESDAY", "startTime": "11:30:00", "endTime": "12:10:00"}
    ],
    "classList": [
        {"grade": "9th", "classes": ["A", "B", "C", "D", "E", "F", "G", "H"]},
        {"grade": "10th", "classes": ["A", "B", "C", "D", "E", "F", "G", "H"]}
    ],
    "teacherWorkloadConfig": {
        "totalTimeslotsPerWeek": 30,
        "freePeriodsPerTeacherPerWeek": 5,
        "maxPeriodsPerTeacherPerWeek": 20
    },
    "subjectList": ["Math", "English", "Chemistry", "Physics", "Biology"],
    "lessonAssignmentList": [
        {"subject": "Math", "grade": "9th", "possibleTeachers": ["Tharindu Silva", "Malinda Perera","Namal","Komal"], "periodsPerWeek": 7, "maxPeriodsPerDay": 2},
        {"subject": "English", "grade": "9th", "possibleTeachers": ["William Shakespeare", "Jane Austen"], "periodsPerWeek": 5, "maxPeriodsPerDay": 1},
        {"subject": "Chemistry", "grade": "9th", "possibleTeachers": ["Marie Curie", "Antoine Lavoisier"], "periodsPerWeek": 2, "maxPeriodsPerDay": 1},
        {"subject": "Physics", "grade": "9th", "possibleTeachers": ["Albert Einstein", "Isaac Newton"], "periodsPerWeek": 2, "maxPeriodsPerDay": 1},
        {"subject": "Biology", "grade": "9th", "possibleTeachers": ["Charles Darwin", "Gregor Mendel"], "periodsPerWeek": 2, "maxPeriodsPerDay": 1},
        {"subject": "Math", "grade": "10th", "possibleTeachers": ["Danul", "Kasun", "Nimal","Safeet","Binura"], "periodsPerWeek": 8, "maxPeriodsPerDay": 2},
        {"subject": "English", "grade": "10th", "possibleTeachers": ["Navindu", "Anuththara","Rashika Ramachandran"], "periodsPerWeek": 5, "maxPeriodsPerDay": 2}
    ]
};

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
        console.warn('Could not load request.json from any path, using fallback configuration');
        showApiStatus('Could not load request.json file. Using sample configuration. Make sure to serve files through a web server (not file://).', 'warning');
        loadFallbackConfiguration();
    }
}

function loadFallbackConfiguration() {
    document.getElementById('jsonInput').value = JSON.stringify(FALLBACK_CONFIG, null, 2);
    extractTimeslotInfo(FALLBACK_CONFIG.timeslotList);
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

function loadEmptyConfiguration() {
    const emptyConfig = {
        "timeslotList": [],
        "classList": [],
        "teacherWorkloadConfig": {
            "totalTimeslotsPerWeek": 30,
            "freePeriodsPerTeacherPerWeek": 5,
            "maxPeriodsPerTeacherPerWeek": 20
        },
        "subjectList": [],
        "lessonAssignmentList": []
    };
    
    document.getElementById('jsonInput').value = JSON.stringify(emptyConfig, null, 2);
    clearValidation();
}

function assignSubjectColors(subjects) {
    subjectColorMap.clear();
    
    subjects.forEach((subject, index) => {
        const colorIndex = index % COLOR_PALETTE.length;
        subjectColorMap.set(subject, {
            index: colorIndex,
            gradient: COLOR_PALETTE[colorIndex]
        });
    });
}

function getSubjectColorClass(subject) {
    const colorInfo = subjectColorMap.get(subject);
    return colorInfo ? `subject-color-${colorInfo.index}` : 'subject-color-0';
}

function createSubjectLegend(subjects) {
    if (!subjects || subjects.length === 0) return '';
    
    let legendHtml = '<div class="subject-legend"><strong>Subject Colors:</strong>';
    
    subjects.forEach(subject => {
        const colorInfo = subjectColorMap.get(subject);
        if (colorInfo) {
            legendHtml += `
                <div class="legend-item">
                    <div class="legend-color subject-color-${colorInfo.index}"></div>
                    <span>${subject}</span>
                </div>
            `;
        }
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
    
    // Display unassigned summary
    displayUnassignedSummary(data.unassignedSummary);
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

function displayUnassignedSummary(unassignedSummary) {
    const countDisplay = document.getElementById('unassignedCount');
    const messageDisplay = document.getElementById('unassignedMessage');
    
    countDisplay.textContent = unassignedSummary?.totalUnassignedPeriods || 0;
    
    if (unassignedSummary?.totalUnassignedPeriods > 0) {
        countDisplay.className = 'h3 score-warning';
        messageDisplay.textContent = `${unassignedSummary.totalUnassignedClasses} classes affected`;
    } else {
        countDisplay.className = 'h3 score-good';
        messageDisplay.textContent = 'All periods assigned successfully';
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
    const days = availableDays.length > 0 ? availableDays : ['MONDAY', 'TUESDAY', 'WEDNESDAY'];
    const timeSlots = availableTimeSlots.length > 0 ? availableTimeSlots : ['07:50:00', '08:30:00', '09:10:00', '09:50:00', '10:50:00', '11:30:00'];
    
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
