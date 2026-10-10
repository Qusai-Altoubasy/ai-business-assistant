# AI Business Assistant — Backend

The Quarkus backend for AI Business Assistant. It exposes structured and streaming chat endpoints backed by Gemini or the company's Ollama server and reads business data from PostgreSQL.

Resource-based prompting, structured output, PostgreSQL-backed conversation history and turn-aware memory, and read-only inventory, sales, customer statistics, and current-date tools are implemented. Embeddings, pgvector, RAG, and further reliability/evaluation remain future stages. See the [project overview](../README.md) and [deployment guide](../deploy/README.md) for the wider application.

## Tech Stack

- Java 21 and Quarkus 3.39.3
- Quarkus REST with Jackson
- LangChain4j AI Services with Google Gemini Developer API and Ollama
- PostgreSQL 18, blocking JDBC, and Hibernate ORM with Panache
- Flyway for schema migrations and seed data
- Maven 3.9.11 via the wrapper and a multi-stage Docker build

Quarkus and LangChain4j dependencies use the BOMs in [pom.xml](pom.xml). The PostgreSQL JDBC driver is managed by the Quarkus BOM.

## Current Implemented Features

- `POST /api/chat` returns `BusinessAnalysisDTO` with `summary`, `insights`, and `recommendations`.
- `POST /api/chat/stream` streams JSON SSE events using the same tools and persistent conversation memory.
- `BusinessAnalysisService` is the shared contract. Provider-specific adapters delegate to `GeminiAiService` and `OllamaAiService`, which use named `@RegisterAiService` models, the shared prompt/tools, and `@MemoryId`.
- An optional request `provider` chooses Gemini or Ollama for a new conversation. The selection is saved and fixed for all follow-ups; the configured default is used only when creating a conversation without a selection.
- PostgreSQL stores successful user/assistant exchanges and the active context window. The window retains the latest 10 whole user turns and the system message.
- Inventory tools read low-stock products and stock for an individual product ID through `ProductRepository`.
- Sales tools aggregate recorded order amounts and order count for an inclusive date range through `OrderRepository`.
- Customer tools read customer identity through `CustomerRepository` and aggregate all of that customer's orders through `OrderRepository`, including average order value.
- The current-date tool supports relative-date questions using the backend's `LocalDate.now()`.
- Four business entities and their Panache repositories provide business-data access; chat entities and repositories store conversations, messages, and memory state.
- Flyway creates the schema and loads deterministic business data; Hibernate validates the mappings at startup.
- Focused request and tool logs record AI boundaries, tool invocation and result counts, timings, and meaningful failures without logging prompt contents.

The AI prompt is zero-shot and is loaded from [prompts/business-analysis-system.txt](src/main/resources/prompts/business-analysis-system.txt) using `@SystemMessage(fromResource = ...)`. The configured chat-model temperature is `0.1`.

### Business Prompt Scope

- Supports company-specific inventory, product, sales, order, and customer questions using read-only tools when data is needed.
- Answers general concepts within those business domains directly; company-specific data must come from the user or tools.
- Requests the current-date tool for relative dates rather than guessing the date.
- For unrelated questions, requests a brief out-of-scope `summary`, empty `insights`, and one business-focused suggestion in `recommendations`.
- Requires evidence or a supplied benchmark before classifying results, asserting trends/causes, or assigning customer segments. Optional explanations and suggestions must be presented as possibilities.
- Requests factual reporting and explicit treatment of missing data/entities. Structured requests use the analysis DTO; streaming requests use plain text.

These are model instructions, not hard API validation or guarantees of factual accuracy. The resource is bundled in the application; prompt changes require rebuilding/restarting the packaged backend.

## Architecture

```text
Backend/
├── src/main/java/com/aibusinessassistant/
│   ├── chat/
│   │   ├── ChatResource.java
│   │   ├── ChatService.java
│   │   ├── ai/
│   │   │   ├── BusinessAnalysisService.java
│   │   │   ├── AiProvider.java / AiProviderSelector.java
│   │   │   ├── GeminiAiService.java / OllamaAiService.java
│   │   │   └── GeminiBusinessAnalysisService.java / OllamaBusinessAnalysisService.java
│   │   ├── dto/
│   │   │   ├── ChatRequestDTO.java
│   │   │   ├── BusinessAnalysisDTO.java
│   │   │   ├── ChatStreamEventDTO.java
│   │   │   └── StreamEventType.java
│   │   ├── history/          # Conversations and complete user/assistant history
│   │   └── memory/           # Turn-aware window and PostgreSQL backing store
│   ├── product/
│   │   ├── Product.java
│   │   ├── ProductRepository.java
│   │   ├── dto/LowStockProductDTO.java
│   │   ├── dto/ProductStockDTO.java
│   │   └── tools/InventoryTools.java
│   ├── customer/
│   │   ├── Customer.java
│   │   ├── CustomerRepository.java
│   │   ├── dto/CustomerStatisticsDTO.java
│   │   └── tools/CustomerTools.java
│   ├── order/
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   ├── OrderRepository.java
│   │   ├── OrderItemRepository.java
│   │   ├── dto/SalesSummaryDTO.java
│   │   ├── dto/CustomerOrderStatisticsDTO.java
│   │   └── tools/SalesTools.java
│   └── common/tools/CommonTools.java
├── src/main/resources/
│   ├── application.properties
│   ├── prompts/business-analysis-system.txt
│   └── db/migration/
│       ├── V1__create_business_schema.sql
│       ├── V2__seed_business_data.sql
│       ├── V3__add_persistent_chat_history.sql
│       └── V4__add_conversation_ai_provider.sql
├── src/test/java/com/aibusinessassistant/
│   ├── chat/ChatResourceTest.java
│   ├── chat/memory/          # Turn-aware and persistence tests
│   ├── customer/CustomerToolsTest.java
│   └── order/BusinessPersistenceTest.java
├── .mvn/wrapper/maven-wrapper.properties
├── Dockerfile
├── mvnw
├── mvnw.cmd
└── pom.xml
```

Packages group code by business feature. The chat resource, orchestration service, AI interface, and DTOs live together under `chat`; each persistence feature owns its entities, repositories, and business tools. `ChatResource` validates HTTP requests and delegates to `ChatService`, which handles AI invocation, streaming events, logging, and history persistence. The current-date tool is shared under `common.tools`.

### Request Flow

```text
Client JSON conversationId + query + optional provider
  → ChatResource
  → ChatService
  → ConversationHistoryService.findOrCreateConversation → saved provider
  → AiProviderSelector → provider adapter (BusinessAnalysisService contract)
  → GeminiAiService / OllamaAiService (@MemoryId)
      ↔ TurnAwareChatMemory → PostgresChatMemoryStore → PostgreSQL chat_memory_state
  → Gemini / Ollama
      ↕ when business data is needed
    InventoryTools / SalesTools / CustomerTools → repositories → PostgreSQL
    CommonTools → current application date
  → BusinessAnalysisDTO or streamed ChatEvent values
  → ChatService → ConversationHistoryService → PostgreSQL chat_messages
  → ChatResource
  → JSON response or JSON SSE events
```

`ChatRequestDTO` contains a UUID `conversationId`, `query`, and optional `provider`. Both provider-specific AI services use `@MemoryId` to select a separate turn-aware chat memory for each conversation. It retains the most recent 10 whole user turns and the system message. The active window is stored in PostgreSQL so it is restored after a backend restart. Successful user and assistant exchanges are stored separately in `chat_messages` and are not deleted when turns leave the active window. The AI services are `@ApplicationScoped`, and the PostgreSQL store supplies their memory across requests. Both register `InventoryTools`, `SalesTools`, `CustomerTools`, and `CommonTools`; the saved provider's model decides which to invoke. The structured endpoint returns the final analysis without tool details; the streaming endpoint emits text chunks and tool names, but not tool arguments or results.

### Registered Tools

| Tool | Returned data and behavior |
| --- | --- |
| `getLowStockProducts()` | List of `LowStockProductDTO` containing ID, name, stock quantity, and minimum stock for products at or below their minimum. |
| `getProductStock(productId)` | `ProductStockDTO` containing the same fields for one ID; throws `IllegalArgumentException` if that product is missing. |
| `getSales(from, to)` | `SalesSummaryDTO` containing `from`, `to`, `totalSales`, and `orderCount`. Dates are inclusive; null dates or a reversed range are rejected. |
| `getCustomerStatistics(customerId)` | `CustomerStatisticsDTO` containing `customerId`, `customerName`, `orderCount`, `totalSpent`, and `averageOrderValue`. Aggregates all recorded orders for one customer; throws `IllegalArgumentException` if the customer is missing. |
| `getCurrentDate()` | Current `LocalDate` using the backend runtime's default time zone; the prompt requests it for relative dates. |

These are read-only AI tools, not separate REST or CRUD endpoints. The sales tool sums stored `Order.totalAmount` and counts orders for a period. Customer statistics use their own tool; neither tool calculates units sold or margins.

Customer statistics have no date filter. `totalSpent` sums stored order amounts; `averageOrderValue` divides it by the order count with two decimal places and `RoundingMode.HALF_UP`. An existing customer with no orders returns zero count, zero total spent, and zero average. The tool does not return email, individual orders, loyalty status, customer segments, or churn predictions.

## Database

Compose configures the official `postgres:18` image. Database access uses blocking JDBC and Hibernate ORM, with application-scoped `ProductRepository`, `CustomerRepository`, `OrderRepository`, and `OrderItemRepository`. `ProductRepository.findLowStockProducts()` selects products at or below `minimumStock`. `OrderRepository.getSalesSummary(from, to)` sums recorded order totals and counts orders between the supplied dates, returning zero totals/counts for an empty period.

`OrderRepository.getCustomerOrderStatistics(customerId)` returns a `CustomerOrderStatisticsDTO` projection of `COUNT(o)` and `COALESCE(SUM(o.totalAmount), 0)`, filtered by customer ID. `CustomerTools` first checks that the customer exists, then combines the aggregate with customer identity and computes the average.

### Data Model

| Entity / table | Fields and relationships |
| --- | --- |
| `Product` / `products` | `id`, `name`, `category`, `price`, `stockQuantity`, `minimumStock` |
| `Customer` / `customers` | `id`, `name`, unique `email` |
| `Order` / `orders` | `id`, `customer` → `Customer`, `orderDate`, `totalAmount` |
| `OrderItem` / `order_items` | `id`, `order` → `Order`, `product` → `Product`, `quantity`, `price` |
| `Conversation` / `conversations` | UUID `id`, creation and update timestamps |
| `ConversationMessage` / `chat_messages` | Generated `id`, `conversation` → `Conversation`, `role` (`USER` or `ASSISTANT`), `content`, creation timestamp |
| `ChatMemoryState` / `chat_memory_state` | `conversationId` UUID primary key referencing `conversations`, serialized active messages, update timestamp |

Business entity IDs and `chat_messages.id` are generated `Long` values using PostgreSQL identity columns; conversation IDs are client-generated UUIDs. Monetary values use `BigDecimal` mapped to `NUMERIC(12,2)`, and order dates use `LocalDate`. Database columns use snake_case. Business relationships are required, lazy, unidirectional many-to-one mappings. `chat_messages` also has a required many-to-one conversation mapping; `chat_memory_state` references its conversation through the UUID column. There are no reverse collections or cascade operations configured.

An order item's `price` is the unit price at purchase, which may differ from the product's current price. Seeded order totals match the sum of their line items; no application logic currently calculates or maintains those totals.

### Flyway and Startup

Flyway is the schema authority. The configuration enables `quarkus.flyway.migrate-at-start=true`, sets `quarkus.hibernate-orm.schema-management.strategy=validate`, and disables Hibernate SQL loading with `quarkus.hibernate-orm.sql-load-script=no-file`.

```text
PostgreSQL initializes the configured database
  → PostgreSQL healthcheck passes; Compose starts the backend
  → Flyway applies pending migrations
  → Hibernate validates the entity mappings
  → Quarkus finishes startup
```

PostgreSQL creates the database using `POSTGRES_DB` on first initialization. Flyway creates and evolves the schema inside it; Hibernate does not create or update tables.

| Migration | Purpose |
| --- | --- |
| [V1__create_business_schema.sql](src/main/resources/db/migration/V1__create_business_schema.sql) | Creates the four tables, identity keys, foreign keys, required columns, unique customer emails, and indexes for relationship/date lookups. |
| [V2__seed_business_data.sql](src/main/resources/db/migration/V2__seed_business_data.sql) | Inserts 10 products, 5 customers, 12 orders, and 24 order items; advances identity sequences past the explicit seed IDs. |
| [V3__add_persistent_chat_history.sql](src/main/resources/db/migration/V3__add_persistent_chat_history.sql) | Creates conversations, the append-only user/assistant history table, and the active chat-memory state table. The history role has a database check constraint. |
| [V4__add_conversation_ai_provider.sql](src/main/resources/db/migration/V4__add_conversation_ai_provider.sql) | Adds the required provider and allowed-value constraint, backfilling legacy conversations with `GEMINI`. |

The fictional seed data spans January–March 2026, includes repeat customers and products below minimum stock, and supports the current sales, inventory, and customer queries. Customer 1 (Maya Reed) has three seeded orders totaling `483.00`, averaging `161.00`. Relative-date questions use the actual backend date, so "last month" may fall outside the seeded period and return no sales. Flyway tracks applied migrations in `flyway_schema_history`. Add new migrations for later changes instead of editing migrations already applied to a database.

The named Docker volume `ai-business-assistant-postgres-data` mounts at `/var/lib/postgresql`; PostgreSQL 18 stores its data under `18/docker`. Container recreation and `docker compose down` preserve the volume. `docker compose down -v` deletes it. Changing `POSTGRES_*` values does not reconfigure an already initialized database, and changing an image tag does not upgrade an existing database's data format.

## Configuration

Use [deploy/.env.example](../deploy/.env.example) as the single committed template and `deploy/.env` as the ignored local environment file. Commands in this section start at the repository root.

On first setup only:

```bash
cp deploy/.env.example deploy/.env
```

If the file already exists, update it rather than overwriting it. Fill in `GEMINI_API_KEY`, select `GEMINI_MODEL`, and set a nonempty `POSTGRES_PASSWORD`. Keep the other template settings and adjust ports as needed. Never commit real secrets.

The backend reads the following variables through [application.properties](src/main/resources/application.properties):

| Variable | Purpose | Backend default |
| --- | --- | --- |
| `GEMINI_API_KEY` | Gemini Developer API key | Required |
| `GEMINI_MODEL` | Gemini chat model ID | Required |
| `AI_DEFAULT_PROVIDER` | Provider for new conversations without `provider` | `GEMINI` |
| `OLLAMA_BASE_URL` | Ollama server origin without `/api/chat` | `https://ai.llm.ensera.dev` |
| `OLLAMA_MODEL` | Company's Ollama model ID | `qwen3-vl:8b-instruct-q8_0` |
| `OLLAMA_TIMEOUT` | Ollama request timeout | `120s` |
| `DB_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5432/ai_business_assistant` |
| `DB_USERNAME` | JDBC username | `ai_business_assistant` |
| `DB_PASSWORD` | JDBC password | Required; no application default |
| `FRONTEND_ORIGIN` | Allowed browser CORS origin | `http://127.0.0.1:3000` |

CORS allows `POST`. The test profile provides non-secret placeholders for both named models; database credentials still come from the environment. `GeminiAiService` binds to `business-gemini`; `OllamaAiService` binds to `business-ollama`. Ollama Dev Services are disabled because the backend uses an existing server.

`app.chat.memory.max-turns=10` in `application.properties` controls the number of whole user turns kept in the active context. Older turns remain in `chat_messages` but are no longer sent to the model.

Compose reads `deploy/.env` and maps `POSTGRES_USER` to `DB_USERNAME`, `POSTGRES_PASSWORD` to `DB_PASSWORD`, and `POSTGRES_DB` into `jdbc:postgresql://ai-business-assistant-postgres:5432/<database>`. `POSTGRES_PORT` controls the host database port; it does not change the internal port. `BACKEND_PORT` controls the host API port. Exported shell variables take precedence over Compose's env-file values.

Compose has development fallbacks for omitted PostgreSQL settings, including a password fallback. A directly run backend has no `DB_PASSWORD` fallback, so explicitly configure the password in the shared env file for both workflows. `API_BASE_URL` and `FRONTEND_PORT` configure the frontend deployment; see the [deployment guide](../deploy/README.md).

## Running the Backend

### Docker Compose

Requires Docker with the Compose plugin and the configured `deploy/.env`. From the repository root:

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml config --quiet
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up --build backend
```

This starts the backend and its PostgreSQL dependency. The backend is available at `http://127.0.0.1:8080` with the template's port settings. The Dockerfile builds with Maven/Java 21, skips tests during image creation, and runs the packaged application as a non-root user on a Java 21 runtime. Use the [root setup guide](../README.md#docker-compose) to start the full application.

### Quarkus Dev Mode

Requires JDK 21 or later and a running PostgreSQL database. Start the database from the repository root:

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --wait postgres
```

Then load the same env file into a POSIX shell, still from the repository root. Keep its values shell-compatible, quoting spaces and shell special characters:

```bash
set -a
. ./deploy/.env
set +a
export DB_URL="jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/${POSTGRES_DB}"
export DB_USERNAME="$POSTGRES_USER"
export DB_PASSWORD="${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in deploy/.env}"
cd Backend
./mvnw quarkus:dev
```

Quarkus reads these exported variables. Local dev mode listens on port `8080` by default; `BACKEND_PORT` only controls Compose's port mapping. Avoid running both backend modes on the same host port. On Windows, use `mvnw.cmd` after setting equivalent environment variables in your shell.

### Build, Test, and Run the Packaged Application

With PostgreSQL running and the environment loaded as above, from `Backend/`:

```bash
./mvnw clean verify
java -jar target/quarkus-app/quarkus-run.jar
```

Stop dev mode before starting the packaged application. Keep the entire `target/quarkus-app/` directory when distributing the build.

The suite includes chat endpoint/provider selection, provider migration, business-persistence, conversation-memory persistence, customer-tool, and turn-aware memory tests. The turn-aware tests verify whole-turn eviction, system-message retention, and rejection of a persisted window that starts mid-turn. Tests do not call Gemini or Ollama or evaluate live model tool selection/response quality. Database tests require a development database with the original seed data. Persistence test transactions roll back; endpoint tests commit their chat records, and identity sequences still advance. Prefer a disposable database for the suite.

CI ([backend-ci.yml](../.github/workflows/backend-ci.yml)) runs the backend tests against a disposable PostgreSQL 18 service on every push to `main` and every pull request that changes `Backend/`.

## API

Both endpoints consume `application/json` and accept `{"conversationId":"...","query":"...","provider":"ollama"}`. `provider` is optional and accepts `gemini` or `ollama`, case-insensitively. `/api/chat` produces JSON; `/api/chat/stream` produces `text/event-stream`. The ID must be a UUID; reuse it for follow-up questions and use a new UUID for a new chat. Examples below use the default local port. Responses are illustrative; calling the endpoint sends the query and context to the saved provider and Gemini may incur API usage costs.

`ConversationHistoryService.findOrCreateConversation(id, requestedProvider)` sets the provider on creation, using `app.ai.default-provider` (`AI_DEFAULT_PROVIDER`, default `GEMINI`) when omitted. Follow-ups use the saved choice. Requesting a different provider returns HTTP 409 before invoking an AI service or opening SSE; unknown or blank provider values return HTTP 400. Use a new UUID to change providers. V4 backfills existing conversations with `GEMINI`. `AiProviderSelector` uses CDI-injected provider adapters. The provider is committed before LLM generation; a failed generation can leave an empty conversation with its provider pinned. History and memory continue to use the existing one-argument `findOrCreateConversation` overload. Concurrent creation and concurrent turns for the same UUID are not handled; send requests sequentially.

Endpoint tests replace only `GeminiAiService` and `OllamaAiService`, exercising the production selector and adapters. They verify both providers, default selection, cross-mode follow-ups, 400/409 rejection without model calls, and the existing history/SSE behavior. The migration test runs V3/V4 in a separate transaction-scoped schema and verifies that legacy conversations become Gemini conversations.

### `POST /api/chat`

```bash
curl http://localhost:8080/api/chat \
  --header 'Content-Type: application/json' \
  --data '{"conversationId":"1f5299c7-84a5-4a89-a5e4-1058debf4a31","query":"Do we have any products that need restocking?"}'
```

```json
{
  "summary": "Three products currently need restocking.",
  "insights": ["USB-C Cable has the largest gap between current and minimum stock."],
  "recommendations": ["Prioritize replenishing USB-C Cable and the other low-stock products."]
}
```

`BusinessAnalysisDTO` is a Java record containing `summary`, `insights`, and `recommendations`; the latter two fields are lists of strings. Flutter uses `/api/chat` in Structured Chat and `/api/chat/stream` in Streaming Chat. `ChatResource` delegates both endpoints to the concrete `ChatService`. Application logs record request lengths/completion timing and tool activity without recording the query text.

For customer analysis, use the same endpoint and contract:

```bash
curl http://localhost:8080/api/chat \
  --header 'Content-Type: application/json' \
  --data '{"conversationId":"1f5299c7-84a5-4a89-a5e4-1058debf4a31","query":"What are the purchase statistics for customer ID 1?"}'
```

Customer statistics DTOs are internal tool results; they are not new HTTP response fields. Structured out-of-scope responses use `BusinessAnalysisDTO`, with empty insights and one suggestion to ask about the supported business domains.

### `POST /api/chat/stream`

```bash
curl -N http://localhost:8080/api/chat/stream \
  --header 'Content-Type: application/json' \
  --header 'Accept: text/event-stream' \
  --data '{"conversationId":"f9019d76-2372-49e2-bef9-c05d0ad1c925","query":"Give me a short business analysis about reducing excess inventory."}'
```

Tool Calling example:

```bash
curl -N http://localhost:8080/api/chat/stream \
  --header 'Content-Type: application/json' \
  --header 'Accept: text/event-stream' \
  --data '{"conversationId":"03da41ca-af68-49ce-ae03-89ea95d5de2c","query":"Which products are low stock?"}'
```

Memory example (run sequentially using the same UUID):

```bash
curl -N http://localhost:8080/api/chat/stream \
  --header 'Content-Type: application/json' \
  --header 'Accept: text/event-stream' \
  --data '{"conversationId":"0dc136ef-58ba-48de-a41a-c6f8a490fb1c","query":"Show me customer 2 statistics."}'

curl -N http://localhost:8080/api/chat/stream \
  --header 'Content-Type: application/json' \
  --header 'Accept: text/event-stream' \
  --data '{"conversationId":"0dc136ef-58ba-48de-a41a-c6f8a490fb1c","query":"How much did they spend?"}'
```

`BusinessAnalysisService.chatStream` returns `Multi<ChatEvent>` using the event API supported by the installed Quarkus LangChain4j extension. The REST endpoint returns `Multi<ChatStreamEventDTO>` with JSON SSE `data:` elements. The selected provider's actual partial responses become `CHUNK` events without splitting or waiting for the complete answer. `BeforeToolExecutionEvent` becomes `TOOL_STARTED`, and `ToolExecutedEvent` becomes `TOOL_COMPLETED`; both expose only the actual tool name. Thinking, arguments, raw results, and intermediate responses are filtered out.

```text
data: {"type":"TOOL_STARTED","content":"getLowStockProducts"}

data: {"type":"TOOL_COMPLETED","content":"getLowStockProducts"}

data: {"type":"CHUNK","content":"Three products are low stock."}

data: {"type":"DONE","content":null}
```

This method shares the resource-based system prompt, registered tools, `@MemoryId`, turn-aware memory provider, and PostgreSQL memory store with `chat`. The shared prompt selects JSON when the existing method requires its DTO schema, and plain text otherwise. Each provider's chat and streaming models use its named model configuration. `/api/chat` still returns `BusinessAnalysisDTO`; the SSE protocol does not stream that DTO.

Quarkus REST keeps the connection open until generation finishes. `ChatService` accumulates only `CHUNK` content to save one successful USER/ASSISTANT exchange in `chat_messages`; status events never enter assistant text, and accumulation does not delay delivery. After successful completion, history is persisted before exactly one final `DONE` event. Completion/history persistence runs on Quarkus's existing worker pool because the application uses blocking JDBC, without a transaction spanning LLM generation.

Stream or history-persistence failures are logged in detail and produce one final `{"type":"ERROR","content":"Unable to complete the streaming request."}` event with no `DONE` afterward. Partial failed responses are not saved as successful exchanges. Invalid/missing conversation UUIDs still return HTTP 400 before streaming starts.

The installed Quarkus `3.39.3`, Quarkus LangChain4j `1.13.1`, and LangChain4j `1.19.0` support actual tool lifecycle events. The Ollama extension uses the same BOM-managed versions. Chunk sizes and timing are provider-controlled, and chunks are not guaranteed to correspond to individual tokens. This version commits streaming input to active memory before generation, so a failed/cancelled turn can leave input in that window; only successful exchanges enter full history. Finish one request before sending another with the same conversation ID, including requests that mix the two endpoints.

## Current Limitations

- Tools are read-only. Policy/document retrieval, write operations, units-sold analysis, and margins are not implemented.
- Customer statistics are lifetime aggregates of recorded orders for one ID, without date filtering, customer search/ranking, segmentation, or predictive analysis.
- The API has no endpoint to list stored conversations or retrieve their full history for a client. Sending the same conversation ID restores the active AI context after a backend restart.
- No embeddings, pgvector extension, semantic search, or RAG.
- No business CRUD endpoints or order/inventory business logic.
- No application authentication, comprehensive query validation, or custom API error contract. The endpoint rejects a missing or malformed conversation ID.
- No metrics, distributed tracing, production alerting, or AI response evaluation suite beyond the focused application logs and existing framework setup.

## Roadmap

Completed capabilities and possible future work:

1. **Completed:** PostgreSQL persistence, repositories, migrations, seed data, structured chat, conversation history and turn-aware memory, low-stock and product-stock queries, sales summaries, customer purchase statistics, current-date Tool Calling, and a resource-based business prompt.
2. **Next:** Additional explicit read-only business queries beyond the existing tools.
3. Conversation history retrieval for clients.
4. Embeddings.
5. pgvector semantic search.
6. RAG.
7. Further reliability, security, and observability work.
8. AI response evaluation.
9. MCP and Agents concepts later.

## Scope / Non-Goals

The current API focuses on chat and read-only business queries. It does not provide a full ERP or a large CRUD surface, microservices, Kafka, or authentication. Generated SQL is not used for model-driven data access; company data comes from explicit read-only tools. The Flutter client lives in the separate `Frontend` directory.
