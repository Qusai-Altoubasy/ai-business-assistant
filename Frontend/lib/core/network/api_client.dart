import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import 'api_exception.dart';

class ApiClient {
  ApiClient({required this.config, Dio? dio, http.Client? streamingClient})
    : _dio = dio ?? Dio(),
      _streamingClient = streamingClient ?? http.Client() {
    _dio.options = _dio.options.copyWith(
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(minutes: 2),
      responseType: ResponseType.json,
    );
  }

  final AppConfig config;
  final Dio _dio;
  final http.Client _streamingClient;

  void close() {
    _dio.close();
    _streamingClient.close();
  }

  Stream<List<int>> postStream(
    String path, {
    Object? data,
    Future<void>? abortTrigger,
  }) async* {
    final abort = Completer<void>();
    abortTrigger?.then((_) {
      if (!abort.isCompleted) abort.complete();
    });
    try {
      final request =
          http.AbortableRequest(
              'POST',
              config.validatedBaseUri.resolve(path),
              abortTrigger: abort.future,
            )
            ..headers.addAll({
              'Content-Type': 'application/json',
              'Accept': 'text/event-stream',
            })
            ..body = jsonEncode(data);
      final response = await _streamingClient
          .send(request)
          .timeout(const Duration(seconds: 30));
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw ApiException(
          ApiFailureType.server,
          'The AI service returned an unexpected response.',
          statusCode: response.statusCode,
        );
      }
      if (!(response.headers['content-type'] ?? '').toLowerCase().startsWith(
        'text/event-stream',
      )) {
        throw const ApiException(
          ApiFailureType.malformed,
          'The AI service returned an unexpected streaming response.',
        );
      }
      yield* response.stream.timeout(const Duration(minutes: 2));
    } on FormatException {
      throw const ApiException(
        ApiFailureType.malformed,
        'The AI service address is not configured correctly.',
      );
    } on TimeoutException {
      throw const ApiException(
        ApiFailureType.timeout,
        'The AI service took too long to respond.',
      );
    } on http.ClientException {
      throw const ApiException(
        ApiFailureType.network,
        'The streaming response was interrupted. Please try again.',
      );
    } finally {
      if (!abort.isCompleted) abort.complete();
    }
  }

  Future<Response<dynamic>> post(String path, {Object? data}) async {
    try {
      _dio.options.baseUrl = config.validatedBaseUri.toString();
      return await _dio.post<dynamic>(path, data: data);
    } on FormatException {
      throw const ApiException(
        ApiFailureType.malformed,
        'The AI service address is not configured correctly.',
      );
    } on DioException catch (error) {
      switch (error.type) {
        case DioExceptionType.connectionTimeout:
        case DioExceptionType.sendTimeout:
        case DioExceptionType.receiveTimeout:
        case DioExceptionType.transformTimeout:
          throw const ApiException(
            ApiFailureType.timeout,
            'The AI service took too long to respond.',
          );
        case DioExceptionType.badResponse:
          throw ApiException(
            ApiFailureType.server,
            'The AI service returned an unexpected response.',
            statusCode: error.response?.statusCode,
          );
        case DioExceptionType.connectionError:
          throw const ApiException(
            ApiFailureType.unavailable,
            'The AI service is unavailable. Check that the backend is running.',
          );
        case DioExceptionType.cancel:
        case DioExceptionType.badCertificate:
        case DioExceptionType.unknown:
          throw const ApiException(
            ApiFailureType.network,
            'I could not retrieve a response from the AI service.',
          );
      }
    }
  }
}
