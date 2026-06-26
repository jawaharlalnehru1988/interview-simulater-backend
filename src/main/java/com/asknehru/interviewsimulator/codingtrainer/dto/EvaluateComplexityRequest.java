package com.asknehru.interviewsimulator.codingtrainer.dto;

import lombok.Data;

import java.util.List;

@Data
public class EvaluateComplexityRequest {
    private String topic; // language
    private String category;
    private List<String> questions;
    private List<String> answers;
}
