import '../entities/business_analysis.dart';
import '../entities/chat_stream_event.dart';

abstract interface class ChatRepository {
  Future<BusinessAnalysis> sendMessage(String message, String conversationId);
  Stream<ChatStreamEvent> streamMessage(
    String message,
    String conversationId, {
    Future<void>? abortTrigger,
  });
}
