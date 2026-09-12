import 'package:flutter/material.dart';

import 'app_colors.dart';

abstract final class AppTextStyles {
  static const display = TextStyle(
    color: AppColors.ink,
    fontSize: 32,
    height: 1.25,
    fontWeight: FontWeight.w600,
    letterSpacing: -0.8,
  );
  static const headline = TextStyle(
    color: AppColors.ink,
    fontSize: 16,
    height: 1.5,
    fontWeight: FontWeight.w600,
    letterSpacing: -0.16,
  );
  static const body = TextStyle(
    color: AppColors.ink,
    fontSize: 14,
    height: 1.55,
  );
  static const bodySmall = TextStyle(
    color: AppColors.inkMuted,
    fontSize: 13,
    height: 1.4,
  );
  static const mono = TextStyle(
    color: AppColors.inkMuted,
    fontFamily: 'JetBrains Mono',
    fontFamilyFallback: ['monospace'],
    fontSize: 11,
    height: 1.45,
    fontWeight: FontWeight.w500,
  );
  static const label = TextStyle(
    color: AppColors.outline,
    fontSize: 11,
    height: 1.3,
    fontWeight: FontWeight.w600,
    letterSpacing: 0.55,
  );
}
