package com.asknehru.interviewsimulator.codingtrainer.category;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "saved_question_sets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedQuestionSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String setName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "saved_question_set_items", joinColumns = @JoinColumn(name = "saved_question_set_id"))
    @Column(name = "question", length = 1000)
    private List<String> questions;
}
