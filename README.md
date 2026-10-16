# Spotisave

Spotisave ist eine 100% native Android App (Kotlin + Jetpack Compose) sowie Progressive Web App (PWA) zur automatischen Erfassung und 1-Click-Wiederaufnahme von Spotify-Playlisten und Hörpositionen nach **Apple Human Interface Guidelines (HIG)** und SwiftUI-inspirierten deklarativen Design-Prinzipien.

## Native Android App (Kotlin + Jetpack Compose)

Die native Android App verbindet lokale Android-Systemüberwachung (Broadcasts & MediaSession) mit der offiziellen **Spotify Web API im Hybrid-Modus**:

- **Hybrid-Modus & Spotify Web API Integration**:
  - **Spotify PKCE OAuth (RFC 7636)**: Sichere browser-basierte Authentifizierung mit individueller Client ID ohne Client Secret über das Redirect-Schema `spotisave://callback`.
  - **Präzises Playlist-Tracking**: Synchronisiert im Hintergrund `/me/player`, um exakte Playlist-URIs (`context.uri`), Titel, Album-Cover und Wiedergabepositionen abzugleichen.
  - **1-Click Smart Resume**: Sendet `PUT /me/player/play` mit `context_uri`, `offset: { uri: track_uri }` und `position_ms`, um direkt an der exakten Stelle **innerhalb der Playlist** fortzusetzen. Inklusive intelligentem Fallback auf aktive Geräte (404-Handling), Einzeltitel (400-Handling) sowie native Spotify-Intents und MediaController.
- **Apple-Style Bottom Navigation Bar**:
  - Frosted Translucent Glass Bar (`Color(0xF0101012)`) mit 0.5dp `HairlineBorder` und zwei Haupt-Tabs:
    1. **Playlists**: Top Hero Card ("Zuletzt gehört") mit pulsierendem Live-Indikator, Fortschrittsbalken und Liste aller gespeicherten Playlist-Lesezeichen.
    2. **Statistiken**: Umfassendes Hörgewohnheiten- und Analytics-Dashboard.
- **Statistiken & Einblicke**:
  - **Gesamte Hörzeit**: Aggregierte Wiedergabezeit in Stunden und Minuten.
  - **Playlists & Alben**: Anzahl aktiv getrackter Playlists.
  - **Top-Playlist**: Meistgehörte Playlist mit Fortschritt und direktem 1-Click "Fortsetzen"-Button.
  - **Lieblings-Album**: Meistgehörtes Album mit Cover-Artwork, Titelanzahl und aggregierter Gesamthörzeit.
  - **Meistgehörte Songs (Top-Songs)**: Dynamisches Ranking der beliebtesten Tracks inklusive Hördauer und 1-Click Fortsetzen.
  - **Genaue Historie**: Chronologischer Verlauf der zuletzt gehörten Titel mit relativen Zeitstempeln ("vor X Min.", "Gestern"), Kontext und Sofort-Play.
  - **Track-Anzahl & Ø Song-Dauer**: Präzise KPI-Kennzahlen über das Hörverhalten.
  - **Wöchentliche Aktivität**: Interaktives 7-Tage-Balkendiagramm (Montag bis Sonntag) mit normalisierter Aktivitätshöhe.
  - **Top-Interpreten**: Ranking der Top 5 Interpreten mit prozentualer Verteilung.
  - **Hör-Persönlichkeit & Primäre Tageszeit**: Automatische Klassifizierung des Hör-Typs ("Nachteule 🦉", "Morgenmensch 🌅", "Fokus-Nachmittag ⚡️", "Abend-Chiller 🌙") mit aktivstem Zeitfenster.
  - **Durchhör-Quote (Completion Rate)**: Aggregierte Durchhör-Rate in Prozent mit Profilierung ("Fokus-Durchhörer 🎧", "Playlist-Explorer 🚀", "Track-Skipper ⚡️").
  - **Aktivster Wochentag**: Erkennung des musikalischen Spitzentags der Woche mit absoluter Hörzeit.
- **Robuste Hintergrund-Architektur (Android 8–14+)**:
  - `SpotifyBroadcastReceiver`: Reagiert auf Spotify-System-Broadcasts (`com.spotify.music.metadatachanged`, `com.spotify.music.playbackstatechanged`, `com.spotify.music.queuechanged`).
  - `SpotifyForegroundService`: Hält den Tracking-Prozess bei gesperrtem Bildschirm aktiv, registriert den Receiver dynamisch und schützt vor aggressiven OEM-Taskkillern.
  - `SpotifyNotificationListenerService`: Systemweiter Fallback, der Spotify-MediaSessions direkt liest und PlaybackState/Metadata auswertet – vollständig ausgenommen von Android Doze / Standby-Limits.
  - `BookmarkDatabaseHelper` & `BookmarkRepository`: ACID-konforme SQLite-Persistierung mit reaktiven `StateFlow`-Streams für die Compose-UI.
- **Apple HIG UI / Jetpack Compose Ästhetik**:
  - Deep OLED Canvas (`#000000`), abgesetzte Inset-Cards (`#161618`) und Hairline-Borders (`1.dp solid rgba(255, 255, 255, 0.12)`).
  - Mindestens 44dp Berührungsflächen (Touch Targets) für alle Steuerelemente.
  - Status & Diagnose Bottom Sheet zur Verwaltung des Dienstes und Systemberechtigungen.
- **Cloud CI / GitHub Actions**:
  - Automatisierter Workflow `.github/workflows/build-apk.yml` baut auf Push/PR via `./gradlew testDebugUnitTest assembleDebug` und lädt die signierbare Debug-APK als Artefakt hoch.

## Web PWA Fallback

Die ursprüngliche Web PWA bleibt im Repository als leichtgewichtiger Fallback erhalten:
- Single-File Architektur (`index.html`) mit Service Worker (`sw.js`).
- PKCE OAuth 2.0 Authentifizierung (RFC 7636).
- Web Worker & Chromium Web Locks für Browser-basierte Sitzungen.

## Automatisierte Tests ausführen

Zur Überprüfung von nativer Android-Architektur, Manifest, Gradle-Konfigurationen, Kotlin-Modellen, PKCE-Kryptografie, Spotify Web API Parsing, Resume-Payloads, StatisticsEngine (Lieblings-Album, Top-Songs, Historie), Playlist-Tracking, Apple HIG Farbkontrasten und nativer MediaSession-Simulation (204 Tests in 14 Testgruppen):

```bash
node test/verify.js
```