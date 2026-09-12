import 'package:flutter/material.dart';

import '../features/chat/presentation/pages/chat_page.dart';
import 'theme/app_theme.dart';

class BusinessAssistantApp extends StatelessWidget {
  const BusinessAssistantApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AI Business Assistant',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      home: const ChatPage(),
    );
  }
}
