# Interview Simulator Backend (MVP)

Django + DRF backend for an AI-powered interview simulator.

## Features

- Start interview sessions by topic and round type
- Generate adaptive next questions
- Submit answers for AI evaluation
- Persist interview state, questions, answers, and evaluations

## Quick Start

```bash
cd /var/www/interview-simulator
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python manage.py migrate
python manage.py runserver 0.0.0.0:8000
```

## API Endpoints

- `POST /api/interview/auth/register/`
- `POST /api/auth/token/`
- `POST /api/auth/token/refresh/`
- `POST /api/interview/start/`
- `GET /api/interview/<interview_id>/next/`
- `POST /api/interview/answer/`
- `GET /api/interview/<interview_id>/summary/`
- `GET /health/`

## Sample Requests

Register:

```bash
curl -X POST http://127.0.0.1:8000/api/interview/auth/register/ \
  -H "Content-Type: application/json" \
  -d '{"username": "demo", "password": "demoPass123"}'
```

Get token:

```bash
curl -X POST http://127.0.0.1:8000/api/auth/token/ \
  -H "Content-Type: application/json" \
  -d '{"username": "demo", "password": "demoPass123"}'
```

Start interview:

```bash
curl -X POST http://127.0.0.1:8000/api/interview/start/ \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"topic": "System Design", "round": "technical"}'
```

Next question:

```bash
curl http://127.0.0.1:8000/api/interview/1/next/ \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

Submit answer:

```bash
curl -X POST http://127.0.0.1:8000/api/interview/answer/ \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"question_id": 1, "answer": "I would design with horizontal scaling and caching."}'
```

Summary report:

```bash
curl http://127.0.0.1:8000/api/interview/1/summary/ \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

## Reliability Settings

Configure LLM request behavior in `.env`:

- `LLM_REQUEST_TIMEOUT` request timeout in seconds
- `LLM_MAX_RETRIES` retry count for transient upstream failures
- `LLM_RETRY_BACKOFF_SECONDS` linear retry backoff in seconds

## Validation Commands

Run API tests and configuration checks:

```bash
cd /var/www/interview-simulator
source venv/bin/activate
python manage.py test
python manage.py check
```
