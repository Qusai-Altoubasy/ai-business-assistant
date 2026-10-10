enum AiProvider {
  gemini(label: 'Gemini', apiValue: 'gemini'),
  ollama(label: 'Ollama', apiValue: 'ollama');

  const AiProvider({required this.label, required this.apiValue});

  final String label;
  final String apiValue;
}
