import 'package:flutter/material.dart';

import '../../../../app/theme/app_colors.dart';
import '../../../../app/theme/app_spacing.dart';
import '../../../../app/theme/app_text_styles.dart';

class PromptCard extends StatefulWidget {
  const PromptCard({
    super.key,
    required this.category,
    required this.prompt,
    required this.description,
    required this.onSelected,
  });

  final String category;
  final String prompt;
  final String description;
  final ValueChanged<String> onSelected;

  @override
  State<PromptCard> createState() => _PromptCardState();
}

class _PromptCardState extends State<PromptCard> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 160),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: _hovered
                ? AppColors.surfaceHighest
                : AppColors.outlineSoft.withValues(alpha: 0.35),
          ),
          boxShadow: [
            if (_hovered)
              const BoxShadow(
                color: Color(0x10000000),
                blurRadius: 18,
                offset: Offset(0, 5),
              )
            else
              AppColors.softShadow,
          ],
        ),
        child: InkWell(
          key: Key('prompt-${widget.prompt}'),
          onTap: () => widget.onSelected(widget.prompt),
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.md),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceMid,
                        borderRadius: BorderRadius.circular(5),
                      ),
                      child: Text(
                        widget.category.toUpperCase(),
                        style: AppTextStyles.label.copyWith(
                          color: AppColors.inkMuted,
                        ),
                      ),
                    ),
                    const Spacer(),
                    const Text('MVP endpoint', style: AppTextStyles.mono),
                  ],
                ),
                const SizedBox(height: AppSpacing.sm),
                Text(widget.prompt, style: AppTextStyles.headline),
                const SizedBox(height: AppSpacing.xxs),
                Text(widget.description, style: AppTextStyles.bodySmall),
                const Spacer(),
                const SizedBox(height: AppSpacing.md),
                Row(
                  children: [
                    const Text('Use prompt', style: AppTextStyles.mono),
                    const SizedBox(width: 4),
                    AnimatedSlide(
                      duration: const Duration(milliseconds: 160),
                      offset: _hovered ? const Offset(0.2, 0) : Offset.zero,
                      child: const Icon(
                        Icons.arrow_forward,
                        size: 14,
                        color: AppColors.outline,
                      ),
                    ),
                    const Spacer(),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 7,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceLow,
                        borderRadius: BorderRadius.circular(5),
                      ),
                      child: const Text(
                        'Advanced flow · Soon',
                        style: AppTextStyles.mono,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
