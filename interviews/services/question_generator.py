import re

from .llm_client import LLMClient


class QuestionGenerator:
    def __init__(self):
        self.client = LLMClient()

    def generate(self, topic, difficulty, round_type):
        if round_type == "mcq":
            prompt = f"""
Generate exactly one {difficulty} multiple-choice interview question on: {topic}.
Return in this exact format with four options and no explanation:
Question: <question text>
A) <option A>
B) <option B>
C) <option C>
D) <option D>
""".strip()
        else:
            prompt = f"""
Generate exactly one concise {difficulty} {round_type} interview question on the topic: {topic}.
Only return the question text.
""".strip()

        generated = self.client.generate(prompt)
        if generated:
            cleaned = generated.strip()
            if round_type == "mcq" and len(self.extract_mcq_options(cleaned)) < 2:
                return self._fallback_mcq_question(topic)
            return cleaned

        if round_type == "mcq":
            return self._fallback_mcq_question(topic)
        return f"Explain a key concept in {topic} and how you would apply it in production."

    def generate_suggested_answer(self, question_text, round_type, mcq_options=None):
        options_block = ""
        if mcq_options:
            options_block = "\n".join(mcq_options)

        if round_type == "mcq":
            prompt = f"""
You are helping a candidate review a multiple-choice interview question.

Question:
{question_text}

Options:
{options_block}

Return only one concise line in this exact format:
Correct option: <A|B|C|D>) <option text> - <one-sentence reason>
""".strip()
        else:
            prompt = f"""
You are helping a candidate who is stuck in an interview.

Question:
{question_text}

Return a concise model answer in 3-6 lines that can be studied quickly.
""".strip()

        generated = self.client.generate(prompt)
        if isinstance(generated, str) and generated.strip():
            return generated.strip()

        return self._fallback_suggested_answer(question_text, round_type, mcq_options or [])

    @staticmethod
    def extract_mcq_options(question_text):
        options = []
        for line in question_text.splitlines():
            line = line.strip()
            match = re.match(r"^([A-D])[\)\.:\-]\s*(.+)$", line, flags=re.IGNORECASE)
            if match:
                label = match.group(1).upper()
                value = match.group(2).strip()
                if value:
                    options.append(f"{label}) {value}")
        return options

    @staticmethod
    def _fallback_mcq_question(topic):
        return (
            f"Question: Which choice best represents a key goal in {topic}?\n"
            "A) Increase accidental complexity\n"
            "B) Improve reliability and maintainability\n"
            "C) Remove all monitoring\n"
            "D) Avoid scalability planning"
        )

    @staticmethod
    def _fallback_suggested_answer(question_text, round_type, mcq_options):
        if round_type == "mcq":
            if mcq_options:
                return f"Correct option: {mcq_options[0]} - Review this option and compare each distractor carefully before finalizing."
            return "Correct option: A) Not enough options provided - review core concepts and eliminate clearly wrong choices first."

        return (
            "Define the concept briefly, explain why it matters in real systems, "
            "cover one tradeoff, and finish with a practical production example."
        )
