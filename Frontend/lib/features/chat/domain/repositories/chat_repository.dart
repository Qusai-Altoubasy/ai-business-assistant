import '../entities/business_analysis.dart';

abstract interface class ChatRepository {
  Future<BusinessAnalysis> sendMessage(String message, String conversationId);
}
