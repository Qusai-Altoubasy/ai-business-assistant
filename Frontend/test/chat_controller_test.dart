import 'dart:async';

import 'package:ai_business_assistant/core/network/api_exception.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/business_analysis.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/chat_message.dart';
import 'package:ai_business_assistant/features/chat/domain/repositories/chat_repository.dart';
import 'package:ai_business_assistant/features/chat/presentation/controllers/chat_controller.dart';
import 'package:flutter_test/flutter_test.dart';

const _analysis = BusinessAnalysis(
  summary: 'Hello from the backend',
  insights: ['Low stock'],
  recommendations: ['Restock'],
);

void main() {
  group('ChatController', () {
    test('sends a normalized message through the repository', () async {
      final repository = _FakeChatRepository(response: _analysis);
      final controller = ChatController(repository);

      await controller.sendMessage('  Hello AI  ');

      expect(repository.messages, ['Hello AI']);
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
        expect(controller.state.messages, hasLength(2));
        expect(controller.state.messages.first.content, 'Stock?');
        expect(controller.state.messages.last.analysis, same(_analysis));
        expect(controller.state.messages.last.status, MessageStatus.success);
        expect(controller.state.lastFailedPrompt, isNull);
        expect(await controller.retryLast(), isFalse);
      },
    );

    test('summary-only response and reset keep local state behavior', () async {
      final controller = ChatController(
        _FakeChatRepository(
          response: const BusinessAnalysis(summary: 'No issues.'),
        ),
      );
      addTearDown(controller.dispose);
      await controller.sendMessage('Status?');
      expect(controller.state.messages.last.analysis!.insights, isEmpty);
      expect(controller.state.messages.last.analysis!.recommendations, isEmpty);
      controller.resetChat();
      expect(controller.state.messages, isEmpty);
      expect(controller.state.isSubmitting, isFalse);
      expect(controller.state.lastFailedPrompt, isNull);
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
  });
}

class _FakeChatRepository implements ChatRepository {
  _FakeChatRepository({this.response, this.error});

  final BusinessAnalysis? response;
  Object? error;
  final List<String> messages = [];

  @override
  Future<BusinessAnalysis> sendMessage(String message) async {
    messages.add(message);
    if (error != null) throw error!;
    return response!;
  }
}

class _PendingChatRepository implements ChatRepository {
  final messages = <String>[];
  final _completer = Completer<BusinessAnalysis>();

  @override
  Future<BusinessAnalysis> sendMessage(String message) {
    messages.add(message);
    return _completer.future;
  }

  void complete(BusinessAnalysis value) => _completer.complete(value);
}
