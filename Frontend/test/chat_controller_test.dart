import 'dart:async';

import 'package:ai_business_assistant/core/network/api_exception.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/business_analysis.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/chat_message.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/chat_stream_event.dart';
import 'package:ai_business_assistant/features/chat/presentation/controllers/chat_state.dart';
import 'package:ai_business_assistant/features/chat/domain/repositories/chat_repository.dart';
import 'package:ai_business_assistant/features/chat/presentation/controllers/chat_controller.dart';
import 'package:flutter_test/flutter_test.dart';

const _analysis = BusinessAnalysis(
  summary: 'Hello from the backend',
  insights: ['Low stock'],
  recommendations: ['Restock'],
);

void main() {
  group('Streaming mode', () {
    test(
      'updates one bubble progressively, displays tool progress and finalizes on DONE',
      () async {
        final repository = _StreamingRepository();
        final controller = ChatController(repository)
          ..setMode(ChatMode.streaming);
        addTearDown(controller.dispose);
        final pending = controller.sendMessage('Inventory?');
        await _flush();
        final assistantId = controller.state.messages.last.id;
        expect(
          controller.state.messages.last.progressLabel,
          'Waiting for response…',
        );
        expect(await controller.sendMessage('Duplicate'), isFalse);
        controller.setMode(ChatMode.structured);
        expect(controller.state.mode, ChatMode.streaming);
        repository.streams.single.add(
          const ChatStreamEvent(
            StreamEventType.toolStarted,
            'getLowStockProducts',
          ),
        );
        await _flush();
        expect(
          controller.state.messages.last.progressLabel,
          'Checking inventory…',
        );
        repository.streams.single.add(
          const ChatStreamEvent(
            StreamEventType.toolCompleted,
            'getLowStockProducts',
          ),
        );
        await _flush();
        expect(
          controller.state.messages.last.progressLabel,
          'Business data retrieved…',
        );
        repository.streams.single.add(
          const ChatStreamEvent(StreamEventType.chunk, 'Hello'),
        );
        await _flush();
        expect(controller.state.messages.last.content, 'Hello');
        expect(controller.state.isSubmitting, isTrue);
        repository.streams.single.add(
          const ChatStreamEvent(StreamEventType.chunk, ' world'),
        );
        await _flush();
        expect(controller.state.messages, hasLength(2));
        expect(controller.state.messages.last.id, assistantId);
        expect(controller.state.messages.last.content, 'Hello world');
        repository.streams.single.add(
          const ChatStreamEvent(StreamEventType.done, null),
        );
        await pending;
        expect(controller.state.messages.last.status, MessageStatus.success);
        expect(controller.state.messages.last.progressLabel, isNull);
        expect(controller.state.isSubmitting, isFalse);
        controller.setMode(ChatMode.structured);
        await controller.sendMessage('Follow-up');
        expect(repository.modes, [ChatMode.streaming, ChatMode.structured]);
        expect(repository.ids.first, repository.ids.last);
        expect(controller.state.messages, hasLength(4));
      },
    );

    test(
      'ERROR preserves partial text and enables a retry without DONE',
      () async {
        final repository = _StreamingRepository();
        final controller = ChatController(repository)
          ..setMode(ChatMode.streaming);
        addTearDown(controller.dispose);
        final pending = controller.sendMessage('Question');
        await _flush();
        repository.streams.single
          ..add(const ChatStreamEvent(StreamEventType.chunk, 'Partial text'))
          ..add(
            const ChatStreamEvent(
              StreamEventType.error,
              'Unable to complete the streaming request.',
            ),
          );
        await pending;
        expect(controller.state.messages.last.content, 'Partial text');
        expect(
          controller.state.messages.last.error,
          'Unable to complete the streaming request.',
        );
        expect(controller.state.messages.last.status, MessageStatus.error);
        expect(controller.state.messages.last.progressLabel, isNull);
        expect(controller.state.isSubmitting, isFalse);
        final retry = controller.retryLast();
        await _flush();
        repository.streams.last.add(
          const ChatStreamEvent(StreamEventType.done, null),
        );
        await retry;
        expect(controller.state.messages, hasLength(2));
        expect(repository.ids.first, repository.ids.last);
      },
    );

    test('EOF without DONE is an interrupted response', () async {
      final repository = _StreamingRepository();
      final controller = ChatController(repository)
        ..setMode(ChatMode.streaming);
      addTearDown(controller.dispose);
      final pending = controller.sendMessage('Question');
      await _flush();
      repository.streams.single.add(
        const ChatStreamEvent(StreamEventType.chunk, 'Partial'),
      );
      await repository.streams.single.close();
      await pending;
      expect(controller.state.messages.last.status, MessageStatus.error);
      expect(controller.state.messages.last.content, 'Partial');
      expect(controller.state.isSubmitting, isFalse);
    });

    test(
      'New Chat cancels the stream, changes UUID and preserves mode',
      () async {
        final repository = _StreamingRepository();
        final controller = ChatController(repository)
          ..setMode(ChatMode.streaming);
        addTearDown(controller.dispose);
        final pending = controller.sendMessage('Old question');
        await _flush();
        controller.resetChat();
        await pending;
        expect(controller.state.messages, isEmpty);
        expect(controller.state.mode, ChatMode.streaming);
        final next = controller.sendMessage('New question');
        await _flush();
        expect(repository.ids.first, isNot(repository.ids.last));
        repository.streams.last.add(
          const ChatStreamEvent(StreamEventType.done, null),
        );
        await next;
      },
    );

    test('disposal cancels streaming and ignores late completion', () async {
      final repository = _StreamingRepository();
      final controller = ChatController(repository)
        ..setMode(ChatMode.streaming);
      final pending = controller.sendMessage('Question');
      await _flush();
      controller.dispose();
      await pending;
      expect(repository.streams.single.isClosed, isTrue);
    });
  });
  group('ChatController', () {
    test('sends a normalized message through the repository', () async {
      final repository = _FakeChatRepository(response: _analysis);
      final controller = ChatController(repository);

      await controller.sendMessage('  Hello AI  ');

      expect(repository.messages, ['Hello AI']);
      expect(
        repository.conversationIds.single,
        matches(
          RegExp(
            r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
          ),
        ),
      );
      controller.dispose();
    });

    test('successful response adds an assistant message', () async {
      final controller = ChatController(
        _FakeChatRepository(response: _analysis),
      );

      final submitted = await controller.sendMessage('Hello AI');

      expect(submitted, isTrue);
      expect(controller.state.messages, hasLength(2));
      expect(controller.state.messages.last.role, ChatRole.assistant);
      expect(controller.state.messages.first.content, 'Hello AI');
      expect(controller.state.messages.first.analysis, isNull);
      expect(controller.state.messages.last.content, _analysis.summary);
      expect(controller.state.messages.last.analysis, same(_analysis));
      expect(controller.state.messages.last.analysis!.insights, ['Low stock']);
      expect(controller.state.messages.last.analysis!.recommendations, [
        'Restock',
      ]);
      expect(controller.state.messages.last.status, MessageStatus.success);
      expect(controller.state.isSubmitting, isFalse);
      controller.dispose();
    });

    test('failed request creates a safe inline assistant error', () async {
      final controller = ChatController(
        _FakeChatRepository(
          error: const ApiException(
            ApiFailureType.unavailable,
            'The AI service is unavailable.',
          ),
        ),
      );

      await controller.sendMessage('Hello AI');

      expect(controller.state.messages, hasLength(2));
      expect(controller.state.messages.last.role, ChatRole.assistant);
      expect(controller.state.messages.last.status, MessageStatus.error);
      expect(
        controller.state.messages.last.content,
        'The AI service is unavailable.',
      );
      expect(controller.state.lastFailedPrompt, 'Hello AI');
      controller.dispose();
    });

    test(
      'retry replaces the failed exchange with structured analysis',
      () async {
        final repository = _FakeChatRepository(
          response: _analysis,
          error: const ApiException(ApiFailureType.network, 'Please retry.'),
        );
        final controller = ChatController(repository);
        addTearDown(controller.dispose);
        await controller.sendMessage('  Stock?  ');
        expect(controller.state.messages.last.analysis, isNull);
        expect(controller.state.isSubmitting, isFalse);
        repository.error = null;

        expect(await controller.retryLast(), isTrue);
        expect(repository.messages, ['Stock?', 'Stock?']);
        expect(
          repository.conversationIds.first,
          repository.conversationIds.last,
        );
        expect(controller.state.messages, hasLength(2));
        expect(controller.state.messages.first.content, 'Stock?');
        expect(controller.state.messages.last.analysis, same(_analysis));
        expect(controller.state.messages.last.status, MessageStatus.success);
        expect(controller.state.lastFailedPrompt, isNull);
        expect(await controller.retryLast(), isFalse);
      },
    );

    test('summary-only response and reset keep local state behavior', () async {
      final repository = _FakeChatRepository(
        response: const BusinessAnalysis(summary: 'No issues.'),
      );
      final controller = ChatController(repository);
      addTearDown(controller.dispose);
      await controller.sendMessage('Status?');
      expect(controller.state.messages.last.analysis!.insights, isEmpty);
      expect(controller.state.messages.last.analysis!.recommendations, isEmpty);
      controller.resetChat();
      expect(controller.state.messages, isEmpty);
      expect(controller.state.isSubmitting, isFalse);
      expect(controller.state.lastFailedPrompt, isNull);
      await controller.sendMessage('Status again?');
      expect(repository.conversationIds, hasLength(2));
      expect(
        repository.conversationIds.first,
        isNot(repository.conversationIds.last),
      );
    });

    test('empty message is not submitted', () async {
      final repository = _FakeChatRepository(response: _analysis);
      final controller = ChatController(repository);

      final submitted = await controller.sendMessage('   \n  ');

      expect(submitted, isFalse);
      expect(repository.messages, isEmpty);
      expect(controller.state.messages, isEmpty);
      controller.dispose();
    });

    test(
      'duplicate submission is ignored while a request is pending',
      () async {
        final repository = _PendingChatRepository();
        final controller = ChatController(repository);

        final first = controller.sendMessage('First');
        final second = await controller.sendMessage('Second');
        expect(controller.state.isSubmitting, isTrue);
        expect(controller.state.messages.last.status, MessageStatus.sending);
        expect(controller.state.messages.last.analysis, isNull);
        repository.complete(_analysis);
        await first;

        expect(second, isFalse);
        expect(repository.messages, ['First']);
        controller.dispose();
      },
    );

    test('reset ignores the response from the previous conversation', () async {
      final repository = _PendingChatRepository();
      final controller = ChatController(repository);
      addTearDown(controller.dispose);

      final pending = controller.sendMessage('Old question');
      controller.resetChat();
      repository.complete(_analysis);
      await pending;

      expect(controller.state.messages, isEmpty);
      expect(controller.state.isSubmitting, isFalse);
    });
  });
}

class _FakeChatRepository implements ChatRepository {
  _FakeChatRepository({this.response, this.error});

  final BusinessAnalysis? response;
  Object? error;
  final List<String> messages = [];
  final List<String> conversationIds = [];

  @override
  Stream<ChatStreamEvent> streamMessage(
    String message,
    String conversationId, {
    Future<void>? abortTrigger,
  }) => throw UnimplementedError();

  @override
  Future<BusinessAnalysis> sendMessage(
    String message,
    String conversationId,
  ) async {
    messages.add(message);
    conversationIds.add(conversationId);
    if (error != null) throw error!;
    return response!;
  }
}

class _PendingChatRepository implements ChatRepository {
  final messages = <String>[];
  final _completer = Completer<BusinessAnalysis>();

  @override
  Stream<ChatStreamEvent> streamMessage(
    String message,
    String conversationId, {
    Future<void>? abortTrigger,
  }) => throw UnimplementedError();

  @override
  Future<BusinessAnalysis> sendMessage(String message, String conversationId) {
    messages.add(message);
    return _completer.future;
  }

  void complete(BusinessAnalysis value) => _completer.complete(value);
}

Future<void> _flush() => Future<void>.delayed(Duration.zero);

class _StreamingRepository implements ChatRepository {
  final ids = <String>[];
  final modes = <ChatMode>[];
  final streams = <StreamController<ChatStreamEvent>>[];

  @override
  Future<BusinessAnalysis> sendMessage(
    String message,
    String conversationId,
  ) async {
    ids.add(conversationId);
    modes.add(ChatMode.structured);
    return _analysis;
  }

  @override
  Stream<ChatStreamEvent> streamMessage(
    String message,
    String conversationId, {
    Future<void>? abortTrigger,
  }) {
    ids.add(conversationId);
    modes.add(ChatMode.streaming);
    final events = StreamController<ChatStreamEvent>();
    streams.add(events);
    abortTrigger?.then((_) {
      if (!events.isClosed) {
        events.addError(
          const ApiException(ApiFailureType.network, 'Cancelled'),
        );
        events.close();
      }
    });
    return events.stream;
  }
}
