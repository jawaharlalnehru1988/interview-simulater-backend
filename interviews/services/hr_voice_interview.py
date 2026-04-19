from .llm_client import LLMClient


class HRVoiceInterviewService:
    def __init__(self):
        self.client = LLMClient()

    def generate_questions(self, profile, aspiration, job_analysis, question_count=12):
        common_questions = [
            "What is your total years of experience and how much is directly relevant to this role?",
            "What is your current position and where are you currently working?",
            "Why are you planning to leave your current job?",
            "What are your salary expectations for this role?",
            "Can you explain your experience in your top key skills with concrete examples?",
            "If there is any career gap, can you explain it briefly and what you learned in that period?",
        ]

        prompt = f"""
You are an HR recruiter conducting an initial screening call.
Generate {question_count} short, realistic HR screening questions.

Candidate Profile:
{profile}

Aspiration:
{aspiration}

JD analysis context:
{job_analysis}

Rules:
- Focus on HR/recruiter screening style.
- Include experience, current role, reason for switch, salary expectation, skill depth, location/notice period readiness.
- Include role-fit and motivation checks.
- Keep each question on one line.
- No numbering.
- No explanations.
""".strip()

        raw = self.client.generate(prompt)
        generated = self._extract_lines(raw)

        merged = []
        seen = set()
        for question in [*common_questions, *generated]:
            key = question.lower().strip()
            if not key or key in seen:
                continue
            seen.add(key)
            merged.append(question.strip())
            if len(merged) >= question_count:
                break

        while len(merged) < question_count:
            merged.append(f"Please share one more concrete example that proves your fit for this role ({len(merged)+1}).")

        return merged[:question_count]

    def evaluate_answer(self, question, answer, profile, aspiration, job_analysis):
        prompt = f"""
You are evaluating an HR interview answer.

Question: {question}
Answer: {answer}

Candidate Profile:
{profile}
Aspiration:
{aspiration}
JD context:
{job_analysis}

Return STRICT JSON:
{{
  "score": 0-100 integer,
  "strengths": ["string"],
  "weaknesses": ["string"],
                    "improvements": ["string"],
                    "better_answer": "A concise sample answer (3-5 lines) that the candidate could have said for this exact question"
}}

Rules:
- The better_answer must sound natural for an HR screening call.
- Keep it specific, measurable, and role-fit oriented.
- Do not include markdown.
""".strip()

        raw = self.client.generate(prompt)
        parsed = self.client.safe_json_loads(raw)

        if not parsed:
            return {
                "score": 58,
                "strengths": ["You attempted to answer the recruiter question."],
                "weaknesses": ["The answer lacks specific evidence and structure."],
                "improvements": ["Answer with concise structure: context, action, outcome, and role-fit."],
                "better_answer": (
                    "I currently work as a backend engineer leading API reliability improvements. "
                    "In the last release cycle, I reduced p95 latency by 28% and cut incident volume by 35% "
                    "through caching and retry redesign. I am now looking for a role where I can own larger "
                    "distributed systems and deliver measurable product impact."
                ),
            }

        return {
            "score": self._safe_score(parsed.get("score")),
            "strengths": self._normalize_list(parsed.get("strengths")) or ["Clear intent in the response."],
            "weaknesses": self._normalize_list(parsed.get("weaknesses")) or ["Needs more concrete examples."],
            "improvements": self._normalize_list(parsed.get("improvements"))
            or ["Add measurable outcomes and role relevance."],
            "better_answer": str(parsed.get("better_answer", "")).strip()
            or (
                "I am currently in a role where I handle core responsibilities relevant to this position, "
                "and I have delivered measurable results such as improved quality, speed, and collaboration. "
                "I am looking to move because this role aligns better with my long-term growth and impact goals."
            ),
        }

    def finalize_interview(self, turns, profile, aspiration, job_analysis):
        avg_score = 0
        if turns:
            avg_score = round(sum(item.get("score", 0) for item in turns) / len(turns), 2)

        strong_answers = [item for item in turns if item.get("score", 0) >= 70]
        weak_answers = [item for item in turns if item.get("score", 0) < 55]

        pass_decision = avg_score >= 65 and len(weak_answers) <= max(2, len(turns) // 4)

        summary_prompt = f"""
You are an HR interviewer. Provide final interview feedback.

Average score: {avg_score}
Pass decision suggestion: {'pass' if pass_decision else 'fail'}

Profile:
{profile}
Aspiration:
{aspiration}
JD context:
{job_analysis}

Turns:
{turns}

Return STRICT JSON:
{{
  "overall_feedback": "string",
  "improvement_plan": ["string"]
}}
""".strip()

        raw = self.client.generate(summary_prompt)
        parsed = self.client.safe_json_loads(raw)

        overall_feedback = (
            str(parsed.get("overall_feedback", "")).strip()
            if parsed
            else "You showed useful strengths but should improve consistency and role-specific evidence."
        )
        if not overall_feedback:
            overall_feedback = "You showed useful strengths but should improve consistency and role-specific evidence."

        improvement_plan = self._normalize_list(parsed.get("improvement_plan")) if parsed else []
        if not improvement_plan:
            improvement_plan = [
                "Use concise STAR-style answers (Situation, Task, Action, Result).",
                "Quantify outcomes wherever possible (numbers, scope, impact).",
                "Align every answer to the JD must-have requirements.",
            ]

        return {
            "pass": pass_decision,
            "average_score": avg_score,
            "overall_feedback": overall_feedback,
            "strong_answers": [
                {
                    "question": item.get("question", ""),
                    "answer": item.get("answer", ""),
                    "score": item.get("score", 0),
                    "strengths": item.get("strengths", []),
                }
                for item in strong_answers[:5]
            ],
            "weak_answers": [
                {
                    "question": item.get("question", ""),
                    "answer": item.get("answer", ""),
                    "score": item.get("score", 0),
                    "weaknesses": item.get("weaknesses", []),
                    "improvements": item.get("improvements", []),
                }
                for item in weak_answers[:5]
            ],
            "improvement_plan": improvement_plan,
        }

    @staticmethod
    def _extract_lines(raw_text):
        if not raw_text:
            return []

        lines = []
        for line in raw_text.splitlines():
            cleaned = line.strip()
            cleaned = cleaned.lstrip("-*0123456789. ").strip()
            if cleaned:
                lines.append(cleaned)

        unique = []
        seen = set()
        for line in lines:
            key = line.lower()
            if key not in seen:
                seen.add(key)
                unique.append(line)
        return unique

    @staticmethod
    def _normalize_list(value):
        if not value:
            return []
        if isinstance(value, list):
            return [str(item).strip() for item in value if str(item).strip()]
        if isinstance(value, str):
            text = value.strip()
            return [text] if text else []
        return [str(value)]

    @staticmethod
    def _safe_score(value):
        try:
            score = int(value)
        except (TypeError, ValueError):
            score = 58
        return max(0, min(score, 100))
