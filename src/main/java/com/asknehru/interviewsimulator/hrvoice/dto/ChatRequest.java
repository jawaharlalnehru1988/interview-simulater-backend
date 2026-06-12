package com.asknehru.interviewsimulator.hrvoice.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ChatRequest {
    private List<Map<String, String>> messages;
}
