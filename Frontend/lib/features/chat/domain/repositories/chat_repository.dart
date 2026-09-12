abstract interface class ChatRepository {
  Future<String> sendMessage(String message);
}
