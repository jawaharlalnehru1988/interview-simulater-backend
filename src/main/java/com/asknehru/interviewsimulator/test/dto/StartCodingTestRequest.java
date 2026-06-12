package com.asknehru.interviewsimulator.test.dto;

import com.asknehru.interviewsimulator.interview.Question;
import lombok.Data;

@Data
public class StartCodingTestRequest {
    private String topic;
    private Question.Difficulty difficulty;
    private String description;
}
