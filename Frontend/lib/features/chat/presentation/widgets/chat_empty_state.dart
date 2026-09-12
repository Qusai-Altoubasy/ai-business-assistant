import 'package:flutter/material.dart';

import '../../../../app/theme/app_colors.dart';
import '../../../../app/theme/app_spacing.dart';
import '../../../../app/theme/app_text_styles.dart';
import 'prompt_card.dart';

class ChatEmptyState extends StatelessWidget {
  const ChatEmptyState({super.key, required this.onPromptSelected});

  final ValueChanged<String> onPromptSelected;

  static const _prompts =
      <({String category, String prompt, String description})>[
        (
          category: 'Sales analytics',
          prompt: 'How much did we sell last month?',
          description:
              'Ask the current backend endpoint using this sales question.',
        ),
        (
          category: 'Inventory ops',
          prompt: 'Which products are currently low in stock?',
          description: 'Send an inventory question to the current MVP service.',
        ),
        (
          category: 'Company policy',
          prompt: 'What is our return policy?',
          description:
              'Use the same chat endpoint; document search is planned.',
        ),
        (
          category: 'Compliance',
          prompt: 'Which products violate our inventory policy?',
          description:
              'Use the MVP endpoint; hybrid analysis is not enabled yet.',
        ),
      ];

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const SizedBox(height: AppSpacing.md),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(20),
            boxShadow: const [AppColors.softShadow],
          ),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.circle, size: 8, color: AppColors.success),
              SizedBox(width: 7),
              Text('MVP chat runtime', style: AppTextStyles.mono),
              SizedBox(width: 8),
              Icon(Icons.circle, size: 4, color: AppColors.outlineSoft),
              SizedBox(width: 8),
              Text('POST /api/chat', style: AppTextStyles.mono),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        const Text(
          'How can I help with your business today?',
          textAlign: TextAlign.center,
          style: AppTextStyles.display,
        ),
        const SizedBox(height: AppSpacing.sm),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 680),
          child: const Text(
            'Ask a question and the assistant will send it to the current backend. '
            'Data tools, RAG, citations, and multi-turn memory are planned extensions.',
            textAlign: TextAlign.center,
            style: AppTextStyles.body,
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        const Wrap(
          alignment: WrapAlignment.center,
          spacing: 8,
          runSpacing: 8,
          children: [
            _Capability(
              icon: Icons.storage_outlined,
              label: 'PostgreSQL · Planned',
            ),
            _Capability(
              icon: Icons.hub_outlined,
              label: 'Semantic search · Planned',
            ),
            _Capability(icon: Icons.memory_outlined, label: 'Memory · Planned'),
            _Capability(
              icon: Icons.verified_outlined,
              label: 'Citations · Planned',
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.xl),
        LayoutBuilder(
          builder: (context, constraints) {
            final columns = constraints.maxWidth >= 720 ? 2 : 1;
            const gap = AppSpacing.sm;
            final width = columns == 2
                ? (constraints.maxWidth - gap) / 2
                : constraints.maxWidth;
            return Wrap(
              spacing: gap,
              runSpacing: gap,
              children: [
                for (final item in _prompts)
                  SizedBox(
                    width: width,
                    height: 184,
                    child: PromptCard(
                      category: item.category,
                      prompt: item.prompt,
                      description: item.description,
                      onSelected: onPromptSelected,
                    ),
                  ),
              ],
            );
          },
        ),
        const SizedBox(height: AppSpacing.lg),
      ],
    );
  }
}

class _Capability extends StatelessWidget {
  const _Capability({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(20),
        boxShadow: const [AppColors.softShadow],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 15, color: AppColors.inkMuted),
          const SizedBox(width: 6),
          Text(label, style: AppTextStyles.mono),
        ],
      ),
    );
  }
}
