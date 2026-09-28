enum StreamEventType { chunk, toolStarted, toolCompleted, done, error }

class ChatStreamEvent {
  const ChatStreamEvent(this.type, this.content);

  final StreamEventType type;
  final String? content;

  factory ChatStreamEvent.fromJson(Object? json) {
    if (json is! Map<String, dynamic>) {
      throw const FormatException('Invalid stream event');
    }
    final type = switch (json['type']) {
      'CHUNK' => StreamEventType.chunk,
      'TOOL_STARTED' => StreamEventType.toolStarted,
      'TOOL_COMPLETED' => StreamEventType.toolCompleted,
      'DONE' => StreamEventType.done,
      'ERROR' => StreamEventType.error,
      _ => throw const FormatException('Unknown stream event'),
    };
    final content = json['content'];
    if ((content != null && content is! String) ||
        (type != StreamEventType.done && content is! String)) {
      throw const FormatException('Invalid stream content');
    }
    return ChatStreamEvent(type, content as String?);
  }
}
