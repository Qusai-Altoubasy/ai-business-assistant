# AI Business Assistant

A learning-focused AI Business Assistant that evolves incrementally, applying practical GenAI concepts in a Quarkus backend with a simple Flutter client.

## Tech Stack

- Java 21 and Quarkus 3.39.3
- LangChain4j AI Services with Google Gemini Developer API
- Maven Wrapper
- Flutter with Riverpod and Dio
- Docker / Docker Compose; Nginx serves the containerized Flutter Web client

## Current Learning Stage

The current implementation demonstrates a chat API, declarative AI Services, prompting basics, structured output, temperature experimentation, and basic prompt-based handling of hallucinations and missing data.

The Flutter client sends individual queries to the chat endpoint, displays responses, and supports suggested prompts, inline errors with retry, and resetting the local chat. Messages live in client state; conversation history is not sent to the backend or persisted. Sidebar modules and recent conversations are placeholders. The business-analysis endpoint is currently available through an HTTP client, with no dedicated Flutter integration.

### Prompting

- `@SystemMessage` defines the assistant's role and instructions; `@UserMessage` supplies the query.
- Both current prompts are zero-shot. One-shot / few-shot prompting is an experimentation topic; no demonstration examples are included in the current service prompts.
- Temperature is currently `0.1` in `application.properties` for experimentation.
- Structured output maps business analysis to `BusinessAnalysisDTO`.
- Prompts ask the model not to invent business data and to distinguish facts from assumptions. These instructions do not guarantee factual accuracy.

## Architecture

```text
User
  ↓
Flutter / HTTP client
  ↓
Quarkus REST API (ChatResource)
  ↓
LangChain4j AI Service (ChatService / BusinessAnalysisService)
  ↓
Google Gemini
  ↓
String / structured Java record → JSON response to the client
```

The backend follows a **feature-based architecture**: the REST resource, AI interfaces, and DTOs belong to `chat`. Both `@RegisterAiService` interfaces live in `chat.ai`; request, response, and structured-output records live in `chat.dto`.

There are no global `controller`, `service`, `dto`, or `repository` packages. Future features such as `product`, `customer`, `order`, `tools`, `memory`, and `rag` can become sibling packages under `com.aibusinessassistant`; none exists yet. PostgreSQL, Tool Calling, Memory, and RAG are planned additions.

## Project Structure

```text
ai-business-assistant/
├── Backend/                      # Quarkus API and Maven build
├── Frontend/                     # Flutter client (lib/features/chat)
├── deploy/                       # Local Docker Compose setup
└── README.md

Backend/
├── src/main/java/com/aibusinessassistant/chat/
│   ├── ChatResource.java
│   ├── ai/
│   │   ├── ChatService.java
│   │   └── BusinessAnalysisService.java
│   └── dto/
│       ├── ChatRequestDTO.java
│       ├── ChatResponseDTO.java
│       └── BusinessAnalysisDTO.java
├── src/main/resources/application.properties
├── src/test/java/com/aibusinessassistant/chat/ChatResourceTest.java
├── .mvn/wrapper/
├── .env.example
├── Dockerfile
├── mvnw
├── mvnw.cmd
└── pom.xml
```

## Configuration

The backend injects Gemini settings from environment variables:

```properties
quarkus.langchain4j.ai.gemini.api-key=${GEMINI_API_KEY}
quarkus.langchain4j.ai.gemini.chat-model.model-id=${GEMINI_MODEL}
```

| Variable | Purpose | Default |
| --- | --- | --- |
| `GEMINI_API_KEY` | Gemini Developer API key; required for AI requests | None |
| `GEMINI_MODEL` | Gemini chat model ID; required | None |
| `FRONTEND_ORIGIN` | Browser origin allowed by backend CORS | `http://127.0.0.1:3000` |
| `API_BASE_URL` | Backend URL compiled into Flutter via `--dart-define` | `http://localhost:8080` |

The Gemini chat-model temperature is set directly to `0.1` in `Backend/src/main/resources/application.properties`; there is no project-defined `TEMPERATURE` environment variable. The test profile supplies non-secret Gemini placeholders.

`Backend/.env.example` is a reference template for the required Gemini settings. Choose a model available to your API key. Supply real values through the backend's environment; no backend `.env` file is supplied. The Compose setup has its own template, described below.

## Running Locally

### Backend

Requires JDK 21 or later and a Gemini Developer API key. Maven is provided by the wrapper.

From the repository root, replace the placeholders before running:

```bash
export GEMINI_API_KEY="your-api-key"
export GEMINI_MODEL="your-gemini-model-id"
cd Backend
./mvnw quarkus:dev
```

The API listens on port `8080` by default. On Windows, use `mvnw.cmd`.

### Flutter Client

Requires Flutter with Dart 3.12 support and Chrome for the Web client. The frontend Docker build uses Flutter 3.44.0.

In a second terminal, from the repository root:

```bash
cd Frontend
flutter pub get
flutter run -d chrome \
  --web-hostname 127.0.0.1 \
  --web-port 3000 \
  --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

These settings match the backend's default CORS origin. If you change the browser origin, set `FRONTEND_ORIGIN` on the backend accordingly. Linux desktop instructions are in [Frontend/README.md](Frontend/README.md).

### Docker Compose

Requires Docker with the Compose plugin. From the repository root:

```bash
cd deploy
cp .env.example .env
```

Edit the local `.env`: supply `GEMINI_API_KEY` and select `GEMINI_MODEL`. The template also sets `FRONTEND_PORT=3000`, `BACKEND_PORT=8080`, `API_BASE_URL=http://127.0.0.1:8080`, and `FRONTEND_ORIGIN=http://127.0.0.1:3000`. Keep URLs and ports aligned if changing them. Exported shell variables take precedence over Compose's `.env` values.

Then, from `deploy/`:

```bash
docker compose config --quiet
docker compose up --build
```

Open [the Flutter client](http://127.0.0.1:3000). Both containers publish ports only on `127.0.0.1`. The browser calls the backend directly, so `API_BASE_URL` must be browser-reachable; it is compiled into the frontend image. Compose runs only the backend and frontend, with no database service.

Stop and remove the containers from `deploy/`:

```bash
docker compose down
```

## API

Both endpoints consume and produce `application/json`. Both accept `ChatRequestDTO` with a single string field, `query`.

### `POST /api/chat`

Returns free-form AI text wrapped in `ChatResponseDTO` under `response`.

```bash
curl --request POST http://localhost:8080/api/chat \
  --header 'Content-Type: application/json' \
  --data '{"query":"Hello"}'
```

Example response shape:

```json
{
  "response": "I can help answer business questions."
}
```

### `POST /api/chat/business-analysis`

Returns structured AI output mapped to `BusinessAnalysisDTO`: `summary` is a string; `insights` and `recommendations` are lists of strings.

```bash
curl --request POST http://localhost:8080/api/chat/business-analysis \
  --header 'Content-Type: application/json' \
  --data '{"query":"Our revenue increased by 20%, but profit decreased by 10%. Analyze the situation."}'
```

Illustrative response:

```json
{
  "summary": "Revenue increased by 20%, while profit decreased by 10%.",
  "insights": [
    "The supplied figures do not establish why profit declined.",
    "Higher costs are one possible explanation, not a confirmed fact."
  ],
  "recommendations": [
    "Compare costs and margins for the same periods to investigate the change."
  ]
}
```

Generated responses vary. Calling either endpoint sends the query to Gemini and may incur API usage costs.

## Verification

From the repository root, run the backend's existing endpoint test:

```bash
cd Backend
./mvnw clean test
```

It substitutes a test chat service and does not call Gemini. There is currently no dedicated business-analysis endpoint test.

For the frontend, from the repository root:

```bash
cd Frontend
flutter analyze
flutter test
```

The existing Flutter tests cover chat state handling and the remote request/response contract.

## Security

Never commit API keys. `.env` and `.env.*` files are ignored by Git, except for `.env.example` templates, which contain only placeholder or sample configuration. Keep secrets in backend environment configuration, never in Flutter build arguments or source code. The Docker Compose setup is intended for local/demo use.

## Roadmap — Planned

These capabilities are not implemented:

1. PostgreSQL seed data, Tool Calling, and read-only business tools — the next learning stage
2. Conversation Memory
3. Embeddings and pgvector
4. RAG
5. Further reliability, security, and observability work
6. AI response evaluation
7. MCP and Agents concepts

## Owner

Qusai Altoubasy
