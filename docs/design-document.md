# Music Player — Design Document

Last updated: 20 October 2026

Status key: Accepted · Proposed · Deferred · Rejected

Source: the "Music Discovery App — Investigation Spec" (Claude Docs, 20 October 2026). This document records how the proof of concept (POC) implements it and where it departs from it.

## 1. Purpose

A personal album library that an AI model curates. People tell the model what they like; it stores that, finds new albums they would likely enjoy and builds themed playlists, all through an MCP server. A thin Android app sits on top. Streaming services such as Spotify recommend tracks by popularity and are poor at suggesting whole albums. Here the recommender is an LLM that sees the person's explicit taste profile, and the streaming service is only the catalogue and the player.

## 2. Engineering principles

- SOLID and clean code throughout
- Test-driven development: red, green, refactor
- Readable, concise code; comments only where something genuinely needs explaining
- Domain logic kept free of framework and vendor detail so it stays easy to test

## 3. Constraints

- A self-built Tidal player only gets 30-second previews on Tidal's third-party tier, even for paying subscribers.
- Spotify's Development Mode allows at most 5 users, needs the owner to have Premium, caps search pages at 10 and gives new apps no `/recommendations`.
- A custom app cannot add its own Google Home voice commands; Assistant Actions were retired in 2023.
- claude.ai reaches custom connectors from Anthropic's cloud, so `/mcp` must be on public HTTPS.
- Household use: up to 5 people, a private APK, the existing always-on home server, low running cost.

## 4. Decision log

### ADR-001 Engineering approach — Accepted

SOLID, clean code, TDD, minimal comments (see section 2).

### ADR-002 Technology stack — Accepted

- **Backend:** Java 25, Spring Boot 4.1, Spring AI 2.0 MCP server starter (`spring-ai-starter-mcp-server-webmvc`, Streamable HTTP), PostgreSQL 18 with Flyway, plain `JdbcClient` for persistence. Built with Maven and run with Docker Compose.
- **App:** Kotlin and Jetpack Compose, Material 3, a single module with no DI framework. AGP 8.13, Gradle 8.14, minSdk 26.

**Why:** the spec chose the Java/Spring stack already used for garmin-ai, and the versions match that project and Todolistapp, so their caches and conventions carry over. Postgres rather than SQLite because the home server already runs it for garmin-ai and Flyway and Testcontainers work the same way.

### ADR-003 Architecture: ports and adapters (hexagonal) — Accepted

- **Domain** (`domain`): people, albums and album keys, library, taste profiles, categories, play tracking, recommendation lists and the candidate pool's ranking, all as pure code.
- **Ports** (`domain.port`): `MusicProvider`, `SimilaritySource`, `AlbumCatalogue`, `ListeningHistory`, `AlbumWishlist` and one repository per aggregate.
- **Application** (`application`): one service per use case, wired in `ApplicationConfiguration`.
- **Adapters** (`adapter`): Tidal, Spotify, Last.fm, MusicBrainz, Lidarr, OAuth, JDBC, MCP tools, REST controllers and the play collection schedule.

ArchUnit keeps the domain free of Spring, Jakarta and Jackson, and keeps the application layer from depending on adapters.

**Why:** the streaming APIs are the least stable part of the system (constraints above). Isolating them keeps the choice between Tidal and Spotify reversible, as the spec asks.

### ADR-004 MCP first; the model is the recommender and the app never talks to it — Accepted

The person talks to Claude on their own Claude plan. Claude reads and writes the library through MCP tools (section 5). The Android app reads the same database through a small REST API, so the phone and the model always see the same data. Recommendations run conversationally: nothing in the server calls an LLM.

- **Prompt:** `album_picks` (person, optional category) walks Claude through the pipeline in ADR-007.
- **Instructions:** the server tells Claude it is the recommender, to use refs from the tools rather than invent ids, to record feedback and rewrite the taste profile, to respect "never recommend" and category rules, and never to ask anyone to turn play counting on.

**Why:** this is the spec's POC plan. It proves the AI loop with no API spend and no extra infrastructure.

### ADR-005 Hand-off playback: playlists in the person's own streaming account — Accepted

Lists are written as real playlists in the person's streaming account, named predictably: `AI Picks` and `AI Picks — <Category>`. A new list for the same person and category replaces the previous playlist. The official Tidal or Spotify app plays them, which gives full-length playback, Android Auto, casting and (on Spotify) Google Home voice ("Hey Google, play AI Picks — Running on Spotify") for free. The Android app links to albums and playlists rather than playing them.

- **Tidal:** playlists hold tracks, so an album is written as all its tracks in order (20 per request). The previous playlist is deleted.
- **Spotify:** there is no playlist delete, so the previous one is unfollowed, which removes it from the library.

**Why:** the 30-second preview limit rules out an in-app Tidal player.

### ADR-006 `MusicProvider` port; Tidal preferred, Spotify as a drop-in — Accepted

`MusicProvider` covers search, album lookup, matching by artist and title, similar albums, favourites (read and add), replacing a playlist, and app links. Both adapters implement all of it (Spotify returns no similar albums). Each person has a service (`tidal` or `spotify`). Catalogue search uses the person's service, else Tidal, else Spotify.

- **Tidal:** JSON:API v2 at `openapi.tidal.com/v2`, with the app's client-credentials token for catalogue reads and the person's token for collections and playlists.
- **Spotify:** Web API v1, with search capped at 10 results.

**Risk:** both adapters follow the public API documentation and are tested against recorded-shape stubs, not live accounts. The spec's first POC step, an evening's spike per API, still needs doing before relying on them; see section 7.

### ADR-007 Recommendation pipeline: server gathers, model chooses, server checks — Accepted

| Step | Done by | Where |
| --- | --- | --- |
| 1. Gather candidates | Server | `CandidateService`: up to 8 seeds (best rated, plus most played when counting is on), 10 suggestions per seed per source |
| 2. Filter | Server | Drops what is in the library, recommended in the last 28 days, already known (any category) or turned down for this category; keeps only what the person's service carries, at most 3 lookups per album wanted |
| 3. Pre-rank | Server | `CandidatePool`: 2 per agreeing source + 1 per seed + the best seed's rating + half the log2 of seed plays |
| 4. Choose | Model | About 7 from `get_candidates` plus up to 3 wildcards |
| 5. Explain and learn | Model | A reason per pick; `update_taste_profile` |
| 6. Validate and deliver | Server | `save_recommendations` resolves every pick to a real album on the person's service, drops the rest with a reason and syncs the playlist |

- **Album identity:** `AlbumKey` matches albums across catalogues by normalised artist and title, ignoring case, accents, a leading "The", "&" against "and", and edition suffixes such as "(Remastered 2019)" or "[Deluxe Edition]".
- **Refs:** albums travel as `tidal:<id>`, `spotify:<id>`, `mbid:<release group id>` or `Artist — Title`. Names are checked against the library and past verdicts before any catalogue lookup.
- **More like this (R15):** steps 1 to 3 from one album, served to the app at once, with no model involved.
- **Feedback:** liked picks join the library tagged `ai-pick`. "Not for me" is kept per category, so an album rejected for Running can still come up under Driving. "Already know" applies everywhere.

**Why:** this follows the spec's division of work, and the guardrail that no hallucinated album is ever saved.

### ADR-008 Each person connects their own streaming account — Accepted

The authorisation code flow with PKCE (S256) is used for both services. The app asks the server for a sign-in link and opens it in the browser. The service returns the browser to `<PUBLIC_URL>/api/connect/<service>/callback`, which needs no login: the state it carries was issued for that person and expires after 10 minutes. Tokens are kept per person and service in `provider_account` and refreshed 2 minutes before they expire. A refresh the service refuses removes the connection, and the person is asked to connect again. The service's own user id is read once at connection, from the token response (Tidal) or `/me` (Spotify).

**Why:** the spec decided each person links their own account, so lists land in their own library and Voice Match plays the right one.

### ADR-009 Grounding sources: Last.fm and the service's own similar albums; MusicBrainz as the neutral check — Accepted

- **Last.fm:** a seed's artist's other top albums, plus the top 2 albums of each of its 5 most similar artists. Answers are cached for 24 hours. With no API key, Last.fm is left out.
- **Tidal:** `similarAlbums` for seeds that have a Tidal id.
- **MusicBrainz:** release-group search confirms that an album exists when the person has no streaming service, and supplies the id Lidarr needs. Calls are spaced 1.1 s apart and a 503 is retried once. An unreachable MusicBrainz counts as "not found", so the pick is dropped rather than trusted.
- **ListenBrainz:** Deferred. It needs MusicBrainz artist ids for every seed, and Last.fm plus Tidal give enough candidates for the POC.

### ADR-010 Play counts as an opt-out signal — Accepted

Plays are stored one row per track (`play`, keyed by person, source and time so repeats are ignored) and rolled up to albums when read. A job reads everyone's new plays every hour:

- **Spotify:** recently played, the last 50 tracks, each with the playlist it came from. A play from an AI Picks playlist counts towards that list's category.
- **Last.fm:** scrobbles, for Tidal and anything else that scrobbles. They carry no playlist, so they count towards the person only.

Counting is on by default per person. Each category can override it either way, or follow the person's setting again. When counting is off for a person or category, nothing is collected there and `get_play_stats` returns nothing. Counts already kept stay until cleared (`set_play_tracking` with `clearExisting`).

### ADR-011 Access: one household login, and a bearer token for claude.ai — Accepted

- **API and app:** HTTP Basic with `APP_USERNAME` and `APP_PASSWORD`, stateless, with no CSRF because no browser page holds a session. Claude Desktop reaches `/mcp` the same way through `mcp-remote`.
- **claude.ai and the Claude phone app:** when `MCP_TOKEN` is set (at least 32 characters), `/mcp` also accepts `Authorization: Bearer <MCP_TOKEN>`, compared in constant time. The token opens nothing else.
- **Reachability:** as in garmin-ai ADR-029, Tailscale Funnel publishes only the MCP endpoint on public HTTPS, at `/music-mcp` because garmin-ai holds `/mcp` on the same machine. Tailscale Serve publishes the whole app to the tailnet on port 8444 (garmin-ai has 8443), for the phone app and the sign-in callback.

**Departure from the spec:** the spec suggested a Cloudflare Tunnel with OAuth. That needs a domain and an authorisation server. The garmin-ai setup is already proven on this home server and needs neither.

### ADR-012 Lidarr for albums worth owning — Accepted

`send_to_lidarr` looks the album up in Lidarr, by MusicBrainz id when known, else by artist and title. An album Lidarr already lists is set to monitored and searched for. A new one is added as wanted with its artist, who is otherwise left unmonitored, using the configured root folder and profiles. It is optional: without `LIDARR_URL` the tool says so.

### ADR-013 The Android app — Accepted

The app has three screens and a person switcher.

- **Picks:** a tab per category plus "All". Each tab shows the latest list with reasons, a wildcard badge, feedback chips, "Open in Tidal/Spotify", "More like this" and "Play in Tidal/Spotify" for the whole playlist.
- **Library:** ratings, notes and tags, with the same actions.
- **Settings:** server address and login, connecting the streaming service, and the play-count switch for the person and per category (Default / On / Off).

It is signed with the debug key as a private APK. It refreshes when it comes back to the foreground, so returning from a sign-in or from listening shows the latest.

### ADR-014 Out of the POC — Deferred

- **Scheduled refresh (R7):** a weekly job calling an LLM API with the same tools. It needs an API key and a model choice (open question).
- **Windows client (R10):** the server already serves any client.
- **Own player and native Android Auto (R9 beyond the official apps):** only worth it with a Lidarr or local library.
- **Google Home voice on Tidal (R8):** depends on the open question below; Spotify works today through the predictable playlist names.

## 5. MCP tools

| Tool | Purpose |
| --- | --- |
| `list_people`, `add_person`, `set_person_service` | People, their service and connection, Last.fm user |
| `search_catalogue` | Albums in the person's service (or the first configured), with refs |
| `add_liked_album`, `update_album`, `remove_album`, `get_library`, `import_favourites` | The library (R1, R11) |
| `get_taste_profile`, `update_taste_profile` | Profile, rules, recent verdicts and the category's rules |
| `list_categories`, `upsert_category` | Shared categories and their rules (R6) |
| `get_play_stats`, `set_play_tracking` | Play counts and the toggle (R14) |
| `get_candidates`, `get_similar` | The ranked pool, and ad-hoc similar albums |
| `save_recommendations`, `get_recommendations`, `record_feedback` | Lists, validation, playlist sync and verdicts (R3, R4) |
| `send_to_lidarr` | Wanted albums (R12) |

## 6. Requirements coverage

| ID | Requirement | Status |
| --- | --- | --- |
| R1 | Library with rating, notes, tags, date added | Built |
| R2 | MCP server to manage albums by conversation | Built |
| R3 | AI recommendation lists per person with reasons | Built |
| R4 | Feedback on picks improves the next batch | Built |
| R5 | Play from the app via Tidal, Spotify or Lidarr | Built as hand-off links (ADR-005); Lidarr playback deferred |
| R6 | Lists per category or activity | Built |
| R7 | Scheduled refresh | Deferred (ADR-014) |
| R8 | Google Home voice | Spotify: by playlist name; Tidal: open question |
| R9 | Android Auto | Through the official apps |
| R10 | Windows client | Deferred |
| R11 | Import favourites | Built (`import_favourites`) |
| R12 | Push picks to Lidarr | Built |
| R13 | Up to 5 people | Built |
| R14 | Play counts with a toggle | Built |
| R15 | "More like this" without a model call | Built |

## 7. Open questions

- Do the Tidal and Spotify adapters work unchanged against live accounts? Run the spike (POC step 1): register the apps, connect one person and save one list.
- Does Tidal's third-party tier allow `playlists.write` and collection writes without approval?
- Can Tidal be linked as a voice service in the Google Home app? If not, is voice worth choosing Spotify for?
- A self-built player later: Tidal's higher tier, or Lidarr and own files?
- Where to host: the always-on Samba server or the headless Ubuntu PC?
- Which model and monthly spend for scheduled runs (R7)?
