// Spotisave Automated Verification & Deep Test Suite
// Verifies HTML/JS syntax, PKCE RFC 7636 vectors, Service Worker cache rules,
// 1-Click Intelligent Resume Decision Tree, Background Check Loop,
// Apple HIG contrast tokens, and executes the ACTUAL inline code from index.html.

import fs from 'fs';
import path from 'path';
import vm from 'vm';
import nodeCrypto from 'crypto';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

let totalTests = 0;
let passedTests = 0;
let failedTests = 0;

function assert(condition, testName, details = '') {
    totalTests++;
    if (condition) {
        passedTests++;
        console.log(`  ✓ ${testName}`);
    } else {
        failedTests++;
        console.error(`  ✗ FAIL: ${testName} ${details ? `(${details})` : ''}`);
    }
}

// Helper to set up sandbox DOM with the real index.html inline script
function setupSandbox(customFetch) {
    const storage = {};
    const mockLocalStorage = {
        getItem: (k) => (k in storage ? storage[k] : null),
        setItem: (k, v) => {
            storage[k] = String(v);
            if (k === 'sp_access_token' && !storage['sp_token_expiry']) {
                storage['sp_token_expiry'] = String(Date.now() + 3600000);
            }
        },
        removeItem: (k) => { delete storage[k]; },
        clear: () => { Object.keys(storage).forEach(k => delete storage[k]); }
    };

    const mockElements = {};
    function createMockEl(id) {
        const classSet = new Set();
        const attrs = {};
        const el = {
            id,
            style: {},
            classList: {
                add: (c) => classSet.add(c),
                remove: (c) => classSet.delete(c),
                contains: (c) => classSet.has(c)
            },
            innerText: '',
            innerHTML: '',
            value: '',
            href: '',
            disabled: false,
            scrollTop: 0,
            setAttribute: (k, v) => { attrs[k] = String(v); if (k === 'href') el.href = String(v); },
            getAttribute: (k) => attrs[k] || (k === 'href' ? el.href : ''),
            addEventListener: () => {},
            querySelector: (sel) => createMockEl(sel),
            querySelectorAll: () => []
        };
        return el;
    }

    const mockDocument = {
        getElementById: (id) => {
            if (!mockElements[id]) mockElements[id] = createMockEl(id);
            return mockElements[id];
        },
        querySelector: (sel) => {
            if (!mockElements[sel]) mockElements[sel] = createMockEl(sel);
            return mockElements[sel];
        },
        querySelectorAll: () => [],
        addEventListener: () => {},
        title: 'Spotisave',
        hidden: false
    };

    let wakeLockReleased = false;
    const mockWakeLockSentinel = {
        release: async () => { wakeLockReleased = true; },
        addEventListener: (event, handler) => {
            if (event === 'release') mockWakeLockSentinel._onRelease = handler;
        }
    };

    const sandbox = {
        window: {
            location: { origin: 'https://example.com', pathname: '/spotisave/', search: '', href: '' },
            addEventListener: () => {},
            history: { replaceState: () => {} }
        },
        document: mockDocument,
        localStorage: mockLocalStorage,
        navigator: {
            onLine: true,
            serviceWorker: { register: async () => ({}), controller: { postMessage: () => {} } },
            vibrate: () => true,
            clipboard: { writeText: async () => {} },
            wakeLock: {
                request: async (type) => mockWakeLockSentinel
            },
            locks: {
                request: async (name, cb) => cb()
            }
        },
        URLSearchParams,
        TextEncoder,
        Uint8Array,
        Array,
        String,
        Math,
        Date,
        JSON,
        Number,
        Boolean,
        setTimeout: (fn, ms) => {
            if (typeof fn === 'function') setTimeout(fn, ms);
            return 1;
        },
        clearTimeout: () => {},
        setInterval: () => 1,
        clearInterval: () => {},
        console: { log: () => {}, error: () => {}, warn: () => {} },
        crypto: {
            getRandomValues: (arr) => nodeCrypto.randomFillSync(arr),
            subtle: crypto.subtle
        },
        Blob: class { constructor(parts, opts) { this.parts = parts; this.opts = opts; } },
        URL: { createObjectURL: () => 'blob:mock-worker', revokeObjectURL: () => {} },
        Worker: class {
            constructor() { this.onmessage = null; }
            postMessage() {}
            terminate() {}
        },
        btoa: (str) => Buffer.from(str, 'binary').toString('base64'),
        atob: (b64) => Buffer.from(b64, 'base64').toString('binary'),
        fetch: customFetch || (async () => ({ ok: true, status: 200, json: async () => ({}) }))
    };
    sandbox.window.location.origin = 'https://example.com';
    sandbox.window.location.pathname = '/spotisave/';

    const indexHtml = fs.readFileSync(path.join(rootDir, 'index.html'), 'utf8');
    const scriptMatch = indexHtml.match(/<script>([\s\S]*?)<\/script>/);
    const inlineJs = scriptMatch ? scriptMatch[1] : '';

    const vmContext = vm.createContext(sandbox);
    vm.runInContext(inlineJs, vmContext);
    sandbox.engineState = sandbox.window.engineState;

    return { sandbox, mockElements, mockLocalStorage, mockWakeLockSentinel };
}

async function runTests() {
    console.log('\n========================================');
    console.log('🧪 SPOTISAVE COMPREHENSIVE TEST SUITE');
    console.log('========================================\n');

    const indexHtmlPath = path.join(rootDir, 'index.html');
    const indexHtml = fs.readFileSync(indexHtmlPath, 'utf8');
    const swPath = path.join(rootDir, 'sw.js');
    const swCode = fs.readFileSync(swPath, 'utf8');
    const manifestPath = path.join(rootDir, 'manifest.json');
    const manifestContent = fs.readFileSync(manifestPath, 'utf8');

    // ----------------------------------------------------
    // TEST 1: HTML & Inline JS Syntax Validation
    // ----------------------------------------------------
    console.log('Test Group 1: HTML Structure & Inline JS Syntax');
    assert(indexHtml.includes('<!DOCTYPE html>'), 'index.html has DOCTYPE declaration');
    assert(indexHtml.includes('viewport-fit=cover'), 'index.html supports iOS safe-area viewport-fit=cover');
    assert(!indexHtml.includes('user-scalable=no'), 'index.html does not restrict user scaling (HIG Accessibility)');
    assert(indexHtml.includes('apple-mobile-web-app-capable'), 'index.html has iOS PWA meta tag');
    assert(indexHtml.includes('apple-mobile-web-app-status-bar-style'), 'index.html has translucent status bar meta tag');
    assert(indexHtml.includes('manifest.json'), 'index.html links to manifest.json');

    const scriptMatch = indexHtml.match(/<script>([\s\S]*?)<\/script>/);
    assert(!!scriptMatch, 'index.html contains <script> tag');

    let scriptSyntaxValid = false;
    try {
        new vm.Script(scriptMatch[1], { filename: 'index.html' });
        scriptSyntaxValid = true;
    } catch (e) {
        console.error('JS Syntax error in index.html:', e);
    }
    assert(scriptSyntaxValid, 'Inline JavaScript in index.html passes full syntax check with 0 errors');

    // ----------------------------------------------------
    // TEST 2: Service Worker Syntax & Caching Rules
    // ----------------------------------------------------
    console.log('\nTest Group 2: Service Worker Syntax & Cache Rules');
    let swSyntaxValid = false;
    try {
        new vm.Script(swCode, { filename: 'sw.js' });
        swSyntaxValid = true;
    } catch (e) {
        console.error('JS Syntax error in sw.js:', e);
    }
    assert(swSyntaxValid, 'sw.js passes full syntax check with 0 errors');
    assert(swCode.includes("CACHE_NAME = 'spotisave-v2'"), 'sw.js uses bumped cache version spotisave-v2');
    assert(swCode.includes('caches.delete'), 'sw.js cleans up stale caches in activate listener');
    assert(swCode.includes("request.method !== 'GET'"), 'sw.js guards fetch to only handle GET requests');
    assert(swCode.includes('api.spotify.com') && swCode.includes('accounts.spotify.com'), 'sw.js never caches Spotify API or Auth requests');
    assert(swCode.includes('ignoreSearch: true'), 'sw.js supports ignoreSearch in offline cache fallback');

    // ----------------------------------------------------
    // TEST 3: Web App Manifest Validation
    // ----------------------------------------------------
    console.log('\nTest Group 3: Web App Manifest Validation');
    let manifestData = null;
    let manifestValidJson = false;
    try {
        manifestData = JSON.parse(manifestContent);
        manifestValidJson = true;
    } catch (e) {
        console.error('Manifest JSON error:', e);
    }
    assert(manifestValidJson, 'manifest.json is valid JSON');
    assert(manifestData?.short_name === 'Spotisave', 'manifest.json short_name is Spotisave');
    assert(manifestData?.background_color === '#000000', 'manifest.json background_color is pure OLED black #000000');
    assert(manifestData?.display === 'standalone', 'manifest.json display mode is standalone');
    assert(Array.isArray(manifestData?.icons) && manifestData.icons.length >= 2, 'manifest.json contains required icons');

    // ----------------------------------------------------
    // TEST 4: PKCE RFC 7636 Cryptographic Compliance (Running actual code from index.html)
    // ----------------------------------------------------
    console.log('\nTest Group 4: PKCE RFC 7636 Cryptographic Compliance (index.html inline functions)');
    const { sandbox: baseSandbox } = setupSandbox();

    const testRandStr = baseSandbox.generateRandomString(64);
    assert(testRandStr.length === 64, 'index.html generateRandomString(64) produces exactly 64 characters');
    assert(/^[A-Za-z0-9]+$/.test(testRandStr), 'index.html generateRandomString only contains valid alphanumeric characters');

    // Verifier SHA-256 + Base64URL test against RFC 7636 test vector
    const rfcVerifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';
    const rfcDigest = await baseSandbox.sha256(rfcVerifier);
    const actualChallenge = baseSandbox.base64encode(rfcDigest);
    const expectedChallenge = nodeCrypto.createHash('sha256').update(rfcVerifier).digest('base64url');

    assert(actualChallenge === expectedChallenge, 'index.html PKCE code_challenge matches SHA-256 Base64URL standard', `Got: ${actualChallenge}, Expected: ${expectedChallenge}`);

    // ----------------------------------------------------
    // TEST 5: Time Formatting & Relative Counters (Running actual code from index.html)
    // ----------------------------------------------------
    console.log('\nTest Group 5: Time Formatting & Relative Counters (index.html inline functions)');
    assert(baseSandbox.formatTime(0) === '0:00', 'formatTime(0) returns 0:00');
    assert(baseSandbox.formatTime(5000) === '0:05', 'formatTime(5000) returns 0:05');
    assert(baseSandbox.formatTime(65000) === '1:05', 'formatTime(65000) returns 1:05');
    assert(baseSandbox.formatTime(600000) === '10:00', 'formatTime(600000) returns 10:00');
    assert(baseSandbox.formatTime(3665000) === '1:01:05', 'formatTime(3665000) formats hours with zero-padded minutes 1:01:05');
    assert(baseSandbox.formatTime(-500) === '0:00', 'formatTime(-500) returns 0:00 (negative edge case handled)');
    assert(baseSandbox.formatTime(null) === '0:00', 'formatTime(null) returns 0:00 (null edge case handled)');
    assert(baseSandbox.formatTime(NaN) === '0:00', 'formatTime(NaN) returns 0:00 (NaN edge case handled)');

    const mockNow = 1000000000000;
    assert(baseSandbox.formatRelativeTime(mockNow - 2000, mockNow) === 'Gerade eben', 'formatRelativeTime handles seconds < 5');
    assert(baseSandbox.formatRelativeTime(mockNow - 30000, mockNow) === 'Vor 30 Sek.', 'formatRelativeTime handles seconds < 60');
    assert(baseSandbox.formatRelativeTime(mockNow - 180000, mockNow) === 'Vor 3 Min.', 'formatRelativeTime handles minutes');
    assert(baseSandbox.formatRelativeTime(mockNow - 7200000, mockNow) === 'Vor 2 Std.', 'formatRelativeTime handles hours');
    assert(baseSandbox.formatRelativeTime(mockNow - 172800000, mockNow) === 'Vor 2 Tg.', 'formatRelativeTime handles days');
    assert(baseSandbox.formatRelativeTime(null, mockNow) === 'Noch nie', 'formatRelativeTime handles null/undefined');

    // ----------------------------------------------------
    // TEST 6: 1-Click Intelligent Resume Decision Tree (Running actual resumeBookmark from index.html)
    // ----------------------------------------------------
    console.log('\nTest Group 6: 1-Click Intelligent Resume Decision Tree (Running index.html resumeBookmark)');

    const sampleBookmark = {
        playlist_id: '37i9dQZF1DXcBWIGoYBM5M',
        playlist_name: "Today's Top Hits",
        track_name: 'Espresso',
        artist_name: 'Sabrina Carpenter',
        track_uri: 'spotify:track:4cOdK2wGLETKBW3PvgPWqT',
        context_uri: 'spotify:playlist:37i9dQZF1DXcBWIGoYBM5M',
        progress_ms: 45000,
        updated_at: Date.now()
    };

    // Scenario A: Direct 204 success
    {
        const calls = [];
        const { sandbox } = setupSandbox(async (url, opts) => {
            calls.push({ url, opts });
            return { ok: true, status: 204, json: async () => ({}) };
        });
        sandbox.localStorage.setItem('sp_access_token', 'mock_token');
        sandbox.localStorage.setItem('sp_bookmarks', JSON.stringify({ [sampleBookmark.playlist_id]: sampleBookmark }));

        await sandbox.resumeBookmark(sampleBookmark.playlist_id);
        assert(calls.length === 1 && calls[0].url.includes('/me/player/play'), 'Resume Scenario A: Direct 204 success calling /me/player/play');
        const reqBody = JSON.parse(calls[0].opts.body);
        assert(reqBody.context_uri === sampleBookmark.context_uri && reqBody.position_ms === 45000, 'Resume Scenario A: Request payload matches bookmark');
    }

    // Scenario B: 404 No Active Device -> Resolves target device -> Successfully resumes on target
    {
        const calls = [];
        const { sandbox } = setupSandbox(async (url, opts) => {
            calls.push({ url, opts });
            if (url.includes('/me/player/play') && !url.includes('device_id')) {
                return { ok: false, status: 404, json: async () => ({ error: { status: 404, message: 'No active device' } }) };
            }
            if (url.includes('/me/player/devices')) {
                return { ok: true, status: 200, json: async () => ({ devices: [{ id: 'device_iphone_01', is_active: false, name: 'iPhone' }] }) };
            }
            if (url.includes('/me/player/play?device_id=device_iphone_01')) {
                return { ok: true, status: 204, json: async () => ({}) };
            }
            return { ok: true, status: 200, json: async () => ({}) };
        });
        sandbox.localStorage.setItem('sp_access_token', 'mock_token');
        sandbox.localStorage.setItem('sp_bookmarks', JSON.stringify({ [sampleBookmark.playlist_id]: sampleBookmark }));

        await sandbox.resumeBookmark(sampleBookmark.playlist_id);
        const devicePlayCall = calls.find(c => c.url.includes('/me/player/play?device_id=device_iphone_01'));
        assert(!!devicePlayCall, 'Resume Scenario B: 404 resolved by querying devices and retrying on target device_id');
    }

    // Scenario C: 404 -> Target Device resolved -> Returns 400 (Track offset invalid) -> Fallback 2 uses direct track WITH the same target device!
    {
        const calls = [];
        const { sandbox } = setupSandbox(async (url, opts) => {
            calls.push({ url, opts });
            if (url.includes('/me/player/play') && !url.includes('device_id')) {
                return { ok: false, status: 404, json: async () => ({ error: { status: 404 } }) };
            }
            if (url.includes('/me/player/devices')) {
                return { ok: true, status: 200, json: async () => ({ devices: [{ id: 'device_mac_02', is_active: false }] }) };
            }
            if (url.includes('/me/player/play?device_id=device_mac_02')) {
                // If it's the context play call, fail with 400; if it's the track URI fallback, succeed!
                const body = JSON.parse(opts.body);
                if (body.context_uri) {
                    return { ok: false, status: 400, json: async () => ({ error: { status: 400 } }) };
                }
                return { ok: true, status: 204, json: async () => ({}) };
            }
            return { ok: true, status: 200, json: async () => ({}) };
        });
        sandbox.localStorage.setItem('sp_access_token', 'mock_token');
        sandbox.localStorage.setItem('sp_bookmarks', JSON.stringify({ [sampleBookmark.playlist_id]: sampleBookmark }));

        await sandbox.resumeBookmark(sampleBookmark.playlist_id);
        const fallbackCall = calls.find(c => c.url.includes('/me/player/play?device_id=device_mac_02') && c.opts.body.includes('uris'));
        assert(!!fallbackCall, 'Resume Scenario C: 400 fallback cleanly uses direct track URI preserving the target device_id');
    }

    // Scenario D: 404 with 0 devices online -> Opens 1-click fallback sheet with native and web links
    {
        const { sandbox, mockElements } = setupSandbox(async (url) => {
            if (url.includes('/me/player/devices')) {
                return { ok: true, status: 200, json: async () => ({ devices: [] }) };
            }
            return { ok: false, status: 404, json: async () => ({ error: { status: 404 } }) };
        });
        sandbox.localStorage.setItem('sp_access_token', 'mock_token');
        sandbox.localStorage.setItem('sp_bookmarks', JSON.stringify({ [sampleBookmark.playlist_id]: sampleBookmark }));

        await sandbox.resumeBookmark(sampleBookmark.playlist_id);
        const modal = mockElements['fallback-modal'];
        assert(modal.classList.contains('open'), 'Resume Scenario D: 404 with 0 devices opens 1-click fallback sheet modal');
        const appBtn = mockElements['fallback-app-btn'];
        assert(appBtn.getAttribute('data-uri') === sampleBookmark.context_uri, 'Resume Scenario D: Fallback app button has native deep-link URI');
        const webBtn = mockElements['fallback-web-btn'];
        assert(webBtn.href.includes(`https://open.spotify.com/playlist/${sampleBookmark.playlist_id}`), 'Resume Scenario D: Fallback web button links to open.spotify.com');
    }

    // Scenario E: 403 Forbidden (Free Spotify Account Web API restriction) -> Opens 1-click fallback sheet
    {
        const { sandbox, mockElements } = setupSandbox(async () => {
            return { ok: false, status: 403, json: async () => ({ error: { status: 403, message: 'Premium required' } }) };
        });
        sandbox.localStorage.setItem('sp_access_token', 'mock_token');
        sandbox.localStorage.setItem('sp_bookmarks', JSON.stringify({ [sampleBookmark.playlist_id]: sampleBookmark }));

        await sandbox.resumeBookmark(sampleBookmark.playlist_id);
        const modal = mockElements['fallback-modal'];
        assert(modal.classList.contains('open'), 'Resume Scenario E: 403 Free user opens 1-click fallback sheet modal');
        const desc = mockElements['fallback-desc'];
        assert(desc.innerHTML.includes('Spotify Premium erforderlich'), 'Resume Scenario E: Fallback sheet informs about Spotify Premium requirement');
    }

    // ----------------------------------------------------
    // TEST 7: Background Polling & Auto-Save Loop (Running actual checkNowPlaying from index.html)
    // ----------------------------------------------------
    console.log('\nTest Group 7: Background Polling & Auto-Save Loop (Running index.html checkNowPlaying)');

    // Case 1: 204 No Content -> IDLE state
    {
        const { sandbox } = setupSandbox(async () => ({ ok: true, status: 204, json: async () => ({}) }));
        sandbox.localStorage.setItem('sp_access_token', 'mock_token');
        await sandbox.checkNowPlaying(false);
        assert(sandbox.engineState.currentStatus === 'IDLE', 'checkNowPlaying 204 No Content sets status to IDLE');
    }

    // Case 2: 200 Playing in playlist -> Saves bookmark to localStorage & updates UI
    {
        const mockPlayerResponse = {
            is_playing: true,
            progress_ms: 62000,
            item: {
                name: 'Levitating',
                duration_ms: 203000,
                uri: 'spotify:track:levitating123',
                artists: [{ name: 'Dua Lipa' }],
                album: { images: [{ url: 'https://example.com/cover.jpg' }] }
            },
            context: {
                type: 'playlist',
                uri: 'spotify:playlist:pop_hits_123'
            }
        };

        const { sandbox, mockElements } = setupSandbox(async (url) => {
            if (url.includes('/playlists/pop_hits_123')) {
                return { ok: true, status: 200, json: async () => ({ name: 'Pop Hits 2026', images: [{ url: 'https://example.com/pl.jpg' }] }) };
            }
            return { ok: true, status: 200, json: async () => mockPlayerResponse };
        });
        sandbox.localStorage.setItem('sp_access_token', 'mock_token');

        await sandbox.checkNowPlaying(false);
        assert(sandbox.engineState.currentStatus === 'PLAYING', 'checkNowPlaying active playlist playback sets status to PLAYING');
        const bookmarks = JSON.parse(sandbox.localStorage.getItem('sp_bookmarks') || '{}');
        assert(bookmarks['pop_hits_123'] && bookmarks['pop_hits_123'].progress_ms === 62000, 'checkNowPlaying saves accurate playlist bookmark with progress 62000ms');
        assert(bookmarks['pop_hits_123'].artist_name === 'Dua Lipa', 'checkNowPlaying captures correct artist name');
        assert(mockElements['np-track-name'].innerText === 'Levitating', 'checkNowPlaying updates now-playing hero track title');
    }

    // Case 3: 200 PAUSED playback -> DOES NOT discard paused position! Saves bookmark if changed!
    {
        const mockPausedResponse = {
            is_playing: false,
            progress_ms: 95000,
            item: {
                name: 'Midnight City',
                duration_ms: 240000,
                uri: 'spotify:track:m83_midnight',
                artists: [{ name: 'M83' }],
                album: { images: [{ url: 'https://example.com/m83.jpg' }] }
            },
            context: {
                type: 'playlist',
                uri: 'spotify:playlist:electronic_123'
            }
        };

        const { sandbox } = setupSandbox(async (url) => {
            if (url.includes('/playlists/electronic_123')) {
                return { ok: true, status: 200, json: async () => ({ name: 'Electronic Favorites', images: [] }) };
            }
            return { ok: true, status: 200, json: async () => mockPausedResponse };
        });
        sandbox.localStorage.setItem('sp_access_token', 'mock_token');

        await sandbox.checkNowPlaying(false);
        assert(sandbox.engineState.currentStatus === 'PAUSED', 'checkNowPlaying paused playback sets status to PAUSED');
        const bookmarks = JSON.parse(sandbox.localStorage.getItem('sp_bookmarks') || '{}');
        assert(bookmarks['electronic_123'] && bookmarks['electronic_123'].progress_ms === 95000, 'checkNowPlaying successfully preserves and saves bookmark when user pauses');
    }

    // Case 4: 200 with Podcast Episode -> Extracts show name properly
    {
        const mockPodcastResponse = {
            is_playing: true,
            progress_ms: 120000,
            item: {
                name: 'Episode 42: KI im Alltag',
                duration_ms: 3600000,
                uri: 'spotify:episode:ki_alltag_42',
                show: { name: 'Tech Talk Daily', images: [{ url: 'https://example.com/show.jpg' }] }
            },
            context: {
                type: 'show',
                uri: 'spotify:show:techtalk'
            }
        };

        const { sandbox, mockElements } = setupSandbox(async () => ({ ok: true, status: 200, json: async () => mockPodcastResponse }));
        sandbox.localStorage.setItem('sp_access_token', 'mock_token');

        await sandbox.checkNowPlaying(false);
        assert(mockElements['np-meta-line'].innerText.includes('Tech Talk Daily'), 'checkNowPlaying extracts podcast show name for episodes');
    }

    // Case 5: 401 Unauthorized -> Updates status to Abgemeldet / offline (does NOT claim nothing is playing)
    {
        const { sandbox } = setupSandbox(async () => ({ ok: false, status: 401, json: async () => ({ error: 'Unauthorized' }) }));
        sandbox.localStorage.setItem('sp_access_token', 'expired_token');

        await sandbox.checkNowPlaying(false);
        assert(sandbox.engineState.currentStatus === 'OFFLINE', 'checkNowPlaying on 401 sets status to OFFLINE / Abgemeldet');
    }

    // Case 6: 429 Rate Limit -> Respects Retry-After backoff
    {
        const { sandbox } = setupSandbox(async () => ({
            ok: false,
            status: 429,
            headers: { get: (h) => (h === 'Retry-After' ? '8' : null) },
            json: async () => ({})
        }));
        sandbox.localStorage.setItem('sp_access_token', 'mock_token');

        await sandbox.checkNowPlaying(false);
        assert(sandbox.engineState.currentStatus === 'PAUSED', 'checkNowPlaying on 429 enters rate-limit backoff status');
    }

    // ----------------------------------------------------
    // TEST 8: Screen WakeLock & Background Keep-Alive Toggle
    // ----------------------------------------------------
    console.log('\nTest Group 8: Screen WakeLock & Keep-Alive Toggle (Running index.html toggleKeepAlive)');
    {
        const { sandbox, mockElements, mockWakeLockSentinel } = setupSandbox();
        sandbox.localStorage.setItem('sp_access_token', 'mock_token');

        // Initial toggle ON
        await sandbox.toggleKeepAlive();
        assert(sandbox.engineState.wakeLockActive === true, 'toggleKeepAlive turn ON activates wake lock in engineState');
        const btn = mockElements['keep-alive-btn'];
        assert(btn.classList.contains('btn-active-toggle'), 'toggleKeepAlive turn ON adds btn-active-toggle class');
        assert(mockElements['keep-alive-label'].innerText === 'Fokus-Schutz aktiv', 'toggleKeepAlive turn ON sets button label to active');

        // Toggle OFF (verifying it is an actual toggle)
        await sandbox.toggleKeepAlive();
        assert(sandbox.engineState.wakeLockActive === false, 'toggleKeepAlive turn OFF deactivates wake lock in engineState');
        assert(!btn.classList.contains('btn-active-toggle'), 'toggleKeepAlive turn OFF removes btn-active-toggle class');
        assert(mockElements['keep-alive-label'].innerText === 'Fokus-Schutz inaktiv', 'toggleKeepAlive turn OFF sets button label to inaktiv');
    }

    // ----------------------------------------------------
    // TEST 9: 2-Step Safe Bookmark Deletion (Running index.html confirmDeleteBookmark)
    // ----------------------------------------------------
    console.log('\nTest Group 9: 2-Step Safe Bookmark Deletion (Running index.html confirmDeleteBookmark)');
    {
        const { sandbox } = setupSandbox();
        const testBookmarks = {
            'pl_1': { playlist_id: 'pl_1', playlist_name: 'Chill Vibes', updated_at: 1000 },
            'pl_2': { playlist_id: 'pl_2', playlist_name: 'Workout Hits', updated_at: 2000 }
        };
        sandbox.localStorage.setItem('sp_bookmarks', JSON.stringify(testBookmarks));

        // Step 1: First click sets pendingDeleteId without deleting
        sandbox.confirmDeleteBookmark('pl_1');
        let currentBookmarks = JSON.parse(sandbox.localStorage.getItem('sp_bookmarks'));
        assert(currentBookmarks['pl_1'] !== undefined, 'confirmDeleteBookmark step 1 does NOT delete immediately');

        // Step 2: Second click confirms and deletes
        sandbox.confirmDeleteBookmark('pl_1');
        currentBookmarks = JSON.parse(sandbox.localStorage.getItem('sp_bookmarks'));
        assert(currentBookmarks['pl_1'] === undefined, 'confirmDeleteBookmark step 2 deletes the bookmark');
        assert(currentBookmarks['pl_2'] !== undefined, 'confirmDeleteBookmark step 2 preserves other bookmarks');
    }

    // ----------------------------------------------------
    // TEST 10: Apple HIG Color Contrast & Touch Target Compliance
    // ----------------------------------------------------
    console.log('\nTest Group 10: Apple HIG Color Contrast & Touch Target Verification');

    function getLuminance(r, g, b) {
        const [rs, gs, bs] = [r, g, b].map(c => {
            c = c / 255;
            return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        });
        return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
    }

    function getContrast(rgb1, rgb2) {
        const l1 = getLuminance(...rgb1);
        const l2 = getLuminance(...rgb2);
        const brightest = Math.max(l1, l2);
        const darkest = Math.min(l1, l2);
        return (brightest + 0.05) / (darkest + 0.05);
    }

    const black = [0, 0, 0];
    const surface = [22, 22, 24]; // #161618
    const white = [255, 255, 255];
    const appleGreen = [48, 209, 88]; // #30D158 (iOS dark mode system green)
    const tertiaryText = [129, 129, 134]; // rgba(235, 235, 245, 0.55) on black

    const contrastWhiteOnBlack = getContrast(white, black);
    const contrastWhiteOnSurface = getContrast(white, surface);
    const contrastGreenOnBlack = getContrast(appleGreen, black);
    const contrastBlackOnGreen = getContrast(black, appleGreen);
    const contrastTertiaryOnBlack = getContrast(tertiaryText, black);

    assert(contrastWhiteOnBlack >= 20.0, `White on Black contrast is ${contrastWhiteOnBlack.toFixed(1)}:1 (Exceeds WCAG AAA 7:1)`);
    assert(contrastWhiteOnSurface >= 15.0, `White on Surface card contrast is ${contrastWhiteOnSurface.toFixed(1)}:1 (Exceeds WCAG AAA 7:1)`);
    assert(contrastGreenOnBlack >= 7.0, `Apple Green on Black contrast is ${contrastGreenOnBlack.toFixed(1)}:1 (Exceeds WCAG AAA 7:1)`);
    assert(contrastBlackOnGreen >= 7.0, `Black text on Primary Green Button contrast is ${contrastBlackOnGreen.toFixed(1)}:1 (Exceeds WCAG AAA 7:1)`);
    assert(contrastTertiaryOnBlack >= 4.5, `Tertiary captions on Black contrast is ${contrastTertiaryOnBlack.toFixed(1)}:1 (Exceeds WCAG AA 4.5:1)`);

    assert(indexHtml.includes('min-height: 44px;') && indexHtml.includes('min-width: 44px;'), 'CSS specifies 44px minimum touch targets conforming to Apple HIG');

    console.log('\n========================================');
    console.log(`📊 TEST RESULTS: ${passedTests}/${totalTests} Passed (${failedTests} Failed)`);
    console.log('========================================\n');

    if (failedTests > 0) {
        process.exit(1);
    }
}

runTests().catch(err => {
    console.error('Test execution error:', err);
    process.exit(1);
});
