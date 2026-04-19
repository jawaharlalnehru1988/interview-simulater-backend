from celery import shared_task

from .models import Answer, Evaluation
from .services.evaluator import Evaluator


@shared_task
def evaluate_answer_task(answer_id):
    answer = Answer.objects.select_related("question").get(id=answer_id)
    result = Evaluator().evaluate(answer.question.text, answer.user_input)

    Evaluation.objects.update_or_create(
        answer=answer,
        defaults={
            "score": result["score"],
            "strengths": result["strengths"],
            "weaknesses": result["weaknesses"],
            "improvements": result["improvements"],
        },
    )
    answer.evaluation_status = Answer.EvaluationStatus.COMPLETED
    answer.save(update_fields=["evaluation_status"])

    return result
