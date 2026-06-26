package com.asknehru.interviewsimulator.codingtrainer.dto;

import lombok.Data;
import java.util.List;

@Data
public class SubmitMcqTestRequest {
    
    @Data
    public static class AnswerEntry {
        private Long questionId;
        private String answer;
    }

    private List<AnswerEntry> answers;
}
