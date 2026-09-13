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
