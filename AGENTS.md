# AGENTS.md

## Branches And Commits

- Create work branches from `main`. Release Please runs only after changes reach `main`, and release APKs are built from tags in the `vX.Y.Z` format.
- Name branches as `<type>/<short-kebab-description>`, where `<type>` matches the release category when possible: `feat`, `fix`, `perf`, `refactor`, `docs`, or `chore`.
- Write commit messages using Conventional Commits: `<type>(<scope>): <summary>`. Use `feat`, `fix`, `perf`, or `refactor` for changes that should appear in `CHANGELOG.md`; use `docs` or `chore` for changes that Release Please should hide from the changelog.
- For breaking changes, use `!` in the commit header or add a `BREAKING CHANGE:` footer so Release Please can calculate the correct version bump.
- Do not manually edit `version.txt`, `.release-please-manifest.json`, `CHANGELOG.md`, or create release tags unless the user explicitly asks for release maintenance. Let Release Please update them from commits merged to `main`.

## Quality Gates

Before committing or after completing a task, always run:

```bash
./gradlew --no-daemon --build-cache :app:lintDebug :app:detekt :app:testDebugUnitTest :app:assembleDebug
```

All four checks must pass before the work is considered done.
