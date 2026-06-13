package com.asknehru.interviewsimulator.repositoryai;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "repository_analysis")
@Data
@NoArgsConstructor
public class RepositoryAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String githubUrl;

    @Column(nullable = false)
    private String status; // PENDING, ANALYZING, COMPLETED, FAILED

    @Column(columnDefinition = "TEXT")
    private String summaryData;

    @Column(columnDefinition = "TEXT")
    private String fileTreeData;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
