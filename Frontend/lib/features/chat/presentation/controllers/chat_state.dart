import '../../domain/entities/chat_message.dart';

enum ChatMode { structured, streaming }

class ChatState {
  const ChatState({
    this.messages = const [],
    this.isSubmitting = false,
    this.lastFailedPrompt,
    this.mode = ChatMode.structured,
  });

  final List<ChatMessage> messages;
  final bool isSubmitting;
  final String? lastFailedPrompt;
  final ChatMode mode;

  bool get isEmpty => messages.isEmpty;

  ChatState copyWith({
    List<ChatMessage>? messages,
    bool? isSubmitting,
    String? lastFailedPrompt,
    bool clearLastFailedPrompt = false,
    ChatMode? mode,
  }) {
    return ChatState(
      mode: mode ?? this.mode,
      messages: messages ?? this.messages,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      lastFailedPrompt: clearLastFailedPrompt
          ? null
          : lastFailedPrompt ?? this.lastFailedPrompt,
    );
  }
}
