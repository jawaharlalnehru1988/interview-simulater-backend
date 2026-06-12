package com.asknehru.interviewsimulator.interview.dto;

import com.asknehru.interviewsimulator.interview.Interview;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StartInterviewRequest {
    private String topic;
    
    @JsonProperty("round")
    private Interview.RoundType roundType;
}
