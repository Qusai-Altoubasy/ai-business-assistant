import 'package:flutter/material.dart';

import '../../../../app/theme/app_spacing.dart';
import '../../domain/entities/chat_message.dart';
import 'assistant_message_card.dart';
import 'user_message_bubble.dart';

class ChatMessageList extends StatelessWidget {
  const ChatMessageList({
    super.key,
    required this.messages,
    required this.scrollController,
    required this.onRetry,
  });

  final List<ChatMessage> messages;
  final ScrollController scrollController;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      key: const Key('chat-message-list'),
      controller: scrollController,
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.lg),
      itemCount: messages.length,
      separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.md),
      itemBuilder: (context, index) {
        final message = messages[index];
        return message.role == ChatRole.user
            ? UserMessageBubble(message: message)
            : AssistantMessageCard(message: message, onRetry: onRetry);
      },
    );
  }
}
