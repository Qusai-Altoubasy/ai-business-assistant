import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../domain/entities/business_analysis.dart';
import '../../domain/entities/chat_message.dart';
import '../../domain/repositories/chat_repository.dart';
import 'chat_state.dart';

class ChatController extends StateNotifier<ChatState> {
  ChatController(this._repository) : super(const ChatState());

  final ChatRepository _repository;
  int _idSeed = 0;
  String _conversationId = _newConversationId();

  Future<bool> sendMessage(String rawMessage) async {
    final prompt = rawMessage.trim();
    if (prompt.isEmpty || state.isSubmitting) return false;

    final now = DateTime.now();
    final conversationId = _conversationId;
    final assistantId = _nextId('assistant');
    state = state.copyWith(
      isSubmitting: true,
      clearLastFailedPrompt: true,
      messages: [
        ...state.messages,
        ChatMessage(
          id: _nextId('user'),
          role: ChatRole.user,
          content: prompt,
          createdAt: now,
        ),
        ChatMessage(
          id: assistantId,
          role: ChatRole.assistant,
          content: 'Analyzing your request…',
          createdAt: now,
          status: MessageStatus.sending,
        ),
      ],
    );

    try {
      final response = await _repository.sendMessage(prompt, conversationId);
      if (conversationId != _conversationId) return true;
      _replaceAssistant(
        assistantId,
        content: response.summary,
        analysis: response,
        status: MessageStatus.success,
      );
      state = state.copyWith(isSubmitting: false, clearLastFailedPrompt: true);
    } on ApiException catch (error) {
      if (conversationId != _conversationId) return true;
      _setFailure(assistantId, prompt, error.userMessage);
    } catch (_) {
      if (conversationId != _conversationId) return true;
      _setFailure(
        assistantId,
        prompt,
        'I could not retrieve a response from the AI service.',
      );
    }
    return true;
  }

  Future<bool> retryLast() async {
    final prompt = state.lastFailedPrompt;
    if (prompt == null || state.isSubmitting) return false;
    final messages = [...state.messages];
    if (messages.isNotEmpty &&
        messages.last.role == ChatRole.assistant &&
        messages.last.status == MessageStatus.error) {
      messages.removeLast();
    }
    if (messages.isNotEmpty &&
        messages.last.role == ChatRole.user &&
        messages.last.content == prompt) {
      messages.removeLast();
    }
    state = state.copyWith(messages: messages, clearLastFailedPrompt: true);
    return sendMessage(prompt);
  }

  void resetChat() {
    _conversationId = _newConversationId();
    state = const ChatState();
  }

  void _setFailure(String id, String prompt, String error) {
    _replaceAssistant(
      id,
      content: error,
      status: MessageStatus.error,
      error: error,
    );
    state = state.copyWith(isSubmitting: false, lastFailedPrompt: prompt);
  }

  void _replaceAssistant(
    String id, {
    required String content,
    required MessageStatus status,
    String? error,
    BusinessAnalysis? analysis,
  }) {
    state = state.copyWith(
      messages: [
        for (final item in state.messages)
          if (item.id == id)
            ChatMessage(
              id: item.id,
              role: item.role,
              content: content,
              createdAt: item.createdAt,
              status: status,
              error: error,
              metadata: item.metadata,
              analysis: analysis,
            )
          else
            item,
      ],
    );
  }

  String _nextId(String prefix) =>
      '$prefix-${DateTime.now().microsecondsSinceEpoch}-${_idSeed++}';

  static String _newConversationId() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes.map((byte) => byte.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
        '${hex.substring(12, 16)}-${hex.substring(16, 20)}-'
        '${hex.substring(20)}';
  }
}
