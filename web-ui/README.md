# School Timetable Solver - Web UI

This web interface provides an intuitive way to interact with the OptaPlanner School Timetable Solver API.

## ⚠️ Important: CORS and File Access

Browsers block local file access (file:// protocol) for security reasons. You **must** serve the files through a web server.

## Quick Setup

### Option 1: Python HTTP Server (Recommended)
```bash
# Navigate to the project root directory
cd /home/rasi/Documents/Education/OptaPlanner-School-Timetable-Solver

# Start a simple HTTP server
python3 -m http.server 8000

# Open browser and navigate to
http://localhost:8000/web-ui/
```

### Option 2: Node.js HTTP Server
```bash
# Install http-server globally
npm install -g http-server

# Navigate to project root
cd /home/rasi/Documents/Education/OptaPlanner-School-Timetable-Solver

# Start server
http-server -p 8000

# Open browser and navigate to
http://localhost:8000/web-ui/
```

### Option 3: VSCode Live Server
1. Install "Live Server" extension in VSCode
2. Open the `web-ui` folder in VSCode
3. Right-click on `index.html` → "Open with Live Server"

## Features

### 📝 JSON Configuration Input
- **Syntax-highlighted editor** for JSON input
- **JSON validation** with error highlighting
- **Multiple file loading attempts** with fallback configuration
- **Keyboard shortcuts** (Ctrl+Enter to process)

### 📊 Results Display
- **Solution score** and feasibility indicators
- **Class-by-class timetables** with interactive selection
- **Dynamic color assignment** for subjects
- **Teacher workload visualization** with progress bars
- **Unassigned periods summary** with detailed breakdown

### 🎨 Modern UI Design
- **Bootstrap 5** responsive design
- **Font Awesome icons** for enhanced UX
- **Dynamic color schemes** for different subjects
- **Mobile-friendly** responsive layout

## File Loading Strategy

The UI attempts to load `request.json` from multiple locations:
1. `./request.json` (same directory as HTML)
2. `../request.json` (parent directory - original location)
3. `/request.json` (root of web server)
4. `request.json` (relative to current path)

If none are found, it uses a built-in fallback configuration.

## Usage

### 1. Start Backend API
```bash
# Ensure your OptaPlanner Spring Boot app is running
cd /home/rasi/Documents/Education/OptaPlanner-School-Timetable-Solver
mvn spring-boot:run
```

### 2. Start Web Server
```bash
# Use any of the setup options above
python3 -m http.server 8000
```

### 3. Access the Interface
Open: http://localhost:8000/web-ui/

### 4. Using the Interface

#### Input Configuration
1. **Auto-load**: request.json loads automatically when page opens
2. **Reload**: Click "Reload Request.json" to refresh configuration
3. **Custom JSON**: Modify the loaded JSON or paste your own
4. **Validation**: System validates JSON syntax before processing

#### Process Timetable
1. Click **"Process Timetable"** to send request to API
2. **Loading indicator** shows processing status
3. **API status** displays success/error messages

#### View Results
1. **Solution Summary**: Score, feasibility, and unassigned periods count
2. **Class Selection**: Choose any class from dropdown to view timetable
3. **Color-coded Timetable**: Automatically assigned colors for subjects
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
├── request.json       # Copy of request configuration
└── README.md          # This documentation
```

## Features Breakdown

### Dynamic Subject Colors
- **Automatic Assignment**: 15 unique gradient colors assigned to subjects
- **Color Legend**: Shows subject-color mapping above each timetable
- **Consistent Colors**: Same subject always gets same color across classes

### Flexible Configuration
- **Dynamic Days**: Supports any days of the week from timeslotList
- **Dynamic Time Slots**: Uses actual time slots from configuration
- **Format Handling**: Handles both "HH:mm" and "HH:mm:ss" formats
- **Class Structure**: Adapts to any grade/class structure

### Error Handling
- **CORS Detection**: Warns when running from file:// protocol
- **Fallback Loading**: Uses sample data when request.json unavailable
- **JSON Validation**: Comprehensive error reporting for malformed JSON
- **API Errors**: User-friendly error messages for API failures

## Troubleshooting

### Common Issues

1. **CORS Error / Failed to Fetch**
   ```
   Solution: Use a web server instead of opening HTML directly
   Command: python3 -m http.server 8000
   URL: http://localhost:8000/web-ui/
   ```

2. **API Connection Failed**
   - Ensure Spring Boot app is running on port 8080
   - Check CORS configuration in backend
   - Verify network connectivity

3. **Request.json Not Loading**
   - Make sure request.json exists in web-ui directory
   - Check file permissions
   - Use browser dev tools to see fetch attempts

4. **JSON Validation Errors**
   - Check JSON syntax (missing commas, brackets)
   - Verify required fields are present
   - Use JSON validator tools

### Debug Mode
Open browser developer tools (F12) to see:
- Network requests and responses
- JavaScript console errors
- File loading attempts

## Browser Compatibility

- ✅ Chrome 90+
- ✅ Firefox 88+
- ✅ Safari 14+
- ✅ Edge 90+

## Development Notes

### Adding New Features
- Subject colors are dynamically assigned from COLOR_PALETTE array
- Add new gradients to COLOR_PALETTE for more subjects
- Time slots and days are extracted from timeslotList automatically

### Customization
```javascript
// Add more colors
const COLOR_PALETTE = [
    // ...existing colors...
    'linear-gradient(135deg, #newcolor1 0%, #newcolor2 100%)'
];

// Change API endpoint
const API_BASE_URL = 'http://your-server:port/api/timetable';
```

## Security Notes

- Web server required for file access
- CORS headers needed for API calls
- No sensitive data stored in browser
- All API calls use standard HTTP/HTTPS
