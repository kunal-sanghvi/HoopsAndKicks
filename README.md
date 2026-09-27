# Hoops & Kicks – Basketball & Football Tournament Manager (Android)

Native Android app (Kotlin + Jetpack Compose + Material 3). Everything is stored locally on the device
(one JSON file per tournament in the app's private storage). No accounts needed; the app works fully offline.
Live hosting (viewers watching on their own phones, or in any browser via the [web viewer](#web-viewer-browser-and-ios)) is an optional extra, see below.

> **Build note:** the app module applies the Firebase `google-services` plugin, so **the build fails until
> `app/google-services.json` exists**. That is expected, not a bug: see "Hosting a tournament live (optional)".

## Open and run
1. Install Android Studio (Ladybug or newer) with an Android SDK (API 35).
2. `File > Open` this folder and let Gradle sync (Android Studio downloads Gradle 8.10.2 and dependencies).
3. Pick an emulator or a phone (Android 8.0+, API 26) and press Run.
4. To produce an installable APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
5. Unit tests for the scheduling, standings, event-log and clock logic: right-click `app/src/test` > Run Tests.

## What is in the app
| Feature | Where |
|---|---|
| Create tournament, X players, Y teams | `ui/SetupScreens.kt` |
| Split players (random, balanced by skill, manual) | `SplitScreen`, `data/Scheduler.kt` |
| Schedule algorithms: Group Stage → Semis → Finals (default), Round Robin, Single/Double Elimination, Swiss | `data/Scheduler.kt`, `ui/PlanScreens.kt` |
| Game format: continuous or two halves + break, all configurable | `GameFormatScreen` |
| Sports: basketball or football (terminology, default format, tie-breaker names) | `Sport` in `data/Model.kt` |
| Live match: clock, score, baskets/goals credited to players, substitutions, undo | `ui/MatchScreens.kt`, `ui/LiveViewModel.kt` |
| Tie-breaker (fixed, no overtime): free-throw shootout (basketball, 3 each) / free-kick shootout (football, 5 each), alternating, then sudden death; each attempt logged with shooter | `TieBreakerContent`, `data/Shootout.kt` |
| Match event log (append-only) and replay | `data/MatchLog.kt` |
| Optional live hosting: room code, read-only viewers | `data/remote/RemoteSync.kt`, `firestore.rules` |
| Web viewer for iOS and any browser (GitHub Pages, read-only) | `docs/`, tests in `web-tests/` |
| Leaderboards: per stage, overall across stages, player top scorers | `ui/LeaderboardScreens.kt`, `Leaderboards` in `data/Scheduler.kt` |
| Match times and courts, enforced end time (no match planned past it can start) | `scheduleTimes`/`fits` in `data/Scheduler.kt`, `ScheduleFixActions` in `ui/TournamentScreens.kt` |

## Rules used
- Points per win / tie / loss are configurable (default 2 / 1 / 0) and only awarded in group and league stages. A level group or league match is a draw; a level knockout, semi-final or final always goes to the shootout tie-breaker.
- Ranking: points, then head-to-head (two-way ties only), then point difference, then points scored.
- Group stage: top N per group advance (configurable). Knockout rounds are drawn automatically when the previous round finishes; byes are given to top seeds.
- Double elimination pairs teams by number of losses (0-loss with 0-loss, 1-loss with 1-loss); a team is out at 2 losses.
- Swiss plays ceil(log2(teams)) rounds, pairing by current standings and avoiding rematches.

## Match times and courts
When creating a tournament you pick when it starts and when your venue booking ends (date + time pickers),
how many courts/pitches can run games at once, and the changeover minutes between matches on the same court.
Match start times and court numbers are **computed automatically, never entered by hand**:
`Scheduler.scheduleTimes` re-slots every match (in stage → round → match-number order) onto whichever court
is available first, using each game's estimated length (periods + breaks; shootout tie-breakers are not
predictable and are ignored) plus the changeover buffer. A stage never starts early on a spare court: every
match of the next stage (knockout round; for Swiss and double elimination, the next round) is slotted after
the last match of the previous stage has ended, on every court. It runs when the schedule is generated, after
every round is drawn, when the game format is saved, when a schedule fix is applied, and when a saved
tournament is loaded. Fixtures and the "Up next" card show e.g. "Match 3 · Court 2 · 10:40 AM". Byes get no slot.

**Matches cannot go beyond the end time.** The app can't stop a match that is already being played, so the
rule is enforced on the plan:
- *Creating a tournament:* "Save & finish" stays disabled while the whole tournament, played through every
  stage (later stages are projected with placeholder results, `Scheduler.projectedSchedule`), would end after
  the end time. An inline error says by how much and offers the fixes below; it re-checks live as you adjust them.
- *Starting a match:* a match whose planned end (start + estimated length, `Match.exceedsWindow`) is after the
  end time is locked everywhere it could be started (Fixtures card, "Up next" card, match-ready screen) and shows
  "Exceeds tournament end time" with a **Fix schedule** shortcut.
- *During the tournament:* newly drawn rounds are always created (the bracket must exist), but while any match
  doesn't fit (`Scheduler.fits`) the hub shows a banner that can't be dismissed, with how many minutes over and
  how many matches are locked, until the schedule fits again.

The fixes are the same everywhere: extend the end time (one tap to the earliest end that fits, or pick any
date/time) or add a court; each re-slots every match and saves immediately. Changing the game format to shorter
games also re-slots. Because estimates exclude tie-breakers, a day that runs late in reality is not detected; only
the planned schedule is checked. Tournaments saved before this feature load with no start/end time (nothing is
slotted or checked), 1 court and a 10 minute buffer.

## Match event log
Every live-match action (start, pause/resume, score, undo, substitution, shootout attempt, break, period start,
tie-breaker, end, reopen) also appends a `MatchEvent` to `Match.log`. The log is append-only: an undo
never deletes anything, it appends a `VOID` whose `voidsSeq` points at the cancelled `SCORE`. Each event
has a unique `id` and a strictly increasing `seq`. The local screens still read the regular match fields
(scores, `events`, lineups), and the leaderboards still read `Match.events`; the log is a side record
used for live sync and by viewers to rebuild the match (`MatchLog.replay`).

## Clock formula
Clock-changing events store the clock value at that moment (`clockSec`), and the match stores
`clockRunning` + `clockEpochMs` next to `remainingSec`/`breakRemainingSec`. Anyone can then show the clock as

```
displaySeconds = if (running) max(0, valueAtChange - (nowEpochMs - changedAtEpochMs) / 1000) else valueAtChange
```

(`computeDisplaySeconds` in `data/MatchLog.kt`; negative elapsed time from a viewer clock that is behind the host's counts as 0.)

## Hosting a tournament live (optional)
From a tournament's `⋮` menu choose **Go live** to get a 6-character room code; others tap **Join** on
the home screen and watch the hub, fixtures and leaderboards update live (read-only). Only the hosting
phone can change anything.

A viewer's room code is remembered on their device (`data/remote/ViewerPrefs.kt`, backed by
`SharedPreferences`), not just held in memory. Pressing back, or closing the app entirely, disconnects the
live listener but keeps the code — the home screen then offers **Resume watching room XXXXXX** instead of
the "Join a tournament" button, so the viewer never has to re-type it. The code is only forgotten once the
room turns out to be gone (organiser stopped hosting, or a stale/invalid code) or the viewer taps "Join a
different room".

Two one-time steps only you can do (the assistant will also walk you through them):

1. Create a free Firebase project, add an Android app with package `com.hoopsandkicks.tournament`, and put the
   generated `google-services.json` into `app/`.
2. In that project's console, enable **Authentication > Sign-in method > Anonymous** and create a
   **Firestore** database, then paste the contents of `firestore.rules` into Firestore's **Rules** tab and publish.

Step 1 is needed for the project to build at all. Without step 2 (or with no network), Go live / Join just
show an error and everything else keeps working offline.

## Web viewer (browser and iOS)

The app is Android-only, so viewers on iPhones (or anything with a browser) use a read-only web page instead. It lives in
`docs/`, is plain HTML/CSS/JS with no build step, and reads the same Firestore room the Android viewer does: the hub with
the live match and clock, fixtures, standings, teams and top scorers, including live shootouts. A match feed (scorers with the
running score, substitutions, half-time, shootout attempts, full time) shows the latest five on the live card, and the
whole timeline when a fixture card is tapped. It works on phone screens
and follows the system dark mode. Open `https://<your-github-user>.github.io/<repo>/`, type the 6-character room code,
or share a direct link: `https://<your-github-user>.github.io/<repo>/?room=ABC234`.

| File | What it does |
|---|---|
| `docs/js/model.js` | Pure port of the Android viewer logic: event replay (`MatchLog`), clock formula, shootout, standings, leaderboards. No DOM or Firebase, so it is unit-tested in Node. |
| `docs/js/backend.js` | The only file that talks to Firestore (the Firebase web SDK is loaded from Google's CDN). |
| `docs/js/app.js` | Rendering and navigation. Everything shown goes through an escaping `html` template tag. |
| `docs/js/config.js` | The Firebase web config. |

Keep `model.js` in step with the Kotlin it mirrors (each section names its source file) when the Android rules change.
Run its tests with `node --test web-tests/model.test.mjs`.

To read fewer documents than the Android viewer, the page only listens to the event log of matches that are in progress;
a finished match's result already comes from the room snapshot, and its event log (for the match feed) is fetched once, when
its card is tapped. Until a log arrives, the feed falls back to the scorers in the room snapshot.

One-time setup (the app itself needs none of this):

1. **Firebase console:** Project settings > Your apps > Add app > Web, and paste the config into `docs/js/config.js`.
   Nothing else is needed there: the page reads without signing in, so Authentication and authorized domains are not involved.
2. **Publish `firestore.rules`** again. It now lets anyone with a code read one room (`get`) but not list all rooms.
3. **Google Cloud Console > APIs & Services > Credentials:** restrict the web API key to HTTP referrers
   `https://<your-github-user>.github.io/*` and to the Cloud Firestore API. Use a separate key from the Android one
   (a referrer restriction would break the app). Do not add a `Referrer-Policy: no-referrer`, the restriction needs the referrer.
4. **Google Cloud Billing:** add a budget alert. Do not enforce App Check on Firestore: F-Droid builds cannot pass it.
5. **GitHub:** Settings > Pages > Deploy from a branch > `main` / `docs`.

The Firebase web config is public by design, and so is this repo: what protects the data is the rules file (read one room by
code, only the host writes) plus the key restriction and budget alert above.

## Notes
- Fonts use the system condensed face. To use Barlow Condensed, add it under `res/font` and point `DisplayFont` in `ui/Theme.kt` to it.
- Storage is deliberately simple (JSON via `org.json`, see `data/Repository.kt`). Swapping in Room later only touches `Repository.kt`.
