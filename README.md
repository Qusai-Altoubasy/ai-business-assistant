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

The current implementation demonstrates a structured chat API, declarative AI Services, a resource-based business prompt, temperature experimentation, and read-only LangChain4j tools for inventory, sales, customer statistics, and the current application date.

The Flutter client sends individual queries to `POST /api/chat` and renders the `BusinessAnalysisDTO` response as summary, insights, and recommendations. It supports editable suggested prompts, inline errors with retry, and resetting the local chat. Messages live in Riverpod state; conversation history is not sent to the backend or persisted. Sales, inventory, customer statistics, and business-data capabilities are available through chat; independent sidebar screens remain placeholders, and the sidebar lists example questions rather than stored conversations.

### Available Business Data

| Capability | Backend implementation | Current behavior |
| --- | --- | --- |
| Low-stock products | `InventoryTools.getLowStockProducts()` | Reads products whose quantity is at or below their minimum stock. |
| Product stock | `InventoryTools.getProductStock(productId)` | Reads one product's ID, name, quantity, and minimum stock. |
| Sales by period | `SalesTools.getSales(from, to)` | Returns total recorded order amounts and order count for an inclusive date range; both dates are required and the start cannot follow the end. |
| Customer purchase statistics | `CustomerTools.getCustomerStatistics(customerId)` | Returns the customer's ID/name, order count, total spent, and average order value across all recorded orders. A missing customer is rejected; an existing customer with no orders has zero totals. |
| Relative dates | `CommonTools.getCurrentDate()` | Returns the backend's current `LocalDate` for questions such as "last month". |

The seed data covers January–March 2026. A question about last month uses the backend's current date and may return no sales outside that seeded period. Customer statistics use all recorded orders, without a date filter; averages are rounded to two decimal places with `HALF_UP`. Company-policy retrieval, RAG, and conversation memory are not implemented.

### Prompting

- `@SystemMessage(fromResource = "prompts/business-analysis-system.txt")` loads the assistant's role and instructions from [the backend prompt](Backend/src/main/resources/prompts/business-analysis-system.txt); `@UserMessage` supplies the query.
- The current service prompt is zero-shot. One-shot / few-shot prompting remains an experimentation topic.
- Temperature is currently `0.1` in `application.properties` for experimentation.
- Structured output maps business analysis to `BusinessAnalysisDTO`.
- The prompt allows general business concepts within the supported inventory, products, sales, orders, and customers domains; company-specific questions use available tools as needed.
- Unrelated questions are instructed to return a structured out-of-scope response: a brief summary, empty insights, and one suggestion to ask a supported business question.
- The prompt asks the model not to invent business data, classifications, trends, or customer segments without supporting evidence. Optional suggestions must be labeled as possibilities. These instructions do not guarantee factual accuracy and are not a separate API validation layer.

## Architecture

```text
User
  ↓
Flutter / HTTP client
  ↓
Quarkus REST API (ChatResource)
  ↓
LangChain4j AI Service (BusinessAnalysisService)
  ↓
Google Gemini ↔ InventoryTools / SalesTools / CustomerTools → repositories → PostgreSQL
              ↔ CommonTools → current application date
  ↓
BusinessAnalysisDTO → JSON → Flutter analysis sections
```

The backend follows a **feature-based architecture**: the REST resource, `BusinessAnalysisService`, and request/analysis DTOs belong to `chat`. Inventory, sales, and customer tools live with their business features; the shared current-date tool lives in `common.tools`. System instructions live under `src/main/resources/prompts` and are packaged with the backend.

There are no global `controller`, `service`, `dto`, or `repository` packages. The sibling `product`, `customer`, and `order` packages contain four JPA entities and their Panache repositories. Gemini decides which registered read-only tools to use. Flutter consumes only the final analysis DTO and does not receive tool execution details.

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
│   │   └── BusinessAnalysisService.java
│   └── dto/
│       ├── ChatRequestDTO.java
│       └── BusinessAnalysisDTO.java
├── src/main/java/com/aibusinessassistant/product/  # Product, repository, DTOs, and InventoryTools
├── src/main/java/com/aibusinessassistant/customer/ # Customer, repository, statistics DTO and CustomerTools
├── src/main/java/com/aibusinessassistant/order/    # Order/OrderItem, repositories, sales/customer aggregates and SalesTools
├── src/main/java/com/aibusinessassistant/common/   # CommonTools (current date)
├── src/main/resources/
│   ├── application.properties
│   ├── prompts/business-analysis-system.txt
│   └── db/migration/              # Flyway V1 schema and V2 seed data
├── src/test/java/com/aibusinessassistant/chat/ChatResourceTest.java
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
| `API_BASE_URL` | Browser URL compiled into Flutter; Compose sends `/api/` through Nginx | `http://127.0.0.1:3000` in `deploy/.env.example` |
| `DB_USERNAME` | JDBC username for a locally run backend | `ai_business_assistant` |
| `DB_PASSWORD` | JDBC password for a locally run backend; required | None |
| `DB_URL` | JDBC URL for a locally run backend | `jdbc:postgresql://localhost:5432/ai_business_assistant` |

Compose reads `deploy/.env` and configures the backend's `DB_*` values from `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`, using `ai-business-assistant-postgres:5432` as the database address inside Docker. `POSTGRES_PORT` controls the host port (default `5432`). See [deploy/.env.example](deploy/.env.example). For Maven, load the same file into the shell as shown below; Quarkus reads the exported variables.

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

On backend startup, Flyway applies `V1__create_business_schema.sql` and `V2__seed_business_data.sql` inside that database. Hibernate then validates the schema; it never creates or updates tables. V2 seeds 10 products, 5 customers, 12 orders, and 24 order items across January–March 2026, including low-stock products. Order-item prices are historical unit prices; each order total matches its line items. Migrations run once and are tracked in `flyway_schema_history`; evolve the schema with new migrations instead of editing applied ones.

## API

The chat endpoint consumes and produces `application/json`. It accepts `ChatRequestDTO` with a single string field, `query`.

### `POST /api/chat`

Returns structured AI output mapped to `BusinessAnalysisDTO`: `summary` is a string; `insights` and `recommendations` are lists of strings. This is the endpoint used by Flutter. There is no separate business-analysis route or free-form response endpoint.

```bash
curl --request POST http://localhost:8080/api/chat \
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
    "Prioritize replenishing USB-C Cable and the other low-stock products."
  ]
}
```

Generated responses vary. Calling the endpoint sends the query to Gemini and may incur API usage costs. Flutter preserves the structured fields and hides empty insight/recommendation sections.

Other supported queries include `"How much did we sell from January 1 to March 31, 2026?"`, `"What is the current stock for product ID 1?"`, and `"What are the purchase statistics for customer ID 1?"`. With the original seed data, customer 1 (Maya Reed) has three orders totaling `483.00`, with an average order value of `161.00`. These values are internal tool data; the HTTP response remains the same three analysis fields.

## Verification

Start PostgreSQL and load `deploy/.env` into the shell with the JDBC mappings shown above, then run the backend build and tests from the repository root:

```bash
cd Backend
./mvnw clean verify
```

The endpoint test substitutes `BusinessAnalysisService` and verifies all three response fields and query forwarding. Four persistence tests verify both migrations, seeded repository reads, order totals, relationships, and generated IDs after seeding. Three customer-tool tests exercise database aggregates, average rounding, a customer without orders, and a missing customer. Tests do not call Gemini. Test inserts roll back, though PostgreSQL identity sequences still advance. Use a development database with the original seed data; tests use the configured `DB_*` connection. Model tool selection and AI response quality are not tested yet.

For the frontend, from the repository root:

```bash
cd Frontend
flutter analyze
flutter test
flutter build web --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

The Flutter tests cover structured parsing, chat state, normalized failures and retry, section visibility, suggested prompts, keyboard input, and reset behavior.

## Security

Never commit API keys. Keep real secrets in local environment files or backend environment configuration, never in Flutter build arguments or source code. The Docker Compose setup is intended for local/demo use.

## Roadmap

1. **Completed:** PostgreSQL persistence, structured chat integration, low-stock and product-stock queries, sales summaries, customer purchase statistics, current-date Tool Calling, and a resource-based business prompt.
2. Additional explicit read-only business tools beyond the current inventory, sales, and customer aggregates.
3. Conversation Memory.
4. Embeddings and pgvector.
5. RAG.
6. Further reliability, security, and observability work.
7. AI response evaluation.
8. MCP and Agents concepts.

## Owner

Qusai Altoubasy
