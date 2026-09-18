# AI Business Assistant — Backend

The Quarkus backend for an educational AI Business Assistant. It exposes one Gemini-backed structured chat endpoint and reads business data from PostgreSQL.

Resource-based prompting, structured output, PostgreSQL, and read-only inventory, sales, customer statistics, and current-date tools are implemented. Memory, Embeddings, pgvector, RAG, and further reliability/evaluation remain future stages. See the [project overview](../README.md) and [deployment guide](../deploy/README.md) for the wider application.

## Tech Stack

- Java 21 and Quarkus 3.39.3
- Quarkus REST with Jackson
- LangChain4j AI Services with Google Gemini Developer API
- PostgreSQL 18, blocking JDBC, and Hibernate ORM with Panache
- Flyway for schema migrations and seed data
- Maven 3.9.11 via the wrapper and a multi-stage Docker build

Quarkus and LangChain4j dependencies use the BOMs in [pom.xml](pom.xml). The PostgreSQL JDBC driver is managed by the Quarkus BOM.

## Current Implemented Features

- `POST /api/chat` returns `BusinessAnalysisDTO` with `summary`, `insights`, and `recommendations`.
- `BusinessAnalysisService` uses `@RegisterAiService`, `@SystemMessage`, and `@UserMessage`.
- Inventory tools read low-stock products and stock for an individual product ID through `ProductRepository`.
- Sales tools aggregate recorded order amounts and order count for an inclusive date range through `OrderRepository`.
- Customer tools read customer identity through `CustomerRepository` and aggregate all of that customer's orders through `OrderRepository`, including average order value.
- The current-date tool supports relative-date questions using the backend's `LocalDate.now()`.
- Four business entities and four `PanacheRepository<Entity>` implementations provide database access.
- Flyway creates the schema and loads deterministic business data; Hibernate validates the mappings at startup.
- Focused request and tool logs record AI boundaries, tool invocation and result counts, timings, and meaningful failures without logging prompt contents.

The AI prompt is zero-shot and is loaded from [prompts/business-analysis-system.txt](src/main/resources/prompts/business-analysis-system.txt) using `@SystemMessage(fromResource = ...)`. The configured chat-model temperature is `0.1`.

### Business Prompt Scope

- Supports company-specific inventory, product, sales, order, and customer questions using read-only tools when data is needed.
- Answers general concepts within those business domains directly; company-specific data must come from the user or tools.
- Requests the current-date tool for relative dates rather than guessing the date.
- For unrelated questions, requests a brief out-of-scope `summary`, empty `insights`, and one business-focused suggestion in `recommendations`.
- Requires evidence or a supplied benchmark before classifying results, asserting trends/causes, or assigning customer segments. Optional explanations and suggestions must be presented as possibilities.
- Requests factual reporting, explicit treatment of missing data/entities, and the same structured DTO for every response.

These are model instructions, not hard API validation or guarantees of factual accuracy. The resource is bundled in the application; prompt changes require rebuilding/restarting the packaged backend.

## Architecture

```text
Backend/
├── src/main/java/com/aibusinessassistant/
│   ├── chat/
│   │   ├── ChatResource.java
│   │   ├── ai/
│   │   │   └── BusinessAnalysisService.java
│   │   └── dto/
│   │       ├── ChatRequestDTO.java
│   │       └── BusinessAnalysisDTO.java
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
│       └── V2__seed_business_data.sql
├── src/test/java/com/aibusinessassistant/
│   ├── chat/ChatResourceTest.java
│   ├── customer/CustomerToolsTest.java
│   └── order/BusinessPersistenceTest.java
├── .mvn/wrapper/maven-wrapper.properties
├── Dockerfile
├── mvnw
├── mvnw.cmd
└── pom.xml
```

Packages group code by business feature. The chat resource, AI interface, and DTOs live together under `chat`; each persistence feature owns its entities, repositories, and business tools. The current-date tool is shared under `common.tools`.

### Request Flow

```text
Client JSON query
  → ChatResource
  → BusinessAnalysisService
  → Google Gemini
      ↕ when business data is needed
    InventoryTools / SalesTools / CustomerTools → repositories → PostgreSQL
    CommonTools → current application date
  → BusinessAnalysisDTO
  → JSON response
```

`ChatRequestDTO` contains only `query`. No conversation history is passed to the AI service. `BusinessAnalysisService` registers `InventoryTools`, `SalesTools`, `CustomerTools`, and `CommonTools`; Gemini decides which to invoke. The client receives only the final structured analysis, without tool-call details.

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
| `DB_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5432/ai_business_assistant` |
| `DB_USERNAME` | JDBC username | `ai_business_assistant` |
| `DB_PASSWORD` | JDBC password | Required; no application default |
| `FRONTEND_ORIGIN` | Allowed browser CORS origin | `http://127.0.0.1:3000` |

CORS allows `POST`. The test profile provides non-secret Gemini placeholders; database credentials still come from the environment.

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

The current suite contains eight tests: one structured chat endpoint test, four persistence tests, and three customer-tool tests. The endpoint test substitutes `BusinessAnalysisService` and verifies query forwarding and all three analysis fields. Persistence tests verify migration history, seeded repository reads, historical order totals, relationships, and generated IDs. Customer-tool tests call the tool directly against PostgreSQL and verify seeded totals, average rounding, zero orders, and missing-customer behavior. Tests do not call Gemini or evaluate model tool selection/response quality. Use a development database with the original seed data. Test inserts roll back, but identity sequences advance.

## API

The endpoint consumes and produces `application/json` and accepts `{"query":"..."}`. Examples below use the default local port. Responses are illustrative; calling the endpoint sends the query to Gemini and may incur API usage costs.

### `POST /api/chat`

```bash
curl http://localhost:8080/api/chat \
  --header 'Content-Type: application/json' \
  --data '{"query":"Do we have any products that need restocking?"}'
```

```json
{
  "summary": "Three products currently need restocking.",
  "insights": ["USB-C Cable has the largest gap between current and minimum stock."],
  "recommendations": ["Prioritize replenishing USB-C Cable and the other low-stock products."]
}
```

`BusinessAnalysisDTO` is a Java record containing `summary`, `insights`, and `recommendations`; the latter two fields are lists of strings. `/api/chat` is also the endpoint used by Flutter. The previous separate business-analysis route, `ChatService`, and `ChatResponseDTO` are removed. Application logs record analysis request lengths/completion timing and tool arguments/results without recording the query text.

For customer analysis, use the same endpoint and contract:

```bash
curl http://localhost:8080/api/chat \
  --header 'Content-Type: application/json' \
  --data '{"query":"What are the purchase statistics for customer ID 1?"}'
```

Customer statistics DTOs are internal tool results; they are not new HTTP response fields. The system prompt also instructs out-of-scope requests to use `BusinessAnalysisDTO`, with empty insights and one suggestion to ask about the supported business domains.

## Current Limitations

- Tools are read-only. Policy/document retrieval, write operations, units-sold analysis, and margins are not implemented.
- Customer statistics are lifetime aggregates of recorded orders for one ID, without date filtering, customer search/ranking, segmentation, or predictive analysis.
- No server-side conversation memory or persisted chat history.
- No embeddings, pgvector extension, semantic search, or RAG.
- No business CRUD endpoints or order/inventory business logic.
- No application authentication, explicit request validation, or custom API error contract.
- No metrics, distributed tracing, production alerting, or AI response evaluation suite beyond the focused application logs and existing framework setup.

## Roadmap

This educational progression distinguishes completed work from planned capabilities; planned items are not delivery commitments:

1. **Completed:** PostgreSQL persistence, repositories, migrations, seed data, structured chat, low-stock and product-stock queries, sales summaries, customer purchase statistics, current-date Tool Calling, and a resource-based business prompt.
2. **Next:** Additional explicit read-only business queries beyond the existing tools.
3. Conversation Memory.
4. Embeddings.
5. pgvector semantic search.
6. RAG.
7. Further reliability, security, and observability work.
8. AI response evaluation.
9. MCP and Agents concepts later.

## Scope / Non-Goals

This is an incremental learning project, not a full ERP. The current scope excludes a large CRUD surface, microservices, Kafka, complex authentication, and premature Agents/MCP work. Generated SQL is not the default planned LLM strategy; the next phase focuses on explicit read-only tools. Frontend work belongs to the separate Flutter project.
