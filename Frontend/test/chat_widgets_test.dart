import 'dart:async';

import 'package:ai_business_assistant/app/theme/app_theme.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/business_analysis.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/chat_message.dart';
import 'package:ai_business_assistant/features/chat/domain/repositories/chat_repository.dart';
import 'package:ai_business_assistant/features/chat/presentation/controllers/chat_providers.dart';
import 'package:ai_business_assistant/features/chat/presentation/pages/chat_page.dart';
import 'package:ai_business_assistant/features/chat/presentation/widgets/assistant_message_card.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  for (final insights in [
    <String>[],
    ['Low stock'],
  ]) {
    for (final recommendations in [
      <String>[],
      ['Restock soon'],
    ]) {
      testWidgets(
        'renders only populated analysis sections: $insights / $recommendations',
        (tester) async {
          await tester.pumpWidget(
            MaterialApp(
              home: Scaffold(
                body: AssistantMessageCard(
                  message: ChatMessage(
                    id: 'assistant',
                    role: ChatRole.assistant,
                    content: 'Inventory summary',
                    createdAt: DateTime(2026),
                    analysis: BusinessAnalysis(
                      summary: 'Inventory summary',
                      insights: insights,
                      recommendations: recommendations,
                    ),
                  ),
                  onRetry: () {},
                ),
              ),
            ),
          );
          expect(find.text('Summary'), findsOneWidget);
          expect(find.text('Inventory summary'), findsOneWidget);
          expect(
            find.text('Insights'),
            insights.isEmpty ? findsNothing : findsOneWidget,
          );
          expect(
            find.text('Recommendations'),
            recommendations.isEmpty ? findsNothing : findsOneWidget,
          );
          for (final text in [...insights, ...recommendations]) {
            expect(find.text(text), findsOneWidget);
          }
          expect(
            find.text('•  '),
            findsNWidgets(insights.length + recommendations.length),
          );
          expect(tester.takeException(), isNull);
        },
      );
    }
  }

  testWidgets('loading and inline error retain their existing presentation', (
    tester,
  ) async {
    var retries = 0;
    Future<void> show(MessageStatus status, String content) =>
        tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: AssistantMessageCard(
                message: ChatMessage(
                  id: 'assistant',
                  role: ChatRole.assistant,
                  content: content,
                  createdAt: DateTime(2026),
                  status: status,
                ),
                onRetry: () => retries++,
              ),
            ),
          ),
        );
    await show(MessageStatus.sending, 'Analyzing your request…');
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.text('Analyzing your request…'), findsOneWidget);
    expect(find.text('Summary'), findsNothing);
    await show(MessageStatus.error, 'The AI service is unavailable.');
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('The AI service is unavailable.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('retry-button')));
    expect(retries, 1);
  });

  testWidgets('suggested prompt uses the composer and existing chat flow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1400, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _PendingRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [chatRepositoryProvider.overrideWithValue(repository)],
        child: MaterialApp(
          theme: AppTheme.light,
          // Ahem's wide test glyphs overflow the existing fixed prompt cards.
          // This test exercises interactions rather than font layout.
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(0.85)),
            child: child!,
          ),
          home: const ChatPage(),
        ),
      ),
    );
    const prompt = 'Which products are currently low in stock?';
    await tester.tap(find.text(prompt));
    await tester.pump();
    final input = tester.widget<TextField>(find.byKey(const Key('chat-input')));
    expect(input.controller!.text, prompt);
    expect(repository.messages, isEmpty);

    await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
    await tester.pump();
    expect(repository.messages, isEmpty);
    // Platform text input delivers the newline after the unhandled shortcut.
    tester.testTextInput.updateEditingValue(
      const TextEditingValue(
        text: '$prompt\n',
        selection: TextSelection.collapsed(offset: prompt.length + 1),
      ),
    );
    await tester.pump();
    expect(input.controller!.text, '$prompt\n');

    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pump();
    expect(repository.messages, [prompt]);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    repository.result.complete(
      const BusinessAnalysis(
        summary: 'Review inventory.',
        insights: ['Low stock'],
        recommendations: ['Restock'],
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Review inventory.'), findsOneWidget);
    expect(find.text('Insights'), findsOneWidget);
    expect(find.text('Recommendations'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.tap(find.byKey(const Key('new-chat-button')));
    await tester.pumpAndSettle();
    expect(find.text('Review inventory.'), findsNothing);
    expect(find.text(prompt), findsOneWidget);
    expect(
      tester
          .widget<TextField>(find.byKey(const Key('chat-input')))
          .controller!
          .text,
      isEmpty,
    );
  });
}

class _PendingRepository implements ChatRepository {
  final messages = <String>[];
  final result = Completer<BusinessAnalysis>();

  @override
  Future<BusinessAnalysis> sendMessage(String message) {
    messages.add(message);
    return result.future;
  }
}
