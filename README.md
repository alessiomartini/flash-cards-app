# EngVocab

An Android flashcard app for growing your vocabulary - words, phrasal verbs, idioms, and
expressions - in English, German, French, or Dutch, translated to Italian. No ads, no card
limits, local, free, open source. Switch which language you're studying from a chip on the
Home screen (or in Settings); Home/Study/Cards all scope to whichever language is selected.

## Installing it on your phone (no Android Studio needed)

Every push to this branch automatically triggers a **GitHub Actions** workflow
(`.github/workflows/build-apk.yml`) that builds the app and publishes the APK as a
**Release** on the repository:

1. Go to the repo's GitHub page, **Releases** section (or `.../releases/tag/latest`).
2. Download `engvocab-debug.apk` directly from your phone (browser, or a GitHub client app).
3. Open it: Android will ask once to allow installs from whatever app you used to download
   it (e.g. Chrome) - that's normal for an APK distributed outside the Play Store, not a
   sign the app itself is unsafe.
4. Install. From then on the app lives on your phone like any other: FSRS, import, all
   local, no connection to this repository required.

It's neither a website nor a PWA: it's a real, native Android APK. GitHub in this flow is
just the "factory" that compiles the code - once installed, the app no longer depends on
GitHub in any way. Every new build (new push) refreshes the same `latest` release with a
stable signature (see `keystore/debug.keystore`), so you can install right over the
previous version without uninstalling first.

If you'd rather build it yourself with Android Studio instead of downloading the ready
APK, see "Building it yourself" below.

## The memorization method: FSRS

Research on long-term memory converges on two techniques: **retrieval practice** (actively
pulling information out of memory, not just re-reading it) and **spaced repetition**
(reviewing information at growing intervals, right before you'd forget it). Self-graded
flashcards, like Duocards', already implement the first one. What actually changes the
quality of a flashcard app is *which algorithm decides the intervals*.

EngVocab uses **FSRS (Free Spaced Repetition Scheduler)**, the best-performing spaced
repetition algorithm according to current public benchmarks:

- FSRS models a card's recall probability with two parameters (*stability* and
  *difficulty*) fitted from a model trained on hundreds of millions of real reviews,
  instead of the fixed, one-size-fits-all formulas of the classic SM-2 algorithm (the one
  "old-school" Anki and SuperMemo use).
- In the Open Spaced Repetition project's benchmarks, FSRS matches SM-2's retention with
  roughly **20-30% fewer reviews**, and beats SM-2 for 99.6% of tested users (FSRS-6, with
  recency weighting).
- Since late 2023 it's Anki's recommended default, and has become the de facto standard
  for spaced repetition apps.

Sources: [FSRS vs SM-2 (antiagent.io)](https://www.antiagent.io/blog/fsrs-vs-sm-2),
[Open Spaced Repetition project benchmark](https://expertium.github.io/Benchmark.html),
[The FSRS Algorithm - official wiki](https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm).

The implementation in `core/.../fsrs/FsrsScheduler.kt` is a faithful port of
[`py-fsrs`](https://github.com/open-spaced-repetition/py-fsrs) (the Open Spaced Repetition
project's reference Python library, MIT licensed) - same 21 parameters, same formulas. It's
verified by 7 tests that compare results value-by-value against the real Python library's
output, given the exact same inputs (see "Testing" below).

## Project structure

```
core/   pure Kotlin module (no Android dependency): FSRS algorithm, import parsers
        (Duocards, Kindle), dictionary/translation API response parsers, and the
        Cloudflare D1 REST client used to sync vocabulary online.
        Fully unit tested with JUnit 5 - see core/src/test.
app/    the Android app (Jetpack Compose + Room + Navigation), depends on :core.
cli/    computer-side bulk-import tool (plain JVM), depends on :core. See "Importing
        your vocabulary" below - this is how a Duocards/Kindle export gets pushed into
        the online database, no adb, no in-app file picker involved.
```

The critical logic (scheduling algorithm, parsing) lives in `:core`, with no Android
dependency: you can read it, test it, and trust its behavior independently of the UI.

## Building it yourself

This project was developed in a sandbox environment **with no access to Google's Maven
repositories** (`dl.google.com`/`maven.google.com`, where AndroidX, Jetpack Compose, and
the Android Gradle Plugin live) - only Maven Central was reachable. Because of that:

- **`:core` and `:cli` were built and tested in that environment** (`./gradlew :core:test`,
  34 tests, all green; `./gradlew :cli:build`) since both only need Maven Central.
- **`:app` was not built there** since it requires Google's Maven repo. The code follows
  the stable AndroidX/Compose/Room APIs for the versions declared in `app/build.gradle.kts`,
  but **the first build should happen in an environment with normal internet access**
  (your PC/Mac, Android Studio) before fully trusting it. (It has since been built
  successfully by CI - see the badge/Releases page - but if you're reading this from a
  fresh clone, treat a first local build as the real verification.)

To build and run the app:

1. Open the project folder in **Android Studio** (Koala or newer).
2. Let Gradle sync (it will download AndroidX, Compose, Room, etc. from Google's Maven).
3. Connect an Android phone (or use an emulator) and hit Run.
4. If Android Studio offers to auto-update AGP/Compose/Room versions, that's safe to accept.

Or from the terminal, with JDK 17+ and normal network access:

```bash
./gradlew :app:assembleDebug
# the APK will be at app/build/outputs/apk/debug/
```

## Testing

```bash
./gradlew :core:test
```

34 tests cover:
- **FSRS scheduler** (7 tests): review sequences (Again/Hard/Good/Easy, learning, review,
  relearning, lapses) compared value-by-value against the official `py-fsrs` Python
  library's output, given identical parameters and timestamps.
- **CSV/TSV import** (8 tests): auto-detected delimiter, optional header row, quoted
  fields, missing columns, and the real Duocards word-list shape (see below).
- **Kindle import** (8 tests): highlight+note pairing, English and Italian locale formats,
  highlights with no note, bookmarks ignored, deduplication, language tagging.
- **Dictionary/translation response parsing** (7 tests): valid and malformed responses
  from the external APIs.
- **Cloudflare D1 response parsing** (4 tests): the D1 REST API's JSON envelope, including
  the error-envelope and malformed-response cases.

## Importing your vocabulary

Your vocabulary lives in an online database (a [Cloudflare D1](https://developers.cloudflare.com/d1/)
database, free tier), not just on your phone. Day to day, add single words on the phone
(the **Cards** tab's `+` button) - those stay local, never uploaded. Bulk imports (e.g. your
whole Duocards deck, or a Kindle clippings file) and any editing/curation of that shared
vocabulary happen **from a computer**: either directly in Cloudflare's own dashboard table
editor, or in bulk with the `:cli` tool. The phone only ever *pulls* from the cloud - your
review/FSRS progress is never uploaded, it stays local to each phone.

### One-time setup

You need a free Cloudflare account and a D1 database - this repo's D1 database is already
created (name `engvocab`), so you just need your own read/write access to it:

1. In the [Cloudflare dashboard](https://dash.cloudflare.com), open **Workers & Pages → D1**
   and find the `engvocab` database. Its **Database ID** is shown on that page - copy it.
2. Your **Account ID** is shown on the right sidebar of almost any page in the dashboard
   (or under **Workers & Pages → Overview**) - copy it too.
3. Create an API token: **My Profile → API Tokens → Create Token → Custom Token**, grant it
   **Account → D1 → Edit** permission (scoped to your account), then create it and copy the
   token (Cloudflare only shows it once).
4. In EngVocab, open **Settings** and paste the Account ID, Database ID, and API token into
   the "Cloud sync" fields.
5. On your computer, export the same three values as environment variables (the `:cli` tool
   reads them from the environment, not from the app):

   ```bash
   export CF_ACCOUNT_ID="..."
   export CF_D1_DATABASE_ID="..."
   export CF_API_TOKEN="..."
   ```

### Editing vocabulary from a computer

For quick edits/additions, use Cloudflare's own dashboard: **Workers & Pages → D1 →
engvocab → Tables → words**, which gives you a spreadsheet-like table editor (or the
**Console** tab for raw SQL) - no custom admin panel needed. Columns: `front`, `back`,
`language` (`en`/`de`/`fr`/`nl`), `definition`, `example`, `part_of_speech`, `card_type`,
`tags`, `source`, `source_label`, `known_already` (0/1), `is_deleted` (0/1 - set instead of
deleting the row, so phones can detect and remove it too), `created_at`/`updated_at` (epoch
milliseconds).

### Bulk import (Duocards / Kindle)

```bash
./gradlew :cli:run --args="duocards path/to/export.csv en"
# or: ./gradlew :cli:run --args="kindle \"My Clippings.txt\" de"
```

First argument is the format (`duocards` or `kindle`), second is the input file, third is
the language code (`en`, `de`, `fr`, or `nl`). The tool parses the file, skips any word
already present in D1 for that language (safe to re-run), and inserts the rest directly
into the `words` table over Cloudflare's REST API. It prints a summary when done.

### Pulling it down to your phone

Open EngVocab, go to the **Sync** tab, and tap **Sync now**. This pulls every non-deleted
word from D1: new remote words are added (already-known ones get two synthetic "Good"
reviews applied, same as before, so they start in long-term review instead of at zero), an
edited remote word updates the matching local card without touching its FSRS progress, and
a word removed from D1 (`is_deleted = 1`, or the row deleted outright) is removed from the
phone too. Cards you added manually on the phone are never touched by sync.

### Duocards' real export format

Duocards' word-list export has no translation column at all - just the term and a
learning-status column (Italian locale: `Parola;Livello`, values `In apprendimento` /
`Imparata completamente`; other locales use `Word;Status`-style headers with values like
`Learning`/`Mastered`). The `:cli` tool/`:core` parser detects this automatically: the
back is left blank (filled in later via auto-fill or by hand), and cards whose status
means "already learned" are flagged `known_already` in D1, which triggers the two synthetic
"Good" reviews on the phone described above. If your export has a real translation column
instead (front/back/example), that's supported too and takes priority.

### From Kindle

Connect your Kindle to a computer over USB and copy the `documents/My Clippings.txt` file,
then run the `:cli` tool on it as above with `kindle` as the format. It automatically
recognizes highlights and, if you added a note right after highlighting a term (e.g. its
translation), pairs them into front=highlight, back=note. Highlights with no note become
cards with an empty back, to be filled in via the dashboard or auto-fill on the phone. The
parser recognizes both the English format ("Highlight", "Location") and the Italian one
("evidenziazione", "posizione"). Bookmarks are ignored, and highlighting the same word
twice doesn't create duplicates.

### Auto-fill

For cards with no translation (the "Auto-fill" button on a card's form, phone-side only),
the app queries two free online services (no key required):
[dictionaryapi.dev](https://dictionaryapi.dev) for the definition/example (covers English,
German, and French - it doesn't publish a Dutch dictionary, so Dutch cards only get the
translation) and [MyMemory](https://mymemory.translated.net) for the Italian translation,
for all four languages. You can turn this off in Settings. Note: MyMemory has a daily
free-usage cap.

## Known limitations / possible future work

- Cloud sync is one-directional (cloud → phone) and vocabulary-only, by design: the app
  never writes to D1, and review/FSRS progress is never uploaded or synced across devices.
- The phone talks to Cloudflare's D1 REST API directly (no Worker proxy in front of it),
  which Cloudflare's docs note is best suited for lower-volume, administrative-style use -
  fine for an occasional manual sync, but keep the API token to yourself.
- No pronunciation audio.
- Voice notes/images aren't supported.
- Bulk-importing large batches into D1 with `:cli` runs one insert per new word, so a
  multi-thousand-word import can take a few minutes.
