import 'package:dio/dio.dart';

import '../config/app_config.dart';
import 'api_exception.dart';

class ApiClient {
  ApiClient({required this.config, Dio? dio}) : _dio = dio ?? Dio() {
    _dio.options = _dio.options.copyWith(
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(minutes: 2),
      responseType: ResponseType.json,
    );
  }

  final AppConfig config;
  final Dio _dio;

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
