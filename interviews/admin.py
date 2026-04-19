from django.contrib import admin

from .models import Answer, Evaluation, Interview, Question


@admin.register(Interview)
class InterviewAdmin(admin.ModelAdmin):
	list_display = ("id", "user", "topic", "round_type", "status", "current_question_index", "created_at")
	list_filter = ("round_type", "status")
	search_fields = ("topic",)


@admin.register(Question)
class QuestionAdmin(admin.ModelAdmin):
	list_display = ("id", "interview", "difficulty", "order", "created_at")
	list_filter = ("difficulty",)
	search_fields = ("text",)


@admin.register(Answer)
class AnswerAdmin(admin.ModelAdmin):
	list_display = ("id", "question", "evaluation_status", "created_at")
	search_fields = ("user_input",)


@admin.register(Evaluation)
class EvaluationAdmin(admin.ModelAdmin):
	list_display = ("id", "answer", "score", "created_at")
