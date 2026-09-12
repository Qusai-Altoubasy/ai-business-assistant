# Local Docker demo

This directory runs the Quarkus backend and Flutter Web frontend in Docker for
local/demo use. Both services are published only on `127.0.0.1`. There is no
database service; PostgreSQL will be introduced later if the project needs
persistence for tool calling.

## Configuration

From this directory, copy the safe template and add your Gemini Developer API
key:

```bash
cp .env.example .env
```

The deployment uses:

| Variable | Purpose |
| --- | --- |
| `FRONTEND_PORT` | Host port for Flutter Web |
| `BACKEND_PORT` | Host port for Quarkus |
| `API_BASE_URL` | Backend URL compiled into the Flutter Web app |
| `FRONTEND_ORIGIN` | Browser origin allowed by backend CORS |
| `GEMINI_API_KEY` | Gemini Developer API key; set this only in `.env` |
| `GEMINI_MODEL` | Gemini chat model ID |

`API_BASE_URL` uses `http://127.0.0.1:8080`, not Docker's `backend` service
name, because Flutter Web API calls are made by the host browser rather than by
the frontend container.

## Run

Build and start both services:

```bash
docker compose up --build
```

Open the frontend at <http://127.0.0.1:3000>. The backend chat endpoint is
`POST http://127.0.0.1:8080/api/chat`.

Validate the resolved Compose configuration:

```bash
docker compose config
```

Stop and remove the containers:

```bash
docker compose down
```
