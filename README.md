# AI Business Assistant

[![Backend CI](https://github.com/Qusai-Altoubasy/ai-business-assistant/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Qusai-Altoubasy/ai-business-assistant/actions/workflows/backend-ci.yml)

AI Business Assistant answers business questions about inventory, sales, customers and company policies. A Quarkus backend reads business data from PostgreSQL through read-only tools, searches the company policies in a pgvector knowledge base, and answers with Gemini or the company's Ollama server. A Flutter client offers structured and streaming chat.

- `POST /api/chat` answers data questions and, with Gemini, combines them with the company policies, returning the cited policy rules as `sources`. It returns JSON, or SSE events when the request sends `Accept: text/event-stream`, and keeps a chat memory per conversation.
- `POST /api/knowledge/answer` answers a policy question only from the retrieved policy rules, with the rules it cites.

## Tech Stack

- Java 21 and Quarkus 3.39.3
- LangChain4j AI Services with the Google Gemini Developer API (chat and embeddings) and the company's Ollama server
- PostgreSQL 18 with pgvector, Hibernate ORM with Panache, and Flyway
- Flutter with Riverpod
- Docker Compose; Nginx serves the containerized Flutter Web client

## Quick Start

Requires Docker with the Compose plugin and a Gemini Developer API key. From the repository root, create the local environment file from the committed template:

```bash
cp deploy/.env.example deploy/.env
```

Edit `deploy/.env`: set `GEMINI_API_KEY` and a nonempty `POSTGRES_PASSWORD`, and check `GEMINI_MODEL`. Git ignores this file. Then build and start PostgreSQL, the backend and the frontend:

```bash
cd deploy
docker compose up -d --build
```

Open [the Flutter client](http://127.0.0.1:3000); the backend listens on <http://127.0.0.1:8080>. On startup the backend applies the database migrations, including the seed data, and indexes the knowledge base. Stop the containers from `deploy/` with:

```bash
docker compose down
```

## Documentation

| Document | Covers |
| --- | --- |
| [Backend/README.md](Backend/README.md) | Architecture, configuration, running with Maven, database and migrations, knowledge base, API with example questions, refusals and errors, tests and the evaluation |
| [deploy/README.md](deploy/README.md) | Docker Compose services, variables, commands and rebuilds |
| [Frontend/README.md](Frontend/README.md) | The Flutter client: behaviour, running on the Web or Linux, tests |

## Project Structure

```text
ai-business-assistant/
├── Backend/      # Quarkus API, knowledge base, Flyway migrations and Maven build
├── Frontend/     # Flutter client
├── deploy/       # Docker Compose setup and environment template
└── .github/      # Backend CI workflow
```

## Security

Never commit API keys. Keep real secrets in `deploy/.env` or the backend's environment, never in Flutter build arguments or source code. The Docker Compose setup binds its published ports to `127.0.0.1`.

## Owner

Qusai Altoubasy
