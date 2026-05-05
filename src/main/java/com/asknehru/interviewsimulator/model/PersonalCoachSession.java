package com.asknehru.interviewsimulator.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews_personalcoachsession")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalCoachSession {

    public enum Stage {
        SUBTOPIC_SELECTION, LESSON_SELECTION, QUESTIONING, COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(nullable = false)
    private String topic;

    @Column(columnDefinition = "TEXT")
    private String subtopics; // JSON

    @Column(name = "lessons_by_subtopic", columnDefinition = "TEXT")
    private String lessonsBySubtopic; // JSON

    @Column(name = "selected_subtopic")
    private String selectedSubtopic;

    @Column(name = "selected_lesson")
    private String selectedLesson;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Stage stage = Stage.SUBTOPIC_SELECTION;

    @Column(name = "current_lesson", columnDefinition = "TEXT")
    private String currentLesson;

    @Column(name = "current_question", columnDefinition = "TEXT")
    private String currentQuestion;

    @Column(name = "mastery_score")
    private Integer masteryScore = 0;

    @Column(name = "attempt_count")
    private Integer attemptCount = 0;

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
