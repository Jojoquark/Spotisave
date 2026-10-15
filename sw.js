// Spotisave Service Worker - Apple HIG PWA Edition
const CACHE_NAME = 'spotisave-v3';
const ASSETS = [
    './',
    './index.html',
    './manifest.json',
    './icons/icon-192.png',
    './icons/icon-512.png',
    './icons/maskable-192.png',
    './icons/maskable-512.png'
];

self.addEventListener('install', (event) => {
    self.skipWaiting();
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS))
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) => {
            return Promise.all(
                keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
            );
        }).then(() => self.clients.claim())
    );
});

// Netzwerk-Anfragen durchlassen und bei Offline-Status auf Cache zurückgreifen
self.addEventListener('fetch', (event) => {
    // Nur GET-Anfragen abfangen
    if (event.request.method !== 'GET') return;

    const url = event.request.url;
    // Spotify-API- und Auth-Calls niemals im Service Worker cachen
    if (url.includes('api.spotify.com') || url.includes('accounts.spotify.com')) {
        return;
    }

    event.respondWith(
        fetch(event.request)
            .then((response) => {
                // Bei erfolgreicher lokaler Antwort Cache im Hintergrund aktualisieren
                if (response.status === 200 && response.type === 'basic') {
                    const responseClone = response.clone();
                    caches.open(CACHE_NAME).then((cache) => {
                        cache.put(event.request, responseClone);
                    });
                }
                return response;
            })
            .catch(() => {
                return caches.match(event.request, { ignoreSearch: true }).then((cachedResponse) => {
                    if (cachedResponse) return cachedResponse;
                    // Fallback auf index.html für Navigation im Offline-Modus
                    if (event.request.mode === 'navigate') {
                        return caches.match('./index.html').then((indexRes) => {
                            return indexRes || caches.match('./');
                        });
                    }
                    return new Response('Offline', { status: 503, statusText: 'Offline' });
                });
            })
    );
});

// Heartbeat vom Web-Worker empfangen, um den Service Worker wachzuhalten
self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'HEARTBEAT') {
        event.waitUntil(Promise.resolve());
    }
});
