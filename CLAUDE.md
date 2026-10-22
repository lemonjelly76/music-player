# Music Player

Design decisions live in docs/design-document.md. Read it before changing architecture.

- TDD: failing test first, make it pass, then refactor.
- SOLID and clean code; small, well-named classes and methods.
- Comments only where something genuinely needs explaining.
- Domain code stays free of Spring, Jakarta and vendor types (ADR-003); ArchUnit enforces it.
- Secrets come from environment variables; never commit .env.
- Backend: `cd backend && ./mvnw test` (needs Docker for Testcontainers).
- Apps (Android and Windows share `apps/shared`, ADR-016): Gradle 8.14 does not run on Java 25, so build with JDK 21:
  `cd apps && JAVA_HOME=~/.sdkman/candidates/java/21.0.12+1.1-tem ./gradlew :shared:desktopTest :desktop:test :android:assembleDebug`.
  The Windows zip: `desktop/package-windows.sh <unzipped Windows JDK 21>` (needs wine).
- Try changes by hand against the test stack (test-stack/README.md: fake Spotify, charts and MusicBrainz), not the
  real server; Spotify locks the app out for a day after a burst. Use the real services only for a final check.
