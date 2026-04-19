from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.urls import reverse
from rest_framework import status
from rest_framework.test import APITestCase

from .models import Interview
from .services.personal_coach import PersonalCoachService


class InterviewFlowApiTests(APITestCase):
    def setUp(self):
        self.username = "candidate1"
        self.password = "candidatePass123"
        self.user = get_user_model().objects.create_user(
            username=self.username,
            password=self.password,
        )

    def _get_access_token(self):
        response = self.client.post(
            reverse("token_obtain_pair"),
            {"username": self.username, "password": self.password},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        return response.data["access"]

    def test_register_and_token_endpoints(self):
        register_response = self.client.post(
            reverse("register"),
            {
                "username": "newcandidate",
                "password": "newcandidatePass123",
                "email": "candidate@example.com",
            },
            format="json",
        )
        self.assertEqual(register_response.status_code, status.HTTP_201_CREATED)

        token_response = self.client.post(
            reverse("token_obtain_pair"),
            {
                "username": "newcandidate",
                "password": "newcandidatePass123",
            },
            format="json",
        )
        self.assertEqual(token_response.status_code, status.HTTP_200_OK)
        self.assertIn("access", token_response.data)
        self.assertIn("refresh", token_response.data)

    @patch("interviews.views.QuestionGenerator.generate", return_value="Explain idempotency in APIs.")
    @patch(
        "interviews.views.QuestionGenerator.generate_suggested_answer",
        return_value="Use idempotency keys with request deduplication and safe retries.",
    )
    @patch(
        "interviews.views.Evaluator.evaluate",
        return_value={
            "score": 82,
            "strengths": ["Good structure"],
            "weaknesses": ["Missing edge cases"],
            "improvements": ["Discuss retries and deduplication"],
        },
    )
    def test_full_interview_flow(self, _mock_evaluate, _mock_generate_suggested_answer, _mock_generate):
        access_token = self._get_access_token()
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {access_token}")

        start_response = self.client.post(
            reverse("start_interview"),
            {"topic": "Backend Design", "round": "technical"},
            format="json",
        )
        self.assertEqual(start_response.status_code, status.HTTP_201_CREATED)
        interview_id = start_response.data["interview_id"]

        next_question_response = self.client.get(reverse("next_question", args=[interview_id]))
        self.assertEqual(next_question_response.status_code, status.HTTP_200_OK)
        self.assertIn("suggested_answer", next_question_response.data)
        self.assertTrue(next_question_response.data["suggested_answer"])
        question_id = next_question_response.data["question_id"]

        submit_response = self.client.post(
            reverse("submit_answer"),
            {
                "question_id": question_id,
                "answer": "I would use idempotency keys and a deduplicating write path.",
            },
            format="json",
        )
        self.assertEqual(submit_response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(submit_response.data["evaluation"]["score"], 82)

        summary_response = self.client.get(reverse("interview_summary", args=[interview_id]))
        self.assertEqual(summary_response.status_code, status.HTTP_200_OK)
        self.assertEqual(summary_response.data["interview_id"], interview_id)
        self.assertGreaterEqual(summary_response.data["questions_asked"], 1)
        self.assertGreaterEqual(summary_response.data["evaluations_completed"], 1)

    @patch(
        "interviews.views.QuestionGenerator.generate",
        return_value=(
            "Question: Which is a key system design goal?\n"
            "A) Reliability\n"
            "B) Randomness\n"
            "C) No monitoring\n"
            "D) Single point of failure"
        ),
    )
    @patch(
        "interviews.views.QuestionGenerator.generate_suggested_answer",
        return_value="Correct option: A) Reliability - reliability is a core design objective.",
    )
    def test_mcq_options_are_returned(self, _mock_generate_suggested_answer, _mock_generate):
        access_token = self._get_access_token()
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {access_token}")

        start_response = self.client.post(
            reverse("start_interview"),
            {"topic": "System Design", "round": "mcq"},
            format="json",
        )
        self.assertEqual(start_response.status_code, status.HTTP_201_CREATED)

        interview_id = start_response.data["interview_id"]
        next_question_response = self.client.get(reverse("next_question", args=[interview_id]))
        self.assertEqual(next_question_response.status_code, status.HTTP_200_OK)
        self.assertIn("mcq_options", next_question_response.data)
        self.assertEqual(len(next_question_response.data["mcq_options"]), 4)
        self.assertIn("suggested_answer", next_question_response.data)
        self.assertTrue(next_question_response.data["suggested_answer"])

    def test_interview_is_user_scoped(self):
        access_token = self._get_access_token()
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {access_token}")

        interview = Interview.objects.create(
            user=self.user,
            topic="System Design",
            round_type=Interview.RoundType.TECHNICAL,
        )

        other_user = get_user_model().objects.create_user(
            username="candidate2",
            password="candidatePass456",
        )
        other_token = self.client.post(
            reverse("token_obtain_pair"),
            {"username": other_user.username, "password": "candidatePass456"},
            format="json",
        ).data["access"]

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {other_token}")
        response = self.client.get(reverse("next_question", args=[interview.id]))
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)


class PersonalCoachApiTests(APITestCase):
    def setUp(self):
        self.username = "coachuser"
        self.password = "coachPass123"
        self.user = get_user_model().objects.create_user(
            username=self.username,
            password=self.password,
        )

    def _authenticate(self):
        response = self.client.post(
            reverse("token_obtain_pair"),
            {"username": self.username, "password": self.password},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {response.data['access']}")

    @patch(
        "interviews.views.PersonalCoachService.generate_subtopics",
        return_value=["Caching", "Load Balancing", "Sharding"],
    )
    @patch(
        "interviews.views.PersonalCoachService.generate_lessons",
        return_value=["Intro to Caching", "Cache Invalidation"],
    )
    @patch(
        "interviews.views.PersonalCoachService.generate_lesson_and_question",
        return_value=("Lesson content", "What is caching?"),
    )
    def test_start_and_choose_subtopic(self, _mock_lesson_and_q, _mock_lessons, _mock_subtopics):
        self._authenticate()

        start_response = self.client.post(
            reverse("start_personal_coach"),
            {"topic": "System Design"},
            format="json",
        )
        self.assertEqual(start_response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(start_response.data["subtopics"], ["Caching", "Load Balancing", "Sharding"])

        session_id = start_response.data["session_id"]
        choose_response = self.client.post(
            reverse("choose_personal_coach_subtopic", args=[session_id]),
            {"subtopic": "Caching"},
            format="json",
        )
        self.assertEqual(choose_response.status_code, status.HTTP_200_OK)
        self.assertEqual(choose_response.data["subtopic"], "Caching")
        self.assertEqual(choose_response.data["lessons"], ["Intro to Caching", "Cache Invalidation"])

        lesson_response = self.client.post(
            reverse("choose_personal_coach_lesson", args=[session_id]),
            {"lesson": "Intro to Caching"},
            format="json",
        )
        self.assertEqual(lesson_response.status_code, status.HTTP_200_OK)
        self.assertEqual(lesson_response.data["selected_lesson"], "Intro to Caching")
        self.assertEqual(lesson_response.data["question"], "What is caching?")

    @patch(
        "interviews.views.PersonalCoachService.generate_subtopics",
        return_value=["Caching", "Load Balancing", "Sharding"],
    )
    @patch(
        "interviews.views.PersonalCoachService.generate_lessons",
        return_value=["Intro to Caching", "Cache Invalidation"],
    )
    @patch(
        "interviews.views.PersonalCoachService.generate_lesson_and_question",
        return_value=("Lesson content", "What is caching?"),
    )
    @patch(
        "interviews.views.PersonalCoachService.evaluate_answer",
        side_effect=[
            {
                "score": 62,
                "strengths": ["Basic understanding"],
                "gaps": ["Missing tradeoffs"],
                "feedback": "Add practical tradeoffs.",
                "follow_up_question": "Explain cache invalidation tradeoffs.",
                "next_subtopic": "Cache Invalidation",
            },
            {
                "score": 84,
                "strengths": ["Good depth"],
                "gaps": ["Could mention consistency"],
                "feedback": "Strong answer.",
                "follow_up_question": "",
                "next_subtopic": "Distributed Caching",
            },
        ],
    )
    def test_personal_coach_remediate_then_advance(
        self,
        _mock_evaluate,
        _mock_lesson,
        _mock_lessons,
        _mock_subtopics,
    ):
        self._authenticate()

        start_response = self.client.post(
            reverse("start_personal_coach"),
            {"topic": "System Design"},
            format="json",
        )
        session_id = start_response.data["session_id"]

        self.client.post(
            reverse("choose_personal_coach_subtopic", args=[session_id]),
            {"subtopic": "Caching"},
            format="json",
        )
        self.client.post(
            reverse("choose_personal_coach_lesson", args=[session_id]),
            {"lesson": "Intro to Caching"},
            format="json",
        )

        first_answer = self.client.post(
            reverse("answer_personal_coach_question", args=[session_id]),
            {"answer": "Caching stores data."},
            format="json",
        )
        self.assertEqual(first_answer.status_code, status.HTTP_200_OK)
        self.assertEqual(first_answer.data["coach_decision"], "remediate")
        self.assertIn("next_question", first_answer.data)

        second_answer = self.client.post(
            reverse("answer_personal_coach_question", args=[session_id]),
            {"answer": "Caching balances latency, freshness, and invalidation tradeoffs."},
            format="json",
        )
        self.assertEqual(second_answer.status_code, status.HTTP_200_OK)
        self.assertEqual(second_answer.data["coach_decision"], "advance")
        self.assertEqual(second_answer.data["suggested_next_subtopic"], "Distributed Caching")

    @patch(
        "interviews.views.PersonalCoachService.generate_subtopics",
        return_value=["Caching", "Load Balancing", "Sharding"],
    )
    @patch(
        "interviews.views.PersonalCoachService.generate_lessons",
        return_value=["Intro to Caching", "Cache Invalidation"],
    )
    @patch(
        "interviews.views.PersonalCoachService.generate_lesson_and_question",
        return_value=("Lesson content", "What is caching?"),
    )
    @patch(
        "interviews.views.PersonalCoachService.evaluate_answer",
        return_value={
            "score": 78,
            "strengths": ["Clear structure"],
            "gaps": ["Could include failure mode"],
            "feedback": "Good answer overall.",
            "follow_up_question": "",
            "next_subtopic": "Distributed Caching",
        },
    )
    @patch(
        "interviews.views.QuestionGenerator.generate",
        return_value="Explain database indexing.",
    )
    @patch(
        "interviews.views.Evaluator.evaluate",
        return_value={
            "score": 81,
            "strengths": ["Good explanation"],
            "weaknesses": ["Missing depth"],
            "improvements": ["Add tradeoffs"],
        },
    )
    def test_user_learning_progress_endpoint(
        self,
        _mock_interview_eval,
        _mock_interview_q,
        _mock_coach_eval,
        _mock_lesson,
        _mock_lessons,
        _mock_subtopics,
    ):
        self._authenticate()

        start_interview_response = self.client.post(
            reverse("start_interview"),
            {"topic": "Databases", "round": "technical"},
            format="json",
        )
        interview_id = start_interview_response.data["interview_id"]

        next_question_response = self.client.get(reverse("next_question", args=[interview_id]))
        question_id = next_question_response.data["question_id"]
        self.client.post(
            reverse("submit_answer"),
            {"question_id": question_id, "answer": "Indexes improve read speed."},
            format="json",
        )

        start_coach_response = self.client.post(
            reverse("start_personal_coach"),
            {"topic": "System Design"},
            format="json",
        )
        session_id = start_coach_response.data["session_id"]

        self.client.post(
            reverse("choose_personal_coach_subtopic", args=[session_id]),
            {"subtopic": "Caching"},
            format="json",
        )
        self.client.post(
            reverse("choose_personal_coach_lesson", args=[session_id]),
            {"lesson": "Intro to Caching"},
            format="json",
        )
        self.client.post(
            reverse("answer_personal_coach_question", args=[session_id]),
            {"answer": "Caching helps reduce latency and backend load."},
            format="json",
        )

        progress_response = self.client.get(reverse("user_learning_progress"))
        self.assertEqual(progress_response.status_code, status.HTTP_200_OK)
        self.assertIn("modules", progress_response.data)
        self.assertIn("interview", progress_response.data["modules"])
        self.assertIn("personal_coach", progress_response.data["modules"])
        self.assertIn("Databases", progress_response.data["attempted_topics"]["interview_topics"])
        self.assertIn("System Design", progress_response.data["attempted_topics"]["personal_coach_topics"])
        self.assertGreaterEqual(len(progress_response.data["personal_coach_topic_stats"]), 1)

    @patch(
        "interviews.views.PersonalCoachService.generate_subtopics",
        return_value=["Caching", "Load Balancing", "Sharding"],
    )
    @patch(
        "interviews.views.PersonalCoachService.explain_query",
        return_value="Idempotency means repeating a request has the same effect as doing it once.",
    )
    def test_personal_coach_explainer_endpoint(self, _mock_explain, _mock_subtopics):
        self._authenticate()

        start_response = self.client.post(
            reverse("start_personal_coach"),
            {"topic": "System Design"},
            format="json",
        )
        session_id = start_response.data["session_id"]

        explain_response = self.client.post(
            reverse("explain_personal_coach_query", args=[session_id]),
            {"question": "What is idempotency in APIs?"},
            format="json",
        )
        self.assertEqual(explain_response.status_code, status.HTTP_200_OK)
        self.assertIn("explanation", explain_response.data)
        self.assertIn("idempotency", explain_response.data["explanation"].lower())

    @patch(
        "interviews.views.PersonalCoachService.generate_subtopics",
        return_value=["Caching", "Load Balancing", "Sharding"],
    )
    @patch(
        "interviews.views.PersonalCoachService.generate_lessons",
        return_value=["Intro to Caching", "Cache Invalidation"],
    )
    @patch(
        "interviews.views.PersonalCoachService.generate_lesson_and_question",
        return_value=("Caching lesson", "What is caching?"),
    )
    @patch(
        "interviews.views.PersonalCoachService.evaluate_answer",
        return_value={
            "score": 61,
            "strengths": ["Basic concept"],
            "gaps": ["Missing invalidation tradeoffs"],
            "feedback": "Explain invalidation and consistency.",
            "follow_up_question": "When does cached data become dangerous?",
            "next_subtopic": "Distributed Caching",
        },
    )
    def test_resume_personal_coach_session_returns_saved_state(
        self,
        _mock_eval,
        _mock_lesson,
        _mock_lessons,
        _mock_subtopics,
    ):
        self._authenticate()

        start_response = self.client.post(
            reverse("start_personal_coach"),
            {"topic": "System Design"},
            format="json",
        )
        session_id = start_response.data["session_id"]

        self.client.post(
            reverse("choose_personal_coach_subtopic", args=[session_id]),
            {"subtopic": "Caching"},
            format="json",
        )
        self.client.post(
            reverse("choose_personal_coach_lesson", args=[session_id]),
            {"lesson": "Intro to Caching"},
            format="json",
        )
        self.client.post(
            reverse("answer_personal_coach_question", args=[session_id]),
            {"answer": "Caching stores data closer to reads."},
            format="json",
        )

        resume_response = self.client.get(reverse("resume_personal_coach_session", args=[session_id]))
        self.assertEqual(resume_response.status_code, status.HTTP_200_OK)
        self.assertEqual(resume_response.data["lesson"], "Caching lesson")
        self.assertEqual(resume_response.data["question"], "When does cached data become dangerous?")
        self.assertEqual(resume_response.data["selected_subtopic"], "Caching")
        self.assertEqual(resume_response.data["selected_lesson"], "Intro to Caching")
        self.assertEqual(resume_response.data["latest_attempt"]["score"], 61)


class PersonalCoachServiceTests(APITestCase):
    def test_fallback_subtopics_are_dynamic_for_python(self):
        subtopics = PersonalCoachService._fallback_subtopics("Python coding")
        self.assertGreaterEqual(len(subtopics), 6)
        self.assertTrue(any("syntax" in item.lower() or "pattern" in item.lower() for item in subtopics))

    def test_fallback_subtopics_are_dynamic_for_carnatic_music(self):
        subtopics = PersonalCoachService._fallback_subtopics("Carnatic sangeet")
        self.assertGreaterEqual(len(subtopics), 5)
        self.assertTrue(any("performance" in item.lower() or "practice" in item.lower() for item in subtopics))

    def test_fallback_subtopics_are_dynamic_for_system_design(self):
        subtopics = PersonalCoachService._fallback_subtopics("System design")
        self.assertGreaterEqual(len(subtopics), 6)
        self.assertTrue(any("advanced" in item.lower() or "fundamentals" in item.lower() for item in subtopics))

    def test_fallback_subtopics_are_dynamic_for_angular(self):
        subtopics = PersonalCoachService._fallback_subtopics("Angular")
        self.assertGreaterEqual(len(subtopics), 6)
        self.assertTrue(any("syntax" in item.lower() or "advanced" in item.lower() for item in subtopics))


class JobDescriptionAnalyzerApiTests(APITestCase):
    def setUp(self):
        self.username = "jdanalyzer"
        self.password = "analyzerPass123"
        self.user = get_user_model().objects.create_user(
            username=self.username,
            password=self.password,
        )

    def _authenticate(self):
        response = self.client.post(
            reverse("token_obtain_pair"),
            {"username": self.username, "password": self.password},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {response.data['access']}")

    @patch(
        "interviews.views.JobDescriptionAnalyzerService.analyze",
        return_value={
            "recruiter_intent": "Needs production-ready backend ownership.",
            "skill_tiers": {
                "strong_match": ["Python", "Django", "PostgreSQL"],
                "okay_match": ["Redis"],
                "low_priority": ["Nice-to-have cloud certifications"],
            },
            "disclosed_salary": {
                "found": True,
                "currency": "INR",
                "minimum": 12,
                "maximum": 18,
                "unit": "LPA",
                "raw_text": "12-18 LPA",
            },
            "market_salary_estimate": {
                "role_focus": "Mid backend engineer",
                "demandable_min": 15,
                "demandable_max": 22,
                "unit": "LPA",
                "confidence": "high",
                "reasoning": ["Strong skill demand in current market."],
            },
            "recommendations": ["Quantify backend performance wins."],
            "encouragement": "You are very close to this role.",
        },
    )
    def test_job_description_analyzer_endpoint(self, _mock_analyze):
        self._authenticate()

        response = self.client.post(
            reverse("analyze_job_description"),
            {
                "job_description": "Looking for Django backend developer with Python, PostgreSQL, and Redis. Salary 12-18 LPA.",
            },
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("analysis", response.data)
        self.assertIn("analysis_id", response.data)
        self.assertEqual(
            response.data["analysis"]["skill_tiers"]["strong_match"],
            ["Python", "Django", "PostgreSQL"],
        )

    @patch(
        "interviews.views.JobDescriptionAnalyzerService.analyze",
        return_value={
            "recruiter_intent": "Needs a backend engineer for high ownership.",
            "skill_tiers": {
                "strong_match": ["Python"],
                "okay_match": ["PostgreSQL"],
                "low_priority": ["Certifications"],
            },
            "disclosed_salary": {
                "found": False,
                "currency": "INR",
                "minimum": None,
                "maximum": None,
                "unit": "LPA",
                "raw_text": "",
            },
            "market_salary_estimate": {
                "role_focus": "Backend role",
                "demandable_min": 12,
                "demandable_max": 18,
                "unit": "LPA",
                "confidence": "medium",
                "reasoning": ["Skill demand and ownership scope."],
            },
            "recommendations": ["Prepare scalable backend examples."],
            "encouragement": "You are on the right track.",
        },
    )
    def test_jd_analysis_history_is_returned_in_progress(self, _mock_analyze):
        self._authenticate()

        self.client.post(
            reverse("analyze_job_description"),
            {
                "job_description": (
                    "Company: Acme Labs. Recruiter: Priya Sharma. "
                    "Apply by: 30/04/2026. Looking for Python backend engineer."
                ),
            },
            format="json",
        )

        progress_response = self.client.get(reverse("user_learning_progress"))
        self.assertEqual(progress_response.status_code, status.HTTP_200_OK)
        self.assertIn("job_description_analyzer", progress_response.data["modules"])
        self.assertEqual(len(progress_response.data["modules"]["job_description_analyzer"]), 1)

        item = progress_response.data["modules"]["job_description_analyzer"][0]
        self.assertEqual(item["company_name"], "Acme Labs")
        self.assertEqual(item["recruiter_name"], "Priya Sharma")

    @patch(
        "interviews.views.JobDescriptionAnalyzerService.analyze",
        return_value={
            "recruiter_intent": "Need backend ownership.",
            "skill_tiers": {
                "strong_match": ["Python"],
                "okay_match": ["Django"],
                "low_priority": ["Cloud cert"],
            },
            "disclosed_salary": {
                "found": False,
                "currency": "INR",
                "minimum": None,
                "maximum": None,
                "unit": "LPA",
                "raw_text": "",
            },
            "market_salary_estimate": {
                "role_focus": "Backend engineer",
                "demandable_min": 10,
                "demandable_max": 16,
                "unit": "LPA",
                "confidence": "medium",
                "reasoning": ["Role demand stable."],
            },
            "recommendations": ["Show impact metrics."],
            "encouragement": "Good fit with preparation.",
        },
    )
    def test_resume_job_description_analysis_returns_saved_data(self, _mock_analyze):
        self._authenticate()

        create_response = self.client.post(
            reverse("analyze_job_description"),
            {
                "job_description": "Company: Acme Labs. Recruiter: Priya Sharma. Apply by: 30/04/2026.",
            },
            format="json",
        )
        self.assertEqual(create_response.status_code, status.HTTP_200_OK)
        analysis_id = create_response.data["analysis_id"]

        resume_response = self.client.get(
            reverse("resume_job_description_analysis", args=[analysis_id])
        )
        self.assertEqual(resume_response.status_code, status.HTTP_200_OK)
        self.assertEqual(resume_response.data["analysis_id"], analysis_id)
        self.assertIn("job_description", resume_response.data)
        self.assertIn("application_context", resume_response.data)


class UserAspirationApiTests(APITestCase):
    def setUp(self):
        self.username = "aspuser"
        self.password = "aspPass123"
        self.user = get_user_model().objects.create_user(
            username=self.username,
            password=self.password,
        )

    def _authenticate(self):
        response = self.client.post(
            reverse("token_obtain_pair"),
            {"username": self.username, "password": self.password},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {response.data['access']}")

    @patch(
        "interviews.views.UserAspirationService.generate_roadmap",
        return_value={
            "summary": "Target is achievable with focused execution.",
            "readiness_score": 63,
            "gap_analysis": ["Need deeper system design examples."],
            "roadmap_phases": [
                {
                    "phase": "Foundation",
                    "duration": "2 months",
                    "focus": "Close role-specific gaps",
                    "actions": ["Study and apply core concepts"],
                    "deliverables": ["Mini project"],
                }
            ],
            "weekly_execution": ["10 hours focused prep"],
            "interview_preparation": ["2 mock interviews/week"],
            "encouragement": "Stay consistent and you will get there.",
        },
    )
    def test_create_and_resume_user_aspiration(self, _mock_generate):
        self._authenticate()

        create_response = self.client.post(
            reverse("create_user_aspiration"),
            {
                "current_position": "Software Engineer",
                "target_job": "Senior Backend Engineer",
                "timeline_months": 8,
                "current_skills": ["Python", "Django"],
                "constraints": "Weekends only",
                "additional_context": "Need remote role",
            },
            format="json",
        )
        self.assertEqual(create_response.status_code, status.HTTP_201_CREATED)
        self.assertIn("aspiration_id", create_response.data)
        aspiration_id = create_response.data["aspiration_id"]

        resume_response = self.client.get(reverse("resume_user_aspiration", args=[aspiration_id]))
        self.assertEqual(resume_response.status_code, status.HTTP_200_OK)
        self.assertEqual(resume_response.data["target_job"], "Senior Backend Engineer")
        self.assertEqual(resume_response.data["roadmap"]["readiness_score"], 63)

    @patch(
        "interviews.views.UserAspirationService.generate_roadmap",
        return_value={
            "summary": "Clear path from current role to target role.",
            "readiness_score": 60,
            "gap_analysis": ["Need stronger project outcomes"],
            "roadmap_phases": [],
            "weekly_execution": [],
            "interview_preparation": [],
            "encouragement": "Progress compounds with consistency.",
        },
    )
    def test_aspiration_history_in_progress(self, _mock_generate):
        self._authenticate()

        self.client.post(
            reverse("create_user_aspiration"),
            {
                "current_position": "Developer",
                "target_job": "Engineering Manager",
                "timeline_months": 12,
            },
            format="json",
        )

        progress_response = self.client.get(reverse("user_learning_progress"))
        self.assertEqual(progress_response.status_code, status.HTTP_200_OK)
        self.assertIn("aspirations", progress_response.data["modules"])
        self.assertEqual(len(progress_response.data["modules"]["aspirations"]), 1)
        self.assertEqual(
            progress_response.data["modules"]["aspirations"][0]["target_job"],
            "Engineering Manager",
        )

    @patch(
        "interviews.views.UserAspirationService.generate_roadmap",
        return_value={
            "summary": "Roadmap summary.",
            "readiness_score": 61,
            "gap_analysis": ["Gap 1"],
            "roadmap_phases": [
                {
                    "phase": "Phase 1",
                    "duration": "2 months",
                    "focus": "Focus 1",
                    "actions": ["Action A", "Action B"],
                    "deliverables": ["Deliverable A"],
                }
            ],
            "weekly_execution": ["Weekly item 1"],
            "interview_preparation": ["Mock interview"],
            "encouragement": "Keep going.",
        },
    )
    def test_generate_and_toggle_aspiration_checklist(self, _mock_generate):
        self._authenticate()

        create_response = self.client.post(
            reverse("create_user_aspiration"),
            {
                "current_position": "Developer",
                "target_job": "Senior Engineer",
                "timeline_months": 6,
            },
            format="json",
        )
        aspiration_id = create_response.data["aspiration_id"]

        checklist_response = self.client.post(
            reverse("generate_aspiration_checklist", args=[aspiration_id]),
            {},
            format="json",
        )
        self.assertIn(checklist_response.status_code, [status.HTTP_200_OK, status.HTTP_201_CREATED])
        self.assertGreater(checklist_response.data["total_count"], 0)
        self.assertIn("weeks", checklist_response.data)

        first_item_id = checklist_response.data["items"][0]["id"]
        toggle_response = self.client.post(
            reverse("toggle_aspiration_checklist_item", args=[aspiration_id]),
            {
                "item_id": first_item_id,
                "completed": True,
            },
            format="json",
        )
        self.assertEqual(toggle_response.status_code, status.HTTP_200_OK)
        self.assertEqual(toggle_response.data["completed_count"], 1)

        resume_response = self.client.get(reverse("resume_user_aspiration", args=[aspiration_id]))
        self.assertEqual(resume_response.status_code, status.HTTP_200_OK)
        self.assertIsNotNone(resume_response.data["checklist"])
        self.assertEqual(resume_response.data["checklist"]["completed_count"], 1)


class HRVoiceInterviewApiTests(APITestCase):
    def setUp(self):
        self.username = "hrvoiceuser"
        self.password = "hrvoicePass123"
        self.user = get_user_model().objects.create_user(
            username=self.username,
            password=self.password,
        )

    def _authenticate(self):
        response = self.client.post(
            reverse("token_obtain_pair"),
            {"username": self.username, "password": self.password},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {response.data['access']}")

    def test_candidate_profile_settings_put_and_get(self):
        self._authenticate()

        put_response = self.client.put(
            reverse("candidate_profile_settings"),
            {
                "current_position": "Senior Developer",
                "current_company": "Acme",
                "total_experience_years": 7.5,
                "primary_skills": ["Java", "Spring"],
                "salary_expectation": "25 LPA",
                "reason_for_leaving": "Looking for larger ownership",
            },
            format="json",
        )
        self.assertEqual(put_response.status_code, status.HTTP_200_OK)

        get_response = self.client.get(reverse("candidate_profile_settings"))
        self.assertEqual(get_response.status_code, status.HTTP_200_OK)
        self.assertEqual(get_response.data["current_position"], "Senior Developer")
        self.assertEqual(get_response.data["salary_expectation"], "25 LPA")

    @patch(
        "interviews.views.HRVoiceInterviewService.generate_questions",
        return_value=[
            "What is your current role?",
            "Why are you looking for a change?",
            "What is your salary expectation?",
        ],
    )
    @patch(
        "interviews.views.HRVoiceInterviewService.evaluate_answer",
        side_effect=[
            {
                "score": 72,
                "strengths": ["Clear and concise"],
                "weaknesses": ["Could be more quantified"],
                "improvements": ["Add measurable impact"],
            },
            {
                "score": 66,
                "strengths": ["Good intent"],
                "weaknesses": ["Missing specifics"],
                "improvements": ["Mention exact projects"],
            },
            {
                "score": 70,
                "strengths": ["Aligned expectation"],
                "weaknesses": ["Need market benchmark"],
                "improvements": ["Mention realistic compensation band"],
            },
        ],
    )
    @patch(
        "interviews.views.HRVoiceInterviewService.finalize_interview",
        return_value={
            "pass": True,
            "average_score": 69.33,
            "overall_feedback": "Good screening round performance.",
            "strong_answers": [
                {
                    "question": "What is your current role?",
                    "answer": "I am a backend engineer.",
                    "score": 72,
                    "strengths": ["Clear and concise"],
                }
            ],
            "weak_answers": [
                {
                    "question": "Why are you looking for a change?",
                    "answer": "Growth",
                    "score": 66,
                    "weaknesses": ["Missing specifics"],
                    "improvements": ["Mention exact projects"],
                }
            ],
            "improvement_plan": ["Use STAR structure"],
        },
    )
    def test_hr_voice_interview_end_to_end(
        self,
        _mock_finalize,
        _mock_evaluate,
        _mock_questions,
    ):
        self._authenticate()

        start_response = self.client.post(
            reverse("start_hr_voice_interview"),
            {
                "question_count": 10,
            },
            format="json",
        )
        self.assertEqual(start_response.status_code, status.HTTP_201_CREATED)
        self.assertIn("profile", start_response.data["context"])
        self.assertIn("is_profile_complete", start_response.data["context"]["profile"])
        session_id = start_response.data["session_id"]

        first_answer = self.client.post(
            reverse("answer_hr_voice_interview", args=[session_id]),
            {"answer": "I am currently a backend engineer at Acme."},
            format="json",
        )
        self.assertEqual(first_answer.status_code, status.HTTP_200_OK)
        self.assertEqual(first_answer.data["status"], "in_progress")
        self.assertTrue(first_answer.data["evaluation"]["improvements"][0].startswith("Better way to answer:"))

        self.client.post(
            reverse("answer_hr_voice_interview", args=[session_id]),
            {"answer": "I want bigger ownership and better role fit."},
            format="json",
        )
        final_response = self.client.post(
            reverse("answer_hr_voice_interview", args=[session_id]),
            {"answer": "I expect 24 to 26 LPA based on current market."},
            format="json",
        )
        self.assertEqual(final_response.status_code, status.HTTP_200_OK)
        self.assertEqual(final_response.data["status"], "completed")
        self.assertTrue(final_response.data["pass"])
        self.assertIn("final_feedback", final_response.data)

        resume_response = self.client.get(reverse("resume_hr_voice_interview", args=[session_id]))
        self.assertEqual(resume_response.status_code, status.HTTP_200_OK)
        self.assertEqual(resume_response.data["status"], "completed")
        self.assertEqual(len(resume_response.data["turns"]), 3)
        self.assertTrue(resume_response.data["turns"][0]["improvements"][0].startswith("Better way to answer:"))
        self.assertIn("profile", resume_response.data["context"])
        self.assertIn("current_position", resume_response.data["context"]["profile"])
