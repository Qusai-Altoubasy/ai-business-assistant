# AI Business Assistant — Backend

The Quarkus backend for AI Business Assistant. It exposes a structured chat endpoint and a streaming chat endpoint that answer business questions with Gemini or the company's Ollama server. The model reads inventory, sales and customer data from PostgreSQL through read-only tools. Conversation history and the active chat memory are stored in PostgreSQL.

## Tech Stack

Versions defined in [pom.xml](pom.xml) and the Maven wrapper:

- Java 21 (`maven.compiler.release`)
- Quarkus 3.39.3 (`quarkus-bom` and `quarkus-langchain4j-bom`)
- Quarkus REST with Jackson
- Quarkus LangChain4j with the Gemini (`quarkus-langchain4j-ai-gemini`) and Ollama (`quarkus-langchain4j-ollama`) extensions; the BOM resolves Quarkus LangChain4j 1.13.1 and LangChain4j 1.19.0
- PostgreSQL JDBC driver, Hibernate ORM with Panache, Hibernate Vector (`hibernate-vector`) for the pgvector column, and Flyway (versions managed by the Quarkus BOM)
- Lombok 1.18.44
- Maven 3.9.11 via the wrapper ([maven-wrapper.properties](.mvn/wrapper/maven-wrapper.properties))
- PostgreSQL 18 with the pgvector extension (`pgvector/pgvector:0.8.7-pg18-trixie` in [docker-compose.yml](../deploy/docker-compose.yml))

## Project Structure

```text
Backend/
├── src/main/java/com/aibusinessassistant/
│   ├── chat/              # ChatResource (REST endpoints) and ChatService (AI calls, streaming, history)
│   │   ├── ai/            # BusinessAnalysisService contract, AiProvider, AiProviderSelector,
│   │   │                  # GeminiAiService / OllamaAiService and their adapters
│   │   ├── dto/           # ChatRequestDTO, BusinessAnalysisDTO, ChatStreamEventDTO, StreamEventType
│   │   ├── history/       # Conversation and ConversationMessage entities, repositories, ConversationHistoryService
│   │   └── memory/        # TurnAwareChatMemory, its provider, PostgresChatMemoryStore, ChatMemoryState
│   ├── product/           # Product entity, ProductRepository, DTOs, tools/InventoryTools
│   ├── customer/          # Customer entity, CustomerRepository, DTO, tools/CustomerTools
│   ├── order/             # Order and OrderItem entities, repositories, DTOs, tools/SalesTools
│   └── common/tools/      # CommonTools (current date)
├── src/main/resources/
│   ├── application.properties
│   ├── prompts/business-analysis-system.txt
│   └── db/migration/      # Flyway migrations V1–V5
├── src/test/java/com/aibusinessassistant/
├── Dockerfile
├── mvnw / mvnw.cmd
└── pom.xml
```

Packages group code by business feature. The chat resource, orchestration service, AI interfaces and DTOs live under `chat`; each business feature owns its entities, repositories and tools. `ChatResource` validates HTTP requests and delegates to `ChatService`, which handles AI invocation, streaming events, logging and history persistence. The current-date tool is shared under `common.tools`.

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

`GeminiAiService` and `OllamaAiService` are `@ApplicationScoped` and use `@MemoryId` to select a separate chat memory for each conversation. Both use the system prompt from [prompts/business-analysis-system.txt](src/main/resources/prompts/business-analysis-system.txt), a temperature of `0.1`, and the same tools:

| Tool | Returns |
| --- | --- |
| `getLowStockProducts()` | Products at or below their minimum stock: ID, name, stock quantity, minimum stock. |
| `getProductStock(productId)` | The same fields for one product; fails if the product does not exist. |
| `getSales(from, to)` | `from`, `to`, `totalSales` and `orderCount` for an inclusive date range. |
| `getCustomerStatistics(customerId)` | `customerId`, `customerName`, `orderCount`, `totalSpent` and `averageOrderValue` over all of the customer's orders; fails if the customer does not exist. |
| `getCurrentDate()` | The backend's current `LocalDate`, used for relative dates. |

The tools are read-only and are not exposed as REST endpoints. The saved provider's model decides which tools to call. `averageOrderValue` is `totalSpent` divided by the order count, rounded to two decimals with `RoundingMode.HALF_UP`; a customer with no orders gets zero for all three values.

The chat memory keeps the system message and the latest 10 whole user turns (`app.chat.memory.max-turns=10`). The active window is stored in PostgreSQL, so it is restored after a backend restart. Successful user and assistant exchanges are stored separately in `chat_messages` and stay there when turns leave the active window.

### Business Prompt

The system prompt instructs the model to:

- Answer company-specific inventory, product, sales, order and customer questions, using the read-only tools when data is needed.
- Answer general concepts within those domains directly; company-specific data must come from the user or the tools.
- Call the current-date tool for relative dates instead of guessing the date.
- For unrelated questions, return a brief out-of-scope `summary`, empty `insights`, and one business-focused suggestion in `recommendations`.
- Classify results, claim trends or causes, or assign customer segments only with supporting data or a supplied benchmark, and present optional explanations and suggestions as possibilities.
- Report facts first and state clearly when data or an entity is missing. Structured requests use the analysis DTO; streaming requests use plain text.

These are model instructions, not API validation or a guarantee of factual accuracy. The prompt is packaged with the application, so a change needs a rebuild and restart (see [deploy/README.md](../deploy/README.md#rebuild-after-changes) for Compose).

### Logging

`ChatService` logs each AI request with the conversation ID, provider and duration, plus the query length and memory size for `/api/chat`. Each tool logs its call, its arguments or result counts, and failures. The query text is not logged.

## Configuration

The backend reads these environment variables in [application.properties](src/main/resources/application.properties):

| Variable | Purpose | Default |
| --- | --- | --- |
| `GEMINI_API_KEY` | Gemini Developer API key | None — required |
| `GEMINI_MODEL` | Gemini chat model ID | None — required |
| `AI_DEFAULT_PROVIDER` | Provider for new conversations that do not send `provider` (`GEMINI` or `OLLAMA`) | `GEMINI` |
| `OLLAMA_BASE_URL` | Ollama server origin, without `/api/chat` | `https://ai.llm.ensera.dev` |
| `OLLAMA_MODEL` | Ollama model ID | `qwen3-vl:8b-instruct-q8_0` |
| `OLLAMA_TIMEOUT` | Ollama request timeout | `120s` |
| `DB_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5432/ai_business_assistant` |
| `DB_USERNAME` | Database user | `ai_business_assistant` |
| `DB_PASSWORD` | Database password | None — required |
| `FRONTEND_ORIGIN` | Browser origin allowed by CORS (CORS allows `POST`) | `http://127.0.0.1:3000` |

`GeminiAiService` uses the named model `business-gemini` and `OllamaAiService` uses `business-ollama`. Ollama Dev Services are disabled because the backend uses an existing Ollama server. The test profile supplies placeholder Gemini and Ollama settings, so tests need no API key. The `DB_*` variables apply only to dev and prod; tests use their own database (see [Tests](#tests)).

The committed template is [deploy/.env.example](../deploy/.env.example); the local copy `deploy/.env` is ignored by Git. From the repository root, on first setup:

```bash
cp deploy/.env.example deploy/.env
```

Fill in `GEMINI_API_KEY` and a nonempty `POSTGRES_PASSWORD`, and check `GEMINI_MODEL`. Docker Compose maps `POSTGRES_USER`, `POSTGRES_PASSWORD` and `POSTGRES_DB` to `DB_USERNAME`, `DB_PASSWORD` and `DB_URL` for the backend container and has a password fallback; a backend run with Maven has no `DB_PASSWORD` default. See the [deployment guide](../deploy/README.md) for the Compose variables.

## Run Locally

Requires JDK 21 and Docker with the Compose plugin. From the repository root, start PostgreSQL:

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --wait postgres
```

Then, still from the repository root, load `deploy/.env`, map its PostgreSQL settings to the backend variables, and start dev mode. Keep the file's values shell-compatible, quoting spaces and special characters:

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

The backend listens on `http://localhost:8080`. `BACKEND_PORT` only changes the Compose port mapping, so do not run dev mode and the Compose backend on the same host port. On Windows, set the same variables in your shell and use `mvnw.cmd`.

### Docker Image

The [Dockerfile](Dockerfile) builds with Maven 3.9.11 on Java 21, skips tests during the image build, and runs the packaged application as a non-root user on a Java 21 runtime. To run the backend in Docker Compose, see the [deployment guide](../deploy/README.md#commands).

### Switching the AI provider

Each conversation is pinned to one provider when it is created:

- Send `"provider": "gemini"` or `"provider": "ollama"` (case-insensitive) in the first request of a conversation.
- If `provider` is omitted, `AI_DEFAULT_PROVIDER` is used.
- Follow-up requests use the saved provider. Requesting a different provider for an existing conversation returns HTTP 409 before any AI service is called or SSE is opened; an unknown or blank value returns HTTP 400. Use a new conversation ID to change providers.

`ConversationHistoryService.findOrCreateConversation(id, requestedProvider)` saves the provider in its own transaction before the model is called, so a failed first request can leave an empty conversation with its provider set. `AiProviderSelector` returns the Gemini or Ollama adapter for the saved provider.

## Database

Database access uses blocking JDBC and Hibernate ORM with application-scoped `ProductRepository`, `CustomerRepository`, `OrderRepository` and `OrderItemRepository`:

- `ProductRepository.findLowStockProducts()` selects products with `stockQuantity <= minimumStock`.
- `OrderRepository.getSalesSummary(from, to)` sums `totalAmount` and counts orders with `orderDate` between the two dates, returning zero for an empty period.
- `OrderRepository.getCustomerOrderStatistics(customerId)` returns `COUNT(o)` and `COALESCE(SUM(o.totalAmount), 0)` for one customer. `CustomerTools` first checks that the customer exists, then adds the customer's identity and computes the average.

### Data Model

| Entity / table | Fields and relationships |
| --- | --- |
| `Product` / `products` | `id`, `name`, `category`, `price`, `stockQuantity`, `minimumStock` |
| `Customer` / `customers` | `id`, `name`, unique `email` |
| `Order` / `orders` | `id`, `customer` → `Customer`, `orderDate`, `totalAmount` |
| `OrderItem` / `order_items` | `id`, `order` → `Order`, `product` → `Product`, `quantity`, `price` |
| `Conversation` / `conversations` | UUID `id`, `provider` (`ai_provider` column, `GEMINI` or `OLLAMA`, not updatable), creation and update timestamps |
| `ConversationMessage` / `chat_messages` | Generated `id`, `conversation` → `Conversation`, `role` (`USER` or `ASSISTANT`), `content`, creation timestamp |
| `ChatMemoryState` / `chat_memory_state` | `conversationId` UUID primary key referencing `conversations`, serialized active messages, update timestamp |
| `KnowledgeChunk` / `knowledge_chunks` | Generated `id`, `source`, `section`, `ruleId`, `chunkIndex`, `content`, `contentHash`, `embedding` (`float[]` mapped to `VECTOR(768)`), `embeddingModel`, JSON `metadata`, creation and update timestamps. Read-only: `KnowledgeChunkRepository` writes rows with native SQL |

Business entity IDs and `chat_messages.id` are `Long` values generated by PostgreSQL identity columns; conversation IDs are client-generated UUIDs. Monetary values are `BigDecimal` mapped to `NUMERIC(12,2)`, and order dates are `LocalDate`. Columns use snake_case. Business relationships are required, lazy, one-directional many-to-one mappings; `chat_messages` has a required many-to-one mapping to its conversation, and `chat_memory_state` references its conversation through the UUID column. No reverse collections or cascades are configured.

`KnowledgeChunk.metadata` is read with Hibernate's own JSON mapper (`quarkus.hibernate-orm.mapping.format.global=ignore`), not the REST `ObjectMapper`.

An order item's `price` is the unit price at purchase, which can differ from the product's current price. Seeded order totals match the sum of their line items.

### Flyway and Startup

Flyway owns the schema. `application.properties` sets `quarkus.flyway.migrate-at-start=true`, `quarkus.hibernate-orm.schema-management.strategy=validate`, and `quarkus.hibernate-orm.sql-load-script=no-file`.

```text
PostgreSQL initializes the configured database
  → in Compose, the PostgreSQL healthcheck passes and the backend starts
  → Flyway applies pending migrations
  → Hibernate validates the entity mappings
  → Quarkus finishes startup
```

At startup, `KnowledgeBaseIndexer` indexes the knowledge-base files into `knowledge_chunks` when `app.knowledge-base.index-at-startup` is `true` (the default; `false` in tests), embedding only new or changed chunks and logging an error without stopping the application if indexing fails.

`KnowledgeRetriever.retrieve(question)` embeds a question as a query and returns up to `app.knowledge-base.retrieval.top-k` (5) of the closest chunks by cosine distance whose cosine similarity is at least `app.knowledge-base.retrieval.min-similarity`, with their source, section and rule ID; an empty list means no valid evidence. Chunks embedded with another model are ignored.

PostgreSQL creates the database from `POSTGRES_DB` on first initialization. Flyway creates and changes the schema inside it; Hibernate does not create or update tables. Flyway records applied migrations in `flyway_schema_history`.

| Migration | Creates |
| --- | --- |
| [V1__create_business_schema.sql](src/main/resources/db/migration/V1__create_business_schema.sql) | Tables `products`, `customers`, `orders` and `order_items`, with identity keys, foreign keys, unique customer emails, and indexes on order customer, order date and order-item references. |
| [V2__seed_business_data.sql](src/main/resources/db/migration/V2__seed_business_data.sql) | Seed data: 10 products, 5 customers, 12 orders (January–March 2026) and 24 order items. |
| [V3__add_persistent_chat_history.sql](src/main/resources/db/migration/V3__add_persistent_chat_history.sql) | Tables `conversations`, `chat_messages` (user/assistant history) and `chat_memory_state` (active chat memory). |
| [V4__add_conversation_ai_provider.sql](src/main/resources/db/migration/V4__add_conversation_ai_provider.sql) | Required `conversations.ai_provider` column limited to `GEMINI` or `OLLAMA`; existing conversations are set to `GEMINI`. |
| [V5__add_knowledge_chunks.sql](src/main/resources/db/migration/V5__add_knowledge_chunks.sql) | The `vector` extension and the `knowledge_chunks` table with a `VECTOR(768)` column and an HNSW cosine index. |

Seeded order dates fall in January–March 2026. Relative-date questions such as "last month" use the backend's current date and may match no seeded orders. Add a new migration for each schema change instead of editing an applied one.

## API

| Method | Path | Produces | Resource method |
| --- | --- | --- | --- |
| `POST` | `/api/chat` | `application/json` | `ChatResource.chat` |
| `POST` | `/api/chat/stream` | `text/event-stream` | `ChatResource.chatStream` |

Both consume `application/json`:

```json
{"conversationId":"1f5299c7-84a5-4a89-a5e4-1058debf4a31","query":"Which products are low stock?","provider":"gemini"}
```

`conversationId` must be a UUID; a missing or invalid value returns HTTP 400. Reuse the ID for follow-up questions. `provider` is optional (see [Switching the AI provider](#switching-the-ai-provider)). Send requests for the same conversation one at a time, including requests that mix the two endpoints. Calling either endpoint sends the query to the selected provider. The responses below are illustrative.

### `POST /api/chat`

Returns `BusinessAnalysisDTO`, a record with a `summary` string and `insights` and `recommendations` string lists:

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

Customer questions use the same endpoint and response shape; tool results such as customer statistics are not returned as separate fields:

```bash
curl http://localhost:8080/api/chat \
  --header 'Content-Type: application/json' \
  --data '{"conversationId":"1f5299c7-84a5-4a89-a5e4-1058debf4a31","query":"What are the purchase statistics for customer ID 1?"}'
```

### `POST /api/chat/stream`

Returns JSON SSE `data:` events with a `type` of `CHUNK`, `TOOL_STARTED`, `TOOL_COMPLETED`, `DONE` or `ERROR`.

General question:

```bash
curl -N http://localhost:8080/api/chat/stream \
  --header 'Content-Type: application/json' \
  --header 'Accept: text/event-stream' \
  --data '{"conversationId":"f9019d76-2372-49e2-bef9-c05d0ad1c925","query":"Give me a short business analysis about reducing excess inventory."}'
```

Question that calls a tool:

```bash
curl -N http://localhost:8080/api/chat/stream \
  --header 'Content-Type: application/json' \
  --header 'Accept: text/event-stream' \
  --data '{"conversationId":"03da41ca-af68-49ce-ae03-89ea95d5de2c","query":"Which products are low stock?"}'
```

```text
data: {"type":"TOOL_STARTED","content":"getLowStockProducts"}

data: {"type":"TOOL_COMPLETED","content":"getLowStockProducts"}

data: {"type":"CHUNK","content":"Three products are low stock."}

data: {"type":"DONE","content":null}
```

Follow-up using memory (run in order with the same UUID):

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

`BusinessAnalysisService.chatStream` returns `Multi<ChatEvent>`, and `ChatResource.chatStream` returns `Multi<ChatStreamEventDTO>`. `ChatService.chatStream` maps the events:

- Each partial response from the provider becomes a `CHUNK` event as it arrives, without splitting or waiting for the full answer. Chunk sizes and timing are set by the provider and do not necessarily match tokens.
- `BeforeToolExecutionEvent` becomes `TOOL_STARTED` and `ToolExecutedEvent` becomes `TOOL_COMPLETED`, each with only the tool name. All other events, including tool arguments and results, are dropped.

Both methods share the system prompt, tools, `@MemoryId`, chat memory and PostgreSQL memory store, and use the provider's named model. The prompt asks for JSON when the structured method requires the DTO, and plain text otherwise; the SSE stream does not carry `BusinessAnalysisDTO`.

`ChatService` collects the `CHUNK` text only to save one successful USER/ASSISTANT exchange in `chat_messages`; delivery is not delayed. After a successful stream, the history write runs on the Quarkus worker pool (the application uses blocking JDBC) and then exactly one `DONE` event is sent. No transaction spans the model call.

If the stream or the history write fails, the error is logged and the stream ends with one `{"type":"ERROR","content":"Unable to complete the streaming request."}` event and no `DONE`. A failed response is not saved as a successful exchange.

## Tests

From `Backend/`, with Docker running:

```bash
./mvnw -B verify
```

The tests need Docker. Quarkus Dev Services start a disposable `pgvector/pgvector:0.8.7-pg18-trixie` container for each run, Flyway applies the migrations and seed data, and the container is removed afterwards, so every run starts from the seed data only. `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` are ignored by the tests, so the development database is never touched. Persistence tests roll back their transactions, but endpoint tests commit chat records. No API key or model server is needed.

To run the tests against an existing PostgreSQL database with the pgvector extension and the original seed data instead, set `_TEST_QUARKUS_DATASOURCE_JDBC_URL`, `_TEST_QUARKUS_DATASOURCE_USERNAME` and `_TEST_QUARKUS_DATASOURCE_PASSWORD`; Dev Services then do not start. Use a disposable database, because the endpoint tests leave their chat records in it.

| Test class | Covers |
| --- | --- |
| `chat/ChatResourceTest` | Both endpoints with test replacements for only `GeminiAiService` and `OllamaAiService`, so the real selector and adapters run: both providers, default selection, follow-ups across modes, 400/409 rejection without model calls, history and SSE events, and failure handling. |
| `chat/ConversationProviderMigrationTest` | Runs V3 and V4 in a separate schema that rolls back with the test, and checks that existing conversations become `GEMINI` conversations. |
| `chat/memory/TurnAwareChatMemoryTest` | Whole-turn eviction, keeping the system message, and rejecting a stored window that starts mid-turn. |
| `chat/memory/ConversationMemoryPersistenceTest` | Memory restored for the same conversation, isolation between conversations, full history kept after eviction, invalid IDs rejected. |
| `customer/CustomerToolsTest` | Seeded customer statistics, zero totals and rounding, missing customers. |
| `order/BusinessPersistenceTest` | Migrations V1–V5 applied, seed data readable through repositories, seeded order totals, generated IDs and relationships. |
| `knowledge/KnowledgeChunksMigrationTest` | The V5 schema: the `vector` extension, the `VECTOR(768)` column, the HNSW cosine index, the dimension check and the unique `(source, chunk_index)` constraint. |
| `knowledge/DocumentChunkerTest` | Chunks of the three knowledge-base files: the preamble, then one chunk per rule with its section and rule ID, the content format and the SHA-256 hash; a paragraph without a rule ID, a multi-line paragraph and text before the title. |
| `knowledge/KnowledgeBaseIndexerTest` | Indexing `inventory-policy.md` with a fake embedding model, reading the rows through the `KnowledgeChunk` entity, and re-indexing: unchanged files, a changed rule, a rule removed in the middle or at the end, a removed file, another embedding model, and a failed embedding call that stops later files. Startup indexing when enabled, disabled and failing, and the rejection of an empty source list. |
| `knowledge/KnowledgeRetrieverTest` | Retrieval with a fake embedding model and hand-made vectors: ordering by cosine similarity with source, section, rule ID and similarity, the top-k limit, the minimum similarity and the empty result, chunks of another embedding model, blank questions without an embedding call, and the question embedded as a query. |
| `knowledge/KnowledgeRetrieverLiveTest` | Skipped unless run manually with a key: indexes the knowledge base with the real Gemini model, then checks that answered questions find their rule (for example INV-3 for "When is a product considered critical?") and that unanswered questions find no evidence. |

The tests do not call Gemini or Ollama and do not evaluate model answers.

Semantic retrieval is tested only manually, because it needs the real embedding model: run `./mvnw test -Dtest=KnowledgeRetrieverLiveTest -Dgemini.live=true` with `GEMINI_API_KEY` set. CI only proves the retrieval query and threshold logic.

`./mvnw -B verify` also packages the application, which can be started from `Backend/` with `java -jar target/quarkus-app/quarkus-run.jar`. Stop dev mode first. Keep the whole `target/quarkus-app/` directory when copying the build.

CI ([backend-ci.yml](../.github/workflows/backend-ci.yml)) runs `./mvnw -B verify` with the same Dev Services database on pushes to `main` and pull requests that change `Backend/` or the workflow.
