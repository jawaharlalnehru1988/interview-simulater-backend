package com.asknehru.interviewsimulator.resume;

import com.asknehru.interviewsimulator.ai.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeAiService {
    private final LlmService llmService;

    public JsonNode generateQuestions(String resumeText) {
        String trimmedText = resumeText.length() > 6000 ? resumeText.substring(0, 6000) + "..." : resumeText;
        String prompt = buildQuestionPrompt(trimmedText);
        String response = llmService.generate(prompt);
        if (response == null || response.isBlank()) {
            throw new RuntimeException("LLM did not return a response for questions.");
        }
        return llmService.safeJsonLoads(response);
    }

    public JsonNode generateAnswers(String question, String resumeContext) {
        String trimmedContext = resumeContext.length() > 2000
                ? resumeContext.substring(0, 2000) + "..."
                : resumeContext;
        String prompt = buildAnswerPrompt(trimmedContext, question);
        String response = llmService.generate(prompt);
        if (response == null || response.isBlank()) {
            throw new RuntimeException("LLM did not return a response for answers.");
        }
        return llmService.safeJsonLoads(response);
    }

    private String buildQuestionPrompt(String resumeText) {
        return """
                You are a panel of expert interviewers: a technical lead, an HR manager, and a career counselor.
                
                Below is the text extracted from a candidate's resume:
                
                --- RESUME START ---
                %s
                --- RESUME END ---
                
                Generate exactly 50 interview questions that a panel of interviewers would ask this specific candidate.
                
                Distribute the questions across ALL of these categories (use as many as are relevant):
                - "Technical": deep-dive questions on technologies, frameworks, and languages listed in the resume
                - "Technical Skill-Based": practical, hands-on scenario questions based on their specific stack
                - "Behavioral": questions about past behavior using STAR method (e.g., "Tell me about a time...")
                - "Situational": hypothetical scenarios to test judgment and decision-making
                - "Project-Based": questions about specific projects, contributions, and outcomes from the resume
                - "Career Gap": questions about any employment gaps (if visible), explain and justify approach
                - "Job Switching": questions about frequent company changes (if visible), motivations, stability
                - "Leadership": questions about team lead/management experiences if mentioned
                - "Cultural Fit": questions about work style, values, collaboration, and team dynamics
                
                Tailor every question directly to THIS candidate's resume — avoid generic questions that could apply to anyone.
                
                Return ONLY a valid JSON array of question objects. No extra text, no markdown.
                Each object must have exactly these fields:
                - "question": the full question text (specific to this candidate)
                - "category": one of the category names listed above
                - "difficulty": one of "Easy", "Medium", "Hard"
                - "hint": what a great answer should include (1–2 sentences for the interviewer)
                """.formatted(resumeText);
    }

    private String buildAnswerPrompt(String resumeContext, String question) {
        return """
                You are a senior career coach and interview preparation expert.
                
                The candidate's resume summary (for context):
                --- RESUME CONTEXT ---
                %s
                --- END CONTEXT ---
                
                Interview Question: "%s"
                
                Generate 3 to 5 strong, realistic example answers a candidate could give for this question.
                Each answer should be:
                - Concrete and specific (not vague)
                - Based on the candidate's background when possible
                - Structured naturally as a spoken interview answer (1–4 sentences)
                - Varying in approach (e.g., confident, humble, structured, storytelling)
                
                Return ONLY a valid JSON array of strings. No extra text. No markdown.
                """.formatted(resumeContext, question);
    }
}
