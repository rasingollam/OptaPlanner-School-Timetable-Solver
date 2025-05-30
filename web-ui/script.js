const API_BASE_URL = 'http://localhost:8080/api/timetable';
let currentTimetableData = null;

// Sample data from the request.json
const sampleData = {
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

function loadSampleData() {
    document.getElementById('jsonInput').value = JSON.stringify(sampleData, null, 2);
    clearValidation();
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
    statusDiv.innerHTML = `
        <div class="alert alert-${type} alert-custom" role="alert">
            <i class="fas fa-${type === 'danger' ? 'exclamation-triangle' : type === 'success' ? 'check-circle' : 'info-circle'} me-2"></i>
            ${message}
        </div>
    `;
    
    if (type === 'success' || type === 'danger') {
        setTimeout(() => {
            statusDiv.innerHTML = '';
        }, 5000);
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
    
    // Display score and feasibility
    displayScore(data);
    
    // Populate class selector
    populateClassSelector(data.studentGroupSchedules);
    
    // Display teacher workload
    displayTeacherWorkload(data.teacherWorkloadSummary);
    
    // Display unassigned summary
    displayUnassignedSummary(data.unassignedSummary);
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
    
    countDisplay.textContent = unassignedSummary.totalUnassignedPeriods || 0;
    
    if (unassignedSummary.totalUnassignedPeriods > 0) {
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
    
    display.innerHTML = generateTimetableHTML(selector.value, classSchedule.weekSchedule);
}

function generateTimetableHTML(className, weekSchedule) {
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY'];
    const timeSlots = ['07:50', '08:30', '09:10', '09:50', '10:50', '11:30'];
    
    let html = `
        <div class="class-header">
            <i class="fas fa-users me-2"></i>
            Class ${className} - Weekly Timetable
        </div>
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
        html += `<tr><td class="time-slot">${timeSlot}</td>`;
        
        days.forEach(day => {
            const lesson = weekSchedule[day] && weekSchedule[day][timeSlot];
            if (lesson) {
                const subjectClass = `subject-${lesson.subject.toLowerCase()}`;
                html += `
                    <td>
                        <div class="lesson-card ${subjectClass}">
                            <div class="subject-name">${lesson.subject}</div>
                            <div class="teacher-name">${lesson.teacher}</div>
                            <div class="time-display">${lesson.startTime} - ${lesson.endTime}</div>
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
    
    const maxWorkload = 20; // Based on teacherWorkloadConfig
    let html = '<div class="row">';
    
    Object.entries(workloadSummary).forEach(([teacher, workload], index) => {
        const percentage = Math.min((workload / maxWorkload) * 100, 100);
        const statusClass = workload > maxWorkload ? 'danger' : workload > 15 ? 'warning' : 'success';
        
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
                            ${workload > maxWorkload ? 'Overloaded' : workload > 15 ? 'Heavy Load' : 'Normal Load'}
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
    // Load sample data by default
    loadSampleData();
    
    // Add Enter key support for processing
    document.getElementById('jsonInput').addEventListener('keydown', function(e) {
        if (e.ctrlKey && e.key === 'Enter') {
            processTimetable();
        }
    });
});
