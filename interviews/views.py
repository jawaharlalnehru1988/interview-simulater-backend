from django.conf import settings
from django.db.models import Avg, Count
from django.shortcuts import get_object_or_404
from rest_framework import status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response

from .models import (
    Answer,
    AspirationChecklist,
    CandidateProfile,
    Evaluation,
    HRVoiceInterviewSession,
    HRVoiceInterviewTurn,
    Interview,
    JobDescriptionAnalysis,
    PersonalCoachAttempt,
    PersonalCoachSession,
    Question,
    UserAspiration,
)
from .serializers import (
    AspirationChecklistGenerateSerializer,
    AspirationChecklistToggleSerializer,
    CandidateProfileSerializer,
    CoachAnswerSerializer,
    CoachExplainSerializer,
    ChooseLessonSerializer,
    ChooseSubtopicSerializer,
    HRVoiceAnswerSerializer,
    JobDescriptionAnalyzeSerializer,
    NextQuestionResponseSerializer,
    RegisterSerializer,
    StartHRVoiceInterviewSerializer,
    StartInterviewSerializer,
    StartPersonalCoachSerializer,
    SubmitAnswerSerializer,
    UserAspirationSerializer,
)
from .services.evaluator import Evaluator
from .services.interview_engine import maybe_complete_interview, resolve_next_difficulty
from .services.job_description_analyzer import JobDescriptionAnalyzerService
from .services.personal_coach import PersonalCoachService
from .services.question_generator import QuestionGenerator
from .services.aspiration_checklist import AspirationChecklistService
from .services.hr_voice_interview import HRVoiceInterviewService
from .services.user_aspiration import UserAspirationService
from .tasks import evaluate_answer_task


def _profile_context(profile):
    if not profile:
        return "No structured candidate profile available."

    return {
        "current_position": profile.current_position,
        "current_company": profile.current_company,
        "total_experience_years": float(profile.total_experience_years)
        if profile.total_experience_years is not None
        else None,
        "primary_skills": profile.primary_skills,
        "current_salary": profile.current_salary,
        "salary_expectation": profile.salary_expectation,
        "notice_period": profile.notice_period,
        "reason_for_leaving": profile.reason_for_leaving,
        "career_gap_details": profile.career_gap_details,
        "highest_education": profile.highest_education,
        "preferred_locations": profile.preferred_locations,
        "preferred_role": profile.preferred_role,
        "additional_notes": profile.additional_notes,
    }


def _aspiration_context(aspiration):
    if not aspiration:
        return "No aspiration record selected."

    return {
        "current_position": aspiration.current_position,
        "target_job": aspiration.target_job,
        "timeline_months": aspiration.timeline_months,
        "current_skills": aspiration.current_skills,
        "constraints": aspiration.constraints,
        "additional_context": aspiration.additional_context,
        "roadmap_summary": aspiration.roadmap.get("summary", ""),
    }


def _job_analysis_context(job_analysis):
    if not job_analysis:
        return "No JD analysis selected."

    return {
        "company_name": job_analysis.company_name,
        "recruiter_name": job_analysis.recruiter_name,
        "application_last_date": job_analysis.application_last_date,
        "recruiter_intent": job_analysis.analysis.get("recruiter_intent", ""),
        "skill_tiers": job_analysis.analysis.get("skill_tiers", {}),
        "market_salary_estimate": job_analysis.analysis.get("market_salary_estimate", {}),
    }


def _profile_reference(profile):
    if not profile:
        return {
            "current_position": "",
            "current_company": "",
            "total_experience_years": None,
            "primary_skills": [],
            "salary_expectation": "",
            "notice_period": "",
            "preferred_role": "",
            "is_profile_complete": False,
        }

    has_core_data = any(
        [
            profile.current_position,
            profile.current_company,
            profile.primary_skills,
            profile.salary_expectation,
            profile.preferred_role,
        ]
    )

    return {
        "current_position": profile.current_position,
        "current_company": profile.current_company,
        "total_experience_years": float(profile.total_experience_years)
        if profile.total_experience_years is not None
        else None,
        "primary_skills": profile.primary_skills,
        "salary_expectation": profile.salary_expectation,
        "notice_period": profile.notice_period,
        "preferred_role": profile.preferred_role,
        "is_profile_complete": has_core_data,
    }


def _coach_practice_state(session):
    attempts = session.attempts.all()
    practiced_subtopics = sorted({item.subtopic for item in attempts if item.subtopic})
    practiced_lessons = {}

    for item in attempts:
        if not item.subtopic or not item.lesson:
            continue
        practiced_lessons.setdefault(item.subtopic, set()).add(item.lesson)

    practiced_lessons = {
        key: sorted(values)
        for key, values in practiced_lessons.items()
    }

    return practiced_subtopics, practiced_lessons


def _coach_progress_metrics(session, practiced_subtopics=None, practiced_lessons=None):
    practiced_subtopics = practiced_subtopics if practiced_subtopics is not None else []
    practiced_lessons = practiced_lessons if practiced_lessons is not None else {}

    total_subtopics = len(session.subtopics or [])
    practiced_subtopics_count = len(practiced_subtopics)

    lessons_by_subtopic = session.lessons_by_subtopic or {}
    total_lessons = sum(len(items or []) for items in lessons_by_subtopic.values())
    practiced_lessons_count = sum(len(items or []) for items in practiced_lessons.values())

    progress_units_total = total_subtopics + total_lessons
    progress_units_done = practiced_subtopics_count + practiced_lessons_count
    progress_percent = round((progress_units_done * 100.0) / progress_units_total, 2) if progress_units_total else 0

    return {
        "total_subtopics": total_subtopics,
        "practiced_subtopics_count": practiced_subtopics_count,
        "total_lessons": total_lessons,
        "practiced_lessons_count": practiced_lessons_count,
        "progress_percent": progress_percent,
    }


@api_view(["POST"])
@permission_classes([AllowAny])
def register(request):
    serializer = RegisterSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    user = serializer.save()
    return Response(
        {"id": user.id, "username": user.username},
        status=status.HTTP_201_CREATED,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def start_interview(request):
    serializer = StartInterviewSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    interview = Interview.objects.create(
        user=request.user,
        topic=serializer.validated_data["topic"],
        round_type=serializer.validated_data["round"],
    )

    return Response(
        {
            "interview_id": interview.id,
            "status": interview.status,
            "topic": interview.topic,
            "round": interview.round_type,
        },
        status=status.HTTP_201_CREATED,
    )


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def next_question(request, interview_id):
    interview = get_object_or_404(Interview, id=interview_id, user=request.user)

    if interview.status == Interview.Status.COMPLETED:
        return Response({"detail": "Interview already completed."}, status=status.HTTP_200_OK)

    difficulty = resolve_next_difficulty(interview)
    generator = QuestionGenerator()
    question_text = generator.generate(
        topic=interview.topic,
        difficulty=difficulty,
        round_type=interview.round_type,
    )

    question = Question.objects.create(
        interview=interview,
        text=question_text,
        difficulty=difficulty,
        order=interview.current_question_index,
    )

    mcq_options = []
    if interview.round_type == Interview.RoundType.MCQ:
        mcq_options = generator.extract_mcq_options(question_text)

    suggested_answer = generator.generate_suggested_answer(
        question_text=question_text,
        round_type=interview.round_type,
        mcq_options=mcq_options,
    )

    interview.current_question_index += 1
    interview.save(update_fields=["current_question_index", "updated_at"])
    maybe_complete_interview(interview, settings.INTERVIEW_MAX_QUESTIONS)

    payload = {
        "interview_id": interview.id,
        "question_id": question.id,
        "question": question.text,
        "difficulty": question.difficulty,
        "question_number": question.order + 1,
        "status": interview.status,
        "mcq_options": mcq_options,
        "suggested_answer": suggested_answer,
    }
    response_serializer = NextQuestionResponseSerializer(data=payload)
    response_serializer.is_valid(raise_exception=True)

    return Response(response_serializer.data)


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def submit_answer(request):
    serializer = SubmitAnswerSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    question = get_object_or_404(
        Question.objects.select_related("interview"),
        id=serializer.validated_data["question_id"],
        interview__user=request.user,
    )
    user_input = serializer.validated_data["answer"]

    answer = Answer.objects.create(
        question=question,
        user_input=user_input,
    )

    if settings.EVALUATION_ASYNC:
        evaluate_answer_task.delay(answer.id)
        return Response(
            {
                "question_id": question.id,
                "answer_id": answer.id,
                "evaluation_status": Answer.EvaluationStatus.PENDING,
            },
            status=status.HTTP_202_ACCEPTED,
        )

    result = Evaluator().evaluate(question.text, user_input)
    Evaluation.objects.create(
        answer=answer,
        score=result["score"],
        strengths=result["strengths"],
        weaknesses=result["weaknesses"],
        improvements=result["improvements"],
    )
    answer.evaluation_status = Answer.EvaluationStatus.COMPLETED
    answer.save(update_fields=["evaluation_status"])

    return Response(
        {
            "question_id": question.id,
            "answer_id": answer.id,
            "evaluation_status": answer.evaluation_status,
            "evaluation": result,
        },
        status=status.HTTP_201_CREATED,
    )


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def interview_summary(request, interview_id):
    interview = get_object_or_404(Interview, id=interview_id, user=request.user)

    evaluations = Evaluation.objects.filter(answer__question__interview=interview)
    avg_score = evaluations.aggregate(avg=Avg("score"))["avg"]

    questions_payload = []
    questions = Question.objects.filter(interview=interview).order_by("order")
    for q in questions:
        answer = q.answers.order_by("-created_at").first()
        evaluation = getattr(answer, "evaluation", None) if answer else None
        questions_payload.append(
            {
                "question_id": q.id,
                "order": q.order,
                "difficulty": q.difficulty,
                "question": q.text,
                "answer": answer.user_input if answer else None,
                "evaluation_status": answer.evaluation_status if answer else None,
                "score": evaluation.score if evaluation else None,
            }
        )

    return Response(
        {
            "interview_id": interview.id,
            "topic": interview.topic,
            "round": interview.round_type,
            "status": interview.status,
            "questions_asked": interview.current_question_index,
            "evaluations_completed": evaluations.count(),
            "average_score": round(avg_score, 2) if avg_score is not None else None,
            "questions": questions_payload,
        }
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def start_personal_coach(request):
    serializer = StartPersonalCoachSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    topic = serializer.validated_data["topic"].strip()
    service = PersonalCoachService()
    subtopics = service.generate_subtopics(topic)

    session = PersonalCoachSession.objects.create(
        user=request.user,
        topic=topic,
        subtopics=subtopics,
    )

    return Response(
        {
            "session_id": session.id,
            "topic": session.topic,
            "subtopics": session.subtopics,
            "stage": session.stage,
            "coach_prompt": "Choose one important interview question you want to learn first.",
        },
        status=status.HTTP_201_CREATED,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def choose_personal_coach_subtopic(request, session_id):
    serializer = ChooseSubtopicSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    session = get_object_or_404(PersonalCoachSession, id=session_id, user=request.user)
    subtopic = serializer.validated_data["subtopic"].strip()

    available = {item.lower() for item in session.subtopics}
    if subtopic.lower() not in available:
        return Response(
            {
                "detail": "Please choose one of the generated subtopics.",
                "subtopics": session.subtopics,
            },
            status=status.HTTP_400_BAD_REQUEST,
        )

    service = PersonalCoachService()
    lessons_by_subtopic = session.lessons_by_subtopic or {}
    lessons = lessons_by_subtopic.get(subtopic, [])
    if not lessons:
        lessons = service.generate_lessons(session.topic, subtopic)
        lessons_by_subtopic[subtopic] = lessons

    session.selected_subtopic = subtopic
    session.selected_lesson = ""
    session.stage = PersonalCoachSession.Stage.LESSON_SELECTION
    session.lessons_by_subtopic = lessons_by_subtopic
    session.current_lesson = ""
    session.current_question = ""
    session.suggested_next_subtopic = ""
    session.save(
        update_fields=[
            "selected_subtopic",
            "selected_lesson",
            "stage",
            "lessons_by_subtopic",
            "current_lesson",
            "current_question",
            "suggested_next_subtopic",
            "updated_at",
        ]
    )

    practiced_subtopics, practiced_lessons = _coach_practice_state(session)

    return Response(
        {
            "session_id": session.id,
            "topic": session.topic,
            "subtopic": session.selected_subtopic,
            "lessons": lessons,
            "practiced_subtopics": practiced_subtopics,
            "practiced_lessons": practiced_lessons.get(session.selected_subtopic, []),
            "stage": session.stage,
            "coach_prompt": "Choose one lesson under this subtopic to begin training.",
        }
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def choose_personal_coach_lesson(request, session_id):
    serializer = ChooseLessonSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    session = get_object_or_404(PersonalCoachSession, id=session_id, user=request.user)
    if not session.selected_subtopic:
        return Response(
            {"detail": "Choose a subtopic first before selecting a lesson."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    lesson_name = serializer.validated_data["lesson"].strip()
    service = PersonalCoachService()

    lessons_by_subtopic = session.lessons_by_subtopic or {}
    lessons = lessons_by_subtopic.get(session.selected_subtopic, [])
    if not lessons:
        lessons = service.generate_lessons(session.topic, session.selected_subtopic)
        lessons_by_subtopic[session.selected_subtopic] = lessons

    available_lessons = {item.lower() for item in lessons}
    if lesson_name.lower() not in available_lessons:
        return Response(
            {
                "detail": "Please choose one of the generated lessons.",
                "lessons": lessons,
            },
            status=status.HTTP_400_BAD_REQUEST,
        )

    lesson_content, question = service.generate_lesson_and_question(
        session.topic,
        session.selected_subtopic,
        lesson_name,
    )

    session.selected_lesson = lesson_name
    session.stage = PersonalCoachSession.Stage.QUESTIONING
    session.lessons_by_subtopic = lessons_by_subtopic
    session.current_lesson = lesson_content
    session.current_question = question
    session.attempt_count = 0
    session.suggested_next_subtopic = ""
    session.save(
        update_fields=[
            "selected_lesson",
            "stage",
            "lessons_by_subtopic",
            "current_lesson",
            "current_question",
            "attempt_count",
            "suggested_next_subtopic",
            "updated_at",
        ]
    )

    practiced_subtopics, practiced_lessons = _coach_practice_state(session)
    progress_metrics = _coach_progress_metrics(session, practiced_subtopics, practiced_lessons)

    return Response(
        {
            "session_id": session.id,
            "topic": session.topic,
            "subtopic": session.selected_subtopic,
            "selected_lesson": session.selected_lesson,
            "practiced_subtopics": practiced_subtopics,
            "practiced_lessons": practiced_lessons.get(session.selected_subtopic, []),
            **progress_metrics,
            "stage": session.stage,
            "lesson": session.current_lesson,
            "question": session.current_question,
            "coach_prompt": "Answer the question below. I will coach you until your answer is strong.",
        }
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def answer_personal_coach_question(request, session_id):
    serializer = CoachAnswerSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    session = get_object_or_404(PersonalCoachSession, id=session_id, user=request.user)
    if session.stage != PersonalCoachSession.Stage.QUESTIONING:
        return Response(
            {"detail": "Select a subtopic first before answering."},
            status=status.HTTP_400_BAD_REQUEST,
        )

    answer = serializer.validated_data["answer"].strip()
    service = PersonalCoachService()
    result = service.evaluate_answer(
        topic=session.topic,
        subtopic=session.selected_subtopic,
        question=session.current_question,
        answer=answer,
    )

    score = result["score"]
    should_advance = score >= 75
    session.mastery_score = score
    session.attempt_count += 1
    current_question = session.current_question

    response_payload = {
        "session_id": session.id,
        "topic": session.topic,
        "subtopic": session.selected_subtopic,
        "selected_lesson": session.selected_lesson,
        "score": score,
        "strengths": result["strengths"],
        "gaps": result["gaps"],
        "feedback": result["feedback"],
        "stage": session.stage,
    }

    if should_advance:
        next_subtopic = result["next_subtopic"].strip()
        if not next_subtopic:
            next_subtopic = f"Advanced {session.selected_subtopic}"

        session.stage = PersonalCoachSession.Stage.SUBTOPIC_SELECTION
        session.suggested_next_subtopic = next_subtopic
        session.selected_lesson = ""
        session.current_lesson = ""
        session.current_question = ""

        if next_subtopic.lower() not in {item.lower() for item in session.subtopics}:
            session.subtopics = [*session.subtopics, next_subtopic]

        response_payload.update(
            {
                "stage": session.stage,
                "coach_decision": "advance",
                "suggested_next_subtopic": next_subtopic,
                "subtopics": session.subtopics,
                "coach_prompt": "Great progress. Choose the next subtopic to continue learning.",
            }
        )
    else:
        follow_up = result["follow_up_question"]
        session.current_question = follow_up
        session.suggested_next_subtopic = ""

        response_payload.update(
            {
                "stage": session.stage,
                "coach_decision": "remediate",
                "next_question": follow_up,
                "coach_prompt": "Let's reinforce this subtopic. Answer the follow-up question.",
            }
        )

    PersonalCoachAttempt.objects.create(
        session=session,
        subtopic=session.selected_subtopic,
        lesson=session.selected_lesson,
        question=current_question,
        user_answer=answer,
        score=score,
        strengths=result["strengths"],
        gaps=result["gaps"],
        feedback=result["feedback"],
        coach_decision=(
            PersonalCoachAttempt.CoachDecision.ADVANCE
            if should_advance
            else PersonalCoachAttempt.CoachDecision.REMEDIATE
        ),
    )

    practiced_subtopics, practiced_lessons = _coach_practice_state(session)
    progress_metrics = _coach_progress_metrics(session, practiced_subtopics, practiced_lessons)
    response_payload["practiced_subtopics"] = practiced_subtopics
    response_payload["practiced_lessons"] = practiced_lessons.get(session.selected_subtopic, [])
    response_payload.update(progress_metrics)

    session.save(
        update_fields=[
            "mastery_score",
            "attempt_count",
            "stage",
            "selected_lesson",
            "current_lesson",
            "current_question",
            "suggested_next_subtopic",
            "subtopics",
            "updated_at",
        ]
    )
    return Response(response_payload)


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def resume_personal_coach_session(request, session_id):
    session = get_object_or_404(
        PersonalCoachSession.objects.prefetch_related("attempts"),
        id=session_id,
        user=request.user,
    )
    latest_attempt = session.attempts.first()
    practiced_subtopics, practiced_lessons = _coach_practice_state(session)
    progress_metrics = _coach_progress_metrics(session, practiced_subtopics, practiced_lessons)

    return Response(
        {
            "session_id": session.id,
            "topic": session.topic,
            "subtopics": session.subtopics,
            "lessons_by_subtopic": session.lessons_by_subtopic,
            "selected_subtopic": session.selected_subtopic,
            "selected_lesson": session.selected_lesson,
            "available_lessons": (session.lessons_by_subtopic or {}).get(session.selected_subtopic, []),
            "practiced_subtopics": practiced_subtopics,
            "practiced_lessons_map": practiced_lessons,
            "practiced_lessons": practiced_lessons.get(session.selected_subtopic, []),
            **progress_metrics,
            "stage": session.stage,
            "lesson": session.current_lesson,
            "question": session.current_question,
            "attempt_count": session.attempt_count,
            "suggested_next_subtopic": session.suggested_next_subtopic,
            "latest_attempt": (
                {
                    "score": latest_attempt.score,
                    "strengths": latest_attempt.strengths,
                    "gaps": latest_attempt.gaps,
                    "feedback": latest_attempt.feedback,
                    "coach_decision": latest_attempt.coach_decision,
                }
                if latest_attempt
                else None
            ),
            "coach_prompt": (
                "Resume by answering the current question."
                if session.stage == PersonalCoachSession.Stage.QUESTIONING and session.current_question
                else "Resume by choosing a lesson in the selected subtopic."
                if session.stage == PersonalCoachSession.Stage.LESSON_SELECTION and session.selected_subtopic
                else "Resume by choosing the next subtopic to continue learning."
            ),
        }
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def explain_personal_coach_query(request, session_id):
    serializer = CoachExplainSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    session = get_object_or_404(PersonalCoachSession, id=session_id, user=request.user)
    learner_question = serializer.validated_data["question"].strip()

    service = PersonalCoachService()
    explanation = service.explain_query(
        topic=session.topic,
        subtopic=session.selected_subtopic,
        learner_question=learner_question,
        current_question=session.current_question,
    )

    return Response(
        {
            "session_id": session.id,
            "topic": session.topic,
            "subtopic": session.selected_subtopic,
            "learner_question": learner_question,
            "explanation": explanation,
        }
    )


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def user_learning_progress(request):
    interviews = Interview.objects.filter(user=request.user).order_by("-created_at")
    interview_items = []
    for interview in interviews:
        avg_score = (
            Evaluation.objects.filter(answer__question__interview=interview).aggregate(avg=Avg("score"))["avg"]
        )
        interview_items.append(
            {
                "interview_id": interview.id,
                "topic": interview.topic,
                "round": interview.round_type,
                "status": interview.status,
                "questions_asked": interview.current_question_index,
                "average_score": round(avg_score, 2) if avg_score is not None else None,
                "last_updated": interview.updated_at,
            }
        )

    coach_sessions = (
        PersonalCoachSession.objects.filter(user=request.user)
        .prefetch_related("attempts")
        .order_by("-updated_at")
    )
    coach_items = []
    for coach in coach_sessions:
        attempts = coach.attempts.all()
        avg_score = attempts.aggregate(avg=Avg("score"))["avg"] if attempts else None
        practiced_subtopics, practiced_lessons = _coach_practice_state(coach)
        progress_metrics = _coach_progress_metrics(coach, practiced_subtopics, practiced_lessons)
        coach_items.append(
            {
                "session_id": coach.id,
                "topic": coach.topic,
                "current_subtopic": coach.selected_subtopic,
                "stage": coach.stage,
                "attempt_count": coach.attempt_count,
                "latest_score": coach.mastery_score if coach.attempt_count else None,
                "average_score": round(avg_score, 2) if avg_score is not None else None,
                "suggested_next_subtopic": coach.suggested_next_subtopic,
                "practiced_subtopics": practiced_subtopics,
                "practiced_lessons_map": practiced_lessons,
                **progress_metrics,
                "last_updated": coach.updated_at,
            }
        )

    attempted_topics = {
        "interview_topics": list(
            Interview.objects.filter(user=request.user).order_by().values_list("topic", flat=True).distinct()
        ),
        "personal_coach_topics": list(
            PersonalCoachSession.objects.filter(user=request.user)
            .order_by()
            .values_list("topic", flat=True)
            .distinct()
        ),
    }

    coach_attempts_summary = (
        PersonalCoachAttempt.objects.filter(session__user=request.user)
        .values("session__topic")
        .annotate(total_attempts=Count("id"), avg_score=Avg("score"))
        .order_by("session__topic")
    )

    jd_analyses = JobDescriptionAnalysis.objects.filter(user=request.user).order_by("-updated_at")
    jd_items = []
    for item in jd_analyses:
        jd_items.append(
            {
                "analysis_id": item.id,
                "company_name": item.company_name,
                "recruiter_name": item.recruiter_name,
                "application_last_date": item.application_last_date,
                "application_last_date_raw": item.application_last_date_raw,
                "job_description_preview": item.job_description[:220],
                "recruiter_intent": item.analysis.get("recruiter_intent", ""),
                "created_at": item.created_at,
                "last_updated": item.updated_at,
            }
        )

    aspiration_items = []
    aspirations = UserAspiration.objects.filter(user=request.user).order_by("-updated_at")
    for item in aspirations:
        aspiration_items.append(
            {
                "aspiration_id": item.id,
                "current_position": item.current_position,
                "target_job": item.target_job,
                "timeline_months": item.timeline_months,
                "readiness_score": item.roadmap.get("readiness_score"),
                "summary": item.roadmap.get("summary", ""),
                "created_at": item.created_at,
                "last_updated": item.updated_at,
            }
        )

    hr_sessions = HRVoiceInterviewSession.objects.filter(user=request.user).order_by("-updated_at")
    hr_items = []
    for session in hr_sessions:
        avg_score = (
            session.turns.aggregate(avg=Avg("score"))["avg"]
            if session.turns.exists()
            else None
        )
        hr_items.append(
            {
                "session_id": session.id,
                "status": session.status,
                "question_count": len(session.questions),
                "answered_count": session.current_question_index,
                "average_score": round(avg_score, 2) if avg_score is not None else None,
                "pass_decision": session.pass_decision,
                "target_job": session.aspiration.target_job if session.aspiration else "",
                "company_name": session.job_analysis.company_name if session.job_analysis else "",
                "last_updated": session.updated_at,
            }
        )

    return Response(
        {
            "user": request.user.username,
            "profile_ready": hasattr(request.user, "candidate_profile"),
            "attempted_topics": attempted_topics,
            "modules": {
                "interview": interview_items,
                "personal_coach": coach_items,
                "job_description_analyzer": jd_items,
                "aspirations": aspiration_items,
                "hr_voice_calls": hr_items,
            },
            "personal_coach_topic_stats": [
                {
                    "topic": item["session__topic"],
                    "total_attempts": item["total_attempts"],
                    "average_score": round(item["avg_score"], 2)
                    if item["avg_score"] is not None
                    else None,
                }
                for item in coach_attempts_summary
            ],
        }
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def analyze_job_description(request):
    serializer = JobDescriptionAnalyzeSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    service = JobDescriptionAnalyzerService()
    job_description = serializer.validated_data["job_description"].strip()
    analysis = service.analyze(job_description)

    extracted_context = service.extract_application_context(job_description)
    recruiter_name = serializer.validated_data.get("recruiter_name", "").strip() or extracted_context[
        "recruiter_name"
    ]
    company_name = serializer.validated_data.get("company_name", "").strip() or extracted_context[
        "company_name"
    ]
    application_last_date = serializer.validated_data.get("application_last_date") or extracted_context[
        "application_last_date"
    ]
    application_last_date_raw = extracted_context["application_last_date_raw"]

    history = JobDescriptionAnalysis.objects.create(
        user=request.user,
        job_description=job_description,
        recruiter_name=recruiter_name,
        company_name=company_name,
        application_last_date=application_last_date,
        application_last_date_raw=application_last_date_raw,
        analysis=analysis,
    )

    return Response(
        {
            "analysis_id": history.id,
            "analysis": analysis,
            "application_context": {
                "recruiter_name": recruiter_name,
                "company_name": company_name,
                "application_last_date": application_last_date,
                "application_last_date_raw": application_last_date_raw,
            },
        },
        status=status.HTTP_200_OK,
    )


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def resume_job_description_analysis(request, analysis_id):
    analysis = get_object_or_404(JobDescriptionAnalysis, id=analysis_id, user=request.user)

    return Response(
        {
            "analysis_id": analysis.id,
            "analysis": analysis.analysis,
            "application_context": {
                "recruiter_name": analysis.recruiter_name,
                "company_name": analysis.company_name,
                "application_last_date": analysis.application_last_date,
                "application_last_date_raw": analysis.application_last_date_raw,
            },
            "job_description": analysis.job_description,
            "created_at": analysis.created_at,
            "last_updated": analysis.updated_at,
        },
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def create_user_aspiration(request):
    serializer = UserAspirationSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    service = UserAspirationService()
    roadmap = service.generate_roadmap(
        current_position=serializer.validated_data["current_position"].strip(),
        target_job=serializer.validated_data["target_job"].strip(),
        timeline_months=serializer.validated_data["timeline_months"],
        current_skills=serializer.validated_data.get("current_skills", []),
        constraints=serializer.validated_data.get("constraints", "").strip(),
        additional_context=serializer.validated_data.get("additional_context", "").strip(),
    )

    aspiration = UserAspiration.objects.create(
        user=request.user,
        current_position=serializer.validated_data["current_position"].strip(),
        target_job=serializer.validated_data["target_job"].strip(),
        timeline_months=serializer.validated_data["timeline_months"],
        current_skills=serializer.validated_data.get("current_skills", []),
        constraints=serializer.validated_data.get("constraints", "").strip(),
        additional_context=serializer.validated_data.get("additional_context", "").strip(),
        roadmap=roadmap,
    )

    return Response(
        {
            "aspiration_id": aspiration.id,
            "current_position": aspiration.current_position,
            "target_job": aspiration.target_job,
            "timeline_months": aspiration.timeline_months,
            "current_skills": aspiration.current_skills,
            "constraints": aspiration.constraints,
            "additional_context": aspiration.additional_context,
            "roadmap": aspiration.roadmap,
            "created_at": aspiration.created_at,
            "last_updated": aspiration.updated_at,
        },
        status=status.HTTP_201_CREATED,
    )


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def resume_user_aspiration(request, aspiration_id):
    aspiration = get_object_or_404(UserAspiration, id=aspiration_id, user=request.user)
    checklist = getattr(aspiration, "checklist", None)
    return Response(
        {
            "aspiration_id": aspiration.id,
            "current_position": aspiration.current_position,
            "target_job": aspiration.target_job,
            "timeline_months": aspiration.timeline_months,
            "current_skills": aspiration.current_skills,
            "constraints": aspiration.constraints,
            "additional_context": aspiration.additional_context,
            "roadmap": aspiration.roadmap,
            "checklist": (
                {
                    "id": checklist.id,
                    "completed_count": checklist.completed_count,
                    "total_count": checklist.total_count,
                    "items": checklist.items,
                    "weeks": AspirationChecklistService.group_by_week(checklist.items),
                    "progress_percent": round(
                        (checklist.completed_count * 100.0) / checklist.total_count,
                        2,
                    )
                    if checklist.total_count
                    else 0,
                }
                if checklist
                else None
            ),
            "created_at": aspiration.created_at,
            "last_updated": aspiration.updated_at,
        },
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def generate_aspiration_checklist(request, aspiration_id):
    serializer = AspirationChecklistGenerateSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    aspiration = get_object_or_404(UserAspiration, id=aspiration_id, user=request.user)
    force_regenerate = serializer.validated_data.get("force_regenerate", False)

    service = AspirationChecklistService()
    checklist = getattr(aspiration, "checklist", None)

    if checklist and not force_regenerate:
        return Response(
            {
                "id": checklist.id,
                "completed_count": checklist.completed_count,
                "total_count": checklist.total_count,
                "items": checklist.items,
                "weeks": service.group_by_week(checklist.items),
                "progress_percent": round((checklist.completed_count * 100.0) / checklist.total_count, 2)
                if checklist.total_count
                else 0,
            },
            status=status.HTTP_200_OK,
        )

    items = service.build_items(aspiration.roadmap, aspiration.timeline_months)
    completed_count = sum(1 for item in items if item.get("completed"))

    if checklist:
        checklist.items = items
        checklist.completed_count = completed_count
        checklist.total_count = len(items)
        checklist.save(update_fields=["items", "completed_count", "total_count", "updated_at"])
    else:
        checklist = AspirationChecklist.objects.create(
            aspiration=aspiration,
            items=items,
            completed_count=completed_count,
            total_count=len(items),
        )

    return Response(
        {
            "id": checklist.id,
            "completed_count": checklist.completed_count,
            "total_count": checklist.total_count,
            "items": checklist.items,
            "weeks": service.group_by_week(checklist.items),
            "progress_percent": round((checklist.completed_count * 100.0) / checklist.total_count, 2)
            if checklist.total_count
            else 0,
        },
        status=status.HTTP_201_CREATED,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def toggle_aspiration_checklist_item(request, aspiration_id):
    serializer = AspirationChecklistToggleSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    aspiration = get_object_or_404(UserAspiration, id=aspiration_id, user=request.user)
    checklist = get_object_or_404(AspirationChecklist, aspiration=aspiration)
    service = AspirationChecklistService()

    updated_items, found, completed_count, total_count = service.update_completion(
        checklist.items,
        serializer.validated_data["item_id"],
        serializer.validated_data["completed"],
    )

    if not found:
        return Response({"detail": "Checklist item not found."}, status=status.HTTP_404_NOT_FOUND)

    checklist.items = updated_items
    checklist.completed_count = completed_count
    checklist.total_count = total_count
    checklist.save(update_fields=["items", "completed_count", "total_count", "updated_at"])

    return Response(
        {
            "id": checklist.id,
            "completed_count": checklist.completed_count,
            "total_count": checklist.total_count,
            "items": checklist.items,
            "weeks": service.group_by_week(checklist.items),
            "progress_percent": round((checklist.completed_count * 100.0) / checklist.total_count, 2)
            if checklist.total_count
            else 0,
        },
        status=status.HTTP_200_OK,
    )


@api_view(["GET", "PUT"])
@permission_classes([IsAuthenticated])
def candidate_profile_settings(request):
    profile, _ = CandidateProfile.objects.get_or_create(user=request.user)

    if request.method == "GET":
        return Response(
            {
                "current_position": profile.current_position,
                "current_company": profile.current_company,
                "total_experience_years": profile.total_experience_years,
                "primary_skills": profile.primary_skills,
                "current_salary": profile.current_salary,
                "salary_expectation": profile.salary_expectation,
                "notice_period": profile.notice_period,
                "reason_for_leaving": profile.reason_for_leaving,
                "career_gap_details": profile.career_gap_details,
                "highest_education": profile.highest_education,
                "preferred_locations": profile.preferred_locations,
                "preferred_role": profile.preferred_role,
                "additional_notes": profile.additional_notes,
            },
            status=status.HTTP_200_OK,
        )

    serializer = CandidateProfileSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    for field, value in serializer.validated_data.items():
        setattr(profile, field, value)

    profile.save()
    return Response(
        {
            "detail": "Profile updated.",
            "profile": {
                "current_position": profile.current_position,
                "current_company": profile.current_company,
                "total_experience_years": profile.total_experience_years,
                "primary_skills": profile.primary_skills,
                "current_salary": profile.current_salary,
                "salary_expectation": profile.salary_expectation,
                "notice_period": profile.notice_period,
                "reason_for_leaving": profile.reason_for_leaving,
                "career_gap_details": profile.career_gap_details,
                "highest_education": profile.highest_education,
                "preferred_locations": profile.preferred_locations,
                "preferred_role": profile.preferred_role,
                "additional_notes": profile.additional_notes,
            },
        },
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def start_hr_voice_interview(request):
    serializer = StartHRVoiceInterviewSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    profile, _ = CandidateProfile.objects.get_or_create(user=request.user)

    aspiration_id = serializer.validated_data.get("aspiration_id")
    jd_analysis_id = serializer.validated_data.get("jd_analysis_id")

    aspiration = None
    if aspiration_id:
        aspiration = get_object_or_404(UserAspiration, id=aspiration_id, user=request.user)
    else:
        aspiration = UserAspiration.objects.filter(user=request.user).order_by("-updated_at").first()

    job_analysis = None
    if jd_analysis_id:
        job_analysis = get_object_or_404(JobDescriptionAnalysis, id=jd_analysis_id, user=request.user)
    else:
        job_analysis = JobDescriptionAnalysis.objects.filter(user=request.user).order_by("-updated_at").first()

    service = HRVoiceInterviewService()
    questions = service.generate_questions(
        profile=_profile_context(profile),
        aspiration=_aspiration_context(aspiration),
        job_analysis=_job_analysis_context(job_analysis),
        question_count=serializer.validated_data.get("question_count", 12),
    )

    session = HRVoiceInterviewSession.objects.create(
        user=request.user,
        profile=profile,
        aspiration=aspiration,
        job_analysis=job_analysis,
        questions=questions,
    )

    return Response(
        {
            "session_id": session.id,
            "status": session.status,
            "question_count": len(session.questions),
            "current_question_index": session.current_question_index,
            "current_question": session.questions[0] if session.questions else "",
            "context": {
                "target_job": aspiration.target_job if aspiration else "",
                "company_name": job_analysis.company_name if job_analysis else "",
                "profile": _profile_reference(profile),
            },
        },
        status=status.HTTP_201_CREATED,
    )


@api_view(["POST"])
@permission_classes([IsAuthenticated])
def answer_hr_voice_interview(request, session_id):
    serializer = HRVoiceAnswerSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)

    session = get_object_or_404(
        HRVoiceInterviewSession.objects.select_related("profile", "aspiration", "job_analysis").prefetch_related("turns"),
        id=session_id,
        user=request.user,
    )

    if session.status == HRVoiceInterviewSession.Status.COMPLETED:
        return Response(
            {
                "detail": "Interview already completed.",
                "final_feedback": session.final_feedback,
                "pass": session.pass_decision,
            },
            status=status.HTTP_200_OK,
        )

    if session.current_question_index >= len(session.questions):
        return Response({"detail": "No pending questions."}, status=status.HTTP_400_BAD_REQUEST)

    question = session.questions[session.current_question_index]
    answer = serializer.validated_data["answer"].strip()
    service = HRVoiceInterviewService()

    eval_result = service.evaluate_answer(
        question=question,
        answer=answer,
        profile=_profile_context(session.profile),
        aspiration=_aspiration_context(session.aspiration),
        job_analysis=_job_analysis_context(session.job_analysis),
    )

    coaching_line = f"Better way to answer: {eval_result['better_answer']}"
    improvements = [coaching_line, *eval_result["improvements"]]

    HRVoiceInterviewTurn.objects.create(
        session=session,
        question=question,
        answer=answer,
        score=eval_result["score"],
        strengths=eval_result["strengths"],
        weaknesses=eval_result["weaknesses"],
        improvements=improvements,
    )

    eval_result["improvements"] = improvements

    session.current_question_index += 1
    is_completed = session.current_question_index >= len(session.questions)

    if is_completed:
        turns = list(session.turns.values("question", "answer", "score", "strengths", "weaknesses", "improvements"))
        final_result = service.finalize_interview(
            turns=turns,
            profile=_profile_context(session.profile),
            aspiration=_aspiration_context(session.aspiration),
            job_analysis=_job_analysis_context(session.job_analysis),
        )
        session.status = HRVoiceInterviewSession.Status.COMPLETED
        session.pass_decision = final_result["pass"]
        session.final_feedback = final_result
        session.save(update_fields=["current_question_index", "status", "pass_decision", "final_feedback", "updated_at"])

        return Response(
            {
                "session_id": session.id,
                "status": session.status,
                "question_score": eval_result["score"],
                "evaluation": eval_result,
                "final_feedback": final_result,
                "pass": final_result["pass"],
            },
            status=status.HTTP_200_OK,
        )

    session.save(update_fields=["current_question_index", "updated_at"])
    return Response(
        {
            "session_id": session.id,
            "status": session.status,
            "question_score": eval_result["score"],
            "evaluation": eval_result,
            "next_question_index": session.current_question_index,
            "next_question": session.questions[session.current_question_index],
        },
        status=status.HTTP_200_OK,
    )


@api_view(["GET"])
@permission_classes([IsAuthenticated])
def resume_hr_voice_interview(request, session_id):
    session = get_object_or_404(
        HRVoiceInterviewSession.objects.select_related("aspiration", "job_analysis").prefetch_related("turns"),
        id=session_id,
        user=request.user,
    )

    turns = list(
        session.turns.values(
            "question",
            "answer",
            "score",
            "strengths",
            "weaknesses",
            "improvements",
            "created_at",
        )
    )

    return Response(
        {
            "session_id": session.id,
            "status": session.status,
            "question_count": len(session.questions),
            "current_question_index": session.current_question_index,
            "current_question": (
                session.questions[session.current_question_index]
                if session.status == HRVoiceInterviewSession.Status.IN_PROGRESS
                and session.current_question_index < len(session.questions)
                else ""
            ),
            "turns": turns,
            "final_feedback": session.final_feedback if session.status == HRVoiceInterviewSession.Status.COMPLETED else None,
            "pass": session.pass_decision,
            "context": {
                "target_job": session.aspiration.target_job if session.aspiration else "",
                "company_name": session.job_analysis.company_name if session.job_analysis else "",
                "profile": _profile_reference(session.profile),
            },
        },
        status=status.HTTP_200_OK,
    )
