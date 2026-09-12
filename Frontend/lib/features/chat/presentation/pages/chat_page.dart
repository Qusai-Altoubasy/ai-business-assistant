import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../app/theme/app_colors.dart';
import '../../../../app/theme/app_spacing.dart';
import '../controllers/chat_providers.dart';
import '../widgets/app_sidebar.dart';
import '../widgets/chat_empty_state.dart';
import '../widgets/chat_header.dart';
import '../widgets/chat_input.dart';
import '../widgets/chat_message_list.dart';

class ChatPage extends ConsumerStatefulWidget {
  const ChatPage({super.key});

  @override
  ConsumerState<ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends ConsumerState<ChatPage> {
  final _scaffoldKey = GlobalKey<ScaffoldState>();
  final _inputController = TextEditingController();
  final _inputFocusNode = FocusNode();
  final _scrollController = ScrollController();

  @override
  void dispose() {
    _inputController.dispose();
    _inputFocusNode.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final message = _inputController.text;
    if (message.trim().isEmpty) return;
    final operation = ref
        .read(chatControllerProvider.notifier)
        .sendMessage(message);
    _inputController.clear();
    await operation;
    if (mounted) _inputFocusNode.requestFocus();
  }

  void _selectPrompt(String prompt) {
    _inputController.text = prompt;
    _inputController.selection = TextSelection.collapsed(offset: prompt.length);
    _inputFocusNode.requestFocus();
  }

  void _resetChat() {
    ref.read(chatControllerProvider.notifier).resetChat();
    _inputController.clear();
    _inputFocusNode.requestFocus();
    if (_scaffoldKey.currentState?.isDrawerOpen ?? false) {
      Navigator.of(context).pop();
    }
  }

  void _scrollToLatest() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 220),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(chatControllerProvider);
    ref.listen<int>(
      chatControllerProvider.select((value) => value.messages.length),
      (_, _) => _scrollToLatest(),
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        final desktop = constraints.maxWidth >= 1000;
        return Scaffold(
          key: _scaffoldKey,
          drawer: desktop
              ? null
              : Drawer(
                  width: AppSpacing.sidebarWidth,
                  child: SafeArea(child: AppSidebar(onNewChat: _resetChat)),
                ),
          body: Row(
            children: [
              if (desktop)
                DecoratedBox(
                  decoration: const BoxDecoration(
                    boxShadow: [AppColors.softShadow],
                  ),
                  child: AppSidebar(onNewChat: _resetChat),
                ),
              Expanded(
                child: Column(
                  children: [
                    ChatHeader(
                      onOpenNavigation: desktop
                          ? null
                          : () => _scaffoldKey.currentState?.openDrawer(),
                    ),
                    Expanded(
                      child: Center(
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(
                            maxWidth: AppSpacing.maxContentWidth,
                          ),
                          child: Padding(
                            padding: EdgeInsets.symmetric(
                              horizontal: desktop
                                  ? AppSpacing.lg
                                  : AppSpacing.md,
                            ),
                            child: state.isEmpty
                                ? SingleChildScrollView(
                                    child: Column(
                                      children: [
                                        ChatEmptyState(
                                          onPromptSelected: _selectPrompt,
                                        ),
                                        ChatInput(
                                          controller: _inputController,
                                          focusNode: _inputFocusNode,
                                          onSend: _submit,
                                          isSubmitting: state.isSubmitting,
                                        ),
                                        const _Disclaimer(),
                                      ],
                                    ),
                                  )
                                : Column(
                                    children: [
                                      Expanded(
                                        child: ChatMessageList(
                                          messages: state.messages,
                                          scrollController: _scrollController,
                                          onRetry: () => ref
                                              .read(
                                                chatControllerProvider.notifier,
                                              )
                                              .retryLast(),
                                        ),
                                      ),
                                      ChatInput(
                                        controller: _inputController,
                                        focusNode: _inputFocusNode,
                                        onSend: _submit,
                                        isSubmitting: state.isSubmitting,
                                      ),
                                      const _Disclaimer(),
                                    ],
                                  ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _Disclaimer extends StatelessWidget {
  const _Disclaimer();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 10),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.verified_user_outlined,
            size: 14,
            color: AppColors.outline,
          ),
          SizedBox(width: 6),
          Flexible(
            child: Text(
              'AI Assistant can make mistakes. Verify important responses.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: AppColors.outline),
            ),
          ),
        ],
      ),
    );
  }
}
