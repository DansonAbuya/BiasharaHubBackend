/* BiasharaHub PWA Service Worker – cache static assets, optional offline */
const CACHE_NAME = 'biasharahub-v1';
const STATIC_URLS = [
  '/',
  '/manifest.json',
  '/favicon.png',
  '/logo.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_URLS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  const path = url.pathname;

  /* Never cache API or non-GET */
  if (path.startsWith('/api/') || event.request.method !== 'GET') {
    event.respondWith(fetch(event.request));
    return;
  }

  /* Static assets: cache-first (offline support) */
  if (
    path === '/' ||
    path === '/manifest.json' ||
    path === '/favicon.png' ||
    path === '/logo.png' ||
    path.match(/\.(js|css|ico|png|jpg|jpeg|gif|svg|woff2?|ttf|eot)$/i)
  ) {
    event.respondWith(
      caches.match(event.request).then((cached) =>
        cached ? Promise.resolve(cached) : fetch(event.request).then((res) => {
          const clone = res.clone();
          if (res.ok) caches.open(CACHE_NAME).then((c) => c.put(event.request, clone));
          return res;
        })
      )
    );
    return;
  }

  /* Everything else: network-first, no cache */
  event.respondWith(fetch(event.request));
});
