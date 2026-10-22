# Test stack

The real server talks to Spotify, Apple Music's charts and MusicBrainz. Spotify locks a development-mode app out for
most of a day after a burst of calls, so day-to-day testing runs against a copy of the server that talks to fakes.
Keep the real server for a final check of a finished feature.

```
docker compose --profile test up -d --build test-db fake-services test-app
set -a; . ./.env; set +a           # APP_USERNAME / APP_PASSWORD, never printed
python3 test-stack/seed.py         # adds "Tester" with the fake Spotify connected
test-stack/emulator.sh test        # points the debug app on the emulator at it
```

- **test-app** (`localhost:8092`): the same image as the real server, with its own database that starts empty each
  time the stack starts.
- **fake-services** (`localhost:8091`, WireMock): `fake-services/mappings/fake-services.json`.
  - Spotify: sign-in goes straight back with a code. A song or album asked for by artist and title is always found,
    with a placeholder cover. A typed search finds three results named after the search. Playlist writes succeed.
  - Words in a song or album name trigger edge cases: "not on spotify" finds nothing, and "too many" gets Spotify's
    429 with an hour's Retry-After.
  - Apple Music charts: the UK feeds saved on 2026-10-22 (`fake-services/__files/charts`).
  - MusicBrainz: finds nothing.
- **Claude's tools**: `python3 test-stack/mcp.py save_playlist '{"person": "Tester", ...}'` calls the test server's
  MCP endpoint.
- **Back to the real server**: `test-stack/emulator.sh live https://<server>:8444`.

Stop it with `docker compose --profile test stop test-app fake-services test-db`.
