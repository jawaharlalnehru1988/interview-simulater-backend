import re

from .llm_client import LLMClient


class PersonalCoachService:
    def __init__(self):
        self.client = LLMClient()

    def generate_subtopics(self, topic):
        prompt = f"""
You are a personal interview coach.
Generate a clean list of subtopics for topic: {topic}

Rules:
- Return only subtopic names (not full interview questions).
- Count can vary based on topic depth (typically 6 to 20).
- Keep each line short and clear.
- Do not number lines.
- Do not add headings or explanations.
""".strip()

        raw = self.client.generate(prompt)
        subtopics = self._extract_lines(raw)
        if len(subtopics) >= 4:
            return subtopics

        secondary_prompt = f"""
You are an expert interview preparation coach.
Create a variable-length list of clean subtopics for topic: {topic}

Return STRICT JSON only:
{{
  "subtopics": [
    "subtopic 1",
    "subtopic 2"
  ]
}}

Rules:
- The list size should fit the topic naturally (not fixed).
- Keep names concise and interview-relevant.
""".strip()

        secondary_raw = self.client.generate(secondary_prompt)
        parsed = self.client.safe_json_loads(secondary_raw)
        if parsed and isinstance(parsed.get("subtopics"), list):
            json_subtopics = self._normalize_list(parsed.get("subtopics"))
            if len(json_subtopics) >= 4:
                return json_subtopics

        return self._fallback_subtopics(topic)

    def generate_lessons(self, topic, subtopic):
        prompt = f"""
You are a personal coach creating lesson modules.
Topic: {topic}
Subtopic: {subtopic}

Return STRICT JSON only:
{{
  "lessons": ["Lesson title 1", "Lesson title 2"]
}}

Rules:
- Generate a clean list of lesson titles within this subtopic.
- Count can vary naturally based on complexity (typically 6 to 15).
- Keep titles concise and practical.
- Do not add numbering or descriptions.
""".strip()

        raw = self.client.generate(prompt)
        parsed = self.client.safe_json_loads(raw)
        if parsed and isinstance(parsed.get("lessons"), list):
            lessons = self._normalize_list(parsed.get("lessons"))
            if len(lessons) >= 3:
                return lessons

        return [
            f"Introduction to {subtopic}",
            f"Core Concepts of {subtopic}",
            f"Syntax and Rules in {subtopic}",
            f"Practical Usage of {subtopic}",
            f"Common Mistakes in {subtopic}",
            f"Debugging {subtopic}",
            f"Testing {subtopic}",
            f"Interview Practice on {subtopic}",
        ]

    def generate_lesson_and_question(self, topic, subtopic, lesson):
        prompt = f"""
You are a strict but helpful personal coach.
Topic: {topic}
Subtopic: {subtopic}
Lesson: {lesson}

Return STRICT JSON with this schema:
{{
  "lesson": "4-6 paragraphs teaching this lesson clearly with practical examples. Format as Markdown: use **bold** for emphasis, `code snippets` for inline code, and triple backticks (```language) for code blocks. Use headers like ### for subsections if helpful.",
  "question": "one meaningful practice question specific to this lesson. If topic is coding-related, ask a coding problem."
}}

Important formatting rules for lesson:
- Use **bold text** for key concepts
- Wrap code snippets inline with single backticks: `variable_name`, `ClassName`, `method()`
- For code blocks, use triple backticks with language identifier - e.g., ```python, ```java, ```javascript
- Use bullet points or numbered lists where appropriate
- Use headers (###) to organize complex concepts
- Keep markdown clean and readable
""".strip()

        raw = self.client.generate(prompt)
        parsed = self.client.safe_json_loads(raw)
        lesson_text = str(parsed.get("lesson", "")).strip() if parsed else ""
        question = str(parsed.get("question", "")).strip() if parsed else ""

        if lesson_text and question:
            return lesson_text, question

        fallback_lesson = (
            f"### Let's learn {lesson} in {topic}\n\n"
            f"Start with the **core definition**, then connect it to why it matters in **production**. "
            f"Map the inputs, outputs, and failure points. Use one concrete example and one edge case.\n\n"
            f"```\n"
            f"// Example code would go here\n"
            f"```"
        )
        fallback_question = (
            f"Solve one practical problem for lesson '{lesson}' under subtopic '{subtopic}' in {topic}. "
            f"Explain approach, tradeoffs, and edge cases."
        )
        return fallback_lesson, fallback_question

    def evaluate_answer(self, topic, subtopic, question, answer):
        prompt = f"""
You are evaluating a learner response.
Topic: {topic}
Subtopic: {subtopic}
Question: {question}
Answer: {answer}

Return STRICT JSON:
{{
  "score": 0-100 integer,
  "strengths": ["string"],
  "gaps": ["string"],
  "feedback": "2-4 sentences of direct coaching",
  "follow_up_question": "one question for remediation if score is low",
  "next_subtopic": "one suggested next subtopic if score is high"
}}
""".strip()

        raw = self.client.generate(prompt)
        parsed = self.client.safe_json_loads(raw)

        if not parsed:
            return {
                "score": 55,
                "strengths": ["You attempted the answer."],
                "gaps": ["The response needs more structure and specifics."],
                "feedback": "Explain the concept definition, a practical example, and one tradeoff.",
                "follow_up_question": f"Can you explain {subtopic} with a concrete production example?",
                "next_subtopic": f"Advanced {subtopic}",
            }

        score = self._safe_score(parsed.get("score"))
        strengths = self._normalize_list(parsed.get("strengths"))
        gaps = self._normalize_list(parsed.get("gaps"))
        feedback = str(parsed.get("feedback", "")).strip() or (
            "Improve clarity, include practical reasoning, and cover edge cases."
        )
        follow_up_question = str(parsed.get("follow_up_question", "")).strip() or (
            f"Explain {subtopic} with one concrete example and one tradeoff."
        )
        next_subtopic = str(parsed.get("next_subtopic", "")).strip() or f"Advanced {subtopic}"

        return {
            "score": score,
            "strengths": strengths,
            "gaps": gaps,
            "feedback": feedback,
            "follow_up_question": follow_up_question,
            "next_subtopic": next_subtopic,
        }

    def explain_query(self, topic, subtopic, learner_question, current_question=""):
        prompt = f"""
You are a personal coach helping a trainee.
Topic: {topic}
Subtopic: {subtopic or 'General'}
Current coaching question context: {current_question or 'Not provided'}
Learner asked: {learner_question}

Instructions:
- Explain the term/syntax clearly in context of the topic.
- If the learner asks coding syntax, include a short practical code example.
- Keep it concise and practical (max 180 words).
- End with one quick check question.
""".strip()

        raw = self.client.generate(prompt)
        if raw and raw.strip():
            return raw.strip()

        fallback = (
            f"In {topic}, '{learner_question}' should be understood through this subtopic: {subtopic or 'general context'}. "
            "Focus on what it does, when to use it, and common mistakes.\n\n"
            "Example (syntax style):\n"
            "```python\n"
            "cache = {}\n"
            "if key in cache:\n"
            "    return cache[key]\n"
            "cache[key] = compute_value()\n"
            "return cache[key]\n"
            "```\n\n"
            "Quick check: when would this approach fail or need invalidation?"
        )
        return fallback

    @staticmethod
    def _extract_lines(raw_text):
        if not raw_text:
            return []

        lines = []
        for line in raw_text.splitlines():
            cleaned = line.strip()
            if not cleaned:
                continue
            cleaned = re.sub(r"^[-*\d\.)\s]+", "", cleaned).strip()
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
    def _safe_score(value):
        try:
            score = int(value)
        except (TypeError, ValueError):
            score = 55
        return max(0, min(score, 100))

    @staticmethod
    def _normalize_list(value):
        if not value:
            return []
        if isinstance(value, list):
            return [str(item).strip() for item in value if str(item).strip()]
        if isinstance(value, str):
            cleaned = value.strip()
            return [cleaned] if cleaned else []
        return [str(value)]

    @staticmethod
    def _fallback_subtopics(topic):
        topic_lower = topic.lower()

        technical_keywords = [
            "python",
            "javascript",
            "typescript",
            "java",
            "c",
            "c++",
            "c#",
            "coding",
            "programming",
            "algorithm",
            "react",
            "angular",
            "vue",
            "node",
            "django",
            "flask",
            "spring",
            "api",
            "css",
            "html",
            "frontend",
            "backend",
            "database",
            "sql",
            "system design",
            "architecture",
            "microservice",
            "distributed",
            "cloud",
            "devops",
        ]

        if any(keyword in topic_lower for keyword in technical_keywords):
            return [
                f"Fundamentals of {topic}",
                f"Core Syntax and Language Features in {topic}",
                f"Problem Solving Patterns in {topic}",
                f"Performance and Optimization in {topic}",
                f"Debugging and Troubleshooting in {topic}",
                f"Testing Strategies for {topic}",
                f"Production Use Cases of {topic}",
                f"Advanced Concepts in {topic}",
            ]

        if any(keyword in topic_lower for keyword in ["music", "sangeet", "carnatic", "raga", "tala", "violin", "vocal"]):
            return [
                f"Foundations of {topic}",
                f"Core Terminology in {topic}",
                f"Practice Methods for {topic}",
                f"Performance Techniques in {topic}",
                f"Common Mistakes in {topic}",
                f"Advanced Interpretation in {topic}",
            ]

        return [
            f"Fundamentals of {topic}",
            f"Core Concepts in {topic}",
            f"Practical Applications of {topic}",
            f"Common Mistakes in {topic}",
            f"Advanced Topics in {topic}",
            f"Interview Preparation for {topic}",
        ]
