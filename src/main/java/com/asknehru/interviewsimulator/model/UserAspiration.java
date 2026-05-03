package com.asknehru.interviewsimulator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews_useraspiration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAspiration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "current_position", nullable = false)
    private String currentPosition;

    @Column(name = "target_job", nullable = false)
    private String targetJob;

    @Column(name = "timeline_months")
    private Integer timelineMonths = 6;

    @Column(name = "current_skills", columnDefinition = "TEXT")
    private String currentSkills; // JSON

    @Column(columnDefinition = "TEXT")
    private String constraints;

    @Column(name = "additional_context", columnDefinition = "TEXT")
    private String additionalContext;

    @Column(columnDefinition = "TEXT")
    private String roadmap; // JSON

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
