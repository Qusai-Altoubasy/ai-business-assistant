# AI Business Assistant — Flutter frontend

Desktop-first Flutter client for the backend's AI chat endpoint. The
current implementation provides sales and inventory analysis through chat.
The sidebar marks these capabilities as `In chat`; separate module screens
are not implemented, and the remaining capabilities are marked `Planned`.

## Prerequisites

- Flutter stable (the project was generated with Flutter 3.44 and Dart 3.12)
- Chrome for Flutter Web, or the Linux desktop toolchain
- The backend running and exposing `POST /api/chat`

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

- Starts with four editable suggested prompts for sales by period, low-stock
  products, stock for a product ID, and last month's sales. Selecting one fills
  the composer; Send or Enter submits it through the same chat flow.
- Enter sends a message; Shift+Enter inserts a newline.
- Trims messages and sends them as JSON to `POST /api/chat`.
- Renders structured `summary`, `insights`, and `recommendations` inside the
  existing assistant message. Empty list sections are hidden.
- Converts timeouts, network failures, unsuccessful responses, malformed
  payloads, and empty summaries into safe inline errors with Retry.
- New Chat resets local state and clears the composer.
- The sidebar lists example questions, not persisted conversation history.
- Business data, sales, and inventory analysis are marked available. Semantic
  search, conversation memory, citations, and evaluation remain planned.

The backend is the authority for available data: it can read low-stock products,
stock for a product ID, and recorded revenue/order counts in an inclusive date
range. Relative-date questions use the backend's current date. Seeded orders
cover January–March 2026; last month may be outside that period, so the explicit
January–March suggested prompt is useful for the demo. There is no customer
statistics or company-policy retrieval capability yet.

The backend endpoint accepts `{"query":"..."}` and returns
`{"summary":"...","insights":["..."],"recommendations":["..."]}`. Missing or
null lists become empty lists; blank list entries are omitted. Invalid field
types and blank summaries produce safe inline errors with Retry. Calling it
sends the message to Gemini and may incur API usage costs.

`POST /api/chat` now returns the structured analysis directly. The previous
separate business-analysis route and free-form `response` contract are removed.

## Architecture

```text
lib/
├── app/                         # application shell and design system
├── core/config/                 # dart-define configuration
├── core/network/                # Dio client and normalized failures
└── features/chat/
    ├── data/                    # response model, remote source, and repository
    ├── domain/                  # message entities and repository contract
    └── presentation/            # Riverpod controller, page, and widgets
```

The remote source deserializes a typed response model; the repository maps it
to a domain `BusinessAnalysis`. Riverpod stores it on the existing message
entity, and the assistant widget renders its sections. Message `content` keeps
the summary as a text fallback; lists remain structured. Existing optional
metadata is separate and unused by this response. Backend tool execution details
are not part of the frontend contract.

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

The Web bootstrap removes legacy Flutter service workers and Flutter caches
before loading the app without registering a new worker. Nginx serves the HTML
and bootstrap files with `Cache-Control: no-store`. After an API-contract change,
rebuild and recreate the frontend container; `API_BASE_URL` is compiled into the
bundle rather than read at runtime. See [deployment instructions](../deploy/README.md#rebuild-after-changes).
