# School Timetable Solver - Web UI

This web interface provides an intuitive way to interact with the OptaPlanner School Timetable Solver API.

## Features

### 📝 JSON Configuration Input
- **Syntax-highlighted editor** for JSON input
- **JSON validation** with error highlighting
- **Sample data loader** for quick testing
- **Keyboard shortcuts** (Ctrl+Enter to process)

### 📊 Results Display
- **Solution score** and feasibility indicators
- **Class-by-class timetables** with interactive selection
- **Color-coded subjects** for easy identification
- **Teacher workload visualization** with progress bars
- **Unassigned periods summary** with detailed breakdown

### 🎨 Modern UI Design
- **Bootstrap 5** responsive design
- **Font Awesome icons** for enhanced UX
- **Professional color schemes** for different subjects
- **Mobile-friendly** responsive layout

## Usage

### 1. Setup
```bash
# Navigate to the web-ui directory
cd /home/rasi/Documents/Education/OptaPlanner-School-Timetable-Solver/web-ui

# Serve files using any local server (Python example)
python3 -m http.server 8000

# Open browser and navigate to
# http://localhost:8000
```

### 2. Using the Interface

#### Input Configuration
1. **Load Sample Data**: Click "Load Sample Data" to populate with example configuration
2. **Custom JSON**: Paste your own JSON configuration in the text area
3. **Validation**: The system validates JSON syntax before processing

#### Process Timetable
1. Click **"Process Timetable"** to send request to API
2. **Loading indicator** shows processing status
3. **API status** displays success/error messages

#### View Results
1. **Solution Summary**: Score, feasibility, and unassigned periods count
2. **Class Selection**: Choose any class from dropdown to view timetable
3. **Timetable Grid**: Color-coded schedule with subjects, teachers, and times
4. **Teacher Workload**: Visual representation of each teacher's load

## API Configuration

The UI connects to the Spring Boot API at:
```javascript
const API_BASE_URL = 'http://localhost:8080/api/timetable';
```

Make sure your OptaPlanner application is running on port 8080.

## File Structure

```
web-ui/
├── index.html          # Main HTML page
├── styles.css          # Custom CSS styling
├── script.js          # JavaScript functionality
└── README.md          # This documentation
```

## Features Breakdown

### Timetable Display
- **Time slots**: 07:50-12:10 across three days
- **Subject colors**:
  - 🔵 Math: Blue gradient
  - 🟣 English: Pink gradient  
  - 🟢 Chemistry: Blue-cyan gradient
  - 🟡 Physics: Green gradient
  - 🔴 Biology: Pink-yellow gradient

### Teacher Workload Bars
- **Green**: Normal load (≤15 periods)
- **Yellow**: Heavy load (16-20 periods)
- **Red**: Overloaded (>20 periods)

### Responsive Design
- **Desktop**: Full layout with side-by-side panels
- **Tablet**: Stacked layout with optimal spacing
- **Mobile**: Compressed timetable with touch-friendly controls

## Browser Compatibility

- ✅ Chrome 90+
- ✅ Firefox 88+
- ✅ Safari 14+
- ✅ Edge 90+

## Customization

### Adding New Subject Colors
Edit `styles.css`:
```css
.subject-newsubject { 
    background: linear-gradient(135deg, #color1 0%, #color2 100%); 
}
```

### Modifying API Endpoint
Edit `script.js`:
```javascript
const API_BASE_URL = 'http://your-server:port/api/timetable';
```

### Custom Time Slots
Modify the `timeSlots` array in `generateTimetableHTML()` function.

## Troubleshooting

### Common Issues

1. **API Connection Failed**
   - Ensure Spring Boot app is running on port 8080
   - Check CORS configuration in backend
   - Verify network connectivity

2. **JSON Validation Errors**
   - Check JSON syntax (missing commas, brackets)
   - Verify required fields are present
   - Use JSON validator tools

3. **Empty Timetable Display**
   - Ensure class name exists in response
   - Check if weekSchedule data is present
   - Verify timeslot format matches expected values

### Debug Mode
Open browser developer tools (F12) to see:
- Network requests to API
- JavaScript console errors
- Response data structure
