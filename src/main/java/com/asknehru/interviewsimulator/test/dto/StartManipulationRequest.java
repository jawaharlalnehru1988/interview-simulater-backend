package com.asknehru.interviewsimulator.test.dto;

import lombok.Data;

@Data
public class StartManipulationRequest {
    private String topic;
    private String category;
    private java.util.List<String> previousQuestions;
}
