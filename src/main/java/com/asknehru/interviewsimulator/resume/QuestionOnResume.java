package com.asknehru.interviewsimulator.resume;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "question_on_resume")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionOnResume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    @JsonIgnore
    private ResumeAnalyser resume;

    @Column(name = "category")
    private String category;

    @Column(name = "difficulty")
    private String difficulty;

    @Column(name = "question_text", columnDefinition = "TEXT")
    private String question;

    @Column(name = "hint", columnDefinition = "TEXT")
    private String hint;

    @OneToOne(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private GeneratedAnswers generatedAnswers;
}
