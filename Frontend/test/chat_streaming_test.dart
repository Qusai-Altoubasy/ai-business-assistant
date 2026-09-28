import 'dart:async';
import 'dart:convert';

import 'package:ai_business_assistant/core/config/app_config.dart';
import 'package:ai_business_assistant/core/network/api_client.dart';
import 'package:ai_business_assistant/core/network/api_exception.dart';
import 'package:ai_business_assistant/features/chat/data/datasources/chat_remote_data_source.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/chat_stream_event.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test(
    'POST SSE handles fragmented UTF-8, CRLF, comments and multiline data',
    () async {
      final client = ApiClient(
        config: const AppConfig(apiBaseUrl: 'http://localhost:8080'),
        streamingClient: MockClient.streaming((request, body) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/chat/stream');
          expect(request.headers['Accept'], 'text/event-stream');
          expect(jsonDecode(await utf8.decoder.bind(body).join()), {
            'conversationId': '550e8400-e29b-41d4-a716-446655440000',
            'query': 'Stock?',
          });
          const wire =
              ': heartbeat\r\nid: 1\r\nevent: message\r\n'
              'data: {"type":"CHUNK",\r\ndata: "content":" مرحبًا €"}\r\n\r\n'
              'data: {"type":"DONE","content":null}\n\n';
          return http.StreamedResponse(
            Stream.fromIterable(utf8.encode(wire).map((byte) => [byte])),
            200,
            headers: {'content-type': 'text/event-stream; charset=utf-8'},
          );
        }),
      );
      addTearDown(client.close);
      final events = await ChatRemoteDataSource(client)
          .streamMessage('  Stock?  ', '550e8400-e29b-41d4-a716-446655440000')
          .toList();
      expect(events.map((event) => event.type), [
        StreamEventType.chunk,
        StreamEventType.done,
      ]);
      expect(events.first.content, ' مرحبًا €');
      expect(events.last.content, isNull);
    },
  );

  test(
    'delivers a chunk while connection remains open and aborts after DONE',
    () async {
      final bytes = StreamController<List<int>>();
      final opened = Completer<http.AbortableRequest>();
      final client = ApiClient(
        config: const AppConfig(apiBaseUrl: 'http://localhost:8080'),
        streamingClient: MockClient.streaming((request, _) async {
          opened.complete(request as http.AbortableRequest);
          return http.StreamedResponse(
            bytes.stream,
            200,
            headers: {'content-type': 'text/event-stream'},
          );
        }),
      );
      addTearDown(client.close);
      addTearDown(bytes.close);
      final first = Completer<ChatStreamEvent>();
      final finished = Completer<void>();
      ChatRemoteDataSource(client)
          .streamMessage('Question', 'id')
          .listen(
            (event) {
              if (!first.isCompleted) first.complete(event);
            },
            onDone: finished.complete,
            onError: (Object error) => finished.completeError(error),
          );
      final request = await opened.future;
      bytes.add(utf8.encode('data: {"type":"CHUNK","content":"Hello"}\n\n'));
      expect((await first.future).content, 'Hello');
      expect(finished.isCompleted, isFalse);
      bytes.add(utf8.encode('data: {"type":"DONE","content":null}\n\n'));
      await finished.future;
      await request.abortTrigger;
    },
  );

  for (final payload in [
    '{"type":"UNKNOWN","content":"not a chunk"}',
    '{"type":"CHUNK","content":42}',
    'not JSON',
  ]) {
    test(
      'invalid SSE payload is a safe normalized failure: $payload',
      () async {
        final client = ApiClient(
          config: const AppConfig(apiBaseUrl: 'http://localhost:8080'),
          streamingClient: MockClient.streaming(
            (_, _) async => http.StreamedResponse(
              Stream.value(utf8.encode('data: $payload\n\n')),
              200,
              headers: {'content-type': 'text/event-stream'},
            ),
          ),
        );
        addTearDown(client.close);
        await expectLater(
          ChatRemoteDataSource(client).streamMessage('Question', 'id').toList(),
          throwsA(
            isA<ApiException>().having(
              (e) => e.type,
              'type',
              ApiFailureType.malformed,
            ),
          ),
        );
      },
    );
  }

  test(
    'HTTP failure is normalized without forwarding server internals',
    () async {
      final client = ApiClient(
        config: const AppConfig(apiBaseUrl: 'http://localhost:8080'),
        streamingClient: MockClient.streaming(
          (_, _) async => http.StreamedResponse(
            Stream.value(utf8.encode('Internal SQL / credentials')),
            500,
          ),
        ),
      );
      addTearDown(client.close);
      await expectLater(
        ChatRemoteDataSource(client).streamMessage('Question', 'id').toList(),
        throwsA(
          isA<ApiException>().having(
            (e) => e.userMessage,
            'safe message',
            'The AI service returned an unexpected response.',
          ),
        ),
      );
    },
  );
}
