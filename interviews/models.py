from django.conf import settings
from django.db import models


class Interview(models.Model):
    class RoundType(models.TextChoices):
        TECHNICAL = "technical", "Technical"
        MCQ = "mcq", "MCQ"
        CODING = "coding", "Coding"

    class Status(models.TextChoices):
        IN_PROGRESS = "in_progress", "In Progress"
        COMPLETED = "completed", "Completed"

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="interviews",
        null=True,
        blank=True,
    )
    topic = models.CharField(max_length=255)
    round_type = models.CharField(
        max_length=100,
        choices=RoundType.choices,
        default=RoundType.TECHNICAL,
    )
    status = models.CharField(
        max_length=50,
        choices=Status.choices,
        default=Status.IN_PROGRESS,
    )
    current_question_index = models.PositiveIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"Interview<{self.id}> {self.topic}"


class Question(models.Model):
    class Difficulty(models.TextChoices):
        EASY = "easy", "Easy"
        MEDIUM = "medium", "Medium"
        HARD = "hard", "Hard"

    interview = models.ForeignKey(
        Interview,
        on_delete=models.CASCADE,
        related_name="questions",
    )
    text = models.TextField()
    difficulty = models.CharField(
        max_length=50,
        choices=Difficulty.choices,
        default=Difficulty.EASY,
    )
    order = models.PositiveIntegerField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["order", "created_at"]
        unique_together = ("interview", "order")

    def __str__(self):
        return f"Question<{self.id}> interview={self.interview_id} order={self.order}"


class Answer(models.Model):
    class EvaluationStatus(models.TextChoices):
        PENDING = "pending", "Pending"
        COMPLETED = "completed", "Completed"

    question = models.ForeignKey(
        Question,
        on_delete=models.CASCADE,
        related_name="answers",
    )
    user_input = models.TextField()
    evaluation_status = models.CharField(
        max_length=20,
        choices=EvaluationStatus.choices,
        default=EvaluationStatus.PENDING,
    )
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"Answer<{self.id}> question={self.question_id}"


class Evaluation(models.Model):
    answer = models.OneToOneField(
        Answer,
        on_delete=models.CASCADE,
        related_name="evaluation",
    )
    score = models.PositiveIntegerField()
    strengths = models.JSONField(default=list)
    weaknesses = models.JSONField(default=list)
    improvements = models.JSONField(default=list)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"Evaluation<{self.id}> answer={self.answer_id} score={self.score}"


class PersonalCoachSession(models.Model):
    class Stage(models.TextChoices):
        SUBTOPIC_SELECTION = "subtopic_selection", "Subtopic Selection"
        LESSON_SELECTION = "lesson_selection", "Lesson Selection"
        QUESTIONING = "questioning", "Questioning"
        COMPLETED = "completed", "Completed"

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="personal_coach_sessions",
    )
    topic = models.CharField(max_length=255)
    subtopics = models.JSONField(default=list)
    lessons_by_subtopic = models.JSONField(default=dict)
    selected_subtopic = models.CharField(max_length=255, blank=True)
    selected_lesson = models.CharField(max_length=255, blank=True)
    stage = models.CharField(
        max_length=50,
        choices=Stage.choices,
        default=Stage.SUBTOPIC_SELECTION,
    )
    current_lesson = models.TextField(blank=True)
    current_question = models.TextField(blank=True)
    mastery_score = models.PositiveIntegerField(default=0)
    attempt_count = models.PositiveIntegerField(default=0)
    suggested_next_subtopic = models.CharField(max_length=255, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"PersonalCoachSession<{self.id}> {self.topic}"


class PersonalCoachAttempt(models.Model):
    class CoachDecision(models.TextChoices):
        ADVANCE = "advance", "Advance"
        REMEDIATE = "remediate", "Remediate"

    session = models.ForeignKey(
        PersonalCoachSession,
        on_delete=models.CASCADE,
        related_name="attempts",
    )
    subtopic = models.CharField(max_length=255, blank=True)
    lesson = models.CharField(max_length=255, blank=True)
    question = models.TextField()
    user_answer = models.TextField()
    score = models.PositiveIntegerField()
    strengths = models.JSONField(default=list)
    gaps = models.JSONField(default=list)
    feedback = models.TextField(blank=True)
    coach_decision = models.CharField(
        max_length=20,
        choices=CoachDecision.choices,
    )
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return f"PersonalCoachAttempt<{self.id}> session={self.session_id} score={self.score}"


class JobDescriptionAnalysis(models.Model):
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="job_description_analyses",
    )
    job_description = models.TextField()
    recruiter_name = models.CharField(max_length=255, blank=True)
    company_name = models.CharField(max_length=255, blank=True)
    application_last_date = models.DateField(null=True, blank=True)
    application_last_date_raw = models.CharField(max_length=120, blank=True)
    analysis = models.JSONField(default=dict)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-updated_at"]

    def __str__(self):
        return f"JobDescriptionAnalysis<{self.id}> user={self.user_id}"


class UserAspiration(models.Model):
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="user_aspirations",
    )
    current_position = models.CharField(max_length=255)
    target_job = models.CharField(max_length=255)
    timeline_months = models.PositiveIntegerField(default=6)
    current_skills = models.JSONField(default=list)
    constraints = models.TextField(blank=True)
    additional_context = models.TextField(blank=True)
    roadmap = models.JSONField(default=dict)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-updated_at"]

    def __str__(self):
        return f"UserAspiration<{self.id}> user={self.user_id}"


class AspirationChecklist(models.Model):
    aspiration = models.OneToOneField(
        UserAspiration,
        on_delete=models.CASCADE,
        related_name="checklist",
    )
    items = models.JSONField(default=list)
    completed_count = models.PositiveIntegerField(default=0)
    total_count = models.PositiveIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-updated_at"]

    def __str__(self):
        return f"AspirationChecklist<{self.id}> aspiration={self.aspiration_id}"


class CandidateProfile(models.Model):
    user = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="candidate_profile",
    )
    current_position = models.CharField(max_length=255, blank=True)
    current_company = models.CharField(max_length=255, blank=True)
    total_experience_years = models.DecimalField(max_digits=4, decimal_places=1, null=True, blank=True)
    primary_skills = models.JSONField(default=list)
    current_salary = models.CharField(max_length=120, blank=True)
    salary_expectation = models.CharField(max_length=120, blank=True)
    notice_period = models.CharField(max_length=120, blank=True)
    reason_for_leaving = models.TextField(blank=True)
    career_gap_details = models.TextField(blank=True)
    highest_education = models.CharField(max_length=255, blank=True)
    preferred_locations = models.JSONField(default=list)
    preferred_role = models.CharField(max_length=255, blank=True)
    additional_notes = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"CandidateProfile<{self.id}> user={self.user_id}"


class HRVoiceInterviewSession(models.Model):
    class Status(models.TextChoices):
        IN_PROGRESS = "in_progress", "In Progress"
        COMPLETED = "completed", "Completed"

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="hr_voice_interviews",
    )
    profile = models.ForeignKey(
        CandidateProfile,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="hr_sessions",
    )
    aspiration = models.ForeignKey(
        UserAspiration,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="hr_sessions",
    )
    job_analysis = models.ForeignKey(
        JobDescriptionAnalysis,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="hr_sessions",
    )
    questions = models.JSONField(default=list)
    current_question_index = models.PositiveIntegerField(default=0)
    status = models.CharField(max_length=20, choices=Status.choices, default=Status.IN_PROGRESS)
    pass_decision = models.BooleanField(null=True, blank=True)
    final_feedback = models.JSONField(default=dict)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-updated_at"]

    def __str__(self):
        return f"HRVoiceInterviewSession<{self.id}> user={self.user_id}"


class HRVoiceInterviewTurn(models.Model):
    session = models.ForeignKey(
        HRVoiceInterviewSession,
        on_delete=models.CASCADE,
        related_name="turns",
    )
    question = models.TextField()
    answer = models.TextField()
    score = models.PositiveIntegerField(default=0)
    strengths = models.JSONField(default=list)
    weaknesses = models.JSONField(default=list)
    improvements = models.JSONField(default=list)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["created_at"]

    def __str__(self):
        return f"HRVoiceInterviewTurn<{self.id}> session={self.session_id}"
