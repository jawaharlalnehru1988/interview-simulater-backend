package com.asknehru.interviewsimulator.core;
import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.syllabus.Topic;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews_generatedcontentcache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedContentCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Topic topic;

    @Column(name = "content_type", nullable = false)
    private String contentType; // e.g. "MCQ_TEST", "COACH_SUBTOPICS", "COACH_LESSONS", "COACH_LESSON_DETAILS"

    @Column(name = "\"key\"")
    private String key; // e.g. subtopic name, or subtopic:lesson

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // JSON string or text

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
