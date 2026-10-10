import 'package:flutter/material.dart';

import '../../../../app/theme/app_colors.dart';
import '../../../../app/theme/app_text_styles.dart';
import '../../domain/entities/ai_provider.dart';

class AiProviderSelector extends StatelessWidget {
  const AiProviderSelector({
    super.key,
    required this.provider,
    required this.isPinned,
    required this.isSubmitting,
    required this.onSelected,
  });

  final AiProvider provider;
  final bool isPinned;
  final bool isSubmitting;
  final ValueChanged<AiProvider> onSelected;

  @override
  Widget build(BuildContext context) {
    final enabled = !isPinned && !isSubmitting;
    return PopupMenuButton<AiProvider>(
      key: const Key('ai-provider-selector'),
      enabled: enabled,
      tooltip: isSubmitting
          ? 'Wait for the response before changing AI provider.'
          : isPinned
          ? 'Start a new chat to change AI provider.'
          : 'Choose AI provider',
      onSelected: onSelected,
      position: PopupMenuPosition.under,
      color: AppColors.surface,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      itemBuilder: (_) => [
        for (final option in AiProvider.values)
          PopupMenuItem<AiProvider>(
            key: Key('ai-provider-option-${option.apiValue}'),
            value: option,
            child: Semantics(
              selected: option == provider,
              child: Row(
                children: [
                  Expanded(
                    child: Text(option.label, style: AppTextStyles.body),
                  ),
                  if (option == provider)
                    const Icon(Icons.check, size: 18, color: AppColors.primary)
                  else
                    const SizedBox(width: 18),
                ],
              ),
            ),
          ),
      ],
      child: Semantics(
        label: 'AI provider',
        value: provider.label,
        enabled: enabled,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
          decoration: BoxDecoration(
            color: AppColors.surfaceLow,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                Icons.auto_awesome_outlined,
                size: 16,
                color: AppColors.inkMuted,
              ),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  provider.label,
                  style: AppTextStyles.bodySmall,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 6),
              Icon(
                isPinned ? Icons.lock_outline : Icons.expand_more,
                size: 16,
                color: AppColors.inkMuted,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
