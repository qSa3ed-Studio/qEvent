# Changelog

All notable changes to qEvent are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.0.2] — 2026-10-28

Visibility and first-run resource-pack repair. (Default head position: `0.45`, scale: `0.8`.)

### Fixed
- Moved the head into the visible upper screen area. The previous `offset-y: 1.2` could push it
  outside the vertical field of view on common 16:9 setups, making it look missing.
- Increased the default head scale from `0.55` to `0.8` for clear visibility.
- Existing configs now migrate the shipped old position/size defaults while preserving custom values.
- New installs and unconfigured older installs now receive the permanent official artwork pack URL
  and verified SHA-1. The resource pack adds fonts/decorations only; the real skin head is an entity.

## [1.0.1] — 2026-10-27

Position and diagnostics update.

### Changed
- The announcer head now defaults to the **top of the screen** next to the title
  (`head.offset-y` default `-0.42` → `1.2`) with a smaller default size
  (`head.scale` default `0.85` → `0.55`). Existing servers keep their saved values;
  delete or update `head.offset-y` / `head.scale` in `config.yml` to pick up the new look.

### Added
- Console warnings when an announcement cannot render a head: unsupported runtime,
  failed skin lookup (with the exact reason) or a textureless skin profile. Previously
  these were only visible with `general.debug: true`.

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