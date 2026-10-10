import 'dart:convert';

import '../../../../core/network/api_client.dart';
import '../../../../core/network/api_exception.dart';
import '../../domain/entities/ai_provider.dart';
import '../../domain/entities/chat_stream_event.dart';
import '../models/business_analysis_response.dart';

class ChatRemoteDataSource {
  const ChatRemoteDataSource(this._apiClient);

  final ApiClient _apiClient;

  Stream<ChatStreamEvent> streamMessage(
    String message,
    String conversationId, {
    required AiProvider provider,
    Future<void>? abortTrigger,
  }) async* {
    final normalized = message.trim();
    if (normalized.isEmpty) {
      throw const ApiException(
        ApiFailureType.malformed,
        'Enter a message before sending.',
      );
    }
    final lines = _apiClient
        .postStream(
          '/api/chat/stream',
          data: {
            'conversationId': conversationId,
            'query': normalized,
            'provider': provider.apiValue,
          },
          abortTrigger: abortTrigger,
        )
        .transform(utf8.decoder)
        .transform(const LineSplitter());
    final data = <String>[];
    try {
      await for (final line in lines) {
        if (line.isEmpty) {
          if (data.isNotEmpty) {
            final event = ChatStreamEvent.fromJson(jsonDecode(data.join('\n')));
            data.clear();
            yield event;
            if (event.type == StreamEventType.done ||
                event.type == StreamEventType.error) {
              return;
            }
          }
        } else if (line.startsWith('data:')) {
          var value = line.substring(5);
          if (value.startsWith(' ')) value = value.substring(1);
          data.add(value);
        }
        // Other SSE fields and keep-alive comments carry no assistant content.
      }
    } on FormatException {
      throw const ApiException(
        ApiFailureType.malformed,
        'The AI service returned an invalid streaming response.',
      );
    }
  }

  Future<BusinessAnalysisResponse> sendMessage(
    String message,
    String conversationId, {
    required AiProvider provider,
  }) async {
    final normalized = message.trim();
    if (normalized.isEmpty) {
      throw const ApiException(
        ApiFailureType.malformed,
        'Enter a message before sending.',
      );
    }

    final response = await _apiClient.post(
      '/api/chat',
      data: <String, String>{
        'conversationId': conversationId,
        'query': normalized,
        'provider': provider.apiValue,
      },
    );
    return BusinessAnalysisResponse.fromJson(response.data);
  }
}
