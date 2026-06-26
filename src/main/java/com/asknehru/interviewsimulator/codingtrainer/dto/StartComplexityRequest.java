package com.asknehru.interviewsimulator.codingtrainer.dto;

import lombok.Data;

import java.util.List;

@Data
public class StartComplexityRequest {
    private String topic; // language
    private String category; // complexity category
    private String description;
    private List<String> previousQuestions;
}
