package com.asknehru.interviewsimulator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews_aspirationchecklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AspirationChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aspiration_id", nullable = false)
    private UserAspiration aspiration;

    @Column(columnDefinition = "TEXT")
    private String items; // JSON

    @Column(name = "completed_count")
    private Integer completedCount = 0;

    @Column(name = "total_count")
    private Integer totalCount = 0;

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
