package com.asknehru.interviewsimulator.codingtrainer.dto;

import com.asknehru.interviewsimulator.interview.Question;
import lombok.Data;

@Data
public class StartCodingTestRequest {
    private String topic;
    private Question.Difficulty difficulty;
    private String description;
}
