package com.asknehru.interviewsimulator.service;

import com.asknehru.interviewsimulator.model.JobDescriptionAnalysis;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.JobDescriptionAnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobDescriptionAnalyzerService {

    private final LlmService llmService;
    private final JobDescriptionAnalysisRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public JobDescriptionAnalysis analyzeAndSave(User user, String jobDescription) {
        String prompt = String.format(
            "Analyze this Job Description and return STRICT JSON only.\n\n" +
            "Job Description:\n%s\n\n" +
            "Required JSON schema:\n" +
            "{\n" +
            "  \"recruiter_intent\": \"string\",\n" +
            "  \"skill_tiers\": {\n" +
            "    \"strong_match\": [\"string\"],\n" +
            "    \"okay_match\": [\"string\"],\n" +
            "    \"low_priority\": [\"string\"]\n" +
            "  },\n" +
            "  \"disclosed_salary\": {\n" +
            "    \"found\": boolean,\n" +
            "    \"currency\": \"INR\",\n" +
            "    \"minimum\": number,\n" +
            "    \"maximum\": number,\n" +
            "    \"unit\": \"LPA\",\n" +
            "    \"raw_text\": \"string\"\n" +
            "  },\n" +
            "  \"market_salary_estimate\": {\n" +
            "    \"role_focus\": \"string\",\n" +
            "    \"demandable_min\": number,\n" +
            "    \"demandable_max\": number,\n" +
            "    \"unit\": \"LPA\",\n" +
            "    \"confidence\": \"low|medium|high\",\n" +
            "    \"reasoning\": [\"string\"]\n" +
            "  },\n" +
            "  \"recommendations\": [\"string\"],\n" +
            "  \"encouragement\": \"string\"\n" +
            "}",
            jobDescription
        );

        String raw = llmService.generate(prompt);
        JsonNode parsed = llmService.safeJsonLoads(raw);

        // Extract context via regex (matching Python's logic)
        Map<String, Object> context = extractApplicationContext(jobDescription);
        
        // Normalize and fallback if needed
        Map<String, Object> finalAnalysis = normalizeOutput(parsed, jobDescription);

        JobDescriptionAnalysis entity = JobDescriptionAnalysis.builder()
                .user(user)
                .jobDescription(jobDescription)
                .recruiterName((String) context.get("recruiter_name"))
                .companyName((String) context.get("company_name"))
                .applicationLastDate((LocalDate) context.get("application_last_date"))
                .applicationLastDateRaw((String) context.get("application_last_date_raw"))
                .analysis(serialize(finalAnalysis))
                .build();

        return repository.save(entity);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> extractApplicationContext(String jd) {
        String normalized = jd.replaceAll("\\s+", " ");
        Map<String, Object> result = new HashMap<>();
        result.put("recruiter_name", "");
        result.put("company_name", "");
        result.put("application_last_date", null);
        result.put("application_last_date_raw", "");

        // Recruiter Regex
        Pattern recruiterPattern = Pattern.compile("(?:recruiter|hiring\\s*manager|contact\\s*person)\\s*[:\\-]\\s*([A-Za-z][A-Za-z\\s\\.]{1,80}?)(?=\\.|,\\s*(?:apply|deadline|last\\s*date|company|organization|employer)|$)", Pattern.CASE_INSENSITIVE);
        Matcher m = recruiterPattern.matcher(normalized);
        if (m.find()) {
            result.put("recruiter_name", m.group(1).trim());
        }

        // Company Regex
        Pattern companyPattern = Pattern.compile("(?:company|organization|employer)\\s*[:\\-]\\s*([A-Za-z0-9][A-Za-z0-9\\s&\\.,\\-]{1,100}?)(?=\\.|,\\s*(?:recruiter|apply|deadline|last\\s*date)|$)", Pattern.CASE_INSENSITIVE);
        m = companyPattern.matcher(normalized);
        if (m.find()) {
            result.put("company_name", m.group(1).trim());
        }

        // Date Regex
        Pattern datePattern = Pattern.compile("(?:last\\s*date|apply\\s*by|application\\s*deadline|deadline)\\s*[:\\-]?\\s*([0-3]?\\d[\\-/][01]?\\d[\\-/](?:\\d{2}|\\d{4})|[A-Za-z]{3,9}\\s+[0-3]?\\d,?\\s+\\d{4})", Pattern.CASE_INSENSITIVE);
        m = datePattern.matcher(normalized);
        if (m.find()) {
            String rawDate = m.group(1).trim();
            result.put("application_last_date_raw", rawDate);
            result.put("application_last_date", parseDate(rawDate));
        }

        return result;
    }

    private LocalDate parseDate(String raw) {
        String[] formats = {
            "d/M/yyyy", "d-M-yyyy", "d/M/yy", "d-M-yy",
            "MMM d yyyy", "MMMM d yyyy", "MMM d, yyyy", "MMMM d, yyyy"
        };
        for (String fmt : formats) {
            try {
                return LocalDate.parse(raw.replace(",", ", ").replaceAll("\\s+", " ").trim(), DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH));
            } catch (Exception e) {
                // Continue
            }
        }
        return null;
    }

    private Map<String, Object> normalizeOutput(JsonNode parsed, String jd) {
        // This is a simplified version of Python's _normalize_output
        // In a full implementation, we'd include more regex fallbacks for skills and salary
        Map<String, Object> result = new HashMap<>();
        
        result.put("recruiter_intent", parsed.path("recruiter_intent").asText("Inferred from JD outcomes"));
        
        Map<String, Object> tiers = new HashMap<>();
        tiers.put("strong_match", normalizeList(parsed.path("skill_tiers").path("strong_match")));
        tiers.put("okay_match", normalizeList(parsed.path("skill_tiers").path("okay_match")));
        tiers.put("low_priority", normalizeList(parsed.path("skill_tiers").path("low_priority")));
        result.put("skill_tiers", tiers);

        Map<String, Object> disclosed = new HashMap<>();
        JsonNode sal = parsed.path("disclosed_salary");
        disclosed.put("found", sal.path("found").asBoolean(false));
        disclosed.put("currency", sal.path("currency").asText("INR"));
        disclosed.put("minimum", sal.path("minimum").isNumber() ? sal.path("minimum").asDouble() : null);
        disclosed.put("maximum", sal.path("maximum").isNumber() ? sal.path("maximum").asDouble() : null);
        disclosed.put("unit", sal.path("unit").asText("LPA"));
        disclosed.put("raw_text", sal.path("raw_text").asText(""));
        result.put("disclosed_salary", disclosed);

        Map<String, Object> market = new HashMap<>();
        JsonNode mkt = parsed.path("market_salary_estimate");
        market.put("role_focus", mkt.path("role_focus").asText("General execution focus"));
        market.put("demandable_min", mkt.path("demandable_min").asDouble(10.0));
        market.put("demandable_max", mkt.path("demandable_max").asDouble(15.0));
        market.put("unit", mkt.path("unit").asText("LPA"));
        market.put("confidence", mkt.path("confidence").asText("medium"));
        market.put("reasoning", normalizeList(mkt.path("reasoning")));
        result.put("market_salary_estimate", market);

        result.put("recommendations", normalizeList(parsed.path("recommendations")));
        result.put("encouragement", parsed.path("encouragement").asText("Stay focused and prepare quantified impact stories."));

        return result;
    }

    private List<String> normalizeList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode n : node) list.add(n.asText());
        }
        return list;
    }
}
