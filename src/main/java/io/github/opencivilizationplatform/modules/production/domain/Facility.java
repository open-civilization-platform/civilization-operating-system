package io.github.opencivilizationplatform.modules.production.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "production_facilities")
public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // e.g., VERTICAL_FARM, HOUSING_3D, RECYCLING_HUB

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private Double efficiency;

    @Column(nullable = false)
    private String status; // ACTIVE, MAINTENANCE, OFFLINE

    private String currentOutput;

    public Facility() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Double getEfficiency() { return efficiency; }
    public void setEfficiency(Double efficiency) { this.efficiency = efficiency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentOutput() { return currentOutput; }
    public void setCurrentOutput(String currentOutput) { this.currentOutput = currentOutput; }
}
