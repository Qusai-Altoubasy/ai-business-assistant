import 'package:flutter/material.dart';

import '../../../../app/theme/app_colors.dart';
import '../../../../app/theme/app_spacing.dart';
import '../../../../app/theme/app_text_styles.dart';

class ChatHeader extends StatelessWidget {
  const ChatHeader({super.key, this.onOpenNavigation});

  final VoidCallback? onOpenNavigation;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.background.withValues(alpha: 0.96),
      elevation: 1,
      shadowColor: Colors.black.withValues(alpha: 0.08),
      child: SizedBox(
        height: 72,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final compact = constraints.maxWidth < 760;
              return Row(
                children: [
                  if (onOpenNavigation != null) ...[
                    IconButton(
                      tooltip: 'Open navigation',
                      onPressed: onOpenNavigation,
                      icon: const Icon(Icons.menu),
                    ),
                    const SizedBox(width: AppSpacing.xs),
                  ],
                  Expanded(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Wrap(
                          spacing: 10,
                          runSpacing: 4,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            const Text(
                              'AI Business Assistant',
                              style: AppTextStyles.headline,
                            ),
                            if (!compact)
                              const _StatusPill(
                                label: 'Backend target configured',
                              ),
                          ],
                        ),
                        if (!compact)
                          const Text(
                            'Ask the current AI endpoint about your business.',
                            style: AppTextStyles.bodySmall,
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  const CircleAvatar(
                    radius: 16,
                    backgroundColor: AppColors.primary,
                    child: Icon(
                      Icons.person_outline,
                      color: Colors.white,
                      size: 18,
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(20),
        boxShadow: const [AppColors.softShadow],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.circle, size: 8, color: AppColors.success),
          const SizedBox(width: 6),
          Text(label, style: AppTextStyles.mono),
        ],
      ),
    );
  }
}
