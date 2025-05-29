package com.school.timetabling.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.optaplanner.core.api.domain.lookup.PlanningId;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class Timeslot {
    @PlanningId
    private Long id;
    private DayOfWeek dayOfWeek;
    
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;
    
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;

    public Timeslot() {}

    public Timeslot(Long id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    @Override
    public String toString() {
        return dayOfWeek + " " + startTime + "-" + endTime;
    }
}
