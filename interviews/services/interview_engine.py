from ..models import Interview


def resolve_next_difficulty(interview):
    latest_evaluation = (
        interview.questions.order_by("-order")
        .values_list("answers__evaluation__score", flat=True)
        .first()
    )

    if latest_evaluation is None:
        return "easy"
    if latest_evaluation >= 80:
        return "hard"
    if latest_evaluation >= 60:
        return "medium"
    return "easy"


def maybe_complete_interview(interview, max_questions=10):
    if interview.current_question_index >= max_questions:
        interview.status = Interview.Status.COMPLETED
        interview.save(update_fields=["status", "updated_at"])
