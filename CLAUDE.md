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
  date** while on hold - see ROADMAP.md for the reasoning. Don't bump them
  incidentally as part of an unrelated change; only touch them if Alessio asks or the
  project comes off hold.
- See ROADMAP.md for the full history of *why* it's on hold and what was left
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

## Roadmap, idee e progressi

`ROADMAP.md` nella root è l'**unico** file per todo, idee, decisioni e diario del progetto
(niente `FUTURE-ARCHITECTURE.md`, `IDEE.md`, `TODO.md`). È letto da https://alessiomartini.github.io/projects-overview/.
Sezioni: `## <fase>` (checkbox, contano nella percentuale), `## Idee` (checkbox, non contano),
`## Decisioni e cose provate` (prosa: scartato/rimandato e perché, provato e annullato, limiti e bug noti), `## Log`.

- Alla fine di ogni sessione di lavoro (prima del commit finale), aggiorna `ROADMAP.md`:
  - spunta (`- [x]`) i punti completati in questa sessione;
  - aggiungi come `- [ ]` i nuovi compiti nella fase giusta, e le idee non decise in `## Idee`;
  - annota in `## Decisioni e cose provate` ciò che è stato scartato, rimandato o annullato, col perché;
  - aggiungi in cima a `## Log` una riga `- AAAA-MM-GG: <cosa è stato fatto, in una frase>`
    (se esiste già una riga con la data di oggi, estendila invece di crearne un'altra).
- Non riscrivere né riordinare le voci esistenti senza motivo; non cancellare le voci completate.
- Se `ROADMAP.md` non esiste, crealo da `templates/ROADMAP.md` di projects-overview.
- Includi l'aggiornamento di `ROADMAP.md` nello stesso commit del lavoro.
