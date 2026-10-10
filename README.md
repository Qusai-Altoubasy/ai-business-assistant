# AI Business Assistant

AI Business Assistant with a Quarkus backend, PostgreSQL business data, and a Flutter client that supports structured and streaming chat.

## Tech Stack

- Java 21 and Quarkus 3.39.3
- LangChain4j AI Services with Google Gemini Developer API and the company's Ollama server
- Maven Wrapper
- PostgreSQL 18, Hibernate ORM with Panache, and Flyway
- Flutter with Riverpod, Dio for structured requests, and `http` for Web streaming
- Docker / Docker Compose; Nginx serves the containerized Flutter Web client

## Current Features

The backend exposes structured JSON at `POST /api/chat` and JSON SSE events at `POST /api/chat/stream`. Both use the conversation's saved Gemini or Ollama provider, the same business-analysis contract, resource-based business prompt, conversation memory, and read-only LangChain4j tools for inventory, sales, customer statistics, and the current application date.

Both endpoints accept an optional `provider` (`gemini` or `ollama`). The first request saves that choice on the conversation; omitting it uses `AI_DEFAULT_PROVIDER` (default `GEMINI`). Follow-ups use the saved provider, even after the application default changes. Requesting a different provider for the same UUID returns HTTP 409 before generation or SSE starts; start a new conversation to switch. Unknown or blank providers return HTTP 400. The Flutter client currently omits this optional field, so new chats use the backend default; API clients can select either provider explicitly. V4 assigns `GEMINI` to conversations created before this feature. Concurrent requests with the same UUID are outside the supported flow; send messages sequentially.

The Flutter client offers Structured Chat and Streaming Chat in the sidebar. Structured Chat renders the `BusinessAnalysisDTO` summary, insights, and recommendations. Streaming Chat updates one assistant message as `CHUNK` events arrive and shows temporary tool progress; `DONE` finishes the response, while `ERROR` shows a safe retryable error and preserves partial text. Both modes share one screen, editable suggested prompts, and a stable UUID `conversationId`. Messages displayed in the current tab live in Riverpod state; the backend persists successful user/assistant exchanges and retains the latest 10 whole user turns as active AI context. Mode switches preserve the conversation ID; New Chat creates a new ID, clears the UI, and keeps the selected mode. The sidebar lists chat modes and example-question labels rather than stored conversations.

### Available Business Data

| Capability | Backend implementation | Current behavior |
| --- | --- | --- |
| Low-stock products | `InventoryTools.getLowStockProducts()` | Reads products whose quantity is at or below their minimum stock. |
| Product stock | `InventoryTools.getProductStock(productId)` | Reads one product's ID, name, quantity, and minimum stock. |
| Sales by period | `SalesTools.getSales(from, to)` | Returns total recorded order amounts and order count for an inclusive date range; both dates are required and the start cannot follow the end. |
| Customer purchase statistics | `CustomerTools.getCustomerStatistics(customerId)` | Returns the customer's ID/name, order count, total spent, and average order value across all recorded orders. A missing customer is rejected; an existing customer with no orders has zero totals. |
| Relative dates | `CommonTools.getCurrentDate()` | Returns the backend's current `LocalDate` for questions such as "last month". |

The seed data covers January–March 2026. A question about last month uses the backend's current date and may return no sales outside that seeded period. Customer statistics use all recorded orders, without a date filter; averages are rounded to two decimal places with `HALF_UP`. Company-policy retrieval and RAG are not implemented. PostgreSQL restores the active conversation memory after a backend restart.

### Prompting

- `@SystemMessage(fromResource = "prompts/business-analysis-system.txt")` loads the assistant's role and instructions from [the backend prompt](Backend/src/main/resources/prompts/business-analysis-system.txt); `@UserMessage` supplies the query.
- The current service prompt is zero-shot, and the chat-model temperature is `0.1` in `application.properties`.
- Structured requests map business analysis to `BusinessAnalysisDTO`; streaming requests return plain-text chunks in JSON SSE events.
- The prompt allows general business concepts within the supported inventory, products, sales, orders, and customers domains; company-specific questions use available tools as needed.
- Unrelated questions are instructed to receive an out-of-scope response. In Structured Chat that means a brief summary, empty insights, and one suggestion to ask a supported business question.
- The prompt asks the model not to invent business data, classifications, trends, or customer segments without supporting evidence. Optional suggestions must be labeled as possibilities. These instructions do not guarantee factual accuracy and are not a separate API validation layer.

## Architecture

```text
User
  ↓
Flutter / HTTP client
  ↓
Quarkus REST API (ChatResource)
  ↓
ChatService → ConversationHistoryService.findOrCreateConversation → saved provider
  ↓
AiProviderSelector → GeminiBusinessAnalysisService / OllamaBusinessAnalysisService
  ↓
GeminiAiService / OllamaAiService (named LangChain4j models with @MemoryId)
  ↔ TurnAwareChatMemory → PostgresChatMemoryStore → chat_memory_state
  ↓
Gemini / Ollama ↔ InventoryTools / SalesTools / CustomerTools → repositories → PostgreSQL
              ↔ CommonTools → current application date
  ↓
BusinessAnalysisDTO or accumulated response text → ConversationHistoryService → chat_messages
BusinessAnalysisDTO or mapped ChatEvent values → JSON sections or SSE → Flutter chat
```

The backend follows a **feature-based architecture**: the REST resource, `BusinessAnalysisService`, request/analysis DTOs, conversation history, and chat memory belong to `chat`. Inventory, sales, and customer tools live with their business features; the shared current-date tool lives in `common.tools`. System instructions live under `src/main/resources/prompts` and are packaged with the backend.

There are no global `controller`, `service`, `dto`, or `repository` packages. The sibling `product`, `customer`, and `order` packages contain four JPA entities and their Panache repositories. The selected model decides which registered read-only tools to use. Flutter receives the final analysis DTO in Structured Chat; Streaming Chat receives text chunks and tool names as SSE events, not tool arguments, results, or model thinking.

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
│   ├── ChatService.java
│   ├── ai/
│   │   ├── BusinessAnalysisService.java       # Shared contract
│   │   ├── AiProvider.java / AiProviderSelector.java
│   │   ├── GeminiAiService.java / OllamaAiService.java
│   │   └── GeminiBusinessAnalysisService.java / OllamaBusinessAnalysisService.java
│   ├── dto/                         # Request, structured response, and stream events
│   ├── history/                     # Conversations and full message history
│   └── memory/                      # Turn-aware window and PostgreSQL store
├── src/main/java/com/aibusinessassistant/product/  # Product, repository, DTOs, and InventoryTools
├── src/main/java/com/aibusinessassistant/customer/ # Customer, repository, statistics DTO and CustomerTools
├── src/main/java/com/aibusinessassistant/order/    # Order/OrderItem, repositories, sales/customer aggregates and SalesTools
├── src/main/java/com/aibusinessassistant/common/   # CommonTools (current date)
├── src/main/resources/
│   ├── application.properties
│   ├── prompts/business-analysis-system.txt
│   └── db/migration/              # V1/V2 business data, V3 chat history, V4 saved AI provider
├── src/test/java/com/aibusinessassistant/chat/ChatResourceTest.java
├── src/test/java/com/aibusinessassistant/chat/memory/  # Memory tests
├── src/test/java/com/aibusinessassistant/order/BusinessPersistenceTest.java
├── src/test/java/com/aibusinessassistant/customer/CustomerToolsTest.java
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

The backend injects named-model settings from environment variables:

```properties
app.ai.default-provider=${AI_DEFAULT_PROVIDER:GEMINI}
quarkus.langchain4j.ai.gemini.business-gemini.api-key=${GEMINI_API_KEY}
quarkus.langchain4j.ai.gemini.business-gemini.chat-model.model-id=${GEMINI_MODEL}
quarkus.langchain4j.ollama.business-ollama.base-url=${OLLAMA_BASE_URL:https://ai.llm.ensera.dev}
quarkus.langchain4j.ollama.business-ollama.chat-model.model-id=${OLLAMA_MODEL:qwen3-vl:8b-instruct-q8_0}
```

| Variable | Purpose | Default |
| --- | --- | --- |
| `GEMINI_API_KEY` | Gemini Developer API key; required for AI requests | None |
| `GEMINI_MODEL` | Gemini chat model ID; required | None |
| `AI_DEFAULT_PROVIDER` | Provider for new conversations without an explicit selection | `GEMINI` |
| `OLLAMA_BASE_URL` | Ollama server origin, without `/api/chat` | `https://ai.llm.ensera.dev` |
| `OLLAMA_MODEL` | Model deployed on the Ollama server | `qwen3-vl:8b-instruct-q8_0` |
| `OLLAMA_TIMEOUT` | Timeout for Ollama requests | `120s` |
| `FRONTEND_ORIGIN` | Browser origin allowed by backend CORS | `http://127.0.0.1:3000` |
| `API_BASE_URL` | Browser URL compiled into Flutter; Compose sends `/api/` through Nginx | `http://127.0.0.1:3000` in `deploy/.env.example` |
| `DB_USERNAME` | JDBC username for a locally run backend | `ai_business_assistant` |
| `DB_PASSWORD` | JDBC password for a locally run backend; required | None |
| `DB_URL` | JDBC URL for a locally run backend | `jdbc:postgresql://localhost:5432/ai_business_assistant` |

Compose reads `deploy/.env` and configures the backend's `DB_*` values from `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`, using `ai-business-assistant-postgres:5432` as the database address inside Docker. `POSTGRES_PORT` controls the host port (default `5432`). See [deploy/.env.example](deploy/.env.example). For Maven, load the same file into the shell as shown below; Quarkus reads the exported variables.

Both named chat models use temperature `0.1` in `Backend/src/main/resources/application.properties`; there is no project-defined `TEMPERATURE` environment variable. The test profile supplies non-secret placeholders for both providers; endpoint tests replace the model-facing AI services.

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

Requires Docker with the Compose plugin and the `deploy/.env` configured above. The template sets `FRONTEND_PORT=3000`, `BACKEND_PORT=8080`, `API_BASE_URL=http://127.0.0.1:3000`, and `FRONTEND_ORIGIN=http://127.0.0.1:3000`. Keep URLs and ports aligned if changing them. Exported shell variables take precedence over Compose's `.env` values.

From the repository root:

```bash
cd deploy
docker compose config --quiet
docker compose up --build
```

Open [the Flutter client](http://127.0.0.1:3000). All three services publish ports only on `127.0.0.1`. The browser sends API requests to Nginx at the frontend origin; Nginx forwards them to the backend by container name. The backend connects to PostgreSQL by container name and waits for its healthcheck before starting.

Stop and remove the containers from `deploy/`:

```bash
docker compose down
```

PostgreSQL stores data in the named volume `ai-business-assistant-postgres-data`. Container recreation and `docker compose down` preserve it; `docker compose down -v` deletes it. `POSTGRES_DB` creates the database only on the first initialization of an empty volume. Changing `POSTGRES_*` later does not reconfigure an existing database.

On backend startup, Flyway applies `V1__create_business_schema.sql`, `V2__seed_business_data.sql`, `V3__add_persistent_chat_history.sql`, and `V4__add_conversation_ai_provider.sql` inside that database. V3 creates `conversations`, append-only `chat_messages`, and `chat_memory_state` for the active AI context. V4 adds the saved provider and backfills existing conversations with `GEMINI`. Hibernate then validates the schema; it never creates or updates tables. V2 seeds 10 products, 5 customers, 12 orders, and 24 order items across January–March 2026, including low-stock products. Order-item prices are historical unit prices; each order total matches its line items. Migrations run once and are tracked in `flyway_schema_history`; evolve the schema with new migrations instead of editing applied ones.

## API

Both chat endpoints consume `application/json` with `ChatRequestDTO`: a required UUID `conversationId`, a `query` string, and an optional `provider` (`gemini` or `ollama`). Reuse the ID and its saved provider for follow-up questions; use a new UUID to change providers or start a new chat.

### `POST /api/chat`

Returns structured AI output mapped to `BusinessAnalysisDTO`: `summary` is a string; `insights` and `recommendations` are lists of strings. Flutter uses this endpoint in Structured Chat. There is no separate business-analysis route or free-form response endpoint.

```bash
curl --request POST http://localhost:8080/api/chat \
  --header 'Content-Type: application/json' \
  --data '{"conversationId":"1f5299c7-84a5-4a89-a5e4-1058debf4a31","query":"Do we have any products that need restocking?"}'
```

Illustrative response:

```json
{
  "summary": "Three products currently need restocking.",
  "insights": [
    "USB-C Cable has the largest gap between current and minimum stock."
  ],
  "recommendations": [
    "Prioritize replenishing USB-C Cable and the other low-stock products."
  ]
}
```

Generated responses vary. Calling the endpoint sends the query to the saved provider; Gemini may incur API usage costs. Flutter preserves the structured fields and hides empty insight/recommendation sections.

Other supported queries include `"How much did we sell from January 1 to March 31, 2026?"`, `"What is the current stock for product ID 1?"`, and `"What are the purchase statistics for customer ID 1?"`. With the original seed data, customer 1 (Maya Reed) has three orders totaling `483.00`, with an average order value of `161.00`. These values are internal tool data; the HTTP response remains the same three analysis fields.

### `POST /api/chat/stream`

Returns `text/event-stream` with JSON `data:` values. Events can be `TOOL_STARTED`, `TOOL_COMPLETED`, `CHUNK`, `DONE`, or `ERROR`. Tool events expose a tool name, not its arguments or result. Text arrives in actual provider chunks; `DONE` follows successful history persistence, while failures produce a safe `ERROR` without `DONE`.

```bash
curl -N http://localhost:8080/api/chat/stream \
  --header 'Content-Type: application/json' \
  --header 'Accept: text/event-stream' \
  --data '{"conversationId":"1f5299c7-84a5-4a89-a5e4-1058debf4a31","query":"Which products are low stock?"}'
```

The Flutter client parses SSE framing and UTF-8 across network chunks. It appends each `CHUNK` to one assistant message, presents tool activity as temporary status, and can abort a stream when starting a New Chat or disposing the screen. Nginx disables API proxy buffering for the containerized Web client.

## Verification

Start PostgreSQL and load `deploy/.env` into the shell with the JDBC mappings shown above, then run the backend build and tests from the repository root:

```bash
cd Backend
./mvnw clean verify
```

The endpoint tests substitute the model-facing `GeminiAiService` and `OllamaAiService`, exercising the real selector and adapters. They verify saved/default provider selection, cross-mode follow-ups, 400/409 rejection without model calls, structured fields, stream event order and tool filtering, UUID validation, history persistence, and safe error events. The migration test verifies legacy Gemini backfill. Persistence tests verify seeded repository reads, order totals, relationships, generated IDs, conversation isolation, and retained history after memory eviction. Three customer-tool tests exercise database aggregates, average rounding, a customer without orders, and a missing customer. Turn-aware memory tests verify whole-turn eviction and restored windows. Tests do not call either model. Persistence test transactions roll back, but endpoint tests commit chat records and PostgreSQL identity sequences still advance. Prefer a disposable development database with the original seed data; tests use the configured `DB_*` connection. Live model tool selection and AI response quality are not covered by this suite.

For the frontend, from the repository root:

```bash
cd Frontend
flutter analyze
flutter test
flutter build web --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

The Flutter tests cover structured parsing and rendering, SSE framing and UTF-8, progressive messages and tool status, normalized failures and retry, UUID reuse, mode switching, suggested prompts, and reset/cancellation behavior.

## Security

Never commit API keys. Keep real secrets in local environment files or backend environment configuration, never in Flutter build arguments or source code. The Docker Compose setup binds its published ports to `127.0.0.1`.

## Roadmap

1. **Completed:** PostgreSQL persistence, structured and streaming chat integration, conversation history and turn-aware memory, low-stock and product-stock queries, sales summaries, customer purchase statistics, current-date Tool Calling, and a resource-based business prompt.
2. Additional explicit read-only business tools beyond the current inventory, sales, and customer aggregates.
3. Conversation history retrieval and resume UI.
4. Embeddings and pgvector.
5. RAG.
6. Further reliability, security, and observability work.
7. AI response evaluation.
8. Potential MCP and agent integration.

## Owner

Qusai Altoubasy
