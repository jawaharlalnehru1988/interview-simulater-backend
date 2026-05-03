package com.asknehru.interviewsimulator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews_answer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Answer {

    public enum EvaluationStatus {
        PENDING, COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "user_input", columnDefinition = "TEXT", nullable = false)
    private String userInput;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", length = 20)
    private EvaluationStatus evaluationStatus = EvaluationStatus.PENDING;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(mappedBy = "answer", cascade = CascadeType.ALL)
    private Evaluation evaluation;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
