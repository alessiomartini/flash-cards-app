# Claude Code instructions

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
