import '../../domain/entities/ai_provider.dart';
import '../../domain/entities/business_analysis.dart';
import '../../domain/entities/chat_stream_event.dart';
import '../../domain/repositories/chat_repository.dart';
import '../datasources/chat_remote_data_source.dart';

class ChatRepositoryImpl implements ChatRepository {
  const ChatRepositoryImpl(this._remoteDataSource);

  final ChatRemoteDataSource _remoteDataSource;

  @override
  Stream<ChatStreamEvent> streamMessage(
    String message,
    String conversationId, {
    required AiProvider provider,
    Future<void>? abortTrigger,
  }) => _remoteDataSource.streamMessage(
    message,
    conversationId,
    provider: provider,
    abortTrigger: abortTrigger,
  );

  @override
  Future<BusinessAnalysis> sendMessage(
    String message,
    String conversationId, {
    required AiProvider provider,
  }) async => (await _remoteDataSource.sendMessage(
    message,
    conversationId,
    provider: provider,
  )).toDomain();
}
