# AI Business Assistant

A learning-focused AI Business Assistant that evolves incrementally, applying practical GenAI concepts in a Quarkus backend with a simple Flutter client.

## Tech Stack

- Java 21 and Quarkus 3.39.3
- LangChain4j AI Services with Google Gemini Developer API
- Maven Wrapper
- PostgreSQL 18, Hibernate ORM with Panache, and Flyway
- Flutter with Riverpod and Dio
- Docker / Docker Compose; Nginx serves the containerized Flutter Web client

## Current Learning Stage

The current implementation demonstrates a chat API, declarative AI Services, prompting basics, structured output, temperature experimentation, a read-only database-backed LangChain4j tool, and basic prompt-based handling of hallucinations and missing data.

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
Google Gemini ↔ InventoryTool → ProductRepository → PostgreSQL
  ↓
String / structured Java record → JSON response to the client
```

The backend follows a **feature-based architecture**: the REST resource, AI interfaces, and DTOs belong to `chat`. Both `@RegisterAiService` interfaces live in `chat.ai`; request, response, and structured-output records live in `chat.dto`.

There are no global `controller`, `service`, `dto`, or `repository` packages. The sibling `product`, `customer`, and `order` packages contain four JPA entities and their Panache repositories. `BusinessAnalysisService` exposes `InventoryTool` to Gemini so inventory questions can query current low-stock products through `ProductRepository`. Memory and RAG remain planned additions.

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
├── src/main/java/com/aibusinessassistant/product/  # Product, repository, inventory DTO, and tool
├── src/main/java/com/aibusinessassistant/customer/ # Customer + CustomerRepository
├── src/main/java/com/aibusinessassistant/order/    # Order/OrderItem + repositories
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/              # Flyway V1 schema and V2 seed data
├── src/test/java/com/aibusinessassistant/chat/ChatResourceTest.java
├── src/test/java/com/aibusinessassistant/order/BusinessPersistenceTest.java
├── .mvn/wrapper/
├── Dockerfile
├── mvnw
├── mvnw.cmd
└── pom.xml
```

## Configuration

Use `deploy/.env.example` as the single committed environment template and `deploy/.env` as the local environment file. From the repository root, copy it once:

```bash
cp deploy/.env.example deploy/.env
```

Edit `deploy/.env`: supply `GEMINI_API_KEY`, select `GEMINI_MODEL`, set a nonempty `POSTGRES_PASSWORD`, and configure the ports and other PostgreSQL settings. Keep all settings from the template in this file. If it already exists, update it instead of overwriting it. Git ignores `deploy/.env` and tracks the template.

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
| `DB_USERNAME` | JDBC username for a locally run backend | `ai_business_assistant` |
| `DB_PASSWORD` | JDBC password for a locally run backend; required | None |
| `DB_URL` | JDBC URL for a locally run backend | `jdbc:postgresql://localhost:5432/ai_business_assistant` |

Compose reads `deploy/.env` and configures the backend's `DB_*` values from `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`, using `postgres:5432` as the database host. `POSTGRES_PORT` controls the host port (default `5432`). See [deploy/.env.example](deploy/.env.example). For Maven, load the same file into the shell as shown below; Quarkus reads the exported variables.

The Gemini chat-model temperature is set directly to `0.1` in `Backend/src/main/resources/application.properties`; there is no project-defined `TEMPERATURE` environment variable. The test profile supplies non-secret Gemini placeholders.

## Running Locally

### Backend

Requires JDK 21 or later, PostgreSQL, and a Gemini Developer API key. Maven is provided by the wrapper.

After completing the configuration above, start only PostgreSQL from the repository root (Docker with Compose required):

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --wait postgres
```

For a local Maven run in a POSIX shell, load the same `deploy/.env` from the repository root and map its PostgreSQL settings to JDBC variables. Keep the file shell-compatible, quoting values containing spaces or shell special characters:

```bash
set -a
. ./deploy/.env
set +a
export DB_URL="jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/${POSTGRES_DB}"
export DB_USERNAME="$POSTGRES_USER"
export DB_PASSWORD="$POSTGRES_PASSWORD"
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

Requires Docker with the Compose plugin and the `deploy/.env` configured above. The template sets `FRONTEND_PORT=3000`, `BACKEND_PORT=8080`, `API_BASE_URL=http://127.0.0.1:8080`, and `FRONTEND_ORIGIN=http://127.0.0.1:3000`. Keep URLs and ports aligned if changing them. Exported shell variables take precedence over Compose's `.env` values.

From the repository root:

```bash
cd deploy
docker compose config --quiet
docker compose up --build
```

Open [the Flutter client](http://127.0.0.1:3000). All three services publish ports only on `127.0.0.1`. The browser calls the backend directly, so `API_BASE_URL` must be browser-reachable; it is compiled into the frontend image. The backend waits for PostgreSQL's healthcheck before starting.

Stop and remove the containers from `deploy/`:

```bash
docker compose down
```

PostgreSQL stores data in the named volume `ai-business-assistant-postgres-data`. Container recreation and `docker compose down` preserve it; `docker compose down -v` deletes it. `POSTGRES_DB` creates the database only on the first initialization of an empty volume. Changing `POSTGRES_*` later does not reconfigure an existing database.

On backend startup, Flyway applies `V1__create_business_schema.sql` and `V2__seed_business_data.sql` inside that database. Hibernate then validates the schema; it never creates or updates tables. V2 seeds 10 products, 5 customers, 12 orders, and 24 order items across January–March 2026, including low-stock products. Order-item prices are historical unit prices; each order total matches its line items. Migrations run once and are tracked in `flyway_schema_history`; evolve the schema with new migrations instead of editing applied ones.

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

Returns structured AI output mapped to `BusinessAnalysisDTO`: `summary` is a string; `insights` and `recommendations` are lists of strings. For current inventory questions, Gemini can invoke the read-only `getLowStockProducts()` tool, which queries PostgreSQL and returns products whose stock is at or below their configured minimum.

```bash
curl --request POST http://localhost:8080/api/chat/business-analysis \
  --header 'Content-Type: application/json' \
  --data '{"query":"Do we have any products that need restocking?"}'
```

Illustrative response:

```json
{
  "summary": "Three products currently need restocking.",
  "insights": [
    "USB-C Cable has the largest gap between current and minimum stock."
  ],
  "recommendations": [
    "Prioritize replenishing the products returned by the inventory tool."
  ]
}
```

Generated responses vary. Calling either endpoint sends the query to Gemini and may incur API usage costs.

## Verification

Start PostgreSQL and load `deploy/.env` into the shell with the JDBC mappings shown above, then run the backend build and tests from the repository root:

```bash
cd Backend
./mvnw clean verify
```

The endpoint test substitutes a test chat service and does not call Gemini. Persistence tests verify both migrations, seeded repository reads, order totals, relationships, and generated IDs after seeding. Test inserts roll back, though PostgreSQL identity sequences still advance. Use a development database with the original seed data; tests use the configured `DB_*` connection. There is currently no dedicated business-analysis endpoint test.

For the frontend, from the repository root:

```bash
cd Frontend
flutter analyze
flutter test
```

The existing Flutter tests cover chat state handling and the remote request/response contract.

## Security

Never commit API keys. Keep real secrets in local environment files or backend environment configuration, never in Flutter build arguments or source code. The Docker Compose setup is intended for local/demo use.

## Roadmap

1. **Completed:** PostgreSQL persistence and the read-only `getLowStockProducts()` LangChain4j tool.
2. Additional explicit business tools for sales, product stock, and customer statistics.
3. Conversation Memory.
4. Embeddings and pgvector.
5. RAG.
6. Further reliability, security, and observability work.
7. AI response evaluation.
8. MCP and Agents concepts.

## Owner

Qusai Altoubasy
