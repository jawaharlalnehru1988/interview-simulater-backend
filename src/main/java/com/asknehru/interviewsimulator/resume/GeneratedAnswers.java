package com.asknehru.interviewsimulator.resume;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "generated_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedAnswers {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    @JsonIgnore
    private QuestionOnResume question;

    @Column(name = "answer1", columnDefinition = "TEXT")
    private String answer1;

    @Column(name = "answer2", columnDefinition = "TEXT")
    private String answer2;

    @Column(name = "answer3", columnDefinition = "TEXT")
    private String answer3;

    @Column(name = "answer4", columnDefinition = "TEXT")
    private String answer4;

    @Column(name = "answer5", columnDefinition = "TEXT")
    private String answer5;

    public List<String> toList() {
        List<String> list = new ArrayList<>();
        if (answer1 != null && !answer1.isEmpty()) list.add(answer1);
        if (answer2 != null && !answer2.isEmpty()) list.add(answer2);
        if (answer3 != null && !answer3.isEmpty()) list.add(answer3);
        if (answer4 != null && !answer4.isEmpty()) list.add(answer4);
        if (answer5 != null && !answer5.isEmpty()) list.add(answer5);
        return list;
    }
}
