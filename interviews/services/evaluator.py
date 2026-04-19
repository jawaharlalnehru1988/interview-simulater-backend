from .llm_client import LLMClient


class Evaluator:
    def __init__(self):
        self.client = LLMClient()

    def evaluate(self, question, answer):
        prompt = f"""
Evaluate this interview answer and return strict JSON.

Question: {question}
Answer: {answer}

Output schema:
{{
  \"score\": 0-100 integer,
  \"strengths\": [string],
  \"weaknesses\": [string],
  \"improvements\": [string]
}}
""".strip()

        raw = self.client.generate(prompt)
        parsed = self.client.safe_json_loads(raw)

        if not parsed:
            return {
                "score": 50,
                "strengths": ["Attempted the question"],
                "weaknesses": ["Could not parse model evaluation"],
                "improvements": ["Provide a more structured response"],
            }

        score = parsed.get("score", 50)
        try:
            score = int(score)
        except (TypeError, ValueError):
            score = 50
        score = max(0, min(score, 100))

        return {
            "score": score,
            "strengths": self._normalize_list(parsed.get("strengths", [])),
            "weaknesses": self._normalize_list(parsed.get("weaknesses", [])),
            "improvements": self._normalize_list(parsed.get("improvements", [])),
        }

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
