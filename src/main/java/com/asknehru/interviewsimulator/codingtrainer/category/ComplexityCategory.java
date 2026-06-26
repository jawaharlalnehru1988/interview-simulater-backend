package com.asknehru.interviewsimulator.codingtrainer.category;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "complexity_categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplexityCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;
}
