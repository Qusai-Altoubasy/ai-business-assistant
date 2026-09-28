import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/config/app_config.dart';
import '../../../../core/network/api_client.dart';
import '../../data/datasources/chat_remote_data_source.dart';
import '../../data/repositories/chat_repository_impl.dart';
import '../../domain/repositories/chat_repository.dart';
import 'chat_controller.dart';
import 'chat_state.dart';

final appConfigProvider = Provider<AppConfig>(
  (ref) => AppConfig.fromEnvironment(),
);

final dioProvider = Provider<Dio>((ref) => Dio());

final apiClientProvider = Provider<ApiClient>((ref) {
  final client = ApiClient(
    config: ref.watch(appConfigProvider),
    dio: ref.watch(dioProvider),
  );
  ref.onDispose(client.close);
  return client;
});

final chatRemoteDataSourceProvider = Provider<ChatRemoteDataSource>(
  (ref) => ChatRemoteDataSource(ref.watch(apiClientProvider)),
);

final chatRepositoryProvider = Provider<ChatRepository>(
  (ref) => ChatRepositoryImpl(ref.watch(chatRemoteDataSourceProvider)),
);

final chatControllerProvider =
    StateNotifierProvider.autoDispose<ChatController, ChatState>(
      (ref) => ChatController(ref.watch(chatRepositoryProvider)),
    );
