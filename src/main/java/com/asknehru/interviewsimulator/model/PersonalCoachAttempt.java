package com.asknehru.interviewsimulator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews_personalcoachattempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalCoachAttempt {

    public enum CoachDecision {
        ADVANCE, REMEDIATE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private PersonalCoachSession session;

    private String subtopic;
    private String lesson;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(name = "user_answer", columnDefinition = "TEXT", nullable = false)
    private String userAnswer;

    @Column(nullable = false)
    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String strengths; // JSON

    @Column(columnDefinition = "TEXT")
    private String gaps; // JSON

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Enumerated(EnumType.STRING)
    @Column(name = "coach_decision", length = 20)
    private CoachDecision coachDecision;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
