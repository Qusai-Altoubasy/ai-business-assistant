import 'package:ai_business_assistant/core/config/app_config.dart';
import 'package:ai_business_assistant/core/network/api_client.dart';
import 'package:ai_business_assistant/features/chat/data/datasources/chat_remote_data_source.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('URL encodes the message as one endpoint path segment', () async {
    final client = _RecordingApiClient();
    final dataSource = ChatRemoteDataSource(client);

    final response = await dataSource.sendMessage(' Hello AI / sales? ');

    expect(response, 'Backend response');
    expect(client.lastPath, '/hello/ai/Hello%20AI%20%2F%20sales%3F');
  });
}

class _RecordingApiClient extends ApiClient {
  _RecordingApiClient()
    : super(config: const AppConfig(apiBaseUrl: 'http://localhost:8080'));

  String? lastPath;

  @override
  Future<Response<dynamic>> get(String path) async {
    lastPath = path;
    return Response<dynamic>(
      data: 'Backend response',
      requestOptions: RequestOptions(path: path),
    );
  }
}
