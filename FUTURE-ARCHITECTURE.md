# Future architecture / open ideas

**Status: on hold.** See README.md's top note for the full story: this app ended up
reinventing a chunk of what Anki already does well, so day-to-day vocabulary study moved
to Anki (`scripts/export_to_anki.py`). This repo is kept as a working reference/prototype,
not an actively maintained app — see CLAUDE.md for what that means for future changes here.

## Why it's on hold, and what would bring it back

Anki is general-purpose and doesn't model some language-learning-specific ideas this app
explored — tracking term→meaning, meaning→term, and listening recall as separate-but-
correlated FSRS schedules per word (see README.md's "The memorization method: FSRS"). If
one of those ideas gets fleshed out enough to be worth a dedicated app again, development
picks back up here.

## Left half-built (from README's "Known limitations")

- Cloud sync is one-directional (cloud → phone) and vocabulary-only, by design — FSRS
  review progress is never uploaded/synced across devices.
- The phone talks to Cloudflare's D1 REST API directly, with no Worker proxy in front of
  it (fine for occasional manual sync, not for scale).
- No pronunciation audio; voice notes/images aren't supported.
- Bulk import via `:cli` does one insert per word — a multi-thousand-word import takes a
  few minutes.
- The stats site (`web/stats/`) isn't live from a fresh clone — it needs a one-time
  Cloudflare Pages setup (see README's "Stats site" section).

## User feedback / notes feature — not planned here

Several of Alessio's other sites (`ear-training`, `geopolitics-atlas`, `eating-amsterdam`)
have a small widget letting a visitor leave a free-text note, stored in a Cloudflare D1
database (own dedicated D1 for each, so far — see those repos' own FUTURE-ARCHITECTURE.md
for a discussion of a possible database shared across sites instead of one per site).

This repo deliberately does **not** get that feature while on hold: it has no public
visitor traffic to collect feedback from (distribution is a GitHub Releases APK, not a
website), and CLAUDE.md already says not to add new features here without asking first.
If the project comes off hold and gains a public-facing surface again, revisit this then
— rather than adding it speculatively now.

## Dependencies

Gradle plugin/library versions (`app/build.gradle.kts`, `core/build.gradle.kts`,
`cli/build.gradle.kts`) are intentionally left as-is while on hold, rather than bumped
incidentally. Bumping them now would mean re-verifying the Android build in an environment
with normal Google Maven access (see README's "Building it yourself" — this repo's own dev
sandbox couldn't reach `dl.google.com`) for no functional benefit to a project nobody is
actively using day to day. Revisit when the project comes off hold.
