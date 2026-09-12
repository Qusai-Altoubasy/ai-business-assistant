import 'package:flutter/material.dart';

abstract final class AppColors {
  static const background = Color(0xFFF8F9FF);
  static const surface = Color(0xFFFFFFFF);
  static const surfaceLow = Color(0xFFEFF4FF);
  static const surfaceMid = Color(0xFFE5EEFF);
  static const surfaceHigh = Color(0xFFDCE9FF);
  static const surfaceHighest = Color(0xFFD3E4FE);
  static const ink = Color(0xFF0B1C30);
  static const inkMuted = Color(0xFF45464D);
  static const outline = Color(0xFF76777D);
  static const outlineSoft = Color(0xFFC6C6CD);
  static const primary = Color(0xFF111827);
  static const success = Color(0xFF16804A);
  static const error = Color(0xFFBA1A1A);
  static const errorSurface = Color(0xFFFFF1F0);

  static const softShadow = BoxShadow(
    color: Color(0x0A000000),
    blurRadius: 12,
    offset: Offset(0, 2),
  );
}
