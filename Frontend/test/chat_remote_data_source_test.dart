import 'package:ai_business_assistant/core/config/app_config.dart';
import 'package:ai_business_assistant/core/network/api_client.dart';
import 'package:ai_business_assistant/core/network/api_exception.dart';
import 'package:ai_business_assistant/features/chat/data/datasources/chat_remote_data_source.dart';
import 'package:ai_business_assistant/features/chat/data/repositories/chat_repository_impl.dart';
import 'package:ai_business_assistant/features/chat/presentation/controllers/chat_controller.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/chat_message.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'posts the trimmed query and preserves structured response fields',
    () async {
      final client = _RecordingApiClient({
        'summary': ' Stock needs attention. ',
        'insights': [' Low stock ', '  '],
        'recommendations': [' Restock soon. '],
      });
      final response = await ChatRemoteDataSource(
        client,
      ).sendMessage(' Stock? ', 'conversation-123');

      expect(response.summary, 'Stock needs attention.');
      expect(response.insights, ['Low stock']);
      expect(response.recommendations, ['Restock soon.']);
      expect(client.lastPath, '/api/chat');
      expect(client.lastData, <String, String>{
        'conversationId': 'conversation-123',
        'query': 'Stock?',
      });
      final domain = response.toDomain();
      expect(domain.summary, response.summary);
      expect(domain.insights, response.insights);
      expect(domain.recommendations, response.recommendations);
    },
  );

  for (final lists in [
    <String, Object?>{},
    {'insights': null, 'recommendations': null},
    {'insights': <String>[], 'recommendations': <String>[]},
  ]) {
    test('handles absent or empty lists: $lists', () async {
      final response = await ChatRemoteDataSource(
        _RecordingApiClient({'summary': 'No issues.', ...lists}),
      ).sendMessage('Status?', 'conversation-123');
      expect(response.insights, isEmpty);
      expect(response.recommendations, isEmpty);
    });
  }

  final malformedPayloads = <Object?>[
    null,
    'not an object',
    [],
    {'response': 'Legacy response'},
    {'summary': 42},
    {'summary': null},
    {'summary': 'Status', 'insights': 'wrong type'},
    {
      'summary': 'Status',
      'recommendations': [42],
    },
    {
      'summary': 'Status',
      'insights': ['Valid', null],
    },
  ];
  for (var i = 0; i < malformedPayloads.length; i++) {
    test('malformed payload $i becomes a normalized error', () async {
      final source = ChatRemoteDataSource(
        _RecordingApiClient(malformedPayloads[i]),
      );
      await expectLater(
        source.sendMessage('Status?', 'conversation-123'),
        throwsA(
          isA<ApiException>().having(
            (e) => e.type,
            'type',
            ApiFailureType.malformed,
          ),
        ),
      );
    });
  }

  test('blank summary becomes an empty-response error', () async {
    final source = ChatRemoteDataSource(
      _RecordingApiClient({'summary': ' \n '}),
    );
    await expectLater(
      source.sendMessage('Status?', 'conversation-123'),
      throwsA(
        isA<ApiException>().having(
          (e) => e.type,
          'type',
          ApiFailureType.emptyResponse,
        ),
      ),
    );
  });

  test('empty query never reaches the API', () async {
    final client = _RecordingApiClient(null);
    await expectLater(
      ChatRemoteDataSource(client).sendMessage('  ', 'conversation-123'),
      throwsA(isA<ApiException>()),
    );
    expect(client.lastPath, isNull);
  });

  for (final failure in [
    (DioExceptionType.connectionError, ApiFailureType.unavailable),
    (DioExceptionType.receiveTimeout, ApiFailureType.timeout),
    (DioExceptionType.badResponse, ApiFailureType.server),
    (DioExceptionType.unknown, ApiFailureType.network),
  ]) {
    test('Dio ${failure.$1} remains normalized', () async {
      final dio = Dio();
      addTearDown(dio.close);
      dio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (options, handler) {
            handler.reject(
              DioException(
                requestOptions: options,
                type: failure.$1,
                response: failure.$1 == DioExceptionType.badResponse
                    ? Response(requestOptions: options, statusCode: 500)
                    : null,
              ),
            );
          },
        ),
      );
      final source = ChatRemoteDataSource(
        ApiClient(
          config: const AppConfig(apiBaseUrl: 'http://localhost:8080'),
          dio: dio,
        ),
      );
      await expectLater(
        source.sendMessage('Stock?', 'conversation-123'),
        throwsA(
          isA<ApiException>()
              .having((e) => e.type, 'type', failure.$2)
              .having(
                (e) => e.statusCode,
                'statusCode',
                failure.$1 == DioExceptionType.badResponse ? 500 : null,
              ),
        ),
      );
    });
  }

  test(
    'malformed response can be retried through the full chat flow',
    () async {
      final client = _RecordingApiClient({
        'summary': 'Status',
        'insights': [42],
      });
      final controller = ChatController(
        ChatRepositoryImpl(ChatRemoteDataSource(client)),
      );
      addTearDown(controller.dispose);
      await controller.sendMessage('Status?');
      expect(controller.state.messages.last.status, MessageStatus.error);
      expect(
        controller.state.messages.last.error,
        'The AI service returned an unexpected response.',
      );
      expect(controller.state.lastFailedPrompt, 'Status?');
      client.payload = {
        'summary': 'All good.',
        'insights': ['Stock is sufficient.'],
      };
      await controller.retryLast();
      expect(controller.state.messages, hasLength(2));
      expect(controller.state.messages.last.analysis!.summary, 'All good.');
      expect(controller.state.messages.last.analysis!.insights, [
        'Stock is sufficient.',
      ]);
      expect(controller.state.messages.last.status, MessageStatus.success);
      expect(controller.state.lastFailedPrompt, isNull);
    },
  );
}

class _RecordingApiClient extends ApiClient {
  _RecordingApiClient(this.payload)
    : super(config: const AppConfig(apiBaseUrl: 'http://localhost:8080'));

  Object? payload;
  String? lastPath;
  Object? lastData;

  @override
  Future<Response<dynamic>> post(String path, {Object? data}) async {
    lastPath = path;
    lastData = data;
    return Response<dynamic>(
      data: payload,
      requestOptions: RequestOptions(path: path),
    );
  }
}
