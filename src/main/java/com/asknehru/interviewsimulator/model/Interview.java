package com.asknehru.interviewsimulator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "interviews_interview")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview {

    public enum RoundType {
        BASIC, CRITICAL_SCENARIO, MCQ, CODING;

        @com.fasterxml.jackson.annotation.JsonCreator
        public static RoundType fromString(String value) {
            return value == null ? null : RoundType.valueOf(value.toUpperCase());
        }
    }

    public enum Status {
        IN_PROGRESS, COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "round_type", length = 100)
    private RoundType roundType = RoundType.BASIC;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Status status = Status.IN_PROGRESS;

    @Column(name = "current_question_index")
    private Integer currentQuestionIndex = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL)
    private List<Question> questions;

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
