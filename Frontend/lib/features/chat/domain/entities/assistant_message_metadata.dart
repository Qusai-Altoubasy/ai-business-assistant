class AssistantMessageMetadata {
  const AssistantMessageMetadata({
    this.sources = const [],
    this.toolsUsed = const [],
    this.latency,
    this.tokenUsage,
    this.model,
    this.citations = const [],
  });

  final List<String> sources;
  final List<String> toolsUsed;
  final Duration? latency;
  final int? tokenUsage;
  final String? model;
  final List<String> citations;
}
