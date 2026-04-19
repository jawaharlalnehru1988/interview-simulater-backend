from collections import defaultdict


class AspirationChecklistService:
    def build_items(self, roadmap, timeline_months):
        total_weeks = max(1, int(timeline_months) * 4)
        items = []

        phases = roadmap.get("roadmap_phases") if isinstance(roadmap, dict) else []
        if not isinstance(phases, list):
            phases = []

        week_bucket = 1
        for phase_index, phase in enumerate(phases):
            if not isinstance(phase, dict):
                continue

            actions = phase.get("actions") if isinstance(phase.get("actions"), list) else []
            deliverables = phase.get("deliverables") if isinstance(phase.get("deliverables"), list) else []

            for action_index, action in enumerate(actions):
                if not str(action).strip():
                    continue
                week = min(total_weeks, week_bucket)
                items.append(
                    {
                        "id": f"phase-{phase_index}-action-{action_index}",
                        "week": week,
                        "category": "phase_action",
                        "title": str(action).strip(),
                        "completed": False,
                    }
                )
                week_bucket += 1

            for deliverable_index, deliverable in enumerate(deliverables):
                if not str(deliverable).strip():
                    continue
                week = min(total_weeks, week_bucket)
                items.append(
                    {
                        "id": f"phase-{phase_index}-deliverable-{deliverable_index}",
                        "week": week,
                        "category": "deliverable",
                        "title": f"Deliverable: {str(deliverable).strip()}",
                        "completed": False,
                    }
                )
                week_bucket += 1

        weekly_execution = roadmap.get("weekly_execution") if isinstance(roadmap, dict) else []
        if isinstance(weekly_execution, list):
            for index, item in enumerate(weekly_execution):
                if not str(item).strip():
                    continue
                week = min(total_weeks, (index % total_weeks) + 1)
                items.append(
                    {
                        "id": f"weekly-{index}",
                        "week": week,
                        "category": "weekly_execution",
                        "title": str(item).strip(),
                        "completed": False,
                    }
                )

        interview_preparation = roadmap.get("interview_preparation") if isinstance(roadmap, dict) else []
        if isinstance(interview_preparation, list):
            for index, item in enumerate(interview_preparation):
                if not str(item).strip():
                    continue
                week = min(total_weeks, ((index + 1) % total_weeks) + 1)
                items.append(
                    {
                        "id": f"interview-{index}",
                        "week": week,
                        "category": "interview_prep",
                        "title": str(item).strip(),
                        "completed": False,
                    }
                )

        if not items:
            for week in range(1, min(total_weeks, 4) + 1):
                items.append(
                    {
                        "id": f"fallback-{week}",
                        "week": week,
                        "category": "weekly_execution",
                        "title": "Complete one meaningful step toward your target role this week.",
                        "completed": False,
                    }
                )

        return items

    @staticmethod
    def update_completion(items, item_id, completed):
        updated = []
        found = False
        for item in items:
            if not isinstance(item, dict):
                continue
            new_item = dict(item)
            if str(new_item.get("id")) == str(item_id):
                new_item["completed"] = bool(completed)
                found = True
            updated.append(new_item)

        completed_count = sum(1 for item in updated if item.get("completed"))
        return updated, found, completed_count, len(updated)

    @staticmethod
    def group_by_week(items):
        grouped = defaultdict(list)
        for item in items:
            if not isinstance(item, dict):
                continue
            week = int(item.get("week", 1))
            grouped[week].append(item)

        return [
            {
                "week": week,
                "items": grouped[week],
            }
            for week in sorted(grouped.keys())
        ]
