import '../../domain/entities/chat_message.dart';

class ChatState {
  const ChatState({
    this.messages = const [],
    this.isSubmitting = false,
    this.lastFailedPrompt,
  });

  final List<ChatMessage> messages;
  final bool isSubmitting;
  final String? lastFailedPrompt;

  bool get isEmpty => messages.isEmpty;

  ChatState copyWith({
    List<ChatMessage>? messages,
    bool? isSubmitting,
    String? lastFailedPrompt,
    bool clearLastFailedPrompt = false,
  }) {
    return ChatState(
      messages: messages ?? this.messages,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      lastFailedPrompt: clearLastFailedPrompt
          ? null
          : lastFailedPrompt ?? this.lastFailedPrompt,
    );
  }
}
