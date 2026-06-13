package com.asknehru.interviewsimulator.roadmap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "roadmap_subtopics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapSubtopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    @JsonIgnore
    private RoadmapChapter chapter;

    @Column(name = "subtopic_name", nullable = false, length = 1000)
    private String subtopicName;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;
}
