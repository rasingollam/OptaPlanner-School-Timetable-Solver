package com.school.timetabling.domain;

import org.optaplanner.core.api.domain.lookup.PlanningId;

public class Room {
    @PlanningId
    private String name;
    private int capacity;
    private boolean hasProjector;
    private boolean lab;
    private String buildingCode;

    public Room() {}

    public Room(String name, int capacity, boolean hasProjector, boolean lab, String buildingCode) {
        this.name = name;
        this.capacity = capacity;
        this.hasProjector = hasProjector;
        this.lab = lab;
        this.buildingCode = buildingCode;
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    
    public boolean isHasProjector() { return hasProjector; }
    public void setHasProjector(boolean hasProjector) { this.hasProjector = hasProjector; }
    
    public boolean isLab() { return lab; }
    public void setLab(boolean lab) { this.lab = lab; }
    
    public String getBuildingCode() { return buildingCode; }
    public void setBuildingCode(String buildingCode) { this.buildingCode = buildingCode; }

    @Override
    public String toString() {
        return name;
    }
}
