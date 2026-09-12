import 'dart:async';

import 'package:ai_business_assistant/core/network/api_exception.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/chat_message.dart';
import 'package:ai_business_assistant/features/chat/domain/repositories/chat_repository.dart';
import 'package:ai_business_assistant/features/chat/presentation/controllers/chat_controller.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ChatController', () {
    test('sends a normalized message through the repository', () async {
      final repository = _FakeChatRepository(
        response: 'Hello from the backend',
      );
      final controller = ChatController(repository);

      await controller.sendMessage('  Hello AI  ');

      expect(repository.messages, ['Hello AI']);
      controller.dispose();
    });

    test('successful response adds an assistant message', () async {
      final controller = ChatController(
        _FakeChatRepository(response: 'Hello from the backend'),
      );

      final submitted = await controller.sendMessage('Hello AI');

      expect(submitted, isTrue);
      expect(controller.state.messages, hasLength(2));
      expect(controller.state.messages.last.role, ChatRole.assistant);
      expect(controller.state.messages.last.content, 'Hello from the backend');
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

    test('empty message is not submitted', () async {
      final repository = _FakeChatRepository(response: 'unused');
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
        repository.complete('Done');
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

  final String? response;
  final Object? error;
  final List<String> messages = [];

  @override
  Future<String> sendMessage(String message) async {
    messages.add(message);
    if (error != null) throw error!;
    return response!;
  }
}

class _PendingChatRepository implements ChatRepository {
  final messages = <String>[];
  final _completer = Completer<String>();

  @override
  Future<String> sendMessage(String message) {
    messages.add(message);
    return _completer.future;
  }

  void complete(String value) => _completer.complete(value);
}
