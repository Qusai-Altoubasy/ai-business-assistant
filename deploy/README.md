# Local Docker deployment

[docker-compose.yml](docker-compose.yml) runs the backend, the Flutter Web frontend and PostgreSQL. All ports are published only on `127.0.0.1`.

| Service | Container | Image / build | Port (host → container) |
| --- | --- | --- | --- |
| `postgres` | `ai-business-assistant-postgres` | `pgvector/pgvector:0.8.7-pg18-trixie` (PostgreSQL 18 with pgvector) | `${POSTGRES_PORT:-5432}` → `5432` |
| `backend` | `ai-business-assistant-backend` | Built from `../Backend/Dockerfile` | `${BACKEND_PORT}` → `8080` |
| `frontend` | `ai-business-assistant-frontend` | Built from `../Frontend/Dockerfile`, with the `API_BASE_URL` build argument | `${FRONTEND_PORT}` → `80` |

The backend starts after the `postgres` healthcheck (`pg_isready`) passes, and the frontend starts after the backend. The backend connects to `ai-business-assistant-postgres:5432`.

PostgreSQL data is stored in the named volume `ai-business-assistant-postgres-data`, mounted at `/var/lib/postgresql`. The volume is kept by `docker compose down` and deleted by `docker compose down -v`. PostgreSQL applies `POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD` only when the volume is empty.

## Configuration

Compose reads `.env` in this directory. From `deploy/`, copy the committed template once:

```bash
cp .env.example .env
```

Git ignores `.env`. Exported shell variables take precedence over values in the file.

| Variable | Used for | Compose default |
| --- | --- | --- |
| `FRONTEND_PORT` | Host port for the frontend | None — required |
| `BACKEND_PORT` | Host port for the backend | None — required |
| `API_BASE_URL` | Frontend build argument | None — required |
| `FRONTEND_ORIGIN` | Backend CORS origin | None — required |
| `GEMINI_API_KEY` | Gemini Developer API key | None — required |
| `GEMINI_MODEL` | Gemini chat model ID | None — required |
| `AI_DEFAULT_PROVIDER` | Provider for new conversations | `GEMINI` |
| `OLLAMA_BASE_URL` | Ollama server origin | `https://ai.llm.ensera.dev` |
| `OLLAMA_MODEL` | Ollama model ID | `qwen3-vl:8b-instruct-q8_0` |
| `OLLAMA_TIMEOUT` | Ollama request timeout | `120s` |
| `POSTGRES_DB` | Database name; also used in the backend's `DB_URL` | `ai_business_assistant` |
| `POSTGRES_USER` | Database user; passed to the backend as `DB_USERNAME` | `ai_business_assistant` |
| `POSTGRES_PASSWORD` | Database password; passed to the backend as `DB_PASSWORD` | `dev_password` |
| `POSTGRES_PORT` | Host port for PostgreSQL | `5432` |

`.env.example` sets every variable except `GEMINI_API_KEY` and `POSTGRES_PASSWORD`. Set both in `.env`.

`API_BASE_URL` is the frontend origin, `http://127.0.0.1:3000` in the template. The browser calls Nginx in the frontend container, which forwards `/api/` requests to `ai-business-assistant-backend:8080`; the browser cannot resolve Docker container names.

## Commands

Run these from `deploy/`.

Validate the resolved Compose configuration:

```bash
docker compose config --quiet
```

Build and start all services:

```bash
docker compose up -d --build
```

Start only PostgreSQL, for a backend run with Maven (see the [backend guide](../Backend/README.md#run-locally)):

```bash
docker compose up -d --wait postgres
```

View logs:

```bash
docker compose logs -f
```

```bash
docker compose logs -f backend
```

Stop and remove the containers, keeping the database volume:

```bash
docker compose down
```

Reset the data by also deleting the database volume; the next start recreates the database and Flyway reapplies the migrations:

```bash
docker compose down -v
```

With the template's ports, the frontend is at <http://127.0.0.1:3000> and the backend at <http://127.0.0.1:8080>. How the frontend and the backend behave is described in the [frontend README](../Frontend/README.md#current-behavior) and the [backend README](../Backend/README.md).

## Rebuild After Changes

These commands keep the PostgreSQL volume. Rebuild and recreate the application containers:

```bash
docker compose up -d --build backend frontend
```

Rebuild without the Docker build-layer cache and pull newer base images:

```bash
docker compose build --no-cache --pull backend frontend
docker compose up -d --force-recreate backend frontend
```

The system prompt at `Backend/src/main/resources/prompts/business-analysis-system.txt` is packaged into the backend image, not mounted or read from `.env`. After changing the prompt or the tools, rebuild and recreate the backend:

```bash
docker compose up -d --build --force-recreate backend
```

`API_BASE_URL` is compiled into the Flutter bundle, so changing it in `.env` requires rebuilding the frontend image.

## Troubleshooting

- **Ollama `upstream request timeout`:** this can come from the remote Ollama service or a gateway in front of it. Check those logs and test the configured `OLLAMA_BASE_URL` directly with its `/api/chat` endpoint. Raising `OLLAMA_TIMEOUT` does not change a remote gateway's timeout. The backend logs the provider and duration of each request, which helps tell an Ollama failure apart from a Gemini request.
- **Browser runs an old frontend bundle:** the Web bootstrap removes old Flutter service workers and caches, and Nginx serves `index.html`, `flutter_bootstrap.js` and `flutter_service_worker.js` with `Cache-Control: no-store`. If a tab still runs an old bundle, close it and clear site data for `http://127.0.0.1:3000`. Docker build cache and browser cache are separate.
