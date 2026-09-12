import 'package:flutter/material.dart';

import '../../../../app/theme/app_colors.dart';
import '../../../../app/theme/app_text_styles.dart';
import '../../domain/entities/chat_message.dart';

class AssistantMessageCard extends StatelessWidget {
  const AssistantMessageCard({
    super.key,
    required this.message,
    required this.onRetry,
  });

  final ChatMessage message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final loading = message.status == MessageStatus.sending;
    final failed = message.status == MessageStatus.error;
    return Align(
      alignment: Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 760),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: failed ? AppColors.errorSurface : AppColors.surface,
            borderRadius: const BorderRadius.only(
              topLeft: Radius.circular(4),
              topRight: Radius.circular(14),
              bottomLeft: Radius.circular(14),
              bottomRight: Radius.circular(14),
            ),
            border: Border.all(
              color: failed
                  ? AppColors.error.withValues(alpha: 0.2)
                  : AppColors.outlineSoft.withValues(alpha: 0.45),
            ),
            boxShadow: const [AppColors.softShadow],
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  color: failed ? AppColors.error : AppColors.primary,
                  borderRadius: BorderRadius.circular(7),
                ),
                child: Icon(
                  failed ? Icons.error_outline : Icons.terminal,
                  size: 16,
                  color: Colors.white,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          failed ? 'Request failed' : 'AI Assistant',
                          style: AppTextStyles.headline.copyWith(fontSize: 14),
                        ),
                        if (loading) ...[
                          const SizedBox(width: 10),
                          const SizedBox(
                            width: 12,
                            height: 12,
                            child: CircularProgressIndicator(strokeWidth: 1.8),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 6),
                    SelectableText(message.content, style: AppTextStyles.body),
                    if (failed) ...[
                      const SizedBox(height: 8),
                      TextButton.icon(
                        key: const Key('retry-button'),
                        onPressed: onRetry,
                        icon: const Icon(Icons.refresh, size: 16),
                        label: const Text('Retry'),
                        style: TextButton.styleFrom(
                          foregroundColor: AppColors.error,
                          padding: EdgeInsets.zero,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
