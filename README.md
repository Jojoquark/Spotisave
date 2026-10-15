# Spotisave

Spotisave ist eine moderne Progressive Web App (PWA) zur automatischen Erfassung und 1-Click-Wiederaufnahme von Spotify-Playlisten und Hörpositionen nach **Apple Human Interface Guidelines (HIG)**.

## Highlights & Features

- **Apple HIG Dark Mode Ästhetik & Interaktion**:
  - Deep OLED Black (`#000000`), abgesetzte Inset-Cards (`#161618`) und Hairline-Borders (`1px solid rgba(255, 255, 255, 0.08)`).
  - Apple SF Pro Typografie-Hierarchie mit Tabular-Figures für exakte Zeitangaben.
  - Floating Functional Glass Header mit `backdrop-filter: blur(20px) saturate(180%)`.
  - Swipe-down-to-dismiss Touch-Geste auf allen Bottom Sheets (iOS Sheet-Verhalten) und Escape-Taste auf Desktop.
  - Animierte Equalizer-Wellen und pulsierender Live-Status-Dot.
  - Taptic-Engine Haptik-Simulation (`navigator.vibrate`) bei Klicks & Aktionen.
  - 44px Mindestgröße für berührungssensitive Bedienelemente (Touch Targets) ohne künstliche Skalierungsverbote (`viewport-fit=cover`).
  - Vollständige Barrierefreiheit für `prefers-reduced-motion` und `prefers-contrast: more` (Kontrast > 5:1 selbst für kleinste Beschriftungen).

- **Hintergrund-Synchronisation & Worker-Tracking**:
  - Dedizierter Web-Worker (5-Sekunden-Takt) gegen Browser-Throttling.
  - Echter 2-Wege Fokus-Schutz-Toggle (Aktivieren / Deaktivieren von Screen WakeLock mit dynamischem Button-Status).
  - Pausen-Schutz: Sichert Hörpositionen selbst bei pausierter Wiedergabe, sodass keine Positionen verloren gehen.
  - Chromium Web Locks (`spotify_saver_background_lock`) & Screen WakeLock Integration.
  - Status-Inspektor (Modal-Sheet) mit Live-Kennzahlen (Worker-Status, Ticks, letzter Sync, WakeLock).
  - Service Worker (`sw.js` v2) mit Cache-Invalidierung, `ignoreSearch` Offline-Navigation und API-Request-Filterung.

- **Intelligente 1-Click Resume Fallback Engine**:
  - 1. Versuch: Direkte Wiedergabe über Spotify Web-API (`/me/player/play`).
  - 2. Versuch: Automatische Geräteerkennung bei 404 (suche aktive/verfügbare Spotify Connect Geräte).
  - 3. Versuch: Direkter Track-URI Fallback bei 400 (behält ermitteltes Zielgerät bei).
  - 4. Versuch: Apple Action Sheet mit 1-Click Deep Link zur nativen Spotify App (`spotify:track:...`) oder Web Player (`https://open.spotify.com/...`).

## Automatisierte Tests ausführen

Zur Überprüfung von Syntax, PKCE-Kryptografie (RFC 7636), Hintergrund-Loop, 1-Click Fallback-Entscheidungsbaum, Safe-Delete, Screen WakeLock und Apple HIG Farbkontrasten (70 Tests in 10 Testgruppen):

```bash
node test/verify.js
```