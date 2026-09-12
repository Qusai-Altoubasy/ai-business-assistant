import '../../../../core/network/api_client.dart';
import '../../../../core/network/api_exception.dart';

class ChatRemoteDataSource {
  const ChatRemoteDataSource(this._apiClient);

  final ApiClient _apiClient;

  Future<String> sendMessage(String message) async {
    final normalized = message.trim();
    if (normalized.isEmpty) {
      throw const ApiException(
        ApiFailureType.malformed,
        'Enter a message before sending.',
      );
    }

    final response = await _apiClient.post(
      '/api/chat',
      data: <String, String>{'query': normalized},
    );
    final data = response.data;
    if (data is! Map<String, dynamic> || data['response'] is! String) {
      throw const ApiException(
        ApiFailureType.malformed,
        'The AI service returned an unexpected response.',
      );
    }

    final content = (data['response'] as String).trim();
    if (content.isEmpty) {
      throw const ApiException(
        ApiFailureType.emptyResponse,
        'The AI service returned an empty response.',
      );
    }
    return content;
  }
}
