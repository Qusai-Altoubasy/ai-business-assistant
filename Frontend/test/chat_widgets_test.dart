import 'dart:async';

import 'package:ai_business_assistant/app/theme/app_theme.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/ai_provider.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/business_analysis.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/chat_message.dart';
import 'package:ai_business_assistant/features/chat/domain/entities/chat_stream_event.dart';
import 'package:ai_business_assistant/features/chat/domain/repositories/chat_repository.dart';
import 'package:ai_business_assistant/features/chat/presentation/controllers/chat_providers.dart';
import 'package:ai_business_assistant/features/chat/presentation/pages/chat_page.dart';
import 'package:ai_business_assistant/features/chat/presentation/widgets/assistant_message_card.dart';
import 'package:ai_business_assistant/features/chat/presentation/widgets/chat_input.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('provider menu selects Ollama and Gemini with a checkmark', (
    tester,
  ) async {
    await _pumpChat(tester, _PendingRepository());
    expect(_providerMenu(tester).enabled, isTrue);
    expect(find.text('Gemini'), findsOneWidget);

    await tester.tap(find.byKey(const Key('ai-provider-selector')));
    await tester.pumpAndSettle();
    expect(
      find.descendant(
        of: find.byKey(const Key('ai-provider-option-gemini')),
        matching: find.byIcon(Icons.check),
      ),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const Key('ai-provider-option-ollama')));
    await tester.pumpAndSettle();
    expect(find.text('Ollama'), findsOneWidget);
    expect(find.text('Gemini'), findsNothing);

    await tester.tap(find.byKey(const Key('ai-provider-selector')));
    await tester.pumpAndSettle();
    expect(
      find.descendant(
        of: find.byKey(const Key('ai-provider-option-ollama')),
        matching: find.byIcon(Icons.check),
      ),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const Key('ai-provider-option-gemini')));
    await tester.pumpAndSettle();
    expect(find.text('Gemini'), findsOneWidget);
    expect(find.text('Ollama'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('structured requests lock the provider until New Chat', (
    tester,
  ) async {
    final repository = _PendingRepository();
    await _pumpChat(tester, repository);
    await tester.tap(find.byKey(const Key('ai-provider-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('ai-provider-option-ollama')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('chat-input')), 'First');
    await tester.pump();
    await tester.tap(find.byKey(const Key('send-button')));
    await tester.pump();
    expect(repository.providers, [AiProvider.ollama]);
    expect(_providerMenu(tester).enabled, isFalse);
    await tester.tap(find.byKey(const Key('ai-provider-selector')));
    await tester.pump();
    expect(find.byKey(const Key('ai-provider-option-gemini')), findsNothing);
    repository.result.complete(const BusinessAnalysis(summary: 'Answer'));
    await tester.pumpAndSettle();
    expect(_providerMenu(tester).enabled, isFalse);
    expect(find.byIcon(Icons.lock_outline), findsOneWidget);
    expect(
      _providerMenu(tester).tooltip,
      'Start a new chat to change AI provider.',
    );
    await tester.enterText(find.byKey(const Key('chat-input')), 'Follow-up');
    await tester.pump();
    await tester.tap(find.byKey(const Key('send-button')));
    await tester.pumpAndSettle();
    expect(repository.providers, [AiProvider.ollama, AiProvider.ollama]);
    expect(repository.ids.first, repository.ids.last);

    await tester.tap(find.byKey(const Key('new-chat-button')));
    await tester.pumpAndSettle();
    expect(find.text('Answer'), findsNothing);
    expect(find.text('Ollama'), findsOneWidget);
    expect(_providerMenu(tester).enabled, isTrue);
    expect(
      tester
          .widget<TextField>(find.byKey(const Key('chat-input')))
          .controller!
          .text,
      isEmpty,
    );
    await tester.tap(find.byKey(const Key('ai-provider-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('ai-provider-option-gemini')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('chat-input')),
      'New conversation',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('send-button')));
    await tester.pumpAndSettle();
    expect(repository.providers.last, AiProvider.gemini);
    expect(repository.ids.first, isNot(repository.ids.last));
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'compact provider control fits a narrow composer and disables while busy',
    (tester) async {
      final input = TextEditingController();
      final focus = FocusNode();
      addTearDown(input.dispose);
      addTearDown(focus.dispose);
      Future<void> show({required bool submitting}) => tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light,
          home: Scaffold(
            body: Center(
              child: SizedBox(
                width: 288,
                child: ChatInput(
                  controller: input,
                  focusNode: focus,
                  onSend: () {},
                  isSubmitting: submitting,
                  provider: AiProvider.gemini,
                  isProviderPinned: false,
                  onProviderChanged: (_) {},
                ),
              ),
            ),
          ),
        ),
      );
      await show(submitting: false);
      expect(_providerMenu(tester).enabled, isTrue);
      expect(tester.takeException(), isNull);
      await show(submitting: true);
      expect(_providerMenu(tester).enabled, isFalse);
      await tester.tap(find.byKey(const Key('ai-provider-selector')));
      await tester.pump();
      expect(find.byKey(const Key('ai-provider-option-ollama')), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Streaming mode uses the shared composer and one progressive assistant card',
    (tester) async {
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
      expect(find.text('CHAT MODE'), findsOneWidget);
      expect(find.text('ARCHITECTURE MODULES'), findsNothing);
      expect(find.text('Memory · Available'), findsOneWidget);
      await tester.tap(find.text('Streaming Chat'));
      await tester.pump();
      const prompt = 'Which products are currently low in stock?';
      await tester.tap(find.text(prompt));
      await tester.pump();
      await tester.tap(find.byKey(const Key('send-button')));
      await tester.pump();
      expect(repository.streamMessages, [prompt]);
      expect(repository.providers, [AiProvider.gemini]);
      expect(_providerMenu(tester).enabled, isFalse);
      await tester.tap(find.byKey(const Key('ai-provider-selector')));
      await tester.pump();
      expect(find.byKey(const Key('ai-provider-option-ollama')), findsNothing);
      expect(repository.messages, isEmpty);
      repository.events.add(
        const ChatStreamEvent(
          StreamEventType.toolStarted,
          'getLowStockProducts',
        ),
      );
      await tester.pump();
      expect(find.text('Checking inventory…'), findsOneWidget);
      expect(
        tester
            .widget<FilledButton>(find.byKey(const Key('send-button')))
            .onPressed,
        isNull,
      );
      repository.events.add(
        const ChatStreamEvent(
          StreamEventType.toolCompleted,
          'getLowStockProducts',
        ),
      );
      await tester.pump();
      expect(find.text('Business data retrieved…'), findsOneWidget);
      repository.events.add(
        const ChatStreamEvent(StreamEventType.chunk, 'Hello'),
      );
      await tester.pump();
      expect(find.text('Hello'), findsOneWidget);
      expect(find.byType(AssistantMessageCard), findsOneWidget);
      repository.events.add(
        const ChatStreamEvent(StreamEventType.chunk, ' world'),
      );
      await tester.pump();
      expect(find.text('Hello world'), findsOneWidget);
      expect(find.byType(AssistantMessageCard), findsOneWidget);
      repository.events.add(const ChatStreamEvent(StreamEventType.done, null));
      await tester.pumpAndSettle();
      expect(find.byType(CircularProgressIndicator), findsNothing);
      expect(find.text('Receiving response…'), findsNothing);
      expect(_providerMenu(tester).enabled, isFalse);
      await tester.tap(find.byKey(const Key('new-chat-button')));
      await tester.pumpAndSettle();
      expect(find.text('Hello world'), findsNothing);
      expect(find.text('Live streaming · Business analysis'), findsOneWidget);
      expect(find.text(prompt), findsOneWidget);
      expect(find.text('Gemini'), findsOneWidget);
      expect(_providerMenu(tester).enabled, isTrue);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('interrupted streaming keeps partial text and a separate error', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AssistantMessageCard(
            message: ChatMessage(
              id: 'a',
              role: ChatRole.assistant,
              content: 'Partial answer',
              createdAt: DateTime(2026),
              status: MessageStatus.error,
              error: 'Unable to complete the streaming request.',
            ),
            onRetry: () {},
          ),
        ),
      ),
    );
    expect(find.text('Partial answer'), findsOneWidget);
    expect(
      find.text(
        'Response interrupted. Unable to complete the streaming request.',
      ),
      findsOneWidget,
    );
    expect(find.byKey(const Key('retry-button')), findsOneWidget);
  });
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
  final providers = <AiProvider>[];
  final ids = <String>[];
  final result = Completer<BusinessAnalysis>();
  final streamMessages = <String>[];
  final events = StreamController<ChatStreamEvent>();

  @override
  Stream<ChatStreamEvent> streamMessage(
    String message,
    String conversationId, {
    required AiProvider provider,
    Future<void>? abortTrigger,
  }) {
    streamMessages.add(message);
    providers.add(provider);
    ids.add(conversationId);
    abortTrigger?.then((_) {
      if (!events.isClosed) events.close();
    });
    return events.stream;
  }

  @override
  Future<BusinessAnalysis> sendMessage(
    String message,
    String conversationId, {
    required AiProvider provider,
  }) {
    messages.add(message);
    providers.add(provider);
    ids.add(conversationId);
    return result.future;
  }
}

PopupMenuButton<AiProvider> _providerMenu(WidgetTester tester) =>
    tester.widget<PopupMenuButton<AiProvider>>(
      find.byKey(const Key('ai-provider-selector')),
    );

Future<void> _pumpChat(WidgetTester tester, ChatRepository repository) async {
  tester.view.physicalSize = const Size(1400, 1200);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [chatRepositoryProvider.overrideWithValue(repository)],
      child: MaterialApp(
        theme: AppTheme.light,
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
}
