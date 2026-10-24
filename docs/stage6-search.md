# Stage 6: Canyon search

A swipe up on the home screen used to hand over to another app's search (chosen in Settings). The
maker's search worked on the Galaxy A03 but not on the A54, so Canyon UI now has a search of its own
and depends on no other app for it. A chosen search app stays available as an option.

Rules carried over from the spec: on-device only (the launcher has no network access and gets none
for this), no hidden APIs, no system permissions, public APIs only, the phone's font, Phosphor
Regular icons (Fill for a selected state), the one interface border, glass/frosted by the
Transparency setting.

## OKRs

**O1. Swipe up always finds things, on every phone, without another app.**
- KR1: In home-screen-only mode a swipe up opens Canyon search, with the keyboard up, on the A03
  and the A54. The field follows the finger like the Apps screen does.
- KR2: The manifest gains no `INTERNET` permission; every result is computed on the phone.
- KR3: Apps update in the same frame as each key press; every other kind of result appears within
  about 150 ms on the A03.

**O2. Find more than apps.**
- KR1: Seven kinds of result: apps, app shortcuts, contacts (when allowed), settings pages (at least
  25), a calculator, Canyon UI's own settings, and hand-offs (web, app store, maps, call, email,
  open a link).
- KR2: The keyboard's Go key opens the top result.
- KR3: An empty search shows suggested apps (most used) and recent searches, which can be cleared.

**O3. Same quality as the rest of the launcher.**
- KR1: Lint 0, unit tests for the calculator, the query detection and the ranking.
- KR2: The same results in the Apps screen's search (drawer mode) as in the swipe-up search.

## Backlog

| # | Item | Plan | Status |
|---|---|---|---|
| 1 | Canyon search as the default swipe-up action in home-screen-only mode; "Search app" setting keeps the old choice (any app or a typed package) | 0.6.0 | Done |
| 2 | Search screen: the Apps screen with its search field focused; empty query shows suggestions and recent searches instead of the full list | 0.6.0 | Done |
| 3 | Apps: existing instant matching (prefix, word start, contains; accents ignored); hidden apps stay hidden | 0.6.0 | Done |
| 4 | App shortcuts (`LauncherApps.getShortcuts`, available to the default home app): "New message", "Scan QR code"... | 0.6.0 | Done |
| 5 | Contacts (`READ_CONTACTS`, a normal runtime permission the user grants from the results; off until allowed): opens the contact | 0.6.0 | Done |
| 6 | Settings pages through public `android.provider.Settings` actions, with keywords ("brightness" finds Display) | 0.6.0 | Done |
| 7 | Calculator: + − × ÷ % ^ and brackets; tap copies the result | 0.6.0 | Done |
| 8 | Hand-offs: search the web (`ACTION_WEB_SEARCH`, answered by the user's browser or search app), search the app store (`market://search`), maps (`geo:`), call a number (`ACTION_DIAL`, no call permission), email an address, open a link | 0.6.0 | Done |
| 9 | Recent searches (kept on the phone, last 8, "Clear") | 0.6.0 | Done |
| 10 | Same extra results under the apps in the Apps screen's search | 0.6.0 | Done |
| 11 | Go opens the top result | 0.6.0 | Done |
| 16 | Commands, English and Portuguese, any case: `call ana` (Go places the call; the call permission is asked once, otherwise the dialer opens), `whatsapp call ana`, `video call ana`, `message ana` / `zap ana` / `text ana`, `email ana`, `play … on spotify`, `directions to …`, `timer 5 min`, `alarm 7:30`, `open …` | 0.6.1 | Done |
| 17 | A person's name alone: "Message <name>" first (WhatsApp or another chat app the contact has, else SMS), when no app's name starts with the text or the text is the person's first name | 0.6.1 | Done |
| 18 | Contacts with message, call and video buttons, through the entries chat apps add to contacts (WhatsApp, Telegram...) | 0.6.1 | Done |
| 19 | `<thing> on <app>` and `<app> <thing>`: that app's shortcuts with everyday words ("stories" finds Instagram's camera), its chats with a person, its own search, playing in it | 0.6.1 | Done |
| 20 | Addresses ("Rua Augusta, 1500", "Av. Paulista 1000", "1600 Amphitheatre Parkway", CEP/ZIP): Open in Maps and Directions. No map picture: it needs map tiles from the internet | 0.6.1 | Done |
| 12 | Typo tolerance ("whatsap", "camrea") | Later | |
| 13 | Calendar events (`READ_CALENDAR`, opt-in like contacts) | Later | |
| 14 | Files and photos (MediaStore with the media permissions already used by the wallpaper chooser) | Later | |
| 15 | Unit conversion and currency | Later (units only: currency rates need the network, which the launcher does not use) | |

**Not possible for an ordinary app** (stated plainly, per the spec):
- Searching inside the system Settings' own index (the deep "search settings" of the Settings app)
  is privileged. The closest public alternative is item 6: a list of public settings pages.
- Searching text messages needs the default SMS app role. Not planned.
- Searching inside other apps' content (notes, mail) is only possible for data those apps choose to
  share with every app; no common public way exists. Not planned.
- WhatsApp chats with people who are not saved contacts, groups and messages stay inside
  WhatsApp. Its recent chats still show when WhatsApp offers them as app shortcuts.
- A map preview of an address needs map tiles from the internet. The address opens in the maps app instead.
- Online suggestions while typing need network access, which the launcher does not have. A web
  search is handed to the user's browser instead (item 8).
