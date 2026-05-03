package com.asknehru.interviewsimulator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews_hrvoiceinterviewturn")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HRVoiceInterviewTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private HRVoiceInterviewSession session;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String answer;

    private Integer score = 0;

    @Column(columnDefinition = "TEXT")
    private String strengths; // JSON

    @Column(columnDefinition = "TEXT")
    private String weaknesses; // JSON

    @Column(columnDefinition = "TEXT")
    private String improvements; // JSON

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
