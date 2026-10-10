import '../../domain/entities/ai_provider.dart';
import '../../domain/entities/chat_message.dart';

enum ChatMode { structured, streaming }

class ChatState {
  const ChatState({
    this.messages = const [],
    this.isSubmitting = false,
    this.lastFailedPrompt,
    this.mode = ChatMode.structured,
    this.provider = AiProvider.gemini,
    this.isProviderPinned = false,
  });

  final List<ChatMessage> messages;
  final bool isSubmitting;
  final String? lastFailedPrompt;
  final ChatMode mode;
  final AiProvider provider;
  final bool isProviderPinned;

  bool get isEmpty => messages.isEmpty;
  bool get canChangeProvider => !isSubmitting && !isProviderPinned;

  ChatState copyWith({
    List<ChatMessage>? messages,
    bool? isSubmitting,
    String? lastFailedPrompt,
    bool clearLastFailedPrompt = false,
    ChatMode? mode,
    AiProvider? provider,
    bool? isProviderPinned,
  }) {
    return ChatState(
      mode: mode ?? this.mode,
      provider: provider ?? this.provider,
      isProviderPinned: isProviderPinned ?? this.isProviderPinned,
      messages: messages ?? this.messages,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      lastFailedPrompt: clearLastFailedPrompt
          ? null
          : lastFailedPrompt ?? this.lastFailedPrompt,
    );
  }
}
