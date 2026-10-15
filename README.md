# Spotisave

Spotisave ist eine 100% native Android App (Kotlin + Jetpack Compose) sowie Progressive Web App (PWA) zur automatischen Erfassung und 1-Click-Wiederaufnahme von Spotify-Playlisten und Hörpositionen nach **Apple Human Interface Guidelines (HIG)** und SwiftUI-inspirierten deklarativen Design-Prinzipien.

## Native Android App (Kotlin + Jetpack Compose)

Die native Android App löst das Problem des Browser-Throttlings und ermöglicht zuverlässige Hintergrund-Synchronisation selbst bei ausgeschaltetem/gesperrtem Bildschirm in der Hosentasche:

- **Robuste Hintergrund-Architektur (Android 8–14+)**:
  - `SpotifyBroadcastReceiver`: Reagiert auf Spotify-System-Broadcasts (`com.spotify.music.metadatachanged`, `com.spotify.music.playbackstatechanged`, `com.spotify.music.queuechanged`).
  - `SpotifyForegroundService`: Hält den Tracking-Prozess bei gesperrtem Bildschirm aktiv, registriert den Receiver dynamisch und schützt vor aggressiven OEM-Taskkillern.
  - `SpotifyNotificationListenerService`: Systemweiter Fallback, der Spotify-MediaSessions direkt liest und PlaybackState/Metadata auswertet – vollständig ausgenommen von Android Doze / Standby-Limits.
  - `BookmarkDatabaseHelper` & `BookmarkRepository`: ACID-konforme SQLite-Persistierung mit reaktiven `StateFlow`-Streams für die Compose-UI.
- **Apple HIG UI / Jetpack Compose Ästhetik**:
  - Deep OLED Canvas (`#000000`), abgesetzte Inset-Cards (`#161618`) und Hairline-Borders (`1.dp solid rgba(255, 255, 255, 0.12)`).
  - Top Hero Card ("Zuletzt gehört") mit pulsierendem Live-Statusindikator, Fortschrittsbalken und prominentem 52dp 1-Click "Fortfahren"-Button.
  - Gespeicherte Lesezeichen-Karten mit relativem Zeitstempel, Fortschrittsbalken und 2-stufiger Löschbestätigung.
  - Mindestens 44dp Berührungsflächen (Touch Targets) für alle Steuerelemente.
  - Status & Diagnose Bottom Sheet zur Verwaltung des Dienstes und Systemberechtigungen.
- **1-Click Resume**:
  - Direkte Wiederaufnahme über native Spotify-Intents (`spotify:track:...` / `spotify:playlist:...`) mit automatischer MediaController Transport-Ansteuerung und Web-Fallback.
- **Cloud CI / GitHub Actions**:
  - Automatisierter Workflow `.github/workflows/build-apk.yml` baut auf Push/PR via `./gradlew testDebugUnitTest assembleDebug` und lädt die signierbare Debug-APK als Artefakt hoch.

## Web PWA Fallback

Die ursprüngliche Web PWA bleibt im Repository als leichtgewichtiger Fallback erhalten:
- Single-File Architektur (`index.html`) mit Service Worker (`sw.js`).
- PKCE OAuth 2.0 Authentifizierung (RFC 7636).
- Web Worker & Chromium Web Locks für Browser-basierte Sitzungen.

## Automatisierte Tests ausführen

Zur Überprüfung von nativer Android-Architektur, Manifest, Gradle-Konfigurationen, Kotlin-Modellen, HTML/JS-Syntax, PKCE-Kryptografie, Fallback-Entscheidungsbaum, Apple HIG Farbkontrasten und nativer MediaSession/Background-Simulation (139 Tests in 13 Testgruppen):

```bash
node test/verify.js
```