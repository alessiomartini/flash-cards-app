# Claude Code instructions

## Status: on hold

This project is **on hold** - day-to-day vocabulary study moved to Anki (see README.md's
top note and "Exporting to Anki" section). The repo is kept as a working reference, not an
actively maintained app. Concretely, this means:

- **Do not add new features** (including a public feedback widget, a shared D1 database
  for notes across Alessio's sites, or anything else) **without Alessio's explicit
  confirmation first**, even if it looks small or obviously useful.
- Bug fixes, doc updates, and small clarifications are fine on request.
- Dependencies (Gradle plugin/library versions in `app/build.gradle.kts`,
  `core/build.gradle.kts`, `cli/build.gradle.kts`) are intentionally **not kept up to
  date** while on hold - see FUTURE-ARCHITECTURE.md for the reasoning. Don't bump them
  incidentally as part of an unrelated change; only touch them if Alessio asks or the
  project comes off hold.
- See FUTURE-ARCHITECTURE.md for the full history of *why* it's on hold and what was left
  half-built.

## Project

Multi-module Android/Kotlin flash-card application with shared core and web/CLI
surfaces. Keep platform-independent logic in the appropriate shared module.

## Verification

- Read `README.md` and the affected module before editing.
- Use the checked-in Gradle wrapper: `.\gradlew.bat <task>`.
- Run the narrowest relevant unit/test task, then the module build or check.
- Never commit keystores, signing credentials, local properties, or generated builds.

## Workflow

- Preserve existing data formats and migration behavior.
- Test English-vocabulary changes with representative valid and invalid cases.
- Inspect `git diff`, Gradle output, and repository status before committing.
