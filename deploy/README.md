# Local Docker demo

This directory defines a two-service local demo: a Quarkus backend and a
Flutter Web frontend. Both services are published only on `127.0.0.1`. There is
no database service; persistence can be added when the project needs it.

## Prerequisites

- Docker with the Compose plugin
- `Backend/Dockerfile`
- `Frontend/Dockerfile`
- A Gemini Developer API key

> **Current repository state:** `docker-compose.yml` expects both service
> Dockerfiles. The frontend Dockerfile is present; the backend container files
> must be added or merged before building the complete stack.

## Configuration

From this directory, copy the safe template:

```bash
cd deploy
cp .env.example .env
```

Set your Gemini Developer API key in `.env`. This file is ignored by Git and
must never be committed.

| Variable | Purpose |
| --- | --- |
| `FRONTEND_PORT` | Host port for Flutter Web |
| `BACKEND_PORT` | Host port for Quarkus |
| `API_BASE_URL` | Backend URL compiled into the Flutter Web application |
| `FRONTEND_ORIGIN` | Browser origin allowed by backend CORS |
| `GEMINI_API_KEY` | Gemini Developer API key; set only in `.env` |
| `GEMINI_MODEL` | Gemini chat model ID |

`API_BASE_URL` uses `http://127.0.0.1:8080`, not Docker's `backend` service
name, because the host browser makes the API requests. `FRONTEND_ORIGIN` must
match the origin opened by that browser.

## Validate the configuration

Validate the committed configuration with the non-secret example values:

```bash
docker compose --env-file .env.example config --quiet
```

Validate your local `.env` configuration without printing the resolved Compose
document or interpolated values:

```bash
docker compose config --quiet
```

Both commands exit successfully with no output when the configuration is valid.

## Run

Build and start both services:

```bash
docker compose up --build
```

Open <http://127.0.0.1:3000>. The backend is available at
<http://127.0.0.1:8080>, and its deterministic endpoint is
<http://127.0.0.1:8080/hello>.

Follow service logs:

```bash
docker compose logs -f
```

Stop and remove the containers and network:

```bash
docker compose down
```
