package com.asknehru.interviewsimulator.dto;

import com.asknehru.interviewsimulator.model.Question;
import lombok.Data;

@Data
public class StartCodingTestRequest {
    private String topic;
    private Question.Difficulty difficulty;
    private String description;
}
