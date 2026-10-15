const CACHE_NAME = 'spotify-saver-v1';
const ASSETS = [
    './',
    './index.html',
    './manifest.json'
];

self.addEventListener('install', (event) => {
    self.skipWaiting();
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS))
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(self.clients.claim());
});

// Netzwerk-Anfragen durchlassen und bei Offline-Status auf Cache zurückgreifen
self.addEventListener('fetch', (event) => {
    if (event.request.url.includes('api.spotify.com') || event.request.url.includes('accounts.spotify.com')) {
        return; // Spotify-API-Calls niemals cachen
    }
    event.respondWith(
        fetch(event.request).catch(() => caches.match(event.request))
    );
});

// Heartbeat vom Web-Worker empfangen, um den Service Worker wachzuhalten
self.addEventListener('message', (event) => {
    if (event.data && event.data.type === 'HEARTBEAT') {
        event.waitUntil(Promise.resolve());
    }
});
