from .llm_client import LLMClient


class UserAspirationService:
    def __init__(self):
        self.client = LLMClient()

    def generate_roadmap(
        self,
        current_position,
        target_job,
        timeline_months,
        current_skills,
        constraints="",
        additional_context="",
    ):
        skills_text = ", ".join(current_skills) if current_skills else "Not specified"
        prompt = f"""
You are a senior career coach and hiring strategist.
Create a practical roadmap to move from current role to target role.

Current position: {current_position}
Target job: {target_job}
Timeline months: {timeline_months}
Current skills: {skills_text}
Constraints: {constraints or 'None provided'}
Additional context: {additional_context or 'None provided'}

Return STRICT JSON only using this schema:
{{
  "summary": "2-4 sentence realistic summary",
  "readiness_score": 0-100 integer,
  "gap_analysis": ["string"],
  "roadmap_phases": [
    {{
      "phase": "string",
      "duration": "string",
      "focus": "string",
      "actions": ["string"],
      "deliverables": ["string"]
    }}
  ],
  "weekly_execution": ["string"],
  "interview_preparation": ["string"],
  "encouragement": "short motivational message"
}}

Rules:
- Keep advice specific to target job outcomes.
- Balance learning, projects, interview prep, and networking.
- Respect constraints and timeline.
- Be realistic and practical.
""".strip()

        raw = self.client.generate(prompt)
        parsed = self.client.safe_json_loads(raw)
        if parsed:
            return self._normalize(parsed, target_job, timeline_months)

        return self._fallback(current_position, target_job, timeline_months, current_skills)

    def _normalize(self, parsed, target_job, timeline_months):
        summary = str(parsed.get("summary", "")).strip() or (
            f"You can transition to {target_job} with structured execution over {timeline_months} months."
        )

        readiness_score = self._safe_score(parsed.get("readiness_score"))
        gap_analysis = self._normalize_list(parsed.get("gap_analysis"))
        phases = parsed.get("roadmap_phases") if isinstance(parsed.get("roadmap_phases"), list) else []

        normalized_phases = []
        for item in phases:
            if not isinstance(item, dict):
                continue
            normalized_phases.append(
                {
                    "phase": str(item.get("phase", "Phase")).strip() or "Phase",
                    "duration": str(item.get("duration", "2-4 weeks")).strip() or "2-4 weeks",
                    "focus": str(item.get("focus", "Core preparation")).strip() or "Core preparation",
                    "actions": self._normalize_list(item.get("actions")),
                    "deliverables": self._normalize_list(item.get("deliverables")),
                }
            )

        if not normalized_phases:
            normalized_phases = self._fallback_phases(target_job, timeline_months)

        weekly_execution = self._normalize_list(parsed.get("weekly_execution"))
        if not weekly_execution:
            weekly_execution = [
                "Plan weekly goals every Sunday and track completion daily.",
                "Spend 5 focused sessions on skill-building and 2 sessions on interview prep.",
                "Review progress weekly and adjust next week based on blockers.",
            ]

        interview_preparation = self._normalize_list(parsed.get("interview_preparation"))
        if not interview_preparation:
            interview_preparation = [
                "Prepare 8-10 STAR stories with measurable impact.",
                "Practice role-specific mock interviews twice a week.",
                "Maintain a concise resume tailored to each job description.",
            ]

        encouragement = str(parsed.get("encouragement", "")).strip() or (
            "You are closer than you think. Consistency over the next few months can create a strong transition."
        )

        return {
            "summary": summary,
            "readiness_score": readiness_score,
            "gap_analysis": gap_analysis,
            "roadmap_phases": normalized_phases,
            "weekly_execution": weekly_execution,
            "interview_preparation": interview_preparation,
            "encouragement": encouragement,
        }

    def _fallback(self, current_position, target_job, timeline_months, current_skills):
        skills_preview = ", ".join(current_skills[:4]) if current_skills else "your current strengths"
        return {
            "summary": (
                f"Moving from {current_position} to {target_job} is realistic in {timeline_months} months with "
                f"disciplined execution and portfolio evidence built around {skills_preview}."
            ),
            "readiness_score": 58,
            "gap_analysis": [
                "Need stronger proof of role-specific project impact.",
                "Need consistent interview storytelling and mock practice.",
                "Need targeted applications and referral-led outreach.",
            ],
            "roadmap_phases": self._fallback_phases(target_job, timeline_months),
            "weekly_execution": [
                "Allocate 8-12 hours weekly for roadmap execution.",
                "Track outcomes weekly: skills learned, project milestones, and interview readiness.",
                "Run one mock interview and one application sprint each week.",
            ],
            "interview_preparation": [
                "Prepare concise role-fit pitch for recruiter screening.",
                "Practice technical/domain questions based on target role.",
                "Refine resume and LinkedIn every 2 weeks with quantified outcomes.",
            ],
            "encouragement": (
                "Your target is achievable. Stay consistent, measure progress weekly, and keep shipping visible outcomes."
            ),
        }

    @staticmethod
    def _fallback_phases(target_job, timeline_months):
        phase_len = max(1, timeline_months // 3)
        return [
            {
                "phase": "Foundation and gap closure",
                "duration": f"{phase_len} month(s)",
                "focus": f"Build core capabilities required for {target_job}",
                "actions": [
                    "Map must-have skills from 20 real job descriptions.",
                    "Create a focused learning plan and complete high-impact topics.",
                ],
                "deliverables": [
                    "Skill-gap matrix",
                    "Documented study notes and mini artifacts",
                ],
            },
            {
                "phase": "Portfolio and proof building",
                "duration": f"{phase_len} month(s)",
                "focus": "Develop evidence of execution and outcomes",
                "actions": [
                    "Build 1-2 role-aligned projects with measurable results.",
                    "Publish project summaries and architecture/decision notes.",
                ],
                "deliverables": [
                    "Project portfolio links",
                    "Impact-focused case study writeups",
                ],
            },
            {
                "phase": "Interview conversion sprint",
                "duration": f"{max(1, timeline_months - (2 * phase_len))} month(s)",
                "focus": "Convert interviews to offers",
                "actions": [
                    "Run mock interviews and iterate based on weak areas.",
                    "Apply strategically with referral-first outreach.",
                ],
                "deliverables": [
                    "Interview Q&A bank",
                    "Target-company application tracker",
                ],
            },
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
    def _safe_score(value):
        try:
            score = int(value)
        except (TypeError, ValueError):
            score = 58
        return max(0, min(score, 100))
