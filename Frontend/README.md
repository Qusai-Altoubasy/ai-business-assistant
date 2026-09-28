# AI Business Assistant — Flutter frontend

Desktop-first Flutter client for the backend's two AI chat endpoints. The
current implementation provides sales, inventory, and customer purchase analysis
through the existing chat flow.
The sidebar's Chat Mode section selects Structured Chat or Streaming Chat.
Both modes share one chat screen, composer, and conversation.

## Prerequisites

- Flutter stable (the project was generated with Flutter 3.44 and Dart 3.12)
- Chrome for Flutter Web, or the Linux desktop toolchain
- The backend running and exposing `POST /api/chat` and `POST /api/chat/stream`

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
exact origin. Keep CORS restricted to the frontend origin.

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
- Trims messages and sends them with a stable UUID v4 conversation ID as JSON to the selected endpoint. Follow-up requests and mode switches reuse that ID.
- Renders structured `summary`, `insights`, and `recommendations` inside the
  existing assistant message. Empty list sections are hidden.
- Streaming Chat consumes JSON SSE events from `POST /api/chat/stream` and appends
  each CHUNK exactly as received to one assistant message. Tool events show temporary
  inventory/customer/sales progress, not separate chat messages. DONE finalizes the
  response; ERROR or a connection ending without DONE preserves partial text and
  shows a separate safe error with Retry.
- Mode switching and sending are disabled during a response. New Chat or controller
  disposal aborts an active stream; late responses cannot update the new chat.
- Converts timeouts, network failures, unsuccessful responses, malformed
  payloads, and empty summaries into safe inline errors with Retry.
- New Chat resets local state, creates a new UUID v4 conversation ID, clears the composer, and preserves the selected mode.
- The sidebar lists example questions, not persisted conversation history.
- Business data, sales, and inventory analysis are marked available. The backend
  persists conversation history and a bounded AI context, but the frontend has
  no history list or resume flow. Semantic search, citations, and evaluation
  remain planned.

The backend is the authority for available data: it can read low-stock products,
stock for a product ID, recorded revenue/order counts in an inclusive date
range, and purchase statistics for a customer ID (order count, total spent,
and average order value across all recorded orders). Relative-date questions
use the backend's current date. Seeded orders
cover January–March 2026; last month may be outside that period, so the explicit
January–March suggested prompt is useful for the seeded data. Customer analysis can be
requested by typing, for example, `What are the purchase statistics for customer
ID 1?` in the same composer; it has no dedicated suggested prompt or profile
screen. Company-policy retrieval is not implemented.

The backend's resource-based prompt supports inventory, products, sales, orders,
customers, and related general business concepts. It instructs unrelated
requests to return an out-of-scope summary, empty insights, and a business-focused
recommendation. The frontend renders those fields through the same assistant
widget in Structured Chat. Streaming Chat shows the generated text and subtle
tool progress; neither mode implements separate domain filtering.

The backend endpoint accepts `{"conversationId":"...","query":"..."}` and returns
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
├── core/network/                # Dio, streamed HTTP, and normalized failures
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
are not displayed as content. The same repository/controller also handles streamed
`ChatStreamEvent` values and temporary progress on the existing message entity.

Dio continues handling structured requests. Streaming uses `http` 1.6's Fetch-backed
browser client inside the same `ApiClient`, because Dio's XHR web adapter buffers
responses until completion. A streaming UTF-8 decoder and line splitter handle
arbitrary byte fragments, CRLF, comments, and multiline SSE data. Unknown event
types and malformed JSON are rejected safely. Abortable requests support cleanup
on New Chat, DONE/ERROR, or disposal. No generation timers or polling are used.
Nginx API proxy buffering is disabled so deployed SSE also arrives progressively.
Any additional reverse proxy must likewise allow streaming; the existing CORS
origin configuration still applies. The stream has a two-minute inactivity timeout.

## Verification

```bash
flutter analyze
flutter test
flutter build web \
  --dart-define=API_BASE_URL=http://127.0.0.1:8080
```

## Container image

The frontend Dockerfile builds the Web client and serves it with Nginx:

With Docker Compose, set `API_BASE_URL` to the frontend origin. Nginx forwards
`/api/` requests to the backend by container name. The standalone image build
below can still use a directly reachable backend URL.

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
