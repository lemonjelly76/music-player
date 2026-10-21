# Music Player

An album library that an AI model curates for a household of up to five people. Tell Claude what you like. It keeps
your library and taste profile, picks albums with a reason for each, and saves them as `AI Picks` playlists in your
own Tidal or Spotify account, where the official app plays them. Thin Android and Windows apps show the picks,
take your ratings and offer "More like this".

Design and decisions: [docs/design-document.md](docs/design-document.md).

## Layout

| Path | What |
| --- | --- |
| `backend/` | Spring Boot MCP server and REST API, PostgreSQL |
| `apps/` | Kotlin + Compose Multiplatform: `shared` screens, the `android` APK and the `desktop` Windows app |
| `docs/` | Design document |
| `compose.yaml` | Database and server for the home server |

## Run the server

1. Copy `.env.example` to `.env` and fill it in. Only `POSTGRES_PASSWORD`, `APP_USERNAME` and `APP_PASSWORD` are
   required; each service you leave out is simply not used.
2. `docker compose up -d --build`. The server listens on port 8082 (`APP_PORT` changes it).

With no streaming service configured, picks are checked against MusicBrainz and kept, but not synced as playlists.

### Streaming services (ADR-006, ADR-008)

Register an app with Tidal (developer.tidal.com) and/or Spotify (developer.spotify.com, Development Mode). Set its
redirect URI to `<PUBLIC_URL>/api/connect/tidal/callback` or `.../spotify/callback`, and put the client id and secret
in `.env`. Each person then picks their service through Claude (`set_person_service`) and connects their own account
from the app's Settings screen.

`PUBLIC_URL` must be an HTTPS address the phone's browser can reach, for example Tailscale Serve, private to your
tailnet. garmin-ai already uses 8443 on the home server, so this uses 8444:

```
sudo tailscale serve --bg --https=8444 http://127.0.0.1:8082
```

`PUBLIC_URL` is then `https://<pc>.<tailnet>.ts.net:8444`.

### Connect Claude (ADR-011)

- **Claude Desktop:** add the server through `mcp-remote` with a Basic login header for `APP_USERNAME:APP_PASSWORD`,
  at `http://<server>:8082/mcp`.
- **claude.ai and the Claude phone app:** set `MCP_TOKEN` (`openssl rand -hex 32`) and publish only the MCP endpoint
  with Tailscale Funnel. garmin-ai already holds `/mcp` on 443, so this one goes at `/music-mcp`:

  ```
  sudo tailscale funnel --bg --set-path=/music-mcp http://127.0.0.1:8082/mcp
  ```

  Add a custom connector (Settings → Connectors) with `https://<pc>.<tailnet>.ts.net/music-mcp` and the header
  `Authorization: Bearer <MCP_TOKEN>`. Anyone with the token can use the tools, so keep it out of chats and the repo.

Then try the `album_picks` prompt, or just ask: "Add Craig, he loves OK Computer and Mezzanine. Give him 10 albums for
running."

### Optional

- `LASTFM_API_KEY` (last.fm/api): similar artists for the candidate pool, and scrobbles as plays for Tidal users who
  set a Last.fm user.
- `LIDARR_URL` and `LIDARR_API_KEY`: `send_to_lidarr` adds albums to Lidarr as wanted.

## Install the app

```
cd apps
JAVA_HOME=<a JDK 21> ./gradlew :android:assembleRelease
adb install android/build/outputs/apk/release/android-release.apk
```

### Windows

Build on Linux with wine and an unzipped Windows JDK 21 (adoptium.net):

```
cd apps
JAVA_HOME=<a JDK 21> desktop/package-windows.sh <path to the Windows JDK>
```

Unzip `desktop/build/windows/AI-Picks-windows.zip` on the PC and run `AI Picks\AI Picks.exe`; it brings its own Java.
The PC needs Tailscale to reach the server. `./gradlew :desktop:run` runs it on Linux.

Then open Settings, enter the server address (the Tailscale Serve URL, `https://<pc>.<tailnet>.ts.net:8444`), username and password, and choose who you are from
the person menu.

## Develop

- Backend tests: `cd backend && ./mvnw test`. Docker must be running for the PostgreSQL Testcontainer.
- App tests: `cd apps && ./gradlew :shared:desktopTest :desktop:test`.
