# AI Business Assistant — Flutter frontend

Desktop-first Flutter client for the backend's AI greeting endpoint. The
current implementation is intentionally scoped to chat; the other navigation
items represent planned capabilities.

## Prerequisites

- Flutter stable (the project was generated with Flutter 3.44 and Dart 3.12)
- Chrome for Flutter Web, or the Linux desktop toolchain
- The backend running and exposing `GET /hello/ai/{message}`

## Install

```bash
cd Frontend
flutter pub get
```

## Run on the Web

The following hostname and port match the backend's default allowed CORS
origin:

```bash
flutter run -d chrome \
  --web-hostname 127.0.0.1 \
  --web-port 3000 \
  --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

If you use another Web origin, set `FRONTEND_ORIGIN` for the backend to that
exact origin. Avoid an unrestricted CORS policy outside disposable local demos.

## Run on Linux

```bash
flutter run -d linux \
  --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

`API_BASE_URL` must be an absolute URL and defaults to
`http://localhost:8080`. For a physical device or remote deployment, provide an
address reachable from that device instead of `localhost`.

## Current behavior

- Starts in a welcome state with four editable suggested prompts.
- Enter sends a message; Shift+Enter inserts a newline.
- Trims and URL-encodes messages before calling `GET /hello/ai/{message}`.
- Renders successful plain-text responses as assistant messages.
- Converts timeouts, network failures, unsuccessful responses, and empty
  responses into safe inline errors with Retry.
- New Chat and Clear reset local state.
- Recent conversations are sample content and are not persisted.

The backend endpoint returns plain text. Calling it sends the message to Gemini
and may incur API usage costs.

## Architecture

```text
lib/
├── app/                         # application shell and design system
├── core/config/                 # dart-define configuration
├── core/network/                # Dio client and normalized failures
└── features/chat/
    ├── data/                    # remote source and repository implementation
    ├── domain/                  # message entities and repository contract
    └── presentation/            # Riverpod controller, page, and widgets
```

The message entity leaves room for optional sources, tools, latency, token
usage, model information, and citations without coupling the UI to a future
backend response contract.

## Verification

```bash
flutter analyze
flutter test
flutter build web \
  --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

## Container image

The frontend Dockerfile builds the Web client and serves it with Nginx:

```bash
cd Frontend
docker build \
  --build-arg API_BASE_URL=http://127.0.0.1:8080 \
  -t ai-business-assistant-frontend .
```
