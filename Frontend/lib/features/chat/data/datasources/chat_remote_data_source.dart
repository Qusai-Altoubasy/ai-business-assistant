import '../../../../core/network/api_client.dart';
import '../../../../core/network/api_exception.dart';
import '../models/business_analysis_response.dart';

class ChatRemoteDataSource {
  const ChatRemoteDataSource(this._apiClient);

  final ApiClient _apiClient;

  Future<BusinessAnalysisResponse> sendMessage(String message) async {
    final normalized = message.trim();
    if (normalized.isEmpty) {
      throw const ApiException(
        ApiFailureType.malformed,
        'Enter a message before sending.',
      );
    }

    final response = await _apiClient.post(
      '/api/chat/business-analysis',
      data: <String, String>{'query': normalized},
    );
    return BusinessAnalysisResponse.fromJson(response.data);
  }
}
