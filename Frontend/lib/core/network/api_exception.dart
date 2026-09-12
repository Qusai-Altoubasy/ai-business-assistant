enum ApiFailureType {
  unavailable,
  timeout,
  server,
  emptyResponse,
  malformed,
  network,
}

class ApiException implements Exception {
  const ApiException(this.type, this.userMessage, {this.statusCode});

  final ApiFailureType type;
  final String userMessage;
  final int? statusCode;

  @override
  String toString() => 'ApiException($type, statusCode: $statusCode)';
}
