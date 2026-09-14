# AI Business Assistant — Backend

The Quarkus backend for an educational AI Business Assistant. It currently exposes two Gemini-backed AI endpoints and a PostgreSQL persistence foundation for business data.

The learning progression starts with prompting, structured output, PostgreSQL, and a read-only inventory tool, which are implemented. Additional Tool Calling, Memory, Embeddings, pgvector, RAG, and production reliability/evaluation are future stages. See the [project overview](../README.md) and [deployment guide](../deploy/README.md) for the wider application.

## Tech Stack

- Java 21 and Quarkus 3.39.3
- Quarkus REST with Jackson
- LangChain4j AI Services with Google Gemini Developer API
- PostgreSQL 18, blocking JDBC, and Hibernate ORM with Panache
- Flyway for schema migrations and seed data
- Maven 3.9.11 via the wrapper and a multi-stage Docker build

Quarkus and LangChain4j dependencies use the BOMs in [pom.xml](pom.xml). The PostgreSQL JDBC driver is managed by the Quarkus BOM.

## Current Implemented Features

- `POST /api/chat` returns free-form AI text in a JSON response.
- `POST /api/chat/business-analysis` returns a structured business analysis.
- `ChatService` and `BusinessAnalysisService` use `@RegisterAiService`, `@SystemMessage`, and `@UserMessage`.
- `BusinessAnalysisService` can invoke `getLowStockProducts()` through a LangChain4j Tool backed by `ProductRepository`.
- Four business entities and four `PanacheRepository<Entity>` implementations provide database access.
- Flyway creates the schema and loads deterministic business data; Hibernate validates the mappings at startup.
- Focused request and tool logs record AI boundaries, tool invocation and result counts, timings, and meaningful failures without logging prompt contents.

The AI prompts are zero-shot and ask Gemini to avoid inventing data. Business analysis asks it to separate facts from assumptions. These are prompt instructions, not guarantees of factual accuracy. The configured chat-model temperature is `0.1`.

## Architecture

```text
Backend/
├── src/main/java/com/aibusinessassistant/
│   ├── chat/
│   │   ├── ChatResource.java
│   │   ├── ai/
│   │   │   ├── ChatService.java
│   │   │   └── BusinessAnalysisService.java
│   │   └── dto/
│   │       ├── ChatRequestDTO.java
│   │       ├── ChatResponseDTO.java
│   │       └── BusinessAnalysisDTO.java
│   ├── product/
│   │   ├── Product.java
│   │   ├── ProductRepository.java
│   │   ├── dto/LowStockProductDTO.java
│   │   └── tools/InventoryTool.java
│   ├── customer/
│   │   ├── Customer.java
│   │   └── CustomerRepository.java
│   └── order/
│       ├── Order.java
│       ├── OrderItem.java
│       ├── OrderRepository.java
│       └── OrderItemRepository.java
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/
│       ├── V1__create_business_schema.sql
│       └── V2__seed_business_data.sql
├── src/test/java/com/aibusinessassistant/
│   ├── chat/ChatResourceTest.java
│   └── order/BusinessPersistenceTest.java
├── .mvn/wrapper/maven-wrapper.properties
├── Dockerfile
├── mvnw
├── mvnw.cmd
└── pom.xml
```

Packages group code by business feature. The chat resource, AI interfaces, and DTOs live together under `chat`; each persistence feature owns its entities and repositories. There are no global controller, service, repository, or entity packages.

### Request Flow

```text
Client JSON query
  → ChatResource
  → ChatService / BusinessAnalysisService
  → Google Gemini
      ↕ when current inventory data is needed
    InventoryTool → ProductRepository → PostgreSQL
  → String wrapped in ChatResponseDTO / BusinessAnalysisDTO
  → JSON response
```

`ChatRequestDTO` contains only `query`. No conversation history is passed to either AI service. `BusinessAnalysisService` registers `InventoryTool`; Gemini decides whether to invoke its read-only low-stock query based on the request. The general chat service has no database tools.

## Database

Compose configures the official `postgres:18` image. Database access uses blocking JDBC and Hibernate ORM, with application-scoped `ProductRepository`, `CustomerRepository`, `OrderRepository`, and `OrderItemRepository`. `ProductRepository.findLowStockProducts()` is the current custom read query; it selects products whose `stockQuantity` is at or below `minimumStock`.

### Data Model

| Entity / table | Fields and relationships |
| --- | --- |
| `Product` / `products` | `id`, `name`, `category`, `price`, `stockQuantity`, `minimumStock` |
| `Customer` / `customers` | `id`, `name`, unique `email` |
| `Order` / `orders` | `id`, `customer` → `Customer`, `orderDate`, `totalAmount` |
| `OrderItem` / `order_items` | `id`, `order` → `Order`, `product` → `Product`, `quantity`, `price` |

IDs are generated `Long` values using PostgreSQL identity columns. Monetary values use `BigDecimal` mapped to `NUMERIC(12,2)`, and order dates use `LocalDate`. Database columns use snake_case. Relationships are required, lazy, unidirectional many-to-one mappings. There are no reverse collections or cascade operations configured.

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

The fictional seed data spans January–March 2026, includes repeat customers and products below minimum stock, and supports future monthly-sales and inventory exercises. Flyway tracks applied migrations in `flyway_schema_history`. Add new migrations for later changes instead of editing migrations already applied to a database.

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
| `DB_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5432/ai_business_assistant` |
| `DB_USERNAME` | JDBC username | `ai_business_assistant` |
| `DB_PASSWORD` | JDBC password | Required; no application default |
| `FRONTEND_ORIGIN` | Allowed browser CORS origin | `http://127.0.0.1:3000` |

CORS allows `POST`. The test profile provides non-secret Gemini placeholders; database credentials still come from the environment.

Compose reads `deploy/.env` and maps `POSTGRES_USER` to `DB_USERNAME`, `POSTGRES_PASSWORD` to `DB_PASSWORD`, and `POSTGRES_DB` into `jdbc:postgresql://postgres:5432/<database>`. `POSTGRES_PORT` controls the host database port; it does not change the internal port. `BACKEND_PORT` controls the host API port. Exported shell variables take precedence over Compose's env-file values.

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

The current suite contains five tests: one chat endpoint test and four persistence tests. The chat test substitutes a local AI service and does not call Gemini. Persistence tests verify migration history, seeded repository reads, historical order totals, relationships, and generated IDs. Use a development database with the original seed data. Test inserts roll back, but identity sequences advance. There is no dedicated business-analysis endpoint test or model-quality evaluation suite yet.

## API

Both endpoints consume and produce `application/json` and accept `{"query":"..."}`. Examples below use the default local port. Responses are illustrative; calling either endpoint sends the query to Gemini and may incur API usage costs.

### `POST /api/chat`

```bash
curl http://localhost:8080/api/chat \
  --header 'Content-Type: application/json' \
  --data '{"query":"What information is useful for reviewing sales performance?"}'
```

```json
{"response":"Start with sales by period, product, and customer, along with costs and margins."}
```

The AI service returns a `String`, which the resource wraps in `ChatResponseDTO`.

### `POST /api/chat/business-analysis`

```bash
curl http://localhost:8080/api/chat/business-analysis \
  --header 'Content-Type: application/json' \
  --data '{"query":"Do we have any products that need restocking?"}'
```

```json
{
  "summary": "Three products currently need restocking.",
  "insights": ["USB-C Cable has the largest gap between current and minimum stock."],
  "recommendations": ["Prioritize replenishing the products returned by the inventory tool."]
}
```

`BusinessAnalysisDTO` is a Java record containing `summary`, `insights`, and `recommendations`; the latter two fields are lists of strings. For current inventory questions, the endpoint can use stored product data through `getLowStockProducts()`. Tool calls and returned counts are logged at `INFO`; AI request completion timing is logged without recording the query text.

## Current Limitations

- The only LangChain4j tool is the read-only low-stock inventory query; there are no write tools or tools for sales, individual product stock, or customer statistics.
- No server-side conversation memory or persisted chat history.
- No embeddings, pgvector extension, semantic search, or RAG.
- No business CRUD endpoints or order/inventory business logic.
- No application authentication, explicit request validation, or custom API error contract.
- No metrics, distributed tracing, production alerting, or AI response evaluation suite beyond the focused application logs and existing framework setup.

## Roadmap

This educational progression distinguishes completed work from planned capabilities; planned items are not delivery commitments:

1. **Completed:** PostgreSQL persistence foundation, repositories, migrations, seed data, and `getLowStockProducts()` Tool Calling.
2. **Next:** Additional read-only LangChain4j tools backed by repositories, such as `getSales(from, to)`, `getProductStock(productId)`, and `getCustomerStatistics(customerId)`.
3. Conversation Memory.
4. Embeddings.
5. pgvector semantic search.
6. RAG.
7. Further reliability, security, and observability work.
8. AI response evaluation.
9. MCP and Agents concepts later.

## Scope / Non-Goals

This is an incremental learning project, not a full ERP. The current scope excludes a large CRUD surface, microservices, Kafka, complex authentication, and premature Agents/MCP work. Generated SQL is not the default planned LLM strategy; the next phase focuses on explicit read-only tools. Frontend work belongs to the separate Flutter project.
