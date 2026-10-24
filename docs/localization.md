# Languages and app coverage

## Where things stand (0.6.8)

| Part | English | Português (Brasil) | Others |
|---|---|---|---|
| Interface (`res/values*/strings.xml`) | yes | yes (`values-pt-rBR`) | not yet |
| Search commands (`search/Commands.kt`) | yes | yes ("ligar pra", "zap", "mensagem pra", "tocar", "como chegar", "temporizador", "despertador") | no |
| Settings pages keywords (`search/SettingsCatalog.kt`) | yes | yes (accent-free) | no |
| Shortcut words (`Commands.SYNONYMS`) | yes | some ("storys", "mensagens", "postar", "escanear", "pagar") | no |

Android picks the interface language from the phone's language. Commands do not depend on it:
every command language works on every phone (an English phone still understands "ligar pra
ana"), because people often type in a mix.

## Adding a language

1. **Interface:** copy `values-pt-rBR/strings.xml` to `values-<lang>/strings.xml` and translate.
   Keep every name and placeholder (`%1$s`, `%1$d`). Plurals need the language's quantities
   (`one`, `few`, `many`, `other`). Lint's `MissingTranslation` check fails the build when a
   string is missing, so nothing ships half translated.
2. **Commands:** add the language's verbs to the regexes in `Commands.kt` (call, message, play,
   directions, timer, alarm, open, and the "on <app>" words) and a test line per verb in
   `CommandsTest`. Watch for short words that clash with names or apps (Spanish "a", "en").
3. **Keywords:** append the language's words to each `SettingPage` and to `SYNONYMS`.

Suggested next languages, by how many people use them on Android: Spanish (shares most
command shapes with Portuguese), Hindi, Indonesian, French, German.

A cleaner structure once a third language arrives: move the command words, settings keywords
and synonyms from Kotlin into per-language resources (string arrays in `values-<lang>/`) and read
all languages at start. Not done yet: with two languages the Kotlin lists are easier to test.

## App coverage (`search/AppLinks.kt`)

Only links apps document for other apps are used, each sent to that app's package, and offered
only when the app is installed:

| App | Typed | Link |
|---|---|---|
| Uber | `uber <address>`, or any address | Universal link with the destination as typed |
| Waze | `waze <address>`, or any address | `waze.com/ul?q=…&navigate=yes` (starts the drive) |
| Google Maps | `maps <address>`, any address, `directions to …` | `google.navigation:q=…` and the directions link |
| Spotify | `spotify <song>`, `play … on spotify` | `spotify:search:…` (and the media "play from search" request) |
| YouTube, YouTube Music | `youtube <video>` | the apps' search links |
| Any app | `<thing> on <app>`, `<app> <thing>` | its shortcuts, its chats with a contact, its own search, play from search |

Known limits:
- Uber's link takes coordinates for an exact pin; the launcher has no network to look the
  address up (Android's address lookup sends it to a server), so Uber gets the text and finds
  it itself. If Uber asks to confirm the destination, that is why.
- 99, iFood, Mercado Livre and banks have no documented public links; they are reached through
  their own shortcuts and search, when they offer them.

Candidates to add: Google Translate (`translate <text>`), Calendar (new event), Gmail compose
(`email <address> <subject>`), Netflix/Prime search (when their links are confirmed on a phone).
