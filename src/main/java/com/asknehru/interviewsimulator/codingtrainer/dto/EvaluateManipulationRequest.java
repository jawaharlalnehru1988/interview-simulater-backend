package com.asknehru.interviewsimulator.codingtrainer.dto;

import lombok.Data;
import java.util.List;

@Data
public class EvaluateManipulationRequest {
    private String topic;
    private String category;
    private List<String> questions;
    private List<String> approaches;
    private List<String> answers;
}
