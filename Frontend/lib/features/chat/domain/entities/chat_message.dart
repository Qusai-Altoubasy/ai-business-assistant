import 'assistant_message_metadata.dart';

enum ChatRole { user, assistant, system }

enum MessageStatus { sending, success, error }

class ChatMessage {
  const ChatMessage({
    required this.id,
    required this.role,
    required this.content,
    required this.createdAt,
    this.status = MessageStatus.success,
    this.error,
    this.metadata,
  });

  final String id;
  final ChatRole role;
  final String content;
  final DateTime createdAt;
  final MessageStatus status;
  final String? error;
  final AssistantMessageMetadata? metadata;
}
