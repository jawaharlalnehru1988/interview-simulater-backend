package com.asknehru.interviewsimulator.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ChatRequest {
    private List<Map<String, String>> messages;
}
