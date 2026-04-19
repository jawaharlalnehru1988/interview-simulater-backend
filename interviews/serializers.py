from django.contrib.auth import get_user_model
from rest_framework import serializers

from .models import Interview


class RegisterSerializer(serializers.Serializer):
    username = serializers.CharField(max_length=150)
    email = serializers.EmailField(required=False, allow_blank=True)
    password = serializers.CharField(min_length=8, write_only=True)

    def validate_username(self, value):
        User = get_user_model()
        if User.objects.filter(username=value).exists():
            raise serializers.ValidationError("Username already exists.")
        return value

    def create(self, validated_data):
        User = get_user_model()
        return User.objects.create_user(
            username=validated_data["username"],
            email=validated_data.get("email", ""),
            password=validated_data["password"],
        )


class StartInterviewSerializer(serializers.Serializer):
    topic = serializers.CharField(max_length=255)
    round = serializers.ChoiceField(
        choices=[choice for choice, _ in Interview.RoundType.choices],
        default=Interview.RoundType.TECHNICAL,
    )


class SubmitAnswerSerializer(serializers.Serializer):
    question_id = serializers.IntegerField(min_value=1)
    answer = serializers.CharField(allow_blank=False)


class NextQuestionResponseSerializer(serializers.Serializer):
    interview_id = serializers.IntegerField(min_value=1)
    question_id = serializers.IntegerField(min_value=1)
    question = serializers.CharField(allow_blank=False)
    difficulty = serializers.ChoiceField(choices=["easy", "medium", "hard"])
    question_number = serializers.IntegerField(min_value=1)
    status = serializers.ChoiceField(choices=[choice for choice, _ in Interview.Status.choices])
    suggested_answer = serializers.CharField(allow_blank=False)
    mcq_options = serializers.ListField(
        child=serializers.CharField(allow_blank=False),
        required=False,
        default=list,
    )


class StartPersonalCoachSerializer(serializers.Serializer):
    topic = serializers.CharField(max_length=255, allow_blank=False)


class ChooseSubtopicSerializer(serializers.Serializer):
    subtopic = serializers.CharField(max_length=255, allow_blank=False)


class ChooseLessonSerializer(serializers.Serializer):
    lesson = serializers.CharField(max_length=255, allow_blank=False)


class CoachAnswerSerializer(serializers.Serializer):
    answer = serializers.CharField(allow_blank=False)


class CoachExplainSerializer(serializers.Serializer):
    question = serializers.CharField(allow_blank=False)


class JobDescriptionAnalyzeSerializer(serializers.Serializer):
    job_description = serializers.CharField(max_length=20000, allow_blank=False)
    recruiter_name = serializers.CharField(max_length=255, required=False, allow_blank=True)
    company_name = serializers.CharField(max_length=255, required=False, allow_blank=True)
    application_last_date = serializers.DateField(required=False)


class UserAspirationSerializer(serializers.Serializer):
    current_position = serializers.CharField(max_length=255, allow_blank=False)
    target_job = serializers.CharField(max_length=255, allow_blank=False)
    timeline_months = serializers.IntegerField(min_value=1, max_value=120, required=False, default=6)
    current_skills = serializers.ListField(
        child=serializers.CharField(max_length=100),
        required=False,
        default=list,
    )
    constraints = serializers.CharField(required=False, allow_blank=True, default="")
    additional_context = serializers.CharField(required=False, allow_blank=True, default="")


class AspirationChecklistGenerateSerializer(serializers.Serializer):
    force_regenerate = serializers.BooleanField(required=False, default=False)


class AspirationChecklistToggleSerializer(serializers.Serializer):
    item_id = serializers.CharField(max_length=80, allow_blank=False)
    completed = serializers.BooleanField()


class CandidateProfileSerializer(serializers.Serializer):
    current_position = serializers.CharField(max_length=255, required=False, allow_blank=True, default="")
    current_company = serializers.CharField(max_length=255, required=False, allow_blank=True, default="")
    total_experience_years = serializers.DecimalField(
        max_digits=4,
        decimal_places=1,
        required=False,
        allow_null=True,
    )
    primary_skills = serializers.ListField(
        child=serializers.CharField(max_length=80),
        required=False,
        default=list,
    )
    current_salary = serializers.CharField(max_length=120, required=False, allow_blank=True, default="")
    salary_expectation = serializers.CharField(max_length=120, required=False, allow_blank=True, default="")
    notice_period = serializers.CharField(max_length=120, required=False, allow_blank=True, default="")
    reason_for_leaving = serializers.CharField(required=False, allow_blank=True, default="")
    career_gap_details = serializers.CharField(required=False, allow_blank=True, default="")
    highest_education = serializers.CharField(max_length=255, required=False, allow_blank=True, default="")
    preferred_locations = serializers.ListField(
        child=serializers.CharField(max_length=80),
        required=False,
        default=list,
    )
    preferred_role = serializers.CharField(max_length=255, required=False, allow_blank=True, default="")
    additional_notes = serializers.CharField(required=False, allow_blank=True, default="")


class StartHRVoiceInterviewSerializer(serializers.Serializer):
    aspiration_id = serializers.IntegerField(required=False, min_value=1)
    jd_analysis_id = serializers.IntegerField(required=False, min_value=1)
    question_count = serializers.IntegerField(required=False, min_value=10, max_value=15, default=12)


class HRVoiceAnswerSerializer(serializers.Serializer):
    answer = serializers.CharField(allow_blank=False)
