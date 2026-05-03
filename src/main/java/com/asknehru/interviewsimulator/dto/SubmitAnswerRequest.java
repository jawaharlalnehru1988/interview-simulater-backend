package com.asknehru.interviewsimulator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SubmitAnswerRequest {
    @JsonProperty("question_id")
    private Long questionId;
    private String answer;
}
