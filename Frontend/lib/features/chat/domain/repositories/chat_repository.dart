import '../entities/ai_provider.dart';
import '../entities/business_analysis.dart';
import '../entities/chat_stream_event.dart';

abstract interface class ChatRepository {
  Future<BusinessAnalysis> sendMessage(
    String message,
    String conversationId, {
    required AiProvider provider,
  });
  Stream<ChatStreamEvent> streamMessage(
    String message,
    String conversationId, {
    required AiProvider provider,
    Future<void>? abortTrigger,
  });
}
