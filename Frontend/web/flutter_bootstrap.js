{{flutter_js}}
{{flutter_build_config}}

async function clearLegacyFlutterWebCache() {
  if ('serviceWorker' in navigator) {
    const registrations = await navigator.serviceWorker.getRegistrations();
    await Promise.all(registrations.map((registration) => registration.unregister()));
  }

  if ('caches' in window) {
    const cacheNames = await caches.keys();
    const flutterCacheNames = cacheNames.filter(
      (name) => name.startsWith('flutter-'),
    );
    await Promise.all(flutterCacheNames.map((name) => caches.delete(name)));
  }
}

clearLegacyFlutterWebCache()
  .catch((error) => console.warn('Could not clear the legacy Flutter cache.', error))
  .then(() => _flutter.loader.load());
