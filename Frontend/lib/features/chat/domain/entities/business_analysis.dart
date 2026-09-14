class BusinessAnalysis {
  const BusinessAnalysis({
    required this.summary,
    this.insights = const [],
    this.recommendations = const [],
  });

  final String summary;
  final List<String> insights;
  final List<String> recommendations;
}
