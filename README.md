# EngVocab

An Android flashcard app for growing your English vocabulary - words, phrasal verbs,
idioms, and expressions - with no ads and no card limits. Local, free, open source.

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
        (Duocards, Kindle), dictionary/translation API response parsers.
        Fully unit tested with JUnit 5 - see core/src/test.
app/    the Android app (Jetpack Compose + Room + Navigation), depends on :core.
```

The critical logic (scheduling algorithm, parsing) lives in `:core`, with no Android
dependency: you can read it, test it, and trust its behavior independently of the UI.

## Building it yourself

This project was developed in a sandbox environment **with no access to Google's Maven
repositories** (`dl.google.com`/`maven.google.com`, where AndroidX, Jetpack Compose, and
the Android Gradle Plugin live) - only Maven Central was reachable. Because of that:

- **`:core` was built and tested in that environment** (`./gradlew :core:test`, 26 tests,
  all green) since it only needs Maven Central.
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

26 tests cover:
- **FSRS scheduler** (7 tests): review sequences (Again/Hard/Good/Easy, learning, review,
  relearning, lapses) compared value-by-value against the official `py-fsrs` Python
  library's output, given identical parameters and timestamps.
- **CSV/TSV import** (5 tests): auto-detected delimiter, optional header row, quoted
  fields, missing columns.
- **Kindle import** (7 tests): highlight+note pairing, English and Italian locale formats,
  highlights with no note, bookmarks ignored, deduplication.
- **Dictionary/translation response parsing** (7 tests): valid and malformed responses
  from the external APIs.

## Importing your vocabulary

From the **Import** screen you can pick between two formats:

### From Duocards

Duocards doesn't publish a stable, official export format, so the parser is deliberately
flexible: it auto-detects whether the file is comma-, semicolon-, or tab-separated,
whether there's a header row (front/back, question/answer, etc.) or not, and takes the
first column as the term and the second as the translation (an optional third column is
read as an example sentence). If your export uses a different layout than expected, let me
know (or paste an anonymized excerpt here) and I'll tune the parser to the real format.

### From Kindle

Connect your Kindle to a computer over USB and copy the `documents/My Clippings.txt` file
(or send it to your phone). The app automatically recognizes highlights and, if you added
a note right after highlighting a term (e.g. its translation), pairs them automatically
into front=highlight, back=note. Highlights with no note become cards with an empty back,
to be filled in manually or via auto-fill. The parser recognizes both the English format
("Highlight", "Location") and the Italian one ("evidenziazione", "posizione"). Bookmarks
are ignored, and highlighting the same word twice doesn't create duplicates.

### Auto-fill

For imported cards with no translation (or when you tap "Auto-fill" on a card's form), the
app queries two free online services (no key required):
[dictionaryapi.dev](https://dictionaryapi.dev) for the English definition/example and
[MyMemory](https://mymemory.translated.net) for the Italian translation. You can turn this
off in Settings. Note: MyMemory has a daily free-usage cap - for very large imports it may
stop translating partway through; if that happens, retry the next day or fill in the
remaining cards by hand.

## Known limitations / possible future work

- No sync across devices (data lives only in the phone's local database); a manual
  backup/export is a natural extension to add.
- No pronunciation audio.
- Voice notes/images aren't supported.
- Importing large batches with auto-fill enabled can take a few minutes (calls to the free
  APIs are sequential to respect their rate limits).
