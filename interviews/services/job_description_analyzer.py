import re
from datetime import date, datetime

from .llm_client import LLMClient


class JobDescriptionAnalyzerService:
    def __init__(self):
        self.client = LLMClient()

    def analyze(self, job_description):
        prompt = f"""
You are an expert hiring strategist and compensation advisor.
Analyze this Job Description and return STRICT JSON only.

Job Description:
{job_description}

Required JSON schema:
{{
  "recruiter_intent": "string",
  "skill_tiers": {{
    "strong_match": ["string"],
    "okay_match": ["string"],
    "low_priority": ["string"]
  }},
  "disclosed_salary": {{
    "found": true,
    "currency": "INR",
    "minimum": 12,
    "maximum": 18,
    "unit": "LPA",
    "raw_text": "string"
  }},
  "market_salary_estimate": {{
    "role_focus": "string",
    "demandable_min": 14,
    "demandable_max": 22,
    "unit": "LPA",
    "confidence": "low|medium|high",
    "reasoning": ["string"]
  }},
  "recommendations": ["string"],
  "encouragement": "string"
}}

Rules:
- Infer recruiter intent from outcomes and priorities in the JD.
- Use practical skill grouping based on must-have vs nice-to-have language.
- If salary is not disclosed, set disclosed_salary.found = false and minimum/maximum as null.
- Keep recommendations specific and action-oriented.
- Keep encouragement short, positive, and realistic.
""".strip()

        raw = self.client.generate(prompt)
        parsed = self.client.safe_json_loads(raw)

        salary_info = self._extract_salary_info(job_description)
        seniority = self._infer_seniority(job_description)
        detected_skills = self._extract_skills(job_description)

        if parsed:
            return self._normalize_output(parsed, salary_info, seniority, detected_skills)

        return self._fallback_analysis(job_description, salary_info, seniority, detected_skills)

    def extract_application_context(self, job_description):
        normalized = " ".join(job_description.split())

        recruiter_name = ""
        company_name = ""
        application_last_date = None
        application_last_date_raw = ""

        recruiter_match = re.search(
            r"(?:recruiter|hiring\s*manager|contact\s*person)\s*[:\-]\s*"
            r"([A-Za-z][A-Za-z\s\.]{1,80}?)(?=\.|,\s*(?:apply|deadline|last\s*date|company|organization|employer)|$)",
            normalized,
            re.IGNORECASE,
        )
        if recruiter_match:
            recruiter_name = recruiter_match.group(1).strip(" .,")

        company_match = re.search(
            r"(?:company|organization|employer)\s*[:\-]\s*"
            r"([A-Za-z0-9][A-Za-z0-9\s&\.,\-]{1,100}?)(?=\.|,\s*(?:recruiter|apply|deadline|last\s*date)|$)",
            normalized,
            re.IGNORECASE,
        )
        if company_match:
            company_name = company_match.group(1).strip(" .,")

        date_match = re.search(
            r"(?:last\s*date|apply\s*by|application\s*deadline|deadline)\s*[:\-]?\s*"
            r"([0-3]?\d[\-/][01]?\d[\-/](?:\d{2}|\d{4})|[A-Za-z]{3,9}\s+[0-3]?\d,?\s+\d{4})",
            normalized,
            re.IGNORECASE,
        )
        if date_match:
            application_last_date_raw = date_match.group(1).strip()
            application_last_date = self._parse_date(application_last_date_raw)

        return {
            "recruiter_name": recruiter_name,
            "company_name": company_name,
            "application_last_date": application_last_date,
            "application_last_date_raw": application_last_date_raw,
        }

    def _normalize_output(self, parsed, salary_info, seniority, detected_skills):
        recruiter_intent = str(parsed.get("recruiter_intent", "")).strip() or self._default_intent(seniority)

        skill_tiers = parsed.get("skill_tiers") if isinstance(parsed.get("skill_tiers"), dict) else {}
        strong_match = self._normalize_list(skill_tiers.get("strong_match"))
        okay_match = self._normalize_list(skill_tiers.get("okay_match"))
        low_priority = self._normalize_list(skill_tiers.get("low_priority"))

        if not strong_match and detected_skills:
            strong_match = detected_skills[:6]
        if not okay_match and len(detected_skills) > 6:
            okay_match = detected_skills[6:12]

        model_salary = parsed.get("disclosed_salary") if isinstance(parsed.get("disclosed_salary"), dict) else {}
        disclosed_salary = {
            "found": bool(model_salary.get("found", salary_info["found"])),
            "currency": str(model_salary.get("currency") or salary_info["currency"]),
            "minimum": self._safe_number(model_salary.get("minimum"), salary_info["minimum"]),
            "maximum": self._safe_number(model_salary.get("maximum"), salary_info["maximum"]),
            "unit": str(model_salary.get("unit") or salary_info["unit"]),
            "raw_text": str(model_salary.get("raw_text") or salary_info["raw_text"]),
        }

        market_salary = self._normalize_market_salary(
            parsed.get("market_salary_estimate"),
            disclosed_salary,
            seniority,
            detected_skills,
        )

        recommendations = self._normalize_list(parsed.get("recommendations"))
        if not recommendations:
            recommendations = self._default_recommendations(seniority, detected_skills)

        encouragement = str(parsed.get("encouragement", "")).strip() or (
            "You are already close. With focused preparation around the top must-have skills, "
            "you can position yourself strongly for this role."
        )

        return {
            "recruiter_intent": recruiter_intent,
            "skill_tiers": {
                "strong_match": strong_match,
                "okay_match": okay_match,
                "low_priority": low_priority,
            },
            "disclosed_salary": disclosed_salary,
            "market_salary_estimate": market_salary,
            "recommendations": recommendations,
            "encouragement": encouragement,
        }

    def _fallback_analysis(self, job_description, salary_info, seniority, detected_skills):
        strong_match = detected_skills[:6]
        okay_match = detected_skills[6:12]

        demand_min, demand_max, confidence, reasoning = self._estimate_market_salary(
            disclosed_salary=salary_info,
            seniority=seniority,
            skills_count=len(detected_skills),
        )

        return {
            "recruiter_intent": self._default_intent(seniority),
            "skill_tiers": {
                "strong_match": strong_match,
                "okay_match": okay_match,
                "low_priority": ["Nice-to-have certifications", "Tooling familiarity", "Domain exposure"],
            },
            "disclosed_salary": salary_info,
            "market_salary_estimate": {
                "role_focus": f"{seniority.title()} role with delivery ownership",
                "demandable_min": demand_min,
                "demandable_max": demand_max,
                "unit": "LPA",
                "confidence": confidence,
                "reasoning": reasoning,
            },
            "recommendations": self._default_recommendations(seniority, detected_skills),
            "encouragement": (
                "You have a clear path. Prioritize the strongest JD signals, prepare quantified examples, "
                "and negotiate from impact."
            ),
        }

    def _normalize_market_salary(self, raw_market_salary, disclosed_salary, seniority, detected_skills):
        if not isinstance(raw_market_salary, dict):
            raw_market_salary = {}

        fallback_min, fallback_max, fallback_confidence, fallback_reasoning = self._estimate_market_salary(
            disclosed_salary=disclosed_salary,
            seniority=seniority,
            skills_count=len(detected_skills),
        )

        demandable_min = self._safe_number(raw_market_salary.get("demandable_min"), fallback_min)
        demandable_max = self._safe_number(raw_market_salary.get("demandable_max"), fallback_max)

        if demandable_min > demandable_max:
            demandable_min, demandable_max = demandable_max, demandable_min

        confidence = str(raw_market_salary.get("confidence", fallback_confidence)).strip().lower()
        if confidence not in {"low", "medium", "high"}:
            confidence = fallback_confidence

        reasoning = self._normalize_list(raw_market_salary.get("reasoning"))
        if not reasoning:
            reasoning = fallback_reasoning

        return {
            "role_focus": str(raw_market_salary.get("role_focus", "")).strip()
            or f"{seniority.title()} role with practical execution focus",
            "demandable_min": demandable_min,
            "demandable_max": demandable_max,
            "unit": str(raw_market_salary.get("unit") or disclosed_salary.get("unit") or "LPA"),
            "confidence": confidence,
            "reasoning": reasoning,
        }

    @staticmethod
    def _extract_salary_info(text):
        normalized = " ".join(text.split())

        range_pattern = re.compile(
            r"(?:ctc|salary|compensation|package)?\s*(?:[:\-])?\s*"
            r"(\d+(?:\.\d+)?)\s*(?:-|to)\s*(\d+(?:\.\d+)?)\s*(lpa|lakhs?|inr|₹|per annum|pa)?",
            re.IGNORECASE,
        )
        single_pattern = re.compile(
            r"(?:ctc|salary|compensation|package)?\s*(?:[:\-])?\s*"
            r"(\d+(?:\.\d+)?)\s*(lpa|lakhs?|inr|₹|per annum|pa)",
            re.IGNORECASE,
        )

        match = range_pattern.search(normalized)
        if match:
            unit = JobDescriptionAnalyzerService._normalize_unit(match.group(3))
            raw_text = match.group(0)
            minimum = float(match.group(1))
            maximum = float(match.group(2))
            if minimum > maximum:
                minimum, maximum = maximum, minimum
            return {
                "found": True,
                "currency": "INR",
                "minimum": round(minimum, 2),
                "maximum": round(maximum, 2),
                "unit": unit,
                "raw_text": raw_text,
            }

        match = single_pattern.search(normalized)
        if match:
            value = float(match.group(1))
            unit = JobDescriptionAnalyzerService._normalize_unit(match.group(2))
            return {
                "found": True,
                "currency": "INR",
                "minimum": round(value, 2),
                "maximum": round(value, 2),
                "unit": unit,
                "raw_text": match.group(0),
            }

        return {
            "found": False,
            "currency": "INR",
            "minimum": None,
            "maximum": None,
            "unit": "LPA",
            "raw_text": "",
        }

    @staticmethod
    def _normalize_unit(unit):
        if not unit:
            return "LPA"
        normalized = unit.lower().strip()
        if normalized in {"lpa", "lakh", "lakhs"}:
            return "LPA"
        if normalized in {"per annum", "pa", "inr", "₹"}:
            return "LPA"
        return unit.upper()

    @staticmethod
    def _infer_seniority(text):
        lowered = text.lower()
        if any(token in lowered for token in ["intern", "internship"]):
            return "intern"
        if any(token in lowered for token in ["lead", "principal", "architect", "staff"]):
            return "lead"
        if any(token in lowered for token in ["senior", "5+ years", "6+ years", "7+ years", "8+ years"]):
            return "senior"
        if any(token in lowered for token in ["3+ years", "4+ years", "mid", "intermediate"]):
            return "mid"
        return "junior"

    @staticmethod
    def _extract_skills(text):
        catalog = [
            "python",
            "java",
            "javascript",
            "typescript",
            "angular",
            "react",
            "node",
            "django",
            "flask",
            "spring boot",
            "microservices",
            "system design",
            "sql",
            "postgresql",
            "mysql",
            "redis",
            "aws",
            "azure",
            "gcp",
            "docker",
            "kubernetes",
            "ci/cd",
            "testing",
            "communication",
            "problem solving",
            "leadership",
        ]

        lowered = text.lower()
        detected = []
        for item in catalog:
            if item in lowered:
                detected.append(item.title())

        unique = []
        seen = set()
        for item in detected:
            key = item.lower()
            if key not in seen:
                seen.add(key)
                unique.append(item)
        return unique

    def _estimate_market_salary(self, disclosed_salary, seniority, skills_count):
        base_ranges = {
            "intern": (3, 6),
            "junior": (6, 12),
            "mid": (12, 22),
            "senior": (22, 38),
            "lead": (35, 55),
        }
        base_min, base_max = base_ranges.get(seniority, (8, 16))

        skill_bonus = min(skills_count, 10) * 0.5
        base_min += skill_bonus
        base_max += skill_bonus

        confidence = "medium"
        reasoning = [
            f"Role appears {seniority} based on JD language.",
            "Demand estimate adjusted by breadth of required skills.",
        ]

        if disclosed_salary.get("found"):
            disclosed_min = disclosed_salary.get("minimum") or base_min
            disclosed_max = disclosed_salary.get("maximum") or base_max
            demand_min = max(base_min, disclosed_min * 1.08)
            demand_max = max(base_max, disclosed_max * 1.2)
            confidence = "high"
            reasoning.append("Salary was disclosed, so negotiation band is calibrated from posted compensation.")
        else:
            demand_min = base_min
            demand_max = base_max
            confidence = "medium"
            reasoning.append("No disclosed salary; estimate uses seniority and skill-demand benchmarks.")

        return round(demand_min, 2), round(demand_max, 2), confidence, reasoning

    def _default_intent(self, seniority):
        if seniority in {"senior", "lead"}:
            return (
                "Recruiter is prioritizing hands-on ownership, architecture decisions, and consistent delivery "
                "in production-scale environments."
            )
        return (
            "Recruiter is looking for reliable execution on core responsibilities, strong fundamentals, "
            "and evidence of growth potential."
        )

    @staticmethod
    def _default_recommendations(seniority, detected_skills):
        top_skills = detected_skills[:3]
        focus_line = (
            f"Prepare quantified project stories for {', '.join(top_skills)}."
            if top_skills
            else "Prepare 2-3 quantified project stories aligned to the JD must-haves."
        )
        return [
            focus_line,
            "Build a gap-closure plan for any missing must-have skills with a 30-day timeline.",
            "Use STAR-format answers and connect every response to business impact.",
            (
                "Negotiate with evidence: market range, outcomes delivered, and scope of responsibility."
                if seniority in {"mid", "senior", "lead"}
                else "Demonstrate fast learning and reliability with concrete examples from projects or internships."
            ),
        ]

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
    def _safe_number(value, fallback):
        if value is None:
            return fallback
        try:
            return round(float(value), 2)
        except (TypeError, ValueError):
            return fallback

    @staticmethod
    def _parse_date(raw_value):
        if not raw_value:
            return None

        formats = [
            "%d/%m/%Y",
            "%d-%m-%Y",
            "%d/%m/%y",
            "%d-%m-%y",
            "%b %d %Y",
            "%B %d %Y",
            "%b %d, %Y",
            "%B %d, %Y",
        ]

        normalized = raw_value.replace(",", ", ").replace("  ", " ").strip()
        for fmt in formats:
            try:
                parsed = datetime.strptime(normalized, fmt)
                return date(parsed.year, parsed.month, parsed.day)
            except ValueError:
                continue
        return None
