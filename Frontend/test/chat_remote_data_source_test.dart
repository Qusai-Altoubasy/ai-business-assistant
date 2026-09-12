import 'package:ai_business_assistant/core/config/app_config.dart';
import 'package:ai_business_assistant/core/network/api_client.dart';
import 'package:ai_business_assistant/features/chat/data/datasources/chat_remote_data_source.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('posts the trimmed query and reads the JSON response', () async {
    final client = _RecordingApiClient();
    final dataSource = ChatRemoteDataSource(client);

    final response = await dataSource.sendMessage(' Hello AI / sales? ');

    expect(response, 'Backend response');
    expect(client.lastPath, '/api/chat');
    expect(client.lastData, <String, String>{'query': 'Hello AI / sales?'});
  });
}

class _RecordingApiClient extends ApiClient {
  _RecordingApiClient()
    : super(config: const AppConfig(apiBaseUrl: 'http://localhost:8080'));

  String? lastPath;
  Object? lastData;

  @override
  Future<Response<dynamic>> post(String path, {Object? data}) async {
    lastPath = path;
    lastData = data;
    return Response<dynamic>(
      data: <String, dynamic>{'response': 'Backend response'},
      requestOptions: RequestOptions(path: path),
    );
  }
}
