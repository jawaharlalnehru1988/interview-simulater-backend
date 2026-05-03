package com.asknehru.interviewsimulator.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class GeneratedQuestion {
    private String question;
    private String suggestedAnswer;
    private List<String> mcqOptions;
}
