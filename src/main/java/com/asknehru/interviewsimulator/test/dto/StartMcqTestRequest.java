package com.asknehru.interviewsimulator.test.dto;

import com.asknehru.interviewsimulator.interview.Question;
import lombok.Data;

@Data
public class StartMcqTestRequest {
    private String topic;
    private Question.Difficulty difficulty;
    private String description;
}
