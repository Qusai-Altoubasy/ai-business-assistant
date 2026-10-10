import 'dart:async';
import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_exception.dart';
import '../../domain/entities/ai_provider.dart';
import '../../domain/entities/business_analysis.dart';
import '../../domain/entities/chat_message.dart';
import '../../domain/entities/chat_stream_event.dart';
import '../../domain/repositories/chat_repository.dart';
import 'chat_state.dart';

class ChatController extends StateNotifier<ChatState> {
  ChatController(this._repository) : super(const ChatState());

  final ChatRepository _repository;
  int _idSeed = 0;
  String _conversationId = _newConversationId();
  Completer<void>? _activeAbort;

  void setMode(ChatMode mode) {
    if (!mounted || state.isSubmitting) return;
    state = state.copyWith(mode: mode);
  }

  void setProvider(AiProvider provider) {
    if (!mounted || !state.canChangeProvider) return;
    state = state.copyWith(provider: provider);
  }

  @override
  void dispose() {
    _cancelStream();
    super.dispose();
  }

  void _cancelStream() {
    final abort = _activeAbort;
    _activeAbort = null;
    if (abort != null && !abort.isCompleted) abort.complete();
  }

  Future<bool> sendMessage(String rawMessage) async {
    final prompt = rawMessage.trim();
    if (!mounted || prompt.isEmpty || state.isSubmitting) return false;

    final now = DateTime.now();
    final conversationId = _conversationId;
    final provider = state.provider;
    final assistantId = _nextId('assistant');
    final streaming = state.mode == ChatMode.streaming;
    final responseText = StringBuffer();
    final abort = streaming ? Completer<void>() : null;
    _activeAbort = abort;
    state = state.copyWith(
      isSubmitting: true,
      // The backend pins the conversation before generating a response, so
      // failed requests and retries must retain this provider too.
      isProviderPinned: true,
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
          content: streaming ? '' : 'Analyzing your request…',
          progressLabel: streaming ? 'Waiting for response…' : null,
          createdAt: now,
          status: MessageStatus.sending,
        ),
      ],
    );

    try {
      if (streaming) {
        await for (final event in _repository.streamMessage(
          prompt,
          conversationId,
          provider: provider,
          abortTrigger: abort!.future,
        )) {
          if (!mounted || conversationId != _conversationId) return true;
          switch (event.type) {
            case StreamEventType.chunk:
              responseText.write(event.content!);
              _replaceAssistant(
                assistantId,
                content: responseText.toString(),
                status: MessageStatus.sending,
                progressLabel: 'Receiving response…',
              );
            case StreamEventType.toolStarted:
              _replaceAssistant(
                assistantId,
                content: responseText.toString(),
                status: MessageStatus.sending,
                progressLabel: _toolStatus(event.content!),
              );
            case StreamEventType.toolCompleted:
              _replaceAssistant(
                assistantId,
                content: responseText.toString(),
                status: MessageStatus.sending,
                progressLabel: 'Business data retrieved…',
              );
            case StreamEventType.done:
              _replaceAssistant(
                assistantId,
                content: responseText.toString(),
                status: MessageStatus.success,
              );
              state = state.copyWith(
                isSubmitting: false,
                clearLastFailedPrompt: true,
              );
              return true;
            case StreamEventType.error:
              _setFailure(
                assistantId,
                prompt,
                event.content!,
                partialContent: responseText.toString(),
              );
              return true;
          }
        }
        throw const ApiException(
          ApiFailureType.network,
          'The response was interrupted before completion. Please try again.',
        );
      }
      final response = await _repository.sendMessage(
        prompt,
        conversationId,
        provider: provider,
      );
      if (!mounted || conversationId != _conversationId) return true;
      _replaceAssistant(
        assistantId,
        content: response.summary,
        analysis: response,
        status: MessageStatus.success,
      );
      state = state.copyWith(isSubmitting: false, clearLastFailedPrompt: true);
    } on ApiException catch (error) {
      if (!mounted || conversationId != _conversationId) return true;
      _setFailure(
        assistantId,
        prompt,
        error.userMessage,
        partialContent: streaming ? responseText.toString() : null,
      );
    } catch (_) {
      if (!mounted || conversationId != _conversationId) return true;
      _setFailure(
        assistantId,
        prompt,
        'I could not retrieve a response from the AI service.',
        partialContent: streaming ? responseText.toString() : null,
      );
    } finally {
      if (abort != null && !abort.isCompleted) abort.complete();
      if (identical(_activeAbort, abort)) _activeAbort = null;
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
    final mode = state.mode;
    final provider = state.provider;
    _cancelStream();
    _conversationId = _newConversationId();
    state = ChatState(mode: mode, provider: provider);
  }

  void _setFailure(
    String id,
    String prompt,
    String error, {
    String? partialContent,
  }) {
    _replaceAssistant(
      id,
      content: partialContent ?? error,
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
    String? progressLabel,
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
              progressLabel: progressLabel,
            )
          else
            item,
      ],
    );
  }

  static String _toolStatus(String tool) => switch (tool) {
    'getLowStockProducts' || 'getProductStock' => 'Checking inventory…',
    'getCustomerStatistics' => 'Checking customer data…',
    'getSales' => 'Checking sales data…',
    'getCurrentDate' => 'Checking the current date…',
    _ => 'Checking business data…',
  };

  String _nextId(String prefix) =>
      '$prefix-${DateTime.now().microsecondsSinceEpoch}-${_idSeed++}';

  static String _newConversationId() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes
        .map((byte) => byte.toRadixString(16).padLeft(2, '0'))
        .join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
        '${hex.substring(12, 16)}-${hex.substring(16, 20)}-'
        '${hex.substring(20)}';
  }
}
