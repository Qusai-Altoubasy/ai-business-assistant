import 'package:flutter/material.dart';

import '../../../../app/theme/app_colors.dart';
import '../../../../app/theme/app_spacing.dart';
import '../../../../app/theme/app_text_styles.dart';

class AppSidebar extends StatelessWidget {
  const AppSidebar({super.key, required this.onNewChat});

  final VoidCallback onNewChat;

  static const _futureModules = <(IconData, String)>[
    (Icons.insights_outlined, 'Analytics'),
    (Icons.dataset_outlined, 'Knowledge Base'),
    (Icons.storage_outlined, 'Data Sources'),
    (Icons.handyman_outlined, 'Tools'),
    (Icons.fact_check_outlined, 'Evaluation'),
    (Icons.settings_outlined, 'Settings'),
  ];

  static const _recent = <String>[
    'Monthly Sales Analysis',
    'Low Stock Products Alert',
    'Return Policy Inquiry',
    'Q3 Revenue Breakdown',
    'Inventory Audit Violations',
  ];

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.surface,
      child: SizedBox(
        width: AppSpacing.sidebarWidth,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(AppSpacing.md),
              child: Row(
                children: [
                  const _AppMark(),
                  const SizedBox(width: AppSpacing.xs),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('AI Assistant', style: AppTextStyles.headline),
                        Text('ENTERPRISE CORE', style: AppTextStyles.mono),
                      ],
                    ),
                  ),
                  _Tag(label: 'v0.1-mvp', background: AppColors.surfaceHigh),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              child: SizedBox(
                width: double.infinity,
                height: 38,
                child: FilledButton(
                  key: const Key('new-chat-button'),
                  onPressed: onNewChat,
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.add, size: 18),
                      SizedBox(width: 6),
                      Expanded(child: Text('New Chat')),
                      Text('⌘N', style: AppTextStyles.mono),
                    ],
                  ),
                ),
              ),
            ),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const _NavItem(
                      icon: Icons.chat_bubble_outline,
                      label: 'Chat',
                      selected: true,
                    ),
                    const _SectionLabel('Architecture modules'),
                    for (final module in _futureModules)
                      _NavItem(
                        icon: module.$1,
                        label: module.$2,
                        trailing: const _Tag(label: 'Soon'),
                        disabled: true,
                      ),
                    const SizedBox(height: AppSpacing.md),
                    const _SectionLabel(
                      'Recent conversations',
                      trailing: Icons.history,
                    ),
                    for (final title in _recent)
                      _NavItem(
                        icon: Icons.article_outlined,
                        label: title,
                        disabled: true,
                        compact: true,
                      ),
                  ],
                ),
              ),
            ),
            Container(
              color: AppColors.surfaceLow,
              padding: const EdgeInsets.all(AppSpacing.sm),
              child: const Column(
                children: [
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 16,
                        backgroundColor: AppColors.primary,
                        child: Icon(
                          Icons.person_outline,
                          color: Colors.white,
                          size: 18,
                        ),
                      ),
                      SizedBox(width: AppSpacing.xs),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Dev / Admin', style: AppTextStyles.body),
                            Text('Workspace: Local', style: AppTextStyles.mono),
                          ],
                        ),
                      ),
                      Icon(Icons.tune, size: 18, color: AppColors.outline),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AppMark extends StatelessWidget {
  const _AppMark();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 34,
      height: 34,
      decoration: BoxDecoration(
        color: AppColors.primary,
        borderRadius: BorderRadius.circular(9),
      ),
      child: const Icon(Icons.terminal, color: Colors.white, size: 19),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel(this.label, {this.trailing});

  final String label;
  final IconData? trailing;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 14, 12, 6),
      child: Row(
        children: [
          Expanded(
            child: Text(label.toUpperCase(), style: AppTextStyles.label),
          ),
          if (trailing != null)
            Icon(trailing, size: 16, color: AppColors.outline),
        ],
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  const _NavItem({
    required this.icon,
    required this.label,
    this.trailing,
    this.selected = false,
    this.disabled = false,
    this.compact = false,
  });

  final IconData icon;
  final String label;
  final Widget? trailing;
  final bool selected;
  final bool disabled;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: disabled ? 0.55 : 1,
      child: Container(
        height: compact ? 34 : 38,
        margin: const EdgeInsets.only(bottom: 2),
        padding: const EdgeInsets.symmetric(horizontal: 12),
        decoration: BoxDecoration(
          color: selected ? AppColors.surfaceHigh : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Icon(icon, size: compact ? 16 : 18, color: AppColors.inkMuted),
            const SizedBox(width: 9),
            Expanded(
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: selected
                    ? AppTextStyles.headline.copyWith(fontSize: 14)
                    : AppTextStyles.bodySmall,
              ),
            ),
            ...switch (trailing) {
              final Widget item => [item],
              null => const <Widget>[],
            },
            if (selected)
              const Icon(Icons.circle, size: 8, color: AppColors.primary),
          ],
        ),
      ),
    );
  }
}

class _Tag extends StatelessWidget {
  const _Tag({required this.label, this.background = AppColors.surfaceMid});

  final String label;
  final Color background;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(label, style: AppTextStyles.mono),
    );
  }
}
