# Changelog

All notable changes to qEvent are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.0.0] — 2026-10-27

First stable release.

### Added
- `/event announce <message>` — cinematic announcement to every online player, including the sender.
- `/event announce <player> <message>` — announce with another player's head; players the server has
  not seen yet can be addressed by quoting the name (`/event announce "Notch" …`).
- `/event title <title> <subtitle>` — custom title and subtitle (supports `"` quotes and `|`).
- `/event preview` — private preview for the sender.
- `/event reload` and `/event help`, with tab completion and per-subcommand permission checks.
- Permissions: `qevent.announce`, `qevent.preview`, `qevent.reload`, `qevent.admin`.
- Real announcer head rendered as a player-head item on a per-viewer display entity, with
  camera-relative anchoring, scale/slide entrance and exit animation, and per-player visibility.
- Skin resolution: online profile → local disk cache → Mojang API (async, timeouts, TTL cache).
- Never renders a generic Steve/Alex head: missing skins fall back to text only, with a stated reason.
- Fully commented `config.yml` and `lang/en.yml`; MiniMessage **and** legacy `&` formatting everywhere.
- Configurable duration, cooldown, queueing, message length limit, title/subtitle/action bar/boss bar,
  sound cues, animation style, head position/scale/brightness, resource-pack delivery, admin logging.
- Optional artwork resource pack (branded display font, badge, brackets, divider) with per-player
  status tracking and a vanilla-safe fallback template.
- 22 unit tests (`mvn test`) covering configuration parsing, text formatting/escaping, version
  parsing and SHA-1 decoding.
- GitHub Actions CI building the project and running the tests on JDK 21.
- MIT license, README, compatibility matrix, rendering and resource-pack documentation, security policy.