# Local Docker demo

This directory runs the Quarkus backend, Flutter Web frontend, and PostgreSQL 18
in Docker for local/demo use. All services are published only on `127.0.0.1`.

## Configuration

From this directory, copy the single committed template once:

```bash
cp .env.example .env
```

Edit `.env` to supply your Gemini Developer API key, select the model, and
configure the ports and PostgreSQL settings. If `.env` already exists, update
it to include the template's settings instead of overwriting it. This is
`deploy/.env` relative to the repository root; Git ignores it while tracking
`deploy/.env.example`. Compose automatically reads this directory's `.env`.
Exported shell variables take precedence over values in the file.

The deployment uses:

| Variable | Purpose |
| --- | --- |
| `FRONTEND_PORT` | Host port for Flutter Web |
| `BACKEND_PORT` | Host port for Quarkus |
| `API_BASE_URL` | Backend URL compiled into the Flutter Web app |
| `FRONTEND_ORIGIN` | Browser origin allowed by backend CORS |
| `GEMINI_API_KEY` | Gemini Developer API key; set this only in `.env` |
| `GEMINI_MODEL` | Gemini chat model ID |
| `POSTGRES_DB` | Database created on first initialization; default `ai_business_assistant` |
| `POSTGRES_USER` | Database user; default `ai_business_assistant` |
| `POSTGRES_PASSWORD` | Database password; local development default `dev_password` |
| `POSTGRES_PORT` | Host PostgreSQL port; default `5432` |

Compose supplies the backend's `DB_USERNAME`, `DB_PASSWORD`, and `DB_URL` from
these PostgreSQL settings, with the JDBC URL using `postgres:5432` internally.

`API_BASE_URL` uses `http://127.0.0.1:8080`, not Docker's `backend` service
name, because Flutter Web API calls are made by the host browser rather than by
the frontend container.

## Run

Build and start all services (the backend waits for PostgreSQL to be healthy):

```bash
docker compose up --build
```

Open the frontend at <http://127.0.0.1:3000>. The backend chat endpoint is
`POST http://127.0.0.1:8080/api/chat`.

It accepts `{"query":"..."}` and returns `summary`, `insights`, and
`recommendations`, which Flutter renders as analysis sections. Sales summaries,
low-stock products, product stock, customer purchase statistics, and relative-date
queries are available through this chat endpoint. The backend uses read-only
tools internally; the frontend receives only the final analysis.

Customer statistics are requested through the same composer and endpoint, for
example `What are the purchase statistics for customer ID 1?`. They aggregate
all recorded orders for that customer; no new service, route, or deployment
variable is required.

Validate the resolved Compose configuration:

```bash
docker compose config --quiet
```

Stop and remove the containers:

```bash
docker compose down
```

The named volume `ai-business-assistant-postgres-data` is mounted at
`/var/lib/postgresql`, with PostgreSQL 18 data stored in `18/docker` beneath it.
It survives container recreation and `down`;
`down -v` deletes the data. PostgreSQL initializes `POSTGRES_DB` and credentials
only when this volume is empty, so changing `POSTGRES_*` does not update an
existing database.

Flyway runs V1 (four business tables) and V2 (10 products, 5 customers, 12 orders,
24 order items) on backend startup. Hibernate validates the resulting schema.
Flyway creates tables inside the database, not the database itself.

For a backend running through Maven, start just the database:

```bash
docker compose up -d --wait postgres
```

Load this same `deploy/.env` into the shell and map its PostgreSQL settings to
`DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` using the
[local backend instructions](../README.md#backend). Quarkus receives these
exported variables; no separate backend environment file is needed.

## Rebuild After Changes

From `deploy/`, rebuild and recreate the application containers:

```bash
docker compose up -d --build backend frontend
```

To bypass Docker build-layer cache and check for newer base images:

```bash
docker compose build --no-cache --pull backend frontend
docker compose up -d --force-recreate backend frontend
```

The system prompt at `Backend/src/main/resources/prompts/business-analysis-system.txt`
is packaged with the backend, rather than mounted or loaded from `.env`. After
changing tools or this prompt, rebuild and recreate the backend image:

```bash
docker compose up -d --build --force-recreate backend
```

`API_BASE_URL` is compiled into the Flutter bundle. Changing it in `.env`
requires rebuilding the frontend image. The Web bootstrap cleans up legacy
Flutter service workers/caches, and Nginx serves HTML and bootstrap files with
`Cache-Control: no-store`. If a browser tab still runs an old bundle, close it
and clear site data for `http://127.0.0.1:3000` before reopening it. Docker build
cache and browser cache are separate.

These commands preserve the PostgreSQL volume. Seeded sales cover January–March
2026; use an explicit period in that range to exercise the sales demo. Questions
such as "last month" use the backend's current date and may have no matching
orders.
