import '../../../../core/network/api_exception.dart';
import '../../domain/entities/business_analysis.dart';

class BusinessAnalysisResponse {
  const BusinessAnalysisResponse({
    required this.summary,
    required this.insights,
    required this.recommendations,
  });

  final String summary;
  final List<String> insights;
  final List<String> recommendations;

  factory BusinessAnalysisResponse.fromJson(Object? json) {
    if (json is! Map<String, dynamic> || json['summary'] is! String) {
      throw _malformed;
    }
    final summary = (json['summary'] as String).trim();
    final insights = _readList(json['insights']);
    final recommendations = _readList(json['recommendations']);
    if (summary.isEmpty) {
      throw const ApiException(
        ApiFailureType.emptyResponse,
        'The AI service returned an empty response.',
      );
    }
    return BusinessAnalysisResponse(
      summary: summary,
      insights: insights,
      recommendations: recommendations,
    );
  }

  BusinessAnalysis toDomain() => BusinessAnalysis(
    summary: summary,
    insights: insights,
    recommendations: recommendations,
  );

  static List<String> _readList(Object? value) {
    if (value == null) return const [];
    if (value is! List || value.any((item) => item is! String)) {
      throw _malformed;
    }
    return List<String>.unmodifiable(
      value
          .cast<String>()
          .map((item) => item.trim())
          .where((item) => item.isNotEmpty),
    );
  }

  static const _malformed = ApiException(
    ApiFailureType.malformed,
    'The AI service returned an unexpected response.',
  );
}
