# AI Business Assistant — Flutter frontend

Desktop-first Flutter client for the repository's current AI greeting endpoint.
The frontend is intentionally scoped to chat. Analytics, Knowledge Base, Data
Sources, Tools, Evaluation, Settings, RAG, citations, and model selection are
presented only as planned capabilities.

## Prerequisites

- Flutter stable (the project was generated with Flutter 3.44 / Dart 3.12)
- Chrome for Flutter Web, or the Linux desktop toolchain
- The backend running and exposing `GET /hello/ai/{name}`

## Install and run

```bash
cd frontend
flutter pub get
flutter run -d chrome \
  --dart-define=API_BASE_URL=http://localhost:8080
```

For Linux desktop:

```bash
flutter run -d linux \
  --dart-define=API_BASE_URL=http://localhost:8080
```

`API_BASE_URL` defaults to `http://localhost:8080`. It must be an absolute URL.
For a physical device or remote deployment, use an address reachable from that
device rather than `localhost`.

## Current behavior

- Starts in a welcome state with four editable suggested prompts.
- Enter sends; Shift+Enter inserts a newline.
- Messages are URL encoded and sent as `GET /hello/ai/{encodedMessage}`.
- Successful plain-text responses are shown as assistant messages.
- Timeouts, network failures, non-success responses, and empty responses are
  normalized into inline errors with Retry.
- New Chat and Clear reset local state. Recent conversations are sample content
  and have no persistence.

The backend source was not available in this workspace at implementation time,
so its concrete response content type and Flutter Web CORS policy could not be
verified. The client requests a plain response and renders its string value.
Before using Chrome, ensure the backend allows the frontend origin (for example,
the local Flutter development origin) without using an unrestricted production
CORS policy.

## Architecture

```text
lib/
├── app/                         # application and design system
├── core/config/                 # dart-define configuration
├── core/network/                # Dio client and normalized failures
└── features/chat/
    ├── data/                    # remote source and repository implementation
    ├── domain/                  # message entities and repository contract
    └── presentation/            # Riverpod controller, page, reusable widgets
```

The message entity already leaves room for optional sources, tools, latency,
token usage, model, and citations without coupling the UI to a future backend
response contract.

## Verification

```bash
flutter analyze
flutter test
flutter build web
```
