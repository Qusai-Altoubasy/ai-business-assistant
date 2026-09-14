import '../../domain/entities/business_analysis.dart';
import '../../domain/repositories/chat_repository.dart';
import '../datasources/chat_remote_data_source.dart';

class ChatRepositoryImpl implements ChatRepository {
  const ChatRepositoryImpl(this._remoteDataSource);

  final ChatRemoteDataSource _remoteDataSource;

  @override
  Future<BusinessAnalysis> sendMessage(String message) async =>
      (await _remoteDataSource.sendMessage(message)).toDomain();
}
