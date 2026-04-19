import json
import logging
import time

import requests
from django.conf import settings


logger = logging.getLogger(__name__)


class LLMClient:
    def __init__(self):
        self.api_key = settings.LLM_API_KEY
        self.model = settings.LLM_MODEL
        self.base_url = settings.LLM_BASE_URL

    def generate(self, prompt):
        if not self.api_key:
            return ""

        retries = max(settings.LLM_MAX_RETRIES, 0)
        for attempt in range(retries + 1):
            try:
                response = requests.post(
                    self.base_url,
                    headers={
                        "Authorization": f"Bearer {self.api_key}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": self.model,
                        "messages": [{"role": "user", "content": prompt}],
                        "temperature": 0.4,
                    },
                    timeout=settings.LLM_REQUEST_TIMEOUT,
                )
                response.raise_for_status()
                body = response.json()

                content = body["choices"][0]["message"]["content"]
                if isinstance(content, str):
                    return content
                return ""
            except (requests.RequestException, KeyError, TypeError, ValueError) as exc:
                logger.warning("LLM request failed on attempt %s: %s", attempt + 1, exc)
                if attempt < retries:
                    sleep_seconds = settings.LLM_RETRY_BACKOFF_SECONDS * (attempt + 1)
                    time.sleep(sleep_seconds)

        return ""

    @staticmethod
    def safe_json_loads(raw_text):
        if not raw_text:
            return {}
        try:
            return json.loads(raw_text)
        except json.JSONDecodeError:
            return {}
