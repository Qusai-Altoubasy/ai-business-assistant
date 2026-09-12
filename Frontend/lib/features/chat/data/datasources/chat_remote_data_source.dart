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

    final encodedMessage = Uri.encodeComponent(normalized);
    final response = await _apiClient.get('/hello/ai/$encodedMessage');
    final content = response.data?.toString().trim() ?? '';
    if (content.isEmpty) {
      throw const ApiException(
        ApiFailureType.emptyResponse,
        'The AI service returned an empty response.',
      );
    }
    return content;
  }
}
