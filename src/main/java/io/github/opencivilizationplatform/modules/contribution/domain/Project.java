package io.github.opencivilizationplatform.modules.contribution.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "contribution_projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String impactArea;

    @ElementCollection
    private List<String> requiredSkillNames;

    @Column(nullable = false)
    private String status; // PROPOSED, ACTIVE, COMPLETED

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Project() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getImpactArea() { return impactArea; }
    public void setImpactArea(String impactArea) { this.impactArea = impactArea; }
    public List<String> getRequiredSkillNames() { return requiredSkillNames; }
    public void setRequiredSkillNames(List<String> requiredSkillNames) { this.requiredSkillNames = requiredSkillNames; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
