package com.asknehru.interviewsimulator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews_hrvoiceinterviewsession")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HRVoiceInterviewSession {

    public enum Status {
        IN_PROGRESS, COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private CandidateProfile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aspiration_id")
    private UserAspiration aspiration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_analysis_id")
    private JobDescriptionAnalysis jobAnalysis;

    @Column(columnDefinition = "TEXT")
    private String questions; // JSON list

    @Column(name = "current_question_index")
    private Integer currentQuestionIndex = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status = Status.IN_PROGRESS;

    @Column(name = "pass_decision")
    private Boolean passDecision;

    @Column(name = "final_feedback", columnDefinition = "TEXT")
    private String finalFeedback; // JSON object

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
